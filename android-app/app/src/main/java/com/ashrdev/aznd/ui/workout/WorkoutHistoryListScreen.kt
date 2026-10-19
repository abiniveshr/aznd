package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.WorkoutHistorySummary
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.components.Card

private const val PAGE_PRESETS = 0
private const val PAGE_CALENDAR = 1
private const val PAGE_ALL = 2

private val HistoryPages = listOf(
    BarPage("Presets", Icons.Default.FitnessCenter),
    BarPage("Calendar", Icons.Default.CalendarMonth),
    BarPage("All", Icons.AutoMirrored.Filled.List)
)

/**
 * History > Workout History, three swipeable pages:
 *   Presets | Calendar | All
 *  - Presets (swipe right): pick a workout to open its own history. Unchanged for now.
 *  - Calendar (centre, opens here): big calendar with workout days highlighted, tap a day for a
 *    popup of its sessions; totals, averages, most-done workout / exercise, and a graph of
 *    workouts per 7 days. See [HistoryOverviewPage].
 *  - All (swipe left): every session, filterable by muscle group, date range and pictures. See
 *    [HistorySessionListPage].
 */
@Composable
fun WorkoutHistoryListScreen(
    viewModel: WorkoutViewModel,
    onOpenWorkout: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val summaries by viewModel.workoutHistorySummaries.collectAsState()
    val sessions by viewModel.allSessions.collectAsState()
    val catalog by viewModel.catalogExercises.collectAsState()
    var toDelete by remember { mutableStateOf<WorkoutHistorySummary?>(null) }
    val pagerState = rememberPagerState(initialPage = PAGE_CALENDAR) { HistoryPages.size }
    val subPages = rememberSubPageBar(pagerState, HistoryPages)

    toDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete history?") },
            text = { Text("This deletes every logged session for \"${target.workoutName}\". The workout itself won't be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteHistoryForWorkout(target.workoutId)
                    toDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancel") }
            }
        )
    }

    ScreenScaffold(
        title = when (pagerState.currentPage) {
            PAGE_PRESETS -> "Workout history • By workout"
            PAGE_ALL -> "Workout history • All sessions"
            else -> "Workout history • Calendar"
        },
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = subPages
    ) { padding ->
        val bottom = padding.calculateBottomPadding()
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                PAGE_PRESETS -> PresetHistoryPage(
                    summaries = summaries,
                    bottomPadding = bottom,
                    onOpenWorkout = onOpenWorkout,
                    onRequestDelete = { toDelete = it }
                )
                PAGE_CALENDAR -> HistoryOverviewPage(
                    sessions = sessions,
                    bottomPadding = bottom + 16.dp,
                    onOpenSession = onOpenSession
                )
                else -> HistorySessionListPage(
                    sessions = sessions,
                    catalog = catalog,
                    bottomPadding = bottom + 16.dp,
                    onOpenSession = onOpenSession,
                    onDeleteSession = { viewModel.deleteSession(it) }
                )
            }
        }
    }
}

/** The original list: one card per workout that has history. */
@Composable
private fun PresetHistoryPage(
    summaries: List<WorkoutHistorySummary>,
    bottomPadding: Dp,
    onOpenWorkout: (Long) -> Unit,
    onRequestDelete: (WorkoutHistorySummary) -> Unit
) {
    if (summaries.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text("No sessions logged yet.", style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(summaries, key = { it.workoutId }) { summary ->
                HistoryRow(
                    summary = summary,
                    onOpen = { onOpenWorkout(summary.workoutId) },
                    onRequestDelete = { onRequestDelete(summary) }
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(
    summary: WorkoutHistorySummary,
    onOpen: () -> Unit,
    onRequestDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(summary.workoutName, style = MaterialTheme.typography.titleMedium)
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "History options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRequestDelete()
                        }
                    )
                }
            }
        }
    }
}
