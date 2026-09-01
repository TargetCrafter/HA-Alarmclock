package com.targetcrafter.haalarmclock.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Tracks whether any analog widget instance — opaque or transparent — currently exists, and
 * starts/stops [AnalogWidgetTickerService] (the foreground service that redraws the widget every
 * second so its second hand actually moves) accordingly.
 *
 * Centralized here so [AnalogClockWidgetProvider], [AnalogClockTransparentWidgetProvider], and
 * `BootReceiver` all agree on the same notion of "does an analog widget exist" — a provider's own
 * `onDisabled` only fires when *that specific* provider's last instance is removed, so checking
 * just one provider's ids from each callsite would stop the service for the other variant too
 * whenever both are present.
 */
object AnalogWidgetTicker {

    /** Starts the ticker service if at least one analog widget instance exists. Safe to call
     * unconditionally (from onEnabled/onUpdate/after reboot) — a no-op otherwise, and idempotent
     * if the service is already running. */
    fun ensureRunning(context: Context) {
        if (hasAnyInstance(context)) {
            ContextCompat.startForegroundService(context, Intent(context, AnalogWidgetTickerService::class.java))
        }
    }

    /** Stops the ticker service once no analog widget instance remains at all — see the class doc
     * for why a plain "stop" from one provider's onDisabled isn't enough on its own. */
    fun stopIfNoInstances(context: Context) {
        if (!hasAnyInstance(context)) {
            context.stopService(Intent(context, AnalogWidgetTickerService::class.java))
        }
    }

    fun hasAnyInstance(context: Context): Boolean = widgetIds(context).isNotEmpty()

    private fun widgetIds(context: Context): IntArray {
        val manager = AppWidgetManager.getInstance(context)
        return manager.getAppWidgetIds(ComponentName(context, AnalogClockWidgetProvider::class.java)) +
            manager.getAppWidgetIds(ComponentName(context, AnalogClockTransparentWidgetProvider::class.java))
    }
}
