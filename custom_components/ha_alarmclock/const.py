"""Constants for the HA Alarm Clock integration."""
from __future__ import annotations

DOMAIN = "ha_alarmclock"

# Fired on the HA event bus when the user controls an alarm switch or presses a button entity;
# the Android app's WebSocket connection subscribes to this event type to receive it.
EVENT_COMMAND = "ha_alarmclock_command"

MANUFACTURER = "HA Alarm Clock"
MODEL = "Android"


def signal_update(entry_id: str) -> str:
    """Dispatcher signal fired whenever a device pushes new state, with device_id as the payload."""
    return f"{DOMAIN}_update_{entry_id}"
