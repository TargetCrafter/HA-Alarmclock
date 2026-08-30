package com.targetcrafter.haalarmclock.mqtt

/**
 * All MQTT topics used by the app, namespaced under [baseTopic] for app state and under
 * `homeassistant/` for Home Assistant's MQTT discovery configs, per
 * https://www.home-assistant.io/integrations/mqtt/#mqtt-discovery
 */
class HaTopics(private val baseTopic: String, private val deviceId: String) {

    val availability get() = "$baseTopic/$deviceId/status"

    val ringingState get() = "$baseTopic/$deviceId/ringing/state"
    val ringingAttributes get() = "$baseTopic/$deviceId/ringing/attributes"
    val ringingSnoozeCommand get() = "$baseTopic/$deviceId/ringing/snooze"
    val ringingDismissCommand get() = "$baseTopic/$deviceId/ringing/dismiss"

    val nextAlarmState get() = "$baseTopic/$deviceId/next_alarm/state"
    val nextAlarmAttributes get() = "$baseTopic/$deviceId/next_alarm/attributes"

    fun alarmState(alarmId: Long) = "$baseTopic/$deviceId/alarm/$alarmId/state"
    fun alarmAttributes(alarmId: Long) = "$baseTopic/$deviceId/alarm/$alarmId/attributes"
    fun alarmSetCommand(alarmId: Long) = "$baseTopic/$deviceId/alarm/$alarmId/set"

    val alarmSetCommandWildcard get() = "$baseTopic/$deviceId/alarm/+/set"

    val discoveryRingingSensor get() = "homeassistant/binary_sensor/${deviceId}_ringing/config"
    val discoveryNextAlarmSensor get() = "homeassistant/sensor/${deviceId}_next_alarm/config"
    val discoverySnoozeButton get() = "homeassistant/button/${deviceId}_snooze/config"
    val discoveryDismissButton get() = "homeassistant/button/${deviceId}_dismiss/config"
    fun discoveryAlarmSwitch(alarmId: Long) = "homeassistant/switch/${deviceId}_alarm_$alarmId/config"

    /** Extracts the alarm id from a topic matching [alarmSetCommandWildcard]. */
    fun alarmIdFromSetTopic(topic: String): Long? {
        val prefix = "$baseTopic/$deviceId/alarm/"
        if (!topic.startsWith(prefix) || !topic.endsWith("/set")) return null
        return topic.removePrefix(prefix).removeSuffix("/set").toLongOrNull()
    }
}
