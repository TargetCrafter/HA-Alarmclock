"""The REST endpoint the Android app pushes alarm/ringing state to."""
from __future__ import annotations

import logging

from aiohttp import web

from homeassistant.components.http import HomeAssistantView
from homeassistant.core import HomeAssistant
from homeassistant.helpers.dispatcher import async_dispatcher_send

from .const import DOMAIN, signal_update
from .store import AlarmClockStore

_LOGGER = logging.getLogger(__name__)


class AlarmClockSyncView(HomeAssistantView):
    """Handles POST /api/ha_alarmclock/sync, authenticated the same as any other HA API call."""

    url = "/api/ha_alarmclock/sync"
    name = "api:ha_alarmclock:sync"
    requires_auth = True

    def __init__(self, hass: HomeAssistant, entry_id: str, store: AlarmClockStore) -> None:
        self._hass = hass
        self._entry_id = entry_id
        self._store = store

    async def post(self, request: web.Request) -> web.Response:
        # hass.http.register_view has no unregister counterpart, so this view outlives the config
        # entry it was set up for; guard against still-arriving pushes after the entry unloads.
        if self._entry_id not in self._hass.data.get(DOMAIN, {}):
            return web.json_response({"error": "integration_unloaded"}, status=410)

        try:
            payload = await request.json()
        except ValueError:
            return web.json_response({"error": "invalid_json"}, status=400)

        if not isinstance(payload, dict) or not payload.get("device_id"):
            return web.json_response({"error": "missing_device_id"}, status=400)

        try:
            device, _is_new = self._store.apply_sync(payload)
        except (KeyError, TypeError, ValueError) as err:
            _LOGGER.warning("Rejected malformed HA Alarm Clock sync payload: %s", err)
            return web.json_response({"error": "malformed_payload"}, status=400)

        async_dispatcher_send(self._hass, signal_update(self._entry_id), device.device_id)

        return web.json_response({"ok": True})
