"""Shared DeviceInfo builder so every entity for a phone groups under one HA device."""
from __future__ import annotations

from homeassistant.helpers.entity import DeviceInfo

from .const import DOMAIN, MANUFACTURER, MODEL
from .store import AlarmClockStore


def build_device_info(store: AlarmClockStore, device_id: str) -> DeviceInfo:
    device = store.devices.get(device_id)
    name = device.device_name if device else device_id
    return DeviceInfo(
        identifiers={(DOMAIN, device_id)},
        name=name,
        manufacturer=MANUFACTURER,
        model=MODEL,
    )
