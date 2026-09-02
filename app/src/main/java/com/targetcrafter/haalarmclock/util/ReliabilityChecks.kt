package com.targetcrafter.haalarmclock.util

import android.content.Context
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService

/** True once the user has exempted this app from battery optimization — without it, the OS can
 * kill the app in the background before a scheduled alarm ever fires. Shared between the Settings
 * "Alarm reliability" card and [com.targetcrafter.haalarmclock.ui.MainActivity]'s launch nudge. */
fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(context.packageName) ?: true

/** True if the OS will actually honor a fullScreenIntent notification (Android 14+ only; always
 * true below that) rather than silently demoting it to a plain heads-up notification. */
fun canUseFullScreenIntent(context: Context): Boolean =
    NotificationManagerCompat.from(context).canUseFullScreenIntent()
