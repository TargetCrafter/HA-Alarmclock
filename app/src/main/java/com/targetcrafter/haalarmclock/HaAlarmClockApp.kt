package com.targetcrafter.haalarmclock

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.provider.Settings as AndroidSettings
import com.targetcrafter.haalarmclock.alarm.AlarmScheduler
import com.targetcrafter.haalarmclock.data.AlarmRepository
import com.targetcrafter.haalarmclock.data.AppDatabase
import com.targetcrafter.haalarmclock.ha.HaApiClient
import com.targetcrafter.haalarmclock.ha.HaSettingsStore
import com.targetcrafter.haalarmclock.ha.HaWebSocketClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

const val NOTIFICATION_CHANNEL_ALARM = "alarm_ringing"
const val NOTIFICATION_CHANNEL_SYNC = "ha_sync"
const val NOTIFICATION_CHANNEL_UPCOMING = "upcoming_alarm"

class HaAlarmClockApp : Application() {

    val scheduler: AlarmScheduler by lazy { AlarmScheduler(this) }
    val repository: AlarmRepository by lazy { AlarmRepository(AppDatabase.get(this).alarmDao(), scheduler) }

    // Shared per OkHttp's own recommendation (connection pooling); the ping interval lets the
    // WebSocket detect a dead connection instead of waiting on a TCP-level timeout.
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    }

    val haSettingsStore: HaSettingsStore by lazy { HaSettingsStore(this) }
    val haApiClient: HaApiClient by lazy { HaApiClient(httpClient) }
    val haWebSocketClient: HaWebSocketClient by lazy { HaWebSocketClient(httpClient) }

    @get:SuppressLint("HardwareIds")
    val deviceId: String by lazy {
        val androidId = AndroidSettings.Secure.getString(contentResolver, AndroidSettings.Secure.ANDROID_ID) ?: "unknown"
        "haalarmclock_$androidId"
    }
    val deviceName: String by lazy { "${Build.MANUFACTURER} ${Build.MODEL} Alarm Clock".trim() }

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
        nm.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_UPCOMING,
                getString(R.string.notification_channel_upcoming_alarm),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    companion object {
        fun from(context: Context): HaAlarmClockApp = context.applicationContext as HaAlarmClockApp
    }
}
