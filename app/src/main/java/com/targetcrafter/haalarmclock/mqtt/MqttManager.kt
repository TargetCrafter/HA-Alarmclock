package com.targetcrafter.haalarmclock.mqtt

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings as AndroidSettings
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult
import com.targetcrafter.haalarmclock.data.Alarm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

enum class MqttConnectionState { DISABLED, DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/**
 * Owns the MQTT connection to the broker and publishes Home Assistant MQTT-discovery entities
 * for the app's alarms. Also subscribes to the command topics HA writes to, forwarding them via
 * the [onAlarmEnabledCommand]/[onSnoozeCommand]/[onDismissCommand] callbacks.
 */
class MqttManager(context: Context, private val settingsStore: MqttSettingsStore) {

    private val appContext = context.applicationContext

    @SuppressLint("HardwareIds")
    private val androidId: String =
        AndroidSettings.Secure.getString(appContext.contentResolver, AndroidSettings.Secure.ANDROID_ID) ?: "unknown"

    private val deviceId = "haalarmclock_$androidId"
    private val deviceName = "${Build.MANUFACTURER} ${Build.MODEL} Alarm Clock".trim()

    private val _connectionState = MutableStateFlow(MqttConnectionState.DISABLED)
    val connectionState: StateFlow<MqttConnectionState> = _connectionState.asStateFlow()

    var onAlarmEnabledCommand: ((alarmId: Long, enabled: Boolean) -> Unit)? = null
    var onSnoozeCommand: (() -> Unit)? = null
    var onDismissCommand: (() -> Unit)? = null

    private var client: Mqtt5AsyncClient? = null
    private var topics: HaTopics? = null
    private var discovery: HaDiscovery? = null
    private var publishedAlarmIds: Set<Long> = emptySet()

    // Guards client/topics/discovery so settings changes, alarm syncs, and state publishes
    // (each potentially triggered from a different coroutine) never race on the connection.
    private val mutex = Mutex()

    suspend fun applySettings(settings: MqttSettings) = withContext(Dispatchers.IO) {
        mutex.withLock {
            disconnectInternal()
            if (!settings.isConfigured) {
                _connectionState.value = MqttConnectionState.DISABLED
                return@withLock
            }
            val newTopics = HaTopics(settings.baseTopic, deviceId)
            topics = newTopics
            discovery = HaDiscovery(deviceId, deviceName, newTopics)
            connectInternal(settings, newTopics)
        }
    }

    private fun connectInternal(settings: MqttSettings, topics: HaTopics) {
        _connectionState.value = MqttConnectionState.CONNECTING
        val builder = Mqtt5Client.builder()
            .identifier("$deviceId-${System.currentTimeMillis()}")
            .serverHost(settings.host)
            .serverPort(settings.port)
            .automaticReconnectWithDefaultConfig()
            .addConnectedListener { _connectionState.value = MqttConnectionState.CONNECTED }
            .addDisconnectedListener { _connectionState.value = MqttConnectionState.DISCONNECTED }
        if (settings.useTls) builder.sslWithDefaultConfig()
        val newClient = builder.buildAsync()
        client = newClient

        val connectBuilder = newClient.connectWith()
            .cleanStart(true)
            .willPublish()
            .topic(topics.availability)
            .payload("offline".toByteArray(StandardCharsets.UTF_8))
            .qos(MqttQos.AT_LEAST_ONCE)
            .retain(true)
            .applyWillPublish()
        if (settings.username.isNotBlank()) {
            connectBuilder.simpleAuth()
                .username(settings.username)
                .password(settings.password.toByteArray(StandardCharsets.UTF_8))
                .applySimpleAuth()
        }

        try {
            connectBuilder.send().get()
            subscribeToCommands(newClient, topics)
            publishRetained(newClient, topics.availability, "online")
        } catch (e: Exception) {
            _connectionState.value = MqttConnectionState.ERROR
        }
    }

