package com.ashrdev.aznd.ui.workout.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Photo
import com.ashrdev.aznd.ui.components.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.data.workout.SessionWithSets
import com.ashrdev.aznd.domain.workout.BodyRegion
import com.ashrdev.aznd.domain.workout.Muscle
import com.ashrdev.aznd.ui.calendar.dayKeyOf
import com.ashrdev.aznd.ui.common.CustomRangeRow
import com.ashrdev.aznd.ui.common.DateRangeKind
import com.ashrdev.aznd.ui.common.DateRangeState
import com.ashrdev.aznd.ui.common.DayRange
import com.ashrdev.aznd.ui.common.rememberDateRangeState
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField
import com.ashrdev.aznd.ui.workout.formatDuration
import com.ashrdev.aznd.ui.workout.formatTimestamp
import com.ashrdev.aznd.ui.theme.Space

// The popup never changes size: whichever filter category is open scrolls inside the space left.
private val FilterDialogHeight = 480.dp

private const val EXERCISE_SEPARATOR = "\u001F"

/**
 * The non-date filters of the session list. Muscle group is a [BodyRegion] (a session matches when
 * any exercise's primary muscle is in a chosen region). Picture filters are two boxes: tick one to
 * keep only sessions with / without a photo; ticking both or neither means no picture filter.
 * [exercises] holds lowercased exercise names (a session matches when it logged any of them).
 */
@Stable
internal class HistoryFilters(
    regions: Set<BodyRegion> = emptySet(),
    withPhotos: Boolean = false,
    withoutPhotos: Boolean = false,
    exercises: Set<String> = emptySet()
) {
    var regions by mutableStateOf(regions)
    var withPhotos by mutableStateOf(withPhotos)
    var withoutPhotos by mutableStateOf(withoutPhotos)
    var exercises by mutableStateOf(exercises)

    val pictureFilterActive: Boolean get() = withPhotos != withoutPhotos

    fun clear() {
        regions = emptySet()
        withPhotos = false
        withoutPhotos = false
        exercises = emptySet()
    }

    companion object {
        val Saver: Saver<HistoryFilters, Any> = listSaver<HistoryFilters, Any>(
            save = {
                listOf(
                    it.regions.joinToString(",") { r -> r.name },
                    it.withPhotos,
                    it.withoutPhotos,
                    // Names may contain commas, so they are joined with the unit separator instead.
                    it.exercises.joinToString(EXERCISE_SEPARATOR)
                )
            },
            restore = {
                val names = (it[0] as String).split(",")
                HistoryFilters(
                    regions = BodyRegion.entries.filter { r -> r.name in names }.toSet(),
                    withPhotos = it[1] as Boolean,
                    withoutPhotos = it[2] as Boolean,
                    exercises = (it.getOrNull(3) as? String ?: "").split(EXERCISE_SEPARATOR).filter { n -> n.isNotEmpty() }.toSet()
                )
            }
        )
    }
}

internal fun filterSessions(
    sessions: List<SessionWithSets>,
    filters: HistoryFilters,
    range: DayRange,
    primaryMuscleByName: Map<String, Muscle>
): List<SessionWithSets> {
    val allowedMuscles = filters.regions.flatMap { it.muscles }.toSet()
    return sessions.filter { item ->
        val inRange = range == DayRange.All || range.contains(dayKeyOf(item.session.startedAt))
        val photoOk = !filters.pictureFilterActive || (item.session.photoUri != null) == filters.withPhotos
        val muscleOk = filters.regions.isEmpty() || item.sets.any { set ->
            primaryMuscleByName[set.exerciseName.lowercase()]?.let { it in allowedMuscles } == true
        }
        val exerciseOk = filters.exercises.isEmpty() ||
            item.sets.any { it.exerciseName.lowercase() in filters.exercises }
        inRange && photoOk && muscleOk && exerciseOk
    }
}

/**
 * A list of logged sessions, newest first, with a Filters button. The flags choose which filter
 * sections exist and how a card reads: [showWorkoutName] false titles the card with its date (for a
 * list that is all one workout), [showExerciseNames] adds a line with the session's exercises.
 */
