package com.targetcrafter.haalarmclock.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetcrafter.haalarmclock.R
import com.targetcrafter.haalarmclock.ui.theme.HaAlarmClockTheme

class RingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpLockScreenVisibility()
        enableEdgeToEdge()
        setContent {
            HaAlarmClockTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RingingScreen(onFinish = { finish() })
                }
            }
        }
    }

    private fun setUpLockScreenVisibility() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun RingingScreen(onFinish: () -> Unit) {
    val alarm by RingingState.current.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(alarm) {
        if (alarm == null) onFinish()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Alarm, contentDescription = null, modifier = Modifier.padding(bottom = 24.dp))
        Text(
            text = alarm?.let { String.format("%02d:%02d", it.hour, it.minute) } ?: "",
            style = MaterialTheme.typography.displayLarge,
            fontSize = 64.sp,
        )
        val label = alarm?.label.orEmpty()
        if (label.isNotBlank()) {
            Text(text = label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedButton(
                onClick = { AlarmActions.snooze(context) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.snooze)) }
            Button(
                onClick = { AlarmActions.dismiss(context) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(),
            ) { Text(stringResource(R.string.dismiss)) }
        }
    }
}
