package com.targetcrafter.haalarmclock.ui.timerlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.targetcrafter.haalarmclock.HaAlarmClockApp
import com.targetcrafter.haalarmclock.R
import com.targetcrafter.haalarmclock.data.Timer
import com.targetcrafter.haalarmclock.data.TimerState
import com.targetcrafter.haalarmclock.timer.TimerActions
import com.targetcrafter.haalarmclock.timer.formatDuration
import com.targetcrafter.haalarmclock.ui.appViewModelFactory
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerListScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val app = HaAlarmClockApp.from(context)
    val viewModel: TimerListViewModel = viewModel(
        factory = appViewModelFactory { TimerListViewModel(app.timerRepository) },
    )
    val timers by viewModel.timers.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    // Room's Flow only emits when a row actually changes, which doesn't happen every second a
    // RUNNING timer is just counting down — this ticks recomposition so the remaining-time text
    // (computed from Timer.remainingMillisNow()) stays live without touching the database.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowMillis = System.currentTimeMillis()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_timers)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            LargeFloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_timer), modifier = Modifier.scale(1.3f))
            }
        },
    ) { padding ->
        if (timers.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.no_timers_yet), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(timers, key = { it.id }) { timer ->
                    TimerRow(
                        timer = timer,
                        nowMillis = nowMillis,
                        onPause = { viewModel.pause(timer) },
                        onResume = { viewModel.resume(timer) },
                        onCancel = { viewModel.cancel(timer) },
                        onDismiss = { TimerActions.dismiss(context, timer.id) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddTimerDialog(
            onDismiss = { showAddDialog = false },
            onStart = { label, durationMillis ->
                viewModel.start(label, durationMillis)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun TimerRow(
    timer: Timer,
    nowMillis: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = timer.label.ifBlank { stringResource(R.string.timer_default_label) },
                style = MaterialTheme.typography.titleMedium,
            )
            val remaining = timer.remainingMillisNow(nowMillis)
            Text(
                text = if (timer.state == TimerState.FINISHED) stringResource(R.string.timer_done) else formatDuration(remaining),
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (timer.state != TimerState.FINISHED && timer.durationMillis > 0) {
                LinearProgressIndicator(
                    progress = { (remaining.toFloat() / timer.durationMillis.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (timer.state) {
                    TimerState.RUNNING -> {
                        OutlinedButton(onClick = onPause) {
                            Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text(stringResource(R.string.timer_pause))
                        }
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.timer_cancel)) }
                    }
                    TimerState.PAUSED -> {
                        OutlinedButton(onClick = onResume) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text(stringResource(R.string.timer_resume))
                        }
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.timer_cancel)) }
                    }
                    TimerState.FINISHED -> {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) { Text(stringResource(R.string.dismiss)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTimerDialog(onDismiss: () -> Unit, onStart: (label: String, durationMillis: Long) -> Unit) {
    var label by remember { mutableStateOf("") }
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(5) }
    var seconds by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_timer)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DurationStepper(value = hours, unit = "h", max = 23, onValueChange = { hours = it }, modifier = Modifier.weight(1f))
                    DurationStepper(value = minutes, unit = "m", max = 59, onValueChange = { minutes = it }, modifier = Modifier.weight(1f))
                    DurationStepper(value = seconds, unit = "s", max = 59, onValueChange = { seconds = it }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.timer_label_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }
        },
        confirmButton = {
            val totalMillis = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L
            TextButton(
                enabled = totalMillis > 0,
                onClick = { onStart(label.trim(), totalMillis) },
            ) { Text(stringResource(R.string.start_timer)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.timer_cancel)) }
        },
    )
}

@Composable
private fun DurationStepper(value: Int, unit: String, max: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(onClick = { onValueChange((value + 1).coerceIn(0, max)) }) { Text("+") }
        Text("$value$unit", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 4.dp))
        FilledIconButton(onClick = { onValueChange((value - 1).coerceIn(0, max)) }) { Text("−") }
    }
}
