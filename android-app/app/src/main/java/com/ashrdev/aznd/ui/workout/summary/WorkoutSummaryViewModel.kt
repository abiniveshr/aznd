package com.ashrdev.aznd.ui.workout.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashrdev.aznd.data.workout.EMPTY_WORKOUT_ID
import com.ashrdev.aznd.data.workout.FREESTYLE_NAME
import com.ashrdev.aznd.data.workout.LoggedSession
import com.ashrdev.aznd.data.workout.SessionWithSets
import com.ashrdev.aznd.data.workout.TrainingRepository
import com.ashrdev.aznd.data.workout.WorkoutDao
import com.ashrdev.aznd.domain.workout.ExerciseProfile
import com.ashrdev.aznd.domain.workout.LoggedSetData
import com.ashrdev.aznd.domain.workout.SessionStatsCalculator
import com.ashrdev.aznd.domain.workout.SetRow
import com.ashrdev.aznd.domain.workout.SetTree
import com.ashrdev.aznd.domain.workout.formatKcal
import com.ashrdev.aznd.domain.workout.formatWeightKg
import com.ashrdev.aznd.ui.calendar.dayKeyOf
import com.ashrdev.aznd.ui.workout.formatDuration
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SummaryState {
    data object Loading : SummaryState
    data object Missing : SummaryState
    data class Ready(val data: SummaryData) : SummaryState
}

