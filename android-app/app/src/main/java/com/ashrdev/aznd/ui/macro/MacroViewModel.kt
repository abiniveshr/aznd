package com.ashrdev.aznd.ui.macro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashrdev.aznd.data.macro.FoodDao
import com.ashrdev.aznd.data.macro.FoodEntry
import com.ashrdev.aznd.data.workout.TrainingRepository
import com.ashrdev.aznd.domain.E1rmCalculator
import com.ashrdev.aznd.domain.MacroStats
import com.ashrdev.aznd.domain.SessionStatsCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Food log + the calories your finished workouts burnt (estimated from the workout module), so the
 * macro screen can show net calories. The burnt numbers are read once when the screen opens.
 */
class MacroViewModel(
    private val dao: FoodDao,
    repository: TrainingRepository,
    trackRpe: Boolean
) : ViewModel() {

    /** Newest first; null until first read. */
    val entries: StateFlow<List<FoodEntry>?> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _burnt = MutableStateFlow<Map<Long, Double>>(emptyMap())
    /** Local-midnight day start -> kcal burnt in finished workouts that day. */
    val burntByDay: StateFlow<Map<Long, Double>> = _burnt.asStateFlow()

    init {
        viewModelScope.launch {
            val sessions = SessionStatsCalculator.all(
                repository.loggedSetData(),
                repository.statsExercises(),
                E1rmCalculator(rpeTracking = trackRpe)
            )
            _burnt.value = MacroStats.burntByDay(sessions)
        }
    }

    fun add(name: String, kcal: Double, proteinG: Double, carbsG: Double, fatG: Double, atMs: Long) {
        viewModelScope.launch {
            dao.insert(FoodEntry(eatenAt = atMs, name = name, calories = kcal, proteinG = proteinG, carbsG = carbsG, fatG = fatG))
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { dao.delete(id) }
    }

    companion object {
        fun factory(dao: FoodDao, repository: TrainingRepository, trackRpe: Boolean): ViewModelProvider.Factory =
            viewModelFactory { initializer { MacroViewModel(dao, repository, trackRpe) } }
    }
}
