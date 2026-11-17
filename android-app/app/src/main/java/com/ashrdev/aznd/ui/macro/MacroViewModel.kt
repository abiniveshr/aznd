package com.ashrdev.aznd.ui.macro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashrdev.aznd.data.body.BodyDao
import com.ashrdev.aznd.data.macro.FoodDao
import com.ashrdev.aznd.data.macro.FoodEntry
import com.ashrdev.aznd.data.macro.Ingredient
import com.ashrdev.aznd.data.macro.IngredientDao
import com.ashrdev.aznd.data.workout.TrainingRepository
import com.ashrdev.aznd.domain.macro.BodyInputs
import com.ashrdev.aznd.domain.workout.E1rmCalculator
import com.ashrdev.aznd.domain.macro.MacroStats
import com.ashrdev.aznd.domain.body.MeasurementSite
import com.ashrdev.aznd.domain.workout.SessionStatsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Food log + the calories your finished workouts burnt (estimated from the workout module), so the
 * macro screen can show net calories. The burnt numbers are read when the screen opens and again whenever
 * a workout session starts, finishes or is discarded.
 */
class MacroViewModel(
    private val dao: FoodDao,
    private val ingredientDao: IngredientDao,
    bodyDao: BodyDao,
    repository: TrainingRepository,
    trackRpe: Boolean
) : ViewModel() {

    /** Newest first; null until first read. */
    val entries: StateFlow<List<FoodEntry>?> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Custom ingredients, A to Z. */
    val ingredients: StateFlow<List<Ingredient>> =
        ingredientDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Latest bodyweight and body fat % from the Body screen's Composition page. */
    val body: StateFlow<BodyInputs?> =
        combine(bodyDao.observeWeights(), bodyDao.observeMeasurements()) { weights, measurements ->
            BodyInputs(
                weights.firstOrNull()?.weightKg,
                measurements.firstOrNull { it.site == MeasurementSite.BODY_FAT.name }?.valueCm
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _burnt = MutableStateFlow<Map<Long, Double>>(emptyMap())
    /** Local-midnight day start -> kcal burnt in finished workouts that day. */
    val burntByDay: StateFlow<Map<Long, Double>> = _burnt.asStateFlow()

    init {
        viewModelScope.launch {
            // The unfinished session changes when one starts, finishes or is discarded.
            repository.observeActiveSession().map { it?.id }.distinctUntilChanged().collectLatest {
                val sets = repository.loggedSetData()
                val exercises = repository.statsExercises()
                _burnt.value = withContext(Dispatchers.Default) {
                    MacroStats.burntByDay(SessionStatsCalculator.all(sets, exercises, E1rmCalculator(rpeTracking = trackRpe)))
                }
            }
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

    fun addIngredient(ingredient: Ingredient) {
        viewModelScope.launch { ingredientDao.insert(ingredient) }
    }

    fun deleteIngredient(id: Long) {
        viewModelScope.launch { ingredientDao.delete(id) }
    }

    companion object {
        fun factory(
            dao: FoodDao,
            ingredientDao: IngredientDao,
            bodyDao: BodyDao,
            repository: TrainingRepository,
            trackRpe: Boolean
        ): ViewModelProvider.Factory =
            viewModelFactory { initializer { MacroViewModel(dao, ingredientDao, bodyDao, repository, trackRpe) } }
    }
}
