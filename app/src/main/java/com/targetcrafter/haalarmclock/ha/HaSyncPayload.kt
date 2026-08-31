package com.targetcrafter.haalarmclock.ha

import com.targetcrafter.haalarmclock.data.Alarm
import com.targetcrafter.haalarmclock.data.Timer
import com.targetcrafter.haalarmclock.data.TimerState
import com.targetcrafter.haalarmclock.data.daysMaskLabel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Builds the JSON body POSTed to the custom integration's `/api/ha_alarmclock/sync` endpoint.
 * The integration owns entity creation/removal on the HA side from this payload; the app doesn't
 * need to know anything about HA entity IDs or discovery, unlike the MQTT approach it replaces.
 */
object HaSyncPayload {

    fun build(
        deviceId: String,
        deviceName: String,
        alarms: List<Alarm>,
        ringing: Alarm?,
        timers: List<Timer> = emptyList(),
    ): JsonObject = buildJsonObject {
        put("device_id", deviceId)
        put("device_name", deviceName)

        putJsonArray("alarms") {
            for (alarm in alarms) {
                addJsonObject {
                    put("id", alarm.id)
                    put("label", alarm.label)
                    put("time", "%02d:%02d".format(alarm.hour, alarm.minute))
                    put("enabled", alarm.enabled)
                    put("repeat", daysMaskLabel(alarm.repeatDaysMask))
                    if (alarm.enabled) put("next_trigger", isoInstant(alarm.nextTriggerAtMillis()))
                    alarm.snoozedUntilMillis?.let { put("snoozed_until", isoInstant(it)) }
                }
            }
        }

        val next = alarms.filter { it.enabled }.minByOrNull { it.nextTriggerAtMillis() }
        putJsonObject("next_alarm") {
            if (next != null) {
                put("alarm_id", next.id)
                put("label", next.label)
                put("trigger_at", isoInstant(next.nextTriggerAtMillis()))
            }
        }

        putJsonObject("ringing") {
            put("active", ringing != null)
            if (ringing != null) {
                put("alarm_id", ringing.id)
                put("label", ringing.label)
                put("time", "%02d:%02d".format(ringing.hour, ringing.minute))
            }
        }

        putJsonArray("timers") {
            val now = System.currentTimeMillis()
            for (timer in timers) {
                addJsonObject {
                    put("id", timer.id)
                    put("label", timer.label)
                    put("state", timer.state.name.lowercase())
                    put("duration_seconds", timer.durationMillis / 1000)
                    put("remaining_seconds", timer.remainingMillisNow(now) / 1000)
                    if (timer.state == TimerState.RUNNING) {
                        timer.endAtMillis?.let { put("trigger_at", isoInstant(it)) }
                    }
                }
            }
        }
    }

    private fun isoInstant(epochMillis: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMillis))
}
