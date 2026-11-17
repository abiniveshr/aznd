package com.ashrdev.aznd.ui.workout.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.SlidingPageTitle
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.domain.workout.ExerciseMeta
import com.ashrdev.aznd.domain.workout.LogField
import com.ashrdev.aznd.domain.workout.SetCard
import com.ashrdev.aznd.domain.workout.SetKind
import com.ashrdev.aznd.domain.workout.SetRow
import com.ashrdev.aznd.domain.workout.Suggestion
import com.ashrdev.aznd.domain.workout.canonicalFieldText
import com.ashrdev.aznd.domain.workout.fieldLabelWithTarget
import com.ashrdev.aznd.domain.workout.fieldLayout
import com.ashrdev.aznd.domain.workout.fieldText
import com.ashrdev.aznd.domain.workout.formatKcal
import com.ashrdev.aznd.domain.workout.label
import com.ashrdev.aznd.domain.workout.rowsKcal
import com.ashrdev.aznd.domain.workout.setLabels
import com.ashrdev.aznd.domain.workout.suggestionField
import com.ashrdev.aznd.domain.workout.suggestionText
import com.ashrdev.aznd.domain.workout.targetLabel
import com.ashrdev.aznd.ui.workout.ConnectorInfo
import com.ashrdev.aznd.ui.workout.connectorInfo
import com.ashrdev.aznd.ui.workout.exercise.ExercisePicker
import com.ashrdev.aznd.ui.workout.exercise.MuscleBadge
import com.ashrdev.aznd.ui.workout.treeConnector
import com.ashrdev.aznd.ui.theme.Space

private val Indent = 20.dp
private val TouchTarget = 48.dp

/**
 * What does not change while typing. One instance is shared by every card, so a card whose own
 * rows are unchanged skips recomposition.
 */
class LoggerEnv(
    val exercises: List<Exercise>,
    val byId: Map<Long, Exercise>,
    val meta: Map<Long, ExerciseMeta>,
    val suggestions: Map<Long, Suggestion>,
    val bodyweightKg: Double,
    val showRpe: Boolean,
    val showTargets: Boolean
) {
    val metaOf: (Long) -> ExerciseMeta? = { id -> meta[id] }
}

/** Everything the card list needs; bundled so the stateless composables stay readable. */
class LoggerData(
    val rows: List<SetRow>,
    val cards: List<SetCard>,
    val depth: Map<Long, Int>,
    val previousOf: Map<Long, SetRow>,
    val env: LoggerEnv
)

/** The focus order of one card's inputs: [next] is where the keyboard's Next key goes (null on the last). */
private class FieldFocus(val requester: FocusRequester, val next: FocusRequester?)

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
 * [undoVisible] shows the "Set deleted" strip that calls [LoggerActions.onUndoDelete].
 */
