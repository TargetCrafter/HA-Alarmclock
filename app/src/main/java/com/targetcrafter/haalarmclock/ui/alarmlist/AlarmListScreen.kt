package com.targetcrafter.haalarmclock.ui.alarmlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.targetcrafter.haalarmclock.HaAlarmClockApp
import com.targetcrafter.haalarmclock.R
import com.targetcrafter.haalarmclock.data.Alarm
import com.targetcrafter.haalarmclock.data.daysMaskLabel
import com.targetcrafter.haalarmclock.ui.appViewModelFactory
import com.targetcrafter.haalarmclock.ui.editor.AlarmEditSheet
import com.targetcrafter.haalarmclock.ui.editor.QuickTimeDialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(onOpenSettings: () -> Unit) {
    val app = HaAlarmClockApp.from(LocalContext.current)
    val viewModel: AlarmListViewModel = viewModel(
        factory = appViewModelFactory { AlarmListViewModel(app.repository) },
    )
    val alarms by viewModel.alarms.collectAsState()

    var quickTimeAlarm by remember { mutableStateOf<Alarm?>(null) }
    var editSheetAlarmId by remember { mutableStateOf<Long?>(null) }
    var showNewAlarmSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Alarms") },
                actions = {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.padding(end = 4.dp)) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", modifier = Modifier.scale(1.2f))
                    }
                },
            )
        },
        floatingActionButton = {
            LargeFloatingActionButton(onClick = { showNewAlarmSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add alarm", modifier = Modifier.scale(1.3f))
            }
        },
    ) { padding ->
        if (alarms.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No alarms yet. Tap + to add one.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmRow(
                        alarm = alarm,
                        onToggle = { enabled -> viewModel.setEnabled(alarm, enabled) },
                        onTimeClick = { quickTimeAlarm = alarm },
                        onDetailsClick = { editSheetAlarmId = alarm.id },
                    )
                }
            }
        }
    }

    quickTimeAlarm?.let { alarm ->
        QuickTimeDialog(
            initialHour = alarm.hour,
            initialMinute = alarm.minute,
            onDismiss = { quickTimeAlarm = null },
            onConfirm = { hour, minute ->
                viewModel.updateTime(alarm, hour, minute)
                quickTimeAlarm = null
            },
        )
    }

    if (editSheetAlarmId != null) {
        AlarmEditSheet(alarmId = editSheetAlarmId, onDismiss = { editSheetAlarmId = null })
    }
    if (showNewAlarmSheet) {
        AlarmEditSheet(alarmId = null, onDismiss = { showNewAlarmSheet = false })
    }
}

@Composable
private fun AlarmRow(alarm: Alarm, onToggle: (Boolean) -> Unit, onTimeClick: () -> Unit, onDetailsClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlarmInfo(alarm, onTimeClick, onDetailsClick)
            Switch(checked = alarm.enabled, onCheckedChange = onToggle, modifier = Modifier.scale(1.25f))
        }
    }
}

@Composable
private fun RowScope.AlarmInfo(alarm: Alarm, onTimeClick: () -> Unit, onDetailsClick: () -> Unit) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = String.format("%02d:%02d", alarm.hour, alarm.minute),
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.clickable(onClick = onTimeClick).padding(vertical = 4.dp),
        )
        Column(modifier = Modifier.clickable(onClick = onDetailsClick).padding(top = 4.dp)) {
            val subtitle = listOfNotNull(
                alarm.label.ifBlank { null },
                daysMaskLabel(alarm.repeatDaysMask),
            ).joinToString(" • ")
            Text(text = subtitle, style = MaterialTheme.typography.bodyLarge)
            alarm.snoozedUntilMillis?.let { untilMillis ->
                Text(
                    text = stringResource(R.string.snoozed_until, formatTime(untilMillis)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun formatTime(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
