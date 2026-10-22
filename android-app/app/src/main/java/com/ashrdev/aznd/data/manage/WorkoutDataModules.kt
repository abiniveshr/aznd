package com.ashrdev.aznd.data.manage

import com.ashrdev.aznd.data.AppDatabase
import com.ashrdev.aznd.data.workout.EMPTY_WORKOUT_ID
import com.ashrdev.aznd.data.workout.LoggedSession
import com.ashrdev.aznd.data.workout.LoggedSet
import com.ashrdev.aznd.data.workout.LoggedSetEntity
import com.ashrdev.aznd.data.workout.SessionEntity
import com.ashrdev.aznd.data.workout.SetMode
import com.ashrdev.aznd.data.workout.TrainingRepository
import com.ashrdev.aznd.data.workout.WorkoutEntity
import com.ashrdev.aznd.domain.workout.SetKind
import com.ashrdev.aznd.domain.workout.SetRow
import com.ashrdev.aznd.ui.common.DayRange
import org.json.JSONArray
import org.json.JSONObject

class WorkoutRoutinesModule(
    private val db: AppDatabase,
    private val training: TrainingRepository
) : DataModule {
    override val id = "workout_routines"
    override val app = DataApp.WORKOUTS
    override val title = "Workout routines"
    override val description = "Your saved workouts with their exercises, sets and rep targets"
    override val unit = "routines"
    override val deleteNote = "A workout that is running right now is discarded. Logged history is kept."
    override val canExport = true
    override val canImport = true

    private val dao get() = db.manageDao()

    override suspend fun count(range: DayRange): Int = dao.countWorkouts()

    override suspend fun delete(range: DayRange) = dao.deleteAllRoutines()

    override suspend fun export(range: DayRange): ModuleExport {
        val workouts = dao.workouts()
        val writer = ExerciseRefWriter(db.exerciseDao().allOnce().associateBy { it.id })
        val routines = JSONArray()
        for (workout in workouts) {
            val rows = JSONArray()
            for (row in training.templateRows(workout.id)) {
                val exercise = writer.indexOf(row.exerciseId) ?: continue
                rows.put(
                    JSONObject()
                        .put("id", row.id)
                        .put("position", row.position)
                        .put("exercise", exercise)
                        .put("kind", row.kind.name)
                        .put("parentId", row.parentId)
                        .put("targetMin", row.targetMin)
                        .put("targetMax", row.targetMax)
                )
            }
            routines.put(JSONObject().put("name", workout.name).put("rows", rows))
        }
        return ModuleExport(
            workouts.size,
            JSONObject().put("exercises", writer.toJson()).put("routines", routines)
        )
    }

    override suspend fun import(data: JSONObject, version: Int): ImportOutcome {
        val reader = ExerciseRefReader(db.exerciseDao(), data.arrayOrEmpty("exercises"))
        var added = 0
        var skipped = 0
        for (routine in data.arrayOrEmpty("routines").mapObjects { it }) {
            val name = routine.stringOrNull("name")?.trim().orEmpty()
            if (name.isEmpty() || dao.workoutNamed(name) != null) {
                skipped++
                continue
            }
            val workoutId = db.workoutDao().insertWorkout(WorkoutEntity(name = name))
            val rows = ArrayList<SetRow>()
            for (row in routine.arrayOrEmpty("rows").mapObjects { it }) {
                val exercise = reader.resolve(row.intOrNull("exercise")) ?: continue
                rows += SetRow(
                    id = row.getLong("id"),
                    position = row.optInt("position"),
                    exerciseId = exercise.id,
                    kind = enumOrNull<SetKind>(row.stringOrNull("kind")) ?: SetKind.NORMAL,
                    parentId = row.longOrNull("parentId"),
                    targetMin = row.intOrNull("targetMin"),
                    targetMax = row.intOrNull("targetMax")
                )
            }
            training.saveTemplate(workoutId, rows)
            added++
        }
        val notes = if (reader.created > 0) listOf("${reader.created} missing exercises were added as custom exercises") else emptyList()
        return ImportOutcome(added, skipped, notes)
    }
}

