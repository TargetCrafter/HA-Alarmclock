package com.targetcrafter.haalarmclock.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The list of extra timezones shown on the in-app Clock tab, in the order added. App-local only
 * — never synced to Home Assistant, unlike everything else this app persists remotely. */
class WorldClockStore(context: Context) {

    private val prefs = context.getSharedPreferences("world_clocks", Context.MODE_PRIVATE)

    private val _zoneIds = MutableStateFlow(load())
    val zoneIds: StateFlow<List<String>> = _zoneIds.asStateFlow()

    fun add(zoneId: String) {
        if (zoneId in _zoneIds.value) return
        persist(_zoneIds.value + zoneId)
    }

    fun remove(zoneId: String) {
        persist(_zoneIds.value - zoneId)
    }

    private fun persist(newZoneIds: List<String>) {
        prefs.edit().putString(KEY_ZONE_IDS, newZoneIds.joinToString(",")).apply()
        _zoneIds.value = newZoneIds
    }

    private fun load(): List<String> =
        prefs.getString(KEY_ZONE_IDS, null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

    companion object {
        private const val KEY_ZONE_IDS = "zone_ids"
    }
}
