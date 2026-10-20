package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import com.ashrdev.aznd.ui.components.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.data.workout.toMeta
import com.ashrdev.aznd.domain.ExerciseMeta
import com.ashrdev.aznd.domain.LogField
import com.ashrdev.aznd.domain.SetCard
import com.ashrdev.aznd.domain.SetKind
import com.ashrdev.aznd.domain.SetRow
import com.ashrdev.aznd.domain.Suggestion
import com.ashrdev.aznd.domain.canonicalFieldText
import com.ashrdev.aznd.domain.fieldLabelWithTarget
import com.ashrdev.aznd.domain.fieldLayout
import com.ashrdev.aznd.domain.fieldText
import com.ashrdev.aznd.domain.formatKcal
import com.ashrdev.aznd.domain.label
import com.ashrdev.aznd.domain.previousRowFor
import com.ashrdev.aznd.domain.rowsKcal
import com.ashrdev.aznd.domain.setLabels
import com.ashrdev.aznd.domain.suggestionField
import com.ashrdev.aznd.domain.suggestionText
import com.ashrdev.aznd.domain.targetLabel

private val Indent = 20.dp
private val SmallIcon = 40.dp

/** Everything the card list needs; bundled so the stateless composables stay readable. */
class LoggerData(
    val rows: List<SetRow>,
    val cards: List<SetCard>,
    val depth: Map<Long, Int>,
    val exercises: List<Exercise>,
    val previous: List<SetRow>,
    val suggestions: Map<Long, Suggestion>,
    val bodyweightKg: Double,
    val showRpe: Boolean,
    val showTargets: Boolean
) {
    val byId: Map<Long, Exercise> = exercises.associateBy { it.id }
    val meta: (Long) -> ExerciseMeta? = { id -> byId[id]?.toMeta() }
    val totalKcal: Double get() = rowsKcal(rows, meta, bodyweightKg)
}

/**
 * The running workout: one exercise card per page, swiped horizontally with snapping, so a whole
 * exercise is always in view (the next and previous cards peek in at the edges). A card taller
 * than the screen scrolls vertically on its own page.
 *
 * Under each card sit "Add set" and "Delete exercise" (skips the exercise in this session only).
 * They follow the last set; once the sets fill the screen they stay pinned just above the bottom
 * bar while the sets scroll. The last page holds "Add exercise"; adding one swipes to the new
 * card. A header shows the estimated total and which exercise this is.
 * [bottomPadding] keeps the content clear of the bottom bar. The caller owns [pagerState] (it
 * needs the current page for the top bar title) and must give it cards.size + 1 pages.
 */
