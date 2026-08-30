package com.targetcrafter.haalarmclock.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-wide defaults for snooze duration and fade-in duration; any alarm can override either. */
data class AppDefaults(
    val snoozeMinutes: Int = 10,
    val fadeInSeconds: Int = 45,
)

/** Persists [AppDefaults] in plain SharedPreferences — nothing here is sensitive, unlike the HA
 * connection settings, which use EncryptedSharedPreferences. */
class AppDefaultsStore(context: Context) {

    private val prefs = context.getSharedPreferences("app_defaults", Context.MODE_PRIVATE)

    private val _defaults = MutableStateFlow(load())
    val defaults: StateFlow<AppDefaults> = _defaults.asStateFlow()

    fun save(newDefaults: AppDefaults) {
        prefs.edit()
            .putInt(KEY_SNOOZE_MINUTES, newDefaults.snoozeMinutes)
            .putInt(KEY_FADE_IN_SECONDS, newDefaults.fadeInSeconds)
            .apply()
        _defaults.value = newDefaults
    }

    private fun load() = AppDefaults(
        snoozeMinutes = prefs.getInt(KEY_SNOOZE_MINUTES, 10),
        fadeInSeconds = prefs.getInt(KEY_FADE_IN_SECONDS, 45),
    )

    companion object {
        private const val KEY_SNOOZE_MINUTES = "snooze_minutes"
        private const val KEY_FADE_IN_SECONDS = "fade_in_seconds"
    }
}
