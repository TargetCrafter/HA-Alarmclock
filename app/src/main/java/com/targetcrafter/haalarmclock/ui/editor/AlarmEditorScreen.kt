package com.targetcrafter.haalarmclock.ui.editor

import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.targetcrafter.haalarmclock.HaAlarmClockApp
import com.targetcrafter.haalarmclock.ui.appViewModelFactory
import java.time.DayOfWeek

private val ALL_DAYS = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(alarmId: Long?, onDone: () -> Unit) {
    val app = HaAlarmClockApp.from(LocalContext.current)
    val viewModel: AlarmEditorViewModel = viewModel(
        key = "editor-$alarmId",
        factory = appViewModelFactory { AlarmEditorViewModel(app.repository, alarmId) },
    )
    val state by viewModel.state.collectAsState()

    if (state.isLoading) return

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        viewModel.updateRingtone(uri?.toString())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New alarm" else "Edit alarm") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { viewModel.delete(onDone) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete alarm")
                        }
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val timeState = rememberTimePickerState(initialHour = state.hour, initialMinute = state.minute, is24Hour = true)
            TimePicker(state = timeState)
            // Keep the ViewModel in sync with the picker as the user drags it.
            LaunchedEffect(timeState.hour, timeState.minute) {
                viewModel.updateTime(timeState.hour, timeState.minute)
            }

            OutlinedTextField(
                value = state.label,
                onValueChange = viewModel::updateLabel,
                label = { Text("Label") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Repeat")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ALL_DAYS.forEach { day ->
                    val selected = state.repeatDaysMask and (1 shl (day.value - 1)) != 0
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.toggleDay(day) },
                        label = { Text(day.name.take(1)) },
                    )
                }
            }

            ListItem(
                headlineContent = { Text("Vibrate") },
                trailingContent = {
                    Switch(checked = state.vibrate, onCheckedChange = viewModel::updateVibrate)
                },
            )

            ListItem(
                headlineContent = { Text("Ringtone") },
                supportingContent = { Text(state.ringtoneUri?.let { "Custom" } ?: "Default alarm sound") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(
                        RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    )
                    state.ringtoneUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
                }
                ringtonePickerLauncher.launch(intent)
            }) { Text("Choose ringtone") }

            Text("Snooze duration: ${state.snoozeMinutes} min")
            Slider(
                value = state.snoozeMinutes.toFloat(),
                onValueChange = { viewModel.updateSnoozeMinutes(it.toInt()) },
                valueRange = 1f..30f,
                steps = 28,
            )

            Button(onClick = { viewModel.save(onDone) }, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
        }
    }
}
