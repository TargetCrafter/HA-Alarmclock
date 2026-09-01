package com.targetcrafter.haalarmclock.widget

import android.app.Notification
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.targetcrafter.haalarmclock.NOTIFICATION_CHANNEL_WIDGET_TICKER
import com.targetcrafter.haalarmclock.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val NOTIFICATION_ID = 7

/**
 * Keeps the analog widget's second hand ticking every second — something `AlarmManager` alone
 * can't guarantee once the app is out of the foreground. Even `setExactAndAllowWhileIdle`, which
 * is meant to survive Doze, gets throttled by Android's background-app alarm limits to roughly
 * every few seconds once nothing keeps the app "active" — not a bug in how it was being called,
 * just the ceiling AlarmManager puts on a background app requesting sub-Doze-window precision.
 *
 * A foreground service isn't subject to that throttling: a plain coroutine `delay(1000)` loop
 * fires reliably on schedule for as long as it's alive. The cost is the low-priority, silent,
 * ongoing notification Android requires any foreground service to show (see
 * `NOTIFICATION_CHANNEL_WIDGET_TICKER`) and a little extra battery use — accepted here since a
 * live-moving second hand is the whole point of drawing one at all.
 *
 * Started (via [AnalogWidgetTicker.ensureRunning]) whenever an analog widget instance — opaque or
 * transparent — exists, and stops itself the moment none do, checked on every tick (not just at
 * start) so it also shuts down promptly if the last instance is removed while it's running.
 */
class AnalogWidgetTickerService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())

        lifecycleScope.launch {
            while (true) {
                if (!AnalogWidgetTicker.hasAnyInstance(this@AnalogWidgetTickerService)) {
                    stopSelf()
                    break
                }
                ClockWidgetUpdater.updateAll(this@AnalogWidgetTickerService)
                delay(1_000L)
            }
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_WIDGET_TICKER)
            .setContentTitle(getString(R.string.widget_ticker_notification_title))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()
}
