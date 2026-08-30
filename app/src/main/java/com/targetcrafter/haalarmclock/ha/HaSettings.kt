package com.targetcrafter.haalarmclock.ha

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HaSettings(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val accessToken: String = "",
) {
    val isConfigured: Boolean get() = enabled && baseUrl.isNotBlank() && accessToken.isNotBlank()
}

/** Persists the Home Assistant base URL and long-lived access token in EncryptedSharedPreferences. */
class HaSettingsStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "ha_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<HaSettings> = _settings.asStateFlow()

    fun save(newSettings: HaSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, newSettings.enabled)
            .putString(KEY_BASE_URL, newSettings.baseUrl)
            .putString(KEY_TOKEN, newSettings.accessToken)
            .apply()
        _settings.value = newSettings
    }

    private fun load() = HaSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        baseUrl = prefs.getString(KEY_BASE_URL, "") ?: "",
        accessToken = prefs.getString(KEY_TOKEN, "") ?: "",
    )

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TOKEN = "access_token"
    }
}