@Composable
internal fun HistorySessionListPage(
    sessions: List<SessionWithSets>,
    catalog: List<Exercise>,
    bottomPadding: Dp,
    onOpenSession: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit,
    showMuscleFilter: Boolean = true,
    showExerciseFilter: Boolean = false,
    showWorkoutName: Boolean = true,
    showExerciseNames: Boolean = false
) {
    val filters = rememberSaveable(saver = HistoryFilters.Saver) { HistoryFilters() }
    val dates = rememberDateRangeState()
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<SessionWithSets?>(null) }

    val sessionDays = remember(sessions) { sessions.map { dayKeyOf(it.session.startedAt) }.toSet() }
    val muscleByName = remember(catalog) { catalog.associate { it.name.lowercase() to it.primaryMuscle } }
    val shown = filterSessions(sessions, filters, dates.range, muscleByName)
    // Every exercise ever logged here (not just in the filtered list), newest session's casing, A-Z.
    val exerciseOptions = remember(sessions) {
        val byKey = LinkedHashMap<String, String>()
        sessions.sortedByDescending { it.session.startedAt }.forEach { item ->
            item.sets.forEach { byKey.putIfAbsent(it.exerciseName.lowercase(), it.exerciseName) }
        }
        byKey.values.sortedBy { it.lowercase() }
    }
    val activeCount = (if (filters.regions.isNotEmpty()) 1 else 0) +
        (if (filters.exercises.isNotEmpty()) 1 else 0) +
        (if (dates.kind != DateRangeKind.ALL) 1 else 0) +
        (if (filters.pictureFilterActive) 1 else 0)

    toDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete session?") },
            text = {
                Text(
                    "This session log${if (target.session.photoUri != null) " and its photo" else ""} " +
                        "will be deleted. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSession(target.session.id)
                    toDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }

    if (filterOpen) {
        HistoryFilterDialog(
            filters = filters,
            dates = dates,
            matchCount = shown.size,
            showMuscleFilter = showMuscleFilter,
            showExerciseFilter = showExerciseFilter,
            exerciseOptions = exerciseOptions,
            sessionDays = sessionDays,
            onDismiss = { filterOpen = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Space.lg, end = Space.lg, top = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (activeCount == 0) "${sessions.size} sessions" else "${shown.size} of ${sessions.size} sessions",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = { filterOpen = true }) {
                Icon(Icons.Default.FilterList, contentDescription = null)
                Spacer(modifier = Modifier.width(Space.sm))
                Text(if (activeCount == 0) "Filters" else "Filters ($activeCount)")
            }
        }

        when {
            sessions.isEmpty() -> EmptyNote("No sessions logged yet.")
            shown.isEmpty() -> EmptyNote("No sessions match these filters.")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Space.lg, end = Space.lg, top = Space.md, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                items(shown, key = { it.session.id }) { item ->
                    SessionListCard(
                        item = item,
                        showWorkoutName = showWorkoutName,
                        showExerciseNames = showExerciseNames,
                        onClick = { onOpenSession(item.session.id) },
                        onDelete = { toDelete = item }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(Space.xl), contentAlignment = Alignment.TopStart) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SessionListCard(
    item: SessionWithSets,
    showWorkoutName: Boolean,
    showExerciseNames: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val elapsed = ((item.session.finishedAt - item.session.startedAt) / 1000L).toInt()
    val exerciseCount = item.sets.map { it.exerciseName }.distinct().size
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Space.lg, top = Space.sm, bottom = Space.sm, end = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (showWorkoutName) item.session.workoutName else formatTimestamp(item.session.startedAt),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showWorkoutName) {
                    Text(formatTimestamp(item.session.startedAt), style = MaterialTheme.typography.bodySmall)
                }
                if (showExerciseNames) {
                    Text(
                        item.sets.map { it.exerciseName }.distinct().joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "$exerciseCount exercises · ${item.sets.size} sets · ${formatDuration(elapsed)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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

private val DatePresets = listOf(
    DateRangeKind.ALL to "All time",
    DateRangeKind.DAYS_7 to "Last 7 days",
    DateRangeKind.DAYS_30 to "Last 30 days",
    DateRangeKind.DAYS_90 to "Last 90 days",
    DateRangeKind.YEAR_1 to "Last year",
    DateRangeKind.CUSTOM to "Custom range"
)

/**
 * Fixed-size popup: one drop-down section per filter category (exercise and muscle group when
 * enabled, then date range and pictures), each opening onto checkboxes. Only one section is open
 * at a time and its checkboxes scroll inside the popup, so the popup itself never grows or shrinks.
 * Changes apply live.
 */
@Composable
private fun HistoryFilterDialog(
    filters: HistoryFilters,
    dates: DateRangeState,
    matchCount: Int,
    showMuscleFilter: Boolean,
    showExerciseFilter: Boolean,
    exerciseOptions: List<String>,
    sessionDays: Set<String>,
    onDismiss: () -> Unit
) {
    // The open section by key ("" = none); starts on the first one shown.
    var open by remember {
        mutableStateOf(if (showExerciseFilter) "exercise" else if (showMuscleFilter) "muscle" else "date")
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).height(FilterDialogHeight),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(Space.lg)) {
                Text("Filter sessions", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(Space.sm))

                Column(modifier = Modifier.weight(1f)) {
                    if (showExerciseFilter) {
                        FilterSection(
                            title = "Exercise",
                            summary = when (filters.exercises.size) {
                                0 -> "Any"
                                1 -> exerciseOptions.firstOrNull { it.lowercase() in filters.exercises }
                                    ?: filters.exercises.first()
                                else -> "${filters.exercises.size} selected"
                            },
                            expanded = open == "exercise",
                            onToggle = { open = if (open == "exercise") "" else "exercise" }
                        ) {
                            var query by rememberSaveable { mutableStateOf("") }
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                label = { Text("Search exercises") },
                                modifier = Modifier.fillMaxWidth().padding(bottom = Space.sm)
                            )
                            val needle = query.trim().lowercase()
                            val (chosen, rest) = exerciseOptions.partition { it.lowercase() in filters.exercises }
                            val visible = chosen + rest.filter { needle.isEmpty() || it.lowercase().contains(needle) }
                            if (visible.isEmpty()) {
                                Text(
                                    "No exercises match.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            visible.forEach { name ->
                                val key = name.lowercase()
                                CheckRow(name, key in filters.exercises) {
                                    filters.exercises =
                                        if (key in filters.exercises) filters.exercises - key
                                        else filters.exercises + key
                                }
                            }
                        }
                    }
                    if (showMuscleFilter) {
                        FilterSection(
                            title = "Muscle group",
                            summary = when (filters.regions.size) {
                                0 -> "Any"
                                1 -> filters.regions.first().label
                                else -> "${filters.regions.size} selected"
                            },
                            expanded = open == "muscle",
                            onToggle = { open = if (open == "muscle") "" else "muscle" }
                        ) {
                            BodyRegion.entries.forEach { region ->
                                CheckRow(region.label, region in filters.regions) {
                                    filters.regions =
                                        if (region in filters.regions) filters.regions - region
                                        else filters.regions + region
                                }
                            }
                        }
                    }
                    FilterSection(
                        title = "Date range",
                        summary = DatePresets.first { it.first == dates.kind }.second,
                        expanded = open == "date",
                        onToggle = { open = if (open == "date") "" else "date" }
                    ) {
                        DatePresets.forEach { (kind, label) ->
                            CheckRow(label, dates.kind == kind) {
                                // Unticking the chosen preset falls back to all time.
                                dates.kind = if (dates.kind == kind) DateRangeKind.ALL else kind
                            }
                            if (kind == DateRangeKind.CUSTOM && dates.kind == DateRangeKind.CUSTOM) {
                                Box(modifier = Modifier.padding(start = 40.dp, bottom = Space.sm)) {
                                    CustomRangeRow(dates, stacked = true, pickableDays = sessionDays)
                                }
                            }
                        }
                    }
                    FilterSection(
                        title = "Pictures",
                        summary = when {
                            filters.withPhotos == filters.withoutPhotos -> "Any"
                            filters.withPhotos -> "With pictures"
                            else -> "Without pictures"
                        },
                        expanded = open == "pictures",
                        onToggle = { open = if (open == "pictures") "" else "pictures" }
                    ) {
                        CheckRow("Has pictures", filters.withPhotos) { filters.withPhotos = !filters.withPhotos }
                        CheckRow("No pictures", filters.withoutPhotos) { filters.withoutPhotos = !filters.withoutPhotos }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        filters.clear()
                        dates.kind = DateRangeKind.ALL
                    }) { Text("Clear all") }
                    Button(onClick = onDismiss) { Text("Show $matchCount") }
                }
            }
        }
    }
}

@Composable
internal fun ColumnScope.FilterSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    body: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onToggle)
            .padding(horizontal = Space.xs, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse $title" else "Expand $title"
        )
    }
    if (expanded) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            content = body
        )
    }
}

@Composable
internal fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(Space.md))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
