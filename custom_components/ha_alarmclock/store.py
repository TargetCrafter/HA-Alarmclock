"""In-memory state for every phone that has pushed alarm data to this HA instance.

State lives only in memory (matching the integration's "local_push" nature): it's rebuilt from
the next sync push after a Home Assistant restart, rather than persisted to disk. Entities read
straight from this store on demand and are told to refresh via a dispatcher signal.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass
class AlarmInfo:
    id: int
    label: str
    time: str
    enabled: bool
    repeat: str
    next_trigger: str | None = None
    snoozed_until: str | None = None


@dataclass
class RingingInfo:
    active: bool = False
    alarm_id: int | None = None
    label: str = ""
    time: str = ""


@dataclass
class NextAlarmInfo:
    alarm_id: int | None = None
    label: str = ""
    trigger_at: str | None = None


@dataclass
class DeviceState:
    device_id: str
    device_name: str
    alarms: dict[int, AlarmInfo] = field(default_factory=dict)
    ringing: RingingInfo = field(default_factory=RingingInfo)
    next_alarm: NextAlarmInfo = field(default_factory=NextAlarmInfo)
    # slot -> alarm_id, for the currently-existing alarms. Slots are small, stable numbers
    # (1, 2, 3, ...) that entities are keyed on instead of the app's own (ever-growing) alarm id,
    # and get reused for the next new alarm once freed — see AlarmClockStore._reassign_slots.
    alarm_slots: dict[int, int] = field(default_factory=dict)


class AlarmClockStore:
    """Holds the latest known state for every device (phone) syncing to this config entry."""

    def __init__(self) -> None:
        self.devices: dict[str, DeviceState] = {}

    def apply_sync(self, payload: dict[str, Any]) -> tuple[DeviceState, bool]:
        """Merge a sync payload into the store. Returns (device_state, is_new_device)."""
        device_id = str(payload["device_id"])
        is_new = device_id not in self.devices
        device = self.devices.setdefault(
            device_id,
            DeviceState(device_id=device_id, device_name=str(payload.get("device_name") or device_id)),
        )
        device.device_name = str(payload.get("device_name") or device.device_name)

        device.alarms = {
            int(alarm["id"]): AlarmInfo(
                id=int(alarm["id"]),
                label=str(alarm.get("label") or ""),
                time=str(alarm.get("time") or ""),
                enabled=bool(alarm.get("enabled", False)),
                repeat=str(alarm.get("repeat") or ""),
                next_trigger=alarm.get("next_trigger"),
                snoozed_until=alarm.get("snoozed_until"),
            )
            for alarm in payload.get("alarms", [])
            if "id" in alarm
        }

        ringing = payload.get("ringing") or {}
        device.ringing = RingingInfo(
            active=bool(ringing.get("active", False)),
            alarm_id=ringing.get("alarm_id"),
            label=str(ringing.get("label") or ""),
            time=str(ringing.get("time") or ""),
        )

        next_alarm = payload.get("next_alarm") or {}
        device.next_alarm = NextAlarmInfo(
            alarm_id=next_alarm.get("alarm_id"),
            label=str(next_alarm.get("label") or ""),
            trigger_at=next_alarm.get("trigger_at"),
        )

        self._reassign_slots(device)

        return device, is_new

    @staticmethod
    def _reassign_slots(device: DeviceState) -> None:
        """Keeps device.alarm_slots pointing at whatever alarms currently exist, reusing the
        smallest free slot number for any alarm that doesn't have one yet. A slot's entities stay
        registered in HA (as unavailable) when its alarm is deleted, ready to be picked up by the
        next new alarm, rather than each new alarm ever created minting a fresh entity forever.
        """
        current_ids = set(device.alarms.keys())

        for slot, alarm_id in list(device.alarm_slots.items()):
            if alarm_id not in current_ids:
                del device.alarm_slots[slot]

        assigned_ids = set(device.alarm_slots.values())
        used_slots = set(device.alarm_slots.keys())
        next_slot = 1
        for alarm_id in sorted(current_ids - assigned_ids):
            while next_slot in used_slots:
                next_slot += 1
            device.alarm_slots[next_slot] = alarm_id
            used_slots.add(next_slot)
