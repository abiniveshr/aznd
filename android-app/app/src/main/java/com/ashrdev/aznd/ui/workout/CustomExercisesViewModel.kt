package com.ashrdev.aznd.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.data.workout.TrainingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The "Custom exercises" list: every user-made exercise, with delete. Editing is the form screen. */
class CustomExercisesViewModel(private val repository: TrainingRepository) : ViewModel() {

    val exercises: StateFlow<List<Exercise>> =
        repository.observeCustomExercises().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Deletes the exercise and everything that used it; [onDeleted] gets it back (to remove its photo file). */
    fun delete(id: Long, onDeleted: (Exercise) -> Unit) {
        viewModelScope.launch { repository.deleteCustomExercise(id)?.let(onDeleted) }
    }

    companion object {
        fun factory(repository: TrainingRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { CustomExercisesViewModel(repository) } }
    }
}
