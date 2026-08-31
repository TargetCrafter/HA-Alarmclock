package com.targetcrafter.haalarmclock.data

import androidx.room.TypeConverter

/** Room has no built-in enum support; this is the whole of what's needed for [TimerState]. */
class Converters {
    @TypeConverter
    fun fromTimerState(state: TimerState): String = state.name

    @TypeConverter
    fun toTimerState(value: String): TimerState = TimerState.valueOf(value)
}
