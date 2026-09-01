package com.targetcrafter.haalarmclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.targetcrafter.haalarmclock.data.Alarm
import com.targetcrafter.haalarmclock.ui.MainActivity
import java.time.Instant
import java.time.ZoneId

const val EXTRA_ALARM_ID = "alarm_id"
const val EXTRA_IS_SNOOZE = "is_snooze"
const val EXTRA_ORIGINAL_TRIGGER_AT_MILLIS = "original_trigger_at_millis"

const val UPCOMING_NOTIFICATION_LEAD_MILLIS = 60 * 60 * 1000L

/**
 * Schedules alarms with [AlarmManager.setAlarmClock], the API meant for alarm-clock apps: it is
 * always exact (no "schedule exact alarms" permission dance) and shows the alarm-clock icon in
 * the status bar, matching stock Clock app behavior.
 *
 * Also schedules a separate "upcoming alarm" notification [UPCOMING_NOTIFICATION_LEAD_MILLIS]
 * before the real trigger, so the alarm can be skipped from the notification shade if the user
 * wakes up early. That one deliberately does *not* use setAlarmClock (which would make the
 * system's "next alarm" indicator show the notification's fire time instead of the real one) —
 * it uses setExactAndAllowWhileIdle where the exact-alarm permission is granted, else falls back
 * to an inexact-but-Doze-aware trigger, since a few minutes of slack on an informational heads-up
 * doesn't matter.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager
        get() = context.getSystemService<AlarmManager>() ?: error("AlarmManager unavailable")

    /** Schedules [alarm] to fire at [explicitTriggerAtMillis], or its next natural occurrence if null. */
    fun schedule(alarm: Alarm, explicitTriggerAtMillis: Long? = null) {
        if (!alarm.enabled) {
            cancel(alarm)
            return
        }
        val triggerAt = explicitTriggerAtMillis ?: alarm.nextTriggerAtMillis()
        val showIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, showIntent),
            operationPendingIntent(alarm.id),
        )
        scheduleUpcomingNotification(alarm.id, triggerAt)
    }

    fun cancel(alarm: Alarm) {
        alarmManager.cancel(operationPendingIntent(alarm.id))
        cancelSnooze(alarm.id)
        cancelUpcomingNotification(alarm.id)
    }

    /** Schedules a one-shot snooze trigger without disturbing the alarm's regular schedule. */
    fun scheduleSnooze(alarmId: Long, triggerAtMillis: Long) {
        val showIntent = PendingIntent.getActivity(
            context,
            snoozeRequestCode(alarmId),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_IS_SNOOZE, true)
        val pi = PendingIntent.getBroadcast(
            context,
            snoozeRequestCode(alarmId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent), pi)
    }

    fun cancelSnooze(alarmId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            snoozeRequestCode(alarmId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pi)
    }

    /** Computes when [alarm] would next fire after the occurrence at [skippedTriggerAtMillis] and
     * (re)schedules it there — used when the user skips the upcoming occurrence from its notification.
     */
    fun scheduleSkippingOccurrenceAt(alarm: Alarm, skippedTriggerAtMillis: Long) {
        val skippedZoned = Instant.ofEpochMilli(skippedTriggerAtMillis).atZone(ZoneId.systemDefault())
        schedule(alarm, explicitTriggerAtMillis = alarm.nextTriggerAtMillis(from = skippedZoned))
    }

    private fun scheduleUpcomingNotification(alarmId: Long, mainTriggerAtMillis: Long) {
        val notifyAt = mainTriggerAtMillis - UPCOMING_NOTIFICATION_LEAD_MILLIS
        if (notifyAt <= System.currentTimeMillis()) {
            cancelUpcomingNotification(alarmId)
            return
        }
        val pi = upcomingNotificationPendingIntent(alarmId, mainTriggerAtMillis)
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notifyAt, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notifyAt, pi)
        }
    }

    /** Cancels the AlarmManager trigger *and* dismisses any currently-shown notification — use
     * when the alarm itself is being disabled/deleted, so no future notification should fire. */
    fun cancelUpcomingNotification(alarmId: Long) {
        alarmManager.cancel(upcomingNotificationPendingIntent(alarmId, mainTriggerAtMillis = 0L))
        dismissUpcomingNotification(alarmId)
    }

    /** Dismisses a currently-shown upcoming-alarm notification without touching any future
     * trigger — use after "Skip" on a repeating alarm, where [scheduleSkippingOccurrenceAt] has
     * already scheduled a fresh notification for the *next* occurrence that must not be cancelled. */
    fun dismissUpcomingNotification(alarmId: Long) {
        NotificationManagerCompat.from(context).cancel(upcomingNotificationId(alarmId))
    }

    private fun upcomingNotificationPendingIntent(alarmId: Long, mainTriggerAtMillis: Long): PendingIntent {
        val intent = Intent(context, UpcomingAlarmReceiver::class.java)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_ORIGINAL_TRIGGER_AT_MILLIS, mainTriggerAtMillis)
        return PendingIntent.getBroadcast(
            context,
            upcomingRequestCode(alarmId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun operationPendingIntent(alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_ALARM_ID, alarmId)
        return PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun snoozeRequestCode(alarmId: Long) = (alarmId + 1_000_000_000L).toInt()
    private fun upcomingRequestCode(alarmId: Long) = upcomingNotificationId(alarmId)
}

fun upcomingNotificationId(alarmId: Long) = (alarmId + 2_000_000_000L).toInt()
