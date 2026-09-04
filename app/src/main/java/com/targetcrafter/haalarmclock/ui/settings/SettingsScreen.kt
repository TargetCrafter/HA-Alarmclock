package com.targetcrafter.haalarmclock.ui.settings

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.targetcrafter.haalarmclock.HaAlarmClockApp
import com.targetcrafter.haalarmclock.alarm.AlarmSoundTestResult
import com.targetcrafter.haalarmclock.alarm.ScheduleHealth
import com.targetcrafter.haalarmclock.alarm.alarmVolume
import com.targetcrafter.haalarmclock.alarm.checkScheduleHealth
import com.targetcrafter.haalarmclock.alarm.startAlarmSoundTest
import com.targetcrafter.haalarmclock.data.AppDefaults
import com.targetcrafter.haalarmclock.data.ClockStyle
import com.targetcrafter.haalarmclock.data.WidgetAppearance
import com.targetcrafter.haalarmclock.ha.HaConnectionState
import com.targetcrafter.haalarmclock.ha.HaSettings
import com.targetcrafter.haalarmclock.ha.startHaSyncServiceIfConfigured
import com.targetcrafter.haalarmclock.ui.appViewModelFactory
import com.targetcrafter.haalarmclock.util.canUseFullScreenIntent
import com.targetcrafter.haalarmclock.util.isIgnoringBatteryOptimizations
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PRESET_COLORS = listOf(
    0xFF1B1B1B.toInt(), // near-black (default widget background)
    0xFFE8E8E8.toInt(), // light grey (default widget foreground)
    0xFF000000.toInt(),
    0xFFFFFFFF.toInt(),
    0xFF6B6B6B.toInt(),
    0xFF1B1F3B.toInt(), // app's own navy
    0xFFFFB84D.toInt(), // app's own amber
    0xFFEF5350.toInt(),
    0xFF66BB6A.toInt(),
    0xFF42A5F5.toInt(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = HaAlarmClockApp.from(context)
    val viewModel: SettingsViewModel = viewModel(
        factory = appViewModelFactory {
            SettingsViewModel(app.haSettingsStore, app.appDefaultsStore, app.clockPreferencesStore, app.widgetAppearanceStore, app.haWebSocketClient)
        },
    )
    val persisted by viewModel.settings.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val persistedDefaults by viewModel.appDefaults.collectAsState()
    val persistedClockStyle by viewModel.clockStyle.collectAsState()
    val persistedWidgetAppearance by viewModel.widgetAppearance.collectAsState()

    var enabled by remember { mutableStateOf(persisted.enabled) }
    var baseUrl by remember { mutableStateOf(persisted.baseUrl) }
    var accessToken by remember { mutableStateOf(persisted.accessToken) }
    var defaultSnoozeMinutes by remember { mutableIntStateOf(persistedDefaults.snoozeMinutes) }
    var defaultFadeInSeconds by remember { mutableIntStateOf(persistedDefaults.fadeInSeconds) }
    var clockStyle by remember { mutableStateOf(persistedClockStyle) }
    var widgetAppearance by remember { mutableStateOf(persistedWidgetAppearance) }
    var accessTokenVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        enabled = persisted.enabled
        baseUrl = persisted.baseUrl
        accessToken = persisted.accessToken
        defaultSnoozeMinutes = persistedDefaults.snoozeMinutes
        defaultFadeInSeconds = persistedDefaults.fadeInSeconds
        clockStyle = persistedClockStyle
        widgetAppearance = persistedWidgetAppearance
    }

    fun save() {
        viewModel.save(
            HaSettings(
                enabled = enabled,
                baseUrl = baseUrl.trim(),
                accessToken = accessToken.trim(),
            ),
        )
        viewModel.saveAppDefaults(
            AppDefaults(
                snoozeMinutes = defaultSnoozeMinutes,
                fadeInSeconds = defaultFadeInSeconds,
            ),
        )
        viewModel.saveClockStyle(clockStyle)
        viewModel.saveWidgetAppearance(widgetAppearance)
        // The sync service only ever gets started when sync is configured (so its notification
        // doesn't show otherwise); if the user just turned it on, start it now instead of waiting
        // for the next app launch. A no-op if already running.
        startHaSyncServiceIfConfigured(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            // Floating and outside the scrollable column so it's always reachable, however far
            // down the settings list you've scrolled — not just when you happen to be at the bottom.
            ExtendedFloatingActionButton(
                onClick = ::save,
                icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                text = { Text("Save") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ReliabilitySection()

            HorizontalDivider()

            Text("Alarm defaults", style = MaterialTheme.typography.titleMedium)
            Text(
                "Applied to every alarm unless it sets its own value in the alarm's own editor.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SteppedValueRow(
                label = "Snooze duration",
                value = defaultSnoozeMinutes,
                unit = "min",
                onValueChange = { defaultSnoozeMinutes = it.coerceIn(1, 60) },
            )
            SteppedValueRow(
                label = "Fade-in duration",
                value = defaultFadeInSeconds,
                unit = "s",
                step = 5,
                onValueChange = { defaultFadeInSeconds = it.coerceIn(5, 300) },
            )

            HorizontalDivider()

            Text("Clock", style = MaterialTheme.typography.titleMedium)
            Text(
                "Which style the Clock tab's local-time display uses.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ClockStyle.entries.forEachIndexed { index, style ->
                    SegmentedButton(
                        selected = clockStyle == style,
                        onClick = { clockStyle = style },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ClockStyle.entries.size),
                    ) {
                        Text(if (style == ClockStyle.DIGITAL) "Digital" else "Analog")
                    }
                }
            }

            HorizontalDivider()

            Text("Home screen widgets", style = MaterialTheme.typography.titleMedium)
            Text(
                "Colors for both the analog and digital clock widgets. Grayscale by default so " +
                    "they fit most wallpapers/launchers.",
                style = MaterialTheme.typography.bodyMedium,
            )
            ColorPickerRow(
                label = "Background",
                colorArgb = widgetAppearance.backgroundColor,
                onColorChange = { widgetAppearance = widgetAppearance.copy(backgroundColor = it) },
            )
            ColorPickerRow(
                label = "Foreground (hands/text)",
                colorArgb = widgetAppearance.foregroundColor,
                onColorChange = { widgetAppearance = widgetAppearance.copy(foregroundColor = it) },
            )
            TextButton(onClick = { widgetAppearance = WidgetAppearance() }) {
                Text("Reset to default (grayscale)")
            }

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Sync with Home Assistant") },
                supportingContent = { Text(connectionStatusLabel(connectionState)) },
                trailingContent = { Switch(checked = enabled, onCheckedChange = { enabled = it }) },
            )
            HorizontalDivider()

            Text(
                "Requires the \"HA Alarm Clock\" custom integration installed in Home Assistant, " +
                    "and a Long-Lived Access Token from your HA user profile (Security tab).",
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Home Assistant URL") },
                placeholder = { Text("https://homeassistant.local:8123") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            if (baseUrl.trim().startsWith("http://", ignoreCase = true)) {
                ReliabilityWarningRow(
                    title = "Unencrypted connection",
                    description = "This URL uses http://, so your access token is sent in the " +
                        "clear and anyone on the same network can read it — and that token can " +
                        "control all of Home Assistant, not just alarms. Fine on a network you " +
                        "trust; use https:// otherwise.",
                )
            }

            OutlinedTextField(
                value = accessToken,
                onValueChange = { accessToken = it },
                label = { Text("Long-Lived Access Token") },
                supportingText = { Text("Generate one in Home Assistant under your profile's Security tab.") },
                singleLine = true,
                visualTransformation = if (accessTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { accessTokenVisible = !accessTokenVisible }) {
                        Icon(
                            if (accessTokenVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (accessTokenVisible) "Hide token" else "Show token",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    val target = baseUrl.trim().trimEnd('/').ifBlank { return@OutlinedButton }
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$target/profile/security")))
                },
                enabled = baseUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open Home Assistant profile") }
        }
    }
}

/**
 * OS-level settings that can silently stop alarms from ringing even though the app scheduled them
 * correctly: aggressive battery management killing the app before the alarm fires, and (Android
 * 14+) the OS refusing to let a notification's fullScreenIntent actually launch the ringing screen.
 * Neither can be fixed from app code alone — the user has to grant them once, so this surfaces
 * their current status with a one-tap fix.
 */
@Composable
private fun ReliabilitySection() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var batteryUnrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var fullScreenIntentAllowed by remember { mutableStateOf(canUseFullScreenIntent(context)) }
    var volume by remember { mutableStateOf(alarmVolume(context)) }

    // None of these are changeable from in here — the first two need a system settings screen, and
    // the volume is changed with the hardware keys — so re-check on every return to this screen
    // rather than relying on a callback.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryUnrestricted = isIgnoringBatteryOptimizations(context)
                fullScreenIntentAllowed = canUseFullScreenIntent(context)
                volume = alarmVolume(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Text("Alarm reliability", style = MaterialTheme.typography.titleMedium)
    if (!batteryUnrestricted || !fullScreenIntentAllowed || volume.isSilent) {
        Text(
            "These are Android system settings, not part of this app — they can silently stop an " +
                "alarm from ringing even when it's scheduled correctly, and only you can change them.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (volume.isSilent) {
        ReliabilityWarningRow(
            title = "Alarm volume is at zero",
            description = "Alarms play on Android's separate alarm volume, which is currently " +
                "silent — so an alarm will run correctly and you still won't hear it. Raise it " +
                "with the volume keys while the test below is playing.",
        )
    }
    if (!batteryUnrestricted) {
        ReliabilityWarningRow(
            title = "Battery optimization is restricting this app",
            description = "The most common reason an alarm silently doesn't go off: the OS kills " +
                "the app in the background before the alarm fires. Exempt it to fix this.",
            buttonLabel = "Fix",
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                )
            },
        )
    }
    if (!fullScreenIntentAllowed) {
        ReliabilityWarningRow(
            title = "Full-screen alarm not permitted",
            description = "Without this, the ringing screen may not show over the lock screen — " +
                "the alarm can still play sound/vibration, but you may not see it.",
            buttonLabel = "Fix",
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
                )
            },
        )
    }
    ScheduleHealthRow()
    ScheduleDropHistoryRow()
    AlarmSoundTestRow()
}

/**
 * Shows how often Android has been caught having dropped the alarm schedule. Without this the
 * self-repair on app start would hide its own reason for existing: the schedule is put back before
 * anyone can see it was gone, so every morning-after inspection looks healthy no matter what
 * happened overnight.
 */
@Composable
private fun ScheduleDropHistoryRow() {
    val context = LocalContext.current
    val app = HaAlarmClockApp.from(context)
    val audit by app.alarmScheduleAudit.state.collectAsState()

    if (!audit.hasDrops) return

    val formatter = remember { DateTimeFormatter.ofPattern("d MMM, HH:mm") }
    val lastDrop = remember(audit.lastDropAtMillis) {
        Instant.ofEpochMilli(audit.lastDropAtMillis).atZone(ZoneId.systemDefault()).format(formatter)
    }
    ReliabilityWarningRow(
        title = "Android has dropped your alarms ${audit.dropCount}×",
        description = "Most recently on $lastDrop, found and re-armed when the app next started. " +
            "This is the system force-stopping the app, not the alarm failing to ring — no code " +
            "in here can prevent it, so it has to be fixed in the phone's battery settings.",
        buttonLabel = "Reset",
        onClick = { app.alarmScheduleAudit.clear() },
    )
}

/**
 * Reports whether Android itself still holds the alarm the app thinks is next. The app's own list
 * is not evidence: a force-stop drops the OS's alarm entries silently, so an alarm can read as
 * enabled here while nothing is actually scheduled — which is invisible until the morning it
 * doesn't ring. See [checkScheduleHealth].
 */
@Composable
private fun ScheduleHealthRow() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val app = HaAlarmClockApp.from(context)
    val scope = rememberCoroutineScope()
    val alarms by app.repository.alarms.collectAsState(initial = emptyList())

    var health by remember { mutableStateOf<ScheduleHealth>(ScheduleHealth.NoAlarms) }
    // Recheck whenever the alarms change and on every return to the screen — the OS can drop the
    // entry at any time, without anything here changing.
    LaunchedEffect(alarms) { health = checkScheduleHealth(context, alarms) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) health = checkScheduleHealth(context, alarms)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (health) {
        is ScheduleHealth.Missing -> ReliabilityWarningRow(
            title = "Android isn't holding your next alarm",
            description = "The alarm is switched on here, but the system has no record of it — " +
                "which means it will not ring. This happens when the app gets force-stopped, " +
                "usually by a battery manager. Re-arm it, then exempt the app from battery " +
                "optimization above and from any \"sleeping apps\" list your phone has.",
            buttonLabel = "Re-arm",
            onClick = {
                scope.launch {
                    app.repository.rescheduleAll()
                    health = checkScheduleHealth(context, alarms)
                }
            },
        )
        is ScheduleHealth.Registered -> ListItem(
            headlineContent = { Text("Next alarm is registered with Android") },
            supportingContent = { Text("The system is holding it, so it will ring even if this app is closed.") },
        )
        is ScheduleHealth.Inconclusive -> ListItem(
            headlineContent = { Text("Another app has a sooner alarm") },
            supportingContent = { Text("That hides this app's alarm from the check, so it can't be confirmed from here.") },
        )
        is ScheduleHealth.NoAlarms -> Unit
    }
}

