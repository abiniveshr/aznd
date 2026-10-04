package com.ashrdev.aznd.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.data.workout.TrainingRepository
import com.ashrdev.aznd.data.workout.WorkoutSettings
import com.ashrdev.aznd.data.workout.WorkoutSettingsStore
import com.ashrdev.aznd.data.workout.toMeta
import com.ashrdev.aznd.domain.BuilderState
import com.ashrdev.aznd.domain.E1rmCalculator
import com.ashrdev.aznd.domain.LogField
import com.ashrdev.aznd.domain.SessionHistory
import com.ashrdev.aznd.domain.SetRow
import com.ashrdev.aznd.domain.Suggestion
import com.ashrdev.aznd.domain.buildSuggestions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The running session. [bodyweightKg] is the snapshot taken when it started. */
data class LoggerSession(val id: Long, val workoutId: Long, val bodyweightKg: Double)

sealed interface LoggerPhase {
    data object Loading : LoggerPhase
    /** The workout's template has no usable rows, so no session was started. */
    data object EmptyWorkout : LoggerPhase
    data object Active : LoggerPhase
    /** Finished or discarded: the screen should leave. */
    data object Closed : LoggerPhase
}

class LoggerActions(
    val onPickCardExercise: (rowId: Long, exercise: Exercise) -> Unit,
    val onPickRowExercise: (rowId: Long, exercise: Exercise) -> Unit,
    val onDuplicate: (Long) -> Unit,
    val onDelete: (Long) -> Unit,
    val onDrop: (Long) -> Unit,
    val onSuperset: (Long) -> Unit,
    val onField: (rowId: Long, field: LogField, text: String) -> Unit,
    val onSuggest: (rowId: Long, suggestion: Suggestion) -> Unit,
    val onAddCard: () -> Unit
)

/**
 * Active workout logger. Structure and values live in [BuilderState]; this class loads
 * or starts the session, keeps the database in sync, and computes set suggestions.
 *
 * Persistence (one writer at a time, Mutex): structural changes (Duplicate, Delete, Drop,
 * Superset, Add exercise, exercise change, or edits on rows without a permanent ID) re-sync
 * all rows with syncSessionRows and swap temporary IDs for real ones. Plain value edits in an
 * already-saved row call updateSetValues. Rows without an exercise are not persisted.
 */
