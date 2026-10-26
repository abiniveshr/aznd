package com.ashrdev.aznd.ui.routines

import com.ashrdev.aznd.data.routines.routineType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.routines.ReminderEntity
import com.ashrdev.aznd.data.routines.RoutineEntity
import com.ashrdev.aznd.data.streaks.DEFAULT_MILESTONES
import com.ashrdev.aznd.data.streaks.formatMilestones
import com.ashrdev.aznd.data.streaks.parseMilestones
import com.ashrdev.aznd.data.workout.WorkoutWithExercises
import com.ashrdev.aznd.domain.streaks.RoutineFrequency
import com.ashrdev.aznd.domain.streaks.RoutineSchedule
import com.ashrdev.aznd.domain.streaks.RoutineType
import com.ashrdev.aznd.domain.streaks.RoutineUnit
import com.ashrdev.aznd.domain.streaks.TrackMode
import com.ashrdev.aznd.domain.streaks.Weekdays
import com.ashrdev.aznd.domain.streaks.todayKey
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.DropdownField
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.components.AlertDialog
import com.ashrdev.aznd.ui.components.OutlinedTextField
import com.ashrdev.aznd.ui.streaks.DateButton
import com.ashrdev.aznd.ui.streaks.EditorColumn
import com.ashrdev.aznd.ui.streaks.Hint
import com.ashrdev.aznd.ui.streaks.ImagesPage
import com.ashrdev.aznd.ui.streaks.MilestonesEditor
import com.ashrdev.aznd.ui.streaks.NotificationsEditor
import com.ashrdev.aznd.ui.streaks.ReminderDraft
import com.ashrdev.aznd.ui.streaks.SectionTitle
import com.ashrdev.aznd.ui.streaks.StreakPreviewHeader
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.ui.streaks.WeekdayPicker
import kotlinx.coroutines.launch

private const val PAGE_IMAGE = 0
private const val PAGE_ROUTINE = 1
private const val PAGE_GOALS = 2
private const val MAX_PER_WEEK = 7
private const val NAME_LIMIT = 40

private val RoutinePages = listOf(
    BarPage("Image", Icons.Default.Image),
    BarPage("Routine", Icons.Default.Repeat),
    BarPage("Goals", Icons.Default.Flag)
)

@Stable
class RoutineDraft {
    var original: RoutineEntity? = null
    var name by mutableStateOf("")
    var type by mutableStateOf(RoutineType.CUSTOM)
    var workoutId by mutableLongStateOf(RoutineEntity.ANY_WORKOUT)
    var frequency by mutableStateOf(RoutineFrequency.DAILY)
    var weekdays by mutableIntStateOf(Weekdays.bit(0) or Weekdays.bit(2) or Weekdays.bit(4))
    var perWeek by mutableIntStateOf(3)
    var everyText by mutableStateOf("1")
    var everyUnit by mutableStateOf(RoutineUnit.DAYS)
    var startDate by mutableStateOf(todayKey())
    var photoUri by mutableStateOf<String?>(null)
    var bannerUri by mutableStateOf<String?>(null)
    var trackAsStreak by mutableStateOf(true)
    var trackMode by mutableStateOf(TrackMode.AUTO)
    var milestoneAlerts by mutableStateOf(false)
    val milestones = mutableStateListOf<Int>().apply { addAll(DEFAULT_MILESTONES) }
    val reminders = mutableStateListOf<ReminderDraft>()

    val canSave: Boolean
        get() = name.isNotBlank() &&
            (frequency != RoutineFrequency.WEEKDAYS || weekdays != 0) &&
            (frequency != RoutineFrequency.CUSTOM || everyText.toIntOrNull() in 1..RoutineSchedule.MAX_EVERY)

    fun load(r: RoutineEntity, saved: List<ReminderEntity>) {
        original = r
        name = r.name
        type = r.routineType
        workoutId = r.workoutId
        frequency = RoutineFrequency.fromId(r.frequency)
        if (r.weekdays != 0) weekdays = r.weekdays
        perWeek = r.perWeek.coerceIn(1, MAX_PER_WEEK)
        everyText = r.everyCount.coerceIn(1, RoutineSchedule.MAX_EVERY).toString()
        everyUnit = RoutineUnit.fromId(r.everyUnit)
        startDate = r.startDate
        photoUri = r.photoUri
        bannerUri = r.bannerUri
        trackAsStreak = r.trackAsStreak
        trackMode = TrackMode.fromId(r.trackMode)
        milestoneAlerts = r.milestoneAlerts
        milestones.clear()
        milestones.addAll(parseMilestones(r.milestones))
        reminders.clear()
        reminders.addAll(saved.map { ReminderDraft.of(it) })
    }

