package com.ashrdev.aznd.ui.common.share

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.ui.calendar.CalendarZoom
import com.ashrdev.aznd.ui.calendar.MonthKey
import com.ashrdev.aznd.ui.calendar.currentMonth
import com.ashrdev.aznd.ui.calendar.dayKeyOf
import com.ashrdev.aznd.ui.calendar.formatDayLong
import com.ashrdev.aznd.ui.calendar.inZoom
import com.ashrdev.aznd.ui.calendar.monthKeyOf
import com.ashrdev.aznd.ui.calendar.quadrantTitles
import com.ashrdev.aznd.ui.calendar.todayDayKey
import com.ashrdev.aznd.ui.common.rememberImagePicker
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField
import com.ashrdev.aznd.ui.workout.exercise.ExerciseSearchScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToLong
import com.ashrdev.aznd.ui.theme.Space

/*
 * The pickers behind a card's data choice (see FlexDataChoice in FlexConfigure.kt). Each one is plain content that
 * takes the card's FlexBinding and hands back a changed copy; none keeps the choice itself. The lists are bounded in
 * height (they sit inside a scrolling column), and lazy, so a long history costs nothing until it is scrolled.
 */

/** How tall a picker's list is (it scrolls inside this). */
private val LIST_HEIGHT = 260.dp

@Composable
private fun PickerLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PickerHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One row of a pick list: a title, a dimmer line under it, the current pick marked. */
@Composable
private fun PickRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(Space.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (selected) Icon(Icons.Default.Check, contentDescription = "Picked", modifier = Modifier.size(20.dp))
    }
}

/** A small round marker: the pick's place in the order (1, 2, ...), or an empty ring while it is not picked. */
@Composable
private fun OrderBadge(order: Int?) {
    val ring = MaterialTheme.colorScheme.outline
    val fill = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .then(if (order != null) Modifier.background(fill) else Modifier.border(1.dp, ring, CircleShape)),
        contentAlignment = Alignment.Center
    ) {
        if (order != null) {
            Text(order.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

private fun volumeText(kg: Double): String =
    if (kg <= 0.0) "" else String.format(Locale.getDefault(), "%,d kg", kg.roundToLong())

// ---------------------------------------------------------------------------------------------
// Title and text
// ---------------------------------------------------------------------------------------------

/** A Title card: its text (empty = the default title) and, when the card shows its date, the date (today unless picked). */
@Composable
internal fun FlexTitlePicker(block: FlexBlock, binding: FlexBinding, onChange: (FlexBinding) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        OutlinedTextField(
            value = binding.text ?: "",
            onValueChange = { t -> onChange(binding.copy(text = t.take(300))) },
            label = { Text("Title (empty = default)") },
            modifier = Modifier.fillMaxWidth()
        )
        if (block.flag) {
            val shown = binding.dateMs ?: System.currentTimeMillis()
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = shown }
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            val picked = Calendar.getInstance().apply { clear(); set(year, month, day, 12, 0, 0) }
                            onChange(binding.copy(dateMs = picked.timeInMillis))
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(Space.sm))
                    Text(formatDayLong(dayKeyOf(shown)))
                }
                if (binding.dateMs != null) {
                    TextButton(onClick = { onChange(binding.copy(dateMs = null)) }) { Text("Today") }
                }
            }
        } else {
            PickerHint("This card does not show a date. Turn on Show date in the card's settings to add one.")
        }
    }
}

/** A Text card: its text. */
@Composable
internal fun FlexTextPicker(block: FlexBlock, binding: FlexBinding, onChange: (FlexBinding) -> Unit) {
    OutlinedTextField(
        value = binding.text ?: block.text,
        onValueChange = { t -> onChange(binding.copy(text = t.take(300))) },
        label = { Text("Text") },
        modifier = Modifier.fillMaxWidth()
    )
}

// ---------------------------------------------------------------------------------------------
// Workout sessions
// ---------------------------------------------------------------------------------------------

private enum class SessionView(val label: String) { LIST("List"), CALENDAR("Calendar") }

/**
 * Pick one workout: a list (newest first, searchable by name or date) or a month calendar with the days that have a
 * workout highlighted. [selectedId] null marks nothing.
 */
