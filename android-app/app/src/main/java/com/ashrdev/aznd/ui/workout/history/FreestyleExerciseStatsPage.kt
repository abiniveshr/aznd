package com.ashrdev.aznd.ui.workout.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.collectAsState
import com.ashrdev.aznd.data.workout.EMPTY_WORKOUT_ID
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.data.workout.SessionWithSets
import com.ashrdev.aznd.domain.workout.BodyRegion
import com.ashrdev.aznd.domain.workout.ExerciseType
import com.ashrdev.aznd.domain.workout.LoggedSetData
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.theme.Space
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import com.ashrdev.aznd.ui.workout.exercise.ExercisePicker
import com.ashrdev.aznd.ui.workout.exercise.MuscleBadge
import com.ashrdev.aznd.ui.workout.formatDuration
import com.ashrdev.aznd.ui.workout.formatTimestamp
import com.ashrdev.aznd.ui.workout.formatWeight
import com.ashrdev.aznd.ui.workout.stats.ExerciseStatsData
import com.ashrdev.aznd.ui.workout.stats.ExerciseStatsView
import com.ashrdev.aznd.ui.workout.stats.PageDots
import com.ashrdev.aznd.ui.workout.stats.allSessionSetPoints
import com.ashrdev.aznd.ui.workout.stats.buildExerciseStatsData
import com.ashrdev.aznd.ui.workout.stats.formatE1rm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE_LIST = 0
private const val PAGE_GRAPHS = 1
private const val PAGE_COUNT = 2

private val SettingsDialogHeight = 480.dp
private val SourceLabels = listOf("Freestyle only", "All workouts")

private enum class ExerciseSort(val label: String) {
    RECENT("Last done"),
    NAME("Name"),
    SESSIONS("Most sessions"),
    E1RM("Best 1RM"),
    VOLUME("Volume")
}

/** One exercise's numbers in the current data scope. */
private data class ExerciseSummary(
    val exercise: Exercise,
    val sessions: Int,
    val sets: Int,
    val lastDone: Long,
    val stats: ExerciseStatsData,
    val bestE1rm: Double?,
    val volumeKg: Double,
    val bestHoldSec: Int?
)

