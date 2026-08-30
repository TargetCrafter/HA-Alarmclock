package com.targetcrafter.haalarmclock.mqtt

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MqttSettings(
    val enabled: Boolean = false,
    val host: String = "",
    val port: Int = 1883,
    val useTls: Boolean = false,
    val username: String = "",
    val password: String = "",
    val baseTopic: String = "haalarmclock",
) {
    val isConfigured: Boolean get() = enabled && host.isNotBlank()
}

/** Persists MQTT broker settings (including the broker password) in EncryptedSharedPreferences. */
class MqttSettingsStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "mqtt_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<MqttSettings> = _settings.asStateFlow()

    fun save(newSettings: MqttSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, newSettings.enabled)
            .putString(KEY_HOST, newSettings.host)
            .putInt(KEY_PORT, newSettings.port)
            .putBoolean(KEY_TLS, newSettings.useTls)
            .putString(KEY_USERNAME, newSettings.username)
            .putString(KEY_PASSWORD, newSettings.password)
            .putString(KEY_BASE_TOPIC, newSettings.baseTopic)
            .apply()
        _settings.value = newSettings
    }

    private fun load() = MqttSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        host = prefs.getString(KEY_HOST, "") ?: "",
        port = prefs.getInt(KEY_PORT, 1883),
        useTls = prefs.getBoolean(KEY_TLS, false),
        username = prefs.getString(KEY_USERNAME, "") ?: "",
        password = prefs.getString(KEY_PASSWORD, "") ?: "",
        baseTopic = prefs.getString(KEY_BASE_TOPIC, "haalarmclock") ?: "haalarmclock",
    )

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_TLS = "tls"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_BASE_TOPIC = "base_topic"
    }
}
