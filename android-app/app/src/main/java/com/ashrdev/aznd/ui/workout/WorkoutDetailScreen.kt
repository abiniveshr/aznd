package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.Dp
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.workout.builder.BuilderEditorList
import com.ashrdev.aznd.ui.workout.builder.BuilderViewModel
import com.ashrdev.aznd.ui.workout.stats.WorkoutGraphViewModel
import com.ashrdev.aznd.ui.workout.stats.WorkoutStatsPages
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import com.ashrdev.aznd.ui.components.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.WorkoutWithExercises
import com.ashrdev.aznd.domain.workout.ExerciseType
import com.ashrdev.aznd.domain.workout.SetKind
import com.ashrdev.aznd.domain.workout.SetRow
import com.ashrdev.aznd.domain.workout.previousRowFor
import com.ashrdev.aznd.domain.workout.setLabels
import com.ashrdev.aznd.domain.workout.targetLabel
import com.ashrdev.aznd.ui.common.PrimaryAction
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.workout.exercise.MuscleBadge
import com.ashrdev.aznd.ui.workout.session.formatPreviousSet

/** The workout screen's pages, for opening it on one: WorkoutDetail.createRoute(id, page). */
object WorkoutDetailPage {
    const val EDIT = 0
    const val WORKOUT = 1
    const val STATS = 2
}

private val DetailPages = listOf(
    BarPage("Edit", Icons.Default.Edit),
    BarPage("Workout", Icons.Default.FitnessCenter),
    BarPage("Stats", Icons.Default.BarChart)
)

/**
 * One workout, three swipeable pages, opening on the middle one:   Edit | Workout | Stats
 *  - Workout: the template's exercises and sets. Tapping an exercise opens the exercise viewer.
 *    Start opens the logger, which starts a new session or resumes the unfinished one of this
 *    workout. Only one session can run at a time, so while a session of ANOTHER workout is
 *    unfinished, Start offers to go there instead. Share (top bar) opens Flex.
 *  - Edit (swipe left): the builder. Save writes it and comes back to Workout; leaving without
 *    saving drops the changes. Delete is in the top bar.
 *  - Stats (swipe right): the workout's graphs, and below them (swipe up) its exercises' stats.
 *
 * [onOpenSession] receives the id of the workout whose logger should open.
 */
