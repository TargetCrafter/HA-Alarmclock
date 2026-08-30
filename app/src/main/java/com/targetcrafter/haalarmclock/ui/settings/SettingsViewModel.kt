package com.targetcrafter.haalarmclock.ui.settings

import androidx.lifecycle.ViewModel
import com.targetcrafter.haalarmclock.ha.HaConnectionState
import com.targetcrafter.haalarmclock.ha.HaSettings
import com.targetcrafter.haalarmclock.ha.HaSettingsStore
import com.targetcrafter.haalarmclock.ha.HaWebSocketClient
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    private val settingsStore: HaSettingsStore,
    webSocketClient: HaWebSocketClient,
) : ViewModel() {

    val settings: StateFlow<HaSettings> = settingsStore.settings
    val connectionState: StateFlow<HaConnectionState> = webSocketClient.connectionState

    fun save(settings: HaSettings) {
        settingsStore.save(settings)
    }
}
