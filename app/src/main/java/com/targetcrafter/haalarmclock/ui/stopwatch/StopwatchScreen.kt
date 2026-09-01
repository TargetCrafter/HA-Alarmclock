package com.targetcrafter.haalarmclock.ui.stopwatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/** Matches the fullscreen ringing screen's button height (see RingingActivity) — the
 * start/lap controls are the whole point of this screen and worth being able to hit by feel. */
private val StopwatchButtonHeight = 96.dp

@Composable
fun StopwatchScreen() {
    val viewModel: StopwatchViewModel = viewModel()
    val isRunning by viewModel.isRunning.collectAsState()
    val elapsedMillis by viewModel.elapsedMillis.collectAsState()
    val laps by viewModel.laps.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = formatStopwatchTime(elapsedMillis),
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            textAlign = TextAlign.Center,
        )

        HorizontalDivider()

        // Laps take the middle, expanding to fill whatever space is left — this is what pushes
        // the buttons below down to the very bottom of the screen, within a thumb's reach.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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

        HorizontalDivider()

        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StopwatchButton(
                onClick = { if (isRunning) viewModel.pause() else viewModel.start() },
                icon = if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                label = when {
                    isRunning -> "Pause"
                    elapsedMillis > 0 -> "Resume"
                    else -> "Start"
                },
                containerColor = if (isRunning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                contentColor = if (isRunning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
            )
            StopwatchButton(
                onClick = { if (isRunning) viewModel.lap() else viewModel.reset() },
                enabled = isRunning || elapsedMillis > 0,
                icon = if (isRunning) Icons.Filled.Flag else Icons.Filled.Replay,
                label = if (isRunning) "Lap" else "Reset",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun StopwatchButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(StopwatchButtonHeight),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
        Text(text = label, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 16.dp))
    }
}

private fun formatStopwatchTime(millis: Long): String {
    val total = millis.coerceAtLeast(0)
    val minutes = total / 60_000
    val seconds = (total / 1_000) % 60
    val millisPart = total % 1_000
    return String.format("%02d:%02d.%03d", minutes, seconds, millisPart)
}
