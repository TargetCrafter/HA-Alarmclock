package com.targetcrafter.haalarmclock.ha

/** A command Home Assistant sent to this device, received over the WebSocket event subscription. */
sealed interface HaCommand {
    data class SetAlarmEnabled(val alarmId: Long, val enabled: Boolean) : HaCommand
    data object Snooze : HaCommand
    data object Dismiss : HaCommand
}

enum class HaConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }
