package com.targetcrafter.haalarmclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.targetcrafter.haalarmclock.HaAlarmClockApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fired by [android.app.AlarmManager] at the scheduled trigger time. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (alarmId < 0) return

        val isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)
        val app = HaAlarmClockApp.from(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarm = app.repository.getById(alarmId) ?: return@launch
                if (!isSnooze && !alarm.enabled) return@launch

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
