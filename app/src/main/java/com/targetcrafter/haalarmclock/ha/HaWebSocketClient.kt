package com.targetcrafter.haalarmclock.ha

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

private const val EVENT_TYPE_COMMAND = "ha_alarmclock_command"

/**
 * Maintains a connection to Home Assistant's built-in WebSocket API (`/api/websocket`), using the
 * same long-lived access token as the REST push, and listens for [EVENT_TYPE_COMMAND] events that
 * the custom integration fires when a user toggles an alarm switch or presses a button in HA.
 *
 * This class does one connection attempt per [connect] call; reconnect-with-backoff is driven by
 * the caller observing [connectionState] (see HaSyncService), which keeps this class simple and
 * makes the backoff policy cancellable in the normal structured-concurrency way.
 */
class HaWebSocketClient(private val httpClient: OkHttpClient) {

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
    else -> null
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
