package com.ashrdev.aznd.ui.workout.exercise

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.ashrdev.aznd.ui.theme.ThemeColor
import com.ashrdev.aznd.ui.theme.themedFill
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.data.workout.searchExercises
import com.ashrdev.aznd.data.workout.searchMuscles
import com.ashrdev.aznd.domain.workout.ExerciseType
import com.ashrdev.aznd.domain.workout.Muscle
import com.ashrdev.aznd.domain.workout.displayName
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.components.OutlinedButton

/*
 * The one exercise browser. Everything that lists, searches or filters exercises goes through
 * [ExerciseSearchContent]: the add-exercise picker (via [ExerciseSearchScreen]) and the Exercises
 * page. Only what happens on tap differs.
 */

/** "chest, triceps  •  Weight x reps": the muscles an exercise trains and its type, for list rows. */
fun Exercise.subtitle(): String =
    (listOf(primaryMuscle) + secondaryMuscles).joinToString(", ") { it.displayName().lowercase() } +
        "  •  " + type.formLabel()

/**
 * The full-screen browser (a dialog): a header with [title] and a back arrow above
 * [ExerciseSearchContent]. [onPick] receives the tapped exercise.
 */
@Composable
fun ExerciseSearchScreen(
    selected: Exercise?,
    exercises: List<Exercise>,
    onDismiss: () -> Unit,
    onPick: (Exercise) -> Unit,
    onAddCustom: (() -> Unit)?,
    title: String = "Choose exercise"
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().themedFill(ThemeColor.BACKGROUND),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge)
                }
                ExerciseSearchContent(
                    selected = selected,
                    exercises = exercises,
                    onPick = onPick,
                    onAddCustom = onAddCustom,
                    modifier = Modifier.weight(1f),
                    bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
            }
        }
    }
}

/**
 * Search field with a filter button beside it, an optional "Add custom exercise" button and the
 * A-Z list. The filter popup takes any number of muscles and types: an exercise must match one of
 * the chosen muscles (its primary one) AND one of the chosen types; an empty choice means all.
 * [autoFocus] opens the keyboard on arrival; [bottomPadding] keeps the list clear of bars.
 * [dataIds], when given, adds a "Has data" filter to the popup that keeps only exercises whose id
 * is in it (the Exercises page passes the exercises with logged sets; the add-exercise picker
 * passes null, so it has no such option).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExerciseSearchContent(
    selected: Exercise?,
    exercises: List<Exercise>,
    onPick: (Exercise) -> Unit,
    onAddCustom: (() -> Unit)?,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
    bottomPadding: Dp = 0.dp,
    dataIds: Set<Long>? = null
) {
    var query by remember { mutableStateOf("") }
    var onlyWithData by remember { mutableStateOf(false) }
    var muscles by remember { mutableStateOf(emptySet<Muscle>()) }
    var types by remember { mutableStateOf(emptySet<ExerciseType>()) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    // No result cap: every exercise is reachable, ranked when searching, A-Z when not.
    val results = remember(exercises, query, muscles, types, onlyWithData, dataIds) {
        val pool = exercises.filter {
            (muscles.isEmpty() || it.primaryMuscle in muscles) &&
                (types.isEmpty() || it.type in types) &&
                (!onlyWithData || dataIds?.contains(it.id) == true)
        }
        searchExercises(pool, query, limit = Int.MAX_VALUE)
    }
    val grouped = query.isBlank()

    if (autoFocus) {
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
    // dragging the list hides the keyboard so you can see more rows
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { if (it) keyboard?.hide() }
    }
    // jump back to the top whenever the filter changes
    LaunchedEffect(query, muscles, types, onlyWithData) { listState.scrollToItem(0) }

    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp)
        ) {
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
                modifier = Modifier.weight(1f).focusRequester(focus)
            )
            ExerciseFilterButton(
                muscles = muscles,
                types = types,
                resultCount = results.size,
                onMuscles = { muscles = it },
                onTypes = { types = it },
                showDataOption = dataIds != null,
                onlyWithData = onlyWithData,
                onOnlyWithData = { onlyWithData = it }
            )
        }

        if (onAddCustom != null) {
            OutlinedButton(
                onClick = onAddCustom,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add custom exercise")
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
                contentPadding = PaddingValues(bottom = bottomPadding)
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

/** The filter button: shows how many filters are on and opens [ExerciseFilterScreen]. */
@Composable
private fun ExerciseFilterButton(
    muscles: Set<Muscle>,
    types: Set<ExerciseType>,
    resultCount: Int,
    onMuscles: (Set<Muscle>) -> Unit,
    onTypes: (Set<ExerciseType>) -> Unit,
    showDataOption: Boolean,
    onlyWithData: Boolean,
    onOnlyWithData: (Boolean) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val count = muscles.size + types.size + if (onlyWithData) 1 else 0

    IconButton(onClick = { open = true }) {
        BadgedBox(badge = { if (count > 0) Badge { Text(count.toString()) } }) {
            Icon(Icons.Default.FilterList, contentDescription = "Filter")
        }
    }
    if (open) {
        ExerciseFilterScreen(
            muscles = muscles,
            types = types,
            resultCount = resultCount,
            onMuscles = onMuscles,
            onTypes = onTypes,
            showDataOption = showDataOption,
            onlyWithData = onlyWithData,
            onOnlyWithData = onOnlyWithData,
            onDismiss = { open = false }
        )
    }
}

