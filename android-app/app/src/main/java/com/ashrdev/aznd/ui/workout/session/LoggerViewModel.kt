package com.ashrdev.aznd.ui.workout.session

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
import com.ashrdev.aznd.domain.workout.BuilderState
import com.ashrdev.aznd.domain.workout.E1rmCalculator
import com.ashrdev.aznd.domain.workout.LogField
import com.ashrdev.aznd.domain.workout.SessionHistory
import com.ashrdev.aznd.domain.workout.SetRow
import com.ashrdev.aznd.domain.workout.Suggestion
import com.ashrdev.aznd.domain.workout.buildSuggestions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Typing is written to the database this long after the last keystroke. */
private const val VALUE_FLUSH_DELAY_MS = 350L

/** How long the "Set deleted" strip offers Undo. */
private const val UNDO_WINDOW_MS = 5_000L

/** The running session. [bodyweightKg] is the snapshot taken when it started. */
data class LoggerSession(
    val id: Long,
    val workoutId: Long,
    val bodyweightKg: Double,
    val photoUri: String? = null,
    val startedAt: Long = 0L
)

/** A set that was just deleted: the rows from before the delete, so Undo can put it back. */
class DeletedSet(val before: List<SetRow>, val rowId: Long) {
    /** After a sync swapped temporary ids for real ones. */
    fun remap(idMap: Map<Long, Long>): DeletedSet = DeletedSet(
        before.map { it.copy(id = idMap[it.id] ?: it.id, parentId = it.parentId?.let { p -> idMap[p] ?: p }) },
        idMap[rowId] ?: rowId
    )
}

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
    val onFillPrevious: (rowId: Long, previous: SetRow) -> Unit = { _, _ -> },
    val onUndoDelete: () -> Unit = {},
    val onAddCard: () -> Unit,
    val onAddSet: (anyRowIdInCard: Long) -> Unit,
    val onDeleteCard: (rowIds: List<Long>) -> Unit
)

