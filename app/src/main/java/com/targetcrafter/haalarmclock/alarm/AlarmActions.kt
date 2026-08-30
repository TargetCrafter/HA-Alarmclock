package com.targetcrafter.haalarmclock.alarm

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

const val ACTION_SNOOZE = "com.targetcrafter.haalarmclock.action.SNOOZE"
const val ACTION_DISMISS = "com.targetcrafter.haalarmclock.action.DISMISS"

/** Single entry point for snoozing/dismissing the currently ringing alarm, used by both the
 * on-screen ringing UI and Home Assistant-originated commands. No-ops if nothing is ringing.
 */
object AlarmActions {
    fun snooze(context: Context) = fireIfRinging(context, ACTION_SNOOZE)
    fun dismiss(context: Context) = fireIfRinging(context, ACTION_DISMISS)

    private fun fireIfRinging(context: Context, action: String) {
        if (RingingState.current.value == null) return
        val intent = Intent(context, AlarmRingService::class.java).setAction(action)
        ContextCompat.startForegroundService(context, intent)
    }
}