    fun toEntity(): RoutineEntity = (original ?: RoutineEntity(name = "", startDate = startDate)).copy(
        name = name.trim(),
        type = type.id,
        workoutId = if (type == RoutineType.WORKOUT) workoutId else RoutineEntity.ANY_WORKOUT,
        frequency = frequency.id,
        weekdays = if (frequency == RoutineFrequency.WEEKDAYS) weekdays else 0,
        perWeek = perWeek,
        everyCount = everyText.toIntOrNull()?.coerceIn(1, RoutineSchedule.MAX_EVERY) ?: 1,
        everyUnit = everyUnit.id,
        startDate = startDate,
        photoUri = photoUri,
        bannerUri = bannerUri,
        milestones = formatMilestones(milestones.toList()),
        milestoneAlerts = milestoneAlerts && trackAsStreak,
        trackAsStreak = trackAsStreak,
        trackMode = trackMode.id
    )
}

private class StreakSetting(val label: String, val mode: TrackMode?)

private val StreakSettings = listOf(StreakSetting("Off: not tracked as a streak", null)) +
    TrackMode.entries.map { StreakSetting(it.label, it) }

@Composable
fun RoutineEditorScreen(
    routineId: Long?,
    viewModel: StreakViewModel,
    workouts: List<WorkoutWithExercises>,
    onDone: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val draft = remember(routineId) { RoutineDraft() }
    var loaded by remember(routineId) { mutableStateOf(routineId == null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(routineId) {
        if (routineId != null) {
            viewModel.loadRoutine(routineId)?.let { (routine, reminders) -> draft.load(routine, reminders) }
            loaded = true
        }
    }
    val pagerState = rememberPagerState(initialPage = PAGE_ROUTINE) { RoutinePages.size }
    val subPages = rememberSubPageBar(pagerState, RoutinePages)
    val scope = rememberCoroutineScope()
    val save: () -> Unit = {
        if (draft.canSave) {
            viewModel.saveRoutine(draft.toEntity(), draft.reminders.map { it.toEntity() })
            onDone()
        }
    }

    if (confirmDelete && routineId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete routine?") },
            text = { Text("\"${draft.name}\", its streak and its reminders will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteRoutine(routineId)
                    onDone()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    ScreenScaffold(
        title = if (routineId == null) "New routine" else "Edit routine",
        onBack = onDone,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = subPages,
        topBarActions = {
            if (routineId != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete routine", tint = MaterialTheme.colorScheme.error)
                }
            }
            TextButton(onClick = save, enabled = loaded && draft.canSave) { Text("Save") }
        }
    ) { padding ->
        val bottom = padding.calculateBottomPadding()
        if (loaded) HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                PAGE_IMAGE -> ImagesPage(
                    bannerUri = draft.bannerUri,
                    photoUri = draft.photoUri,
                    onBanner = { draft.bannerUri = it },
                    onLogo = { draft.photoUri = it },
                    bottomPadding = bottom
                )
                PAGE_GOALS -> RoutineGoalsPage(draft, bottom)
                else -> RoutineDetailsForm(draft, workouts, bottom, onPickImages = { scope.launch { pagerState.animateScrollToPage(PAGE_IMAGE) } })
            }
        }
    }
}

@Composable
private fun RoutineDetailsForm(draft: RoutineDraft, workouts: List<WorkoutWithExercises>, bottomPadding: Dp, onPickImages: () -> Unit) {
    EditorColumn(bottomPadding) {
        StreakPreviewHeader(bannerUri = draft.bannerUri, photoUri = draft.photoUri, onClick = onPickImages)
        OutlinedTextField(
            value = draft.name,
            onValueChange = { if (it.length <= NAME_LIMIT) draft.name = it },
            label = { Text("Routine name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        SectionTitle("Type")
        DropdownField(
            label = "",
            valueText = draft.type.label,
            options = RoutineType.entries,
            optionText = { it.label },
            onSelect = { draft.type = it }
        )
        Hint(draft.type.blurb)
        if (draft.type == RoutineType.WORKOUT) {
            DropdownField(
                label = "Workout",
                valueText = workouts.firstOrNull { it.workout.id == draft.workoutId }?.workout?.name ?: "Any workout",
                options = listOf<WorkoutWithExercises?>(null) + workouts,
                optionText = { it?.workout?.name ?: "Any workout" },
                onSelect = { draft.workoutId = it?.workout?.id ?: RoutineEntity.ANY_WORKOUT }
            )
        }
        SectionTitle("Frequency")
        DropdownField(
            label = "",
            valueText = draft.frequency.label,
            options = RoutineFrequency.entries,
            optionText = { it.label },
            onSelect = { draft.frequency = it }
        )
        when (draft.frequency) {
            RoutineFrequency.WEEKDAYS -> {
                WeekdayPicker(mask = draft.weekdays, onChange = { draft.weekdays = it }, emptyMeansAll = false)
                Hint(Weekdays.label(draft.weekdays))
            }
            RoutineFrequency.PER_WEEK -> Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { draft.perWeek = (draft.perWeek - 1).coerceAtLeast(1) }, enabled = draft.perWeek > 1) {
                    Icon(Icons.Default.Remove, contentDescription = "Fewer")
                }
                Text("${draft.perWeek}× a week", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(110.dp))
                IconButton(onClick = { draft.perWeek = (draft.perWeek + 1).coerceAtMost(MAX_PER_WEEK) }, enabled = draft.perWeek < MAX_PER_WEEK) {
                    Icon(Icons.Default.Add, contentDescription = "More")
                }
            }
            RoutineFrequency.CUSTOM -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Every", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = draft.everyText,
                        onValueChange = { v -> if (v.length <= 3 && v.all { it.isDigit() }) draft.everyText = v },
                        label = { Text("Number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    DropdownField(
                        label = "",
                        valueText = draft.everyUnit.label,
                        options = RoutineUnit.entries,
                        optionText = { it.label },
                        onSelect = { draft.everyUnit = it },
                        modifier = Modifier.weight(1.3f)
                    )
                }
                Hint(
                    if (draft.everyUnit == RoutineUnit.HOURS && (draft.everyText.toIntOrNull() ?: 0) < 24) {
                        "A day counts once you have checked in at least once; the button reads Done until the hours have passed."
                    } else {
                        "Check in once in every period; the button reads Done until the next period starts."
                    }
                )
            }
            RoutineFrequency.DAILY -> Unit
        }
        DateButton("Start date", draft.startDate, onDate = { draft.startDate = it })
    }
}

@Composable
private fun RoutineGoalsPage(draft: RoutineDraft, bottomPadding: Dp) {
    EditorColumn(bottomPadding) {
        SectionTitle("Streak settings")
        DropdownField(
            label = "",
            valueText = if (draft.trackAsStreak) draft.trackMode.label else StreakSettings.first().label,
            options = StreakSettings,
            optionText = { it.label },
            onSelect = { s ->
                draft.trackAsStreak = s.mode != null
                if (s.mode != null) draft.trackMode = s.mode
            }
        )
        Hint(
            when {
                !draft.trackAsStreak -> "The routine is not counted as a streak. Turning this off later deletes its streak history."
                draft.trackMode == TrackMode.AUTO -> if (draft.type == RoutineType.CUSTOM) {
                    "Counts days you tick the routine off; a missed scheduled day breaks the streak."
                } else {
                    "Counts from what you log in the app; a missed scheduled day breaks the streak."
                }
                else -> draft.trackMode.blurb
            }
        )
        MilestonesEditor(draft.milestones, enabled = draft.trackAsStreak)
        NotificationsEditor(
            milestoneAlerts = draft.milestoneAlerts,
            onMilestoneAlerts = { draft.milestoneAlerts = it },
            alertsAvailable = draft.trackAsStreak,
            reminders = draft.reminders,
            reminderHint = "Custom reminders fire at their time on the chosen days, and are skipped once the routine is done that day."
        )
    }
}
