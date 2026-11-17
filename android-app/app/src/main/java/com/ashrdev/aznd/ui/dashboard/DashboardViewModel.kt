package com.ashrdev.aznd.ui.dashboard

import androidx.compose.runtime.snapshotFlow
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
import com.ashrdev.aznd.data.macro.MacroTargetsStore
import com.ashrdev.aznd.data.workout.SessionWithSets
import com.ashrdev.aznd.data.workout.TrainingRepository
import com.ashrdev.aznd.domain.GraphMode
import com.ashrdev.aznd.domain.macro.CalorieGoal
import com.ashrdev.aznd.domain.macro.MacroStats
import com.ashrdev.aznd.domain.workout.E1rmCalculator
import com.ashrdev.aznd.domain.workout.SessionStatsCalculator
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.ui.streaks.StreakWithCount
import com.ashrdev.aznd.ui.workout.TopExerciseStat
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import com.ashrdev.aznd.ui.workout.topExercisesOf
import com.ashrdev.aznd.data.workout.WorkoutWithExercises
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TOP_LIFTS = 3
private const val CLOCK_TICK_MS = 60_000L
private const val KEEP_MS = 5000L

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    foodDao: FoodDao,
    bodyDao: BodyDao,
    private val repository: TrainingRepository,
    private val trackRpe: () -> Boolean
) : ViewModel() {

    val food: StateFlow<List<FoodEntry>> =
        foodDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_MS), emptyList())

    val weights: StateFlow<List<BodyWeightEntry>> =
        bodyDao.observeWeights().stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_MS), emptyList())

    val measurements: StateFlow<List<BodyMeasurement>> =
        bodyDao.observeMeasurements().stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_MS), emptyList())

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

    // ---- what the dashboard shows, worked out once and shared ----

    private class Sources(
        val workout: WorkoutViewModel,
        val streaks: StreakViewModel,
        val macro: MacroTargetsStore,
        val store: DashboardStore
    )

    private class MacroKey(val goal: CalorieGoal, val offset: Int, val proteinG: Int, val carbsG: Int, val fatG: Int) {
        override fun equals(other: Any?): Boolean =
            other is MacroKey && goal == other.goal && offset == other.offset &&
                proteinG == other.proteinG && carbsG == other.carbsG && fatG == other.fatG

        override fun hashCode(): Int = ((goal.hashCode() * 31 + offset) * 31 + proteinG) * 31 + carbsG * 31 + fatG
    }

    private class Inputs(
        val food: List<FoodEntry>,
        val weights: List<BodyWeightEntry>,
        val measurements: List<BodyMeasurement>,
        val burnt: Map<Long, Double>,
        val macro: MacroKey
    )

    private class Core(
        val streaks: List<StreakWithCount>,
        val sessions: List<SessionWithSets>,
        val workouts: List<WorkoutWithExercises>,
        val lifts: List<TopExerciseStat>,
        val modes: List<GraphMode>
    )

    private val sources = MutableStateFlow<Sources?>(null)

    // Sessions handed over by a screen that never bound the sources (the graph screen).
    private val seeded = MutableStateFlow<List<SessionWithSets>?>(null)

    /** The first screen to bind decides where the dashboard's data comes from; later calls change nothing. */
    fun bind(workout: WorkoutViewModel, streaks: StreakViewModel, macro: MacroTargetsStore, store: DashboardStore) {
        if (sources.value == null) sources.value = Sources(workout, streaks, macro, store)
    }

    fun seedSessions(list: List<SessionWithSets>) {
        if (sources.value == null) seeded.value = list
    }

    private val sessionsFlow: Flow<List<SessionWithSets>> =
        sources.flatMapLatest { s -> if (s != null) s.workout.allSessions else seeded.filterNotNull() }

    /** All ten graph series. Only built while the graph screen, edit mode or a graph tile needs them. */
    val modes: StateFlow<List<GraphMode>> =
        combine(food, weights, measurements, sessionsFlow, burntByDay) { f, w, m, s, b ->
            DashboardSeries.modes(f, w, m, s, b)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_MS), emptyList())

    // Kept (not reset) while nothing is collecting, so tiles never fall back to "No lifts yet".
    private val topLifts: StateFlow<List<TopExerciseStat>> =
        sources.filterNotNull().flatMapLatest { s ->
            combine(
                s.workout.allSessions.onEach { refresh(it.size) },
                s.workout.workouts,
                liftLimit(s.store)
            ) { sessions, workouts, limit -> topExercisesOf(workouts, sessions, limit) }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_MS), emptyList())

    val data: StateFlow<DashboardData> =
        sources.filterNotNull().flatMapLatest { s -> build(s) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_MS), DashboardData.loading())

    private fun liftLimit(store: DashboardStore): Flow<Int> = snapshotFlow {
        val top = store.presets.flatMap { p -> p.widgets.filter { it.type == DashWidgetType.TOP_LIFT }.map { it.rank } }
        maxOf(TOP_LIFTS, top.maxOrNull() ?: 0)
    }.distinctUntilChanged()

    private fun hasGraph(store: DashboardStore): Flow<Boolean> = snapshotFlow {
        store.presets.any { p -> p.widgets.any { it.type == DashWidgetType.GRAPH } }
    }.distinctUntilChanged()

    private fun ticks(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(CLOCK_TICK_MS)
        }
    }

    private fun build(s: Sources): Flow<DashboardData> {
        val graph = hasGraph(s.store)
        val modesShown = graph.flatMapLatest { g -> if (g) modes else flowOf(emptyList<GraphMode>()) }
        val core = combine(s.streaks.streaksWithCounts, s.workout.allSessions, s.workout.workouts, topLifts, modesShown) { st, se, wo, li, mo ->
            Core(st, se, wo, li, mo)
        }
        // Graph tiles read the exact time for their range; without one only the day matters.
        val now = graph.flatMapLatest { g ->
            if (g) ticks() else ticks().map { MacroStats.dayStart(it) }.distinctUntilChanged()
        }
        val macro = snapshotFlow {
            MacroKey(s.macro.goal, s.macro.advancedOffset, s.macro.proteinG, s.macro.carbsG, s.macro.fatG)
        }.distinctUntilChanged()
        val inputs = combine(food, weights, measurements, burntByDay, macro) { f, w, m, b, k -> Inputs(f, w, m, b, k) }
        val day = ticks().map { MacroStats.dayStart(it) }.distinctUntilChanged()
        val today = combine(inputs, day) { i, d ->
            DashboardSeries.today(i.food, i.burnt, i.weights, i.measurements, i.macro.goal, i.macro.offset, i.macro.proteinG, i.macro.carbsG, i.macro.fatG, d)
        }
        return combine(core, today, now) { c, t, n ->
            DashboardData(c.streaks, c.lifts, c.sessions, c.workouts, t, c.modes, n)
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
