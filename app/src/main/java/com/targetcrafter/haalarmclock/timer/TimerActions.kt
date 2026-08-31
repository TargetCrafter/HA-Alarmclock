package com.targetcrafter.haalarmclock.timer

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Lets the UI's own Dismiss button (for a finished timer) stop the ringing the same way the
 * notification's Dismiss action does, rather than only deleting the Room row.
 */
object TimerActions {
    fun dismiss(context: Context, timerId: Long) {
        val intent = Intent(context, TimerService::class.java)
            .setAction(TimerService.ACTION_DISMISS)
            .putExtra(EXTRA_TIMER_ID, timerId)
        ContextCompat.startForegroundService(context, intent)
    }
}