/**
 * Full-screen filters: exercise types on top, then a searchable grid of muscles. Both are
 * multi-select and apply immediately; the bottom button closes the screen.
 */
@Composable
private fun ExerciseFilterScreen(
    muscles: Set<Muscle>,
    types: Set<ExerciseType>,
    resultCount: Int,
    onMuscles: (Set<Muscle>) -> Unit,
    onTypes: (Set<ExerciseType>) -> Unit,
    showDataOption: Boolean,
    onlyWithData: Boolean,
    onOnlyWithData: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query) { searchMuscles(query) }
    val count = muscles.size + types.size + if (onlyWithData) 1 else 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().themedFill(ThemeColor.BACKGROUND),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                    Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(
                        enabled = count > 0,
                        onClick = {
                            onMuscles(emptySet())
                            onTypes(emptySet())
                            onOnlyWithData(false)
                        }
                    ) { Text("Clear") }
                }

                if (showDataOption) {
                    Text(
                        "Data",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 0.dp)) {
                        FilterChip(
                            selected = onlyWithData,
                            onClick = { onOnlyWithData(!onlyWithData) },
                            label = { Text("Has data") }
                        )
                    }
                }

                Text(
                    "Type",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ExerciseType.entries.toList(), key = { it.name }) { t ->
                        FilterChip(
                            selected = t in types,
                            onClick = { onTypes(if (t in types) types - t else types + t) },
                            label = { Text(t.formLabel()) }
                        )
                    }
                }

                Text(
                    if (muscles.isEmpty()) "Muscle" else "Muscle (${muscles.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search muscles") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )

                if (shown.isEmpty()) {
                    Text(
                        "No matching muscle",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(32.dp)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(96.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(shown, key = { it.name }) { m ->
                            MuscleTile(m, m in muscles) {
                                onMuscles(if (m in muscles) muscles - m else muscles + m)
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                ) {
                    Text(if (resultCount == 1) "Show 1 exercise" else "Show $resultCount exercises")
                }
            }
        }
    }
}

@Composable
private fun MuscleTile(muscle: Muscle, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .border(
                width = 2.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        MuscleBadge(muscle, null, size = 64.dp)
        Text(
            muscle.displayName(),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
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
                    else Color.Transparent
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            MuscleBadge(exercise.primaryMuscle, exercise.photoPath, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = exercise.subtitle(),
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
