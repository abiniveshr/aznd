package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.SessionWithSets
import com.ashrdev.aznd.ui.calendar.BottomAnchoredPage
import com.ashrdev.aznd.ui.calendar.DayDecoration
import com.ashrdev.aznd.ui.calendar.DayFill
import com.ashrdev.aznd.ui.calendar.MonthCalendar
import com.ashrdev.aznd.ui.calendar.SelectedDayHeader
import com.ashrdev.aznd.ui.calendar.dayKeyOf
import com.ashrdev.aznd.ui.calendar.monthKeyOf
import com.ashrdev.aznd.ui.calendar.rememberCalendarState
import com.ashrdev.aznd.ui.calendar.rememberFavoriteDays
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.ThirdAction
import com.ashrdev.aznd.ui.components.Card

@Composable
fun SessionHistoryScreen(
    workoutId: Long,
    viewModel: WorkoutViewModel,
    onOpenSession: (Long) -> Unit,
    onOpenStats: (Long) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val sessions by viewModel.sessionsForWorkout(workoutId).collectAsState(initial = emptyList())
    var sessionToDelete by remember { mutableStateOf<SessionWithSets?>(null) }

    // Name for the title: from the logged sessions, or the workout itself while none exist yet.
    var workoutName by remember { mutableStateOf("") }
    LaunchedEffect(workoutId) {
        workoutName = viewModel.getWorkout(workoutId)?.Workout?.name ?: ""
    }
    val displayName = sessions.firstOrNull()?.session?.WorkoutName
        ?: workoutName.ifEmpty { "Workout" }

    val favorites = rememberFavoriteDays("workout:$workoutId")
    val calendarState = rememberCalendarState()
    val sessionsByDay = remember(sessions) { sessions.groupBy { dayKeyOf(it.session.startedAt) } }
    val validMonths = remember(sessionsByDay) { sessionsByDay.keys.map { monthKeyOf(it) }.distinct() }

    sessionToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Delete session?") },
            text = { Text("This session log${if (target.session.photoUri != null) " and its photo" else ""} will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(target.session.id)
                    sessionToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) { Text("Cancel") }
            }
        )
    }

    ScreenScaffold(
        title = "$displayName • History",
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        third = ThirdAction(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            label = "Stats",
            onClick = { onOpenStats(workoutId) }
        ),
        topBarActions = {
            // Share isn't wired up yet, so it sits here disabled.
            IconButton(onClick = {}, enabled = false) {
                Icon(Icons.Default.Share, contentDescription = "Share (coming soon)")
            }
        }
    ) { padding ->
        BottomAnchoredPage(
            bottomPadding = padding.calculateBottomPadding(),
            top = {
                if (sessions.isEmpty()) {
                    Text("No sessions logged yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    sessions.take(3).forEach { item ->
                        key(item.session.id) {
                            HistoryCard(
                                item = item,
                                onClick = { onOpenSession(item.session.id) },
                                onDelete = { sessionToDelete = item }
                            )
                        }
                    }
                }
            },
            bottom = {
                val day = calendarState.selectedDay
                if (day != null) {
                    val daySessions = sessionsByDay[day].orEmpty()
                    SelectedDayHeader(
                        dayKey = day,
                        isFavorite = favorites.isFavorite(day),
                        onToggleFavorite = if (daySessions.isNotEmpty()) {
                            { favorites.toggle(day) }
                        } else null
                    )
                    if (daySessions.isEmpty()) {
                        Text(
                            "No session on this day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        daySessions.forEach { item ->
                            key(item.session.id) {
                                HistoryCard(
                                    item = item,
                                    onClick = { onOpenSession(item.session.id) },
                                    onDelete = { sessionToDelete = item }
                                )
                            }
                        }
                    }
                }
                // Days with a session are outlined; favourite days swap the outline for a highlight.
                MonthCalendar(
                    decorate = { dayKey ->
                        when {
                            dayKey !in sessionsByDay -> DayDecoration.None
                            favorites.isFavorite(dayKey) -> DayDecoration(fill = DayFill.Highlight)
                            else -> DayDecoration(outline = true)
                        }
                    },
                    validMonths = validMonths,
                    state = calendarState
                )
            }
        )
    }
}

@Composable
private fun HistoryCard(
    item: SessionWithSets,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(formatTimestamp(item.session.startedAt), style = MaterialTheme.typography.titleMedium)
                val elapsed = ((item.session.finishedAt - item.session.startedAt) / 1000L).toInt()
                Text(
                    "${item.sets.size} sets · ${formatDuration(elapsed)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.session.photoUri != null) {
                    Icon(Icons.Default.Photo, contentDescription = "Has photo")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete session",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
