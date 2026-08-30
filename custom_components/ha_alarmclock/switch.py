"""One switch entity per alarm, created/removed dynamically as alarms are added/deleted on the phone."""
from __future__ import annotations

from homeassistant.components.switch import SwitchEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .const import DOMAIN, EVENT_COMMAND, signal_update
from .device import build_device_info
from .store import AlarmClockStore


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback) -> None:
    store: AlarmClockStore = hass.data[DOMAIN][entry.entry_id]
    entities: dict[tuple[str, int], AlarmSwitch] = {}

    @callback
    def _sync(device_id: str) -> None:
        device = store.devices.get(device_id)
        if device is None:
            return

        new_entities = []
        for alarm_id in device.alarms:
            key = (device_id, alarm_id)
            if key not in entities:
                entity = AlarmSwitch(store, entry.entry_id, device_id, alarm_id)
                entities[key] = entity
                new_entities.append(entity)
        if new_entities:
            async_add_entities(new_entities)

        for key in [k for k in entities if k[0] == device_id and k[1] not in device.alarms]:
            stale_entity = entities.pop(key)
            hass.async_create_task(stale_entity.async_remove(force_remove=True))

    entry.async_on_unload(async_dispatcher_connect(hass, signal_update(entry.entry_id), _sync))
    for device_id in store.devices:
        _sync(device_id)


class AlarmSwitch(SwitchEntity):
    """Enables/disables one alarm. Optimistic: state flips immediately on command, and is
    corrected by the next sync push if the phone disagrees (e.g. the alarm was deleted meanwhile).
    """

    _attr_has_entity_name = False
    _attr_icon = "mdi:alarm"
    _attr_should_poll = False

    def __init__(self, store: AlarmClockStore, entry_id: str, device_id: str, alarm_id: int) -> None:
        self._store = store
        self._entry_id = entry_id
        self._device_id = device_id
        self._alarm_id = alarm_id
        self._attr_unique_id = f"{device_id}_alarm_{alarm_id}"
        self._attr_device_info = build_device_info(store, device_id)

    async def async_added_to_hass(self) -> None:
        self.async_on_remove(
            async_dispatcher_connect(self.hass, signal_update(self._entry_id), self._handle_signal),
        )

    @callback
    def _handle_signal(self, device_id: str) -> None:
        if device_id == self._device_id:
            self.async_write_ha_state()

    def _alarm(self):
        device = self._store.devices.get(self._device_id)
        return device.alarms.get(self._alarm_id) if device else None

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
        return {"time": alarm.time, "repeat": alarm.repeat, "next_trigger": alarm.next_trigger}

    async def async_turn_on(self, **kwargs) -> None:
        await self._set_enabled(True)

    async def async_turn_off(self, **kwargs) -> None:
        await self._set_enabled(False)

    async def _set_enabled(self, enabled: bool) -> None:
        self.hass.bus.async_fire(
            EVENT_COMMAND,
            {
                "device_id": self._device_id,
                "command": "set_alarm_enabled",
                "alarm_id": self._alarm_id,
                "enabled": enabled,
            },
        )
        alarm = self._alarm()
        if alarm is not None:
            alarm.enabled = enabled
        self.async_write_ha_state()
