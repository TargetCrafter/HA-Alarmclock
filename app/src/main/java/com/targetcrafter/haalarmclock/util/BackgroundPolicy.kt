package com.targetcrafter.haalarmclock.util

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

/**
 * What Android's own background policy currently says about this app.
 *
 * Worth asking directly rather than inferring: when a scheduled alarm silently stops existing, the
 * usual cause is the system having decided the app is idle and force-stopping it, which drops every
 * AlarmManager entry it owned. Both of these are readable without any special permission — the
 * standby bucket query is unprivileged for the *calling* app — so the app can simply report what
 * the OS thinks of it instead of leaving the user to guess from a missed alarm.
 */
data class BackgroundPolicy(
    /** The user or system put this app in "Restricted" background mode — it will be killed
     * aggressively and its alarms will not survive. */
    val isRestricted: Boolean,
    /** Android's App Standby bucket, or null below API 28 where the concept doesn't exist. */
    val standbyBucket: StandbyBucket?,
) {
    /** True when the OS has this app somewhere its alarms can't be relied on. */
    val threatensAlarms: Boolean
        get() = isRestricted || standbyBucket == StandbyBucket.RARE || standbyBucket == StandbyBucket.RESTRICTED
}

enum class StandbyBucket(val label: String) {
    ACTIVE("Active"),
    WORKING_SET("Working set"),
    FREQUENT("Frequent"),
    RARE("Rare"),
    RESTRICTED("Restricted"),
    UNKNOWN("Unknown"),
}

fun backgroundPolicy(context: Context): BackgroundPolicy {
    // Both APIs arrived in Android 9; below that there is nothing to report.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return BackgroundPolicy(false, null)

    val restricted = context.getSystemService<ActivityManager>()?.isBackgroundRestricted() ?: false
    val bucket = when (context.getSystemService<UsageStatsManager>()?.getAppStandbyBucket()) {
        null -> null
        UsageStatsManager.STANDBY_BUCKET_ACTIVE -> StandbyBucket.ACTIVE
        UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> StandbyBucket.WORKING_SET
        UsageStatsManager.STANDBY_BUCKET_FREQUENT -> StandbyBucket.FREQUENT
        UsageStatsManager.STANDBY_BUCKET_RARE -> StandbyBucket.RARE
        // Only exists from Android 11; harmless to match on older releases, it simply never occurs.
        UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> StandbyBucket.RESTRICTED
        else -> StandbyBucket.UNKNOWN
    }
    return BackgroundPolicy(isRestricted = restricted, standbyBucket = bucket)
}
