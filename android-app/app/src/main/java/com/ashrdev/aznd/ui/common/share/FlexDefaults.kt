package com.ashrdev.aznd.ui.common.share

import android.content.Context
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

// ---------------------------------------------------------------------------------------------
// Defaults: one "blueprint" per app screen that has a Share button. A blueprint is a FlexSpec the person designs on
// sample data; sharing from the screen fills it with that screen's data (see [prepareScreenShare]).
// In a blueprint every Photo card is a slot: "the screen's photo #n" (the n-th photo card that is on, in card order); its
// own keys are only what the editor draws (a sample picture).
// ---------------------------------------------------------------------------------------------

/** The screens with a Share button that have a blueprint, in Defaults-page order. */
val FLEX_DEFAULT_SOURCES: List<FlexPreset> =
    listOf(FlexPreset.WORKOUT, FlexPreset.ROUTINE, FlexPreset.EXERCISE, FlexPreset.STREAK_DAY)

/** The name of the screen's entry on the Defaults page. */
fun FlexPreset.defaultsName(): String = when (this) {
    FlexPreset.HOME -> "Home"
    FlexPreset.WORKOUT -> "Workout"
    FlexPreset.ROUTINE -> "Routine"
    FlexPreset.EXERCISE -> "Exercise"
    FlexPreset.STREAK_DAY -> "Streak day"
}

/** The card types this screen's blueprint may hold: the screen's own list plus Title and Text. */
fun FlexPreset.allowedTypes(): Set<FlexBlockType> = when (this) {
    FlexPreset.HOME -> setOf(FlexBlockType.HEADER, FlexBlockType.STREAKS, FlexBlockType.WORKOUT_STATS, FlexBlockType.PHOTO, FlexBlockType.TEXT)
    FlexPreset.WORKOUT -> setOf(FlexBlockType.HEADER, FlexBlockType.WORKOUT_STATS, FlexBlockType.EXERCISES, FlexBlockType.PHOTO, FlexBlockType.TEXT)
    FlexPreset.ROUTINE -> setOf(FlexBlockType.HEADER, FlexBlockType.ROUTINE, FlexBlockType.TEXT)
    FlexPreset.EXERCISE -> setOf(FlexBlockType.HEADER, FlexBlockType.WORKOUT_STATS, FlexBlockType.GRAPH, FlexBlockType.TEXT)
    FlexPreset.STREAK_DAY -> setOf(FlexBlockType.HEADER, FlexBlockType.PHOTO, FlexBlockType.DAY_NOTE, FlexBlockType.STREAKS, FlexBlockType.TEXT)
}

/** The stats a Workout stats card of this screen's blueprint may show (empty when the screen has no such card). */
fun FlexPreset.allowedStats(): List<FlexWorkoutStat> = when (this) {
    FlexPreset.WORKOUT -> listOf(FlexWorkoutStat.SESSION, FlexWorkoutStat.TOP_SET)
    FlexPreset.EXERCISE -> listOf(FlexWorkoutStat.ONE_RM, FlexWorkoutStat.TOP_SET)
    else -> emptyList()
}

/** Saved blueprints, one JSON string per screen (own file: the old per-source card store is not reused, so nothing stale is read). */
object FlexDefaultsStore {
    private const val FILE = "flex_defaults"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun key(source: FlexPreset) = "blueprint_${source.id}"

    /** The saved blueprint, else the built-in one. */
    suspend fun load(context: Context, source: FlexPreset): FlexSpec =
        saved(context, source) ?: builtIn(context, source)

    private fun saved(context: Context, source: FlexPreset): FlexSpec? {
        val raw = prefs(context).getString(key(source), null) ?: return null
        val spec = runCatching { specFromJson(JSONObject(raw)) }.getOrNull() ?: return null
        val allowed = source.allowedTypes()
        val kept = spec.blocks.filter { it.type in allowed }
        return (if (kept.size == spec.blocks.size) spec else spec.copy(blocks = kept)).reconciled()
    }

