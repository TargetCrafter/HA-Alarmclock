package com.targetcrafter.haalarmclock.ha

import android.app.Notification
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.targetcrafter.haalarmclock.HaAlarmClockApp
import com.targetcrafter.haalarmclock.NOTIFICATION_CHANNEL_SYNC
import com.targetcrafter.haalarmclock.R
import com.targetcrafter.haalarmclock.alarm.AlarmActions
import com.targetcrafter.haalarmclock.alarm.RingingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val NOTIFICATION_ID = 1
private const val INITIAL_BACKOFF_MILLIS = 2_000L
private const val MAX_BACKOFF_MILLIS = 60_000L

/**
 * Long-running foreground service that keeps a direct connection to Home Assistant's custom
 * "HA Alarm Clock" integration alive, independent of whether the app UI is open:
 *  - pushes alarm/ringing state via REST whenever it changes
 *  - maintains a WebSocket connection (with backoff reconnect) to receive commands HA sends back
 * Started on app launch and on boot; the connection is simply not opened if HA sync is disabled.
 */
class HaSyncService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        val app = HaAlarmClockApp.from(this)
        startForeground(NOTIFICATION_ID, buildNotification(connected = false))

        // Push current state to HA whenever settings, alarms, or ringing state change.
        lifecycleScope.launch {
            combine(app.haSettingsStore.settings, app.repository.alarms, RingingState.current) { settings, alarms, ringing ->
                Triple(settings, alarms, ringing)
            }.collect { (settings, alarms, ringing) ->
                if (settings.isConfigured) {
                    val payload = HaSyncPayload.build(app.deviceId, app.deviceName, alarms, ringing)
                    app.haApiClient.pushSync(settings.baseUrl, settings.accessToken, payload)
                }
            }
        }

        // Maintain the command WebSocket, reconnecting with backoff on failure. collectLatest
        // means a settings change cancels the whole block below (including any pending backoff
        // delay) and starts clean for the new settings.
        lifecycleScope.launch {
            app.haSettingsStore.settings.collectLatest { settings ->
                if (!settings.isConfigured) {
                    app.haWebSocketClient.close()
                    return@collectLatest
                }
                var backoff = INITIAL_BACKOFF_MILLIS
                app.haWebSocketClient.connect(settings.baseUrl, settings.accessToken)
                app.haWebSocketClient.connectionState.collect { state ->
                    when (state) {
                        HaConnectionState.CONNECTED -> backoff = INITIAL_BACKOFF_MILLIS
                        HaConnectionState.ERROR -> {
                            delay(backoff)
                            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
                            app.haWebSocketClient.connect(settings.baseUrl, settings.accessToken)
                        }
                        else -> {}
                    }
                }
            }
        }

        // Route commands HA sent us back into the app's own action paths.
        lifecycleScope.launch {
            app.haWebSocketClient.commands.collect { command ->
                when (command) {
                    is HaCommand.SetAlarmEnabled -> app.repository.setEnabled(command.alarmId, command.enabled)
                    HaCommand.Snooze -> AlarmActions.snooze(this@HaSyncService)
                    HaCommand.Dismiss -> AlarmActions.dismiss(this@HaSyncService)
                }
            }
        }

        lifecycleScope.launch {
            app.haWebSocketClient.connectionState.collect { state ->
                updateNotification(state == HaConnectionState.CONNECTED)
            }
        }
    }

    private fun updateNotification(connected: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(connected))
    }

    private fun buildNotification(connected: Boolean): Notification =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_SYNC)
            .setContentTitle(getString(R.string.ha_sync_notification_title))
            .setContentText(
                getString(
                    if (connected) R.string.ha_sync_notification_text_connected else R.string.ha_sync_notification_text_disconnected,
                ),
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
}
