package com.ashrdev.aznd.ui.workout.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.EMPTY_WORKOUT_ID
import com.ashrdev.aznd.data.workout.FREESTYLE_NAME
import com.ashrdev.aznd.data.workout.SessionWithSets
import com.ashrdev.aznd.domain.GraphWindow
import com.ashrdev.aznd.domain.streaks.LocalFormat
import com.ashrdev.aznd.domain.streaks.epochDayOrNull
import com.ashrdev.aznd.ui.calendar.DayDecoration
import com.ashrdev.aznd.ui.calendar.DayFill
import com.ashrdev.aznd.ui.calendar.ZoomCalendar
import com.ashrdev.aznd.ui.calendar.dayKeyOf
import com.ashrdev.aznd.ui.calendar.monthKeyOf
import com.ashrdev.aznd.ui.calendar.rememberCalendarState
import com.ashrdev.aznd.ui.calendar.rememberFavoriteDays
import com.ashrdev.aznd.ui.calendar.todayDayKey
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.components.StatTile
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import com.ashrdev.aznd.ui.workout.stats.WorkoutGraphViewModel
import com.ashrdev.aznd.ui.workout.stats.WorkoutStatsPages
import com.ashrdev.aznd.ui.theme.Space
import java.util.Date
import kotlinx.coroutines.launch

private const val PAGE_STATS = 0
private const val PAGE_CALENDAR = 1
private const val PAGE_SESSIONS = 2

private val WorkoutHistoryPages = listOf(
    BarPage("Stats", Icons.Default.BarChart),
    BarPage("Calendar", Icons.Default.CalendarMonth),
    BarPage("Sessions", Icons.AutoMirrored.Filled.List)
)

/**
 * One workout's history (or Freestyle's, [EMPTY_WORKOUT_ID]), three swipeable pages opening on the
 * middle one:   Stats | Calendar | Sessions   (same order for presets and Freestyle)
 *  - Stats (swipe right): a preset gets the workout screen's Stats page ([WorkoutStatsPages]);
 *    Freestyle has no workout-level graphs, so it gets per-exercise stats ([FreestyleExerciseStatsPage]).
 *  - Calendar (centre): the zoomable calendar on top with four stat tiles below it, laid out like
 *    the overall history's centre page; tap a day for a popup of its sessions.
 *  - Sessions (swipe left): this workout's sessions, newest first, filterable by date range and
 *    pictures. Cards are titled with their date. Freestyle also filters by muscle group and by
 *    exercise, and lists each session's exercises on its card. See [HistorySessionListPage].
 */
@Composable
fun WorkoutHistoryScreen(
    workoutId: Long,
    viewModel: WorkoutViewModel,
    graphViewModel: WorkoutGraphViewModel?,
    useTargets: Boolean,
    onOpenSession: (Long) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val sessions by viewModel.sessionsForWorkout(workoutId).collectAsState(initial = emptyList())
    val catalog by viewModel.catalogExercises.collectAsState()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = PAGE_CALENDAR) { WorkoutHistoryPages.size }
    val subPages = rememberSubPageBar(pagerState, WorkoutHistoryPages)

    // Name for the title: from the logged sessions, or the workout itself while none exist yet.
    var workoutName by remember { mutableStateOf("") }
    var workoutHasExercises by remember { mutableStateOf(false) }
    LaunchedEffect(workoutId) {
        if (workoutId == EMPTY_WORKOUT_ID) {
            workoutName = FREESTYLE_NAME
        } else {
            val workout = viewModel.getWorkout(workoutId)
            workoutName = workout?.workout?.name ?: ""
            workoutHasExercises = workout?.exercises?.isNotEmpty() == true
        }
    }
    val displayName = sessions.firstOrNull()?.session?.workoutName
        ?: workoutName.ifEmpty { "Workout" }

    BackHandler(enabled = pagerState.currentPage != PAGE_CALENDAR) {
        scope.launch { pagerState.animateScrollToPage(PAGE_CALENDAR) }
    }

    ScreenScaffold(
        title = "$displayName • History",
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = subPages,
        pageTitle = { page ->
            when (page) {
                PAGE_SESSIONS -> "$displayName • Sessions"
                PAGE_STATS -> "$displayName • Stats"
                else -> "$displayName • History"
            }
        },
        topBarActions = {
            // Share isn't wired up yet, so it sits here disabled.
            IconButton(onClick = {}, enabled = false) {
                Icon(Icons.Default.Share, contentDescription = "Share (coming soon)")
            }
        }
    ) { padding ->
        val bottom = padding.calculateBottomPadding()
        val freestyle = workoutId == EMPTY_WORKOUT_ID
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                PAGE_SESSIONS -> HistorySessionListPage(
                    sessions = sessions,
                    catalog = catalog,
                    bottomPadding = bottom + Space.lg,
                    onOpenSession = onOpenSession,
                    onDeleteSession = { viewModel.deleteSession(it) },
                    showMuscleFilter = freestyle,
                    showExerciseFilter = freestyle,
                    showWorkoutName = false,
                    showExerciseNames = freestyle
                )
                PAGE_STATS -> if (freestyle) {
                    FreestyleExerciseStatsPage(
                        viewModel = viewModel,
                        sessions = sessions,
                        bottomPadding = bottom
                    )
                } else {
                    WorkoutStatsPages(
                        workoutId = workoutId,
                        viewModel = viewModel,
                        hasExercises = sessions.isNotEmpty() || workoutHasExercises,
                        useTargets = useTargets,
                        graphViewModel = graphViewModel,
                        bottomPadding = bottom
                    )
                }
                else -> HistoryCalendarPage(
                    sessions = sessions,
                    favoritesKey = "workout:$workoutId",
                    bottomPadding = bottom,
                    onOpenSession = onOpenSession
                )
            }
        }
    }
}

