package com.ashrdev.aznd.ui.home

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ashrdev.aznd.ui.common.SlidingPageActions
import com.ashrdev.aznd.ui.common.SlidingPageTitle
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
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.ashrdev.aznd.ui.dashboard.DashboardStore
import com.ashrdev.aznd.ui.dashboard.DashboardViewModel
import com.ashrdev.aznd.ui.dashboard.rememberDashboardData
import com.ashrdev.aznd.data.macro.MacroTargetsStore

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

    val dashboardData = rememberDashboardData(workoutViewModel, streakViewModel, dashboardViewModel, dashboardStore, macroTargets)

    val pagePosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction }

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
                // Title and the Dashboard-only Tune button follow the swipe instead of popping at 50 %.
                title = {
                    SlidingPageTitle(
                        pageCount = pages.size,
                        position = pagePosition,
                        title = { pages[it].title }
                    )
                },
                actions = {
                    SlidingPageActions(pageCount = pages.size, position = pagePosition) { page ->
                        if (page == homeIndex) {
                            IconButton(onClick = { onNavigate(Destination.DashboardSettings.route) }) {
                                Icon(Icons.Default.Tune, contentDescription = "Dashboard settings")
                            }
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
                        store = dashboardStore,
                        data = dashboardData,
                        onNavigate = onNavigate,
                        // Long press: edit mode for the page you're on.
                        onEdit = { presetId, widget -> onNavigate(Destination.DashboardEdit.createRoute(presetId, widget)) },
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
