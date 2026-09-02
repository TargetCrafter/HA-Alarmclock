package com.targetcrafter.haalarmclock.ha

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

private const val TAG = "HaWebSocketClient"
private const val EVENT_TYPE_COMMAND = "ha_alarmclock_command"

/**
 * Maintains a connection to Home Assistant's built-in WebSocket API (`/api/websocket`), using the
 * same long-lived access token as the REST push, and listens for [EVENT_TYPE_COMMAND] events that
 * the custom integration fires when a user toggles an alarm switch or presses a button in HA.
 *
 * [deviceId] is this phone's own id (the one it syncs under, see HaSyncPayload): HA's event bus is
 * global, so every phone connected to the same Home Assistant receives *every* command event,
 * including ones the integration aimed at a different phone. Commands are filtered against it in
 * [isForThisDevice] so a second phone in the household doesn't act on the first one's snooze,
 * dismiss, or alarm-toggle — the last of which would otherwise apply another phone's alarm id to
 * whatever unrelated alarm happens to share that id here.
 *
 * This class does one connection attempt per [connect] call; reconnect-with-backoff is driven by
 * the caller observing [connectionState] (see HaSyncService), which keeps this class simple and
 * makes the backoff policy cancellable in the normal structured-concurrency way.
 */
class HaWebSocketClient(private val httpClient: OkHttpClient, private val deviceId: String) {

    private val _connectionState = MutableStateFlow(HaConnectionState.DISCONNECTED)
    val connectionState: StateFlow<HaConnectionState> = _connectionState.asStateFlow()

    private val _commands = MutableSharedFlow<HaCommand>(extraBufferCapacity = 8)
    val commands: SharedFlow<HaCommand> = _commands.asSharedFlow()

    // OkHttp's WebSocketListener callbacks run on its own dispatcher thread, while connect()/close()
    // are called from the service's coroutine; @Volatile keeps identity checks in isCurrent() safe
    // across that boundary.
    @Volatile private var webSocket: WebSocket? = null
    private var nextMessageId = 1

    fun connect(baseUrl: String, accessToken: String) {
        close(newState = HaConnectionState.CONNECTING)
        nextMessageId = 1
        val request = Request.Builder().url(toWebSocketUrl(baseUrl)).build()
        webSocket = httpClient.newWebSocket(request, Listener(accessToken))
    }

    fun close() = close(newState = HaConnectionState.DISCONNECTED)

    private fun close(newState: HaConnectionState) {
        webSocket?.close(1000, null)
        webSocket = null
        _connectionState.value = newState
    }

    // Guards against a stale callback from a socket that [connect] has since replaced: without
    // this, a delayed onClosed/onFailure from the previous attempt could clobber the state of a
    // newer, already-successful connection.
    private fun isCurrent(ws: WebSocket) = webSocket === ws

    /**
     * Whether a command event was aimed at this phone. Every event the integration fires carries
     * the `device_id` it resolved (see its switch.py/button.py/assist.py), so a mismatch means the
     * command belongs to a different phone syncing to the same Home Assistant and must be ignored.
     *
     * An event with no `device_id` at all is accepted: the field has always been sent by the
     * integration, so the only way to see one without it is an older integration version or a
     * hand-fired event, and silently ignoring every command would be a worse failure than the
     * cross-device leak this guards against.
     */
    private fun isForThisDevice(data: JsonObject): Boolean {
        val target = data["device_id"]?.jsonPrimitive?.contentOrNull
        if (target == null) {
            Log.w(TAG, "Command event carried no device_id; accepting it, but it may be from an outdated integration")
            return true
        }
        return target == deviceId
    }

    private inner class Listener(private val accessToken: String) : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!isCurrent(webSocket)) return
            val obj = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "auth_required" -> webSocket.send(
                    buildJsonObject {
                        put("type", "auth")
                        put("access_token", accessToken)
                    }.toString(),
                )
                "auth_ok" -> {
                    _connectionState.value = HaConnectionState.CONNECTED
                    webSocket.send(
                        buildJsonObject {
                            put("id", nextMessageId++)
                            put("type", "subscribe_events")
                            put("event_type", EVENT_TYPE_COMMAND)
                        }.toString(),
                    )
                }
                "auth_invalid" -> _connectionState.value = HaConnectionState.ERROR
                "event" -> {
                    val data = obj["event"]?.jsonObject?.get("data")?.jsonObject ?: return
                    if (!isForThisDevice(data)) return
                    parseCommand(data)?.let { _commands.tryEmit(it) }
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!isCurrent(webSocket)) return
            _connectionState.value = HaConnectionState.ERROR
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!isCurrent(webSocket)) return
            if (_connectionState.value == HaConnectionState.CONNECTED || _connectionState.value == HaConnectionState.CONNECTING) {
                _connectionState.value = HaConnectionState.ERROR
            }
        }
    }
}

private fun parseCommand(data: JsonObject): HaCommand? = when (data["command"]?.jsonPrimitive?.contentOrNull) {
    "set_alarm_enabled" -> {
        val id = data["alarm_id"]?.jsonPrimitive?.longOrNull
        val enabled = data["enabled"]?.jsonPrimitive?.booleanOrNull
        if (id != null && enabled != null) HaCommand.SetAlarmEnabled(id, enabled) else null
    }
    "snooze" -> HaCommand.Snooze
    "dismiss" -> HaCommand.Dismiss
    "create_alarm" -> parseCreateAlarm(data)
    else -> null
}

private val TIME_REGEX = Regex("""^(\d{1,2}):(\d{2})$""")
private val REPEAT_DAY_BITS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

private fun parseCreateAlarm(data: JsonObject): HaCommand? {
    val time = data["time"]?.jsonPrimitive?.contentOrNull ?: return null
    val match = TIME_REGEX.matchEntire(time) ?: return null
    val (hourText, minuteText) = match.destructured
    // The regex shape alone matches "29:00" and "12:75" — anything out of range has to be rejected
    // here, since an Alarm built from it throws from java.time the moment it's scheduled (and, as
    // the row is written before it's scheduled, would throw again on every launch and every boot).
    val hour = hourText.toIntOrNull() ?: return null
    val minute = minuteText.toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) {
        Log.w(TAG, "Ignoring create_alarm command with out-of-range time '$time'")
        return null
    }
    val label = data["label"]?.jsonPrimitive?.contentOrNull ?: ""
    val repeatDays = data["repeat_days"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
    val repeatMask = repeatDays.fold(0) { mask, day ->
        val bit = REPEAT_DAY_BITS.indexOf(day.lowercase())
        if (bit >= 0) mask or (1 shl bit) else mask
    }
    return HaCommand.CreateAlarm(hour, minute, label, repeatMask)
}

private fun toWebSocketUrl(baseUrl: String): String {
    val trimmed = normalizeBaseUrl(baseUrl)
    val wsBase = when {
        trimmed.startsWith("https://", ignoreCase = true) -> "wss://" + trimmed.removePrefix("https://")
        trimmed.startsWith("http://", ignoreCase = true) -> "ws://" + trimmed.removePrefix("http://")
        else -> "wss://$trimmed"
    }
    return "$wsBase/api/websocket"
}
