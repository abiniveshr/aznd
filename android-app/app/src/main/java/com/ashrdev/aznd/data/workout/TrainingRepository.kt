package com.ashrdev.aznd.data.workout

import com.ashrdev.aznd.domain.BodyweightProvider
import com.ashrdev.aznd.domain.ExerciseType
import com.ashrdev.aznd.domain.MuscleExercise
import com.ashrdev.aznd.domain.MuscleSetData
import com.ashrdev.aznd.domain.PlaceholderBodyweightProvider
import com.ashrdev.aznd.domain.SessionHistory
import com.ashrdev.aznd.domain.SetRow
import com.ashrdev.aznd.domain.SetTree
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Everything the builder and logger need from the database.
 *
 * Wire-up:
 *  - AppDatabase lists Exercise, TemplateSet, LoggedSession and LoggedSet and exposes exerciseDao(),
 *    templateDao() and sessionDao().
 *  - Build one: TrainingRepository(db.exerciseDao(), db.templateDao(), db.sessionDao(), legacy = db.WorkoutDao()).
 *  - Deleting a workout must also call deleteTemplate(workoutId) (workoutId has no foreign key);
 *    WorkoutViewModel.deleteWorkout does that.
 *
 * [legacy] keeps the History / Stats / Dashboard screens working: they read the `exercises`,
 * `exercise_sets`, `sessions` and `logged_sets` tables. Saving a template mirrors its exercises
 * there, and finishing a session writes the matching history row. Pass null to switch it off.
 */
