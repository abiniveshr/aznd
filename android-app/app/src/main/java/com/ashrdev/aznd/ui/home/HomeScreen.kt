package com.ashrdev.aznd.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ashrdev.aznd.ui.common.TrayMemory
import com.ashrdev.aznd.ui.common.bottomBarClearance
import com.ashrdev.aznd.ui.common.rememberTrayState
import com.ashrdev.aznd.ui.common.settleTo
import com.ashrdev.aznd.ui.common.share.FlexPreset
import com.ashrdev.aznd.ui.common.share.route
import com.ashrdev.aznd.ui.components.TopAppBar
import com.ashrdev.aznd.ui.navigation.Destination
import com.ashrdev.aznd.ui.theme.ThemeColor
import com.ashrdev.aznd.ui.theme.themedFill
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.ui.workout.TopExerciseStat
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.produceState
import com.ashrdev.aznd.data.macro.MacroTargetsStore
import com.ashrdev.aznd.domain.macro.MacroStats
import com.ashrdev.aznd.ui.dashboard.DashWidgetType
import com.ashrdev.aznd.ui.dashboard.DashboardData
import com.ashrdev.aznd.ui.dashboard.DashboardSeries
import com.ashrdev.aznd.ui.dashboard.DashboardStore
import com.ashrdev.aznd.ui.dashboard.DashboardViewModel
import com.ashrdev.aznd.ui.dashboard.rememberDashboardModes

private const val TOP_LIFTS = 3
private const val CLOCK_TICK_MS = 60_000L

@Composable
fun HomeScreen(
    workoutViewModel: WorkoutViewModel,
    streakViewModel: StreakViewModel,
    dashboardViewModel: DashboardViewModel,
    dashboardStore: DashboardStore,
    macroTargets: MacroTargetsStore,
    homeSignal: Int,
    onNavigate: (String) -> Unit
) {
    val pages = HomePageId.entries
    val homeIndex = HomePageId.DASHBOARD.ordinal
    // Back from a screen lands on the page you left (History / Home / Dashboard).
    var lastPage by rememberSaveable { mutableIntStateOf(homeIndex) }
    val pagerState = rememberPagerState(initialPage = lastPage, pageCount = { pages.size })
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { lastPage = it }
    }

    // The Home button on other screens asks for the middle Dashboard page specifically.
    var handledSignal by rememberSaveable { mutableIntStateOf(homeSignal) }
    LaunchedEffect(homeSignal) {
        if (homeSignal != handledSignal) {
            handledSignal = homeSignal
            pagerState.scrollToPage(homeIndex)
        }
    }
    val tray = rememberTrayState()
    // This screen has no Back button, so a pending "keep the bar pulled up" ends here.
    LaunchedEffect(Unit) { TrayMemory.clear() }
    val scope = rememberCoroutineScope()
    val bottomClearance = bottomBarClearance()

    val streaks by streakViewModel.streaksWithCounts.collectAsState()
    val sessions by workoutViewModel.allSessions.collectAsState()
    val workouts by workoutViewModel.workouts.collectAsState()
    val food by dashboardViewModel.food.collectAsState()
    val weights by dashboardViewModel.weights.collectAsState()
    val measurements by dashboardViewModel.measurements.collectAsState()
    val burnt by dashboardViewModel.burntByDay.collectAsState()
    val widgets = dashboardStore.widgets
    val liftLimit = remember(widgets) {
        maxOf(TOP_LIFTS, widgets.filter { it.type == DashWidgetType.TOP_LIFT }.maxOfOrNull { it.rank } ?: 0)
    }

    var topExercises by remember { mutableStateOf<List<TopExerciseStat>>(emptyList()) }

    LaunchedEffect(liftLimit, sessions.size) {
        topExercises = workoutViewModel.getTopExercises(liftLimit)
    }
    LaunchedEffect(sessions.size) { dashboardViewModel.refresh(sessions.size) }

    val nowMs by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(CLOCK_TICK_MS)
            value = System.currentTimeMillis()
        }
    }
    val modes = rememberDashboardModes(dashboardViewModel, sessions)
    val dayStart = MacroStats.dayStart(nowMs)
    val today = remember(food, burnt, weights, measurements, dayStart, macroTargets.goal, macroTargets.advancedOffset, macroTargets.proteinG, macroTargets.carbsG, macroTargets.fatG) {
        DashboardSeries.today(
            food, burnt, weights, measurements, macroTargets.goal, macroTargets.advancedOffset,
            macroTargets.proteinG, macroTargets.carbsG, macroTargets.fatG, nowMs
        )
    }
    val minute = nowMs / CLOCK_TICK_MS
    val dashboardData = remember(streaks, topExercises, sessions, workouts, today, modes, minute) {
        DashboardData(streaks, topExercises, sessions, workouts, today, modes, nowMs)
    }

    fun goToPage(index: Int) {
        scope.launch { pagerState.animateScrollToPage(index) }
    }

    BackHandler(enabled = pagerState.currentPage != homeIndex) { goToPage(homeIndex) }
    BackHandler(enabled = tray.value > 0f) { scope.launch { tray.settleTo(0f) } }

    Scaffold(
        modifier = Modifier.fillMaxSize().themedFill(ThemeColor.BACKGROUND),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = {
                    Crossfade(targetState = pagerState.currentPage, label = "pageTitle") { page ->
                        Text(pages[page].title)
                    }
                },
                actions = {
                    if (pagerState.currentPage == homeIndex) {
                        IconButton(onClick = { onNavigate(Destination.DashboardSettings.createRoute()) }) {
                            Icon(Icons.Default.Tune, contentDescription = "Dashboard settings")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (pages[page]) {
                    HomePageId.HISTORY -> HistoryPage(
                        workoutViewModel = workoutViewModel,
                        onNavigate = onNavigate,
                        bottomPadding = bottomClearance
                    )
                    HomePageId.DASHBOARD -> DashboardPage(
                        widgets = widgets,
                        data = dashboardData,
                        onNavigate = onNavigate,
                        onOpenSettings = { focus -> onNavigate(Destination.DashboardSettings.createRoute(focus)) },
                        bottomPadding = bottomClearance
                    )
                    HomePageId.APPS -> AppsPage(
                        onNavigate = onNavigate,
                        bottomPadding = bottomClearance
                    )
                }
            }

            if (tray.value > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f * tray.value.coerceIn(0f, 1f)))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { scope.launch { tray.settleTo(0f) } }
                )
            }

            HomeBottomBar(
                pagerState = pagerState,
                tray = tray,
                onSelectPage = { goToPage(it) },
                onOpenSettings = { onNavigate(Destination.Settings.route) },
                onShare = { onNavigate(FlexPreset.HOME.route()) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