@Composable
internal fun FlexSessionPicker(
    sessions: List<FlexSessionInfo>,
    selectedId: Long?,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var view by remember { mutableStateOf(SessionView.LIST) }
    // Which workout to show: every session, the freestyle ones (no routine), or one routine's. Shared by both views.
    val filters = remember(sessions) { sessionFilters(sessions) }
    var filter by remember { mutableStateOf<SessionFilter>(SessionFilter.All) }
    val shown = remember(sessions, filter) { sessions.filter { filter.matches(it) } }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        PickerLabel("Workout")
        if (sessions.isEmpty()) {
            PickerHint("There are no workouts yet.")
            return@Column
        }
        FlexSegmented(SessionView.entries.toList(), view, { it.label }) { view = it }
        if (filters.size > 2) {
            DropdownField("Show", filters, filter, { it.label }) { filter = it }
        }
        when (view) {
            SessionView.LIST -> SessionList(shown, selectedId, onPick)
            SessionView.CALENDAR -> SessionCalendar(shown, selectedId, onPick)
        }
    }
}

/** What the workout picker narrows the sessions to. A freestyle session is one started without a routine. */
private sealed class SessionFilter(val label: String) {
    abstract fun matches(s: FlexSessionInfo): Boolean
    object All : SessionFilter("All workouts") { override fun matches(s: FlexSessionInfo) = true }
    object Freestyle : SessionFilter(FREESTYLE) { override fun matches(s: FlexSessionInfo) = s.routineId == null }
    class Routine(val id: Long, name: String) : SessionFilter(name) {
        override fun matches(s: FlexSessionInfo) = s.routineId == id
        override fun equals(other: Any?) = other is Routine && other.id == id
        override fun hashCode() = id.hashCode()
    }
}

private const val FREESTYLE = "Freestyle"

/** All, Freestyle (when there are such sessions), then each routine by the name of its newest session, A to Z. */
private fun sessionFilters(sessions: List<FlexSessionInfo>): List<SessionFilter> {
    val routines = sessions.filter { it.routineId != null }
        .groupBy { it.routineId!! }
        .map { (id, list) -> SessionFilter.Routine(id, list.maxByOrNull { it.startedAt }?.name ?: "Workout") }
        .sortedBy { it.label.lowercase() }
    val freestyle = if (sessions.any { it.routineId == null }) listOf(SessionFilter.Freestyle) else emptyList()
    return listOf(SessionFilter.All) + freestyle + routines
}

@Composable
private fun SessionList(sessions: List<FlexSessionInfo>, selectedId: Long?, onPick: (Long) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(sessions, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) sessions
        else sessions.filter {
            it.name.lowercase().contains(q) || formatDayLong(dayKeyOf(it.startedAt)).lowercase().contains(q) ||
                // "freestyle" finds the sessions started without a routine, whatever they were named.
                (it.routineId == null && FREESTYLE.lowercase().contains(q))
        }
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Search by name or date") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    if (shown.isEmpty()) {
        PickerHint("No workout matches.")
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth().height(LIST_HEIGHT)) {
            items(shown, key = { it.id }) { s ->
                PickRow(
                    title = s.name,
                    subtitle = listOf(formatDayLong(dayKeyOf(s.startedAt)), volumeText(s.volumeKg)).filter { it.isNotEmpty() }.joinToString(" · "),
                    selected = s.id == selectedId,
                    onClick = { onPick(s.id) }
                )
            }
        }
    }
}

private val WEEKDAY_LETTERS = listOf("S", "M", "T", "W", "T", "F", "S")

