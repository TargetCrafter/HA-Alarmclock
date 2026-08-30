package com.targetcrafter.haalarmclock.ui.settings

import androidx.lifecycle.ViewModel
import com.targetcrafter.haalarmclock.mqtt.MqttConnectionState
import com.targetcrafter.haalarmclock.mqtt.MqttManager
import com.targetcrafter.haalarmclock.mqtt.MqttSettings
import com.targetcrafter.haalarmclock.mqtt.MqttSettingsStore
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    private val settingsStore: MqttSettingsStore,
    mqttManager: MqttManager,
) : ViewModel() {

    val settings: StateFlow<MqttSettings> = settingsStore.settings
    val connectionState: StateFlow<MqttConnectionState> = mqttManager.connectionState

    fun save(settings: MqttSettings) {
        settingsStore.save(settings)
    }
}