/**
 * Plays the alarm sound on demand, through the same ringtone candidates and audio attributes a
 * real alarm uses. An alarm that fails only at 07:00 is close to undebuggable — this turns
 * "wait until tomorrow morning to find out" into a ten-second check.
 */
@Composable
private fun AlarmSoundTestRow() {
    val context = LocalContext.current
    val playing = remember { mutableStateOf<MediaPlayer?>(null) }
    val status = remember { mutableStateOf<String?>(null) }

    // Never leave the sound playing if the screen goes away while it's running.
    DisposableEffect(Unit) {
        onDispose { releaseQuietly(playing.value) }
    }

    ListItem(
        headlineContent = { Text("Test the alarm sound") },
        supportingContent = {
            Text(
                status.value ?: "Plays through the same audio path a real alarm uses, so you can " +
                    "check it's actually audible without waiting for one.",
            )
        },
        trailingContent = {
            TextButton(
                onClick = {
                    if (playing.value != null) {
                        releaseQuietly(playing.value)
                        playing.value = null
                        status.value = null
                    } else {
                        when (val result = startAlarmSoundTest(context)) {
                            is AlarmSoundTestResult.Playing -> {
                                playing.value = result.player
                                val volume = alarmVolume(context)
                                status.value = if (volume.isSilent) {
                                    "Playing — but the alarm volume is at zero, so there's nothing " +
                                        "to hear. Turn it up with the volume keys now."
                                } else {
                                    "Playing at ${volume.percent}% alarm volume. If you can't hear " +
                                        "this, you wouldn't hear an alarm either."
                                }
                            }
                            is AlarmSoundTestResult.Failed -> status.value = result.reason
                        }
                    }
                },
            ) { Text(if (playing.value != null) "Stop" else "Play") }
        },
    )
}