@Composable
fun LoggerPager(
    data: LoggerData,
    actions: LoggerActions,
    pagerState: PagerState,
    bottomPadding: Dp,
    onAddExercise: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cards = data.cards
    var cardToDelete by remember { mutableStateOf<SetCard?>(null) }

    cardToDelete?.let { card ->
        val name = card.exerciseId?.let { data.byId[it]?.name } ?: "this exercise"
        AlertDialog(
            onDismissRequest = { cardToDelete = null },
            title = { Text("Delete exercise?") },
            text = { Text("$name and everything logged for it will be removed from this session. Your saved workout is not changed.") },
            confirmButton = {
                TextButton(onClick = {
                    cardToDelete = null
                    actions.onDeleteCard(card.rows.map { it.id })
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { cardToDelete = null }) { Text("Keep") } }
        )
    }

    // A card was added: bring it into view.
    var lastCount by remember { mutableIntStateOf(cards.size) }
    LaunchedEffect(cards.size) {
        if (cards.size > lastCount) pagerState.animateScrollToPage(cards.size - 1)
        lastCount = cards.size
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Estimated total: ${formatKcal(data.totalKcal)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (pagerState.currentPage < cards.size) "${pagerState.currentPage + 1} / ${cards.size}" else "Add",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 8.dp,
            verticalAlignment = Alignment.Top,
            key = { index ->
                val k: Any = cards.getOrNull(index)?.rows?.firstOrNull()?.id ?: "add-exercise"
                k
            }
        ) { page ->
            val card = cards.getOrNull(page)
            val hasButtons = card != null && card.exerciseId != null
            val scroll = rememberScrollState()

            // A set was added: scroll down to it.
            if (card != null) {
                var lastRows by remember { mutableIntStateOf(card.rows.size) }
                LaunchedEffect(card.rows.size) {
                    if (card.rows.size > lastRows) {
                        withFrameNanos { }
                        scroll.animateScrollTo(scroll.maxValue)
                    }
                    lastRows = card.rows.size
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // fill = false: a short card keeps its buttons right under the last set; a tall one
                // takes the free height, scrolls, and leaves the buttons pinned above the bar.
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scroll)
                        .padding(top = 4.dp, bottom = if (hasButtons) 0.dp else bottomPadding)
                ) {
                    if (card != null) {
                        LoggerCard(card, data, actions)
                    } else {
                        OutlinedButton(onClick = onAddExercise, modifier = Modifier.fillMaxWidth()) { Text("Add exercise") }
                    }
                }
                if (card != null && hasButtons) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = bottomPadding),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { actions.onAddSet(card.rows.first().id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add set", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { cardToDelete = card },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete exercise", maxLines = 1, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoggerCard(card: SetCard, data: LoggerData, actions: LoggerActions) {
    val first = card.rows.first()
    val exercise = card.exerciseId?.let { data.byId[it] }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (exercise != null) MuscleBadge(exercise.primaryMuscle, exercise.photoPath)
                Column(modifier = Modifier.weight(1f)) {
                    ExercisePicker(
                        selected = exercise,
                        exercises = data.exercises,
                        onPick = { actions.onPickCardExercise(first.id, it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (exercise != null) {
                        Text(
                            text = "≈ ${formatKcal(rowsKcal(card.rows, data.meta, data.bodyweightKg))} (estimated)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (card.exerciseId == null) {
                    IconButton(onClick = { actions.onDelete(first.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove card", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (card.exerciseId != null) {
                val labels = setLabels(card)
                val connectors = connectorInfo(card.rows)
                card.rows.forEach { row ->
                    key(row.id) {
                        LoggerRow(row, data.depth[row.id] ?: 0, labels[row.id].orEmpty(), connectors[row.id], data, actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoggerRow(
    row: SetRow,
    depth: Int,
    label: String,
    connector: ConnectorInfo?,
    data: LoggerData,
    actions: LoggerActions
) {
    val exercise = row.exerciseId?.let { data.byId[it] }
    val lineColor = MaterialTheme.colorScheme.outline
    val nested = if (depth > 0 && connector != null) {
        Modifier.treeConnector(
            lineColor, Indent, connector, gap = 10.dp,
            elbowY = if (row.kind == SetKind.SUPERSET) 28.dp else SmallIcon / 2
        )
    } else {
        Modifier
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Indent * depth)
            .then(nested),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (row.kind == SetKind.SUPERSET) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (exercise != null) MuscleBadge(exercise.primaryMuscle, exercise.photoPath, size = 28.dp)
                ExercisePicker(
                    selected = exercise,
                    exercises = data.exercises,
                    onPick = { actions.onPickRowExercise(row.id, it) },
                    modifier = Modifier.weight(1f),
                    label = "Superset exercise"
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { actions.onDuplicate(row.id) }, modifier = Modifier.size(SmallIcon)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate")
            }
            IconButton(onClick = { actions.onDelete(row.id) }, modifier = Modifier.size(SmallIcon)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
            LoggerMenu(onSuperset = { actions.onSuperset(row.id) }, onDrop = { actions.onDrop(row.id) })
        }
        if (exercise != null) {
            LoggerFields(row, exercise, data, actions)
            val prev = previousRowFor(row, data.rows, data.previous)
                ?.let { formatPreviousSet(exercise.type, it, data.showRpe) }
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

@Composable
private fun LoggerFields(row: SetRow, exercise: Exercise, data: LoggerData, actions: LoggerActions) {
    val layout = fieldLayout(exercise.type, data.showRpe)
    val target = targetLabel(row, exercise.type, data.showTargets)
    val suggestion = data.suggestions[row.id]
    val fillsField = suggestionField(exercise.type)
    // Hide the hint once the field already holds that value.
    val hint = suggestion?.takeIf { fillsField != null && fieldText(row, fillsField) != fieldText(applySuggestion(row, exercise, it), fillsField) }

    @Composable
    fun hintFor(field: LogField) {
        if (hint != null && fillsField == field) {
            Text(
                text = suggestionText(exercise.type, hint.fieldKg),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { actions.onSuggest(row.id, hint) }
                    .padding(vertical = 4.dp)
            )
        }
    }

    hintFor(layout.big)
    LogInput(row, layout.big, fieldLabelWithTarget(layout.big, target), big = true, actions = actions, modifier = Modifier.fillMaxWidth())
    if (layout.second.isNotEmpty()) {
        Column {
            layout.second.forEach { hintFor(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                layout.second.forEach { f ->
                    LogInput(row, f, fieldLabelWithTarget(f, target), big = false, actions = actions, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** What the row would hold after tapping the hint (only used to decide whether to still show it). */
private fun applySuggestion(row: SetRow, exercise: Exercise, s: Suggestion): SetRow =
    com.ashrdev.aznd.domain.applySuggestionTo(row, exercise.type, s.fieldKg)

/**
 * One input box. The user's text is kept locally while typing; only valid text is stored (invalid
 * keeps the old value, blank clears it). The text is replaced from the stored value only when that
 * value CHANGED from outside (a tapped suggestion) or when focus leaves the box.
 */
@Composable
private fun LogInput(
    row: SetRow,
    field: LogField,
    label: String,
    big: Boolean,
    actions: LoggerActions,
    modifier: Modifier
) {
    val stored = fieldText(row, field)
    var text by remember(row.id, field) { mutableStateOf(stored) }
    val latestStored by rememberUpdatedState(stored)
    LaunchedEffect(stored) {
        if (canonicalFieldText(field, text) != stored) text = stored
    }
    val whole = field == LogField.REPS || field == LogField.SECONDS
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            actions.onField(row.id, field, it)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (whole) KeyboardType.Number else KeyboardType.Decimal),
        textStyle = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,
        modifier = modifier.onFocusChanged { if (!it.isFocused) text = latestStored }
    )
}

@Composable
private fun LoggerMenu(onSuperset: () -> Unit, onDrop: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(SmallIcon)) {
            Icon(Icons.Default.MoreVert, contentDescription = "More")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Superset") }, onClick = { open = false; onSuperset() })
            DropdownMenuItem(text = { Text("Drop set") }, onClick = { open = false; onDrop() })
        }
    }
}
