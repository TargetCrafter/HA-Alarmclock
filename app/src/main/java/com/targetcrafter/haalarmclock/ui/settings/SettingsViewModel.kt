package com.targetcrafter.haalarmclock.ui.settings

import androidx.lifecycle.ViewModel
import com.targetcrafter.haalarmclock.data.AppDefaults
import com.targetcrafter.haalarmclock.data.AppDefaultsStore
import com.targetcrafter.haalarmclock.ha.HaConnectionState
import com.targetcrafter.haalarmclock.ha.HaSettings
import com.targetcrafter.haalarmclock.ha.HaSettingsStore
import com.targetcrafter.haalarmclock.ha.HaWebSocketClient
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    private val settingsStore: HaSettingsStore,
    private val appDefaultsStore: AppDefaultsStore,
    webSocketClient: HaWebSocketClient,
) : ViewModel() {

    val settings: StateFlow<HaSettings> = settingsStore.settings
    val connectionState: StateFlow<HaConnectionState> = webSocketClient.connectionState
    val appDefaults: StateFlow<AppDefaults> = appDefaultsStore.defaults

    fun save(settings: HaSettings) {
        settingsStore.save(settings)
    }

    fun saveAppDefaults(defaults: AppDefaults) {
        appDefaultsStore.save(defaults)
    }
}
