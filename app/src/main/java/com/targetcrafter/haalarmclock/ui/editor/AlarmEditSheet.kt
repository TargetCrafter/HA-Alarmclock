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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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
        factory = appViewModelFactory { AlarmEditorViewModel(app.repository, app.appDefaultsStore, alarmId) },
    )
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        if (state.isLoading) return@ModalBottomSheet

        var soundExpanded by remember { mutableStateOf(false) }
        var snoozeExpanded by remember { mutableStateOf(false) }
        var fadeInDurationExpanded by remember { mutableStateOf(false) }

        val ringtonePickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            viewModel.updateRingtone(uri?.toString())
        }

        // A fixed (rather than content-driven) height gives the FAB below a stable frame to pin
        // to, so it stays visible in place while the column beneath it scrolls, instead of
        // scrolling away with the content.
        val sheetHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
        Box(modifier = Modifier.fillMaxWidth().height(sheetHeight)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (state.isNew) "New alarm" else "Edit alarm",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Row {
                        if (!state.isNew) {
                            IconButton(
                                onClick = { viewModel.delete(onDismiss) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete alarm", modifier = Modifier.size(28.dp))
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(28.dp))
                        }
                    }
                }

                OutlinedTextField(
                    value = state.label,
                    onValueChange = viewModel::updateLabel,
                    label = { Text("Label", style = MaterialTheme.typography.bodyLarge) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                )

                Text("Repeat", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ALL_DAYS.forEach { day ->
                        val selected = state.repeatDaysMask and (1 shl (day.value - 1)) != 0
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.toggleDay(day) },
                            label = {
                                Text(
                                    day.name.take(1),
                                    style = MaterialTheme.typography.titleSmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                SectionCard {
                    ListItem(
                        headlineContent = { Text("Vibrate", style = MaterialTheme.typography.bodyLarge) },
                        trailingContent = {
                            Switch(
                                checked = state.vibrate,
                                onCheckedChange = viewModel::updateVibrate,
                                modifier = Modifier.scale(1.15f),
                            )
                        },
                    )
                }

                SectionCard {
                    ListItem(
                        headlineContent = { Text("Fade in gently", style = MaterialTheme.typography.bodyLarge) },
                        supportingContent = { Text("Ramps up to full volume") },
                        trailingContent = {
                            Switch(
                                checked = state.fadeInEnabled,
                                onCheckedChange = viewModel::updateFadeIn,
                                modifier = Modifier.scale(1.15f),
                            )
                        },
                    )
                    AnimatedVisibility(visible = state.fadeInEnabled) {
                        Column {
                            HorizontalDivider()
                            ListItem(
                                headlineContent = { Text("Duration") },
                                supportingContent = {
                                    Text(
                                        if (state.fadeInSecondsOverride == null) {
                                            "${state.effectiveFadeInSeconds}s (default)"
                                        } else {
                                            "${state.effectiveFadeInSeconds}s (custom)"
                                        },
                                    )
                                },
                                trailingContent = {
                                    Icon(
                                        if (fadeInDurationExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = if (fadeInDurationExpanded) "Collapse" else "Expand",
                                        modifier = Modifier.size(28.dp),
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { fadeInDurationExpanded = !fadeInDurationExpanded },
                            )
                            AnimatedVisibility(visible = fadeInDurationExpanded) {
                                Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                                    DurationOverrideEditor(
                                        usesDefault = state.fadeInSecondsOverride == null,
                                        value = state.effectiveFadeInSeconds,
                                        unit = "s",
                                        step = 5,
                                        onUseDefaultChanged = { useDefault ->
                                            viewModel.setFadeInSecondsOverride(if (useDefault) null else state.effectiveFadeInSeconds)
                                        },
                                        onValueChange = { viewModel.setFadeInSecondsOverride(it) },
                                    )
                                }
                            }
                        }
                    }
                }

                SectionCard {
                    ExpandableOptionRow(
                        title = "Sound",
                        summary = if (state.ringtoneUri != null) "Custom" else "Default alarm sound",
                        expanded = soundExpanded,
                        onToggle = { soundExpanded = !soundExpanded },
                    ) {
                        Button(
                            onClick = {
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
                            },
                            contentPadding = ButtonDefaults.ContentPadding,
                        ) { Text("Choose ringtone", style = MaterialTheme.typography.bodyLarge) }
                    }
                }

                SectionCard {
                    ExpandableOptionRow(
                        title = "Snooze duration",
                        summary = if (state.snoozeMinutesOverride == null) {
                            "${state.effectiveSnoozeMinutes} min (default)"
                        } else {
                            "${state.effectiveSnoozeMinutes} min (custom)"
                        },
                        expanded = snoozeExpanded,
                        onToggle = { snoozeExpanded = !snoozeExpanded },
                    ) {
                        DurationOverrideEditor(
                            usesDefault = state.snoozeMinutesOverride == null,
                            value = state.effectiveSnoozeMinutes,
                            unit = "min",
                            onUseDefaultChanged = { useDefault ->
                                viewModel.setSnoozeMinutesOverride(if (useDefault) null else state.effectiveSnoozeMinutes)
                            },
                            onValueChange = { viewModel.setSnoozeMinutesOverride(it) },
                        )
                    }
                }
            }

            LargeFloatingActionButton(
                onClick = { viewModel.save(onDismiss) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Save", modifier = Modifier.size(32.dp))
            }
        }
    }
}

/** A rounded-corner card used for every option group, so the sheet reads as a consistent list of cards. */
@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
private fun DurationOverrideEditor(
    usesDefault: Boolean,
    value: Int,
    unit: String,
    onUseDefaultChanged: (Boolean) -> Unit,
    onValueChange: (Int) -> Unit,
    step: Int = 1,
) {
    Column {
        ListItem(
            headlineContent = { Text("Use app default") },
            trailingContent = { Switch(checked = usesDefault, onCheckedChange = onUseDefaultChanged) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!usesDefault) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilledIconButton(onClick = { onValueChange(value - step) }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                }
                Text(
                    "$value $unit",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                FilledIconButton(onClick = { onValueChange(value + step) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase")
                }
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
            headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
            supportingContent = { Text(summary) },
            trailingContent = {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(28.dp),
                )
            },
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        )
        AnimatedVisibility(visible = expanded) {
            Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                content()
            }
        }
    }
}
