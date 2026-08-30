"""Next-alarm timestamp sensor: one per phone."""
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
from .store import AlarmClockStore


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback) -> None:
    store: AlarmClockStore = hass.data[DOMAIN][entry.entry_id]
    added: set[str] = set()

    @callback
    def _maybe_add(device_id: str) -> None:
        if device_id in added:
            return
        added.add(device_id)
        async_add_entities([NextAlarmSensor(store, entry.entry_id, device_id)])

    entry.async_on_unload(async_dispatcher_connect(hass, signal_update(entry.entry_id), _maybe_add))
    for device_id in store.devices:
        _maybe_add(device_id)


class NextAlarmSensor(SensorEntity):
    """The timestamp of the next enabled alarm on this phone."""

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
