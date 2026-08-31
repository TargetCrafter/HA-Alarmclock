"""Lets Home Assistant create a new alarm on the phone: a `ha_alarmclock.create_alarm` service
(callable from automations/scripts/Developer Tools, and from an LLM-based Assist agent that has
the service exposed to it as a tool) plus a registered intent + custom_sentences/en/ha_alarmclock.yaml
so the built-in keyword-based Assist pipeline understands sentences like "set an alarm for 7am".

Either path ends up firing the same EVENT_COMMAND the phone already listens to for
snooze/dismiss/enable-toggle, with a new "create_alarm" command — the phone inserts a brand-new
Room alarm from it, the same as if the user had tapped + in the app.
"""
from __future__ import annotations

import logging
import re

import voluptuous as vol
from homeassistant.core import HomeAssistant, ServiceCall
from homeassistant.exceptions import HomeAssistantError
from homeassistant.helpers import config_validation as cv, intent

from .const import DOMAIN, EVENT_COMMAND
from .store import AlarmClockStore

_LOGGER = logging.getLogger(__name__)

SERVICE_CREATE_ALARM = "create_alarm"
INTENT_CREATE_ALARM = "HaAlarmClockCreateAlarm"

_TIME_RE = re.compile(
    r"^\s*(?P<hour>\d{1,2})(?:[:.](?P<minute>\d{2}))?\s*(?P<ampm>am|pm|a\.m\.|p\.m\.)?\s*$",
    re.IGNORECASE,
)

SERVICE_CREATE_ALARM_SCHEMA = vol.Schema(
    {
        vol.Optional("device_id"): cv.string,
        vol.Required("time"): cv.string,
        vol.Optional("label", default=""): cv.string,
        vol.Optional("repeat", default=list): [cv.string],
    },
)

_DAY_ABBREVIATIONS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]


def parse_time_text(text: str) -> tuple[int, int] | None:
    """Parses common spoken/typed time phrasings ("7", "7am", "7:30 pm", "19:30") into (hour,
    minute) in 24-hour form. A bare hour with no am/pm is taken as-is (24-hour), e.g. "7" -> 07:00
    — the natural reading for an alarm clock, though it means "7" can't mean 7pm; say "7pm" for that.
    """
    match = _TIME_RE.match(text)
    if not match:
        return None
    hour = int(match.group("hour"))
    minute = int(match.group("minute") or 0)
    ampm = (match.group("ampm") or "").lower().replace(".", "")
    if ampm == "pm" and hour != 12:
        hour += 12
    elif ampm == "am" and hour == 12:
        hour = 0
    if not (0 <= hour <= 23 and 0 <= minute <= 59):
        return None
    return hour, minute


def parse_repeat_days(days: list[str]) -> list[str]:
    """Normalizes a list of day names/abbreviations to the three-letter codes the phone expects;
    unrecognized entries are dropped rather than rejecting the whole request."""
    normalized = []
    for day in days:
        key = day.strip().lower()[:3]
        if key in _DAY_ABBREVIATIONS:
            normalized.append(key)
    return normalized


def _resolve_device_id(store: AlarmClockStore, requested: str | None) -> str:
    if requested:
        if requested not in store.devices:
            raise HomeAssistantError(f"No HA Alarm Clock device with id '{requested}'")
        return requested
    if len(store.devices) == 1:
        return next(iter(store.devices))
    if not store.devices:
        raise HomeAssistantError("No HA Alarm Clock phone has synced yet")
    raise HomeAssistantError(
        "Multiple HA Alarm Clock phones are set up — specify which one with 'device_id'",
    )


def _fire_create_alarm(hass: HomeAssistant, device_id: str, hour: int, minute: int, label: str, repeat: list[str]) -> None:
    hass.bus.async_fire(
        EVENT_COMMAND,
        {
            "device_id": device_id,
            "command": "create_alarm",
            "time": f"{hour:02d}:{minute:02d}",
            "label": label,
            "repeat_days": repeat,
        },
    )


def async_setup_assist(hass: HomeAssistant, store: AlarmClockStore) -> None:
    """Registers the create_alarm service and intent — call once per HA run (idempotent)."""

    async def _handle_service(call: ServiceCall) -> None:
        parsed = parse_time_text(call.data["time"])
        if parsed is None:
            raise HomeAssistantError(f"Couldn't understand time '{call.data['time']}' (try e.g. '07:30' or '7:30 pm')")
        device_id = _resolve_device_id(store, call.data.get("device_id"))
        _fire_create_alarm(hass, device_id, parsed[0], parsed[1], call.data["label"], parse_repeat_days(call.data["repeat"]))

    if not hass.services.has_service(DOMAIN, SERVICE_CREATE_ALARM):
        hass.services.async_register(DOMAIN, SERVICE_CREATE_ALARM, _handle_service, schema=SERVICE_CREATE_ALARM_SCHEMA)

    intent.async_register(hass, CreateAlarmIntentHandler(store))


class CreateAlarmIntentHandler(intent.IntentHandler):
    """Handles the HassIL-matched `HaAlarmClockCreateAlarm` intent — see
    custom_sentences/en/ha_alarmclock.yaml for the sentences that trigger it. Voice input only
    carries a time and (optionally) a label; use the create_alarm service directly from an
    automation/script for repeat days.
    """

    intent_type = INTENT_CREATE_ALARM

    def __init__(self, store: AlarmClockStore) -> None:
        self._store = store

    async def async_handle(self, intent_obj: intent.Intent) -> intent.IntentResponse:
        hass = intent_obj.hass
        slots = self.async_validate_slots(intent_obj.slots)
        time_text = slots["ha_alarmclock_time"]["value"]
        label = slots.get("ha_alarmclock_label", {}).get("value", "")

        parsed = parse_time_text(time_text)
        response = intent_obj.create_response()
        if parsed is None:
            response.async_set_speech(f"Sorry, I didn't understand the time '{time_text}'.")
            return response

        try:
            device_id = _resolve_device_id(self._store, None)
        except HomeAssistantError as err:
            response.async_set_speech(str(err))
            return response

        _fire_create_alarm(hass, device_id, parsed[0], parsed[1], label, [])
        response.async_set_speech(f"Alarm set for {parsed[0]:02d}:{parsed[1]:02d}.")
        return response
