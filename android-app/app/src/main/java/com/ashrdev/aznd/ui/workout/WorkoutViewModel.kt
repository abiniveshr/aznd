package com.ashrdev.aznd.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ashrdev.aznd.data.workout.ActiveSessionWithSets
import com.ashrdev.aznd.data.workout.ExerciseSetPoint
import com.ashrdev.aznd.data.workout.ExerciseWithSets
import com.ashrdev.aznd.data.workout.LoggedSetEntity
import com.ashrdev.aznd.data.workout.WorkoutDao
import com.ashrdev.aznd.data.workout.WorkoutEntity
import com.ashrdev.aznd.data.workout.WorkoutWithExercises
import com.ashrdev.aznd.data.workout.SessionWithSets
import com.ashrdev.aznd.data.workout.WorkoutHistorySummary
import com.ashrdev.aznd.data.workout.RecentWorkoutSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TopExerciseStat(
    val WorkoutId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val bestPoint: SessionE1rmPoint
)

class WorkoutViewModel(private val dao: WorkoutDao) : ViewModel() {
    val Workouts: StateFlow<List<WorkoutWithExercises>> = dao.getWorkoutsWithExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSession: StateFlow<ActiveSessionWithSets?> = dao.observeActiveSession()
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val workoutHistorySummaries: StateFlow<List<WorkoutHistorySummary>> =
        dao.getWorkoutHistorySummaries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun sessionsForWorkout(WorkoutId: Long): Flow<List<SessionWithSets>> =
        dao.getSessionsForWorkout(WorkoutId)
    suspend fun getMostRecentSession(): RecentWorkoutSession? = dao.getMostRecentSession()
    suspend fun getWorkout(id: Long): WorkoutWithExercises? =
        dao.getWorkoutWithExercises(id)

    suspend fun getSession(id: Long): SessionWithSets? =
        dao.getSession(id)

    suspend fun getPreviousSession(WorkoutId: Long): SessionWithSets? =
        dao.getLatestSessionForWorkout(WorkoutId)

    suspend fun getExerciseHistory(
        WorkoutId: Long,
        exerciseName: String
    ): List<ExerciseSetPoint> =
        dao.getExerciseHistory(WorkoutId, exerciseName)

    // Best estimated-1RM set per exercise, across every Workout, heaviest first.
    suspend fun getTopExercises(limit: Int = 3): List<TopExerciseStat> {
        val Workouts = dao.getAllWorkoutsWithExercisesOnce()
        return Workouts
            .flatMap { rws -> rws.exercises.map { ews -> Triple(rws.Workout.id, ews.exercise.id, ews.exercise.name) } }
            .mapNotNull { (WorkoutId, exerciseId, exerciseName) ->
                val best = buildSessionE1rmPoints(dao.getExerciseHistory(WorkoutId, exerciseName))
                    .maxByOrNull { it.e1rm }
                best?.let { TopExerciseStat(WorkoutId, exerciseId, exerciseName, it) }
            }
            .sortedByDescending { it.bestPoint.e1rm }
            .take(limit)
    }

    fun saveWorkout(
        id: Long,
        name: String,
        exercises: List<ExerciseWithSets>
    ) {
        viewModelScope.launch {
            dao.saveWorkout(
                WorkoutEntity(
                    id = id,
                    name = name
                ),
                exercises
            )
        }
    }

    fun deleteWorkout(id: Long) {
        viewModelScope.launch {
            dao.deleteWorkout(id)
        }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            dao.deleteSession(id)
        }
    }

    fun startSession(Workout: WorkoutWithExercises, onStarted: () -> Unit = {}) {
        viewModelScope.launch {
            val sets = mutableListOf<com.ashrdev.aznd.data.workout.ActiveSetEntity>()
            Workout.exercises
                .sortedBy { it.exercise.orderIndex }
                .forEachIndexed { exIndex, ews ->
                    ews.sets
                        .sortedBy { it.orderIndex }
                        .forEachIndexed { setIndex, s ->
                            sets += com.ashrdev.aznd.data.workout.ActiveSetEntity(
                                exerciseName = ews.exercise.name,
                                exerciseIndex = exIndex,
                                setIndex = setIndex,
                                mode = s.mode,
                                valueText = "",
                                weightText = ""
                            )
                        }
                }
            dao.startActiveSession(
                com.ashrdev.aznd.data.workout.ActiveSessionEntity(
                    WorkoutId = Workout.Workout.id,
                    WorkoutName = Workout.Workout.name,
                    startedAt = System.currentTimeMillis()
                ),
                sets
            )
            onStarted()
        }
    }
    
    fun deleteHistoryForWorkout(WorkoutId: Long) {
        viewModelScope.launch { dao.deleteSessionsForWorkout(WorkoutId) }
    }

    fun updateActiveSet(
        set: com.ashrdev.aznd.data.workout.ActiveSetEntity
    ) {
        viewModelScope.launch {
            dao.updateActiveSet(set)
        }
    }

    fun attachPhoto(uri: String?) {
        viewModelScope.launch {
            dao.setActivePhoto(uri)
        }
    }

    fun discardSession() {
        viewModelScope.launch {
            dao.clearActiveSession()
        }
    }

    fun finishSession(onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            dao.finishActiveSession()?.let(onSaved)
        }
    }
}

class WorkoutViewModelFactory(
    private val dao: WorkoutDao
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T = WorkoutViewModel(dao) as T
}