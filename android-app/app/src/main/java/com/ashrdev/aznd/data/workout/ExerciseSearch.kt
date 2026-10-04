package com.ashrdev.aznd.data.workout

import com.ashrdev.aznd.domain.Muscle

const val PICKER_MAX_RESULTS = 30

/** Lower-case words a user may type for a muscle: "upper back", "upper_back", "UBK". */
private fun Muscle.keys(): List<String> =
    listOf(name.lowercase(), name.lowercase().replace('_', ' '), badgeCode.lowercase())

/**
 * Picker filter: blank query = first [limit] by name; otherwise every word of the query must
 * appear in the exercise name OR match one of its muscles (primary or secondary). Name matches
 * that START with the query come first, then primary-muscle matches, then the rest, each A-Z.
 */
fun searchExercises(all: List<Exercise>, query: String, limit: Int = PICKER_MAX_RESULTS): List<Exercise> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return all.sortedBy { it.name.lowercase() }.take(limit)
    val words = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
    fun matches(e: Exercise): Boolean {
        val name = e.name.lowercase()
        val muscles = (listOf(e.primaryMuscle) + e.secondaryMuscles).flatMap { it.keys() }
        return words.all { w -> name.contains(w) || muscles.any { it.startsWith(w) } } ||
            (e.primaryMuscle.keys() + e.secondaryMuscles.flatMap { it.keys() }).any { it == q }
    }
    fun rank(e: Exercise): Int = when {
        e.name.lowercase().startsWith(q) -> 0
        e.primaryMuscle.keys().any { it.startsWith(q) } -> 1
        else -> 2
    }
    return all.filter(::matches)
        .sortedWith(compareBy<Exercise>({ rank(it) }, { it.name.lowercase() }))
        .take(limit)
}
