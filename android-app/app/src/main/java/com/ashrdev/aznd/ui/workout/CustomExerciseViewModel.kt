package com.ashrdev.aznd.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashrdev.aznd.data.workout.CustomExerciseForm
import com.ashrdev.aznd.data.workout.FormError
import com.ashrdev.aznd.data.workout.TrainingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Form state + save for a custom exercise. Errors are shown only after the first Save attempt.
 * [editId] = null creates a new exercise; otherwise that custom exercise is loaded and Save
 * updates it in place (same id, so workouts and history follow). The type can only be changed
 * while nothing uses the exercise yet, because logged values mean different things per type.
 */
class CustomExerciseViewModel(
    private val repository: TrainingRepository,
    private val editId: Long? = null
) : ViewModel() {

    val isEdit: Boolean = editId != null

    private val _form = MutableStateFlow(CustomExerciseForm())
    val form: StateFlow<CustomExerciseForm> = _form.asStateFlow()

    private val _errors = MutableStateFlow<Set<FormError>>(emptySet())
    /** Empty until Save was pressed once; then refreshed on every edit. */
    val errors: StateFlow<Set<FormError>> = _errors.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _loaded = MutableStateFlow(editId == null)
    /** False only while an existing exercise is being loaded for editing. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _typeLocked = MutableStateFlow(false)
    val typeLocked: StateFlow<Boolean> = _typeLocked.asStateFlow()

    /** The photo the exercise had when editing started (its file must survive until Save). */
    var originalPhoto: String? = null
        private set

    private var attempted = false
    private var nameTaken = false

    init {
        if (editId != null) {
            viewModelScope.launch {
                val e = repository.exercise(editId)
                if (e == null || !e.isCustom) {
                    _saved.value = true // nothing to edit any more: leave the screen
                } else {
                    originalPhoto = e.photoPath
                    _form.value = CustomExerciseForm.from(e)
                    _typeLocked.value = repository.exerciseInUse(editId)
                    _loaded.value = true
                }
            }
        }
    }

    fun update(op: (CustomExerciseForm) -> CustomExerciseForm) {
        _form.update { old ->
            val next = op(old)
            if (_typeLocked.value) next.copy(type = old.type) else next
        }
        if (attempted) _errors.value = _form.value.errors(nameTaken && _form.value.cleanName.isNotEmpty())
    }

    fun save() {
        if (_saved.value) return
        val f = _form.value
        viewModelScope.launch {
            attempted = true
            nameTaken = f.cleanName.isNotEmpty() && repository.exerciseNameTaken(f.cleanName, editId)
            val errs = f.errors(nameTaken)
            _errors.value = errs
            if (errs.isEmpty()) {
                f.toExercise()?.let { draft ->
                    if (editId == null) repository.addCustomExercise(draft)
                    else repository.updateCustomExercise(draft.copy(id = editId))
                }
                _saved.value = true
            }
        }
    }

    companion object {
        fun factory(repository: TrainingRepository, editId: Long? = null): ViewModelProvider.Factory =
            viewModelFactory { initializer { CustomExerciseViewModel(repository, editId) } }
    }
}
