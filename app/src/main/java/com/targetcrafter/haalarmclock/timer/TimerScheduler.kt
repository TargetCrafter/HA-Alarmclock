package com.targetcrafter.haalarmclock.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.targetcrafter.haalarmclock.data.Timer

const val EXTRA_TIMER_ID = "timer_id"

/** Arms/cancels the single AlarmManager trigger for a running timer's zero point. Distinct from
 * [com.targetcrafter.haalarmclock.alarm.AlarmScheduler]: a timer has no repeat schedule and no
 * separate "upcoming" notification, just one trigger to cancel/re-arm across pause/resume.
 */
class TimerScheduler(private val context: Context) {

    private val alarmManager: AlarmManager
        get() = context.getSystemService<AlarmManager>() ?: error("AlarmManager unavailable")

    fun schedule(timer: Timer) {
        val endAt = timer.endAtMillis ?: return
        val pi = operationPendingIntent(timer.id)
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi)
        }
    }

    fun cancel(timerId: Long) {
        alarmManager.cancel(operationPendingIntent(timerId))
    }

    private fun operationPendingIntent(timerId: Long): PendingIntent {
        val intent = Intent(context, TimerReceiver::class.java)
            .setAction(TimerReceiver.ACTION_TRIGGER)
            .putExtra(EXTRA_TIMER_ID, timerId)
        return PendingIntent.getBroadcast(
            context,
            timerId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
