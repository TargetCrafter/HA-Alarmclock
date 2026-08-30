package com.targetcrafter.haalarmclock.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.targetcrafter.haalarmclock.ha.HaSyncService
import com.targetcrafter.haalarmclock.ui.alarmlist.AlarmListScreen
import com.targetcrafter.haalarmclock.ui.editor.AlarmEditorScreen
import com.targetcrafter.haalarmclock.ui.settings.SettingsScreen
import com.targetcrafter.haalarmclock.ui.theme.HaAlarmClockTheme

private const val ROUTE_LIST = "list"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_EDITOR = "editor/{alarmId}"
private const val ARG_NEW_ALARM_ID = -1L

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        startHaSyncService()

        setContent {
            HaAlarmClockTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = ROUTE_LIST) {
                    composable(ROUTE_LIST) {
                        AlarmListScreen(
                            onAddAlarm = { navController.navigate("editor/$ARG_NEW_ALARM_ID") },
                            onEditAlarm = { id -> navController.navigate("editor/$id") },
                            onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                        )
                    }
                    composable(ROUTE_EDITOR) { backStackEntry ->
                        val alarmId = backStackEntry.arguments?.getString("alarmId")?.toLongOrNull() ?: ARG_NEW_ALARM_ID
                        AlarmEditorScreen(
                            alarmId = if (alarmId == ARG_NEW_ALARM_ID) null else alarmId,
                            onDone = { navController.popBackStack() },
                        )
                    }
                    composable(ROUTE_SETTINGS) {
                        SettingsScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startHaSyncService() {
        ContextCompat.startForegroundService(this, Intent(this, HaSyncService::class.java))
    }
}
