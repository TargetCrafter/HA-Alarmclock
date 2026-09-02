package com.targetcrafter.haalarmclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.targetcrafter.haalarmclock.HaAlarmClockApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "AlarmReceiver"

/** Fired by [android.app.AlarmManager] at the scheduled trigger time. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (alarmId < 0) {
            Log.w(TAG, "onReceive: missing/invalid EXTRA_ALARM_ID, ignoring")
            return
        }

        val isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)
        val app = HaAlarmClockApp.from(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarm = app.repository.getById(alarmId)
                if (alarm == null) {
                    Log.w(TAG, "onReceive: alarm $alarmId no longer exists in the DB, not ringing (isSnooze=$isSnooze)")
                    return@launch
                }
                if (!isSnooze && !alarm.enabled) {
                    Log.w(TAG, "onReceive: alarm $alarmId fired but is disabled, not ringing")
                    return@launch
                }

                app.repository.clearSnoozed(alarmId)
                app.scheduler.cancelUpcomingNotification(alarmId)

                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AlarmRingService::class.java)
                        .setAction(AlarmRingService.ACTION_START)
                        .putExtra(EXTRA_ALARM_ID, alarmId),
                )
                if (!isSnooze) {
                    app.repository.disableAfterOneOffFired(alarmId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
