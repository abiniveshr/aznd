package com.ashrdev.aznd.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ashrdev.aznd.data.workout.ExerciseSetPoint
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.data.workout.ExerciseWithSets
import com.ashrdev.aznd.data.workout.TrainingRepository
import com.ashrdev.aznd.data.workout.WorkoutDao
import com.ashrdev.aznd.data.workout.WorkoutEntity
import com.ashrdev.aznd.data.workout.WorkoutWithExercises
import com.ashrdev.aznd.data.workout.SessionWithSets
import com.ashrdev.aznd.data.workout.WorkoutHistorySummary
import com.ashrdev.aznd.data.workout.RecentWorkoutSession
import com.ashrdev.aznd.domain.SetCard
import com.ashrdev.aznd.domain.SetTree
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TopExerciseStat(
    val WorkoutId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val bestPoint: SessionE1rmPoint
)

/** The unfinished session, if any, shown on the Workouts list and checked before starting another. */
data class ActiveSessionInfo(val workoutId: Long, val workoutName: String, val startedAt: Long)

/** A workout's saved template, grouped into exercise cards (unset rows removed), for the read-only view. */
class TemplateView(val cards: List<SetCard>, val exercises: Map<Long, Exercise>)

class WorkoutViewModel(
    private val dao: WorkoutDao,
    private val training: TrainingRepository
) : ViewModel() {
    val Workouts: StateFlow<List<WorkoutWithExercises>> = dao.getWorkoutsWithExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Null when nothing is running, or when the running session's workout no longer exists. */
    val activeSession: StateFlow<ActiveSessionInfo?> =
        combine(training.observeActiveSession(), Workouts) { session, workouts ->
            session?.let { s ->
                workouts.firstOrNull { it.Workout.id == s.workoutId }
                    ?.let { ActiveSessionInfo(s.workoutId, it.Workout.name, s.startedAt) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val workoutHistorySummaries: StateFlow<List<WorkoutHistorySummary>> =
        dao.getWorkoutHistorySummaries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun sessionsForWorkout(WorkoutId: Long): Flow<List<SessionWithSets>> =
        dao.getSessionsForWorkout(WorkoutId)
    suspend fun getMostRecentSession(): RecentWorkoutSession? = dao.getMostRecentSession()
    suspend fun getWorkout(id: Long): WorkoutWithExercises? =
        dao.getWorkoutWithExercises(id)

    suspend fun saveWorkoutRecord(id: Long?, name: String): Long =
        dao.saveWorkoutRecord(id, name)

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

    /** Deletes the workout, its template and any session of it that is still running. */
    fun deleteWorkout(id: Long) {
        viewModelScope.launch {
            dao.deleteWorkout(id)
            training.deleteTemplate(id)
        }
    }

    /** Deletes a session from the history, together with its copy used for "Previous:" and suggestions. */
    fun deleteSession(id: Long) {
        viewModelScope.launch {
            dao.deleteSession(id)
            training.deleteByLegacySession(id)
        }
    }

    fun deleteHistoryForWorkout(WorkoutId: Long) {
        viewModelScope.launch {
            dao.deleteSessionsForWorkout(WorkoutId)
            training.deleteCompletedForWorkout(WorkoutId)
        }
    }

    suspend fun templateView(workoutId: Long): TemplateView {
        val rows = SetTree.dropUnsetRows(training.templateRows(workoutId))
        return TemplateView(
            cards = SetTree.cards(rows),
            exercises = training.exercisesById(rows.mapNotNull { it.exerciseId })
        )
    }
}

class WorkoutViewModelFactory(
    private val dao: WorkoutDao,
    private val training: TrainingRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T = WorkoutViewModel(dao, training) as T
}
