package com.targetcrafter.haalarmclock.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val REQUEST_CODE = 8823

/** Redraws the analog widget's bitmap once a minute — the one piece of state a bitmap-based
 * "clock" can't update on its own the way TextClock/the old AnalogClock did. Only ever armed
 * while at least one analog widget instance exists (from AnalogClockWidgetProvider's
 * onUpdate/onEnabled) and cancelled the moment the last one is removed (onDisabled), so it never
 * wakes the device for a widget nobody has on their home screen.
 */
object AnalogWidgetTicker {

    fun scheduleNextTick(context: Context) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val now = System.currentTimeMillis()
        val nextMinuteBoundary = now - (now % 60_000L) + 60_000L
        val pi = pendingIntent(context)
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMinuteBoundary, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMinuteBoundary, pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService<AlarmManager>()?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AnalogWidgetTickReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class AnalogWidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, AnalogClockWidgetProvider::class.java))
        if (ids.isEmpty()) return // onDisabled should already have cancelled us; be defensive anyway

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ClockWidgetUpdater.updateAll(context)
            } finally {
                pendingResult.finish()
            }
        }
        AnalogWidgetTicker.scheduleNextTick(context)
    }
}
