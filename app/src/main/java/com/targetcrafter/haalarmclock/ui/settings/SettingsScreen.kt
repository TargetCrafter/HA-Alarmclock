package com.targetcrafter.haalarmclock.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.targetcrafter.haalarmclock.HaAlarmClockApp
import com.targetcrafter.haalarmclock.mqtt.MqttConnectionState
import com.targetcrafter.haalarmclock.mqtt.MqttSettings
import com.targetcrafter.haalarmclock.ui.appViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = HaAlarmClockApp.from(LocalContext.current)
    val viewModel: SettingsViewModel = viewModel(
        factory = appViewModelFactory { SettingsViewModel(app.mqttSettingsStore, app.mqttManager) },
    )
    val persisted by viewModel.settings.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    var enabled by remember { mutableStateOf(persisted.enabled) }
    var host by remember { mutableStateOf(persisted.host) }
    var port by remember { mutableStateOf(persisted.port.toString()) }
    var useTls by remember { mutableStateOf(persisted.useTls) }
    var username by remember { mutableStateOf(persisted.username) }
    var password by remember { mutableStateOf(persisted.password) }
    var baseTopic by remember { mutableStateOf(persisted.baseTopic) }

    LaunchedEffect(Unit) {
        enabled = persisted.enabled
        host = persisted.host
        port = persisted.port.toString()
        useTls = persisted.useTls
        username = persisted.username
        password = persisted.password
        baseTopic = persisted.baseTopic
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home Assistant / MQTT") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ListItem(
                headlineContent = { Text("Sync to Home Assistant via MQTT") },
                supportingContent = { Text(connectionStatusLabel(connectionState)) },
                trailingContent = { Switch(checked = enabled, onCheckedChange = { enabled = it }) },
            )
            HorizontalDivider()

            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Broker host") },
                placeholder = { Text("homeassistant.local") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = port,
                onValueChange = { new -> if (new.all(Char::isDigit)) port = new },
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            ListItem(
                headlineContent = { Text("Use TLS") },
                trailingContent = { Switch(checked = useTls, onCheckedChange = { useTls = it }) },
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (optional)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = baseTopic,
                onValueChange = { baseTopic = it },
                label = { Text("Base topic") },
                supportingText = { Text("Entities publish under this topic; change only if it collides with another device.") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    viewModel.save(
                        MqttSettings(
                            enabled = enabled,
                            host = host.trim(),
                            port = port.toIntOrNull() ?: 1883,
                            useTls = useTls,
                            username = username,
                            password = password,
                            baseTopic = baseTopic.trim().ifBlank { "haalarmclock" },
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}

private fun connectionStatusLabel(state: MqttConnectionState): String = when (state) {
    MqttConnectionState.DISABLED -> "Not enabled"
    MqttConnectionState.DISCONNECTED -> "Disconnected"
    MqttConnectionState.CONNECTING -> "Connecting…"
    MqttConnectionState.CONNECTED -> "Connected"
    MqttConnectionState.ERROR -> "Connection error — check host/credentials"
}
