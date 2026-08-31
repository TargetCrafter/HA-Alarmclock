package com.targetcrafter.haalarmclock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.targetcrafter.haalarmclock.HaAlarmClockApp
import com.targetcrafter.haalarmclock.R
import com.targetcrafter.haalarmclock.data.Alarm
import com.targetcrafter.haalarmclock.ui.MainActivity
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pushes the "Next: HH:mm" line into every clock widget instance. The clocks themselves
 * (TextClock/AnalogClock) tick on their own with no code involved — this only covers the one
 * piece of state RemoteViews can't refresh by itself.
 */
object ClockWidgetUpdater {

    suspend fun updateAll(context: Context) {
        val app = HaAlarmClockApp.from(context)
        val alarms = app.repository.alarms.first()
        val next = alarms.filter { it.enabled }.minByOrNull { it.nextTriggerAtMillis() }
        val label = next?.let { formatNextAlarm(context, it) } ?: context.getString(R.string.widget_no_alarm)

        val manager = AppWidgetManager.getInstance(context)
        updateWidgets(context, manager, DigitalClockWidgetProvider::class.java, R.layout.widget_digital_clock, label)
        updateWidgets(context, manager, AnalogClockWidgetProvider::class.java, R.layout.widget_analog_clock, label)
    }

    private fun updateWidgets(context: Context, manager: AppWidgetManager, provider: Class<*>, layoutId: Int, nextAlarmLabel: String) {
        val ids = manager.getAppWidgetIds(ComponentName(context, provider))
        if (ids.isEmpty()) return

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        for (id in ids) {
            val views = RemoteViews(context.packageName, layoutId)
            views.setTextViewText(R.id.widget_next_alarm, nextAlarmLabel)
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            manager.updateAppWidget(id, views)
        }
    }

    private fun formatNextAlarm(context: Context, alarm: Alarm): String {
        val time = DateTimeFormatter.ofPattern("HH:mm")
            .format(Instant.ofEpochMilli(alarm.nextTriggerAtMillis()).atZone(ZoneId.systemDefault()))
        val withLabel = if (alarm.label.isBlank()) time else "${alarm.label} · $time"
        return context.getString(R.string.widget_next_alarm, withLabel)
    }
}
