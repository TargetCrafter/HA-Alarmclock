package com.targetcrafter.haalarmclock.alarm

import com.targetcrafter.haalarmclock.data.Alarm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide holder for "which alarm is currently ringing", if any. Lets the ringing
 * notification/activity, the MQTT sync layer, and MQTT-originated snooze/dismiss commands all
 * observe and act on the same state without binding to each other directly.
 */
object RingingState {
    private val _current = MutableStateFlow<Alarm?>(null)
    val current: StateFlow<Alarm?> = _current.asStateFlow()

    fun setRinging(alarm: Alarm?) {
        _current.value = alarm
    }
}
