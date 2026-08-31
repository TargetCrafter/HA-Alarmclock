"""One switch entity per alarm *slot*, reused across the alarms that occupy that slot over time
(see AlarmClockStore._reassign_slots) rather than minting a new entity per alarm ever created.
"""
from __future__ import annotations

from homeassistant.components.switch import SwitchEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .const import DOMAIN, EVENT_COMMAND, signal_update
from .device import build_device_info
from .store import AlarmClockStore, AlarmInfo


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback) -> None:
    store: AlarmClockStore = hass.data[DOMAIN][entry.entry_id]
    entities: dict[tuple[str, int], AlarmSwitch] = {}

    @callback
    def _sync(device_id: str) -> None:
        device = store.devices.get(device_id)
        if device is None:
            return

        new_entities = []
        for slot in device.alarm_slots:
            key = (device_id, slot)
            if key not in entities:
                entity = AlarmSwitch(store, entry.entry_id, device_id, slot)
                entities[key] = entity
                new_entities.append(entity)
        if new_entities:
            async_add_entities(new_entities)
        # Slots whose alarm was deleted are *not* removed here — the entity just goes
        # unavailable (see AlarmSwitch.available) and is picked back up when a new alarm claims
        # that slot number, which is the whole point of keying entities by slot instead of by the
        # phone's own (ever-growing) alarm id.

    entry.async_on_unload(async_dispatcher_connect(hass, signal_update(entry.entry_id), _sync))
    for device_id in store.devices:
        _sync(device_id)


class AlarmSwitch(SwitchEntity):
    """Enables/disables whichever alarm currently occupies this slot. Optimistic: state flips
    immediately on command, and is corrected by the next sync push if the phone disagrees.
    """

    _attr_has_entity_name = False
    _attr_icon = "mdi:alarm"
    _attr_should_poll = False

    def __init__(self, store: AlarmClockStore, entry_id: str, device_id: str, slot: int) -> None:
        self._store = store
        self._entry_id = entry_id
        self._device_id = device_id
        self._slot = slot
        self._attr_unique_id = f"{device_id}_alarm_{slot}"
        self._attr_device_info = build_device_info(store, device_id)

    async def async_added_to_hass(self) -> None:
        self.async_on_remove(
            async_dispatcher_connect(self.hass, signal_update(self._entry_id), self._handle_signal),
        )

    @callback
    def _handle_signal(self, device_id: str) -> None:
        if device_id == self._device_id:
            self.async_write_ha_state()

    def _alarm(self) -> AlarmInfo | None:
        device = self._store.devices.get(self._device_id)
        if device is None:
            return None
        alarm_id = device.alarm_slots.get(self._slot)
        if alarm_id is None:
            return None
        return device.alarms.get(alarm_id)

    @property
    def available(self) -> bool:
        return self._alarm() is not None

    @property
    def name(self) -> str | None:
        alarm = self._alarm()
        if alarm is None:
            return None
        return alarm.label or f"Alarm {alarm.time}"

    @property
    def is_on(self) -> bool:
        alarm = self._alarm()
        return alarm.enabled if alarm else False

    @property
    def extra_state_attributes(self) -> dict[str, object]:
        alarm = self._alarm()
        if alarm is None:
            return {}
        return {
            "time": alarm.time,
            "repeat": alarm.repeat,
            "next_trigger": alarm.next_trigger,
            "snoozed_until": alarm.snoozed_until,
        }

    async def async_turn_on(self, **kwargs) -> None:
        await self._set_enabled(True)

    async def async_turn_off(self, **kwargs) -> None:
        await self._set_enabled(False)

    async def _set_enabled(self, enabled: bool) -> None:
        alarm = self._alarm()
        if alarm is None:
            return
        self.hass.bus.async_fire(
            EVENT_COMMAND,
            {
                "device_id": self._device_id,
                "command": "set_alarm_enabled",
                "alarm_id": alarm.id,
                "enabled": enabled,
            },
        )
        alarm.enabled = enabled
        self.async_write_ha_state()
