package com.ashrdev.aznd.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ashrdev.aznd.data.workout.EMPTY_WORKOUT_ID
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
import com.ashrdev.aznd.domain.LoggedSetData
import com.ashrdev.aznd.domain.SetCard
import com.ashrdev.aznd.domain.SetKind
import com.ashrdev.aznd.domain.SetRow
import com.ashrdev.aznd.domain.SetTree
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TopExerciseStat(
    val workoutId: Long,
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
    val workouts: StateFlow<List<WorkoutWithExercises>> = dao.getWorkoutsWithExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Null when nothing is running, or when the running session's workout no longer exists. */
    val activeSession: StateFlow<ActiveSessionInfo?> =
        combine(training.observeActiveSession(), workouts) { session, workouts ->
            session?.let { s ->
                if (s.workoutId == EMPTY_WORKOUT_ID) return@let ActiveSessionInfo(s.workoutId, "Empty workout", s.startedAt)
                workouts.firstOrNull { it.workout.id == s.workoutId }
                    ?.let { ActiveSessionInfo(s.workoutId, it.workout.name, s.startedAt) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val workoutHistorySummaries: StateFlow<List<WorkoutHistorySummary>> =
        dao.getWorkoutHistorySummaries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun sessionsForWorkout(workoutId: Long): Flow<List<SessionWithSets>> =
        dao.getSessionsForWorkout(workoutId)
    suspend fun getMostRecentSession(): RecentWorkoutSession? = dao.getMostRecentSession()
    suspend fun getWorkout(id: Long): WorkoutWithExercises? =
        dao.getWorkoutWithExercises(id)

    /** Every catalog and custom exercise, A-Z. */
    val catalogExercises: StateFlow<List<Exercise>> = training.observeExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Ids of exercises with logged data, for the Exercises page's "Has data" filter. */
    val exerciseIdsWithData: StateFlow<Set<Long>> = training.observeExerciseIdsWithData()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** One catalog exercise (for the exercise viewer). */
    suspend fun exercise(id: Long): Exercise? = training.exercise(id)

    /** One exercise's finished sets from every session, empty workouts included. */
    suspend fun loggedSetsForExercise(exerciseId: Long): List<LoggedSetData> =
        training.loggedSetDataForExercise(exerciseId)

    suspend fun saveWorkoutRecord(id: Long?, name: String): Long =
        dao.saveWorkoutRecord(id, name)

    suspend fun getSession(id: Long): SessionWithSets? =
        dao.getSession(id)

    suspend fun getPreviousSession(workoutId: Long): SessionWithSets? =
        dao.getLatestSessionForWorkout(workoutId)

    suspend fun getExerciseHistory(
        workoutId: Long,
        exerciseName: String
    ): List<ExerciseSetPoint> =
        dao.getExerciseHistory(workoutId, exerciseName)

    // Best estimated-1RM set per exercise, across every workout, heaviest first.
    suspend fun getTopExercises(limit: Int = 3): List<TopExerciseStat> {
        val workouts = dao.getAllWorkoutsWithExercisesOnce()
        return workouts
            .flatMap { rws -> rws.exercises.map { ews -> Triple(rws.workout.id, ews.exercise.id, ews.exercise.name) } }
            .mapNotNull { (workoutId, exerciseId, exerciseName) ->
                val best = buildSessionE1rmPoints(dao.getExerciseHistory(workoutId, exerciseName))
                    .maxByOrNull { it.e1rm }
                best?.let { TopExerciseStat(workoutId, exerciseId, exerciseName, it) }
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

    fun deleteHistoryForWorkout(workoutId: Long) {
        viewModelScope.launch {
            dao.deleteSessionsForWorkout(workoutId)
            training.deleteCompletedForWorkout(workoutId)
        }
    }

    suspend fun repRangeFor(workoutId: Long, exerciseName: String): IntRange? {
        val view = templateView(workoutId)
        val rows = view.cards
            .filter { card -> card.exerciseId?.let { view.exercises[it]?.name } == exerciseName }
            .flatMap { it.rows }
            .filter { it.kind != SetKind.DROP && it.targetMin != null }
        if (rows.isEmpty()) return null
        val low = rows.minOf { it.targetMin ?: 0 }
        val high = rows.maxOf { it.targetMax ?: it.targetMin ?: 0 }
        return if (high >= low) low..high else null
    }

    /** The catalog exercises this workout's template uses, A-Z (what its stats can be asked about). */
    suspend fun workoutCatalogExercises(workoutId: Long): List<Exercise> =
        templateView(workoutId).exercises.values.sortedBy { it.name.lowercase() }

    /** Rows of the last completed session of this workout, for the "Previous:" lines before starting. */
    suspend fun previousRows(workoutId: Long): List<SetRow> = training.previousSessionRows(workoutId)

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
