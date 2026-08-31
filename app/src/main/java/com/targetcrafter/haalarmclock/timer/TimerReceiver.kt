package com.targetcrafter.haalarmclock.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.targetcrafter.haalarmclock.HaAlarmClockApp
import com.targetcrafter.haalarmclock.data.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles both the AlarmManager zero-trigger and the Pause/Resume/Cancel actions on the
 * running/paused timer notification, so those work without opening the app.
 */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timerId = intent.getLongExtra(EXTRA_TIMER_ID, -1L)
        if (timerId < 0) return

        val app = HaAlarmClockApp.from(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_PAUSE -> app.timerRepository.pause(timerId)
                    ACTION_RESUME -> app.timerRepository.resume(timerId)
                    ACTION_CANCEL -> app.timerRepository.cancel(timerId)
                    else -> handleTrigger(context, timerId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleTrigger(context: Context, timerId: Long) {
        val app = HaAlarmClockApp.from(context)
        val timer = app.timerRepository.getById(timerId) ?: return
        if (timer.state != TimerState.RUNNING) return
        app.timerRepository.markFinished(timerId)
        ContextCompat.startForegroundService(
            context,
            Intent(context, TimerService::class.java).setAction(TimerService.ACTION_FINISH).putExtra(EXTRA_TIMER_ID, timerId),
        )
    }

    companion object {
        const val ACTION_TRIGGER = "com.targetcrafter.haalarmclock.action.TIMER_TRIGGER"
        const val ACTION_PAUSE = "com.targetcrafter.haalarmclock.action.TIMER_PAUSE"
        const val ACTION_RESUME = "com.targetcrafter.haalarmclock.action.TIMER_RESUME"
        const val ACTION_CANCEL = "com.targetcrafter.haalarmclock.action.TIMER_CANCEL"
    }
}
