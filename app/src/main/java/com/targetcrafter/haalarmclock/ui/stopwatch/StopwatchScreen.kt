package com.targetcrafter.haalarmclock.ui.stopwatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun StopwatchScreen() {
    val viewModel: StopwatchViewModel = viewModel()
    val isRunning by viewModel.isRunning.collectAsState()
    val elapsedMillis by viewModel.elapsedMillis.collectAsState()
    val laps by viewModel.laps.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = formatStopwatchTime(elapsedMillis), style = MaterialTheme.typography.displayLarge)
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = { if (isRunning) viewModel.lap() else viewModel.reset() },
                    enabled = isRunning || elapsedMillis > 0,
                ) {
                    Icon(
                        if (isRunning) Icons.Filled.Flag else Icons.Filled.Replay,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(if (isRunning) "Lap" else "Reset")
                }
                Button(
                    onClick = { if (isRunning) viewModel.pause() else viewModel.start() },
                    colors = if (isRunning) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    Icon(
                        if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(
                        when {
                            isRunning -> "Pause"
                            elapsedMillis > 0 -> "Resume"
                            else -> "Start"
                        },
                    )
                }
            }
        }

        HorizontalDivider()

        if (laps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Laps will show up here once the stopwatch is running.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(laps, key = { it.number }) { lap ->
                    ListItem(
                        headlineContent = { Text("Lap ${lap.number}") },
                        supportingContent = { Text("Total ${formatStopwatchTime(lap.totalMillis)}") },
                        trailingContent = { Text(formatStopwatchTime(lap.lapMillis), style = MaterialTheme.typography.titleMedium) },
                    )
                }
            }
        }
    }
}

private fun formatStopwatchTime(millis: Long): String {
    val total = millis.coerceAtLeast(0)
    val minutes = total / 60_000
    val seconds = (total / 1_000) % 60
    val millisPart = total % 1_000
    return String.format("%02d:%02d.%03d", minutes, seconds, millisPart)
}
