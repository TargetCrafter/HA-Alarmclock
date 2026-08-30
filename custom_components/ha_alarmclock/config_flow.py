"""Config flow for HA Alarm Clock.

There's nothing to configure here: the phone identifies and authenticates itself with a normal
Home Assistant long-lived access token (generated in the user's HA profile, entered into the
Android app), so this flow only needs to confirm the integration should be set up. Only one
instance is allowed; every phone that pushes to it becomes its own HA device automatically.
"""
from __future__ import annotations

from typing import Any

import voluptuous as vol
from homeassistant import config_entries
from homeassistant.data_entry_flow import FlowResult

from .const import DOMAIN


class HaAlarmClockConfigFlow(config_entries.ConfigFlow, domain=DOMAIN):
    """Handle a config flow for HA Alarm Clock."""

    VERSION = 1

    async def async_step_user(self, user_input: dict[str, Any] | None = None) -> FlowResult:
        await self.async_set_unique_id(DOMAIN)
        self._abort_if_unique_id_configured()

        if user_input is not None:
            return self.async_create_entry(title="HA Alarm Clock", data={})

        return self.async_show_form(step_id="user", data_schema=vol.Schema({}))
