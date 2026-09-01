package com.targetcrafter.haalarmclock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
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
 * Pushes the current appearance (colors) and next-alarm info into every clock widget instance —
 * both the normal (background-filled) and transparent (no-background) variant of each. The
 * digital widgets' [android.widget.TextClock] ticks on its own and show next-alarm info as a
 * separate text row; the analog widgets' whole face — including a centered next-alarm badge, see
 * [AnalogClockRenderer] — is redrawn here as one bitmap and needs a fresh push every tick (see
 * [AnalogWidgetTicker] for what drives that).
 */
object ClockWidgetUpdater {

    suspend fun updateAll(context: Context) {
        val app = HaAlarmClockApp.from(context)
        val alarms = app.repository.alarms.first()
        val appearance = app.widgetAppearanceStore.appearance.first()
        val next = alarms.filter { it.enabled }.minByOrNull { it.nextTriggerAtMillis() }

        val manager = AppWidgetManager.getInstance(context)
        val digitalLabel = next?.let { formatNextAlarmWithLabel(context, it) }
        updateDigitalWidgets(context, manager, appearance, digitalLabel, DigitalClockWidgetProvider::class.java, applyBackground = true)
        updateDigitalWidgets(context, manager, appearance, digitalLabel, DigitalClockTransparentWidgetProvider::class.java, applyBackground = false)

        // The analog badge shows a bare time next to an alarm-bell glyph — the glyph already says
        // "alarm", so unlike the digital widget's line, no "Next:" prefix or alarm label is shown.
        val analogTime = next?.let { formatTimeOnly(it) }
        val now = LocalTime.now()
        val face = AnalogClockRenderer.render(
            now.hour, now.minute, now.second,
            appearance.foregroundColor, appearance.backgroundColor, analogTime,
        )
        updateAnalogWidgets(context, manager, appearance, face, AnalogClockWidgetProvider::class.java, applyBackground = true)
        updateAnalogWidgets(context, manager, appearance, face, AnalogClockTransparentWidgetProvider::class.java, applyBackground = false)
    }

    private fun updateDigitalWidgets(
        context: Context,
        manager: AppWidgetManager,
        appearance: WidgetAppearance,
        nextAlarmLabel: String?,
        provider: Class<out AppWidgetProvider>,
        applyBackground: Boolean,
    ) {
        val ids = manager.getAppWidgetIds(ComponentName(context, provider))
        if (ids.isEmpty()) return

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_digital_clock)
            applyCommon(context, views, appearance, applyBackground)
            if (nextAlarmLabel != null) {
                views.setViewVisibility(R.id.widget_next_alarm, View.VISIBLE)
                views.setTextViewText(R.id.widget_next_alarm, nextAlarmLabel)
                views.setTextColor(R.id.widget_next_alarm, withAlpha(appearance.foregroundColor, 0.7f))
            } else {
                views.setViewVisibility(R.id.widget_next_alarm, View.GONE)
            }
            views.setTextColor(R.id.widget_digital_time, appearance.foregroundColor)
            manager.updateAppWidget(id, views)
        }
    }

    private fun updateAnalogWidgets(
        context: Context,
        manager: AppWidgetManager,
        appearance: WidgetAppearance,
        face: Bitmap,
        provider: Class<out AppWidgetProvider>,
        applyBackground: Boolean,
    ) {
        val ids = manager.getAppWidgetIds(ComponentName(context, provider))
        if (ids.isEmpty()) return

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_analog_clock)
            applyCommon(context, views, appearance, applyBackground)
            views.setImageViewBitmap(R.id.widget_analog_face, face)
            manager.updateAppWidget(id, views)
        }
    }

    private fun applyCommon(context: Context, views: RemoteViews, appearance: WidgetAppearance, applyBackground: Boolean) {
        // Losing the widget's rounded corners on API < 31 is the tradeoff for user-choosable
        // colors here — see the widget layouts, which no longer carry a static shape background.
        // API 31+ launchers clip/round every widget's outer bounds automatically regardless.
        // The transparent variants skip this entirely, leaving the wallpaper showing through.
        views.setInt(R.id.widget_root, "setBackgroundColor", if (applyBackground) appearance.backgroundColor else Color.TRANSPARENT)
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

    private fun formatTimeOnly(alarm: Alarm): String =
        DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(alarm.nextTriggerAtMillis()).atZone(ZoneId.systemDefault()))

    private fun formatNextAlarmWithLabel(context: Context, alarm: Alarm): String {
        val time = formatTimeOnly(alarm)
        val withLabel = if (alarm.label.isBlank()) time else "${alarm.label} · $time"
        return context.getString(R.string.widget_next_alarm, withLabel)
    }
}
