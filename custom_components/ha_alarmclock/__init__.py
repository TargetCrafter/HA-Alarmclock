"""The HA Alarm Clock integration.

Talks directly to the HA Alarm Clock Android app: the app pushes alarm/ringing state to
`/api/ha_alarmclock/sync` (see http.py) using a normal Home Assistant long-lived access token,
and this integration exposes that state as entities. Commands going the other way (toggling an
alarm switch, pressing snooze/dismiss, creating a new alarm from Assist — see assist.py) are
published as `ha_alarmclock_command` events on the HA event bus, which the app receives over
Home Assistant's own WebSocket API.
"""
from __future__ import annotations

from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant

from .assist import async_setup_assist
from .const import DOMAIN
from .http import AlarmClockSyncView
from .store import AlarmClockStore

PLATFORMS = ["binary_sensor", "sensor", "switch", "button"]


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    hass.data.setdefault(DOMAIN, {})
    store = AlarmClockStore()
    hass.data[DOMAIN][entry.entry_id] = store

    hass.http.register_view(AlarmClockSyncView(hass, entry.entry_id, store))
    async_setup_assist(hass, store)

    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    return True


async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    unload_ok = await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
    if unload_ok:
        hass.data[DOMAIN].pop(entry.entry_id, None)
    return unload_ok
