package com.targetcrafter.haalarmclock.util

import android.content.Context
import android.os.Build
import com.targetcrafter.haalarmclock.alarm.ScheduleHealth
import com.targetcrafter.haalarmclock.alarm.alarmVolume
import com.targetcrafter.haalarmclock.alarm.checkScheduleHealth
import com.targetcrafter.haalarmclock.data.Alarm
import com.targetcrafter.haalarmclock.data.ScheduleAuditState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun Long.asTime(): String =
    if (this <= 0L) "never" else Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(TIMESTAMP)

/**
 * Everything worth knowing when an alarm didn't ring, as one block of text to copy into a bug
 * report. Gathered in one place because the useful answer is never a single value — it's the
 * combination of what the OS thinks of the app, whether the schedule is actually registered, and
 * what crashed. Chasing those one screenshot at a time is what made this slow to diagnose.
 */
fun buildDiagnosticsReport(
    context: Context,
    alarms: List<Alarm>,
    audit: ScheduleAuditState,
    crashes: List<RecordedCrash>,
): String = buildString {
    val version = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${@Suppress("DEPRECATION") info.versionCode})"
    }.getOrDefault("unknown")

    appendLine("HA Alarm Clock diagnostics")
    appendLine("Generated: ${System.currentTimeMillis().asTime()}")
    appendLine()

    appendLine("== App and device ==")
    appendLine("App version: $version")
    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
    appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    appendLine("Build: ${Build.DISPLAY}")
    appendLine()

    appendLine("== What Android allows this app ==")
    appendLine("Ignoring battery optimization: ${isIgnoringBatteryOptimizations(context)}")
    val policy = backgroundPolicy(context)
    appendLine("Background restricted: ${policy.isRestricted}")
    appendLine("Standby bucket: ${policy.standbyBucket?.label ?: "n/a"}")
    appendLine("Can use full-screen intent: ${canUseFullScreenIntent(context)}")
    val volume = alarmVolume(context)
    appendLine("Alarm volume: ${volume.current}/${volume.max}")
    appendLine()

    appendLine("== Alarm schedule ==")
    val enabled = alarms.filter { it.enabled }
    appendLine("Alarms: ${alarms.size} total, ${enabled.size} enabled")
    for (alarm in enabled) {
        val next = if (alarm.hasValidTime) alarm.nextTriggerAtMillis().asTime() else "INVALID TIME"
        appendLine("  #${alarm.id} %02d:%02d".format(alarm.hour, alarm.minute) + " -> $next")
    }
    val health = checkScheduleHealth(context, alarms)
    appendLine(
        "Registered with Android: " + when (health) {
            is ScheduleHealth.Registered -> "yes"
            is ScheduleHealth.Missing -> "NO — expected ${health.expectedAtMillis.asTime()}"
            is ScheduleHealth.Inconclusive -> "unknown (another app has a sooner alarm)"
            is ScheduleHealth.NoAlarms -> "n/a (nothing enabled)"
        },
    )
    appendLine("Times Android dropped the schedule: ${audit.dropCount}")
    appendLine("Last drop: ${audit.lastDropAtMillis.asTime()}")
    appendLine("Last confirmed healthy: ${audit.lastHealthyAtMillis.asTime()}")
    appendLine()

    appendLine("== Crashes (${crashes.size}) ==")
    if (crashes.isEmpty()) {
        appendLine("None recorded.")
    } else {
        for (crash in crashes) {
            appendLine("--- ${crash.atMillis.asTime()} on thread \"${crash.threadName}\" ---")
            appendLine(crash.stackTrace.trimEnd())
            appendLine()
        }
    }
}
