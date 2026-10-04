package com.ashrdev.aznd.data.workout

import android.content.Context
import com.ashrdev.aznd.domain.ExerciseSeedParser

const val SEED_ASSET = "exercise_seed.txt"

/**
 * Loads app/src/main/assets/exercise_seed.txt into the catalog. Call [seedMissing] on every app
 * start (off the main thread): it only inserts lines whose key is not in the table yet, so new
 * catalog lines show up after an app update and existing rows (and their ids) are never touched.
 */
class ExerciseSeeder(
    private val dao: ExerciseDao,
    private val readSeedText: () -> String,
    /** Gets the messages for any catalog lines that were skipped, e.g. to log them. */
    private val onSkippedLines: (List<String>) -> Unit = {}
) {
    /** Returns how many exercises were added. */
    suspend fun seedMissing(): Int {
        val parsed = ExerciseSeedParser.parse(readSeedText())
        if (parsed.errors.isNotEmpty()) onSkippedLines(parsed.errors)
        val existing = dao.seedKeys().toHashSet()
        val missing = parsed.exercises
            .filter { it.seedKey !in existing }
            .map { it.toExercise() }
        if (missing.isEmpty()) return 0
        return dao.insertIgnore(missing).count { it != -1L }
    }
}

fun assetSeedReader(context: Context, fileName: String = SEED_ASSET): () -> String = {
    context.assets.open(fileName).bufferedReader().use { it.readText() }
}
