package com.ashrdev.aznd.ui.streaks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.routines.StreakTrack
import com.ashrdev.aznd.data.streaks.StreakEntity
import com.ashrdev.aznd.data.streaks.parseMilestones
import com.ashrdev.aznd.domain.streaks.StreakStats
import com.ashrdev.aznd.domain.streaks.StreakTracking
import com.ashrdev.aznd.domain.streaks.computeCurrentStreak
import com.ashrdev.aznd.domain.streaks.computeStreakStats
import com.ashrdev.aznd.domain.streaks.displayDate
import com.ashrdev.aznd.domain.streaks.todayKey
import com.ashrdev.aznd.ui.calendar.CalendarState
import com.ashrdev.aznd.ui.calendar.rememberCalendarState
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.components.AlertDialog

private const val PAGE_GRAPH = 0
private const val PAGE_DETAILS = 1
private const val PAGE_CALENDAR = 2

private val DetailPages = listOf(
    BarPage("Progress", Icons.Default.BarChart),
    BarPage("Details", Icons.Default.LocalFireDepartment),
    BarPage("Calendar", Icons.Default.DateRange)
)

class DayAction(val label: String, val run: () -> Unit)

/**
 * The view shared by a streak and a routine: Progress | Details | Calendar, opening on Details.
 * What differs between them comes in as parameters: the top-bar actions, what tapping a day can do,
 * and the buttons under the calendar.
 */
@Composable
fun TrackedDetailScreen(
    title: String,
    streak: StreakEntity,
    track: StreakTrack,
    savedDays: Set<String>,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    topBarActions: @Composable RowScope.() -> Unit,
    onPickBanner: () -> Unit,
    onPickLogo: () -> Unit,
    dayAction: (String) -> DayAction?,
    calendarActions: @Composable ColumnScope.() -> Unit
) {
    var tappedDate by remember { mutableStateOf<String?>(null) }
    val pagerState = rememberPagerState(initialPage = PAGE_DETAILS) { DetailPages.size }
    val subPages = rememberSubPageBar(pagerState, DetailPages)
    val calendarState = rememberCalendarState()

    val breaks = track.tracked.breaks
    val today = todayKey()
    val milestones = remember(streak.milestones) { parseMilestones(streak.milestones) }
    val stats = remember(streak.startDate, breaks, milestones, track.tracked.asOf) {
        computeStreakStats(streak.startDate, breaks, milestones, track.tracked.asOf)
    }
    val broken = remember(stats, breaks) { StreakTracking.broken(stats, breaks) }

    tappedDate?.let { date ->
        val inStreak = date >= streak.startDate && date <= today
        val reached = stats.reachedMilestones[date]
        val upcoming = stats.upcomingMilestones[date]
        val action = if (inStreak) dayAction(date) else null
        val lines = buildList {
            if (inStreak) {
                val length = computeCurrentStreak(streak.startDate, breaks, date)
                add("Streak length on this day: $length day${if (length == 1) "" else "s"}")
            }
            if (reached != null) add("Milestone reached: $reached days")
            if (upcoming != null) add("Milestone due: $upcoming days")
            if (isEmpty()) add(if (date > today) "Nothing on this day yet." else "Before this streak started.")
        }
        AlertDialog(
            onDismissRequest = { tappedDate = null },
            title = { Text(displayDate(date)) },
            text = { Column { lines.forEach { Text(it) } } },
            confirmButton = {
                if (action != null) {
                    TextButton(onClick = {
                        action.run()
                        tappedDate = null
                    }) { Text(action.label) }
                } else {
                    TextButton(onClick = { tappedDate = null }) { Text("Close") }
                }
            },
            dismissButton = if (action != null) {
                { TextButton(onClick = { tappedDate = null }) { Text("Close") } }
            } else null
        )
    }

    ScreenScaffold(
        title = title,
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = subPages,
        topBarActions = topBarActions
    ) { padding ->
        val bottom = padding.calculateBottomPadding()
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            when (index) {
                PAGE_GRAPH -> StreakGraphPage(stats = stats, milestones = milestones, bottomPadding = bottom)
                PAGE_CALENDAR -> CalendarPage(
                    startDate = streak.startDate,
                    breaks = breaks,
                    savedDays = savedDays,
                    stats = stats,
                    state = calendarState,
                    bottomPadding = bottom,
                    onDayClick = { tappedDate = it },
                    actions = calendarActions
                )
                else -> StreakDetailsPage(
                    streak = streak,
                    stats = stats,
                    broken = broken,
                    hasMilestones = milestones.isNotEmpty(),
                    bottomPadding = bottom,
                    onPickBanner = onPickBanner,
                    onPickLogo = onPickLogo
                )
            }
        }
    }
}

@Composable
private fun CalendarPage(
    startDate: String,
    breaks: Set<String>,
    savedDays: Set<String>,
    stats: StreakStats,
    state: CalendarState,
    bottomPadding: Dp,
    onDayClick: (String) -> Unit,
    actions: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StreakCalendar(
            startDate = startDate,
            breaks = breaks,
            savedDays = savedDays,
            reachedMilestones = stats.reachedMilestones,
            upcomingMilestones = stats.upcomingMilestones,
            state = state,
            onDayClick = onDayClick,
            modifier = Modifier.fillMaxWidth()
        )
        StreakCalendarLegend()
        actions()
    }
}
