package com.ashrdev.aznd.ui.common.share

import android.content.Context
import com.ashrdev.aznd.data.workout.Exercise
import com.ashrdev.aznd.data.workout.SessionWithSets
import com.ashrdev.aznd.data.workout.SetMode
import com.ashrdev.aznd.domain.streaks.displayDate
import com.ashrdev.aznd.domain.streaks.parseKey
import com.ashrdev.aznd.ui.calendar.CalendarZoom
import com.ashrdev.aznd.ui.calendar.todayDayKey
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import com.ashrdev.aznd.ui.workout.stats.allSessionSetPoints
import com.ashrdev.aznd.ui.workout.stats.buildSessionE1rmPoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

// ---------------------------------------------------------------------------------------------
// Bindings: what each card of a poster shows when it is exported (the Use screen's data choices).
// A binding is separate from the card's spec: "Update preset to current" saves the layout and the
// card options, never these choices.
// ---------------------------------------------------------------------------------------------

/** What one card shows at export time; null fields = "use the default" (see [defaultBindings]). */
data class FlexBinding(
    val sessionId: Long? = null,            // EXERCISES, WORKOUT_STATS(SESSION)
    val exerciseNames: List<String>? = null,// WORKOUT_STATS(TOP_SET/ONE_RM): lifts, null = Auto; EXERCISES: exercises within the session, null = all
    val routineId: Long? = null,            // ROUTINE (a workout template's id)
    val streakIds: List<Long>? = null,      // STREAKS: null = Auto
    val noteStreakId: Long? = null,         // DAY_NOTE: latest note of this streak (or the note of [dayKey])
    val photoUri: String? = null,           // PHOTO
    val text: String? = null,               // TEXT text, HEADER title
    val dateMs: Long? = null,               // HEADER date, null = today
    val graph: FlexGraphConfig? = null,     // GRAPH, null = the card's own
    val cal: FlexCalendarSpec? = null,      // CALENDAR (source + period), null = the card's own
    /** DAY_NOTE and the calendar's month: the streak day a share started from ("yyyy-MM-dd"), null = the streak's latest note. */
    val dayKey: String? = null
)

typealias FlexBindings = Map<Int, FlexBinding>   // by FlexBlock.id

/** What a card needs the person to choose (one Configure step); the order of the entries is not the poster order. */
enum class FlexDataNeed { TITLE, TEXT, SESSION, SESSION_EXERCISES, LIFTS, ROUTINE, STREAKS, GRAPH, PHOTO, CALENDAR, DAY_NOTE }

/**
 * The choice this card asks for; null = nothing to choose: a disabled card, the 3D model (hidden until it is real), and a
 * photo card that shows one of its screen's own pictures (keys + [FLEX_SCREEN_PHOTO]; those are fixed by the preset).
 * An empty photo card ([FLEX_NO_PHOTO]) still needs its picture, so it is [FlexDataNeed.PHOTO].
 */
fun FlexBlock.dataNeed(): FlexDataNeed? {
    if (!enabled) return null
    return when (type) {
        FlexBlockType.HEADER -> FlexDataNeed.TITLE
        FlexBlockType.TEXT -> FlexDataNeed.TEXT
        FlexBlockType.STREAKS -> FlexDataNeed.STREAKS
        FlexBlockType.WORKOUT_STATS -> if (stat == FlexWorkoutStat.SESSION) FlexDataNeed.SESSION else FlexDataNeed.LIFTS
        FlexBlockType.EXERCISES -> FlexDataNeed.SESSION_EXERCISES
        FlexBlockType.ROUTINE -> FlexDataNeed.ROUTINE
        FlexBlockType.GRAPH -> FlexDataNeed.GRAPH
        FlexBlockType.DAY_NOTE -> FlexDataNeed.DAY_NOTE
        FlexBlockType.PHOTO -> if (keys != null && text == FLEX_SCREEN_PHOTO) null else FlexDataNeed.PHOTO
        FlexBlockType.CALENDAR -> FlexDataNeed.CALENDAR
        FlexBlockType.MODEL -> null
    }
}

/**
 * Where a share started (FlexScreen's preset/a/b/s). [a] / [b] / [s] mean what they do in [FlexPreset]:
 * WORKOUT a = session id; ROUTINE a = workout (template) id; EXERCISE a = workout id, b = the template's exercise id;
 * STREAK_DAY a = streak id, s = day key. HOME has none.
 */
data class FlexShareContext(val source: FlexPreset, val a: Long, val b: Long, val s: String)

/** Remembers each preset's last data choices (per preset id, per card id). SharedPreferences, one JSON string per preset. */
object FlexBindingStore {
    private const val FILE = "flex_bindings"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun key(presetId: Long) = "bindings_$presetId"