/**
 * The Stats page of the Freestyle history: exercise statistics for sessions started without a
 * preset. Two stacked pages (swipe up / down), like the workout stats screen:
 *  - the list: every exercise with data (sessions, sets, last done, best 1RM or hold, volume),
 *    a "Freestyle only | All workouts" switch for the data source, and a Settings popup with
 *    a muscle-group filter and the sort order;
 *  - the graphs: the picked exercise's graph views (tap a card to jump here).
 * [sessions] is only a reload key: the set data is read again whenever it changes (e.g. a delete).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FreestyleExerciseStatsPage(
    viewModel: WorkoutViewModel,
    sessions: List<SessionWithSets>,
    bottomPadding: Dp
) {
    var freestyleOnly by rememberSaveable { mutableStateOf(true) }
    var regionNames by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(ExerciseSort.RECENT.name) }
    var reverse by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    val regions = remember(regionNames) {
        val names = regionNames.split(",")
        BodyRegion.entries.filter { it.name in names }.toSet()
    }
    val sort = remember(sortName) { ExerciseSort.entries.firstOrNull { it.name == sortName } ?: ExerciseSort.RECENT }

    val catalog by viewModel.catalogExercises.collectAsState()
    val rows by produceState<List<LoggedSetData>?>(null, sessions) {
        value = withContext(Dispatchers.Default) { viewModel.allLoggedSetData().filter { it.isLogged } }
    }
    val summaries by produceState<List<ExerciseSummary>?>(null, rows, catalog, freestyleOnly) {
        val all = rows ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val scoped = if (freestyleOnly) all.filter { it.workoutId == EMPTY_WORKOUT_ID } else all
            val byId = scoped.groupBy { it.exerciseId }
            catalog.mapNotNull { ex ->
                val own = byId[ex.id] ?: return@mapNotNull null
                val stats = buildExerciseStatsData(ex.name, allSessionSetPoints(ex, own), null)
                ExerciseSummary(
                    exercise = ex,
                    sessions = own.map { it.sessionId }.distinct().size,
                    sets = own.size,
                    lastDone = own.maxOf { it.startedAt },
                    stats = stats,
                    bestE1rm = stats.points.maxOfOrNull { it.e1rm },
                    volumeKg = stats.volume.sumOf { it.value },
                    bestHoldSec = if (ex.type == ExerciseType.TIME_HELD) own.maxOf { it.durationSec ?: 0 } else null
                )
            }
        }
    }

    val loaded = summaries ?: emptyList()
    val shown = remember(loaded, regions, sort, reverse) {
        val allowed = regions.flatMap { it.muscles }.toSet()
        val kept = loaded.filter { regions.isEmpty() || it.exercise.primaryMuscle in allowed }
        val byName = compareBy<ExerciseSummary, String>(String.CASE_INSENSITIVE_ORDER) { it.exercise.name }
        val sorted = when (sort) {
            ExerciseSort.RECENT -> kept.sortedWith(compareByDescending<ExerciseSummary> { it.lastDone }.then(byName))
            ExerciseSort.NAME -> kept.sortedWith(byName)
            ExerciseSort.SESSIONS -> kept.sortedWith(compareByDescending<ExerciseSummary> { it.sessions }.then(byName))
            ExerciseSort.E1RM -> kept.sortedWith(
                compareByDescending<ExerciseSummary> { it.bestE1rm != null }
                    .thenByDescending { it.bestE1rm ?: 0.0 }.then(byName)
            )
            ExerciseSort.VOLUME -> kept.sortedWith(compareByDescending<ExerciseSummary> { it.volumeKg }.then(byName))
        }
        if (reverse) sorted.reversed() else sorted
    }
    val activeCount = (if (regions.isNotEmpty()) 1 else 0) + (if (sort != ExerciseSort.RECENT || reverse) 1 else 0)

    val emptyText = when {
        summaries == null -> null
        loaded.isEmpty() -> if (freestyleOnly) "No freestyle sessions logged yet." else "No sessions logged yet."
        shown.isEmpty() -> "No exercises match these settings."
        else -> null
    }

    val pager = rememberPagerState { PAGE_COUNT }
    val scope = rememberCoroutineScope()

    if (settingsOpen) {
        ExerciseSettingsDialog(
            regions = regions,
            onRegions = { regionNames = it.joinToString(",") { r -> r.name } },
            sort = sort,
            onSort = { sortName = it.name },
            reverse = reverse,
            onReverse = { reverse = it },
            matchCount = shown.size,
            onDismiss = { settingsOpen = false }
        )
    }

    Box(Modifier.fillMaxSize()) {
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            Column(Modifier.fillMaxSize()) {
                Text(
                    if (page == PAGE_LIST) "Exercises · swipe up for graphs" else "Graphs · swipe down for the list",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Space.lg, end = Space.xl, top = Space.sm)
                )
                if (page == PAGE_LIST) {
                    Column(Modifier.weight(1f)) {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg).padding(top = Space.sm)
                        ) {
                            SourceLabels.forEachIndexed { i, label ->
                                SegmentedButton(
                                    selected = (i == 0) == freestyleOnly,
                                    onClick = { freestyleOnly = i == 0 },
                                    shape = SegmentedButtonDefaults.itemShape(index = i, count = SourceLabels.size)
                                ) { Text(label) }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = Space.lg, end = Space.lg, top = Space.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (shown.size == loaded.size) "${loaded.size} exercises"
                                else "${shown.size} of ${loaded.size} exercises",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = { settingsOpen = true }) {
                                Icon(Icons.Default.FilterList, contentDescription = null)
                                Spacer(modifier = Modifier.width(Space.sm))
                                Text(if (activeCount == 0) "Settings" else "Settings ($activeCount)")
                            }
                        }
                        if (emptyText != null) {
                            EmptyText(emptyText)
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = Space.lg, end = Space.lg, top = Space.md, bottom = bottomPadding
                                ),
                                verticalArrangement = Arrangement.spacedBy(Space.md)
                            ) {
                                items(shown, key = { it.exercise.id }) { item ->
                                    ExerciseSummaryCard(item) {
                                        selectedId = item.exercise.id
                                        scope.launch { pager.animateScrollToPage(PAGE_GRAPHS) }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val selected = shown.firstOrNull { it.exercise.id == selectedId } ?: shown.firstOrNull()
                    if (selected == null) {
                        if (emptyText != null) EmptyText(emptyText)
                    } else {
                        Column(Modifier.weight(1f).padding(top = Space.lg, bottom = bottomPadding)) {
                            ExercisePicker(
                                selected = selected.exercise,
                                exercises = shown.map { it.exercise },
                                onPick = { selectedId = it.id },
                                modifier = Modifier.padding(horizontal = Space.lg),
                                allowAddCustom = false
                            )
                            Text(
                                if (freestyleOnly) "From freestyle sessions only" else "From every workout",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Space.lg)
                            )
                            ExerciseStatsView(selected.stats, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        PageDots(
            count = PAGE_COUNT,
            position = { pager.currentPage + pager.currentPageOffsetFraction },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = Space.xs, bottom = bottomPadding)
        )
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(Space.xl)
    )
}

@Composable
private fun ExerciseSummaryCard(item: ExerciseSummary, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            MuscleBadge(item.exercise.primaryMuscle, item.exercise.photoPath)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${item.sessions} sessions · ${item.sets} sets · Last ${formatTimestamp(item.lastDone)}",
                    style = MaterialTheme.typography.bodySmall
                )
                val best = item.bestE1rm?.let { "Best 1RM ${formatE1rm(it)}" }
                    ?: item.bestHoldSec?.takeIf { it > 0 }?.let { "Best hold ${formatDuration(it)}" }
                val volume = if (item.volumeKg > 0) "Volume ${formatWeight(item.volumeKg)}" else null
                val line = listOfNotNull(best, volume).joinToString(" · ")
                if (line.isNotEmpty()) {
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Fixed-size popup, same shell as the session filters: muscle group and sort order. */
@Composable
private fun ExerciseSettingsDialog(
    regions: Set<BodyRegion>,
    onRegions: (Set<BodyRegion>) -> Unit,
    sort: ExerciseSort,
    onSort: (ExerciseSort) -> Unit,
    reverse: Boolean,
    onReverse: (Boolean) -> Unit,
    matchCount: Int,
    onDismiss: () -> Unit
) {
    var open by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).height(SettingsDialogHeight),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(Space.lg)) {
                Text("Exercise stats settings", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(Space.sm))

                Column(modifier = Modifier.weight(1f)) {
                    FilterSection(
                        title = "Muscle group",
                        summary = when (regions.size) {
                            0 -> "Any"
                            1 -> regions.first().label
                            else -> "${regions.size} selected"
                        },
                        expanded = open == 0,
                        onToggle = { open = if (open == 0) -1 else 0 }
                    ) {
                        BodyRegion.entries.forEach { region ->
                            CheckRow(region.label, region in regions) {
                                onRegions(if (region in regions) regions - region else regions + region)
                            }
                        }
                    }
                    FilterSection(
                        title = "Sort by",
                        summary = sort.label + if (reverse) " · reversed" else "",
                        expanded = open == 1,
                        onToggle = { open = if (open == 1) -1 else 1 }
                    ) {
                        ExerciseSort.entries.forEach { option ->
                            CheckRow(option.label, sort == option) { onSort(option) }
                        }
                        CheckRow("Reverse order", reverse) { onReverse(!reverse) }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        onRegions(emptySet())
                        onSort(ExerciseSort.RECENT)
                        onReverse(false)
                    }) { Text("Reset") }
                    Button(onClick = onDismiss) { Text("Show $matchCount") }
                }
            }
        }
    }
}
