package com.targetcrafter.haalarmclock.ha

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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
import com.targetcrafter.haalarmclock.data.TimerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val NOTIFICATION_ID = 1
private const val INITIAL_BACKOFF_MILLIS = 2_000L
private const val MAX_BACKOFF_MILLIS = 60_000L
private const val RUNNING_TIMER_PUSH_INTERVAL_MILLIS = 15_000L

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
        // plus, while at least one timer is RUNNING, on a short interval so its "live" remaining
        // value keeps advancing in HA without needing a separate polling mechanism on the HA side.
        // (No such ticker runs while every timer is idle/paused/finished — the architecture stays
        // push-based, not polling, the rest of the time.)
        lifecycleScope.launch {
            val runningTimerTicker = app.timerRepository.timers.flatMapLatest { timers ->
                if (timers.any { it.state == TimerState.RUNNING }) {
                    flow { while (true) { emit(Unit); delay(RUNNING_TIMER_PUSH_INTERVAL_MILLIS) } }
                } else {
                    flowOf(Unit)
                }
            }
            combine(
                app.haSettingsStore.settings,
                app.repository.alarms,
                RingingState.current,
                app.timerRepository.timers,
                runningTimerTicker,
            ) { settings, alarms, ringing, timers, _ ->
                SyncInputs(settings, alarms, ringing, timers)
            }.collect { inputs ->
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

        // Route commands HA sent us back into the app's own action paths.
        lifecycleScope.launch {
            app.haWebSocketClient.commands.collect { command ->
                when (command) {
                    is HaCommand.SetAlarmEnabled -> app.repository.setEnabled(command.alarmId, command.enabled)
                    HaCommand.Snooze -> AlarmActions.snooze(this@HaSyncService)
                    HaCommand.Dismiss -> AlarmActions.dismiss(this@HaSyncService)
                    is HaCommand.CreateAlarm -> app.repository.save(
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

        lifecycleScope.launch {
            app.haWebSocketClient.connectionState.collect { state ->
                updateNotification(state == HaConnectionState.CONNECTED)
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
