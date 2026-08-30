package com.targetcrafter.haalarmclock.ui.editor

import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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

/**
 * The full alarm editor, as a large bottom sheet rather than a separate screen — opened when the
 * user taps an alarm's label/repeat area (or the add button). Every option is reachable without
 * navigating away; less-common options collapse into dropdown-style rows so the default view fits
 * on screen without scrolling on typical phones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditSheet(alarmId: Long?, onDismiss: () -> Unit) {
    val app = HaAlarmClockApp.from(LocalContext.current)
    val viewModel: AlarmEditorViewModel = viewModel(
        key = "editor-$alarmId",
        factory = appViewModelFactory { AlarmEditorViewModel(app.repository, alarmId) },
    )
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        if (state.isLoading) return@ModalBottomSheet

        var soundExpanded by remember { mutableStateOf(false) }
        var snoozeExpanded by remember { mutableStateOf(false) }

        val ringtonePickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            viewModel.updateRingtone(uri?.toString())
        }

        // A fixed (rather than content-driven) height gives the FAB below a stable frame to pin
        // to, so it stays visible in place while the column beneath it scrolls, instead of
        // scrolling away with the content.
        val sheetHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
        Box(modifier = Modifier.fillMaxWidth().height(sheetHeight)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (state.isNew) "New alarm" else "Edit alarm",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Row {
                        if (!state.isNew) {
                            IconButton(onClick = { viewModel.delete(onDismiss) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete alarm")
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    }
                }

                OutlinedTextField(
                    value = state.label,
                    onValueChange = viewModel::updateLabel,
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Repeat", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    trailingContent = { Switch(checked = state.vibrate, onCheckedChange = viewModel::updateVibrate) },
                    modifier = Modifier.fillMaxWidth(),
                )
                ListItem(
                    headlineContent = { Text("Fade in gently") },
                    supportingContent = { Text("Ramps up to full volume over the first 45 seconds") },
                    trailingContent = { Switch(checked = state.fadeInEnabled, onCheckedChange = viewModel::updateFadeIn) },
                    modifier = Modifier.fillMaxWidth(),
                )

                ExpandableOptionRow(
                    title = "Sound",
                    summary = if (state.ringtoneUri != null) "Custom" else "Default alarm sound",
                    expanded = soundExpanded,
                    onToggle = { soundExpanded = !soundExpanded },
                ) {
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
                }

                ExpandableOptionRow(
                    title = "Snooze duration",
                    summary = "${state.snoozeMinutes} min",
                    expanded = snoozeExpanded,
                    onToggle = { snoozeExpanded = !snoozeExpanded },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FilledIconButton(onClick = { viewModel.updateSnoozeMinutes(state.snoozeMinutes - 1) }) {
                            Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                        }
                        Text(
                            "${state.snoozeMinutes} min",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        FilledIconButton(onClick = { viewModel.updateSnoozeMinutes(state.snoozeMinutes + 1) }) {
                            Icon(Icons.Filled.Add, contentDescription = "Increase")
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { viewModel.save(onDismiss) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Save")
            }
        }
    }
}

@Composable
private fun ExpandableOptionRow(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(summary) },
            trailingContent = {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            },
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        )
        AnimatedVisibility(visible = expanded) {
            Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 8.dp)) {
                content()
            }
        }
    }
}
