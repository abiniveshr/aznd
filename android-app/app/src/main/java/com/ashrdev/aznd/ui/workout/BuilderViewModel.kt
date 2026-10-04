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
import com.ashrdev.aznd.domain.BuilderState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the builder's [BuilderState]. All editing logic lives in the pure BuilderState; this class
 * loads the saved template, exposes StateFlows, and saves changes.
 */
class BuilderViewModel(
    private val repository: TrainingRepository,
    settingsStore: WorkoutSettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(BuilderState())
    val state: StateFlow<BuilderState> = _state.asStateFlow()

    private val _ready = MutableStateFlow(false)
    /** False until the saved template (if any) has been read. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    val exercises: StateFlow<List<Exercise>> =
        repository.observeExercises().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<WorkoutSettings> = settingsStore.settings

    private var loadedKey: Long? = null
    private var loadedOnce = false

    /** Call from the screen; a second call (rotation) with the same id does nothing. [workoutId] null = new workout. */
    fun load(workoutId: Long?) {
        if (loadedOnce && loadedKey == workoutId) return
        loadedOnce = true
        loadedKey = workoutId
        if (workoutId == null) {
            _state.value = BuilderState.from(emptyList())
            _ready.value = true
        } else {
            _ready.value = false
            viewModelScope.launch {
                _state.value = BuilderState.from(repository.templateRows(workoutId))
                _ready.value = true
            }
        }
    }

    /**
     * Writes the template (rows without an exercise are dropped). For a NEW workout, insert your
     * workout record first and pass its real id here.
     */
    suspend fun save(workoutId: Long) {
        repository.saveTemplate(workoutId, _state.value.rowsForSave())
    }

    val actions = BuilderActions(
        onPickCardExercise = { row, ex -> _state.update { it.pickCardExercise(row, ex) } },
        onPickRowExercise = { row, ex -> _state.update { it.pickRowExercise(row, ex) } },
        onDuplicate = { row -> _state.update { it.duplicate(row) } },
        onDelete = { row -> _state.update { it.delete(row) } },
        onDrop = { row -> _state.update { it.addDrop(row) } },
        onSuperset = { row -> _state.update { it.addSuperset(row) } },
        onTargetText = { row, text -> _state.update { it.setTargetText(row, text) } },
        onAddCard = { _state.update { it.addCard() } }
    )

    companion object {
        fun factory(repository: TrainingRepository, settingsStore: WorkoutSettingsStore): ViewModelProvider.Factory =
            viewModelFactory { initializer { BuilderViewModel(repository, settingsStore) } }
    }
}
