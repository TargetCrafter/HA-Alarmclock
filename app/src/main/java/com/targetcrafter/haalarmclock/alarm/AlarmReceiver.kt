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

        // Start the ringing service before touching the database — the ordering matters, and used
        // to be the other way around. Everything below is a disk read, and when this alarm is the
        // thing that woke a process Android killed hours ago (the app hasn't been opened since
        // bedtime), those reads come off cold storage while the device is still spinning up out of
        // Doze. Loading the alarm first pushed the service start behind all of it, and the service
        // then has only five seconds from that call to reach startForeground() before the OS kills
        // the app instead of ringing. Starting first also raises this process's priority for the
        // rest of the work here. Whether the alarm still exists and is enabled is now checked
        // inside the service, which has to load it anyway.
        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmRingService::class.java)
                .setAction(AlarmRingService.ACTION_START)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_IS_SNOOZE, isSnooze),
        )

        val app = HaAlarmClockApp.from(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.repository.clearSnoozed(alarmId)
                app.scheduler.cancelUpcomingNotification(alarmId)
                if (!isSnooze) {
                    app.repository.disableAfterOneOffFired(alarmId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
