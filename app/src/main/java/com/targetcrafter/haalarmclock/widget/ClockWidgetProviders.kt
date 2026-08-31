package com.targetcrafter.haalarmclock.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Digital clock widget: a self-updating [android.widget.TextClock] plus the next-alarm line. */
class DigitalClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAsync(context)
    }
}

/** Analog clock widget: a custom-drawn face (see [AnalogClockRenderer]) redrawn once a minute by
 * [AnalogWidgetTicker] for as long as at least one instance exists, plus the next-alarm line.
 */
class AnalogClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAsync(context)
        AnalogWidgetTicker.scheduleNextTick(context)
    }

    override fun onEnabled(context: Context) {
        AnalogWidgetTicker.scheduleNextTick(context)
    }

    override fun onDisabled(context: Context) {
        AnalogWidgetTicker.cancel(context)
    }
}

/** [AppWidgetProvider.onUpdate] runs on the main thread with a short execution budget, and
 * reading the next alarm needs the Room DAO — goAsync() extends that budget for the coroutine.
 */
private fun AppWidgetProvider.refreshAsync(context: Context) {
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            ClockWidgetUpdater.updateAll(context)
        } finally {
            pendingResult.finish()
        }
    }
}
