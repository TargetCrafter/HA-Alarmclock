package com.targetcrafter.haalarmclock.ui.clock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.targetcrafter.haalarmclock.HaAlarmClockApp
import com.targetcrafter.haalarmclock.data.ClockStyle
import com.targetcrafter.haalarmclock.ui.appViewModelFactory
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val COMMON_ZONE_IDS = listOf(
    "UTC",
    "America/New_York",
    "America/Chicago",
    "America/Los_Angeles",
    "Europe/London",
    "Europe/Berlin",
    "Europe/Amsterdam",
    "Asia/Tokyo",
    "Asia/Shanghai",
    "Asia/Kolkata",
    "Australia/Sydney",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockScreen(onOpenSettings: () -> Unit) {
    val app = HaAlarmClockApp.from(LocalContext.current)
    val viewModel: ClockViewModel = viewModel(
        factory = appViewModelFactory { ClockViewModel(app.clockPreferencesStore, app.worldClockStore) },
    )
    val style by viewModel.clockStyle.collectAsState()
    val zoneIds by viewModel.worldClockZoneIds.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clock") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            LargeFloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add timezone", modifier = Modifier.scale(1.3f))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            LocalTimeHero(now = now, style = style, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp))

            HorizontalDivider()

            if (zoneIds.isEmpty()) {
                Text(
                    "No timezones added. Tap + to add one.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                for (zoneId in zoneIds) {
                    WorldClockRow(zoneId = zoneId, now = now, onRemove = { viewModel.removeZone(zoneId) })
                }
            }
        }
    }

    if (showAddDialog) {
        AddTimezoneDialog(
            existing = zoneIds,
            onDismiss = { showAddDialog = false },
            onAdd = { zoneId ->
                viewModel.addZone(zoneId)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun LocalTimeHero(now: ZonedDateTime, style: ClockStyle, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (style == ClockStyle.ANALOG) {
            AnalogClockFace(time = now.toLocalTime(), modifier = Modifier.size(220.dp))
            Text(
                text = now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            Text(text = now.format(DateTimeFormatter.ofPattern("HH:mm:ss")), style = MaterialTheme.typography.displayLarge)
        }
        Text(text = now.zone.id, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun WorldClockRow(zoneId: String, now: ZonedDateTime, onRemove: () -> Unit) {
    val zoneTime = remember(zoneId, now) { now.withZoneSameInstant(ZoneId.of(zoneId)) }
    val dayOffset = ChronoUnit.DAYS.between(now.toLocalDate(), zoneTime.toLocalDate())
    val subtitle = when {
        dayOffset > 0 -> "$zoneId · tomorrow"
        dayOffset < 0 -> "$zoneId · yesterday"
        else -> zoneId
    }
    ListItem(
        headlineContent = { Text(zoneId.substringAfterLast('/').replace('_', ' ')) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(zoneTime.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove")
                }
            }
        },
    )
}

@Composable
private fun AddTimezoneDialog(existing: List<String>, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val allZoneIds = remember { ZoneId.getAvailableZoneIds().sorted() }
    val results = remember(query) {
        if (query.isBlank()) COMMON_ZONE_IDS else allZoneIds.filter { it.contains(query, ignoreCase = true) }.take(100)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add timezone") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp).padding(top = 8.dp)) {
                    items(results, key = { it }) { zoneId ->
                        val alreadyAdded = zoneId in existing
                        ListItem(
                            headlineContent = { Text(zoneId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !alreadyAdded) { onAdd(zoneId) },
                            trailingContent = if (alreadyAdded) {
                                { Text("Added", style = MaterialTheme.typography.bodySmall) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
