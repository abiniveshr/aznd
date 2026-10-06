package com.ashrdev.aznd.domain

/** One parsed catalog line. [seedKey] is the slug of the name and is what identifies it across app starts. */
data class SeedExercise(
    val seedKey: String,
    val name: String,
    val type: ExerciseType,
    val primaryMuscle: Muscle,
    val secondaryMuscles: List<Muscle>,
    val bodyweightShare: Double
)

/** Good lines plus a human-readable message for every line that was skipped. */
data class SeedParseResult(
    val exercises: List<SeedExercise>,
    val errors: List<String>
)

/** "Pull-Up" -> "pull-up", "T-Bar Row" -> "t-bar-row". Stable key for catalog lines. */
fun slugify(name: String): String =
    name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

/**
 * Parser for exercise_seed.txt. One exercise per line:
 *   name|type|primaryMuscle|secondaryMuscles|bodyweightSharePercent
 * Blank lines and lines starting with '#' are ignored. A bad line is reported and skipped,
 * it never stops the rest of the file from loading.
 */
object ExerciseSeedParser {

    fun parse(text: String): SeedParseResult {
        val exercises = ArrayList<SeedExercise>()
        val errors = ArrayList<String>()
        val seen = HashSet<String>()
        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            try {
                val exercise = parseLine(line)
                if (seen.add(exercise.seedKey)) {
                    exercises += exercise
                } else {
                    errors += "line ${index + 1}: duplicate key '${exercise.seedKey}'"
                }
            } catch (e: IllegalArgumentException) {
                errors += "line ${index + 1}: ${e.message}"
            }
        }
        return SeedParseResult(exercises, errors)
    }

    /** Throws IllegalArgumentException with a readable message if the line is not valid. */
    fun parseLine(line: String): SeedExercise {
        val fields = line.split("|").map { it.trim() }
        require(fields.size in 3..5) { "expected 3 to 5 fields separated by '|', got ${fields.size}" }

        val name = fields[0]
        val key = slugify(name)
        require(key.isNotEmpty()) { "name has no letters or digits" }

        val type = when (fields[1]) {
            "W" -> ExerciseType.WEIGHTED
            "B" -> ExerciseType.BODYWEIGHT_REPS
            "A" -> ExerciseType.ASSISTED_BODYWEIGHT
            "T" -> ExerciseType.TIME_HELD
            else -> throw IllegalArgumentException("unknown type code '${fields[1]}' (use W, B, A or T)")
        }

        val primary = muscle(fields[2])
        val secondary = fields.getOrElse(3) { "" }
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
            .map { muscle(it) }
        require(secondary.size < Muscle.COUNT) { "too many secondary muscles" }
        require(secondary.toSet().size == secondary.size && primary !in secondary) {
            "a muscle is listed twice"
        }

        val percent = fields.getOrElse(4) { "" }
        val share = if (type.isBodyweight) {
            if (percent.isEmpty()) 1.0 else shareFromPercent(percent)
        } else {
            require(percent.isEmpty()) { "bodyweight share is only for types B and A" }
            1.0
        }
        return SeedExercise(key, name, type, primary, secondary, share)
    }

    private fun muscle(name: String): Muscle =
        Muscle.values().firstOrNull { it.name == name.trim().uppercase() }
            ?: throw IllegalArgumentException("unknown muscle '$name'")

    private fun shareFromPercent(text: String): Double {
        val percent = text.toIntOrNull() ?: throw IllegalArgumentException("bad share '$text'")
        require(percent in 1..100) { "share must be 1 to 100, got $percent" }
        return percent / 100.0
    }
}
