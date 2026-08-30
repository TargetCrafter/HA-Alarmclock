package com.targetcrafter.haalarmclock

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.targetcrafter.haalarmclock.alarm.AlarmScheduler
import com.targetcrafter.haalarmclock.data.AlarmRepository
import com.targetcrafter.haalarmclock.data.AppDatabase
import com.targetcrafter.haalarmclock.mqtt.MqttManager
import com.targetcrafter.haalarmclock.mqtt.MqttSettingsStore

const val NOTIFICATION_CHANNEL_ALARM = "alarm_ringing"
const val NOTIFICATION_CHANNEL_SYNC = "ha_sync"

class HaAlarmClockApp : Application() {

    val scheduler: AlarmScheduler by lazy { AlarmScheduler(this) }
    val repository: AlarmRepository by lazy { AlarmRepository(AppDatabase.get(this).alarmDao(), scheduler) }
    val mqttSettingsStore: MqttSettingsStore by lazy { MqttSettingsStore(this) }
    val mqttManager: MqttManager by lazy { MqttManager(this, mqttSettingsStore) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ALARM,
                getString(R.string.notification_channel_alarm_ringing),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { setBypassDnd(true) },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_SYNC,
                getString(R.string.notification_channel_ha_sync),
                NotificationManager.IMPORTANCE_MIN,
            ),
        )
    }

    companion object {
        fun from(context: Context): HaAlarmClockApp = context.applicationContext as HaAlarmClockApp
    }
}