    private fun subscribeToCommands(client: Mqtt5AsyncClient, topics: HaTopics) {
        client.subscribeWith()
            .topicFilter(topics.alarmSetCommandWildcard)
            .callback { publish -> handleAlarmSetCommand(topics, publish) }
            .send()
        client.subscribeWith()
            .topicFilter(topics.ringingSnoozeCommand)
            .callback { onSnoozeCommand?.invoke() }
            .send()
        client.subscribeWith()
            .topicFilter(topics.ringingDismissCommand)
            .callback { onDismissCommand?.invoke() }
            .send()
    }

    private fun handleAlarmSetCommand(topics: HaTopics, publish: Mqtt5Publish) {
        val alarmId = topics.alarmIdFromSetTopic(publish.topic.toString()) ?: return
        val payload = payloadString(publish)
        onAlarmEnabledCommand?.invoke(alarmId, payload.equals("ON", ignoreCase = true))
    }

    private fun payloadString(publish: Mqtt5Publish): String =
        publish.payload.orElse(null)?.let { StandardCharsets.UTF_8.decode(it).toString() } ?: ""

    /** Publishes discovery configs and current state for [alarms]; removes discovery for alarms no longer present. */
    suspend fun syncAlarms(alarms: List<Alarm>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val client = client ?: return@withLock
            val topics = topics ?: return@withLock
            val discovery = discovery ?: return@withLock

            publishRetained(client, topics.discoveryRingingSensor, discovery.ringingSensorConfig().toString())
            publishRetained(client, topics.discoveryNextAlarmSensor, discovery.nextAlarmSensorConfig().toString())
            publishRetained(client, topics.discoverySnoozeButton, discovery.snoozeButtonConfig().toString())
            publishRetained(client, topics.discoveryDismissButton, discovery.dismissButtonConfig().toString())

            val currentIds = alarms.map { it.id }.toSet()
            for (removedId in publishedAlarmIds - currentIds) {
                publishRetained(client, topics.discoveryAlarmSwitch(removedId), "")
            }
            publishedAlarmIds = currentIds

            for (alarm in alarms) {
                publishRetained(client, topics.discoveryAlarmSwitch(alarm.id), discovery.alarmSwitchConfig(alarm).toString())
                publishRetained(client, topics.alarmState(alarm.id), if (alarm.enabled) "ON" else "OFF")
                publishRetained(client, topics.alarmAttributes(alarm.id), discovery.alarmAttributes(alarm).toString())
            }

            val next = alarms.filter { it.enabled }.minByOrNull { it.nextTriggerAtMillis() }
            publishRetained(client, topics.nextAlarmState, next?.let { isoInstant(it.nextTriggerAtMillis()) } ?: "")
            publishRetained(client, topics.nextAlarmAttributes, discovery.nextAlarmAttributes(next).toString())
        }
    }

    suspend fun publishRingingState(alarm: Alarm?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val client = client ?: return@withLock
            val topics = topics ?: return@withLock
            val discovery = discovery ?: return@withLock
            publishRetained(client, topics.ringingState, if (alarm != null) "ON" else "OFF")
            if (alarm != null) {
                publishRetained(client, topics.ringingAttributes, discovery.ringingAttributes(alarm).toString())
            }
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) { mutex.withLock { disconnectInternal() } }

    private fun disconnectInternal() {
        val client = client ?: return
        try {
            publishRetained(client, topics?.availability ?: return, "offline")
            client.disconnect().get()
        } catch (_: Exception) {
        }
        this.client = null
        _connectionState.value = MqttConnectionState.DISCONNECTED
    }

    private fun publishRetained(client: Mqtt5AsyncClient, topic: String, payload: String): CompletableFuture<Mqtt5PublishResult> =
        client.publishWith()
            .topic(topic)
            .payload(payload.toByteArray(StandardCharsets.UTF_8))
            .qos(MqttQos.AT_LEAST_ONCE)
            .retain(true)
            .send()

    private fun isoInstant(epochMillis: Long): String =
        java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.ofEpochMilli(epochMillis))
}