class WorkoutHistoryModule(
    private val db: AppDatabase,
    private val photos: PhotoFiles
) : DataModule {
    override val id = "workout_history"
    override val app = DataApp.WORKOUTS
    override val title = "Workout history"
    override val description = "Every finished workout with its logged sets, weights, reps and photos"
    override val unit = "sessions"
    override val supportsDateRange = true
    override val dateRangeNote = "by the day the workout started"
    override val deleteNote = "A workout that is running right now is not touched. Routines are kept."
    override val canExport = true
    override val canImport = true

    private val dao get() = db.manageDao()

    override suspend fun count(range: DayRange): Int =
        dao.countLegacySessions(range.fromMillis, range.toMillisExclusive) +
            dao.countUnlinkedLoggedSessions(range.fromMillis, range.toMillisExclusive)

    override suspend fun delete(range: DayRange) {
        val from = range.fromMillis
        val to = range.toMillisExclusive
        val uris = dao.legacyPhotoUris(from, to) + dao.loggedPhotoUris(from, to)
        dao.deleteHistory(from, to)
        photos.delete(uris)
    }

    override suspend fun export(range: DayRange): ModuleExport {
        val from = range.fromMillis
        val to = range.toMillisExclusive
        val legacy = dao.legacySessions(from, to)
        val legacySets = dao.legacySets(from, to).groupBy { it.sessionId }
        val logged = dao.finishedLoggedSessions(from, to)
        val loggedSets = dao.loggedSetsOfFinished(from, to).groupBy { it.sessionId }
        val writer = ExerciseRefWriter(db.exerciseDao().allOnce().associateBy { it.id })

        val legacyNames = legacy.associate { it.id to it.workoutName }
        val routineNames = HashMap<Long, String?>()
        for (workoutId in logged.map { it.workoutId }.distinct()) {
            if (workoutId != EMPTY_WORKOUT_ID) routineNames[workoutId] = dao.workoutName(workoutId)
        }

        val sessions = legacy.toJsonArray { s ->
            JSONObject()
                .put("id", s.id)
                .put("workoutName", s.workoutName)
                .put("startedAt", s.startedAt)
                .put("finishedAt", s.finishedAt)
                .put("sets", legacySets[s.id].orEmpty().toJsonArray { set ->
                    JSONObject()
                        .put("exerciseName", set.exerciseName)
                        .put("mode", set.mode.name)
                        .put("value", set.value)
                        .put("weight", set.weight)
                        .put("rpe", set.rpe)
                        .put("orderIndex", set.orderIndex)
                })
        }

        val loggedJson = logged.toJsonArray { s ->
            val name = s.legacySessionId?.let { legacyNames[it] } ?: routineNames[s.workoutId]
            val sets = JSONArray()
            for (set in loggedSets[s.id].orEmpty()) {
                val exercise = writer.indexOf(set.exerciseId) ?: continue
                sets.put(
                    JSONObject()
                        .put("id", set.id)
                        .put("position", set.position)
                        .put("exercise", exercise)
                        .put("kind", set.kind.name)
                        .put("parentId", set.parentSetId)
                        .put("targetMin", set.targetMin)
                        .put("targetMax", set.targetMax)
                        .put("weightKg", set.weightKg)
                        .put("reps", set.reps)
                        .put("durationSec", set.durationSec)
                        .put("rpe", set.rpe)
                )
            }
            JSONObject()
                .put("empty", s.workoutId == EMPTY_WORKOUT_ID)
                .put("workoutName", name)
                .put("startedAt", s.startedAt)
                .put("finishedAt", s.finishedAt)
                .put("bodyweightKg", s.bodyweightKgSnapshot)
                .put("legacySessionId", s.legacySessionId)
                .put("sets", sets)
        }

        val count = legacy.size + logged.count { it.legacySessionId == null }
        return ModuleExport(
            count,
            JSONObject()
                .put("exercises", writer.toJson())
                .put("sessions", sessions)
                .put("loggedSessions", loggedJson)
        )
    }

    override suspend fun import(data: JSONObject, version: Int): ImportOutcome {
        val reader = ExerciseRefReader(db.exerciseDao(), data.arrayOrEmpty("exercises"))
        val workoutIds = HashMap<String, Long>()

        suspend fun workoutIdFor(name: String): Long = workoutIds.getOrPut(name.lowercase()) {
            dao.workoutNamed(name)?.id ?: db.workoutDao().insertWorkout(WorkoutEntity(name = name))
                .also { dao.deleteWorkoutRow(it) }
        }

        var added = 0
        var skipped = 0
        val fileLegacyIds = HashSet<Long>()
        val legacyIdMap = HashMap<Long, Long>()

        for (s in data.arrayOrEmpty("sessions").mapObjects { it }) {
            val oldId = s.getLong("id")
            fileLegacyIds += oldId
            val name = s.stringOrNull("workoutName")?.trim().orEmpty().ifEmpty { "Imported workout" }
            val startedAt = s.getLong("startedAt")
            if (dao.legacySessionExists(startedAt, name) > 0) {
                skipped++
                continue
            }
            val newId = db.workoutDao().insertSession(
                SessionEntity(
                    workoutId = workoutIdFor(name),
                    workoutName = name,
                    startedAt = startedAt,
                    finishedAt = s.getLong("finishedAt")
                )
            )
            val sets = s.arrayOrEmpty("sets").mapObjects { set ->
                LoggedSetEntity(
                    sessionId = newId,
                    exerciseName = set.getString("exerciseName"),
                    mode = enumOrNull<SetMode>(set.stringOrNull("mode")) ?: SetMode.REPS,
                    value = set.optInt("value"),
                    weight = set.optDouble("weight", 0.0),
                    rpe = set.doubleOrNull("rpe"),
                    orderIndex = set.optInt("orderIndex")
                )
            }
            if (sets.isNotEmpty()) db.workoutDao().insertLoggedSets(sets)
            legacyIdMap[oldId] = newId
            added++
        }

        for (s in data.arrayOrEmpty("loggedSessions").mapObjects { it }) {
            val oldLegacy = s.longOrNull("legacySessionId")
            var newLegacy: Long? = null
            if (oldLegacy != null && oldLegacy in fileLegacyIds) {
                newLegacy = legacyIdMap[oldLegacy] ?: continue
            }
            val startedAt = s.getLong("startedAt")
            val workoutId = if (s.optBoolean("empty")) EMPTY_WORKOUT_ID
            else workoutIdFor(s.stringOrNull("workoutName")?.trim().orEmpty().ifEmpty { "Imported workout" })
            if (newLegacy == null) {
                if (dao.loggedSessionExists(startedAt, workoutId) > 0) {
                    skipped++
                    continue
                }
            }
            val sessionId = db.sessionDao().insertSession(
                LoggedSession(
                    workoutId = workoutId,
                    startedAt = startedAt,
                    finishedAt = s.longOrNull("finishedAt"),
                    bodyweightKgSnapshot = s.optDouble("bodyweightKg", 75.0),
                    legacySessionId = newLegacy
                )
            )
            insertSets(sessionId, s.arrayOrEmpty("sets").mapObjects { it }, reader)
            if (newLegacy == null) added++
        }

        val notes = if (reader.created > 0) listOf("${reader.created} missing exercises were added as custom exercises") else emptyList()
        return ImportOutcome(added, skipped, notes)
    }

    private suspend fun insertSets(sessionId: Long, sets: List<JSONObject>, reader: ExerciseRefReader) {
        val idMap = HashMap<Long, Long>()
        var pending = sets
        while (pending.isNotEmpty()) {
            val ready = pending.filter { set ->
                val parent = set.longOrNull("parentId")
                parent == null || idMap.containsKey(parent)
            }
            val batch = ready.ifEmpty { pending }
            for (set in batch) {
                val exercise = reader.resolve(set.intOrNull("exercise"))
                if (exercise != null) {
                    val parent = set.longOrNull("parentId")?.let { idMap[it] }
                    val newId = db.sessionDao().insertSet(
                        LoggedSet(
                            sessionId = sessionId,
                            position = set.optInt("position"),
                            exerciseId = exercise.id,
                            kind = enumOrNull<SetKind>(set.stringOrNull("kind")) ?: SetKind.NORMAL,
                            parentSetId = parent,
                            targetMin = set.intOrNull("targetMin"),
                            targetMax = set.intOrNull("targetMax"),
                            weightKg = set.doubleOrNull("weightKg"),
                            reps = set.intOrNull("reps"),
                            durationSec = set.intOrNull("durationSec"),
                            rpe = set.doubleOrNull("rpe")
                        )
                    )
                    idMap[set.getLong("id")] = newId
                }
            }
            pending = pending - batch.toSet()
        }
    }
}