@Composable
fun LoggerPager(
    data: LoggerData,
    actions: LoggerActions,
    pagerState: PagerState,
    bottomPadding: Dp,
    onAddExercise: () -> Unit,
    modifier: Modifier = Modifier,
    undoVisible: Boolean = false
) {
    val cards = data.cards
    val env = data.env
    val totalKcal = remember(data.rows, env) { rowsKcal(data.rows, env.metaOf, env.bodyweightKg) }
    var cardToDelete by remember { mutableStateOf<SetCard?>(null) }

    cardToDelete?.let { card ->
        val name = card.exerciseId?.let { env.byId[it]?.name } ?: "this exercise"
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
                .padding(horizontal = Space.lg, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Estimated total: ${formatKcal(totalKcal)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // The counter drifts and cross-fades with the swipe, like a top-bar title.
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary)) {
                SlidingPageTitle(
                    pageCount = pagerState.pageCount,
                    position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                    title = { page -> if (page < cards.size) "${page + 1} / ${cards.size}" else "Add" },
                    endAligned = true
                )
            }
        }
        if (undoVisible) UndoStrip(onUndo = actions.onUndoDelete)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Space.lg),
            pageSpacing = Space.sm,
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
                        .padding(top = Space.xs, bottom = if (hasButtons) 0.dp else bottomPadding)
                ) {
                    if (card != null) {
                        LoggerCard(card, data.depth, data.previousOf, env, actions)
                    } else {
                        OutlinedButton(onClick = onAddExercise, modifier = Modifier.fillMaxWidth()) { Text("Add exercise") }
                    }
                }
                if (card != null && hasButtons) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Space.sm, bottom = bottomPadding),
                        horizontalArrangement = Arrangement.spacedBy(Space.md)
                    ) {
                        OutlinedButton(
                            onClick = { actions.onAddSet(card.rows.first().id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(Space.sm))
                            Text("Add set", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { cardToDelete = card },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(Space.sm))
                            Text("Delete exercise", maxLines = 1, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

/** "Set deleted" with Undo; the view model hides it after a few seconds. */
@Composable
private fun UndoStrip(onUndo: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg)
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            modifier = Modifier.padding(start = Space.lg, end = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Set deleted", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onUndo, modifier = Modifier.heightIn(min = TouchTarget)) {
                Text("Undo", color = MaterialTheme.colorScheme.inversePrimary)
            }
        }
    }
}

@Composable
private fun LoggerCard(
    card: SetCard,
    depth: Map<Long, Int>,
    previousOf: Map<Long, SetRow>,
    env: LoggerEnv,
    actions: LoggerActions
) {
    val first = card.rows.first()
    val exercise = card.exerciseId?.let { env.byId[it] }
    val kcal = remember(card, env) { rowsKcal(card.rows, env.metaOf, env.bodyweightKg) }
    // Connectors and the focus order only depend on which rows exist, not on typed values.
    val shape = card.rows.map { it.id to it.parentId }
    val connectors = remember(shape) { connectorInfo(card.rows) }
    val fieldKeys = card.rows.flatMap { row ->
        val type = row.exerciseId?.let { env.byId[it]?.type } ?: return@flatMap emptyList()
        val layout = fieldLayout(type, env.showRpe)
        (listOf(layout.big) + layout.second).map { row.id to it }
    }
    val focus = remember(fieldKeys) {
        val requesters = fieldKeys.map { FocusRequester() }
        val byRow = HashMap<Long, MutableMap<LogField, FieldFocus>>()
        fieldKeys.forEachIndexed { i, (rowId, field) ->
            byRow.getOrPut(rowId) { HashMap() }[field] = FieldFocus(requesters[i], requesters.getOrNull(i + 1))
        }
        byRow
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (exercise != null) MuscleBadge(exercise.primaryMuscle, exercise.photoPath)
                Column(modifier = Modifier.weight(1f)) {
                    ExercisePicker(
                        selected = exercise,
                        exercises = env.exercises,
                        onPick = { actions.onPickCardExercise(first.id, it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (exercise != null) {
                        Text(
                            text = "≈ ${formatKcal(kcal)} (estimated)",
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
                card.rows.forEach { row ->
                    key(row.id) {
                        LoggerRow(
                            row = row,
                            depth = depth[row.id] ?: 0,
                            label = labels[row.id].orEmpty(),
                            connector = connectors[row.id],
                            previous = previousOf[row.id],
                            suggestion = env.suggestions[row.id],
                            focus = focus[row.id].orEmpty(),
                            env = env,
                            actions = actions
                        )
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
    previous: SetRow?,
    suggestion: Suggestion?,
    focus: Map<LogField, FieldFocus>,
    env: LoggerEnv,
    actions: LoggerActions
) {
    val exercise = row.exerciseId?.let { env.byId[it] }
    val lineColor = MaterialTheme.colorScheme.outline
    val nested = if (depth > 0 && connector != null) {
        Modifier.treeConnector(
            lineColor, Indent, connector, gap = 10.dp,
            elbowY = if (row.kind == SetKind.SUPERSET) 28.dp else TouchTarget / 2
        )
    } else {
        Modifier
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Indent * depth)
            .then(nested),
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        if (row.kind == SetKind.SUPERSET) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (exercise != null) MuscleBadge(exercise.primaryMuscle, exercise.photoPath, size = 28.dp)
                ExercisePicker(
                    selected = exercise,
                    exercises = env.exercises,
                    onPick = { actions.onPickRowExercise(row.id, it) },
                    modifier = Modifier.weight(1f),
                    label = "Superset exercise"
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { actions.onDuplicate(row.id) }, modifier = Modifier.size(TouchTarget)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate")
            }
            IconButton(onClick = { actions.onDelete(row.id) }, modifier = Modifier.size(TouchTarget)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
            LoggerMenu(onSuperset = { actions.onSuperset(row.id) }, onDrop = { actions.onDrop(row.id) })
        }
        if (exercise != null) {
            LoggerFields(row, exercise, suggestion, focus, env, actions)
            val prev = previous?.let { formatPreviousSet(exercise.type, it, env.showRpe) }
            if (previous != null && prev != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TouchTarget)
                        .clickable(onClickLabel = "Use previous values", role = Role.Button) {
                            actions.onFillPrevious(row.id, previous)
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
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

@Composable
private fun LoggerFields(
    row: SetRow,
    exercise: Exercise,
    suggestion: Suggestion?,
    focus: Map<LogField, FieldFocus>,
    env: LoggerEnv,
    actions: LoggerActions
) {
    val layout = fieldLayout(exercise.type, env.showRpe)
    val target = targetLabel(row, exercise.type, env.showTargets)
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
                    .padding(vertical = Space.xs)
            )
        }
    }

    hintFor(layout.big)
    LogInput(row, layout.big, fieldLabelWithTarget(layout.big, target), big = true, actions = actions, focus = focus[layout.big], modifier = Modifier.fillMaxWidth())
    if (layout.second.isNotEmpty()) {
        Column {
            layout.second.forEach { hintFor(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                layout.second.forEach { f ->
                    LogInput(row, f, fieldLabelWithTarget(f, target), big = false, actions = actions, focus = focus[f], modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** What the row would hold after tapping the hint (only used to decide whether to still show it). */
private fun applySuggestion(row: SetRow, exercise: Exercise, s: Suggestion): SetRow =
    com.ashrdev.aznd.domain.workout.applySuggestionTo(row, exercise.type, s.fieldKg)

/**
 * One input box. The user's text is kept locally while typing; only valid text is stored (invalid
 * keeps the old value, blank clears it). The text is replaced from the stored value only when that
 * value CHANGED from outside (a tapped suggestion) or when focus leaves the box.
 * The keyboard's action key goes to [FieldFocus.next]; the last box of a card gets Done, which closes
 * the keyboard (with no KeyboardActions given, Compose's default for Next moves focus and for Done hides it).
 */
@Composable
private fun LogInput(
    row: SetRow,
    field: LogField,
    label: String,
    big: Boolean,
    actions: LoggerActions,
    focus: FieldFocus?,
    modifier: Modifier
) {
    val stored = fieldText(row, field)
    var text by remember(row.id, field) { mutableStateOf(stored) }
    val latestStored by rememberUpdatedState(stored)
    LaunchedEffect(stored) {
        if (canonicalFieldText(field, text) != stored) text = stored
    }
    val whole = field == LogField.REPS || field == LogField.SECONDS
    val after = focus?.next
    val order = if (focus == null) {
        Modifier
    } else {
        Modifier
            .focusRequester(focus.requester)
            .focusProperties { if (after != null) next = after }
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            actions.onField(row.id, field, it)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (whole) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = if (after != null) ImeAction.Next else ImeAction.Done
        ),
        textStyle = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,
        modifier = modifier.then(order).onFocusChanged { if (!it.isFocused) text = latestStored }
    )
}

@Composable
private fun LoggerMenu(onSuperset: () -> Unit, onDrop: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(TouchTarget)) {
            Icon(Icons.Default.MoreVert, contentDescription = "More")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Superset") }, onClick = { open = false; onSuperset() })
            DropdownMenuItem(text = { Text("Drop set") }, onClick = { open = false; onDrop() })
        }
    }
}