class LoggerViewModel(
    private val repository: TrainingRepository,
    settingsStore: WorkoutSettingsStore
) : ViewModel() {

    private val _phase = MutableStateFlow<LoggerPhase>(LoggerPhase.Loading)
    val phase: StateFlow<LoggerPhase> = _phase.asStateFlow()

    private val _session = MutableStateFlow<LoggerSession?>(null)
    val session: StateFlow<LoggerSession?> = _session.asStateFlow()

    private val _state = MutableStateFlow(BuilderState())
    val state: StateFlow<BuilderState> = _state.asStateFlow()

    private val _previous = MutableStateFlow<List<SetRow>>(emptyList())
    /** Rows of the previous COMPLETED session of the same workout (for the "Previous:" lines). */
    val previousRows: StateFlow<List<SetRow>> = _previous.asStateFlow()

    val exercises: StateFlow<List<Exercise>> =
        repository.observeExercises().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<WorkoutSettings> = settingsStore.settings

    private val histories = MutableStateFlow<Map<Long, List<SessionHistory>>>(emptyMap())
    private val requestedHistory = HashSet<Long>()

    /** Row id -> tappable weight hint. Empty when "Target reps" is off. Never auto-filled. */
    val suggestions: StateFlow<Map<Long, Suggestion>> =
        combine(_state, histories, exercises, settings, _session) { st, hist, ex, set, sess ->
            if (sess == null) {
                emptyMap()
            } else {
                val meta = ex.associate { it.id to it.toMeta() }
                buildSuggestions(
                    rows = st.rows,
                    meta = { meta[it] },
                    history = { hist[it].orEmpty() },
                    workoutId = sess.workoutId,
                    bodyweightKg = sess.bodyweightKg,
                    calculator = E1rmCalculator(rpeTracking = set.trackRpe),
                    targetsOn = set.targetReps
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private var begun = false
    /** Set by Finish / Discard so a double tap cannot finish or delete the session twice. */
    private var closing = false
    private val writer = Mutex()
    private var structuralDirty = false
    private val dirtyValues = LinkedHashSet<Long>()

    /**
     * Resume the unfinished session of this workout if there is one, otherwise start a new one
     * from the template. (An unfinished session of ANOTHER workout is left as it is.)
     */
    fun begin(workoutId: Long) {
        if (begun) return
        begun = true
        viewModelScope.launch {
            val active = repository.observeActiveSession().first()
            val id = if (active != null && active.workoutId == workoutId) active.id else repository.startSession(workoutId)
            if (id == null) {
                _phase.value = LoggerPhase.EmptyWorkout
                return@launch
            }
            val s = repository.session(id)
            if (s == null) {
                _phase.value = LoggerPhase.EmptyWorkout
                return@launch
            }
            _previous.value = repository.previousSessionRows(workoutId)
            _state.value = BuilderState(repository.observeSessionRows(id).first())
            _session.value = LoggerSession(id, workoutId, s.bodyweightKgSnapshot)
            ensureHistory()
            _phase.value = LoggerPhase.Active
        }
    }

    /** Finish button: write everything, then set finishedAt. */
    fun finish() {
        val s = _session.value ?: return
        if (closing) return
        closing = true
        viewModelScope.launch {
            flush()
            repository.finishSession(s.id)
            _phase.value = LoggerPhase.Closed
        }
    }

    /** Delete the whole session (the screen asks for confirmation first). */
    fun discard() {
        val s = _session.value ?: return
        if (closing) return
        closing = true
        viewModelScope.launch {
            writer.withLock {
                structuralDirty = false
                dirtyValues.clear()
                repository.discardSession(s.id)
            }
            _phase.value = LoggerPhase.Closed
        }
    }

    private fun exerciseOf(rowId: Long): Exercise? {
        val ex = _state.value.rows.firstOrNull { it.id == rowId }?.exerciseId ?: return null
        return exercises.value.firstOrNull { it.id == ex }
    }

    private fun structural(op: (BuilderState) -> BuilderState) {
        _state.update(op)
        structuralDirty = true
        ensureHistory()
        viewModelScope.launch { flush() }
    }

    private fun valueEdit(rowId: Long, op: (BuilderState) -> BuilderState) {
        _state.update(op)
        if (rowId < 0) structuralDirty = true else dirtyValues += rowId
        viewModelScope.launch { flush() }
    }

    private fun ensureHistory() {
        val sess = _session.value ?: return
        _state.value.rows.mapNotNull { it.exerciseId }.toSet().filter { requestedHistory.add(it) }.forEach { ex ->
            viewModelScope.launch {
                val h = repository.suggestionHistory(ex, sess.workoutId)
                histories.update { it + (ex to h) }
            }
        }
    }

    private suspend fun flush() {
        val sess = _session.value ?: return
        writer.withLock {
            while (structuralDirty || dirtyValues.isNotEmpty()) {
                if (structuralDirty) {
                    structuralDirty = false
                    dirtyValues.clear()
                    val idMap = repository.syncSessionRows(sess.id, _state.value.rows)
                    _state.update { it.remapIds(idMap) }
                } else {
                    val ids = dirtyValues.toList()
                    dirtyValues.clear()
                    for (id in ids) {
                        val r = _state.value.rows.firstOrNull { it.id == id } ?: continue
                        repository.updateSetValues(id, r.weightKg, r.reps, r.durationSec, r.rpe)
                    }
                }
            }
        }
    }

    val actions = LoggerActions(
        onPickCardExercise = { row, ex ->
            val old = exerciseOf(row)
            structural { it.pickCardExercise(row, ex.id, clearValues = old != null && old.type != ex.type) }
        },
        onPickRowExercise = { row, ex ->
            val old = exerciseOf(row)
            structural { it.pickRowExercise(row, ex.id, clearValues = old != null && old.type != ex.type) }
        },
        onDuplicate = { row -> structural { it.duplicate(row) } },
        onDelete = { row -> structural { it.delete(row) } },
        onDrop = { row -> structural { it.addDrop(row) } },
        onSuperset = { row -> structural { it.addSuperset(row) } },
        onField = { row, field, text -> valueEdit(row) { it.setField(row, field, text) } },
        onSuggest = { row, s ->
            val ex = exerciseOf(row)
            if (ex != null) valueEdit(row) { it.applySuggestion(row, ex.type, s.fieldKg) }
        },
        onAddCard = { structural { it.addCard() } }
    )

    companion object {
        fun factory(repository: TrainingRepository, settingsStore: WorkoutSettingsStore): ViewModelProvider.Factory =
            viewModelFactory { initializer { LoggerViewModel(repository, settingsStore) } }
    }
}
