package com.ashrdev.aznd.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashrdev.aznd.data.workout.TrainingRepository
import com.ashrdev.aznd.domain.E1rmCalculator
import com.ashrdev.aznd.domain.Muscle
import com.ashrdev.aznd.domain.MuscleExercise
import com.ashrdev.aznd.domain.MuscleSetData
import com.ashrdev.aznd.domain.MuscleStats
import com.ashrdev.aznd.domain.MuscleStatsCalculator
import com.ashrdev.aznd.domain.StatsRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loads every finished set once, then recomputes the numbers whenever the range chip changes. */
class MuscleStatsViewModel(
    repository: TrainingRepository,
    val muscle: Muscle,
    trackRpe: Boolean
) : ViewModel() {

    private val calculator = E1rmCalculator(rpeTracking = trackRpe)
    private var rows: List<MuscleSetData> = emptyList()
    private var exercises: Map<Long, MuscleExercise> = emptyMap()
    private var loaded = false

    private val _range = MutableStateFlow(StatsRange.MONTH)
    val range: StateFlow<StatsRange> = _range.asStateFlow()

    private val _stats = MutableStateFlow<MuscleStats?>(null)
    /** Null until the data has been read. */
    val stats: StateFlow<MuscleStats?> = _stats.asStateFlow()

    init {
        viewModelScope.launch {
            rows = repository.muscleSetData()
            exercises = repository.muscleExercises()
            loaded = true
            recompute()
        }
    }

    fun setRange(range: StatsRange) {
        _range.value = range
        if (loaded) recompute()
    }

    private fun recompute() {
        _stats.value = MuscleStatsCalculator.compute(
            muscle, rows, exercises, _range.value, System.currentTimeMillis(), calculator
        )
    }

    companion object {
        fun factory(repository: TrainingRepository, muscle: Muscle, trackRpe: Boolean): ViewModelProvider.Factory =
            viewModelFactory { initializer { MuscleStatsViewModel(repository, muscle, trackRpe) } }
    }
}
