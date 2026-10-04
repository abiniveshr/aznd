package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.data.workout.searchExercises
import com.ashrdev.aznd.domain.ExerciseType
import com.ashrdev.aznd.domain.Muscle
import com.ashrdev.aznd.ui.components.OutlinedTextField as FieldBox

/**
 * Exercise field. Looks like a text field showing the selected name, but tapping it opens a
 * full-screen search page (no dropdown, so the keyboard stays up while typing).
 * Same signature as before, so existing call sites don't change.
 */
@Composable
fun ExercisePicker(
    selected: Exercise?,
    exercises: List<Exercise>,
    onPick: (Exercise) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Exercise"
) {
    var open by remember { mutableStateOf(false) }

    Box(modifier) {
        FieldBox(
            value = selected?.name.orEmpty(),
            onValueChange = {},
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        // transparent overlay: swallows the tap so the field never takes focus / opens the keyboard
        Box(Modifier.matchParentSize().clickable { open = true })
    }

    if (open) {
        ExerciseSearchScreen(
            selected = selected,
            exercises = exercises,
            onDismiss = { open = false },
            onPick = {
                open = false
                onPick(it)
            }
        )
    }
}

private fun Muscle.label(): String = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun ExerciseType.label(): String = when (this) {
    ExerciseType.WEIGHTED -> "Weighted"
    ExerciseType.BODYWEIGHT_REPS -> "Bodyweight"
    ExerciseType.ASSISTED_BODYWEIGHT -> "Assisted"
    ExerciseType.TIME_HELD -> "Timed"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExerciseSearchScreen(
    selected: Exercise?,
    exercises: List<Exercise>,
    onDismiss: () -> Unit,
    onPick: (Exercise) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<Muscle?>(null) }
    var type by remember { mutableStateOf<ExerciseType?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    // No result cap: every exercise is reachable, ranked when searching, A-Z when not.
    val results = remember(exercises, query, muscle, type) {
        val pool = exercises.filter {
            (muscle == null || it.primaryMuscle == muscle) && (type == null || it.type == type)
        }
        searchExercises(pool, query, limit = Int.MAX_VALUE)
    }
    val grouped = query.isBlank()

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // dragging the list hides the keyboard so you can see more rows
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { if (it) keyboard?.hide() }
    }
    // jump back to the top whenever the filter changes
    LaunchedEffect(query, muscle, type) { listState.scrollToItem(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                    Text("Choose exercise", style = MaterialTheme.typography.titleLarge)
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search name or muscle") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .focusRequester(focus)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "all") {
                        FilterChip(
                            selected = muscle == null,
                            onClick = { muscle = null },
                            label = { Text("All") }
                        )
                    }
                    items(Muscle.values().toList(), key = { it.name }) { m ->
                        FilterChip(
                            selected = muscle == m,
                            onClick = { muscle = if (muscle == m) null else m },
                            label = { Text(m.label()) }
                        )
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ExerciseType.values().toList(), key = { it.name }) { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = if (type == t) null else t },
                            label = { Text(t.label()) }
                        )
                    }
                }

                Text(
                    text = if (results.size == 1) "1 exercise" else "${results.size} exercises",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )

                if (results.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f).padding(32.dp), contentAlignment = Alignment.TopCenter) {
                        Text(
                            "No matching exercise",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        )
                    ) {
                        if (grouped) {
                            val byLetter = results.groupBy { it.name.first().uppercaseChar().toString() }
                            byLetter.forEach { (letter, group) ->
                                stickyHeader(key = "h-$letter") {
                                    Text(
                                        text = letter,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.background)
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }
                                items(group, key = { it.id }) { e ->
                                    ExerciseRow(e, e.id == selected?.id) { onPick(e) }
                                }
                            }
                        } else {
                            items(results, key = { it.id }) { e ->
                                ExerciseRow(e, e.id == selected?.id) { onPick(e) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, isSelected: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.background
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            MuscleBadge(exercise.primaryMuscle, exercise.photoPath, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val muscles = (listOf(exercise.primaryMuscle) + exercise.secondaryMuscles)
                    .joinToString(", ") { it.name.lowercase().replace('_', ' ') }
                Text(
                    text = "$muscles  •  ${exercise.type.label()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isSelected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}
