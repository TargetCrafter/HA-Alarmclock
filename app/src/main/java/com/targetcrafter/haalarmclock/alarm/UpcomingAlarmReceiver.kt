package com.targetcrafter.haalarmclock.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.targetcrafter.haalarmclock.HaAlarmClockApp
import com.targetcrafter.haalarmclock.NOTIFICATION_CHANNEL_UPCOMING
import com.targetcrafter.haalarmclock.R
import com.targetcrafter.haalarmclock.data.Alarm
import com.targetcrafter.haalarmclock.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fired [UPCOMING_NOTIFICATION_LEAD_MILLIS] (an hour) before an alarm rings, to post an ongoing,
 * non-dismissable notification with a live countdown (via
 * [NotificationCompat.Builder.setUsesChronometer]) and a "Skip" action, so the alarm can be
 * cancelled from the shade if the user is already awake, and stays visible the whole hour instead
 * of just flashing by. Also handles that Skip action itself (see [ACTION_SKIP]).
 */
class UpcomingAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val originalTriggerAt = intent.getLongExtra(EXTRA_ORIGINAL_TRIGGER_AT_MILLIS, -1L)
        if (alarmId < 0 || originalTriggerAt < 0) return

        val app = HaAlarmClockApp.from(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarm = app.repository.getById(alarmId) ?: return@launch
                if (intent.action == ACTION_SKIP) {
                    handleSkip(app, alarm, originalTriggerAt)
                } else if (alarm.enabled) {
                    postNotification(context, alarm, originalTriggerAt)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleSkip(app: HaAlarmClockApp, alarm: Alarm, originalTriggerAtMillis: Long) {
        if (alarm.isRepeating) {
            // Reschedules the main trigger (and a fresh upcoming-notification trigger for it) to
            // the occurrence after this one; only dismiss the notification that's currently shown,
            // not the new trigger scheduleSkippingOccurrenceAt just set up.
            app.scheduler.scheduleSkippingOccurrenceAt(alarm, originalTriggerAtMillis)
            app.scheduler.dismissUpcomingNotification(alarm.id)
        } else {
            // save() disables the alarm, which cancels everything (main, snooze, upcoming) for it.
            app.repository.save(alarm.copy(enabled = false))
        }
    }

    private fun postNotification(context: Context, alarm: Alarm, originalTriggerAtMillis: Long) {
        val notificationId = upcomingNotificationId(alarm.id)

        val skipIntent = Intent(context, UpcomingAlarmReceiver::class.java)
            .setAction(ACTION_SKIP)
            .putExtra(EXTRA_ALARM_ID, alarm.id)
            .putExtra(EXTRA_ORIGINAL_TRIGGER_AT_MILLIS, originalTriggerAtMillis)
        val skipPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val label = alarm.label.ifBlank { context.getString(R.string.app_name) }
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_UPCOMING)
            .setContentTitle(context.getString(R.string.upcoming_alarm_title, label))
            .setContentText(String.format("%02d:%02d", alarm.hour, alarm.minute))
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setWhen(originalTriggerAtMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPendingIntent)
            .addAction(0, context.getString(R.string.skip_alarm), skipPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    companion object {
        const val ACTION_SKIP = "com.targetcrafter.haalarmclock.action.SKIP_UPCOMING"
    }
}
