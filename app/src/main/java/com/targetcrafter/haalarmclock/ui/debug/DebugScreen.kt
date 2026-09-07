package com.targetcrafter.haalarmclock.ui.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.targetcrafter.haalarmclock.HaAlarmClockApp
import com.targetcrafter.haalarmclock.util.buildDiagnosticsReport
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Everything needed to diagnose a missed alarm, readable from the phone itself. This app is
 * sideloaded, so there is no store console collecting crashes and logcat needs a computer — without
 * this screen a background crash is invisible, and a crash that gets the app force-stopped is
 * exactly what cancels a scheduled alarm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = HaAlarmClockApp.from(context)
    val crashes by app.crashRecorder.crashes.collectAsState()
    val audit by app.alarmScheduleAudit.state.collectAsState()
    val alarms by app.repository.alarms.collectAsState(initial = emptyList())

    fun copy(label: String, text: String) {
        context.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { copy("Diagnostics", buildDiagnosticsReport(context, alarms, audit, crashes)) },
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy diagnostics")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Everything below in one block, ready to paste into a bug report — this is usually " +
                    "the fastest thing to send.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { copy("Diagnostics", buildDiagnosticsReport(context, alarms, audit, crashes)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                Text("Copy full diagnostics", modifier = Modifier.padding(start = 8.dp))
            }

            HorizontalDivider()

            Text("Crashes (${crashes.size})", style = MaterialTheme.typography.titleMedium)
            if (crashes.isEmpty()) {
                Text(
                    "None recorded. Crashes are stored here automatically — including background " +
                        "ones you never see, which are the ones that get the app force-stopped and " +
                        "its alarms cancelled.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                val formatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss") }
                for (crash in crashes) {
                    CrashCard(
                        title = remember(crash.atMillis) {
                            Instant.ofEpochMilli(crash.atMillis).atZone(ZoneId.systemDefault()).format(formatter)
                        },
                        threadName = crash.threadName,
                        stackTrace = crash.stackTrace,
                        onCopy = { copy("Stack trace", crash.stackTrace) },
                    )
                }
                OutlinedButton(onClick = { app.crashRecorder.clear() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear crash history")
                }
            }
        }
    }
}

@Composable
private fun CrashCard(title: String, threadName: String, stackTrace: String, onCopy: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text("Thread \"$threadName\"", style = MaterialTheme.typography.bodySmall)
        // The first line is the exception type and message, which is the part worth seeing without
        // expanding anything.
        Text(
            stackTrace.lineSequence().firstOrNull().orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide trace" else "Show trace")
            }
            TextButton(onClick = onCopy) { Text("Copy") }
        }
        if (expanded) {
            // Monospaced and horizontally scrollable: a stack trace wrapped at phone width is
            // unreadable, and selectable so it can be copied by hand as well as by the button.
            SelectionContainer {
                Text(
                    stackTrace,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    softWrap = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
