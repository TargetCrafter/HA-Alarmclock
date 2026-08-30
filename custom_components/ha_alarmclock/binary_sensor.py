"""Ringing binary_sensor: one per phone, on while an alarm is actively ringing."""
from __future__ import annotations

from homeassistant.components.binary_sensor import BinarySensorDeviceClass, BinarySensorEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity_platform import AddEntitiesCallback

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
        async_add_entities([RingingBinarySensor(store, entry.entry_id, device_id)])

    entry.async_on_unload(async_dispatcher_connect(hass, signal_update(entry.entry_id), _maybe_add))
    for device_id in store.devices:
        _maybe_add(device_id)


class RingingBinarySensor(BinarySensorEntity):
    """Whether an alarm is currently ringing on this phone."""

    _attr_has_entity_name = True
    _attr_translation_key = "ringing"
    _attr_device_class = BinarySensorDeviceClass.SOUND
    _attr_should_poll = False

    def __init__(self, store: AlarmClockStore, entry_id: str, device_id: str) -> None:
        self._store = store
        self._entry_id = entry_id
        self._device_id = device_id
        self._attr_unique_id = f"{device_id}_ringing"
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
    def is_on(self) -> bool | None:
        device = self._store.devices.get(self._device_id)
        return device.ringing.active if device else None

    @property
    def extra_state_attributes(self) -> dict[str, object]:
        device = self._store.devices.get(self._device_id)
        if not device or not device.ringing.active:
            return {}
        return {
            "alarm_id": device.ringing.alarm_id,
            "label": device.ringing.label,
            "time": device.ringing.time,
        }
