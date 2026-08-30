package com.targetcrafter.haalarmclock.mqtt

import com.targetcrafter.haalarmclock.data.Alarm
import com.targetcrafter.haalarmclock.data.daysMaskLabel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Builds Home Assistant MQTT-discovery config payloads. See [HaTopics] for the topics used. */
class HaDiscovery(private val deviceId: String, private val deviceName: String, private val topics: HaTopics) {

    private fun device() = buildJsonObject {
        putJsonArray("identifiers") { add(deviceId) }
        put("name", deviceName)
        put("manufacturer", "HA Alarm Clock")
        put("model", "Android")
    }

    fun ringingSensorConfig(): JsonObject = buildJsonObject {
        put("name", "Ringing")
        put("unique_id", "${deviceId}_ringing")
        put("state_topic", topics.ringingState)
        put("json_attributes_topic", topics.ringingAttributes)
        put("payload_on", "ON")
        put("payload_off", "OFF")
        put("device_class", "sound")
        put("availability_topic", topics.availability)
        put("device", device())
    }

    fun nextAlarmSensorConfig(): JsonObject = buildJsonObject {
        put("name", "Next alarm")
        put("unique_id", "${deviceId}_next_alarm")
        put("state_topic", topics.nextAlarmState)
        put("json_attributes_topic", topics.nextAlarmAttributes)
        put("device_class", "timestamp")
        put("icon", "mdi:alarm")
        put("availability_topic", topics.availability)
        put("device", device())
    }

    fun snoozeButtonConfig(): JsonObject = buildJsonObject {
        put("name", "Snooze alarm")
        put("unique_id", "${deviceId}_snooze")
        put("command_topic", topics.ringingSnoozeCommand)
        put("icon", "mdi:alarm-snooze")
        put("availability_topic", topics.availability)
        put("device", device())
    }

    fun dismissButtonConfig(): JsonObject = buildJsonObject {
        put("name", "Dismiss alarm")
        put("unique_id", "${deviceId}_dismiss")
        put("command_topic", topics.ringingDismissCommand)
        put("icon", "mdi:alarm-off")
        put("availability_topic", topics.availability)
        put("device", device())
    }

    fun alarmSwitchConfig(alarm: Alarm): JsonObject = buildJsonObject {
        put("name", alarm.label.ifBlank { "Alarm %02d:%02d".format(alarm.hour, alarm.minute) })
        put("unique_id", "${deviceId}_alarm_${alarm.id}")
        put("state_topic", topics.alarmState(alarm.id))
        put("command_topic", topics.alarmSetCommand(alarm.id))
        put("json_attributes_topic", topics.alarmAttributes(alarm.id))
        put("payload_on", "ON")
        put("payload_off", "OFF")
        put("icon", "mdi:alarm")
        put("availability_topic", topics.availability)
        put("device", device())
    }

    fun alarmAttributes(alarm: Alarm): JsonObject = buildJsonObject {
        put("time", "%02d:%02d".format(alarm.hour, alarm.minute))
        put("label", alarm.label)
        put("repeat", daysMaskLabel(alarm.repeatDaysMask))
        put("snooze_minutes", alarm.snoozeMinutes)
        if (alarm.enabled) {
            put("next_trigger", isoFormat(alarm.nextTriggerAtMillis()))
        }
    }

    fun ringingAttributes(alarm: Alarm): JsonObject = buildJsonObject {
        put("alarm_id", alarm.id)
        put("label", alarm.label)
        put("time", "%02d:%02d".format(alarm.hour, alarm.minute))
    }

    fun nextAlarmAttributes(alarm: Alarm?): JsonObject = buildJsonObject {
        put("alarm_id", alarm?.id)
        put("label", alarm?.label)
    }

    private fun isoFormat(epochMillis: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMillis))
}
