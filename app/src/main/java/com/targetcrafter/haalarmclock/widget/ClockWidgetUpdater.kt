package com.targetcrafter.haalarmclock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.RemoteViews
import com.targetcrafter.haalarmclock.HaAlarmClockApp
import com.targetcrafter.haalarmclock.R
import com.targetcrafter.haalarmclock.data.Alarm
import com.targetcrafter.haalarmclock.data.WidgetAppearance
import com.targetcrafter.haalarmclock.ui.MainActivity
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pushes the current appearance (colors) and the "Next: HH:mm" line into every clock widget
 * instance — both the normal (background-filled) and transparent (no-background) variant of
 * each. The digital widgets' [android.widget.TextClock] ticks on its own; the analog widgets'
 * face is redrawn here (see [AnalogClockRenderer]) and needs a fresh push every tick — see
 * [AnalogWidgetTicker] for what drives that.
 */
object ClockWidgetUpdater {

    suspend fun updateAll(context: Context) {
        val app = HaAlarmClockApp.from(context)
        val alarms = app.repository.alarms.first()
        val appearance = app.widgetAppearanceStore.appearance.first()
        val next = alarms.filter { it.enabled }.minByOrNull { it.nextTriggerAtMillis() }
        val label = next?.let { formatNextAlarm(context, it) } ?: context.getString(R.string.widget_no_alarm)

        val manager = AppWidgetManager.getInstance(context)
        updateDigitalWidgets(context, manager, appearance, label, DigitalClockWidgetProvider::class.java, applyBackground = true)
        updateDigitalWidgets(context, manager, appearance, label, DigitalClockTransparentWidgetProvider::class.java, applyBackground = false)

        val now = LocalTime.now()
        val face = AnalogClockRenderer.render(now.hour, now.minute, now.second, appearance.foregroundColor)
        updateAnalogWidgets(context, manager, appearance, label, face, AnalogClockWidgetProvider::class.java, applyBackground = true)
        updateAnalogWidgets(context, manager, appearance, label, face, AnalogClockTransparentWidgetProvider::class.java, applyBackground = false)
    }

    private fun updateDigitalWidgets(
        context: Context,
        manager: AppWidgetManager,
        appearance: WidgetAppearance,
        nextAlarmLabel: String,
        provider: Class<out AppWidgetProvider>,
        applyBackground: Boolean,
    ) {
        val ids = manager.getAppWidgetIds(ComponentName(context, provider))
        if (ids.isEmpty()) return

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_digital_clock)
            applyCommon(context, views, appearance, nextAlarmLabel, applyBackground)
            views.setTextColor(R.id.widget_digital_time, appearance.foregroundColor)
            manager.updateAppWidget(id, views)
        }
    }

    private fun updateAnalogWidgets(
        context: Context,
        manager: AppWidgetManager,
        appearance: WidgetAppearance,
        nextAlarmLabel: String,
        face: Bitmap,
        provider: Class<out AppWidgetProvider>,
        applyBackground: Boolean,
    ) {
        val ids = manager.getAppWidgetIds(ComponentName(context, provider))
        if (ids.isEmpty()) return

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_analog_clock)
            applyCommon(context, views, appearance, nextAlarmLabel, applyBackground)
            views.setImageViewBitmap(R.id.widget_analog_face, face)
            manager.updateAppWidget(id, views)
        }
    }

    private fun applyCommon(
        context: Context,
        views: RemoteViews,
        appearance: WidgetAppearance,
        nextAlarmLabel: String,
        applyBackground: Boolean,
    ) {
        // Losing the widget's rounded corners on API < 31 is the tradeoff for user-choosable
        // colors here — see the widget layouts, which no longer carry a static shape background.
        // API 31+ launchers clip/round every widget's outer bounds automatically regardless.
        // The transparent variants skip this entirely, leaving the wallpaper showing through.
        views.setInt(R.id.widget_root, "setBackgroundColor", if (applyBackground) appearance.backgroundColor else Color.TRANSPARENT)
        views.setTextViewText(R.id.widget_next_alarm, nextAlarmLabel)
        views.setTextColor(R.id.widget_next_alarm, withAlpha(appearance.foregroundColor, 0.7f))
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }

    private fun withAlpha(color: Int, alphaFraction: Float): Int {
        val alpha = (Color.alpha(color) * alphaFraction).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun formatNextAlarm(context: Context, alarm: Alarm): String {
        val time = DateTimeFormatter.ofPattern("HH:mm")
            .format(Instant.ofEpochMilli(alarm.nextTriggerAtMillis()).atZone(ZoneId.systemDefault()))
        val withLabel = if (alarm.label.isBlank()) time else "${alarm.label} · $time"
        return context.getString(R.string.widget_next_alarm, withLabel)
    }
}
