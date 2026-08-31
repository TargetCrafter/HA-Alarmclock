package com.targetcrafter.haalarmclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import com.targetcrafter.haalarmclock.HaAlarmClockApp
import com.targetcrafter.haalarmclock.ha.startHaSyncServiceIfConfigured
import com.targetcrafter.haalarmclock.widget.AnalogClockWidgetProvider
import com.targetcrafter.haalarmclock.widget.AnalogWidgetTicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** AlarmManager entries, the HA sync service, and the analog widget's per-minute tick all need to
 * be re-armed after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val app = HaAlarmClockApp.from(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.repository.rescheduleAll()
                app.timerRepository.rescheduleAll()
            } finally {
                pendingResult.finish()
            }
        }
        startHaSyncServiceIfConfigured(context)

        val hasAnalogWidget = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, AnalogClockWidgetProvider::class.java))
            .isNotEmpty()
        if (hasAnalogWidget) AnalogWidgetTicker.scheduleNextTick(context)
    }
}
