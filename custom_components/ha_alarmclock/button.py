"""Snooze/Dismiss buttons: one pair per phone, act on whatever alarm is currently ringing."""
from __future__ import annotations

from homeassistant.components.button import ButtonEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .const import DOMAIN, EVENT_COMMAND, signal_update
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
        async_add_entities(
            [
                CommandButton(store, entry.entry_id, device_id, "snooze", "Snooze", "mdi:alarm-snooze"),
                CommandButton(store, entry.entry_id, device_id, "dismiss", "Dismiss", "mdi:alarm-off"),
            ],
        )

    entry.async_on_unload(async_dispatcher_connect(hass, signal_update(entry.entry_id), _maybe_add))
    for device_id in store.devices:
        _maybe_add(device_id)


class CommandButton(ButtonEntity):
    """Fires an EVENT_COMMAND event; the app decides whether it applies (e.g. ignores it if
    nothing is currently ringing)."""

    _attr_has_entity_name = True
    _attr_should_poll = False

    def __init__(self, store: AlarmClockStore, entry_id: str, device_id: str, command: str, name: str, icon: str) -> None:
        self._entry_id = entry_id
        self._device_id = device_id
        self._command = command
        self._attr_unique_id = f"{device_id}_{command}"
        self._attr_name = name
        self._attr_icon = icon
        self._attr_device_info = build_device_info(store, device_id)

    async def async_press(self) -> None:
        self.hass.bus.async_fire(EVENT_COMMAND, {"device_id": self._device_id, "command": self._command})