class WorkoutSummaryViewModel(
    private val historyId: Long,
    private val repository: TrainingRepository,
    private val legacy: WorkoutDao
) : ViewModel() {

    private val _state = MutableStateFlow<SummaryState>(SummaryState.Loading)
    val state: StateFlow<SummaryState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var savedCopy = false
    private var compareWithId: Long? = null
    private val exerciseCompare = HashMap<String, Long>()
    private var busy = false

    init {
        reload()
    }

    fun setCompareWith(id: Long?) {
        if (id == compareWithId) return
        compareWithId = id
        reload()
    }

    fun setExerciseCompareWith(exerciseName: String, id: Long?) {
        if (exerciseCompare[exerciseName] == id) return
        if (id == null) exerciseCompare.remove(exerciseName) else exerciseCompare[exerciseName] = id
        reload()
    }

    fun messageShown() {
        _message.value = null
    }

    private fun reload() {
        viewModelScope.launch { _state.value = build() }
    }

    private fun ready(): SummaryData? = (_state.value as? SummaryState.Ready)?.data

    fun setPhoto(uri: String?) {
        viewModelScope.launch {
            withContext(NonCancellable) { repository.setHistoryPhoto(historyId, uri) }
            _state.update { s -> if (s is SummaryState.Ready) SummaryState.Ready(s.data.copy(photoUri = uri)) else s }
        }
    }

    fun saveAsPreset(name: String) {
        val data = ready() ?: return
        val clean = name.trim()
        if (clean.isEmpty() || busy || data.templateRows.isEmpty()) return
        busy = true
        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    val id = legacy.saveWorkoutRecord(null, clean)
                    repository.saveTemplate(id, data.templateRows)
                    savedCopy = true
                }
                _message.value = "Saved as preset \"$clean\""
                _state.value = build()
            } finally {
                busy = false
            }
        }
    }

    fun updatePreset() {
        val data = ready() ?: return
        if (busy || !data.canUpdatePreset) return
        busy = true
        viewModelScope.launch {
            try {
                withContext(NonCancellable) { repository.saveTemplate(data.workoutId, data.templateRows) }
                _message.value = "Preset updated"
                _state.value = build()
            } finally {
                busy = false
            }
        }
    }

    fun renamePreset(name: String) {
        val data = ready() ?: return
        val clean = name.trim()
        if (clean.isEmpty() || busy || !data.canRename) return
        busy = true
        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    val rows = repository.templateRows(data.workoutId)
                    legacy.saveWorkoutRecord(data.workoutId, clean)
                    repository.saveTemplate(data.workoutId, rows)
                }
                _message.value = "Preset renamed"
                _state.value = build()
            } finally {
                busy = false
            }
        }
    }

    private suspend fun build(): SummaryState {
        val current = legacy.getSession(historyId) ?: return SummaryState.Missing
        val session = current.session
        val freestyle = session.workoutId == EMPTY_WORKOUT_ID
        val sets = current.sets.sortedBy { it.orderIndex }
        val earlier = legacy.getSessionsOnce()
            .filter { it.session.id != session.id && it.session.startedAt < session.startedAt }
        val previous = if (freestyle) null else earlier.firstOrNull { it.session.workoutId == session.workoutId }
        val chosen = if (freestyle) null else compareWithId?.let { id -> earlier.firstOrNull { it.session.id == id } }
        val overrides = if (freestyle) emptyMap() else exerciseCompare.mapNotNull { (name, id) ->
            earlier.firstOrNull { it.session.id == id }?.let { name to it }
        }.toMap()
        val currentNames = sets.map { it.exerciseName }.toSet()
        val sharing = earlier.filter { s -> s.sets.any { it.exerciseName in currentNames } }
        val ordered = if (freestyle) sharing else {
            sharing.filter { it.session.workoutId == session.workoutId } + sharing.filter { it.session.workoutId != session.workoutId }
        }
        val lastTime = if (freestyle) sharing.firstOrNull() else previous

        val logged = repository.sessionOfHistory(historyId)
        val profiles = repository.statsExercises().values.associate { it.name to ExerciseProfile(it.type, it.bodyweightShare) }
        val currentBodyweight = logged?.bodyweightKgSnapshot ?: repository.currentBodyweightKg()
        val previousBodyweights = HashMap<Long, Double?>()

        val exercises = sets.groupBy { it.exerciseName }.map { (name, list) ->
            val lines = list.map { it.toLine() }
            val picked = overrides[name] ?: chosen
            val source = when {
                picked != null -> picked
                freestyle -> earlier.firstOrNull { s -> s.sets.any { it.exerciseName == name } }
                else -> previous
            }
            val before = source?.sets.orEmpty()
                .filter { it.exerciseName == name }
                .sortedBy { it.orderIndex }
                .map { it.toLine() }
            val profile = profiles[name]
            val currentContext = E1rmContext(profile, currentBodyweight)
            val previousContext = source?.let { s ->
                val snapshot = previousBodyweights.getOrPut(s.session.id) {
                    repository.sessionOfHistory(s.session.id)?.bodyweightKgSnapshot
                }
                E1rmContext(profile, snapshot ?: currentBodyweight)
            }
            ExerciseSummary(
                name = name,
                sets = lines,
                volumeKg = volumeOf(lines),
                bestE1rm = bestE1rm(lines, currentContext),
                comparison = if (source != null && before.isNotEmpty()) {
                    compareExercise(lines, before, source.session.startedAt, currentContext, previousContext)
                } else null,
                e1rmContext = currentContext,
                notInCompared = picked != null && before.isEmpty()
            )
        }

        val overallPicks = HashMap<String, ComparePick>()
        val exercisePicks = HashMap<String, Map<String, ComparePick>>()
        if (!freestyle) {
            for (s in latestPerDay(earlier.filter { it.session.workoutId == session.workoutId })) {
                val pick = overallPick(s, profiles, currentBodyweight, previousBodyweights) ?: continue
                overallPicks[dayKeyOf(s.session.startedAt)] = pick
            }
            for (name in currentNames) {
                val profile = profiles[name]
                val byDay = HashMap<String, ComparePick>()
                for (s in latestPerDay(earlier.filter { s -> s.sets.any { it.exerciseName == name } })) {
                    val context = contextOf(s, profile, currentBodyweight, previousBodyweights)
                    val lines = s.sets.filter { it.exerciseName == name }.sortedBy { it.orderIndex }.map { it.toLine() }
                    val top = topSetOf(lines, context) ?: continue
                    byDay[dayKeyOf(s.session.startedAt)] = ComparePick(
                        s.session.id,
                        s.session.startedAt,
                        s.session.workoutName,
                        topSetText(top, context)
                    )
                }
                if (byDay.isNotEmpty()) exercisePicks[name] = byDay
            }
        }

        val elapsed = ((session.finishedAt - session.startedAt) / 1000L).toInt().coerceAtLeast(0)
        val volume = volumeOf(sets.map { it.toLine() })
        val loggedRows = if (logged == null) emptyList() else SetTree.dropUnsetRows(repository.sessionRows(logged.id))
        val kcal = if (logged == null) null else kcalOf(logged, loggedRows)

        val totals = buildList {
            add(SummaryTotal("Volume", formatWeightKg(roundKg(volume))))
            add(SummaryTotal("Time", formatDuration(elapsed)))
            add(SummaryTotal("Sets completed", sets.size.toString()))
            add(SummaryTotal("Exercises", exercises.size.toString()))
            val intensity = intensityPercent(sets.map { it.toLine() })
            if (intensity != null) add(SummaryTotal("Intensity", "$intensity%"))
            if (kcal != null) add(SummaryTotal("Estimated energy", formatKcal(kcal)))
        }

        val templateRows = templateFromSession(loggedRows)
        val preset = if (freestyle) null else legacy.getWorkoutWithExercises(session.workoutId)?.workout
        val differs = preset != null && templateRows.isNotEmpty() &&
            templateShape(templateRows) != templateShape(repository.templateRows(preset.id))

        return SummaryState.Ready(
            SummaryData(
                historyId = historyId,
                workoutId = session.workoutId,
                title = preset?.name ?: if (freestyle) FREESTYLE_NAME else session.workoutName,
                startedAt = session.startedAt,
                finishedAt = session.finishedAt,
                photoUri = session.photoUri,
                totals = totals,
                exercises = exercises,
                freestyle = freestyle,
                templateRows = templateRows,
                canSaveAsPreset = templateRows.isNotEmpty() && !savedCopy,
                canUpdatePreset = differs,
                canRename = preset != null,
                suggestedName = "Workout ${legacy.workoutCount() + 1}",
                compareWithId = chosen?.session?.id,
                compareWithAt = chosen?.session?.startedAt,
                exerciseCompareAt = overrides.mapValues { it.value.session.startedAt },
                lastTimeId = lastTime?.session?.id,
                fallbackThenId = ordered.firstOrNull()?.session?.id,
                overallPicks = overallPicks,
                exercisePicks = exercisePicks
            )
        )
    }

    private fun latestPerDay(sessions: List<SessionWithSets>): List<SessionWithSets> {
        val seen = HashSet<String>()
        return sessions.sortedByDescending { it.session.startedAt }.filter { seen.add(dayKeyOf(it.session.startedAt)) }
    }

    private suspend fun contextOf(
        s: SessionWithSets,
        profile: ExerciseProfile?,
        fallbackBodyweight: Double?,
        cache: HashMap<Long, Double?>
    ): E1rmContext {
        if (profile?.type?.isBodyweight != true) return E1rmContext(profile, fallbackBodyweight)
        val id = s.session.id
        if (!cache.containsKey(id)) cache[id] = repository.sessionOfHistory(id)?.bodyweightKgSnapshot
        return E1rmContext(profile, cache[id] ?: fallbackBodyweight)
    }

    private suspend fun overallPick(
        s: SessionWithSets,
        profiles: Map<String, ExerciseProfile>,
        fallbackBodyweight: Double?,
        cache: HashMap<Long, Double?>
    ): ComparePick? {
        var best: Triple<String, Pair<SetLine, Double?>, E1rmContext>? = null
        for ((name, list) in s.sets.sortedBy { it.orderIndex }.groupBy { it.exerciseName }) {
            val context = contextOf(s, profiles[name], fallbackBodyweight, cache)
            val top = topSetOf(list.map { it.toLine() }, context) ?: continue
            val current = best
            if (current == null || (top.second ?: -1.0) > (current.second.second ?: -1.0)) best = Triple(name, top, context)
        }
        val found = best ?: return null
        return ComparePick(
            s.session.id,
            s.session.startedAt,
            s.session.workoutName,
            found.first + ": " + topSetText(found.second, found.third)
        )
    }

    private suspend fun kcalOf(logged: LoggedSession, rows: List<SetRow>): Double? {
        val finished = logged.finishedAt ?: return null
        val known = repository.statsExercises()
        val data = rows.mapNotNull { r ->
            r.exerciseId?.let { exerciseId ->
                LoggedSetData(
                    exerciseId = exerciseId,
                    sessionId = logged.id,
                    workoutId = logged.workoutId,
                    startedAt = logged.startedAt,
                    finishedAt = finished,
                    bodyweightKg = logged.bodyweightKgSnapshot,
                    kind = r.kind,
                    weightKg = r.weightKg,
                    reps = r.reps,
                    durationSec = r.durationSec,
                    rpe = r.rpe
                )
            }
        }
        return SessionStatsCalculator.all(data, known).firstOrNull()?.kcal?.takeIf { it > 0.0 }
    }

    companion object {
        fun factory(historyId: Long, repository: TrainingRepository, legacy: WorkoutDao): ViewModelProvider.Factory =
            viewModelFactory { initializer { WorkoutSummaryViewModel(historyId, repository, legacy) } }
    }
}
