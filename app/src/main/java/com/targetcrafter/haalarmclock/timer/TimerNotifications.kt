package com.targetcrafter.haalarmclock.timer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.targetcrafter.haalarmclock.NOTIFICATION_CHANNEL_TIMER
import com.targetcrafter.haalarmclock.R
import com.targetcrafter.haalarmclock.data.Timer
import com.targetcrafter.haalarmclock.ui.MainActivity
import java.util.concurrent.TimeUnit

/** A namespaced bit so timer notification IDs can't collide with the alarm/upcoming/HA-sync ones. */
private const val TIMER_NOTIFICATION_NAMESPACE = 1 shl 30

fun timerNotificationId(timerId: Long): Int = TIMER_NOTIFICATION_NAMESPACE or timerId.toInt()

/**
 * The "running"/"paused" timer notification is a plain (non-foreground) notification using
 * [NotificationCompat.Builder.setUsesChronometer] for a live countdown the system ticks on its
 * own — no service needs to stay alive just to count down. [com.targetcrafter.haalarmclock.timer.TimerService]
 * only exists for the ringing phase once a timer actually hits zero.
 */
class TimerNotifications(private val context: Context) {

    fun postRunning(timer: Timer) {
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_TIMER)
            .setContentTitle(label(timer))
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setWhen(timer.endAtMillis ?: System.currentTimeMillis())
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent(timer.id))
            .addAction(0, context.getString(R.string.timer_pause), actionPendingIntent(timer.id, TimerReceiver.ACTION_PAUSE))
            .addAction(0, context.getString(R.string.timer_cancel), actionPendingIntent(timer.id, TimerReceiver.ACTION_CANCEL))
            .build()
        NotificationManagerCompat.from(context).notify(timerNotificationId(timer.id), notification)
    }

    fun postPaused(timer: Timer) {
        val remainingText = formatDuration(timer.remainingMillisNow())
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_TIMER)
            .setContentTitle(label(timer))
            .setContentText(context.getString(R.string.timer_paused_at, remainingText))
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent(timer.id))
            .addAction(0, context.getString(R.string.timer_resume), actionPendingIntent(timer.id, TimerReceiver.ACTION_RESUME))
            .addAction(0, context.getString(R.string.timer_cancel), actionPendingIntent(timer.id, TimerReceiver.ACTION_CANCEL))
            .build()
        NotificationManagerCompat.from(context).notify(timerNotificationId(timer.id), notification)
    }

    fun cancel(timerId: Long) {
        NotificationManagerCompat.from(context).cancel(timerNotificationId(timerId))
    }

    private fun label(timer: Timer): String = timer.label.ifBlank { context.getString(R.string.timer_default_label) }

    private fun openAppPendingIntent(timerId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        timerNotificationId(timerId),
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun actionPendingIntent(timerId: Long, action: String): PendingIntent {
        val intent = Intent(context, TimerReceiver::class.java).setAction(action).putExtra(EXTRA_TIMER_ID, timerId)
        return PendingIntent.getBroadcast(
            context,
            timerNotificationId(timerId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

fun formatDuration(millis: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis.coerceAtLeast(0))
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
