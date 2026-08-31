package com.targetcrafter.haalarmclock.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TimerState { RUNNING, PAUSED, FINISHED }

/**
 * A single countdown timer. While [state] is RUNNING, [endAtMillis] is the absolute time it's
 * due to hit zero (an AlarmManager trigger is armed for it — see TimerScheduler); while PAUSED,
 * [remainingMillis] holds what was left instead, and no trigger is armed. FINISHED means it has
 * hit zero and is ringing/awaiting dismissal.
 */
@Entity(tableName = "timers")
data class Timer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String = "",
    val durationMillis: Long,
    val state: TimerState = TimerState.RUNNING,
    val endAtMillis: Long? = null,
    val remainingMillis: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
) {
    fun remainingMillisNow(nowMillis: Long = System.currentTimeMillis()): Long = when (state) {
        TimerState.RUNNING -> ((endAtMillis ?: nowMillis) - nowMillis).coerceAtLeast(0)
        TimerState.PAUSED -> remainingMillis ?: durationMillis
        TimerState.FINISHED -> 0L
    }
}
