package com.targetcrafter.haalarmclock.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the startup schedule check has found over time. [lastHealthyAtMillis] and
 * [lastDropAtMillis] are epoch millis, 0 when it hasn't happened yet. */
data class ScheduleAuditState(
    val dropCount: Int = 0,
    val lastDropAtMillis: Long = 0L,
    val lastHealthyAtMillis: Long = 0L,
) {
    val hasDrops: Boolean get() = dropCount > 0
}

/**
 * Records each time Android was found to have dropped this app's alarm schedule.
 *
 * Re-arming on app start repairs a dropped schedule — but on its own it also destroys the evidence
 * that anything went wrong: by the time anyone opens the app to look, it has already been fixed and
 * everything reads healthy. That is precisely the case where knowing *whether it happened at all*
 * is the whole diagnosis, because a dropped schedule means the OS killed the app, while an alarm
 * that was registered and still didn't ring means a bug in this app. Writing the finding down
 * before the repair is what makes those two distinguishable after the fact.
 */
class AlarmScheduleAudit(context: Context) {

    private val prefs = context.getSharedPreferences("alarm_schedule_audit", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<ScheduleAuditState> = _state.asStateFlow()

    /** Records what the startup check found. Call this *before* re-arming, not after. */
    fun recordStartupCheck(scheduleWasDropped: Boolean, atMillis: Long = System.currentTimeMillis()) {
        val current = _state.value
        val next = if (scheduleWasDropped) {
            current.copy(dropCount = current.dropCount + 1, lastDropAtMillis = atMillis)
        } else {
            current.copy(lastHealthyAtMillis = atMillis)
        }
        prefs.edit()
            .putInt(KEY_DROP_COUNT, next.dropCount)
            .putLong(KEY_LAST_DROP, next.lastDropAtMillis)
            .putLong(KEY_LAST_HEALTHY, next.lastHealthyAtMillis)
            .apply()
        _state.value = next
    }

    fun clear() {
        prefs.edit().clear().apply()
        _state.value = ScheduleAuditState()
    }

    private fun read() = ScheduleAuditState(
        dropCount = prefs.getInt(KEY_DROP_COUNT, 0),
        lastDropAtMillis = prefs.getLong(KEY_LAST_DROP, 0L),
        lastHealthyAtMillis = prefs.getLong(KEY_LAST_HEALTHY, 0L),
    )

    companion object {
        private const val KEY_DROP_COUNT = "drop_count"
        private const val KEY_LAST_DROP = "last_drop_at"
        private const val KEY_LAST_HEALTHY = "last_healthy_at"
    }
}