    fun save(context: Context, source: FlexPreset, spec: FlexSpec) {
        prefs(context).edit().putString(key(source), specToJson(spec).toString()).apply()
    }

    /** The hand-designed blueprint the screen ships with ([flexBuiltInBlueprint]): explicit cards, one photo slot at most, no layout (auto align decides). */
    @Suppress("UNUSED_PARAMETER")
    suspend fun builtIn(context: Context, source: FlexPreset): FlexSpec = flexBuiltInBlueprint(source)

    fun reset(context: Context, source: FlexPreset) {
        prefs(context).edit().remove(key(source)).apply()
    }
}

// ---------------------------------------------------------------------------------------------
// Sharing from a screen
// ---------------------------------------------------------------------------------------------

/** The use screen's starting point when sharing from a screen: the poster spec and the data choices behind it. */
data class FlexScreenShare(val spec: FlexSpec, val bindings: FlexBindings)

/**
 * The use screen's starting point when sharing from a screen: bindings from the share context only (no remembered values),
 * card text / graph / calendar from the blueprint, and the blueprint with every card the item has no data for turned off.
 *
 * Cards the blueprint has off stay off, but get their bindings too, so the use screen can turn them on with data behind them.
 */
suspend fun prepareScreenShare(
    context: Context,
    blueprint: FlexSpec,
    share: FlexShareContext,
    library: FlexLibrary,
    workout: WorkoutViewModel,
    streaks: StreakViewModel
): FlexScreenShare {
    val source = share.source
    val wasOn = blueprint.blocks.associate { it.id to it.enabled }
    // Every card (except the 3D model) is bound as if on, so a card the blueprint or the data turned off has its data ready.
    val all = blueprint.copy(blocks = blueprint.blocks.filter { it.type != FlexBlockType.MODEL }.map { it.copy(enabled = true) })
    val base = defaultBindings(all, library, emptyMap(), share)
    val exerciseName = if (source == FlexPreset.EXERCISE) library.templateExerciseNames[share.b]?.takeIf { it.isNotBlank() } else null

    // The item's pictures in order: a workout's photo; a streak day's photo, then the streak's other pictures (as FlexContentLoader).
    val photos = withContext(Dispatchers.Default) { itemPhotos(share, workout, streaks) }
    val photoBlocks = all.blocks.filter { it.type == FlexBlockType.PHOTO }
    // Slots: the blueprint's photo cards that are on take the pictures first, the cards that are off the rest.
    val slotOrder = photoBlocks.filter { wasOn[it.id] == true } + photoBlocks.filter { wasOn[it.id] != true }
    val photoOf = HashMap<Int, String?>()
    slotOrder.forEachIndexed { i, b -> photoOf[b.id] = photos.getOrNull(i) }

    val bindings = LinkedHashMap<Int, FlexBinding>()
    val blocks = all.blocks.map { b ->
        var v = base[b.id] ?: FlexBinding()
        var nb = b
        when (b.type) {
            // The card's own text; a Title with none shows the item's name (what the defaults hold).
            FlexBlockType.HEADER -> v = v.copy(text = b.text.ifBlank { v.text.orEmpty() }.ifBlank { null })
            FlexBlockType.TEXT -> v = v.copy(text = b.text)
            FlexBlockType.GRAPH -> {
                val vars = b.graph.variables.mapNotNull { id ->
                    if (id != FLEX_SCREEN_EXERCISE_1RM) id
                    else exerciseName?.let { FlexGraphLoader.exerciseId(it) }?.takeIf { e -> library.graphs.any { it.id == e } }
                }
                val g = b.graph.copy(variables = vars)
                nb = b.copy(graph = g)
                v = v.copy(graph = g)
            }
            FlexBlockType.CALENDAR -> v = v.copy(cal = b.cal)
            FlexBlockType.PHOTO -> {
                val uri = photoOf[b.id]
                v = v.copy(photoUri = uri)
                nb = b.copy(keys = listOf(uri ?: FLEX_NO_PHOTO), text = "")
            }
            else -> {}
        }
        // The item is the screen's own, whether or not the library lists it.
        when (source) {
            FlexPreset.WORKOUT -> v = v.copy(sessionId = share.a)
            FlexPreset.ROUTINE -> v = v.copy(routineId = share.a)
            FlexPreset.EXERCISE -> if (b.type == FlexBlockType.WORKOUT_STATS) v = v.copy(exerciseNames = exerciseName?.let { listOf(it) })
            FlexPreset.STREAK_DAY -> when (b.type) {
                FlexBlockType.STREAKS -> v = v.copy(streakIds = listOf(share.a))
                FlexBlockType.DAY_NOTE -> v = v.copy(noteStreakId = share.a, dayKey = share.s.takeIf { it.isNotBlank() })
                else -> {}
            }
            FlexPreset.HOME -> {}
        }
        bindings[b.id] = v
        nb
    }
    val bound = all.copy(blocks = blocks)
    val content = loadBoundContent(context, bound, bindings, library, workout, streaks)

    val finalBlocks = blocks.map { b ->
        val v = bindings.getValue(b.id)
        val part = content.forBlock(b.id)
        val hasData = when (b.type) {
            FlexBlockType.HEADER -> true
            FlexBlockType.TEXT -> b.text.isNotBlank()
            FlexBlockType.WORKOUT_STATS ->
                if (b.stat == FlexWorkoutStat.SESSION) part.stats.isNotEmpty()
                else part.lifts.isNotEmpty() && (source != FlexPreset.EXERCISE || exerciseName != null)
            FlexBlockType.EXERCISES -> part.exercises.isNotEmpty()
            FlexBlockType.ROUTINE -> part.routine.isNotEmpty()
            FlexBlockType.STREAKS -> !v.streakIds.isNullOrEmpty() && part.streaks.isNotEmpty()
            FlexBlockType.GRAPH -> hasGraphData(b.graph, content)
            FlexBlockType.DAY_NOTE -> part.dayNote.isNotBlank()
            FlexBlockType.PHOTO -> v.photoUri != null
            FlexBlockType.CALENDAR -> part.calendars.isNotEmpty()
            FlexBlockType.MODEL -> false
        }
        b.copy(enabled = wasOn[b.id] == true && hasData)
    }
    // The 3D model never comes along; everything else keeps its place in the blueprint (and its tile in the layout).
    val spec = blueprint.copy(blocks = blueprint.blocks.map { orig -> finalBlocks.firstOrNull { it.id == orig.id } ?: orig.copy(enabled = false) })
    return FlexScreenShare(spec, bindings)
}

