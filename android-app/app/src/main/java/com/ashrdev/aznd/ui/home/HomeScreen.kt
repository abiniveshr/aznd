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
import com.ashrdev.aznd.ui.components.TopAppBar
import com.ashrdev.aznd.ui.navigation.Screen
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.ui.workout.TopExerciseStat
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    workoutViewModel: WorkoutViewModel,
    streakViewModel: StreakViewModel,
    homeSignal: Int,
    onNavigate: (String) -> Unit
) {
    val pages = HomePageId.entries
    val homeIndex = HomePageId.HOME.ordinal
    // Back from a screen lands on the page you left (History / Home / Dashboard).
    var lastPage by rememberSaveable { mutableIntStateOf(homeIndex) }
    val pagerState = rememberPagerState(initialPage = lastPage, pageCount = { pages.size })
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { lastPage = it }
    }

    // The Home button on other screens asks for the middle Home page specifically.
    var handledSignal by rememberSaveable { mutableIntStateOf(homeSignal) }
    LaunchedEffect(homeSignal) {
        if (homeSignal != handledSignal) {
            handledSignal = homeSignal
            pagerState.scrollToPage(homeIndex)
        }
    }
    val tray = rememberTrayState()
    val scope = rememberCoroutineScope()
    val bottomClearance = bottomBarClearance()

    val streaks by streakViewModel.streaksWithCounts.collectAsState()
    val runningStreaks = remember(streaks) {
        streaks.filter { it.currentStreak > 0 }.sortedByDescending { it.currentStreak }
    }

    var topExercises by remember { mutableStateOf<List<TopExerciseStat>>(emptyList()) }
    var topExercisesLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        topExercises = workoutViewModel.getTopExercises(3)
        topExercisesLoaded = true
    }

    fun goToPage(index: Int) {
        scope.launch { pagerState.animateScrollToPage(index) }
    }

    BackHandler(enabled = pagerState.currentPage != homeIndex) { goToPage(homeIndex) }
    BackHandler(enabled = tray.value > 0f) { scope.launch { tray.settleTo(0f) } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Crossfade(targetState = pagerState.currentPage, label = "pageTitle") { page ->
                        Text(pages[page].title)
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
                    HomePageId.HOME -> HomePage(
                        onNavigate = onNavigate,
                        bottomPadding = bottomClearance
                    )
                    HomePageId.DASHBOARD -> DashboardPage(
                        runningStreaks = runningStreaks,
                        topExercises = topExercises,
                        topExercisesLoaded = topExercisesLoaded,
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
                onOpenSettings = { onNavigate(Screen.Settings.route) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
