package com.targetcrafter.haalarmclock.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalTime
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "dynamic_clock_icon"
private const val ALIAS_PREFIX = "com.targetcrafter.haalarmclock.icon.ClockIconAlias"

/**
 * Fakes a "live" analog-clock launcher icon by enabling the [ClockIconAlias00]..[ClockIconAlias11]
 * activity-alias (declared in AndroidManifest.xml) matching the current hour and disabling the
 * other eleven. There is no public Android API for an app to animate its own launcher icon in
 * real time — the launcher caches icons as static bitmaps — so this only updates hourly, on
 * whatever cadence WorkManager's periodic jobs actually run at (Android enforces a 15-minute
 * floor and can defer further for Doze/battery optimization).
 */
object DynamicIconUpdater {

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<DynamicIconWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun applyNow(context: Context) {
        val targetHour = LocalTime.now().hour % 12
        val pm = context.packageManager
        for (hour in 0..11) {
            val state = if (hour == targetHour) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(aliasComponent(context, hour), state, PackageManager.DONT_KILL_APP)
        }
    }

    private fun aliasComponent(context: Context, hour: Int): ComponentName =
        ComponentName(context.packageName, "$ALIAS_PREFIX${"%02d".format(hour)}")
}

class DynamicIconWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        DynamicIconUpdater.applyNow(applicationContext)
        return Result.success()
    }
}
