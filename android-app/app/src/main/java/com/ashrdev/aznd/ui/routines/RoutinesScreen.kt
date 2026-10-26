package com.ashrdev.aznd.ui.routines

import com.ashrdev.aznd.data.routines.routineType
import com.ashrdev.aznd.data.routines.schedule
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.streaks.nextMilestone
import com.ashrdev.aznd.data.streaks.parseMilestones
import com.ashrdev.aznd.data.workout.WorkoutWithExercises
import com.ashrdev.aznd.domain.streaks.RoutineFrequency
import com.ashrdev.aznd.domain.streaks.RoutineType
import com.ashrdev.aznd.domain.streaks.todayKey
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.streaks.CheckInButton
import com.ashrdev.aznd.ui.streaks.DisciplinePage
import com.ashrdev.aznd.ui.streaks.RoutineStatus
import com.ashrdev.aznd.ui.streaks.StreakLogo
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.ui.streaks.StreakWithCount
import com.ashrdev.aznd.ui.streaks.rememberNowMs

private const val PAGE_DISCIPLINE = 0
private const val PAGE_ROUTINES = 1
private const val PAGE_STREAKS = 2

private val RoutinesPages = listOf(
    BarPage("Discipline", Icons.Default.EmojiEvents),
    BarPage("Routines", Icons.Default.Repeat),
    BarPage("Streaks", Icons.Default.LocalFireDepartment)
)

@Composable
fun RoutinesScreen(
    viewModel: StreakViewModel,
    workouts: List<WorkoutWithExercises>,
    onOpenStreak: (Long) -> Unit,
    onAddStreak: () -> Unit,
    onOpenRoutine: (Long) -> Unit,
    onAddRoutine: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val streaks by viewModel.streaksWithCounts.collectAsState()
    val routines by viewModel.routines.collectAsState()
    val pagerState = rememberPagerState(initialPage = PAGE_ROUTINES) { RoutinesPages.size }
    val subPages = rememberSubPageBar(pagerState, RoutinesPages)
    val today = todayKey()

    ScreenScaffold(
        title = when (pagerState.currentPage) {
            PAGE_DISCIPLINE -> "Discipline"
            PAGE_STREAKS -> "Streaks"
            else -> "Routines"
        },
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = subPages
    ) { padding ->
        val bottom = padding.calculateBottomPadding()
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            when (index) {
                PAGE_DISCIPLINE -> DisciplinePage(bottomPadding = bottom)
                PAGE_STREAKS -> StreakListPage(
                    streaks = streaks,
                    bottomPadding = bottom,
                    onOpenStreak = onOpenStreak,
                    onCheckIn = { id, checked -> viewModel.setCheckIn(id, today, checked) },
                    onAdd = onAddStreak
                )
                else -> RoutineListPage(
                    routines = routines,
                    workouts = workouts,
                    bottomPadding = bottom,
                    onOpen = onOpenRoutine,
                    onOpenStreak = onOpenStreak,
                    onToggleDone = { item, done ->
                        val streakId = item.checkStreakId
                        if (streakId != null) viewModel.setCheckIn(streakId, today, done)
                        else viewModel.toggleRoutineDone(item.routine.id, today, done)
                    },
                    onAdd = onAddRoutine
                )
            }
        }
    }
}

@Composable
private fun AddRow(label: String, hint: String?, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (hint != null) {
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RoutineListPage(
    routines: List<RoutineStatus>,
    workouts: List<WorkoutWithExercises>,
    bottomPadding: Dp,
    onOpen: (Long) -> Unit,
    onOpenStreak: (Long) -> Unit,
    onToggleDone: (RoutineStatus, Boolean) -> Unit,
    onAdd: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(routines, key = { it.routine.id }) { item ->
            RoutineCard(
                item = item,
                workoutName = workouts.firstOrNull { it.workout.id == item.routine.workoutId }?.workout?.name,
                onClick = { onOpen(item.routine.id) },
                onOpenStreak = onOpenStreak,
                onToggleDone = { onToggleDone(item, it) }
            )
        }
        item(key = "add") {
            AddRow(
                label = "Add routine",
                hint = if (routines.isEmpty()) "Workouts, meals, body logs or anything you do on a schedule" else null,
                onClick = onAdd
            )
        }
    }
}

private fun typeLine(item: RoutineStatus, workoutName: String?): String {
    val r = item.routine
    val type = if (r.routineType == RoutineType.WORKOUT && workoutName != null) workoutName else r.routineType.label
    return "$type · ${r.schedule.label()}"
}

private fun statusLine(item: RoutineStatus, done: Boolean): String {
    val schedule = item.routine.schedule
    return when {
        schedule.frequency == RoutineFrequency.PER_WEEK -> "${item.doneThisWeek} of ${item.routine.perWeek} this week"
        done -> if (schedule.perDay) "Done today" else "Done"
        schedule.frequency == RoutineFrequency.WEEKDAYS && !item.dueToday -> "Rest day"
        schedule.perDay -> "Due today"
        else -> "Due"
    }
}

@Composable
private fun RoutineCard(
    item: RoutineStatus,
    workoutName: String?,
    onClick: () -> Unit,
    onOpenStreak: (Long) -> Unit,
    onToggleDone: (Boolean) -> Unit
) {
    val r = item.routine
    val done = item.info.state(rememberNowMs()).done
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StreakLogo(photoUri = r.photoUri, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    typeLine(item, workoutName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    statusLine(item, done),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.manual) {
                    CheckInButton(item.info, onChange = onToggleDone, modifier = Modifier.padding(top = 6.dp))
                }
            }
            val streak = item.streak
            if (streak != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onOpenStreak(streak.streak.id) }.padding(horizontal = 8.dp)
                ) {
                    Text("${streak.currentStreak}", style = MaterialTheme.typography.titleLarge)
                    Text(if (streak.currentStreak == 1) "day" else "days", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StreakListPage(
    streaks: List<StreakWithCount>,
    bottomPadding: Dp,
    onOpenStreak: (Long) -> Unit,
    onCheckIn: (Long, Boolean) -> Unit,
    onAdd: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(streaks, key = { it.streak.id }) { item ->
            StreakCard(
                item = item,
                onClick = { onOpenStreak(item.streak.id) },
                onCheckIn = { onCheckIn(item.streak.id, it) }
            )
        }
        item(key = "add") {
            AddRow(
                label = "Add streak",
                hint = if (streaks.isEmpty()) "Something you keep doing, or keep not doing" else null,
                onClick = onAdd
            )
        }
    }
}

@Composable
private fun StreakCard(item: StreakWithCount, onClick: () -> Unit, onCheckIn: (Boolean) -> Unit) {
    val streak = item.streak
    val days = item.currentStreak
    val next = remember(streak.milestones, days) { nextMilestone(days, parseMilestones(streak.milestones)) }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StreakLogo(photoUri = streak.photoUri, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(streak.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                streak.quote?.takeIf { it.isNotBlank() }?.let { quote ->
                    Text(
                        "“$quote”",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (next != null) {
                    Text(
                        "Next: $next days · ${next - days} to go",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item.check?.let { check ->
                    CheckInButton(check, onChange = onCheckIn, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$days", style = MaterialTheme.typography.headlineMedium)
                Text(if (days == 1) "day" else "days", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
