package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import com.ashrdev.aznd.ui.components.AlertDialog
import androidx.compose.material3.CardDefaults
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
import com.ashrdev.aznd.data.workout.EMPTY_WORKOUT_ID
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.data.workout.WorkoutWithExercises
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.PrimaryAction
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.components.Card

private const val PAGE_EXERCISES = 0
private const val PAGE_VIEW = 1
private const val PAGE_STATS = 2

private val WorkoutPages = listOf(
    BarPage("Exercises", Icons.AutoMirrored.Filled.List),
    BarPage("Workouts", Icons.Default.FitnessCenter),
    BarPage("Stats", Icons.Default.BarChart)
)

/**
 * The Workouts module: three swipeable pages, opening on the middle one.
 *   Exercises | Workouts | Stats
 *  - Workouts: every workout with a Start button; tap a workout to see what is in it.
 *  - Exercises (swipe left): every catalog exercise, A-Z, in the same searcher as the add-exercise
 *    picker. Tapping one opens the exercise viewer. The + in the top bar adds a custom exercise.
 *  - Stats (swipe right): pick a workout to open its stats.
 */
@Composable
fun WorkoutsScreen(
    viewModel: WorkoutViewModel,
    onOpenWorkout: (Long) -> Unit,
    onAddWorkout: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenWorkoutStats: (Long) -> Unit,
    onOpenExercise: (Long) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val openCustom = LocalOpenCustomExercise.current
    val workouts by viewModel.workouts.collectAsState()
    val catalog by viewModel.catalogExercises.collectAsState()
    val withData by viewModel.exerciseIdsWithData.collectAsState()
    val active by viewModel.activeSession.collectAsState()
    var blockedDialog by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = PAGE_VIEW) { WorkoutPages.size }
    val subPages = rememberSubPageBar(pagerState, WorkoutPages)

    if (blockedDialog) {
        val running = active
        AlertDialog(
            onDismissRequest = { blockedDialog = false },
            title = { Text("Session already running") },
            text = {
                Text("You have an unfinished ${running?.workoutName.orEmpty()} session. Finish or discard it before starting another.")
            },
            confirmButton = {
                TextButton(onClick = {
                    blockedDialog = false
                    if (running != null) onOpenSession(running.workoutId)
                }) { Text("Go to session") }
            },
            dismissButton = {
                TextButton(onClick = { blockedDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Only one session can run at a time: starting another workout is blocked while one is open.
    val start: (WorkoutWithExercises) -> Unit = { item ->
        val running = active
        if (running != null && running.workoutId != item.workout.id) blockedDialog = true
        else onOpenSession(item.workout.id)
    }

    // Empty workout: a session with no template, exercises added as you go. Same one-session rule.
    val startEmpty: () -> Unit = {
        val running = active
        if (running != null && running.workoutId != EMPTY_WORKOUT_ID) blockedDialog = true
        else onOpenSession(EMPTY_WORKOUT_ID)
    }

    val page = pagerState.currentPage
    ScreenScaffold(
        title = when (page) {
            PAGE_EXERCISES -> "Exercises"
            PAGE_STATS -> "Workout stats"
            else -> "Workouts"
        },
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = subPages,
        reservePrimarySpace = true,
        primaryAction = if (page == PAGE_VIEW) PrimaryAction(
            icon = Icons.Default.Add,
            label = "New Preset",
            onClick = onAddWorkout
        ) else null,
        secondaryAction = if (page == PAGE_VIEW) PrimaryAction(
            icon = Icons.Default.PlayArrow,
            label = if (active?.workoutId == EMPTY_WORKOUT_ID) "Resume FS" else "Freestyle",
            onClick = startEmpty
        ) else null,
        topBarActions = if (page == PAGE_EXERCISES) {
            {
                if (openCustom != null) {
                    IconButton(onClick = openCustom) {
                        Icon(Icons.Default.Add, contentDescription = "Add custom exercise")
                    }
                }
            }
        } else {
            {}
        }
    ) { padding ->
        val bottom = padding.calculateBottomPadding()
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            when (index) {
                PAGE_EXERCISES -> ExercisesPage(
                    exercises = catalog,
                    dataIds = withData,
                    bottomPadding = bottom,
                    onOpen = { onOpenExercise(it.id) }
                )
                PAGE_STATS -> StatsPickerPage(
                    workouts = workouts,
                    bottomPadding = bottom,
                    onOpen = onOpenWorkoutStats
                )
                else -> ViewPage(
                    workouts = workouts,
                    bottomPadding = bottom,
                    runningSession = active,
                    onOpenWorkout = onOpenWorkout,
                    onOpenSession = onOpenSession,
                    onStart = start
                )
            }
        }
    }
}

// ---- Workouts page: view mode ----

@Composable
private fun ViewPage(
    workouts: List<WorkoutWithExercises>,
    bottomPadding: Dp,
    runningSession: ActiveSessionInfo?,
    onOpenWorkout: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    onStart: (WorkoutWithExercises) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        runningSession?.let { running ->
            item(key = "running") {
                Card(
                    highlight = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { onOpenSession(running.workoutId) })
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Session in progress", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${running.workoutName} · started ${formatClock(running.startedAt)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        if (workouts.isEmpty()) {
            item(key = "empty") {
                Text("No workouts yet.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            items(workouts, key = { it.workout.id }) { item ->
                val isRunning = runningSession?.workoutId == item.workout.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenWorkout(item.workout.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.workout.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${item.exercises.size} exercises · ${item.exercises.sumOf { it.sets.size }} sets",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onStart(item) },
                            enabled = item.exercises.isNotEmpty()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isRunning) "Resume" else "Start")
                        }
                    }
                }
            }
        }
    }
}

// ---- Exercises page: every exercise ----

@Composable
private fun ExercisesPage(
    exercises: List<Exercise>,
    dataIds: Set<Long>,
    bottomPadding: Dp,
    onOpen: (Exercise) -> Unit
) {
    ExerciseSearchContent(
        selected = null,
        exercises = exercises,
        onPick = onOpen,
        onAddCustom = null,
        modifier = Modifier.fillMaxSize().padding(top = 8.dp),
        autoFocus = false,
        bottomPadding = bottomPadding,
        dataIds = dataIds
    )
}

// ---- Stats page: pick a workout ----

@Composable
private fun StatsPickerPage(
    workouts: List<WorkoutWithExercises>,
    bottomPadding: Dp,
    onOpen: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (workouts.isEmpty()) {
            item(key = "empty") { Text("No workouts yet.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(workouts, key = { it.workout.id }) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(item.workout.id) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.workout.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${item.exercises.size} exercises",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }
    }
}
