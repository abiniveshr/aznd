package com.ashrdev.aznd.data.manage

import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.data.workout.ExerciseDao
import com.ashrdev.aznd.domain.workout.ExerciseType
import com.ashrdev.aznd.domain.workout.Muscle
import org.json.JSONArray
import org.json.JSONObject

internal fun Exercise.toRefJson(): JSONObject = JSONObject()
    .put("seedKey", seedKey)
    .put("name", name)
    .put("type", type.name)
    .put("primaryMuscle", primaryMuscle.name)
    .put("secondaryMuscles", JSONArray(secondaryMuscles.map { it.name }))
    .put("bodyweightShare", bodyweightShare)
    .put("isCustom", isCustom)

internal class ExerciseRefWriter(private val byId: Map<Long, Exercise>) {
    private val index = LinkedHashMap<Long, Int>()

    fun indexOf(exerciseId: Long?): Int? {
        if (exerciseId == null || byId[exerciseId] == null) return null
        return index.getOrPut(exerciseId) { index.size }
    }

    fun toJson(): JSONArray = JSONArray().also { array ->
        index.keys.forEach { id -> array.put(byId.getValue(id).toRefJson()) }
    }
}

internal class ExerciseRefReader(private val dao: ExerciseDao, refs: JSONArray) {
    private val defs: List<JSONObject> = refs.mapObjects { it }
    private val resolved = HashMap<Int, Exercise>()
    private var bySeed: HashMap<String, Exercise>? = null
    private var byName: HashMap<String, Exercise>? = null

    var created = 0
        private set

    val size: Int get() = defs.size

    private suspend fun load() {
        if (bySeed != null) return
        val all = dao.allOnce()
        bySeed = HashMap<String, Exercise>().also { m -> all.forEach { e -> e.seedKey?.let { m[it] = e } } }
        byName = HashMap<String, Exercise>().also { m -> all.forEach { e -> m[e.name.lowercase()] = e } }
    }

    suspend fun resolve(index: Int?): Exercise? {
        if (index == null || index !in defs.indices) return null
        resolved[index]?.let { return it }
        load()
        val def = defs[index]
        val name = def.stringOrNull("name")?.trim().orEmpty()
        if (name.isEmpty()) return null
        val found = def.stringOrNull("seedKey")?.let { bySeed!![it] } ?: byName!![name.lowercase()]
        val exercise = found ?: createCustom(def, name)
        resolved[index] = exercise
        return exercise
    }

    private suspend fun createCustom(def: JSONObject, name: String): Exercise {
        val draft = Exercise(
            name = name,
            type = enumOrNull<ExerciseType>(def.stringOrNull("type")) ?: ExerciseType.WEIGHTED,
            primaryMuscle = enumOrNull<Muscle>(def.stringOrNull("primaryMuscle")) ?: Muscle.CHEST,
            secondaryMuscles = def.arrayOrEmpty("secondaryMuscles")
                .let { a -> List(a.length()) { a.getString(it) } }
                .mapNotNull { enumOrNull<Muscle>(it) },
            bodyweightShare = (def.doubleOrNull("bodyweightShare") ?: 1.0).coerceIn(0.0, 1.0),
            isCustom = true
        )
        val saved = draft.copy(id = dao.insert(draft))
        byName!![name.lowercase()] = saved
        created++
        return saved
    }
}
