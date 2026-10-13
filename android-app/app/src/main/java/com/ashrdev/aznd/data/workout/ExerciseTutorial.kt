package com.ashrdev.aznd.data.workout

import android.content.Context
import org.json.JSONObject

const val TUTORIAL_ASSET = "tutorials.json"

data class ExerciseTutorial(
    val steps: List<String>,
    val frames: List<String>,
    val credit: String? = null
)

fun parseTutorials(text: String): Map<String, ExerciseTutorial> {
    val root = JSONObject(text)
    val result = HashMap<String, ExerciseTutorial>()
    val keys = root.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val node = root.optJSONObject(key) ?: continue
        val steps = node.optJSONArray("steps")
        val frames = node.optJSONArray("frames")
        result[key] = ExerciseTutorial(
            steps = List(steps?.length() ?: 0) { steps!!.getString(it) },
            frames = List(frames?.length() ?: 0) { frames!!.getString(it) },
            credit = node.optString("credit").takeIf { it.isNotBlank() }
        )
    }
    return result
}

object TutorialStore {
    private var cache: Map<String, ExerciseTutorial>? = null

    @Synchronized
    fun forExercise(context: Context, seedKey: String?): ExerciseTutorial? {
        if (seedKey == null) return null
        val all = cache ?: runCatching {
            parseTutorials(context.assets.open(TUTORIAL_ASSET).bufferedReader().use { it.readText() })
        }.getOrDefault(emptyMap()).also { cache = it }
        return all[seedKey]?.takeIf { it.steps.isNotEmpty() || it.frames.isNotEmpty() }
    }
}
