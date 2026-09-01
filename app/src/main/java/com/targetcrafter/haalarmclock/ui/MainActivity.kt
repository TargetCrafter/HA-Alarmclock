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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.targetcrafter.haalarmclock.HaAlarmClockApp
import com.targetcrafter.haalarmclock.R
import com.targetcrafter.haalarmclock.ha.startHaSyncServiceIfConfigured
import com.targetcrafter.haalarmclock.ui.alarmlist.AlarmListScreen
import com.targetcrafter.haalarmclock.ui.clock.ClockScreen
import com.targetcrafter.haalarmclock.ui.settings.SettingsScreen
import com.targetcrafter.haalarmclock.ui.theme.HaAlarmClockTheme
import com.targetcrafter.haalarmclock.ui.timerlist.TimerListScreen
import kotlinx.coroutines.launch

private const val ROUTE_TABS = "tabs"
private const val ROUTE_SETTINGS = "settings"

/** Clock first, per the reordering the user asked for. */
private enum class Tab(
    val labelRes: Int,
    val addLabelRes: Int,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
) {
    CLOCK(R.string.tab_clock, R.string.add_timezone, Icons.Filled.AccessTime, Icons.Outlined.AccessTime),
    ALARMS(R.string.tab_alarms, R.string.add_alarm, Icons.Filled.Alarm, Icons.Outlined.Alarm),
    TIMERS(R.string.tab_timers, R.string.add_timer, Icons.Filled.Timer, Icons.Outlined.Timer),
}

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
                // Only Tabs <-> Settings is a real navigation; switching between the three tabs
                // themselves is a HorizontalPager, not a nav-graph destination — see TabsScreen. A
                // fade here showed a visible artifact that a plain instant cut can't: with no
                // animated frames at all, there's nothing for a partial-alpha blend to go wrong on.
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_TABS,
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None },
                ) {
                    composable(ROUTE_TABS) {
                        TabsScreen(onOpenSettings = { navController.navigate(ROUTE_SETTINGS) })
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabsScreen(onOpenSettings: () -> Unit) {
    val app = HaAlarmClockApp.from(LocalContext.current)
    val savedTabIndex by app.tabPreferencesStore.lastTabIndex.collectAsState()
    val pagerState = rememberPagerState(initialPage = savedTabIndex.coerceIn(0, Tab.entries.size - 1)) { Tab.entries.size }
    val scope = rememberCoroutineScope()
    val currentTab = Tab.entries[pagerState.currentPage]

    // Which page's add-sheet is open, keyed by page index rather than a plain boolean, so a swipe
    // away from the tab that opened it can't leak "show add" into whichever page lands under it.
    var pendingAddPage by remember { mutableStateOf<Int?>(null) }

    // Persists the tab the user ends up on, whether they got there by swiping or tapping the
    // bottom nav, so the app reopens on it next time instead of always defaulting to Clock.
    LaunchedEffect(pagerState.currentPage) {
        app.tabPreferencesStore.save(pagerState.currentPage)
    }

    Scaffold(
        topBar = {
            // Shared, non-swiping chrome: only the title text tracks the current page.
            TopAppBar(
                title = { Text(stringResource(currentTab.labelRes)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { index, tab ->
                    val selected = pagerState.currentPage == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        icon = { Icon(if (selected) tab.filledIcon else tab.outlinedIcon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
        floatingActionButton = {
            // Also shared and fixed in place; only its label tracks the current page.
            ExtendedFloatingActionButton(
                onClick = { pendingAddPage = pagerState.currentPage },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(currentTab.addLabelRes)) },
            )
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
        ) { page ->
            when (Tab.entries[page]) {
                Tab.CLOCK -> ClockScreen(
                    showAddDialog = pendingAddPage == page,
                    onAddDialogDismiss = { pendingAddPage = null },
                )
                Tab.ALARMS -> AlarmListScreen(
                    showAddSheet = pendingAddPage == page,
                    onAddSheetDismiss = { pendingAddPage = null },
                )
                Tab.TIMERS -> TimerListScreen(
                    showAddDialog = pendingAddPage == page,
                    onAddDialogDismiss = { pendingAddPage = null },
                )
            }
        }
    }
}