/**
 * Centre page, laid out like [HistoryOverviewPage]: the zoomable calendar at the top and four stat
 * tiles (times done, average per week, times skipped, last done) in a 2x2 grid below it. The
 * calendar's grid is capped to the space the tiles leave, so nothing needs scrolling.
 *
 * Days with a session are outlined; favourite days swap the outline for a highlight. Tap a day for
 * a popup of its sessions (with the favourite heart). Tap "Average per week" for a popup graph.
 */
@Composable
private fun HistoryCalendarPage(
    sessions: List<SessionWithSets>,
    favoritesKey: String,
    bottomPadding: Dp,
    onOpenSession: (Long) -> Unit
) {
    val favorites = rememberFavoriteDays(favoritesKey)
    // No day starts selected: the popup, not the page, is where a day's sessions are shown.
    val calendarState = rememberCalendarState(initialSelectedDay = null)
    val sessionsByDay = remember(sessions) {
        sessions.sortedBy { it.session.startedAt }.groupBy { dayKeyOf(it.session.startedAt) }
    }
    val validMonths = remember(sessionsByDay) { sessionsByDay.keys.map { monthKeyOf(it) }.distinct() }
    val stats = remember(sessions) { computeHistoryStats(sessions) }
    val lastDoneMillis = remember(sessions) { sessions.maxOfOrNull { it.session.startedAt } }
    var popupDay by rememberSaveable { mutableStateOf<String?>(null) }
    var graphOpen by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gridBudget = (maxHeight - Space.sm - bottomPadding - HistoryStatsHeight - HistorySectionGap - HistoryCalendarHeaderHeight)
            .coerceAtLeast(160.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = HistorySidePadding, end = HistorySidePadding, top = Space.sm, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(HistorySectionGap)
        ) {
            ZoomCalendar(
                decorate = { dayKey ->
                    when {
                        dayKey !in sessionsByDay -> DayDecoration.None
                        favorites.isFavorite(dayKey) -> DayDecoration(fill = DayFill.Highlight)
                        else -> DayDecoration(outline = true)
                    }
                },
                validMonths = validMonths,
                state = calendarState,
                // The page swipe is for reaching the other pages, so the chevrons page the calendar instead.
                swipeNavigation = false,
                maxGridHeight = gridBudget,
                dayTooltip = { dayKey -> sessionCountLabel(sessionsByDay[dayKey]?.size ?: 0) },
                onDayClick = { popupDay = it }
            )
            HistoryTileGrid(
                topLeft = { tile ->
                    StatTile(label = "Times done", value = sessions.size.toString(), modifier = tile)
                },
                topRight = { tile ->
                    StatTile(
                        label = "Average per week",
                        value = GraphWindow.formatValue(stats.sessionsPerWeek),
                        supporting = "tap for graph",
                        modifier = tile.clickable { graphOpen = true }
                    )
                },
                bottomLeft = { tile ->
                    // Skipped days only exist once workouts belong to a split, which isn't
                    // implemented yet (Freestyle too will be connectable to splits).
                    StatTile(label = "Times skipped", value = "—", supporting = "needs a split", modifier = tile)
                },
                bottomRight = { tile ->
                    StatTile(
                        label = "Last done",
                        value = lastDoneMillis?.let { lastDoneFormat.format(Date(it)) } ?: "—",
                        supporting = lastDoneMillis?.let { relativeDayText(dayKeyOf(it)) },
                        modifier = tile
                    )
                }
            )
        }
    }

    if (graphOpen) {
        FrequencyGraphDialog(sessions = sessions, onDismiss = { graphOpen = false })
    }

    popupDay?.let { day ->
        val daySessions = sessionsByDay[day].orEmpty()
        DayPopup(
            dayKey = day,
            sessions = daySessions,
            onOpenSession = { id ->
                popupDay = null
                onOpenSession(id)
            },
            onDismiss = { popupDay = null },
            isFavorite = favorites.isFavorite(day),
            onToggleFavorite = if (daySessions.isNotEmpty()) {
                { favorites.toggle(day) }
            } else null
        )
    }
}

private val lastDoneFormat = LocalFormat("d MMM yyyy")

/** Tooltip for a day of one workout's calendar; every session there has the same name, so count them. */
private fun sessionCountLabel(count: Int): String = when (count) {
    0 -> "No session on this day"
    1 -> "1 session"
    else -> "$count sessions"
}

/** "Today", "Yesterday" or "N days ago" for a past [dayKey]; the date itself if it can't be parsed. */
private fun relativeDayText(dayKey: String): String {
    val then = epochDayOrNull(dayKey) ?: return dayKey
    val today = epochDayOrNull(todayDayKey()) ?: return dayKey
    return when (val days = today - then) {
        0 -> "Today"
        1 -> "Yesterday"
        else -> if (days > 1) "$days days ago" else dayKey
    }
}