class TrainingRepository(
    private val exercises: ExerciseDao,
    private val templates: TemplateDao,
    private val sessions: SessionDao,
    private val bodyweight: BodyweightProvider = PlaceholderBodyweightProvider,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val legacy: WorkoutDao? = null
) {

    // ---- catalog ----

    fun observeExercises(): Flow<List<Exercise>> = exercises.observeAll()

    suspend fun exercise(id: Long): Exercise? = exercises.get(id)

    suspend fun exercisesById(ids: Collection<Long>): Map<Long, Exercise> =
        if (ids.isEmpty()) emptyMap() else exercises.getByIds(ids.distinct()).associateBy { it.id }

    /** Custom exercises are always inserted as custom, with no seed key. */
    suspend fun addCustomExercise(draft: Exercise): Long =
        exercises.insert(draft.copy(id = 0, seedKey = null, isCustom = true))

    /** [exceptId]: the exercise being edited, which may keep its own name. */
    suspend fun exerciseNameTaken(name: String, exceptId: Long? = null): Boolean =
        exercises.findByName(name.trim())?.let { it.id != exceptId } ?: false

    fun observeCustomExercises(): Flow<List<Exercise>> =
        exercises.observeAll().map { list -> list.filter { it.isCustom } }

    /** True when any workout or logged session uses this exercise (its type is then locked). */
    suspend fun exerciseInUse(id: Long): Boolean = exercises.usageCount(id) > 0

    /** Edits a custom exercise in place (same id). Catalog exercises are never touched. */
    suspend fun updateCustomExercise(draft: Exercise) {
        val old = exercises.get(draft.id) ?: return
        if (!old.isCustom) return
        exercises.updateCustom(old, draft.copy(seedKey = old.seedKey, isCustom = true))
    }

    /**
     * Deletes a custom exercise and everything that used it (see ExerciseDao.deleteCustomCascade).
     * Returns the deleted exercise (so the caller can remove its photo file), or null if nothing was deleted.
     */
    suspend fun deleteCustomExercise(id: Long): Exercise? {
        val old = exercises.get(id) ?: return null
        if (!old.isCustom) return null
        exercises.deleteCustomCascade(old)
        return old
    }

    // ---- muscle statistics ----

    suspend fun muscleSetData(): List<MuscleSetData> = sessions.completedSetData()

    suspend fun muscleExercises(): Map<Long, MuscleExercise> =
        exercises.allOnce().associate {
            it.id to MuscleExercise(it.id, it.name, it.type, it.bodyweightShare, it.primaryMuscle, it.secondaryMuscles)
        }

    // ---- templates ----

    /** The workout's template, depth-first. */
    suspend fun templateRows(workoutId: Long): List<SetRow> =
        SetTree.ordered(templates.forWorkout(workoutId).map { it.toRow() })

    suspend fun saveTemplate(workoutId: Long, rows: List<SetRow>) {
        templates.replaceTemplate(workoutId, rows)
        mirrorTemplate(workoutId, rows)
    }

    /**
     * Removes the template and any session of this workout that is still running (it could never
     * be resumed, and would block starting other workouts). Finished sessions stay: they still
     * feed "Previous:" and suggestions for other workouts.
     */
    suspend fun deleteTemplate(workoutId: Long) {
        templates.deleteForWorkout(workoutId)
        sessions.deleteUnfinishedForWorkout(workoutId)
    }

    // ---- sessions ----

    /** Starts a session from the workout's template. Null if the template has no usable rows. */
    suspend fun startSession(workoutId: Long): Long? =
        sessions.startSession(workoutId, bodyweight.currentKg(), clock(), templateRows(workoutId))

    fun observeActiveSession(): Flow<LoggedSession?> = sessions.observeActive()

    suspend fun session(id: Long): LoggedSession? = sessions.getSession(id)

    /** The session's rows, depth-first. */
    fun observeSessionRows(sessionId: Long): Flow<List<SetRow>> =
        sessions.observeSets(sessionId).map { list -> SetTree.ordered(list.map { it.toRow() }) }

    suspend fun syncSessionRows(sessionId: Long, rows: List<SetRow>): Map<Long, Long> =
        sessions.syncRows(sessionId, rows)

    suspend fun updateSetValues(id: Long, weightKg: Double?, reps: Int?, durationSec: Int?, rpe: Double?) =
        sessions.updateValues(id, weightKg, reps, durationSec, rpe)

    /**
     * The Finish button: only this makes a session "completed". Finishing twice (double tap) is a
     * no-op the second time, so the history never gets a duplicate.
     */
    suspend fun finishSession(sessionId: Long) {
        val s = sessions.getSession(sessionId) ?: return
        if (s.finishedAt != null) return
        val finishedAt = clock()
        sessions.setFinished(sessionId, finishedAt)
        mirrorSession(s.copy(finishedAt = finishedAt))
    }

    suspend fun discardSession(sessionId: Long) = sessions.deleteSession(sessionId)

    // ---- history screens ----

    /** The History screens deleted their copy of a session. */
    suspend fun deleteByLegacySession(legacySessionId: Long) = sessions.deleteByLegacySession(legacySessionId)

    /** The History screens cleared one workout's history. */
    suspend fun deleteCompletedForWorkout(workoutId: Long) = sessions.deleteCompletedForWorkout(workoutId)

    // ---- history ----

    /** Rows of the most recent completed session of this workout (for the "Previous:" lines). */
    suspend fun previousSessionRows(workoutId: Long): List<SetRow> {
        val last = sessions.lastCompletedForWorkout(workoutId) ?: return emptyList()
        return SetTree.ordered(sessions.setsFor(last.id).map { it.toRow() })
    }

    /**
     * Completed sessions containing the exercise (the latest of this workout, then the latest of
     * any workout), with only that exercise's sets. Feed to SuggestionEngine.findSource.
     */
    suspend fun suggestionHistory(exerciseId: Long, workoutId: Long, limitEach: Int = 10): List<SessionHistory> {
        val found = (sessions.completedWithExerciseForWorkout(exerciseId, workoutId, limitEach) +
            sessions.completedWithExercise(exerciseId, limitEach)).distinctBy { it.id }
        if (found.isEmpty()) return emptyList()
        val setsBySession = sessions.setsForExercise(exerciseId, found.map { it.id }).groupBy { it.sessionId }
        return found.mapNotNull { s ->
            val finished = s.finishedAt ?: return@mapNotNull null
            SessionHistory(
                workoutId = s.workoutId,
                finishedAt = finished,
                bodyweightKg = s.bodyweightKgSnapshot,
                sets = setsBySession[s.id].orEmpty().map { it.toRow().toEntry() }
            )
        }
    }

    // ---- legacy mirror ----

    /** One old-style exercise (with one set per row) for every distinct exercise in the template. */
    private suspend fun mirrorTemplate(workoutId: Long, rows: List<SetRow>) {
        val old = legacy ?: return
        if (old.getWorkoutWithExercises(workoutId) == null) return
        val used = SetTree.dropUnsetRows(rows)
        val byId = exercisesById(used.mapNotNull { it.exerciseId })
        old.deleteExercisesForWorkout(workoutId)
        used.groupBy { it.exerciseId }.entries.forEachIndexed { index, (exerciseId, list) ->
            val ex = byId[exerciseId] ?: return@forEachIndexed
            val mode = if (ex.type == ExerciseType.TIME_HELD) SetMode.TIME else SetMode.REPS
            val id = old.insertExercise(ExerciseEntity(WorkoutId = workoutId, name = ex.name, orderIndex = index))
            old.insertExerciseSets(
                list.mapIndexed { i, _ -> ExerciseSetEntity(exerciseId = id, mode = mode, orderIndex = i) }
            )
        }
    }

    /** Writes the finished session into the history tables and remembers the link. */
    private suspend fun mirrorSession(session: LoggedSession) {
        val old = legacy ?: return
        val finishedAt = session.finishedAt ?: return
        val workout = old.getWorkoutWithExercises(session.workoutId)?.Workout ?: return
        val rows = SetTree.ordered(sessions.setsFor(session.id).map { it.toRow() })
        val byId = exercisesById(rows.mapNotNull { it.exerciseId })
        val legacyId = old.insertSession(
            SessionEntity(
                WorkoutId = workout.id,
                WorkoutName = workout.name,
                startedAt = session.startedAt,
                finishedAt = finishedAt
            )
        )
        val logged = rows
            .filter { it.reps != null || it.durationSec != null }
            .mapNotNull { r ->
                val ex = byId[r.exerciseId] ?: return@mapNotNull null
                val timed = ex.type == ExerciseType.TIME_HELD
                LoggedSetEntity(
                    sessionId = legacyId,
                    exerciseName = ex.name,
                    mode = if (timed) SetMode.TIME else SetMode.REPS,
                    value = (if (timed) r.durationSec else r.reps) ?: 0,
                    weight = r.weightKg ?: 0.0,
                    rpe = r.rpe,
                    orderIndex = 0
                )
            }
            .mapIndexed { i, e -> e.copy(orderIndex = i) }
        if (logged.isNotEmpty()) old.insertLoggedSets(logged)
        sessions.setLegacySessionId(session.id, legacyId)
    }
}