    fun load(context: Context, presetId: Long): FlexBindings {
        val raw = prefs(context).getString(key(presetId), null) ?: return emptyMap()
        return runCatching {
            val arr = JSONObject(raw).getJSONArray("cards")
            val out = LinkedHashMap<Int, FlexBinding>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out[o.getInt("id")] = bindingFromJson(o)
            }
            out as FlexBindings
        }.getOrDefault(emptyMap())
    }

    fun save(context: Context, presetId: Long, bindings: FlexBindings) {
        val arr = JSONArray()
        bindings.forEach { (id, b) -> arr.put(bindingToJson(b).put("id", id)) }
        prefs(context).edit().putString(key(presetId), JSONObject().put("cards", arr).toString()).apply()
    }
}

private fun JSONObject.str(name: String): String? = if (has(name) && !isNull(name)) getString(name) else null
private fun JSONObject.lng(name: String): Long? = if (has(name) && !isNull(name)) getLong(name) else null

private fun bindingToJson(b: FlexBinding): JSONObject {
    val o = JSONObject()
    b.sessionId?.let { o.put("sessionId", it) }
    b.exerciseNames?.let { o.put("exerciseNames", JSONArray(it)) }
    b.routineId?.let { o.put("routineId", it) }
    b.streakIds?.let { ids -> o.put("streakIds", JSONArray().also { a -> ids.forEach { a.put(it) } }) }
    b.noteStreakId?.let { o.put("noteStreakId", it) }
    b.photoUri?.let { o.put("photoUri", it) }
    b.text?.let { o.put("text", it) }
    b.dateMs?.let { o.put("dateMs", it) }
    b.graph?.let { g -> o.put("graph", JSONObject().put("vars", JSONArray(g.variables)).put("range", g.range.name)) }
    b.cal?.let { c ->
        o.put(
            "cal",
            JSONObject().put("zoom", c.zoom.name).put("year", c.year).put("index", c.index)
                .put("source", c.source).put("week", c.weekStart)
        )
    }
    b.dayKey?.let { o.put("dayKey", it) }
    return o
}