/** stop() throws if the player was never started, and this runs from teardown paths where that
 * isn't worth caring about — the release is what matters. */
private fun releaseQuietly(player: MediaPlayer?) {
    player ?: return
    runCatching { player.stop() }
    player.release()
}

/** [buttonLabel]/[onClick] are optional: some warnings (an http:// URL) are fixed by editing a
 * field on this screen rather than by a jump to a system settings page. */
@Composable
private fun ReliabilityWarningRow(
    title: String,
    description: String,
    buttonLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        leadingContent = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = if (buttonLabel != null && onClick != null) {
            { TextButton(onClick = onClick) { Text(buttonLabel) } }
        } else {
            null
        },
    )
}

@Composable
private fun SteppedValueRow(label: String, value: Int, unit: String, onValueChange: (Int) -> Unit, step: Int = 1) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledIconButton(onClick = { onValueChange(value - step) }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
                }
                Text("$value $unit", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
                FilledIconButton(onClick = { onValueChange(value + step) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase $label")
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColorPickerRow(label: String, colorArgb: Int, onColorChange: (Int) -> Unit) {
    var hexInput by remember(colorArgb) { mutableStateOf(hexString(colorArgb)) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (preset in PRESET_COLORS) {
                val selected = preset == colorArgb
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(preset))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable { onColorChange(preset) },
                )
            }
        }
        OutlinedTextField(
            value = hexInput,
            onValueChange = { text ->
                hexInput = text
                parseHexColor(text)?.let(onColorChange)
            },
            label = { Text("Hex") },
            singleLine = true,
            modifier = Modifier.width(160.dp).padding(top = 8.dp),
        )
    }
}

private fun hexString(colorArgb: Int): String = "#%06X".format(colorArgb and 0xFFFFFF)

private fun parseHexColor(text: String): Int? {
    val cleaned = text.removePrefix("#")
    if (cleaned.length != 6) return null
    val rgb = cleaned.toIntOrNull(16) ?: return null
    return (0xFF shl 24) or rgb
}

private fun connectionStatusLabel(state: HaConnectionState): String = when (state) {
    HaConnectionState.DISCONNECTED -> "Not connected"
    HaConnectionState.CONNECTING -> "Connecting…"
    HaConnectionState.CONNECTED -> "Connected"
    HaConnectionState.ERROR -> "Connection error — check URL/token and that the integration is installed"
}