@Composable
fun WorkoutDetailScreen(
    workoutId: Long,
    viewModel: WorkoutViewModel,
    builderViewModel: BuilderViewModel,
    graphViewModel: WorkoutGraphViewModel?,
    useTargets: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSession: (Long) -> Unit,
    // Opens the exercise viewer for a catalog exercise id.
    onViewExercise: (Long) -> Unit,
    // Opens Flex on the routine preset for this workout.
    onShare: (Long) -> Unit,
    // Saves the workout's record (its name) and returns its id; the template is saved after it.
    onSaveRecord: suspend (name: String) -> Long,
    deleteTemplate: suspend (workoutId: Long) -> Unit,
    // The workout was deleted: leave the screen.
    onDeleted: () -> Unit,
    initialPage: Int = WorkoutDetailPage.WORKOUT
) {
    val active by viewModel.activeSession.collectAsState()
    var workout by remember { mutableStateOf<WorkoutWithExercises?>(null) }
    var template by remember { mutableStateOf<TemplateView?>(null) }
    var previous by remember { mutableStateOf<List<SetRow>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var blockedDialog by remember { mutableStateOf(false) }
    // Bumped after a save, so the Workout page shows what was just saved.
    var version by remember { mutableIntStateOf(0) }
    // The name being edited; null until the workout has loaded.
    var name by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, DetailPages.size - 1)) { DetailPages.size }
    val subPages = rememberSubPageBar(pagerState, DetailPages)
    val builderReady by builderViewModel.ready.collectAsState()

    LaunchedEffect(workoutId, version) {
        workout = viewModel.getWorkout(workoutId)
        template = viewModel.templateView(workoutId)
        previous = viewModel.previousRows(workoutId)
        loaded = true
    }
    LaunchedEffect(workoutId) { builderViewModel.load(workoutId) }
    BackHandler(enabled = pagerState.currentPage != WorkoutDetailPage.WORKOUT) {
        scope.launch { pagerState.animateScrollToPage(WorkoutDetailPage.WORKOUT) }
    }

    if (!loaded) return
    val item = workout
    if (item == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val editName = name ?: item.workout.name
    val view = template
    val hasRows = view != null && view.cards.isNotEmpty()

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
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete workout?") },
            text = { Text("\"${item.workout.name}\" and its exercises will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    scope.launch {
                        deleteTemplate(item.workout.id)
                        onDeleted()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } }
        )
    }

    fun save() {
        saving = true
        scope.launch {
            try {
                val id = onSaveRecord(editName.trim())
                builderViewModel.save(id)
                version += 1
                pagerState.animateScrollToPage(WorkoutDetailPage.WORKOUT)
            } finally {
                saving = false
            }
        }
    }

    ScreenScaffold(
        title = item.workout.name,
        pageTitle = { page ->
            when (page) {
                WorkoutDetailPage.EDIT -> "Edit · ${item.workout.name}"
                WorkoutDetailPage.STATS -> "${item.workout.name} · Stats"
                else -> item.workout.name
            }
        },
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = subPages,
        pageTopBarActions = { page ->
            when (page) {
                WorkoutDetailPage.EDIT -> {
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete workout", tint = MaterialTheme.colorScheme.error)
                    }
                    TextButton(enabled = editName.isNotBlank() && !saving && builderReady, onClick = { save() }) { Text("Save") }
                }
                WorkoutDetailPage.WORKOUT -> IconButton(enabled = hasRows, onClick = { onShare(item.workout.id) }) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }
                else -> Unit
            }
        },
        pagePrimaryActions = { page ->
            if (page == WorkoutDetailPage.WORKOUT) {
                listOf(
                    PrimaryAction(
                        icon = Icons.Default.PlayArrow,
                        label = if (active?.workoutId == item.workout.id) "Resume" else "Start",
                        enabled = hasRows,
                        onClick = {
                            val running = active
                            if (running != null && running.workoutId != item.workout.id) blockedDialog = true
                            else onOpenSession(item.workout.id)
                        }
                    )
                )
            } else {
                emptyList()
            }
        }
    ) { padding ->
        val bottom = padding.calculateBottomPadding()
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                WorkoutDetailPage.EDIT -> if (builderReady) {
                    BuilderEditorList(
                        name = editName,
                        onNameChange = { name = it },
                        viewModel = builderViewModel,
                        bottom = bottom
                    )
                } else Unit
                WorkoutDetailPage.STATS -> WorkoutStatsPages(
                    workoutId = item.workout.id,
                    viewModel = viewModel,
                    hasExercises = item.exercises.isNotEmpty(),
                    useTargets = useTargets,
                    graphViewModel = graphViewModel,
                    bottomPadding = bottom
                )
                else -> WorkoutPage(view, previous, bottom, onViewExercise)
            }
        }
    }
}

/** The template's exercises and their sets, each with the same set last time. */
@Composable
private fun WorkoutPage(
    view: TemplateView?,
    previous: List<SetRow>,
    bottom: Dp,
    onViewExercise: (Long) -> Unit
) {
    if (view == null || view.cards.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                "No exercises yet. Swipe to Edit (or tap it below) to add some.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    } else {
        // Depth-first rows of the whole template, to pair each set with the same set last time.
        val allRows = remember(view) { view.cards.flatMap { it.rows } }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottom
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(view.cards, key = { it.rows.first().id }) { card ->
                val exercise = card.exerciseId?.let { view.exercises[it] }
                val labels = setLabels(card)
                val exerciseId = card.exerciseId
                // Tapping an exercise opens the exercise viewer.
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (exerciseId != null) Modifier.clickable { onViewExercise(exerciseId) }
                            else Modifier
                        )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (exercise != null) MuscleBadge(exercise.primaryMuscle, exercise.photoPath)
                            Text(exercise?.name ?: "Unknown exercise", style = MaterialTheme.typography.titleMedium)
                        }
                        card.rows.forEach { row ->
                            val rowExercise = row.exerciseId?.let { view.exercises[it] } ?: exercise
                            val target = targetLabel(row, rowExercise?.type ?: ExerciseType.WEIGHTED, true)
                            val label = labels[row.id].orEmpty()
                            val name = if (row.kind == SetKind.SUPERSET) " · ${rowExercise?.name.orEmpty()}" else ""
                            val prev = previousRowFor(row, allRows, previous)
                                ?.let { formatPreviousSet(rowExercise?.type ?: ExerciseType.WEIGHTED, it, false) }
                            Column(modifier = Modifier.padding(start = if (row.kind == SetKind.NORMAL) 0.dp else 12.dp)) {
                                Text(
                                    text = label + name + if (target != null) " · target $target" else "",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (prev != null) {
                                    Text(
                                        text = "Previous: $prev",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
