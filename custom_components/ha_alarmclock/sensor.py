"""Next-alarm timestamp sensors: one device-wide "soonest of all alarms" sensor per phone, plus
one per-alarm-slot sensor reused across the alarms that occupy that slot over time (see
AlarmClockStore._reassign_slots), mirroring the entity-reuse pattern used by switch.py.
"""
from __future__ import annotations

import datetime as dt

from homeassistant.components.sensor import SensorDeviceClass, SensorEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.util import dt as dt_util

from .const import DOMAIN, signal_update
from .device import build_device_info
from .store import AlarmClockStore, AlarmInfo


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback) -> None:
    store: AlarmClockStore = hass.data[DOMAIN][entry.entry_id]
    added_devices: set[str] = set()
    slot_entities: dict[tuple[str, int], AlarmNextTriggerSensor] = {}

    @callback
    def _sync(device_id: str) -> None:
        device = store.devices.get(device_id)
        if device is None:
            return

        new_entities = []
        if device_id not in added_devices:
            added_devices.add(device_id)
            new_entities.append(NextAlarmSensor(store, entry.entry_id, device_id))

        for slot in device.alarm_slots:
            key = (device_id, slot)
            if key not in slot_entities:
                entity = AlarmNextTriggerSensor(store, entry.entry_id, device_id, slot)
                slot_entities[key] = entity
                new_entities.append(entity)

        if new_entities:
            async_add_entities(new_entities)
        # As with AlarmSwitch, a slot whose alarm was deleted is never removed here — its sensor
        # just goes unavailable and is picked back up when a new alarm claims that slot number.

    entry.async_on_unload(async_dispatcher_connect(hass, signal_update(entry.entry_id), _sync))
    for device_id in store.devices:
        _sync(device_id)


class NextAlarmSensor(SensorEntity):
    """The timestamp of the next enabled alarm on this phone, across all its alarms."""

    _attr_has_entity_name = True
    _attr_translation_key = "next_alarm"
    _attr_device_class = SensorDeviceClass.TIMESTAMP
    _attr_icon = "mdi:alarm"
    _attr_should_poll = False

    def __init__(self, store: AlarmClockStore, entry_id: str, device_id: str) -> None:
        self._store = store
        self._entry_id = entry_id
        self._device_id = device_id
        self._attr_unique_id = f"{device_id}_next_alarm"
        self._attr_device_info = build_device_info(store, device_id)

    async def async_added_to_hass(self) -> None:
        self.async_on_remove(
            async_dispatcher_connect(self.hass, signal_update(self._entry_id), self._handle_signal),
        )

    @callback
    def _handle_signal(self, device_id: str) -> None:
        if device_id == self._device_id:
            self.async_write_ha_state()

    @property
    def native_value(self) -> dt.datetime | None:
        device = self._store.devices.get(self._device_id)
        trigger_at = device.next_alarm.trigger_at if device else None
        if not trigger_at:
            return None
        return dt_util.parse_datetime(trigger_at)

    @property
    def extra_state_attributes(self) -> dict[str, object]:
        device = self._store.devices.get(self._device_id)
        if not device or device.next_alarm.alarm_id is None:
            return {}
        return {"alarm_id": device.next_alarm.alarm_id, "label": device.next_alarm.label}


class AlarmNextTriggerSensor(SensorEntity):
    """The next-trigger timestamp of whichever alarm currently occupies this slot."""

    _attr_has_entity_name = False
    _attr_device_class = SensorDeviceClass.TIMESTAMP
    _attr_icon = "mdi:alarm"
    _attr_should_poll = False

    def __init__(self, store: AlarmClockStore, entry_id: str, device_id: str, slot: int) -> None:
        self._store = store
        self._entry_id = entry_id
        self._device_id = device_id
        self._slot = slot
        self._attr_unique_id = f"{device_id}_alarm_{slot}_next_trigger"
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
        label = alarm.label or f"Alarm {alarm.time}"
        return f"{label} next trigger"

    @property
    def native_value(self) -> dt.datetime | None:
        alarm = self._alarm()
        if alarm is None or not alarm.next_trigger:
            return None
        return dt_util.parse_datetime(alarm.next_trigger)

    @property
    def extra_state_attributes(self) -> dict[str, object]:
        alarm = self._alarm()
        if alarm is None:
            return {}
        return {
            "alarm_id": alarm.id,
            "label": alarm.label,
            "time": alarm.time,
            "repeat": alarm.repeat,
            "enabled": alarm.enabled,
            "snoozed_until": alarm.snoozed_until,
        }
