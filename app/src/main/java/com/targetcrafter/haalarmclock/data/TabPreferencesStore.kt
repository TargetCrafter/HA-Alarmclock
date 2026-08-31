package com.targetcrafter.haalarmclock.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remembers which bottom-nav tab (by index) was open last, so the app reopens there instead of
 * always defaulting to the first tab. */
class TabPreferencesStore(context: Context) {

    private val prefs = context.getSharedPreferences("tab_preferences", Context.MODE_PRIVATE)

    private val _lastTabIndex = MutableStateFlow(prefs.getInt(KEY_LAST_TAB_INDEX, 0))
    val lastTabIndex: StateFlow<Int> = _lastTabIndex.asStateFlow()

    fun save(index: Int) {
        if (index == _lastTabIndex.value) return
        prefs.edit().putInt(KEY_LAST_TAB_INDEX, index).apply()
        _lastTabIndex.value = index
    }

    companion object {
        private const val KEY_LAST_TAB_INDEX = "last_tab_index"
    }
}
