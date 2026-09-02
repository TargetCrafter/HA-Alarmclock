package com.targetcrafter.haalarmclock.ha

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.targetcrafter.haalarmclock.HaAlarmClockApp
import com.targetcrafter.haalarmclock.NOTIFICATION_CHANNEL_SYNC
import com.targetcrafter.haalarmclock.R
import com.targetcrafter.haalarmclock.alarm.AlarmActions
import com.targetcrafter.haalarmclock.alarm.RingingState
import com.targetcrafter.haalarmclock.data.Alarm
import com.targetcrafter.haalarmclock.data.Timer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "HaSyncService"
private const val NOTIFICATION_ID = 1
private const val INITIAL_BACKOFF_MILLIS = 2_000L
private const val MAX_BACKOFF_MILLIS = 60_000L

private data class SyncInputs(val settings: HaSettings, val alarms: List<Alarm>, val ringing: Alarm?, val timers: List<Timer>)

/** Starts [HaSyncService] only if HA sync is actually enabled/configured — see the class doc for why. */
fun startHaSyncServiceIfConfigured(context: Context) {
    if (HaAlarmClockApp.from(context).haSettingsStore.settings.value.isConfigured) {
        ContextCompat.startForegroundService(context, Intent(context, HaSyncService::class.java))
    }
}

/**
 * Long-running foreground service that keeps a direct connection to Home Assistant's custom
 * "HA Alarm Clock" integration alive, independent of whether the app UI is open:
 *  - pushes alarm/ringing state via REST whenever it changes
 *  - maintains a WebSocket connection (with backoff reconnect) to receive commands HA sends back
 *
 * Only ever started (by MainActivity, BootReceiver, or the Settings screen) when HA sync is
 * enabled — a foreground service notification is otherwise required to exist for as long as the
 * service runs, and there's no reason to show one when there's no sync to report on. If sync is
 * disabled while this is running, it stops itself (removing the notification).
 */
class HaSyncService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        val app = HaAlarmClockApp.from(this)
        startForeground(NOTIFICATION_ID, buildNotification(connected = false))

        lifecycleScope.launch {
            app.haSettingsStore.settings.map { it.isConfigured }.distinctUntilChanged().collect { configured ->
                if (!configured) stopSelf()
            }
        }

        // Push current state to HA whenever settings, alarms, ringing state, or timers change —
        // purely push-based, no polling ticker (a timer's remaining time isn't exposed to HA at
        // all; only its trigger_at timestamp is, which only changes on start/pause/resume anyway).
        lifecycleScope.launch {
            combine(
                app.haSettingsStore.settings,
                app.repository.alarms,
                RingingState.current,
                app.timerRepository.timers,
            ) { settings, alarms, ringing, timers -> SyncInputs(settings, alarms, ringing, timers) }
                .collect { inputs ->
                    if (inputs.settings.isConfigured) {
                        val payload = HaSyncPayload.build(app.deviceId, app.deviceName, inputs.alarms, inputs.ringing, inputs.timers)
                        app.haApiClient.pushSync(inputs.settings.baseUrl, inputs.settings.accessToken, payload)
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

        // Route commands HA sent us back into the app's own action paths. Each command is handled
        // inside its own try/catch: lifecycleScope has no CoroutineExceptionHandler, so without
        // this an exception from one malformed command would take the whole app process down and
        // leave HA control dead until the next launch.
        lifecycleScope.launch {
            app.haWebSocketClient.commands.collect { command ->
                try {
                    handleCommand(app, command)
                } catch (e: Exception) {
                    Log.e(TAG, "Ignoring HA command that failed to apply: $command", e)
                }
            }
        }

        lifecycleScope.launch {
            app.haWebSocketClient.connectionState.collect { state ->
                updateNotification(state == HaConnectionState.CONNECTED)
            }
        }
    }

    private suspend fun handleCommand(app: HaAlarmClockApp, command: HaCommand) {
        when (command) {
            is HaCommand.SetAlarmEnabled -> app.repository.setEnabled(command.alarmId, command.enabled)
            HaCommand.Snooze -> AlarmActions.snooze(this)
            HaCommand.Dismiss -> AlarmActions.dismiss(this)
            is HaCommand.CreateAlarm -> {
                // A blank label (e.g. from a plain "set an alarm for 7am" Assist command,
                // which never sets one) reuses the most recently created still-unnamed
                // alarm instead of piling up a new one every time — repeated voice
                // requests just keep moving the same "unnamed" alarm's time. A labeled
                // request always creates fresh, and existing labeled alarms are never
                // touched by this at all.
                val existingUnnamed = if (command.label.isBlank()) {
                    app.repository.alarms.first().filter { it.label.isBlank() }.maxByOrNull { it.id }
                } else {
                    null
                }
                if (existingUnnamed != null) {
                    app.repository.save(
                        existingUnnamed.copy(
                            hour = command.hour,
                            minute = command.minute,
                            repeatDaysMask = command.repeatDaysMask,
                            enabled = true,
                            snoozedUntilMillis = null,
                        ),
                    )
                } else {
                    app.repository.save(
                        Alarm(
                            hour = command.hour,
                            minute = command.minute,
                            label = command.label,
                            repeatDaysMask = command.repeatDaysMask,
                        ),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        // Otherwise the WebSocket would linger open to HA (nothing is listening on it anymore)
        // until OkHttp's own idle timeout or process death — e.g. every time the user disables
        // HA sync, since that now stops this service via the settings-watching coroutine above.
        HaAlarmClockApp.from(this).haWebSocketClient.close()
        super.onDestroy()
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
