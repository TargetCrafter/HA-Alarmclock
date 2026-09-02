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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.util.Locale

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

@Composable
fun ClockScreen(showAddDialog: Boolean, onAddDialogDismiss: () -> Unit) {
    val app = HaAlarmClockApp.from(LocalContext.current)
    val viewModel: ClockViewModel = viewModel(
        factory = appViewModelFactory { ClockViewModel(app.clockPreferencesStore, app.worldClockStore) },
    )
    val style by viewModel.clockStyle.collectAsState()
    val zoneIds by viewModel.worldClockZoneIds.collectAsState()

    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
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
            zoneIds.forEachIndexed { index, zoneId ->
                WorldClockRow(
                    zoneId = zoneId,
                    now = now,
                    canMoveUp = index > 0,
                    canMoveDown = index < zoneIds.lastIndex,
                    onMoveUp = { viewModel.moveZoneUp(zoneId) },
                    onMoveDown = { viewModel.moveZoneDown(zoneId) },
                    onRemove = { viewModel.removeZone(zoneId) },
                )
            }
        }
    }

    if (showAddDialog) {
        AddTimezoneDialog(
            existing = zoneIds,
            onDismiss = onAddDialogDismiss,
            onAdd = { zoneId ->
                viewModel.addZone(zoneId)
                onAddDialogDismiss()
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
private fun WorldClockRow(
    zoneId: String,
    now: ZonedDateTime,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val zoneTime = remember(zoneId, now) { now.withZoneSameInstant(ZoneId.of(zoneId)) }
    val dayOffset = ChronoUnit.DAYS.between(now.toLocalDate(), zoneTime.toLocalDate())
    val subtitle = when {
        dayOffset > 0 -> "$zoneId · tomorrow"
        dayOffset < 0 -> "$zoneId · yesterday"
        else -> zoneId
    }
    ListItem(
        leadingContent = {
            // Up/down instead of drag-and-drop — a couple of taps to reorder a short list of
            // world clocks, with no gesture-detection code to get wrong.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                }
            }
        },
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

/** A zone id paired with the display name of the country it belongs to (per ICU's tz-to-country
 * data), so searching "Lebanon" can find `Asia/Beirut` without the user needing to already know
 * that's the right zone id. Null when ICU doesn't associate the zone with any single country
 * (UTC and the other fixed-offset/"Etc/..." zones, mostly). */
private data class ZoneEntry(val zoneId: String, val countryName: String?)

@Composable
private fun rememberZoneDirectory(): List<ZoneEntry> = remember {
    val canonicalZoneIds = ZoneId.getAvailableZoneIds()
    val zoneToCountry = mutableMapOf<String, String>()
    for (countryCode in Locale.getISOCountries()) {
        val countryName = Locale.Builder().setRegion(countryCode).build().displayCountry
        if (countryName.isBlank()) continue
        for (zoneId in android.icu.util.TimeZone.getAvailableIDs(countryCode)) {
            if (zoneId in canonicalZoneIds) zoneToCountry.putIfAbsent(zoneId, countryName)
        }
    }
    canonicalZoneIds.sorted().map { ZoneEntry(it, zoneToCountry[it]) }
}

@Composable
private fun AddTimezoneDialog(existing: List<String>, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val zoneDirectory = rememberZoneDirectory()
    val results = remember(query, zoneDirectory) {
        if (query.isBlank()) {
            COMMON_ZONE_IDS.map { id -> zoneDirectory.firstOrNull { it.zoneId == id } ?: ZoneEntry(id, null) }
        } else {
            zoneDirectory.filter { entry ->
                entry.zoneId.contains(query, ignoreCase = true) ||
                    entry.countryName?.contains(query, ignoreCase = true) == true
            }.take(100)
        }
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
                    placeholder = { Text("City, region, or country") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp).padding(top = 8.dp)) {
                    items(results, key = { it.zoneId }) { entry ->
                        val alreadyAdded = entry.zoneId in existing
                        ListItem(
                            headlineContent = { Text(entry.zoneId) },
                            supportingContent = entry.countryName?.let { { Text(it) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !alreadyAdded) { onAdd(entry.zoneId) },
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
