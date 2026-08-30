package com.targetcrafter.haalarmclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.targetcrafter.haalarmclock.data.Alarm
import com.targetcrafter.haalarmclock.ui.MainActivity

const val EXTRA_ALARM_ID = "alarm_id"
const val EXTRA_IS_SNOOZE = "is_snooze"

/**
 * Schedules alarms with [AlarmManager.setAlarmClock], the API meant for alarm-clock apps: it is
 * always exact (no "schedule exact alarms" permission dance) and shows the alarm-clock icon in
 * the status bar, matching stock Clock app behavior.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager
        get() = context.getSystemService<AlarmManager>() ?: error("AlarmManager unavailable")

    fun schedule(alarm: Alarm) {
        if (!alarm.enabled) {
            cancel(alarm)
            return
        }
        val triggerAt = alarm.nextTriggerAtMillis()
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
    }

    fun cancel(alarm: Alarm) {
        alarmManager.cancel(operationPendingIntent(alarm.id))
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
}
