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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.targetcrafter.haalarmclock.R
import com.targetcrafter.haalarmclock.ha.startHaSyncServiceIfConfigured
import com.targetcrafter.haalarmclock.ui.alarmlist.AlarmListScreen
import com.targetcrafter.haalarmclock.ui.settings.SettingsScreen
import com.targetcrafter.haalarmclock.ui.theme.HaAlarmClockTheme
import com.targetcrafter.haalarmclock.ui.timerlist.TimerListScreen

private const val ROUTE_LIST = "list"
private const val ROUTE_TIMERS = "timers"
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
                val currentDestination by navController.currentBackStackEntryAsState()
                val showBottomBar = currentDestination?.destination?.hierarchy?.any {
                    it.route == ROUTE_LIST || it.route == ROUTE_TIMERS
                } == true

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentDestination?.destination?.hierarchy?.any { it.route == ROUTE_LIST } == true,
                                    onClick = { navController.navigateToTab(ROUTE_LIST) },
                                    icon = { Icon(Icons.Filled.Alarm, contentDescription = null) },
                                    label = { Text(stringResource(R.string.tab_alarms)) },
                                )
                                NavigationBarItem(
                                    selected = currentDestination?.destination?.hierarchy?.any { it.route == ROUTE_TIMERS } == true,
                                    onClick = { navController.navigateToTab(ROUTE_TIMERS) },
                                    icon = { Icon(Icons.Filled.Timer, contentDescription = null) },
                                    label = { Text(stringResource(R.string.tab_timers)) },
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    // Alarm/timer editing happens in-place on their own list screens (quick time
                    // popup, full-options sheet, add-timer dialog); only List/Timers <-> Settings
                    // is a real navigation. A fade here showed a visible artifact that a plain
                    // instant cut can't: with no animated frames at all, there's nothing for a
                    // partial-alpha blend to go wrong on.
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_LIST,
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None },
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        composable(ROUTE_LIST) {
                            AlarmListScreen(onOpenSettings = { navController.navigate(ROUTE_SETTINGS) })
                        }
                        composable(ROUTE_TIMERS) {
                            TimerListScreen(onOpenSettings = { navController.navigate(ROUTE_SETTINGS) })
                        }
                        composable(ROUTE_SETTINGS) {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }
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

/** Standard bottom-nav-tab navigation: switching tabs doesn't grow the back stack, and returning
 * to a previously-visited tab restores its scroll/dialog state instead of resetting it. */
private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