class CustomExercisesModule(
    private val db: AppDatabase,
    private val training: TrainingRepository,
    private val photos: PhotoFiles
) : DataModule {
    override val id = "custom_exercises"
    override val app = DataApp.WORKOUTS
    override val title = "Custom exercises"
    override val description = "Exercises you created yourself"
    override val unit = "exercises"
    override val deleteNote = "They are also removed from every routine and from workout history."
    override val canExport = true
    override val canImport = true

    private suspend fun customs() = db.exerciseDao().allOnce().filter { it.isCustom }

    override suspend fun count(range: DayRange): Int = customs().size

    override suspend fun delete(range: DayRange) {
        for (exercise in customs()) {
            training.deleteCustomExercise(exercise.id)?.let { photos.deletePath(it.photoPath) }
        }
    }

    override suspend fun export(range: DayRange): ModuleExport {
        val list = customs()
        return ModuleExport(list.size, JSONObject().put("exercises", list.toJsonArray { it.toRefJson() }))
    }

    override suspend fun import(data: JSONObject, version: Int): ImportOutcome {
        val reader = ExerciseRefReader(db.exerciseDao(), data.arrayOrEmpty("exercises"))
        for (i in 0 until reader.size) reader.resolve(i)
        return ImportOutcome(reader.created, reader.size - reader.created)
    }
}
