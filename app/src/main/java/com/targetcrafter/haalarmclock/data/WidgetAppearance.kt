package com.targetcrafter.haalarmclock.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Grayscale by default so the widgets blend into as many home screens/wallpapers as possible;
 * both are ARGB [android.graphics.Color] ints. */
const val DEFAULT_WIDGET_BACKGROUND: Int = 0xFF2E2E2E.toInt()
const val DEFAULT_WIDGET_FOREGROUND: Int = 0xFFE8E8E8.toInt()

data class WidgetAppearance(
    val backgroundColor: Int = DEFAULT_WIDGET_BACKGROUND,
    val foregroundColor: Int = DEFAULT_WIDGET_FOREGROUND,
)

/** Persists the user-chosen colors for both home-screen clock widgets. */
class WidgetAppearanceStore(context: Context) {

    private val prefs = context.getSharedPreferences("widget_appearance", Context.MODE_PRIVATE)

    private val _appearance = MutableStateFlow(load())
    val appearance: StateFlow<WidgetAppearance> = _appearance.asStateFlow()

    fun save(newAppearance: WidgetAppearance) {
        prefs.edit()
            .putInt(KEY_BACKGROUND, newAppearance.backgroundColor)
            .putInt(KEY_FOREGROUND, newAppearance.foregroundColor)
            .apply()
        _appearance.value = newAppearance
    }

    private fun load() = WidgetAppearance(
        backgroundColor = prefs.getInt(KEY_BACKGROUND, DEFAULT_WIDGET_BACKGROUND),
        foregroundColor = prefs.getInt(KEY_FOREGROUND, DEFAULT_WIDGET_FOREGROUND),
    )

    companion object {
        private const val KEY_BACKGROUND = "background_color"
        private const val KEY_FOREGROUND = "foreground_color"
    }
}
