package com.ashrdev.aznd.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashrdev.aznd.data.workout.TrainingRepository
import com.ashrdev.aznd.domain.E1rmCalculator
import com.ashrdev.aznd.domain.SessionStat
import com.ashrdev.aznd.domain.SessionStatsCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Per-session numbers of one workout, for the graph on the workout stats screen. */
class WorkoutGraphViewModel(
    repository: TrainingRepository,
    workoutId: Long,
    trackRpe: Boolean
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<SessionStat>?>(null)
    /** Null until the data has been read. */
    val sessions: StateFlow<List<SessionStat>?> = _sessions.asStateFlow()

    init {
        viewModelScope.launch {
            _sessions.value = SessionStatsCalculator.forWorkout(
                workoutId,
                repository.loggedSetData(),
                repository.statsExercises(),
                E1rmCalculator(rpeTracking = trackRpe)
            )
        }
    }

    companion object {
        fun factory(repository: TrainingRepository, workoutId: Long, trackRpe: Boolean): ViewModelProvider.Factory =
            viewModelFactory { initializer { WorkoutGraphViewModel(repository, workoutId, trackRpe) } }
    }
}
