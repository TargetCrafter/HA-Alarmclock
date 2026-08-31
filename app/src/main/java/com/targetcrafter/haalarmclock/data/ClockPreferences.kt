package com.targetcrafter.haalarmclock.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ClockStyle { ANALOG, DIGITAL }

/** Persists which face style the in-app Clock tab's local-time clock uses. Separate from the two
 * home-screen widgets, which are already distinct analog/digital widgets a user places directly. */
class ClockPreferencesStore(context: Context) {

    private val prefs = context.getSharedPreferences("clock_preferences", Context.MODE_PRIVATE)

    private val _style = MutableStateFlow(load())
    val style: StateFlow<ClockStyle> = _style.asStateFlow()

    fun save(newStyle: ClockStyle) {
        prefs.edit().putString(KEY_STYLE, newStyle.name).apply()
        _style.value = newStyle
    }

    private fun load(): ClockStyle {
        val raw = prefs.getString(KEY_STYLE, null) ?: return ClockStyle.DIGITAL
        return runCatching { ClockStyle.valueOf(raw) }.getOrDefault(ClockStyle.DIGITAL)
    }

    companion object {
        private const val KEY_STYLE = "style"
    }
}