/**
 * Active workout logger. Structure and values live in [BuilderState]; this class loads
 * or starts the session, keeps the database in sync, and computes set suggestions.
 *
 * Persistence (one writer at a time, Mutex): structural changes (Duplicate, Delete, Drop,
 * Superset, Add exercise, exercise change, or edits on rows without a permanent ID) re-sync
 * all rows with syncSessionRows and swap temporary IDs for real ones. Plain value edits in an
 * already-saved row call updateSetValues, once per row shortly after the last keystroke. Rows
 * without an exercise are not persisted.
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
    val suggestions: StateFlow<Map<Long, Suggestion>> = run {
        // Suggestions only read these fields, so typed values never trigger a recompute.
        val suggestionRows = _state
            .map { st ->
                st.rows.filter { it.exerciseId != null }
                    .map { SetRow(id = it.id, exerciseId = it.exerciseId, kind = it.kind, targetMin = it.targetMin) }
            }
            .distinctUntilChanged()
        val metaById = exercises.map { list -> list.associate { it.id to it.toMeta() } }
        combine(suggestionRows, histories, metaById, settings, _session) { rows, hist, meta, set, sess ->
            if (sess == null || !set.targetReps) {
                emptyMap()
            } else {
                buildSuggestions(
                    rows = rows,
                    meta = { meta[it] },
                    history = { hist[it].orEmpty() },
                    workoutId = sess.workoutId,
                    bodyweightKg = sess.bodyweightKg,
                    calculator = E1rmCalculator(rpeTracking = set.trackRpe),
                    targetsOn = true
                )
            }
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    }

    private val _deleted = MutableStateFlow<DeletedSet?>(null)
    /** The set deleted last, for the "Set deleted · Undo" strip; null once the window passed. */
    val deletedSet: StateFlow<DeletedSet?> = _deleted.asStateFlow()
    private var undoJob: Job? = null

    private val _shareId = MutableStateFlow<Long?>(null)
    /** History row of the session finished with "Finish and share"; set before the phase turns Closed. */
    val shareId: StateFlow<Long?> = _shareId.asStateFlow()

    private var begun = false
    /** Set by Finish / Discard so a double tap cannot finish or delete the session twice. */
    private var closing = false
    /** Set under the lock once the session was finished or deleted: nothing is written for it after that. */
    private var sealed = false
    private var structuralDirty = false
    private val dirtyValues = LinkedHashSet<Long>()
    private var pendingFlush: Job? = null
    /** Outlives viewModelScope, which is cancelled before onCleared: only for the last flush. */
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Resume the unfinished session of this workout if there is one, otherwise start a new one
     * from the template. (An unfinished session of ANOTHER workout is left as it is.)
     */
    fun begin(workoutId: Long) {
        if (begun) return
        begun = true
        viewModelScope.launch {
            // Under the shared lock: a previous logger that is still writing its last values finishes first,
            // so the rows read here are never older than what the user typed.
            writer.withLock {
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
                _session.value = LoggerSession(id, workoutId, s.bodyweightKgSnapshot, s.photoUri, s.startedAt)
                ensureHistory()
                _phase.value = LoggerPhase.Active
            }
        }
    }

    /** Finish button: write everything, then set finishedAt. [share] keeps the history row id for the screen. */
    fun finish(share: Boolean = false) {
        val s = _session.value ?: return
        if (closing) return
        closing = true
        pendingFlush?.cancel()
        viewModelScope.launch {
            flush(seal = true)
            // Not cancellable: leaving right after Finish must not stop between "finished" and the history row.
            val historyId = withContext(NonCancellable) { repository.finishSession(s.id) }
            if (share) _shareId.value = historyId
            _phase.value = LoggerPhase.Closed
        }
    }

    /** Attaches (or with null removes) the session photo; saved right away so it survives closing the app. */
    fun setPhoto(uri: String?) {
        val s = _session.value ?: return
        if (closing) return
        _session.value = s.copy(photoUri = uri)
        pendingFlush?.cancel()
        viewModelScope.launch {
            flush()
            writer.withLock { repository.setSessionPhoto(s.id, uri) }
        }
    }

    /** Delete the whole session (the screen asks for confirmation first). */
    fun discard() {
        val s = _session.value ?: return
        if (closing) return
        closing = true
        pendingFlush?.cancel()
        viewModelScope.launch {
            writer.withLock {
                sealed = true
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

    /** [keepUndo]: only the delete itself, which has just set up its own undo. */
    private fun structural(keepUndo: Boolean = false, op: (BuilderState) -> BuilderState) {
        if (!keepUndo) clearUndo()
        _state.update(op)
        structuralDirty = true
        ensureHistory()
        pendingFlush?.cancel()
        viewModelScope.launch { flush() }
    }

    /** Typing: marks the row and writes it once the user pauses, so a burst is one write per row. */
    private fun valueEdit(rowId: Long, op: (BuilderState) -> BuilderState) {
        _state.update(op)
        if (rowId < 0) structuralDirty = true else dirtyValues += rowId
        pendingFlush?.cancel()
        pendingFlush = viewModelScope.launch {
            delay(VALUE_FLUSH_DELAY_MS)
            flush()
        }
    }

    private fun clearUndo() {
        undoJob?.cancel()
        _deleted.value = null
    }

    private fun deleteSet(rowId: Long) {
        val before = _state.value.rows
        val row = before.firstOrNull { it.id == rowId } ?: return
        clearUndo()
        if (row.exerciseId != null) {
            _deleted.value = DeletedSet(before, rowId)
            undoJob = viewModelScope.launch {
                delay(UNDO_WINDOW_MS)
                _deleted.value = null
            }
        }
        structural(keepUndo = true) { it.delete(rowId) }
    }

    private fun undoDelete() {
        val d = _deleted.value ?: return
        structural { it.restoreDeleted(d.before, d.rowId) }
    }

    /** Writes what was typed right now (the app is being paused); the screen calls this. */
    fun saveNow() {
        if (closing || _session.value == null) return
        pendingFlush?.cancel()
        viewModelScope.launch { flush() }
    }

    override fun onCleared() {
        pendingFlush?.cancel()
        // Undispatched: the lock is taken before this returns, so a logger opened next waits for these rows.
        if (!closing) persistScope.launch(start = CoroutineStart.UNDISPATCHED) { flush() }
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

    /** Not cancellable: once rows are taken off the dirty list they must reach the database. */
    private suspend fun flush(seal: Boolean = false) = withContext(NonCancellable) {
        val sess = _session.value ?: return@withContext
        writer.withLock {
            while (!sealed && (structuralDirty || dirtyValues.isNotEmpty())) {
                if (structuralDirty) {
                    structuralDirty = false
                    dirtyValues.clear()
                    val idMap = repository.syncSessionRows(sess.id, _state.value.rows)
                    _state.update { it.remapIds(idMap) }
                    _deleted.update { it?.remap(idMap) }
                } else {
                    val ids = dirtyValues.toList()
                    dirtyValues.clear()
                    for (id in ids) {
                        val r = _state.value.rows.firstOrNull { it.id == id } ?: continue
                        repository.updateSetValues(id, r.weightKg, r.reps, r.durationSec, r.rpe)
                    }
                }
            }
            if (seal) sealed = true
        }
    }

    /** "Add exercise": appends a card with [exercise] already chosen (picked before the card exists). */
    fun addCardWithExercise(exercise: Exercise) {
        structural { current ->
            val withCard = current.addCard()
            val firstRowId = withCard.cards.lastOrNull()?.rows?.firstOrNull()?.id
            if (firstRowId == null) withCard else withCard.pickCardExercise(firstRowId, exercise.id)
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
        onDelete = ::deleteSet,
        onDrop = { row -> structural { it.addDrop(row) } },
        onSuperset = { row -> structural { it.addSuperset(row) } },
        onField = { row, field, text -> valueEdit(row) { it.setField(row, field, text) } },
        onSuggest = { row, s ->
            val ex = exerciseOf(row)
            if (ex != null) valueEdit(row) { it.applySuggestion(row, ex.type, s.fieldKg) }
        },
        onFillPrevious = { row, prev -> valueEdit(row) { it.fillFromPrevious(row, prev) } },
        onUndoDelete = ::undoDelete,
        onAddCard = { structural { it.addCard() } },
        onAddSet = { row -> structural { it.addSet(row, copyWeight = true) } },
        onDeleteCard = { ids -> structural { it.deleteRows(ids) } }
    )

    companion object {
        /** One writer at a time across all loggers: a closing logger's last write is finished before the next one reads. */
        private val writer = Mutex()

        fun factory(repository: TrainingRepository, settingsStore: WorkoutSettingsStore): ViewModelProvider.Factory =
            viewModelFactory { initializer { LoggerViewModel(repository, settingsStore) } }
    }
}
