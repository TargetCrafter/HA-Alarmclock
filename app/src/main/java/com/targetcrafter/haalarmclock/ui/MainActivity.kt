package com.targetcrafter.haalarmclock.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.targetcrafter.haalarmclock.ha.startHaSyncServiceIfConfigured
import com.targetcrafter.haalarmclock.ui.alarmlist.AlarmListScreen
import com.targetcrafter.haalarmclock.ui.settings.SettingsScreen
import com.targetcrafter.haalarmclock.ui.theme.HaAlarmClockTheme

private const val ROUTE_LIST = "list"
private const val ROUTE_SETTINGS = "settings"

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        startHaSyncServiceIfConfigured(this)

        setContent {
            HaAlarmClockTheme {
                val navController = rememberNavController()
                // Alarm editing now happens in-place on the list screen (quick time popup,
                // full-options sheet); only List <-> Settings is a real navigation. A fade here
                // showed a visible artifact that a plain instant cut can't: with no animated
                // frames at all, there's nothing for a partial-alpha blend to go wrong on.
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_LIST,
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None },
                ) {
                    composable(ROUTE_LIST) {
                        AlarmListScreen(onOpenSettings = { navController.navigate(ROUTE_SETTINGS) })
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
}
