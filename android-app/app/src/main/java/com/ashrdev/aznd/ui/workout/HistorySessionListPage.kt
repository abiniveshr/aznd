package com.ashrdev.aznd.ui.workout

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
import androidx.compose.runtime.mutableIntStateOf
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
import com.ashrdev.aznd.domain.BodyRegion
import com.ashrdev.aznd.domain.Muscle
import com.ashrdev.aznd.ui.calendar.dayKeyOf
import com.ashrdev.aznd.ui.common.CustomRangeRow
import com.ashrdev.aznd.ui.common.DateRangeKind
import com.ashrdev.aznd.ui.common.DateRangeState
import com.ashrdev.aznd.ui.common.DayRange
import com.ashrdev.aznd.ui.common.rememberDateRangeState
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton

// The popup never changes size: whichever filter category is open scrolls inside the space left.
private val FilterDialogHeight = 480.dp

/**
 * The non-date filters of the session list. Muscle group is a [BodyRegion] (a session matches when
 * any exercise's primary muscle is in a chosen region). Picture filters are two boxes: tick one to
 * keep only sessions with / without a photo; ticking both or neither means no picture filter.
 */
@Stable
internal class HistoryFilters(
    regions: Set<BodyRegion> = emptySet(),
    withPhotos: Boolean = false,
    withoutPhotos: Boolean = false
) {
    var regions by mutableStateOf(regions)
    var withPhotos by mutableStateOf(withPhotos)
    var withoutPhotos by mutableStateOf(withoutPhotos)

    val pictureFilterActive: Boolean get() = withPhotos != withoutPhotos

    fun clear() {
        regions = emptySet()
        withPhotos = false
        withoutPhotos = false
    }

    companion object {
        val Saver: Saver<HistoryFilters, Any> = listSaver<HistoryFilters, Any>(
            save = { listOf(it.regions.joinToString(",") { r -> r.name }, it.withPhotos, it.withoutPhotos) },
            restore = {
                val names = (it[0] as String).split(",")
                HistoryFilters(
                    regions = BodyRegion.entries.filter { r -> r.name in names }.toSet(),
                    withPhotos = it[1] as Boolean,
                    withoutPhotos = it[2] as Boolean
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
        inRange && photoOk && muscleOk
    }
}

/** Right page: every logged session, newest first, with a Filters button. */
@Composable
internal fun HistorySessionListPage(
    sessions: List<SessionWithSets>,
    catalog: List<Exercise>,
    bottomPadding: Dp,
    onOpenSession: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit
) {
    val filters = rememberSaveable(saver = HistoryFilters.Saver) { HistoryFilters() }
    val dates = rememberDateRangeState()
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<SessionWithSets?>(null) }

    val muscleByName = remember(catalog) { catalog.associate { it.name.lowercase() to it.primaryMuscle } }
    val shown = filterSessions(sessions, filters, dates.range, muscleByName)
    val activeCount = (if (filters.regions.isNotEmpty()) 1 else 0) +
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
            onDismiss = { filterOpen = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (activeCount == 0) "${sessions.size} sessions" else "${shown.size} of ${sessions.size} sessions",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = { filterOpen = true }) {
                Icon(Icons.Default.FilterList, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (activeCount == 0) "Filters" else "Filters ($activeCount)")
            }
        }

        when {
            sessions.isEmpty() -> EmptyNote("No sessions logged yet.")
            shown.isEmpty() -> EmptyNote("No sessions match these filters.")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(shown, key = { it.session.id }) { item ->
                    SessionListCard(
                        item = item,
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
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopStart) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SessionListCard(item: SessionWithSets, onClick: () -> Unit, onDelete: () -> Unit) {
    val elapsed = ((item.session.finishedAt - item.session.startedAt) / 1000L).toInt()
    val exerciseCount = item.sets.map { it.exerciseName }.distinct().size
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.session.workoutName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(formatTimestamp(item.session.startedAt), style = MaterialTheme.typography.bodySmall)
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
 * Fixed-size popup: one drop-down section per filter category (muscle group, date range,
 * pictures), each opening onto checkboxes. Only one section is open at a time and its checkboxes
 * scroll inside the popup, so the popup itself never grows or shrinks. Changes apply live.
 */
@Composable
private fun HistoryFilterDialog(
    filters: HistoryFilters,
    dates: DateRangeState,
    matchCount: Int,
    onDismiss: () -> Unit
) {
    var open by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).height(FilterDialogHeight),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Filter sessions", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    FilterSection(
                        title = "Muscle group",
                        summary = when (filters.regions.size) {
                            0 -> "Any"
                            1 -> filters.regions.first().label
                            else -> "${filters.regions.size} selected"
                        },
                        expanded = open == 0,
                        onToggle = { open = if (open == 0) -1 else 0 }
                    ) {
                        BodyRegion.entries.forEach { region ->
                            CheckRow(region.label, region in filters.regions) {
                                filters.regions =
                                    if (region in filters.regions) filters.regions - region
                                    else filters.regions + region
                            }
                        }
                    }
                    FilterSection(
                        title = "Date range",
                        summary = DatePresets.first { it.first == dates.kind }.second,
                        expanded = open == 1,
                        onToggle = { open = if (open == 1) -1 else 1 }
                    ) {
                        DatePresets.forEach { (kind, label) ->
                            CheckRow(label, dates.kind == kind) {
                                // Unticking the chosen preset falls back to all time.
                                dates.kind = if (dates.kind == kind) DateRangeKind.ALL else kind
                            }
                            if (kind == DateRangeKind.CUSTOM && dates.kind == DateRangeKind.CUSTOM) {
                                Box(modifier = Modifier.padding(start = 40.dp, bottom = 8.dp)) {
                                    CustomRangeRow(dates, stacked = true)
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
                        expanded = open == 2,
                        onToggle = { open = if (open == 2) -1 else 2 }
                    ) {
                        CheckRow("Has pictures", filters.withPhotos) { filters.withPhotos = !filters.withPhotos }
                        CheckRow("No pictures", filters.withoutPhotos) { filters.withoutPhotos = !filters.withoutPhotos }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
private fun ColumnScope.FilterSection(
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
            .padding(horizontal = 4.dp, vertical = 12.dp),
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
private fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
