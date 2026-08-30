package com.targetcrafter.haalarmclock.mqtt

import android.app.Notification
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.targetcrafter.haalarmclock.HaAlarmClockApp
import com.targetcrafter.haalarmclock.NOTIFICATION_CHANNEL_SYNC
import com.targetcrafter.haalarmclock.R
import com.targetcrafter.haalarmclock.alarm.AlarmActions
import com.targetcrafter.haalarmclock.alarm.RingingState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val NOTIFICATION_ID = 1

/**
 * Long-running foreground service that keeps the MQTT connection to Home Assistant alive and
 * mirrors alarm/ringing state to it, independent of whether the app UI is open. Started on app
 * launch and on boot; stopped only if the user disables MQTT sync in settings.
 */
class HaSyncService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        val app = HaAlarmClockApp.from(this)
        startForeground(NOTIFICATION_ID, buildNotification(connected = false))

        app.mqttManager.onAlarmEnabledCommand = { alarmId, enabled ->
            lifecycleScope.launch { app.repository.setEnabled(alarmId, enabled) }
        }
        app.mqttManager.onSnoozeCommand = { AlarmActions.snooze(this) }
        app.mqttManager.onDismissCommand = { AlarmActions.dismiss(this) }

        lifecycleScope.launch {
            app.mqttSettingsStore.settings.collect { settings ->
                app.mqttManager.applySettings(settings)
                if (settings.isConfigured) {
                    app.mqttManager.syncAlarms(app.repository.alarms.first())
                }
            }
        }
        lifecycleScope.launch {
            app.repository.alarms.collect { alarms -> app.mqttManager.syncAlarms(alarms) }
        }
        lifecycleScope.launch {
            RingingState.current.collect { alarm -> app.mqttManager.publishRingingState(alarm) }
        }
        lifecycleScope.launch {
            app.mqttManager.connectionState.collect { state ->
                updateNotification(state == MqttConnectionState.CONNECTED)
            }
        }
    }

    private fun updateNotification(connected: Boolean) {
        val nm = getSystemService(android.app.NotificationManager::class.java)
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