/** True when one of the graph's variables has points in its range. */
private fun hasGraphData(g: FlexGraphConfig, content: FlexContent): Boolean {
    val cutoff = g.range.days?.let { System.currentTimeMillis() - it * 86_400_000L }
    return g.variables.any { id ->
        content.graphs.firstOrNull { it.id == id }?.points?.any { cutoff == null || it.timeMs >= cutoff } == true
    }
}

/** The pictures of the shared item, in order (distinct uris). Empty for a routine and an exercise. */
private suspend fun itemPhotos(share: FlexShareContext, workout: WorkoutViewModel, streaks: StreakViewModel): List<String> = when (share.source) {
    FlexPreset.WORKOUT ->
        listOfNotNull(runCatching { workout.getSession(share.a)?.session?.photoUri }.getOrNull()?.takeIf { it.isNotBlank() })
    FlexPreset.STREAK_DAY -> {
        val own = runCatching { streaks.getSavedDay(share.a, share.s)?.photoUri }.getOrNull()?.takeIf { it.isNotBlank() }
        val others = runCatching { streaks.savedDaysForStreak(share.a).first() }.getOrDefault(emptyList())
            .filter { it.date != share.s && !it.photoUri.isNullOrBlank() }
            .sortedByDescending { it.date }
            .mapNotNull { it.photoUri }
        (listOfNotNull(own) + others).distinct()
    }
    else -> emptyList()
}
