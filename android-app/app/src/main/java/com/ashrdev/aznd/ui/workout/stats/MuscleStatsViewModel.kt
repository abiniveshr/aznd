package com.ashrdev.aznd.ui.workout.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashrdev.aznd.data.workout.TrainingRepository
import com.ashrdev.aznd.domain.workout.E1rmCalculator
import com.ashrdev.aznd.domain.workout.Muscle
import com.ashrdev.aznd.domain.workout.MuscleSession
import com.ashrdev.aznd.domain.workout.MuscleStatsCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Reads every finished set once and turns it into one entry per session for this muscle. */
class MuscleStatsViewModel(
    repository: TrainingRepository,
    val muscle: Muscle,
    trackRpe: Boolean
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<MuscleSession>?>(null)
    /** Null until the data has been read. */
    val sessions: StateFlow<List<MuscleSession>?> = _sessions.asStateFlow()

    init {
        viewModelScope.launch {
            _sessions.value = MuscleStatsCalculator.sessions(
                muscle,
                repository.loggedSetData(),
                repository.statsExercises(),
                E1rmCalculator(rpeTracking = trackRpe)
            )
        }
    }

    companion object {
        fun factory(repository: TrainingRepository, muscle: Muscle, trackRpe: Boolean): ViewModelProvider.Factory =
            viewModelFactory { initializer { MuscleStatsViewModel(repository, muscle, trackRpe) } }
    }
}
