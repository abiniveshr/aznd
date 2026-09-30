package com.ashrdev.aznd.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
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
    onNavigate: (String) -> Unit
) {
    val pages = HomePageId.entries
    val homeIndex = HomePageId.HOME.ordinal
    val pagerState = rememberPagerState(initialPage = homeIndex, pageCount = { pages.size })
    val tray = remember { Animatable(0f) }
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
    BackHandler(enabled = tray.value > 0f) { scope.launch { tray.animateTo(0f) } }

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
                        .background(Color.Black.copy(alpha = 0.45f * tray.value))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { scope.launch { tray.animateTo(0f) } }
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