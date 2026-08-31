package com.targetcrafter.haalarmclock.ha

/** A command Home Assistant sent to this device, received over the WebSocket event subscription. */
sealed interface HaCommand {
    data class SetAlarmEnabled(val alarmId: Long, val enabled: Boolean) : HaCommand
    data object Snooze : HaCommand
    data object Dismiss : HaCommand
    /** From the `ha_alarmclock.create_alarm` service / Assist sentence (see the integration's
     * assist.py) — inserted as a brand-new alarm, same as tapping + in the app. [repeatDaysMask]
     * uses the same bit layout as [com.targetcrafter.haalarmclock.data.Alarm.repeatDaysMask]. */
    data class CreateAlarm(val hour: Int, val minute: Int, val label: String, val repeatDaysMask: Int) : HaCommand
}

enum class HaConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }
