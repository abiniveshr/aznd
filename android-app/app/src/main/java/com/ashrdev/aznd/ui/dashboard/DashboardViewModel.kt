package com.ashrdev.aznd.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashrdev.aznd.data.body.BodyDao
import com.ashrdev.aznd.data.body.BodyMeasurement
import com.ashrdev.aznd.data.body.BodyWeightEntry
import com.ashrdev.aznd.data.macro.FoodDao
import com.ashrdev.aznd.data.macro.FoodEntry
import com.ashrdev.aznd.data.workout.TrainingRepository
import com.ashrdev.aznd.domain.macro.MacroStats
import com.ashrdev.aznd.domain.workout.E1rmCalculator
import com.ashrdev.aznd.domain.workout.SessionStatsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardViewModel(
    foodDao: FoodDao,
    bodyDao: BodyDao,
    private val repository: TrainingRepository,
    private val trackRpe: () -> Boolean
) : ViewModel() {

    val food: StateFlow<List<FoodEntry>> =
        foodDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val weights: StateFlow<List<BodyWeightEntry>> =
        bodyDao.observeWeights().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val measurements: StateFlow<List<BodyMeasurement>> =
        bodyDao.observeMeasurements().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _burnt = MutableStateFlow<Map<Long, Double>>(emptyMap())
    val burntByDay: StateFlow<Map<Long, Double>> = _burnt.asStateFlow()

    private var computedFor = -1

    fun refresh(sessionCount: Int) {
        if (sessionCount == computedFor || (sessionCount == 0 && computedFor < 0)) return
        computedFor = sessionCount
        viewModelScope.launch {
            _burnt.value = withContext(Dispatchers.Default) {
                val sessions = SessionStatsCalculator.all(
                    repository.loggedSetData(),
                    repository.statsExercises(),
                    E1rmCalculator(rpeTracking = trackRpe())
                )
                MacroStats.burntByDay(sessions)
            }
        }
    }

    companion object {
        fun factory(
            foodDao: FoodDao,
            bodyDao: BodyDao,
            repository: TrainingRepository,
            trackRpe: () -> Boolean
        ): ViewModelProvider.Factory =
            viewModelFactory { initializer { DashboardViewModel(foodDao, bodyDao, repository, trackRpe) } }
    }
}
