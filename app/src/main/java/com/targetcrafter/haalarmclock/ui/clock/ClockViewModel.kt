package com.targetcrafter.haalarmclock.ui.clock

import androidx.lifecycle.ViewModel
import com.targetcrafter.haalarmclock.data.ClockPreferencesStore
import com.targetcrafter.haalarmclock.data.ClockStyle
import com.targetcrafter.haalarmclock.data.WorldClockStore
import kotlinx.coroutines.flow.StateFlow

class ClockViewModel(
    private val clockPreferencesStore: ClockPreferencesStore,
    private val worldClockStore: WorldClockStore,
) : ViewModel() {

    val clockStyle: StateFlow<ClockStyle> = clockPreferencesStore.style
    val worldClockZoneIds: StateFlow<List<String>> = worldClockStore.zoneIds

    fun addZone(zoneId: String) = worldClockStore.add(zoneId)

    fun removeZone(zoneId: String) = worldClockStore.remove(zoneId)
}
