package com.ashrdev.aznd.ui.workout.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import com.ashrdev.aznd.ui.components.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ashrdev.aznd.data.workout.SessionWithSets
import com.ashrdev.aznd.domain.GraphMode
import com.ashrdev.aznd.domain.GraphPoint
import com.ashrdev.aznd.domain.GraphStyle
import com.ashrdev.aznd.domain.GraphWindow
import com.ashrdev.aznd.ui.calendar.DayDecoration
import com.ashrdev.aznd.ui.calendar.DayFill
import com.ashrdev.aznd.ui.calendar.ZoomCalendar
import com.ashrdev.aznd.ui.calendar.dayKeyOf
import com.ashrdev.aznd.ui.calendar.formatDayLong
import com.ashrdev.aznd.ui.calendar.monthKeyOf
import com.ashrdev.aznd.ui.calendar.rememberCalendarState
import com.ashrdev.aznd.ui.common.StatsGraph
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.workout.formatClock
import com.ashrdev.aznd.ui.workout.formatDuration

private val SidePadding = 16.dp

// The stat tiles have a fixed height so the page's total height is known up front: the month
// calendar is then sized to exactly the space left, and nothing needs scrolling.
private val TileHeight = 88.dp
private val TileGap = 12.dp
private val StatsHeight = TileHeight * 2 + TileGap

// Gap between the calendar and the tiles.
private val SectionGap = 16.dp

private val CalendarHeaderHeight = 48.dp

/**
 * Centre page: a calendar with every workout day highlighted (touch a day for a tooltip, double
 * tap it for a popup with its sessions) and four stat tiles. Tap "Sessions per week" for a popup
 * graph of workouts per 7 days.
 *
 * The calendar's grid is capped to the space the tiles leave, and keeps that height at every zoom
 * level, so the tiles never move.
 */
@Composable
internal fun HistoryOverviewPage(
    sessions: List<SessionWithSets>,
    bottomPadding: Dp,
    onOpenSession: (Long) -> Unit
) {
    // No day starts selected: the popup, not the page, is where a day's sessions are shown.
    val calendarState = rememberCalendarState(initialSelectedDay = null)
    val sessionsByDay = remember(sessions) {
        sessions.sortedBy { it.session.startedAt }.groupBy { dayKeyOf(it.session.startedAt) }
    }
    val validMonths = remember(sessionsByDay) { sessionsByDay.keys.map { monthKeyOf(it) }.distinct() }
    val stats = remember(sessions) { computeHistoryStats(sessions) }
    var popupDay by rememberSaveable { mutableStateOf<String?>(null) }
    var graphOpen by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gridBudget = (maxHeight - 8.dp - bottomPadding - StatsHeight - SectionGap - CalendarHeaderHeight)
            .coerceAtLeast(160.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = SidePadding, end = SidePadding, top = 8.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(SectionGap)
        ) {
            ZoomCalendar(
                decorate = { dayKey ->
                    if (dayKey in sessionsByDay) DayDecoration(fill = DayFill.Highlight) else DayDecoration.None
                },
                validMonths = validMonths,
                state = calendarState,
                // The page swipe is for reaching the list, so the chevrons page the calendar instead.
                swipeNavigation = false,
                maxGridHeight = gridBudget,
                dayTooltip = { dayKey -> daySummary(sessionsByDay[dayKey].orEmpty()) },
                onDayDoubleClick = { popupDay = it }
            )
            StatsGrid(stats, onOpenWeeklyGraph = { graphOpen = true })
        }
    }

    if (graphOpen) {
        FrequencyGraphDialog(sessions = sessions, onDismiss = { graphOpen = false })
    }

    popupDay?.let { day ->
        DayPopup(
            dayKey = day,
            sessions = sessionsByDay[day].orEmpty(),
            onOpenSession = { id ->
                popupDay = null
                onOpenSession(id)
            },
            onDismiss = { popupDay = null }
        )
    }
}

private fun daySummary(sessions: List<SessionWithSets>): String {
    val firstName = sessions.firstOrNull()?.session?.workoutName ?: return "No session on this day"
    val more = sessions.size - 1
    return if (more > 0) "$firstName +$more" else firstName
}

@Composable
private fun DayPopup(
    dayKey: String,
    sessions: List<SessionWithSets>,
    onOpenSession: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(formatDayLong(dayKey)) },
        text = {
            if (sessions.isEmpty()) {
                Text(
                    "No session on this day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sessions.forEach { item ->
                        key(item.session.id) {
                            DaySessionCard(item, onClick = { onOpenSession(item.session.id) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun DaySessionCard(item: SessionWithSets, onClick: () -> Unit) {
    val elapsed = ((item.session.finishedAt - item.session.startedAt) / 1000L).toInt()
    val exercises = item.sets.map { it.exerciseName }.distinct()
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.session.workoutName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatClock(item.session.startedAt)} · ${item.sets.size} sets · ${formatDuration(elapsed)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (exercises.isNotEmpty()) {
                    Text(
                        exercises.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (item.session.photoUri != null) {
                Icon(Icons.Default.Photo, contentDescription = "Has photo")
            }
        }
    }
}

@Composable
private fun StatsGrid(stats: HistoryStats, onOpenWeeklyGraph: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TileGap)
        ) {
            StatTile(
                label = "Total sessions",
                value = stats.totalSessions.toString(),
                detail = null,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Sessions per week",
                value = GraphWindow.formatValue(stats.sessionsPerWeek),
                detail = "tap for graph",
                modifier = Modifier.weight(1f),
                onClick = onOpenWeeklyGraph
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TileGap)
        ) {
            StatTile(
                label = "Most done workout",
                value = stats.topWorkout?.name ?: "—",
                detail = stats.topWorkout?.let { "${it.count} sessions" },
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Most done exercise",
                value = stats.topExercise?.name ?: "—",
                detail = stats.topExercise?.let { "in ${it.count} sessions" },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    detail: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .height(TileHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Popup around the app's standard [StatsGraph]: one line mode, "workouts in the 7 days up to each
 * day". Its time-range menu is the app-wide graph range, so it behaves like every other graph.
 */
@Composable
private fun FrequencyGraphDialog(sessions: List<SessionWithSets>, onDismiss: () -> Unit) {
    val mode = remember(sessions) {
        GraphMode(
            id = "workouts_per_7_days",
            label = "Workouts per 7 days",
            unit = "workouts",
            style = GraphStyle.LINE,
            points = rollingWeeklyCounts(sessions).map {
                GraphPoint(
                    timeMs = dayKeyMillis(it.dayKey),
                    value = it.count.toDouble(),
                    detail = "in the 7 days up to this day"
                )
            }
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                StatsGraph(
                    modes = listOf(mode),
                    modifier = Modifier.fillMaxWidth(),
                    title = "Workouts per 7 days",
                    bordered = false
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}