@Composable
private fun SessionCalendar(sessions: List<FlexSessionInfo>, selectedId: Long?, onPick: (Long) -> Unit) {
    val byDay = remember(sessions) { sessions.groupBy { dayKeyOf(it.startedAt) } }
    val today = remember { currentMonth() }
    val firstMonth = remember(sessions) { sessions.minOfOrNull { monthKeyOf(dayKeyOf(it.startedAt)) } ?: today }
    val lastMonth = remember(sessions) {
        val newest = sessions.maxOfOrNull { monthKeyOf(dayKeyOf(it.startedAt)) } ?: today
        if (newest > today) newest else today
    }
    var month by remember {
        val on = sessions.firstOrNull { it.id == selectedId } ?: sessions.firstOrNull()
        mutableStateOf(on?.let { monthKeyOf(dayKeyOf(it.startedAt)) } ?: today)
    }
    var openDay by remember { mutableStateOf<String?>(null) }
    val firstDow = remember { Calendar.getInstance().firstDayOfWeek }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { month = month.previous(); openDay = null }, enabled = month > firstMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            month.title(),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { month = month.next(); openDay = null }, enabled = month < lastMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        for (c in 0 until 7) {
            Text(
                WEEKDAY_LETTERS[(firstDow - 1 + c) % 7],
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
    val leading = (month.firstWeekday() - firstDow + 7) % 7
    val days = month.daysInMonth()
    val rows = (leading + days + 6) / 7
    for (r in 0 until rows) {
        Row(modifier = Modifier.fillMaxWidth()) {
            for (c in 0 until 7) {
                val day = r * 7 + c - leading + 1
                if (day !in 1..days) {
                    Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                } else {
                    val key = month.dayKey(day)
                    val list = byDay[key]
                    val picked = list?.any { it.id == selectedId } == true
                    val scheme = MaterialTheme.colorScheme
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    picked -> scheme.primary
                                    list != null -> scheme.primaryContainer
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                }
                            )
                            .clickable(enabled = list != null) {
                                if (list != null && list.size == 1) {
                                    openDay = null
                                    onPick(list[0].id)
                                } else {
                                    openDay = key
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            day.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                picked -> scheme.onPrimary
                                list != null -> scheme.onPrimaryContainer
                                else -> scheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
    val open = openDay?.let { byDay[it] }
    if (open != null) {
        PickerLabel(formatDayLong(openDay ?: ""))
        open.forEach { s ->
            PickRow(
                title = s.name,
                subtitle = volumeText(s.volumeKg).ifEmpty { null },
                selected = s.id == selectedId,
                onClick = { onPick(s.id) }
            )
        }
    } else if (byDay.keys.none { it.startsWith(String.format(Locale.US, "%04d-%02d", month.year, month.month)) }) {
        PickerHint("No workouts this month.")
    }
}

/** The tick list of the exercises of [session]: [names] null = all of them; the last tick cannot be removed. */
@Composable
internal fun FlexSessionExercisesPicker(session: FlexSessionInfo?, names: List<String>?, onChange: (List<String>?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PickerLabel("Exercises to show")
        val all = session?.exerciseNames ?: emptyList()
        if (all.isEmpty()) {
            PickerHint("This workout has no exercises.")
            return@Column
        }
        all.forEach { name ->
            val on = names == null || name in names
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable {
                        val current = names ?: all
                        val next = if (on) current - name else all.filter { it in current || it == name }
                        if (next.isNotEmpty()) onChange(if (next.size == all.size) null else next)
                    }
                    .padding(horizontal = Space.xs, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(Space.sm))
                Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Lifts
// ---------------------------------------------------------------------------------------------

private enum class AutoOrPick(val label: String) { AUTO("Auto"), PICK("Pick") }

/**
 * Which lifts a Workout stats card shows: Auto (its top few) or the exercises picked one by one with the exercise
 * browser, listing only exercises with data. [single] (Hero look) allows one exercise: picking another replaces it.
 * [names] null = Auto; an empty pick is Auto too (nothing is stored until one exercise is picked).
 */
@Composable
internal fun FlexLiftsPicker(
    exercises: List<Exercise>,
    names: List<String>?,
    single: Boolean,
    autoLimit: Int,
    onChange: (List<String>?) -> Unit
) {
    var mode by remember { mutableStateOf(if (names != null) AutoOrPick.PICK else AutoOrPick.AUTO) }
    var browsing by remember { mutableStateOf(false) }
    val picked = names ?: emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        PickerLabel(if (single) "Exercise" else "Exercises")
        FlexSegmented(AutoOrPick.entries.toList(), mode, { it.label }) { m ->
            mode = m
            if (m == AutoOrPick.AUTO) onChange(null)
        }
        if (mode == AutoOrPick.AUTO) {
            PickerHint(if (single) "Shows your top lift." else "Shows your top $autoLimit lifts.")
        } else {
            if (picked.isEmpty()) PickerHint("Nothing picked yet: the card uses Auto until you add an exercise.")
            picked.forEach { name ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = Space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onChange((picked - name).ifEmpty { null }) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove $name")
                    }
                }
            }
            OutlinedButton(onClick = { browsing = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(Space.sm))
                Text(if (single && picked.isNotEmpty()) "Change exercise" else "Add exercise")
            }
            if (exercises.isEmpty()) PickerHint("No exercise has logged sets yet.")
        }
    }
    if (browsing) {
        ExerciseSearchScreen(
            selected = null,
            exercises = if (single) exercises else exercises.filter { it.name !in picked },
            onDismiss = { browsing = false },
            onPick = { e ->
                browsing = false
                onChange(if (single) listOf(e.name) else picked + e.name)
            },
            onAddCustom = null,
            title = "Add exercise"
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Routine
// ---------------------------------------------------------------------------------------------

/** Pick one routine from a searchable list (most recently used first). */
@Composable
internal fun FlexRoutinePicker(routines: List<FlexRoutineInfo>, selectedId: Long?, onPick: (Long) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(routines, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) routines else routines.filter { it.name.lowercase().contains(q) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        PickerLabel("Routine")
        if (routines.isEmpty()) {
            PickerHint("There are no routines yet.")
            return@Column
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search routines") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (shown.isEmpty()) {
            PickerHint("No routine matches.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().height(LIST_HEIGHT)) {
                items(shown, key = { it.id }) { r ->
                    PickRow(
                        title = r.name,
                        subtitle = if (r.exerciseCount == 1) "1 exercise" else "${r.exerciseCount} exercises",
                        selected = r.id == selectedId,
                        onClick = { onPick(r.id) }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Streaks
// ---------------------------------------------------------------------------------------------

/**
 * Which streaks a Streaks card shows: Auto (the top few by current run) or a multi-select where the order of the
 * ticks is the order on the card. [ids] null = Auto; an empty pick is Auto too.
 */
@Composable
internal fun FlexStreaksPicker(streaks: List<FlexStreakInfo>, ids: List<Long>?, autoLimit: Int, onChange: (List<Long>?) -> Unit) {
    var mode by remember { mutableStateOf(if (ids != null) AutoOrPick.PICK else AutoOrPick.AUTO) }
    val picked = ids ?: emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        PickerLabel("Streaks")
        FlexSegmented(AutoOrPick.entries.toList(), mode, { it.label }) { m ->
            mode = m
            if (m == AutoOrPick.AUTO) onChange(null)
        }
        if (mode == AutoOrPick.AUTO) {
            PickerHint("Shows the $autoLimit with the longest current run.")
        } else if (streaks.isEmpty()) {
            PickerHint("There are no streaks yet.")
        } else {
            if (picked.isEmpty()) PickerHint("Nothing picked yet: the card uses Auto until you tick a streak.")
            LazyColumn(modifier = Modifier.fillMaxWidth().height(LIST_HEIGHT)) {
                items(streaks, key = { it.id }) { s ->
                    val at = picked.indexOf(s.id)
                    PickRow(
                        title = s.name,
                        subtitle = "Current ${s.current} · longest ${s.longest}",
                        selected = false,
                        leading = { OrderBadge(if (at >= 0) at + 1 else null) },
                        onClick = { onChange((if (at >= 0) picked - s.id else picked + s.id).ifEmpty { null }) }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Graph
// ---------------------------------------------------------------------------------------------

private const val DAY_MS = 24L * 60 * 60 * 1000

private fun hasPointsIn(series: FlexGraphSeries, range: FlexGraphRange): Boolean {
    val days = range.days ?: return series.points.isNotEmpty()
    val from = System.currentTimeMillis() - days * DAY_MS
    return series.points.any { it.timeMs >= from }
}

private fun graphName(s: FlexGraphSeries): String {
    val named = if (s.unit.isNotBlank()) "${s.label} (${s.unit})" else s.label
    return if (s.points.isEmpty()) "$named · no data" else named
}

/** A Graph card's variable, what to compare it with and the range (the card panel's own choices). */
@Composable
internal fun FlexGraphPicker(
    block: FlexBlock,
    binding: FlexBinding,
    graphs: List<FlexGraphSeries>,
    onChange: (FlexBinding) -> Unit,
    /** Only the range is offered: the variable is fixed by the screen the poster was shared from. */
    rangeOnly: Boolean = false
) {
    val cfg = binding.graph ?: block.graph
    val first = cfg.variables.getOrNull(0)
    val second = cfg.variables.getOrNull(1)
    val main = graphs.firstOrNull { it.id == first }
    val other = graphs.firstOrNull { it.id == second }
    fun edit(f: (FlexGraphConfig) -> FlexGraphConfig) = onChange(binding.copy(graph = f(cfg)))

    if (rangeOnly) {
        DropdownField("Range", FlexGraphRange.entries, cfg.range, { it.label }) { r -> edit { it.copy(range = r) } }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        if (graphs.isEmpty()) PickerHint("There are no graphs to show yet.")
        FlexPickField(
            label = "Variable",
            valueText = main?.label ?: if (first != null) "No longer available" else "Pick a variable",
            options = graphs,
            optionLabel = { graphName(it) },
            isSelected = { it.id == first },
            placeholder = main == null,
            group = { it.group }
        ) { picked ->
            edit { g -> g.copy(variables = listOfNotNull(picked.id, g.variables.getOrNull(1)?.takeIf { it != picked.id })) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Box(modifier = Modifier.weight(1f)) {
                FlexPickField(
                    label = "Compare with",
                    valueText = other?.label ?: if (second != null) "No longer available" else "Nothing",
                    options = listOf<FlexGraphSeries?>(null) + graphs.filter { it.id != first },
                    optionLabel = { if (it == null) "Nothing" else graphName(it) },
                    isSelected = { it?.id == second },
                    group = { it?.group ?: "" },
                    enabled = first != null
                ) { picked ->
                    edit { g ->
                        val main0 = g.variables.firstOrNull()
                        if (main0 == null) g else g.copy(variables = listOfNotNull(main0, picked?.id?.takeIf { it != main0 }))
                    }
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                DropdownField("Range", FlexGraphRange.entries, cfg.range, { it.label }) { r -> edit { it.copy(range = r) } }
            }
        }
        listOfNotNull(main, other).filter { !hasPointsIn(it, cfg.range) }.forEach {
            PickerHint(
                if (cfg.range.days == null) "No ${it.label} data yet."
                else "No ${it.label} data in the last ${cfg.range.label.lowercase().removePrefix("1 ")}."
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Photo
// ---------------------------------------------------------------------------------------------

private val thumbCache = LruCache<String, Bitmap>(48)

/** A small picture, loaded off the main thread and remembered. */
@Composable
private fun PhotoThumb(uri: String, label: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(thumbCache.get(uri), uri) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { FlexBitmaps.load(context, uri, 240) }?.also { thumbCache.put(uri, it) }
        }
    }
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private val PHOTO_GROUPS = listOf("Workouts", "Streaks", "Body")

/**
 * A Photo card's picture: the app's photos (grouped under their headings) or one from the phone's gallery. A picture
 * the person took from the gallery that is not among the app's photos is shown first, as the current pick.
 */
@Composable
internal fun FlexPhotoPicker(photos: List<FlexPhotoInfo>, uri: String?, onPick: (String) -> Unit) {
    val pick by rememberUpdatedState(onPick)
    val picker = rememberImagePicker(aspect = null, outputWidth = 1600, outputHeight = 1600) { picked -> pick(picked.toString()) }
    val groups = remember(photos) {
        val by = photos.groupBy { it.group }
        (PHOTO_GROUPS.filter { it in by } + by.keys.filter { it !in PHOTO_GROUPS }).map { it to by.getValue(it) }
    }
    val fromGallery = uri != null && photos.none { it.uri == uri }
    val ring = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        PickerLabel("Photo")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { picker.launch() }) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(Space.sm))
                Text("Add image")
            }
            if (fromGallery && uri != null) {
                PhotoThumb(
                    uri, "Photo from your gallery",
                    Modifier.size(48.dp).clip(MaterialTheme.shapes.small).border(2.dp, ring, MaterialTheme.shapes.small)
                )
            }
        }
        if (photos.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
                modifier = Modifier.fillMaxWidth().height(300.dp)
            ) {
                groups.forEach { (group, list) ->
                    item(key = "h_$group", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            group,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = Space.sm)
                        )
                    }
                    items(list, key = { it.uri }) { p ->
                        val on = p.uri == uri
                        val shape = MaterialTheme.shapes.small
                        PhotoThumb(
                            p.uri, p.label,
                            Modifier
                                .aspectRatio(1f)
                                .clip(shape)
                                .then(if (on) Modifier.border(3.dp, ring, shape) else Modifier)
                                .clickable { onPick(p.uri) }
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Calendar
// ---------------------------------------------------------------------------------------------

private fun periodName(zoom: CalendarZoom): String = when (zoom) {
    CalendarZoom.MONTH -> "Month"
    CalendarZoom.QUADRANT -> "Quadrant"
    CalendarZoom.YEAR -> "Year"
}

/** A Calendar card's days (source) and period (month, quadrant or year, and which). */
@Composable
internal fun FlexCalendarPicker(block: FlexBlock, binding: FlexBinding, sources: List<FlexCalendarSource>, onChange: (FlexBinding) -> Unit) {
    val cal = binding.cal ?: block.cal
    val anchor = flexCalendarAnchor("", todayDayKey())
    val view = cal.view(anchor)
    val source = sources.sourceFor(cal.source)
    val (first, last) = flexCalendarRange(source, anchor)
    fun edit(f: (FlexCalendarSpec) -> FlexCalendarSpec) = onChange(binding.copy(cal = f(cal)))

    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        if (sources.isEmpty()) PickerHint("There are no days to show yet.")
        if (sources.size > 1) {
            FlexPickField(
                label = "Days shown",
                valueText = source?.label ?: "Pick a source",
                options = sources,
                optionLabel = { it.label },
                isSelected = { it.key == source?.key },
                placeholder = source == null
            ) { s -> edit { it.copy(source = s.key) } }
        } else if (source != null) {
            PickerHint("Days shown: ${source.label}")
        }
        PickerLabel("Shows a")
        FlexSegmented(listOf(CalendarZoom.MONTH, CalendarZoom.QUADRANT, CalendarZoom.YEAR), view.zoom, { periodName(it) }) { zoom ->
            edit { c ->
                if (c.year <= 0) c.copy(zoom = zoom) else {
                    val moved = c.view(anchor).inZoom(zoom)
                    c.copy(zoom = zoom, year = moved.year, index = moved.index)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Box(modifier = Modifier.weight(1f)) {
                DropdownField("Year", flexCalendarYears(first, last, view.year), view.year, { it.toString() }) { y ->
                    edit { it.copy(year = y, index = view.index) }
                }
            }
            when (view.zoom) {
                CalendarZoom.MONTH -> Box(modifier = Modifier.weight(1f)) {
                    DropdownField("Month", (1..12).toList(), view.index, { MonthKey(view.year, it).name() }) { m ->
                        edit { it.copy(year = view.year, index = m) }
                    }
                }
                CalendarZoom.QUADRANT -> Box(modifier = Modifier.weight(1f)) {
                    DropdownField("Quadrant", (0..2).toList(), view.index, { quadrantTitles(view.year)[it] }) { q ->
                        edit { it.copy(year = view.year, index = q) }
                    }
                }
                CalendarZoom.YEAR -> Unit
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Day note
// ---------------------------------------------------------------------------------------------

/** A Day note card: pick a streak, the card shows its latest note. Streaks with no note yet are listed but cannot be picked. */
@Composable
internal fun FlexDayNotePicker(streaks: List<FlexStreakInfo>, selectedId: Long?, onPick: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        PickerLabel("Streak")
        if (streaks.isEmpty()) {
            PickerHint("There are no streaks yet.")
            return@Column
        }
        val ordered = remember(streaks) { streaks.sortedByDescending { it.latestNoteDay ?: "" } }
        LazyColumn(modifier = Modifier.fillMaxWidth().height(LIST_HEIGHT)) {
            items(ordered, key = { it.id }) { s ->
                val note = s.latestNote?.trim().orEmpty()
                PickRow(
                    title = s.name,
                    subtitle = if (note.isEmpty()) "No notes yet" else listOfNotNull(s.latestNoteDay, "\"$note\"").joinToString(" · "),
                    selected = s.id == selectedId,
                    enabled = note.isNotEmpty(),
                    onClick = { onPick(s.id) }
                )
            }
        }
    }
}
