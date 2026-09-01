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

/** Redraws the analog widget's bitmap so its second hand can tick and its hour/minute hands and
 * next-alarm line stay current — the one piece of state a bitmap-based "clock" can't update on
 * its own the way TextClock/the old AnalogClock did. Only ever armed while at least one analog
 * widget instance — of either the opaque or transparent variant, see [widgetIds] — exists (from
 * AnalogClockWidgetProvider/AnalogClockTransparentWidgetProvider's onUpdate/onEnabled) and
 * cancelled once the last one of *both* is removed ([cancelIfNoInstances]), so it never wakes the
 * device for a widget nobody has on their home screen.
 *
 * Always requests the next tick one second out via [AlarmManager.setExactAndAllowWhileIdle], but
 * deliberately doesn't try to detect screen-on/off itself: `ACTION_SCREEN_ON`/`_OFF` can't be
 * received by a manifest-declared receiver at all (Android has always required a live,
 * dynamically-registered one for those two, broadcast-storm reasons), and this app has no
 * always-running component to hold one. Relying on `setExactAndAllowWhileIdle`'s own behavior
 * gets the right outcome anyway: while the screen is on there's no Doze throttling, so ticks land
 * on schedule and the second hand moves smoothly; the moment the screen goes off and the device
 * settles into Doze, the system itself defers "while idle" alarms to widening maintenance
 * windows — no custom battery-saving logic needed, and it self-corrects back to smooth ticking
 * the instant the screen (and thus Doze) comes back.
 */
object AnalogWidgetTicker {

    fun scheduleNextTick(context: Context) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val nextTrigger = System.currentTimeMillis() + 1_000L
        val pi = pendingIntent(context)
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService<AlarmManager>()?.cancel(pendingIntent(context))
    }

    /** Only actually cancels once no analog widget instance remains at all. A plain [cancel] from
     * one provider's onDisabled would otherwise stop ticking for the other variant too if both
     * are present — onDisabled only fires when *that specific* provider's last instance goes. */
    fun cancelIfNoInstances(context: Context) {
        if (!hasAnyInstance(context)) cancel(context)
    }

    fun hasAnyInstance(context: Context): Boolean = widgetIds(context).isNotEmpty()

    private fun widgetIds(context: Context): IntArray {
        val manager = AppWidgetManager.getInstance(context)
        return manager.getAppWidgetIds(ComponentName(context, AnalogClockWidgetProvider::class.java)) +
            manager.getAppWidgetIds(ComponentName(context, AnalogClockTransparentWidgetProvider::class.java))
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
        if (!AnalogWidgetTicker.hasAnyInstance(context)) return // onDisabled should already have cancelled us; be defensive anyway

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
