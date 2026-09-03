package com.targetcrafter.haalarmclock.alarm

import android.app.AlarmManager
import android.content.Context
import androidx.core.content.getSystemService
import com.targetcrafter.haalarmclock.data.Alarm

/**
 * Whether Android itself still holds the alarm this app thinks is next.
 *
 * The app's own database is not evidence that anything is scheduled. `AlarmManager` entries are
 * dropped when an app is force-stopped — which is what OEM battery managers do to apps they decide
 * are idle — and nothing tells the app it happened. The alarm list keeps showing the alarm as
 * enabled while the OS has no record of it, and the first sign of trouble is not waking up.
 *
 * [AlarmManager.getNextAlarmClock] is the same source Android uses for the status-bar alarm icon,
 * so it reports the next alarm clock across *all* apps. That makes it conclusive in only two
 * directions, which is why [Inconclusive] exists rather than a reassuring guess.
 */
sealed interface ScheduleHealth {
    /** Nothing is enabled, so there is nothing to verify. */
    data object NoAlarms : ScheduleHealth

    /** The OS reports an alarm clock at exactly the time we expect. */
    data object Registered : ScheduleHealth

    /** The OS reports no alarm clock at all, or one only *after* ours — either way ours is gone. */
    data class Missing(val expectedAtMillis: Long) : ScheduleHealth

    /** Some other app has a sooner alarm clock, which hides ours from this check. */
    data object Inconclusive : ScheduleHealth
}

/** Tolerance for matching the OS's trigger time against ours; they are set from the same value, so
 * this only absorbs rounding rather than any real drift. */
private const val MATCH_TOLERANCE_MILLIS = 1_000L

fun checkScheduleHealth(context: Context, alarms: List<Alarm>): ScheduleHealth {
    val next = alarms
        .filter { it.enabled && it.hasValidTime }
        .minByOrNull { it.nextTriggerAtMillis() }
        ?: return ScheduleHealth.NoAlarms

    val expected = next.nextTriggerAtMillis()
    val system = context.getSystemService<AlarmManager>()?.nextAlarmClock
        ?: return ScheduleHealth.Missing(expected)

    return when {
        kotlin.math.abs(system.triggerTime - expected) <= MATCH_TOLERANCE_MILLIS -> ScheduleHealth.Registered
        // Ours would have been reported if it were registered and sooner than what we got back.
        system.triggerTime > expected -> ScheduleHealth.Missing(expected)
        else -> ScheduleHealth.Inconclusive
    }
}