private inline fun <reified T : Enum<T>> enumNamed(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

private fun bindingFromJson(o: JSONObject): FlexBinding {
    val names = o.optJSONArray("exerciseNames")?.let { a -> List(a.length()) { a.getString(it) } }
    val streakIds = o.optJSONArray("streakIds")?.let { a -> List(a.length()) { a.getLong(it) } }
    val graph = o.optJSONObject("graph")?.let { g ->
        FlexGraphConfig(
            variables = g.optJSONArray("vars")?.let { a -> List(a.length()) { a.optString(it) }.filter { it.isNotBlank() }.take(2) }.orEmpty(),
            range = enumNamed(g.optString("range"), FlexGraphRange.MONTHS_3)
        )
    }
    val cal = o.optJSONObject("cal")?.let { c ->
        FlexCalendarSpec(
            zoom = enumNamed(c.optString("zoom"), CalendarZoom.MONTH),
            year = c.optInt("year", 0).coerceIn(0, 9999),
            index = c.optInt("index", 0).coerceIn(0, 12),
            source = c.optString("source", ""),
            weekStart = c.optInt("week", Calendar.MONDAY).let { if (it == Calendar.SUNDAY) Calendar.SUNDAY else Calendar.MONDAY }
        )
    }
    return FlexBinding(
        sessionId = o.lng("sessionId"), exerciseNames = names, routineId = o.lng("routineId"), streakIds = streakIds,
        noteStreakId = o.lng("noteStreakId"), photoUri = o.str("photoUri"), text = o.str("text"), dateMs = o.lng("dateMs"),
        graph = graph, cal = cal, dayKey = o.str("dayKey")
    )
}

// ---------------------------------------------------------------------------------------------
// The library: the user's real data the Configure steps choose from.
// ---------------------------------------------------------------------------------------------

data class FlexSessionInfo(
    val id: Long, val name: String, val startedAt: Long, val volumeKg: Double,
    val exerciseNames: List<String>, val routineId: Long?
)

/** [id] is the workout (template) id, as the ROUTINE share route's `a`. */
data class FlexRoutineInfo(val id: Long, val name: String, val exerciseCount: Int)

/** [latestNoteDay] is the day key ("yyyy-MM-dd") of [latestNote]; both null when the streak has no note. */
data class FlexStreakInfo(val id: Long, val name: String, val current: Int, val longest: Int, val latestNote: String?, val latestNoteDay: String?)

/** [group] is "Workouts" | "Streaks" | "Body"; [dateMs] is the workout's start, or the start of the streak day. */
data class FlexPhotoInfo(val uri: String, val label: String, val group: String, val dateMs: Long)

/** The user's real data to choose from; load once per Flex visit, off the main thread. */
class FlexLibrary(
    val sessions: List<FlexSessionInfo>,     // newest first
    val exercisesWithData: List<Exercise>,   // data Exercise entity (what ExerciseSearchScreen takes), A-Z
    val routines: List<FlexRoutineInfo>,     // most recently used first
    val streaks: List<FlexStreakInfo>,       // by current run, longest first
    val photos: List<FlexPhotoInfo>,         // newest first
    val graphs: List<FlexGraphSeries>,
    val calendars: List<FlexCalendarSource>,
    /** Template exercise id (the EXERCISE share route's `b`) to its name. */
    val templateExerciseNames: Map<Long, String> = emptyMap()
) {
    companion object {
        const val GROUP_WORKOUTS = "Workouts"
        const val GROUP_STREAKS = "Streaks"
        const val GROUP_BODY = "Body"

        /** How long a hot flow that starts empty is waited for. */
        private const val WAIT_MS = 600L

        suspend fun load(context: Context, workout: WorkoutViewModel, streaks: StreakViewModel): FlexLibrary =
            withContext(Dispatchers.Default) {
                coroutineScope {
                    val sessionsJob = async { runCatching { FlexContentLoader.allSessions(workout) }.getOrDefault(emptyList()) }
                    val workoutsJob = async {
                        runCatching { withTimeoutOrNull(WAIT_MS) { workout.workouts.first { it.isNotEmpty() } } ?: workout.workouts.value }
                            .getOrDefault(emptyList())
                    }
                    val catalogJob = async {
                        runCatching { withTimeoutOrNull(WAIT_MS) { workout.catalogExercises.first { it.isNotEmpty() } } ?: workout.catalogExercises.value }
                            .getOrDefault(emptyList())
                    }
                    val withDataJob = async {
                        runCatching { withTimeoutOrNull(WAIT_MS) { workout.exerciseIdsWithData.first { it.isNotEmpty() } } ?: workout.exerciseIdsWithData.value }
                            .getOrDefault(emptySet())
                    }
                    val graphsJob = async { runCatching { FlexGraphLoader.load(context, streaks) }.getOrDefault(emptyList()) }
                    val snapshotsJob = async { runCatching { streaks.streakSnapshots() }.getOrDefault(emptyList()) }

                    val rawSessions = sessionsJob.await().sortedByDescending { it.session.startedAt }
                    val workouts = workoutsJob.await()
                    val catalog = catalogJob.await()
                    val dataIds = withDataJob.await()
                    val snapshots = snapshotsJob.await().sortedByDescending { it.longest }.sortedByDescending { it.current }

                    val sessions = rawSessions.map { s -> sessionInfo(s) }

                    // Exercises with at least one logged set: the app's own "has data" ids, else a match by name on the logged sets.
                    val loggedNames = rawSessions.flatMapTo(HashSet()) { s -> s.sets.map { it.exerciseName.lowercase() } }
                    val withData = (if (dataIds.isNotEmpty()) catalog.filter { it.id in dataIds } else catalog.filter { it.name.lowercase() in loggedNames })
                        .sortedBy { it.name.lowercase() }

                    val lastUsed = HashMap<Long, Long>()
                    rawSessions.forEach { s -> if (s.session.workoutId !in lastUsed) lastUsed[s.session.workoutId] = s.session.startedAt }
                    val routines = workouts
                        .map { FlexRoutineInfo(it.workout.id, it.workout.name, it.exercises.size) to (lastUsed[it.workout.id] ?: 0L) }
                        .sortedWith(compareByDescending<Pair<FlexRoutineInfo, Long>> { it.second }.thenBy { it.first.name.lowercase() })
                        .map { it.first }
                    val templateNames = HashMap<Long, String>()
                    workouts.forEach { w -> w.exercises.forEach { templateNames[it.exercise.id] = it.exercise.name } }

                    // Streaks: their latest note, and the photos of their saved days.
                    val streakInfos = ArrayList<FlexStreakInfo>()
                    val photos = ArrayList<FlexPhotoInfo>()
                    val today = todayDayKey()
                    val calendars = ArrayList<FlexCalendarSource>()
                    for (snap in snapshots) {
                        val st = snap.streak
                        val days = runCatching { streaks.savedDaysForStreak(st.id).first() }.getOrDefault(emptyList())
                        val note = days.filter { it.remark.isNotBlank() }.maxByOrNull { it.date }
                        streakInfos += FlexStreakInfo(st.id, st.name, snap.current, snap.longest, note?.remark, note?.date)
                        days.filter { it.photoUri != null }.forEach { d ->
                            val ms = runCatching { parseKey(d.date).timeInMillis }.getOrDefault(d.savedAt)
                            photos += FlexPhotoInfo(d.photoUri.orEmpty(), "${st.name} · ${displayDate(d.date)}", GROUP_STREAKS, ms)
                        }
                        runCatching { FlexContentLoader.streakSource(streaks, st.id, st.name, st.startDate, today) }.getOrNull()?.let { calendars += it }
                    }
                    FlexContentLoader.workoutDays(rawSessions)?.let { calendars += it }
                    rawSessions.filter { it.session.photoUri != null }.forEach { s ->
                        photos += FlexPhotoInfo(
                            s.session.photoUri.orEmpty(), "${s.session.workoutName} · ${FlexContentLoader.date(s.session.startedAt)}",
                            GROUP_WORKOUTS, s.session.startedAt
                        )
                    }
                    // Body photos: the body screens store none today, so the "Body" group stays empty.

                    FlexLibrary(
                        sessions = sessions, exercisesWithData = withData, routines = routines, streaks = streakInfos,
                        photos = photos.sortedByDescending { it.dateMs }, graphs = graphsJob.await(), calendars = calendars,
                        templateExerciseNames = templateNames
                    )
                }
            }

        private fun sessionInfo(s: SessionWithSets) = FlexSessionInfo(
            id = s.session.id,
            name = s.session.workoutName,
            startedAt = s.session.startedAt,
            volumeKg = s.sets.filter { it.mode == SetMode.REPS }.sumOf { it.weight * it.value },
            exerciseNames = s.sets.sortedBy { it.orderIndex }.map { it.exerciseName }.distinct(),
            routineId = s.session.workoutId.takeIf { it > 0L }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Defaults
// ---------------------------------------------------------------------------------------------

/**
 * Skip/initial values: [remembered] (last time's) unless [share] gives this card a context; sessions default to the latest
 * workout. Only cards that need data (see [dataNeed]) get an entry.
 *
 * Cards of the same data need get DIFFERENT defaults (a share context that pins a card still wins, remembered values win where
 * they are valid), in poster (block) order:
 *  - Workout stats SESSION cards: the last gets the latest workout, the ones before it the earlier sessions of the SAME routine
 *    as that workout (else the earlier sessions): "then vs now".
 *  - Exercises cards: the j-th follows the j-th Session card when there are as many of each, else the nearest Session card before
 *    it; with no Session card they follow the rule above among themselves.
 *  - single-lift cards (One rep max / Top set with limit 1), when there are two or more: the top lift, the 2nd, ...
 *  - a Graph of two exercise 1RMs: the same two lifts (the first two single-lift cards' picks, else the top two lifts).
 *  - a Streak calendar / graph: the top streak (library.streaks[0]).
 */
fun defaultBindings(spec: FlexSpec, library: FlexLibrary, remembered: FlexBindings, share: FlexShareContext?): FlexBindings {
    val out = LinkedHashMap<Int, FlexBinding>()
    val src = share?.source
    val ctxSession = if (src == FlexPreset.WORKOUT) library.sessions.firstOrNull { it.id == share.a } else null
    val ctxRoutine = if (src == FlexPreset.ROUTINE) library.routines.firstOrNull { it.id == share.a } else null
    val ctxExercise = if (src == FlexPreset.EXERCISE) library.templateExerciseNames[share.b]?.takeIf { it.isNotBlank() } else null
    val ctxStreak = if (src == FlexPreset.STREAK_DAY) library.streaks.firstOrNull { it.id == share.a } else null
    val ctxDay = if (ctxStreak != null) share?.s?.takeIf { it.isNotBlank() } else null
    val ctxDayMs = ctxDay?.let { runCatching { parseKey(it).timeInMillis }.getOrNull() }

    val streakIds = library.streaks.map { it.id }.toSet()
    val exerciseNames = library.exercisesWithData.map { it.name }.toSet()
    val newestNote = library.streaks.filter { it.latestNoteDay != null }.maxByOrNull { it.latestNoteDay.orEmpty() }
    val topStreak = library.streaks.firstOrNull()

    // Distinct sessions (only where no workout share pins them).
    val sessionFor = HashMap<Int, Long?>()
    if (ctxSession == null) {
        val latest = library.sessions.firstOrNull()
        val sameRoutine = latest?.routineId?.let { r -> library.sessions.filter { it.routineId == r } }.orEmpty()
        fun back(n: Int): Long? =
            (sameRoutine.getOrNull(n) ?: library.sessions.getOrNull(n) ?: latest)?.id
        fun chain(cards: List<FlexBlock>) = cards.forEachIndexed { i, c -> sessionFor[c.id] = back(cards.size - 1 - i) }
        val statCards = spec.blocks.filter { it.dataNeed() == FlexDataNeed.SESSION }
        val exCards = spec.blocks.filter { it.dataNeed() == FlexDataNeed.SESSION_EXERCISES }
        chain(statCards)
        if (statCards.isEmpty()) chain(exCards)
        else exCards.forEachIndexed { j, c ->
            val at = spec.blocks.indexOf(c)
            val follow = if (exCards.size == statCards.size) statCards[j]
            else statCards.lastOrNull { spec.blocks.indexOf(it) < at } ?: statCards.first()
            sessionFor[c.id] = sessionFor[follow.id]
        }
    }

    // Distinct lifts: the top lifts by their best 1RM (the exercise series), else A-Z; single-lift cards take them in order.
    val ranked: List<String> = run {
        val withSeries = library.graphs.filter { it.group == FlexGraphLoader.GROUP_EXERCISE }
            .mapNotNull { g ->
                val name = g.id.removePrefix("e1rm:")
                if (name in exerciseNames) name to (g.points.maxOfOrNull { it.value } ?: 0.0) else null
            }
            .sortedByDescending { it.second }.map { it.first }
        (withSeries + library.exercisesWithData.map { it.name }).distinct()
    }
    val singleLifts = spec.blocks.filter { it.dataNeed() == FlexDataNeed.LIFTS && it.limit == 1 && it.stat != FlexWorkoutStat.SESSION }
    val liftFor = HashMap<Int, String>()
    if (ctxExercise == null && ctxSession == null && singleLifts.size >= 2) {
        singleLifts.forEachIndexed { i, c ->
            val own = remembered[c.id]?.exerciseNames?.filter { it in exerciseNames }?.takeIf { it.isNotEmpty() }
            val pick = own?.firstOrNull() ?: ranked.getOrNull(i)
            if (pick != null) liftFor[c.id] = pick
        }
    }
    val pairLifts = (singleLifts.mapNotNull { liftFor[it.id] } + ranked).distinct().take(2)

    for (b in spec.blocks) {
        val need = b.dataNeed() ?: continue
        val last = remembered[b.id] ?: FlexBinding()
        out[b.id] = when (need) {
            FlexDataNeed.TITLE -> when {
                ctxSession != null -> FlexBinding(text = ctxSession.name, dateMs = ctxSession.startedAt)
                ctxExercise != null -> FlexBinding(text = ctxExercise)
                ctxRoutine != null -> FlexBinding(text = ctxRoutine.name)
                ctxStreak != null -> FlexBinding(text = ctxStreak.name, dateMs = ctxDayMs)
                else -> FlexBinding(text = last.text)           // the date is today unless the person picks one
            }
            FlexDataNeed.TEXT -> FlexBinding(text = last.text)
            FlexDataNeed.SESSION, FlexDataNeed.SESSION_EXERCISES ->
                FlexBinding(sessionId = if (ctxSession != null) ctxSession.id else if (b.id in sessionFor) sessionFor[b.id] else library.sessions.firstOrNull()?.id)
            FlexDataNeed.LIFTS -> FlexBinding(
                exerciseNames = if (ctxExercise != null) listOf(ctxExercise)
                else last.exerciseNames?.filter { it in exerciseNames }?.takeIf { it.isNotEmpty() }
                    ?: liftFor[b.id]?.let { listOf(it) }
            )
            FlexDataNeed.ROUTINE -> FlexBinding(
                routineId = ctxRoutine?.id
                    ?: last.routineId?.takeIf { id -> library.routines.any { it.id == id } }
                    ?: library.routines.firstOrNull()?.id
            )
            FlexDataNeed.STREAKS -> FlexBinding(
                streakIds = if (ctxStreak != null) listOf(ctxStreak.id)
                else last.streakIds?.filter { it in streakIds }?.takeIf { it.isNotEmpty() }
            )
            FlexDataNeed.GRAPH -> {
                var g = last.graph
                // The Exercise blueprint's "this exercise's 1RM" placeholder: the shared exercise's series, else the first exercise series.
                val own = resolveScreenExercise(b.graph, ctxExercise, src == FlexPreset.EXERCISE, library)
                if (g == null && own != null) g = own
                if (g == null) g = remapGraph(b.graph, library)     // a card designed on sample data points at real ones
                // A graph of two exercise 1RMs follows the same two lifts as the lift cards.
                if (last.graph == null && ctxExercise == null && pairLifts.size == 2) {
                    val vars = (g ?: b.graph).variables
                    if (vars.size == 2 && vars.all { it.startsWith("e1rm:") }) {
                        g = (g ?: b.graph).copy(variables = pairLifts.map { FlexGraphLoader.exerciseId(it) })
                    }
                }
                if (ctxExercise != null) {
                    val id = FlexGraphLoader.exerciseId(ctxExercise)
                    if (library.graphs.any { it.id == id }) {
                        val vars = (g ?: b.graph).variables
                        val i = vars.indexOfFirst { it.startsWith("e1rm:") }
                        val next = when {
                            vars.isEmpty() -> listOf(id)
                            i >= 0 -> vars.toMutableList().also { it[i] = id }.distinct()
                            else -> vars
                        }
                        g = (g ?: b.graph).copy(variables = next)
                    }
                }
                FlexBinding(graph = g)
            }
            FlexDataNeed.PHOTO -> {
                val own = when {
                    ctxSession != null -> library.photos.firstOrNull { it.group == FlexLibrary.GROUP_WORKOUTS && it.dateMs == ctxSession.startedAt && it.label.startsWith(ctxSession.name) }
                    ctxStreak != null && ctxDayMs != null -> library.photos.firstOrNull { it.group == FlexLibrary.GROUP_STREAKS && it.dateMs == ctxDayMs && it.label.startsWith(ctxStreak.name) }
                    else -> null
                }
                FlexBinding(photoUri = own?.uri ?: last.photoUri ?: library.photos.firstOrNull()?.uri)
            }
            FlexDataNeed.CALENDAR -> {
                // A streak calendar designed on sample data shows the top streak; one with a source the library lacks, the first source.
                val topKey = topStreak?.let { "streak:${it.id}" }?.takeIf { k -> library.calendars.any { it.key == k } }
                val c = last.cal ?: when {
                    b.cal.source.startsWith("streak:") && topKey != null -> b.cal.copy(source = topKey)
                    b.cal.source.isNotBlank() && library.calendars.none { s -> s.key == b.cal.source } ->
                        b.cal.copy(source = library.calendars.firstOrNull()?.key.orEmpty())
                    else -> null
                }
                FlexBinding(cal = if (ctxStreak != null) (c ?: b.cal).copy(source = "streak:${ctxStreak.id}") else c)
            }
            FlexDataNeed.DAY_NOTE -> when {
                ctxStreak != null -> FlexBinding(noteStreakId = ctxStreak.id, dayKey = ctxDay)
                else -> FlexBinding(
                    noteStreakId = last.noteStreakId?.takeIf { it in streakIds } ?: newestNote?.id ?: library.streaks.firstOrNull()?.id
                )
            }
        }
    }
    return out
}

/**
 * [g] with [FLEX_SCREEN_EXERCISE_1RM] replaced by the shared exercise's 1RM series ([exerciseName], when its series exists), else
 * by the first Exercise-1RM series of the library; dropped when there is none. Null when [g] has no placeholder.
 */
private fun resolveScreenExercise(g: FlexGraphConfig, exerciseName: String?, fromExercise: Boolean, library: FlexLibrary): FlexGraphConfig? {
    if (FLEX_SCREEN_EXERCISE_1RM !in g.variables) return null
    val named = exerciseName?.let { FlexGraphLoader.exerciseId(it) }?.takeIf { id -> library.graphs.any { it.id == id } }
    val own = if (fromExercise) named else named ?: library.graphs.firstOrNull { it.group == FlexGraphLoader.GROUP_EXERCISE }?.id
    return g.copy(variables = g.variables.mapNotNull { if (it == FLEX_SCREEN_EXERCISE_1RM) own else it }.distinct())
}

/**
 * A graph card's variables with the ones the real data lacks replaced by real equivalents: "e1rm:x" by the first exercise
 * 1RM series, "streak:id:kind" always by the same kind of the top streak. Dashboard ids and ids that exist stay. Null when nothing changed.
 */
private fun remapGraph(g: FlexGraphConfig, library: FlexLibrary): FlexGraphConfig? {
    if (FLEX_SCREEN_EXERCISE_1RM in g.variables) return null     // see resolveScreenExercise: never turned into some other exercise here
    val have = library.graphs.map { it.id }.toSet()
    val used = g.variables.filter { it in have }.toMutableSet()
    val topStreak = library.streaks.firstOrNull()?.id
    var changed = false
    val next = g.variables.mapNotNull { v ->
        // A streak graph designed on sample data shows the same kind of graph of the top streak.
        if (v.startsWith("streak:") && topStreak != null) {
            val top = FlexGraphLoader.streakId(topStreak, v.substringAfterLast(':'))
            val repl = top.takeIf { it in have && (it !in used || it == v) } ?: if (v in have) v else null
            if (repl != v) changed = true
            return@mapNotNull repl?.also { used += it }
        }
        if (v in have) return@mapNotNull v
        val repl: String? = when {
            v.startsWith("e1rm:") ->
                library.graphs.firstOrNull { it.group == FlexGraphLoader.GROUP_EXERCISE && it.id !in used }?.id
            v.startsWith("streak:") -> null                     // no streak at all
            else -> v                                           // a dashboard id (calories, ...): kept as it is
        }
        if (repl != v) changed = true
        repl?.also { used += it }
    }
    return if (changed) g.copy(variables = next) else null
}

// ---------------------------------------------------------------------------------------------
// The spec as drawn
// ---------------------------------------------------------------------------------------------

/**
 * The spec as drawn for these choices: applies the binding fields that live on the block (text, graph, calendar, the chosen
 * lifts / exercises / streaks as [FlexBlock.keys], the photo as its key). Blocks without a binding are untouched; a null
 * choice means Auto / the card's own and clears the keys of a list card.
 */
fun FlexSpec.bound(bindings: FlexBindings): FlexSpec {
    if (bindings.isEmpty()) return this
    return copy(blocks = blocks.map { b ->
        val v = bindings[b.id] ?: return@map b
        when (b.type) {
            FlexBlockType.HEADER -> if (v.text != null) b.copy(text = v.text) else b
            FlexBlockType.TEXT -> if (v.text != null) b.copy(text = v.text) else b
            FlexBlockType.GRAPH -> if (v.graph != null) b.copy(graph = v.graph) else b
            FlexBlockType.CALENDAR -> if (v.cal != null) b.copy(cal = v.cal) else b
            // A binding without a picture leaves the block as it is: an empty photo card (see [FLEX_NO_PHOTO]) stays empty.
            FlexBlockType.PHOTO -> if (v.photoUri != null) b.copy(keys = listOf(v.photoUri)) else b
            FlexBlockType.WORKOUT_STATS -> if (b.stat == FlexWorkoutStat.SESSION) b else b.copy(keys = v.exerciseNames?.takeIf { it.isNotEmpty() })
            FlexBlockType.EXERCISES -> b.copy(keys = v.exerciseNames?.takeIf { it.isNotEmpty() })
            FlexBlockType.STREAKS -> b.copy(keys = v.streakIds?.takeIf { it.isNotEmpty() }?.map { "s$it" })
            else -> b
        }
    })
}

// ---------------------------------------------------------------------------------------------
// Content for the choices
// ---------------------------------------------------------------------------------------------

/**
 * The poster's content for these choices: shared fields (graphs, calendars, today, the streak list) + per-card parts
 * ([FlexContent.parts]) that hold exactly what each card shows. Runs off the main thread.
 */
suspend fun loadBoundContent(
    context: Context,
    spec: FlexSpec,
    bindings: FlexBindings,
    library: FlexLibrary,
    workout: WorkoutViewModel,
    streaks: StreakViewModel
): FlexContent = withContext(Dispatchers.Default) {
    val cards = spec.blocks.filter { it.enabled && it.dataNeed() != null }
    val today = todayDayKey()
    fun bindingOf(b: FlexBlock) = bindings[b.id] ?: FlexBinding()

    // Sessions (each loaded once), by the id a card chose, else the latest workout.
    val sessionCache = HashMap<Long, FlexContent?>()
    suspend fun sessionOf(id: Long?): FlexContent? {
        val use = id ?: library.sessions.firstOrNull()?.id ?: return null
        return sessionCache.getOrPut(use) { runCatching { workout.getSession(use) }.getOrNull()?.let { FlexContentLoader.sessionContent(it) } }
    }
    val sessionCards = cards.filter { it.dataNeed() == FlexDataNeed.SESSION || it.dataNeed() == FlexDataNeed.SESSION_EXERCISES }
    val primary = sessionCards.firstOrNull()?.let { sessionOf(bindingOf(it).sessionId) }

    val needStreaks = cards.any { it.type == FlexBlockType.STREAKS }
    val streakItems = if (needStreaks) {
        runCatching { streaks.streakSnapshots() }.getOrDefault(emptyList())
            .sortedByDescending { it.longest }.sortedByDescending { it.current }
            .map { FlexStreakItem(it.streak.id, it.streak.name, it.current, it.longest, it.streak.photoUri) }
    } else emptyList()

    val dayKey = cards.firstNotNullOfOrNull { bindingOf(it).dayKey }
    val shared = FlexContent(
        kicker = "",
        title = "",
        dateText = FlexContentLoader.date(System.currentTimeMillis()),
        streaks = streakItems,
        graphs = library.graphs,
        calendars = library.calendars,
        calendarDay = dayKey.orEmpty(),
        calendarToday = today,
        photos = library.photos.map { FlexPhoto(it.uri, it.label) },
        photoUri = primary?.photoUri
    )

    // Lifts: the all-time best set per exercise (loaded when a card needs them).
    var globalLifts: List<FlexLiftItem>? = null
    suspend fun allLifts(): List<FlexLiftItem> =
        globalLifts ?: runCatching { FlexContentLoader.liftsFrom(workout.getTopExercises(40)) }.getOrDefault(emptyList()).also { globalLifts = it }
    val liftCache = HashMap<String, FlexLiftItem?>()
    suspend fun liftOf(name: String): FlexLiftItem? = liftCache.getOrPut(name) {
        val ex = library.exercisesWithData.firstOrNull { it.name == name }
        val best = ex?.let {
            runCatching {
                buildSessionE1rmPoints(allSessionSetPoints(it, workout.loggedSetsForExercise(it.id))).maxByOrNull { p -> p.e1rm }
            }.getOrNull()
        }
        best?.let { FlexLiftItem(name, it.topSetWeight, it.topSetReps, it.topSetRpe, it.e1rm, it.startedAt) }
            ?: allLifts().firstOrNull { it.name == name }
            ?: primary?.lifts?.firstOrNull { it.name == name }
    }

    val parts = HashMap<Int, FlexContent>()
    for (b in cards) {
        val v = bindingOf(b)
        when (b.dataNeed()) {
            // The workout's name and day travel with the stats (the card's "Show workout & date" line).
            FlexDataNeed.SESSION -> sessionOf(v.sessionId)?.let { parts[b.id] = shared.copy(stats = it.stats, title = it.title, dateText = it.dateText) }
            FlexDataNeed.SESSION_EXERCISES -> sessionOf(v.sessionId)?.let { parts[b.id] = shared.copy(exercises = it.exercises) }
            FlexDataNeed.LIFTS -> {
                val chosen = v.exerciseNames
                val lifts = when {
                    chosen != null && chosen.isNotEmpty() -> chosen.mapNotNull { liftOf(it) }
                    primary != null -> primary.lifts            // a poster about a workout: that workout's best sets
                    else -> allLifts()
                }
                parts[b.id] = shared.copy(lifts = lifts)
            }
            FlexDataNeed.ROUTINE -> {
                val id = v.routineId ?: library.routines.firstOrNull()?.id
                if (id != null) {
                    val r = runCatching { FlexContentLoader.routine(workout, id) }.getOrNull()
                    if (r != null) parts[b.id] = shared.copy(routine = r.routine)
                }
            }
            FlexDataNeed.STREAKS -> {
                val ids = v.streakIds
                if (ids != null && ids.isNotEmpty()) {
                    val byId = streakItems.associateBy { it.id }
                    parts[b.id] = shared.copy(streaks = ids.mapNotNull { byId[it] })
                }
            }
            FlexDataNeed.DAY_NOTE -> {
                val id = v.noteStreakId ?: library.streaks.firstOrNull { it.latestNote != null }?.id
                if (id != null) {
                    val day = runCatching {
                        v.dayKey?.let { streaks.getSavedDay(id, it) }
                            ?: streaks.savedDaysForStreak(id).first().filter { it.remark.isNotBlank() }.maxByOrNull { it.date }
                    }.getOrNull()
                    parts[b.id] = shared.copy(dayNote = day?.remark.orEmpty(), dateText = day?.let { displayDate(it.date) } ?: "")
                }
            }
            FlexDataNeed.PHOTO -> parts[b.id] = shared.copy(photoUri = v.photoUri ?: shared.photoUri)
            FlexDataNeed.TITLE -> parts[b.id] = titlePart(shared, v, cards, bindings, primary, library, workout, streaks)
            else -> {}     // TEXT, GRAPH and CALENDAR read the shared fields; their choices live on the block (see bound)
        }
    }
    shared.copy(parts = parts)
}

/** A title card's part: its kicker, default title (when the card has no text of its own) and date. */
private suspend fun titlePart(
    shared: FlexContent, v: FlexBinding, cards: List<FlexBlock>, bindings: FlexBindings, primary: FlexContent?,
    library: FlexLibrary, workout: WorkoutViewModel, streaks: StreakViewModel
): FlexContent {
    var kicker = ""
    var title = ""
    var subtitle = ""
    val routineCard = cards.firstOrNull { it.dataNeed() == FlexDataNeed.ROUTINE }
    val noteCard = cards.firstOrNull { it.dataNeed() == FlexDataNeed.DAY_NOTE }
    // The poster's subject: a kicker and a default title only when it is about ONE workout, routine, streak day or exercise.
    // Several different workouts or lifts (a comparison) have no single subject: no kicker.
    val sessionIds = cards.filter { it.dataNeed() == FlexDataNeed.SESSION || it.dataNeed() == FlexDataNeed.SESSION_EXERCISES }
        .map { bindings[it.id]?.sessionId ?: library.sessions.firstOrNull()?.id }.distinct()
    val liftNames = cards.filter { it.dataNeed() == FlexDataNeed.LIFTS }.map { bindings[it.id]?.exerciseNames.orEmpty() }
    val oneExercise = liftNames.isNotEmpty() && liftNames.all { it.size == 1 } && liftNames.map { it.first() }.distinct().size == 1
    val kinds = listOf(sessionIds.isNotEmpty(), routineCard != null, noteCard != null).count { it }
    val single = kinds == 1 && sessionIds.size <= 1
    when {
        single && primary != null -> { kicker = "Workout"; title = primary.title }
        single && routineCard != null -> {
            val id = bindings[routineCard.id]?.routineId ?: library.routines.firstOrNull()?.id
            val r = id?.let { runCatching { FlexContentLoader.routine(workout, it) }.getOrNull() }
            if (r != null) { kicker = "Routine"; title = r.title; subtitle = r.subtitle }
        }
        single && noteCard != null -> {
            val nb = bindings[noteCard.id]
            val id = nb?.noteStreakId ?: library.streaks.firstOrNull { it.latestNote != null }?.id
            val info = library.streaks.firstOrNull { it.id == id }
            if (info != null) {
                kicker = info.name
                val day = (nb?.dayKey ?: info.latestNoteDay)?.let { k -> runCatching { streaks.getSavedDay(info.id, k) }.getOrNull() }
                title = if (day != null) "Day ${day.streakCountAtSave}" else info.name
            }
        }
        kinds == 0 && oneExercise -> { kicker = "Exercise"; title = liftNames.first().first() }
        else -> title = "My progress"
    }
    val date = v.dateMs?.let { FlexContentLoader.date(it) } ?: shared.dateText
    return shared.copy(kicker = kicker, title = title, subtitle = subtitle, dateText = date)
}
