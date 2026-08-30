package com.targetcrafter.haalarmclock.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.targetcrafter.haalarmclock.HaAlarmClockApp
import com.targetcrafter.haalarmclock.ha.HaConnectionState
import com.targetcrafter.haalarmclock.ha.HaSettings
import com.targetcrafter.haalarmclock.ui.appViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = HaAlarmClockApp.from(context)
    val viewModel: SettingsViewModel = viewModel(
        factory = appViewModelFactory { SettingsViewModel(app.haSettingsStore, app.haWebSocketClient) },
    )
    val persisted by viewModel.settings.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    var enabled by remember { mutableStateOf(persisted.enabled) }
    var baseUrl by remember { mutableStateOf(persisted.baseUrl) }
    var accessToken by remember { mutableStateOf(persisted.accessToken) }

    LaunchedEffect(Unit) {
        enabled = persisted.enabled
        baseUrl = persisted.baseUrl
        accessToken = persisted.accessToken
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home Assistant") },
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
                headlineContent = { Text("Sync with Home Assistant") },
                supportingContent = { Text(connectionStatusLabel(connectionState)) },
                trailingContent = { Switch(checked = enabled, onCheckedChange = { enabled = it }) },
            )
            HorizontalDivider()

            Text(
                "Requires the \"HA Alarm Clock\" custom integration installed in Home Assistant, " +
                    "and a Long-Lived Access Token from your HA user profile (Security tab).",
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Home Assistant URL") },
                placeholder = { Text("https://homeassistant.local:8123") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = accessToken,
                onValueChange = { accessToken = it },
                label = { Text("Long-Lived Access Token") },
                supportingText = { Text("Generate one in Home Assistant under your profile's Security tab.") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    val target = baseUrl.trim().trimEnd('/').ifBlank { return@OutlinedButton }
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$target/profile/security")))
                },
                enabled = baseUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open Home Assistant profile") }

            Button(
                onClick = {
                    viewModel.save(
                        HaSettings(
                            enabled = enabled,
                            baseUrl = baseUrl.trim(),
                            accessToken = accessToken.trim(),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}

private fun connectionStatusLabel(state: HaConnectionState): String = when (state) {
    HaConnectionState.DISCONNECTED -> "Not connected"
    HaConnectionState.CONNECTING -> "Connecting…"
    HaConnectionState.CONNECTED -> "Connected"
    HaConnectionState.ERROR -> "Connection error — check URL/token and that the integration is installed"
}
