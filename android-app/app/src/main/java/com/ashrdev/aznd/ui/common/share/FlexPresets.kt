package com.ashrdev.aznd.ui.common.share

import android.content.Context
import android.net.Uri
import com.ashrdev.aznd.data.workout.SessionWithSets
import com.ashrdev.aznd.data.workout.SetMode
import com.ashrdev.aznd.domain.workout.ExerciseType
import com.ashrdev.aznd.domain.workout.SetKind
import com.ashrdev.aznd.domain.workout.targetLabel
import com.ashrdev.aznd.ui.navigation.Destination
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.domain.streaks.dayKeysBetween
import com.ashrdev.aznd.domain.streaks.displayDate
import com.ashrdev.aznd.ui.calendar.dayKeyOf
import com.ashrdev.aznd.ui.calendar.todayDayKey
import com.ashrdev.aznd.ui.workout.TopExerciseStat
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import com.ashrdev.aznd.ui.workout.stats.estimatedOneRepMax
import com.ashrdev.aznd.ui.workout.formatDuration
import com.ashrdev.aznd.ui.workout.stats.loadExerciseStats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/** The navigation route that opens Flex on this preset. */
fun FlexPreset.route(a: Long = -1L, b: Long = -1L, s: String = ""): String =
    Destination.Flex.createRoute(id, a, b, Uri.encode(s))

private fun block(
    id: Int,
    type: FlexBlockType,
    enabled: Boolean = true,
    variant: Int = 0,
    limit: Int = 3,
    flag: Boolean = true,
    text: String = "",
    stat: FlexWorkoutStat = FlexWorkoutStat.TOP_SET,
    graph: FlexGraphConfig = FlexGraphConfig()
) = FlexBlock(id = id, type = type, enabled = enabled, variant = variant, keys = null, limit = limit, text = text, flag = flag, stat = stat, graph = graph)

/**
 * The card a preset starts with, before the person changes anything. A workout or a streak day that has pictures
 * starts with them as photo blocks, as many as the card's content budget holds (see [withPhotosThatFit]).
 */
fun FlexPreset.defaultSpec(content: FlexContent): FlexSpec {
    val base = baseSpec(content)
    return when (this) {
        FlexPreset.WORKOUT, FlexPreset.STREAK_DAY -> base.withPhotosThatFit(content)
        else -> base
    }
}

/** Marks a photo block that shows one of the screen's own pictures ([FlexContent.photos]) in [FlexBlock.text]. */
const val FLEX_SCREEN_PHOTO = "screen"

/** The most pictures a card starts with besides the screen's own photo. */
private const val MAX_DEFAULT_PHOTOS = 8

/** This card with a photo block for the picture at [uri], after the others. */
fun FlexSpec.withPhotoBlock(uri: String): FlexSpec =
    copy(blocks = blocks + FlexBlock(id = nextBlockId(), type = FlexBlockType.PHOTO, keys = listOf(uri), text = FLEX_SCREEN_PHOTO))

/**
 * The card with the screen's own photo block turned on (when the screen has a photo) and a photo block added for each
 * of its other pictures in order, as long as the card's content budget holds them: the first one that would take the card
 * past it ends the list, and the rest stay available in the block list.
 */
private fun FlexSpec.withPhotosThatFit(content: FlexContent): FlexSpec {
    val assets = FlexAssets()
    var spec = this
    val own = spec.blocks.firstOrNull { it.type == FlexBlockType.PHOTO && it.keys == null }
    if (content.photoUri != null && own != null && !own.enabled) {
        val on = spec.copy(blocks = spec.blocks.map { if (it.id == own.id) it.copy(enabled = true) else it })
        if (!FlexPainter.refuses(spec, on, content, assets)) spec = on
    }
    for (photo in content.photos.distinctBy { it.uri }.take(MAX_DEFAULT_PHOTOS)) {
        val next = spec.withPhotoBlock(photo.uri)
        if (FlexPainter.refuses(spec, next, content, assets)) break
        spec = next
    }
    return spec
}

/**
 * A saved card without the screen-picture blocks of another screen's pictures: those a different workout or streak day
 * put on it. What the person kept of their own is untouched, and nothing is added (a saved card is the person's).
 */
fun FlexSpec.withoutStalePhotos(content: FlexContent): FlexSpec {
    val here = content.photos.map { it.uri }.toSet()
    val kept = blocks.filter { b -> !(b.type == FlexBlockType.PHOTO && b.text == FLEX_SCREEN_PHOTO && b.keys?.firstOrNull() !in here) }
    return if (kept.size == blocks.size) this else copy(blocks = kept)
}

private fun FlexPreset.baseSpec(content: FlexContent): FlexSpec = when (this) {
    FlexPreset.HOME -> FlexSpec(
        flexStyleOf(FlexPalettes.Midnight, FlexAspect.PORTRAIT),
        listOf(
            block(1, FlexBlockType.HEADER),
            block(2, FlexBlockType.STREAKS, variant = 0, limit = 3),
            block(3, FlexBlockType.WORKOUT_STATS, variant = 1, limit = 3, stat = FlexWorkoutStat.ONE_RM),
            block(4, FlexBlockType.WORKOUT_STATS, variant = 1, limit = 2, stat = FlexWorkoutStat.TOP_SET),
            block(5, FlexBlockType.PHOTO, enabled = false),
            block(6, FlexBlockType.TEXT, enabled = false, text = "Stay consistent.")
        )
    )

    FlexPreset.WORKOUT -> {
        val hasPhoto = content.photoUri != null
        val base = flexStyleOf(FlexPalettes.Ember, FlexAspect.PORTRAIT)
        FlexSpec(
            if (hasPhoto) {
                base.copy(bgMode = FlexBgMode.PHOTO, photoBlur = 0.35f, photoDim = 0.55f, panel = FlexPanel.GLASS)
            } else base,
            listOf(
                block(1, FlexBlockType.HEADER),
                block(2, FlexBlockType.WORKOUT_STATS, variant = 0, stat = FlexWorkoutStat.SESSION),
                block(3, FlexBlockType.WORKOUT_STATS, variant = 0, limit = 1, stat = FlexWorkoutStat.TOP_SET),
                block(4, FlexBlockType.EXERCISES, variant = 1, limit = 6, flag = false),
                block(5, FlexBlockType.PHOTO, enabled = false),
                block(6, FlexBlockType.TEXT, enabled = false, text = "Another one done.")
            )
        )
    }

    FlexPreset.ROUTINE -> FlexSpec(
        flexStyleOf(FlexPalettes.Forest, FlexAspect.PORTRAIT),
        listOf(
            block(1, FlexBlockType.HEADER),
            block(2, FlexBlockType.ROUTINE, variant = 0, limit = 10),
            block(3, FlexBlockType.TEXT, enabled = false, text = "Try it with me.")
        )
    )

    FlexPreset.EXERCISE -> FlexSpec(
        flexStyleOf(FlexPalettes.Ocean, FlexAspect.PORTRAIT),
        listOf(
            block(1, FlexBlockType.HEADER, variant = 2),
            block(2, FlexBlockType.WORKOUT_STATS, variant = 0, limit = 1, stat = FlexWorkoutStat.ONE_RM),
            // The exercise's own 1RM history (the loader puts it first among the graphs); empty until there is one.
            block(
                3, FlexBlockType.GRAPH, variant = 0,
                graph = FlexGraphConfig(
                    variables = listOfNotNull(content.graphs.firstOrNull { it.group == FlexGraphLoader.GROUP_EXERCISE }?.id),
                    range = FlexGraphRange.ALL
                )
            ),
            block(4, FlexBlockType.WORKOUT_STATS, enabled = false, variant = 1, limit = 1, stat = FlexWorkoutStat.TOP_SET)
        )
    )

    FlexPreset.STREAK_DAY -> FlexSpec(
        flexStyleOf(FlexPalettes.Violet, FlexAspect.PORTRAIT),
        listOf(
            block(1, FlexBlockType.HEADER, variant = 1),
            block(2, FlexBlockType.PHOTO, variant = 0),
            block(3, FlexBlockType.DAY_NOTE, variant = 0),
            block(4, FlexBlockType.STREAKS, enabled = false, variant = 2, limit = 1)
        )
    )
}

/**
 * Brings a saved card up to date with the data it is about to show: nothing to do for most cards, but a
 * saved card can name a block type this version no longer has (dropped on load) and stored ids stay unique.
 */
fun FlexSpec.reconciled(): FlexSpec {
    if (blocks.isEmpty()) return this
    val seen = HashSet<Int>()
    var next = (blocks.maxOf { it.id }) + 1
    val fixed = blocks.map { b -> if (seen.add(b.id)) b else b.copy(id = next++).also { seen.add(it.id) } }
    return copy(blocks = fixed)
}

/** Builds the [FlexContent] of a preset from the app's data. Runs once when the screen opens. */
object FlexContentLoader {

    suspend fun load(
        preset: FlexPreset,
        a: Long,
        b: Long,
        s: String,
        workout: WorkoutViewModel,
        streaks: StreakViewModel,
        /** Any context: where the Graph series (food, body, workout history) are read from. Null falls back to the one [FlexFonts] was given. */
        context: Context? = null
    ): FlexContent {
        val content = when (preset) {
            FlexPreset.HOME -> home(workout, streaks)
            FlexPreset.WORKOUT -> workoutSession(workout, a)
            FlexPreset.ROUTINE -> routine(workout, a)
            FlexPreset.EXERCISE -> exercise(workout, a, b)
            FlexPreset.STREAK_DAY -> streakDay(streaks, a, s)
        }
        val ctx = context ?: FlexFonts.appContext ?: return content
        val all = FlexGraphLoader.load(ctx, streaks)
        // A screen's own series (the exercise being shared) come first.
        return content.copy(graphs = (content.graphs + all).distinctBy { it.id })
    }

    internal fun date(ms: Long): String = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ms))

    internal fun liftsFrom(top: List<TopExerciseStat>): List<FlexLiftItem> =
        top.groupBy { it.exerciseName }.values
            .mapNotNull { list -> list.maxByOrNull { it.bestPoint.e1rm } }
            .sortedByDescending { it.bestPoint.e1rm }
            .map {
                val p = it.bestPoint
                FlexLiftItem(it.exerciseName, p.topSetWeight, p.topSetReps, p.topSetRpe, p.e1rm, p.startedAt)
            }

    /** How long the home card waits for the history to arrive (it is a hot flow that starts empty). */
    private const val SESSIONS_WAIT_MS = 600L

    /** Photos of recent workouts the home card can offer. */
    private const val HOME_PHOTOS = 12

    internal suspend fun allSessions(workout: WorkoutViewModel): List<SessionWithSets> =
        withTimeoutOrNull(SESSIONS_WAIT_MS) { workout.allSessions.first { it.isNotEmpty() } } ?: workout.allSessions.value

    private suspend fun home(workout: WorkoutViewModel, streaks: StreakViewModel): FlexContent {
        val snapshots = streaks.streakSnapshots().sortedByDescending { it.current }
        val streakItems = snapshots.map { FlexStreakItem(it.streak.id, it.streak.name, it.current, it.longest, it.streak.photoUri) }
        val today = todayDayKey()
        val sessions = allSessions(workout)
        // The Calendar block can show any streak (the longest running one first, as it always did) or the days trained.
        val sources = buildList {
            snapshots.forEach { snap ->
                runCatching { streakSource(streaks, snap.streak.id, snap.streak.name, snap.streak.startDate, today) }.getOrNull()?.let { add(it) }
            }
            workoutDays(sessions)?.let { add(it) }
        }
        val photos = sessions.filter { it.session.photoUri != null }.take(HOME_PHOTOS).map {
            FlexPhoto(it.session.photoUri.orEmpty(), "${it.session.workoutName} · ${date(it.session.startedAt)}")
        }
        return FlexContent(
            kicker = "",
            title = "My progress",
            dateText = date(System.currentTimeMillis()),
            streaks = streakItems,
            lifts = liftsFrom(workout.getTopExercises(40)),
            calendars = sources,
            calendarToday = today,
            photos = photos
        )
    }

    /**
     * A streak's kept days as a Calendar source: every day from its start to today except its slips, as the card has
     * always marked them.
     */
    internal suspend fun streakSource(streaks: StreakViewModel, streakId: Long, name: String, startDate: String, today: String): FlexCalendarSource {
        val breaks = streaks.breaksForStreak(streakId).first().toSet()
        val kept = dayKeysBetween(startDate, today).filter { it !in breaks }
        return FlexCalendarSource("streak:$streakId", name, kept.associateWith { FlexDay() }, startDate)
    }

    /** The days a workout was done, as a Calendar source; null when none was. */
    internal fun workoutDays(sessions: List<SessionWithSets>): FlexCalendarSource? {
        val days = sessions.map { dayKeyOf(it.session.startedAt) }.toSortedSet()
        if (days.isEmpty()) return null
        return FlexCalendarSource("workouts", "Workout days", days.associateWith { FlexDay() }, days.first())
    }

    private suspend fun workoutSession(workout: WorkoutViewModel, sessionId: Long): FlexContent {
        val data = workout.getSession(sessionId) ?: return FlexContent(kicker = "Workout", title = "Workout")
        return sessionContent(data)
    }

    /** One finished workout as content: its exercises with their sets, its best set per exercise, its totals and its photo. */
    internal fun sessionContent(data: SessionWithSets): FlexContent {
        val session = data.session
        val grouped = data.sets.sortedBy { it.orderIndex }.groupBy { it.exerciseName }

        val exercises = grouped.map { (name, sets) ->
            val lines = sets.map { st ->
                if (st.mode == SetMode.TIME) FlexSetLine(weightKg = st.weight, seconds = st.value, rpe = st.rpe)
                else FlexSetLine(weightKg = st.weight, reps = st.value, rpe = st.rpe)
            }
            val scores = sets.map { st ->
                if (st.mode == SetMode.REPS) estimatedOneRepMax(st.weight, st.value, st.rpe) ?: (st.value / 1000.0)
                else st.value / 1000.0
            }
            FlexExerciseItem(name, lines, scores.indices.maxByOrNull { scores[it] } ?: 0)
        }

        val lifts = grouped.mapNotNull { (name, sets) ->
            sets.filter { it.mode == SetMode.REPS }
                .mapNotNull { st -> estimatedOneRepMax(st.weight, st.value, st.rpe)?.let { st to it } }
                .maxByOrNull { it.second }
                ?.let { (st, e1rm) -> FlexLiftItem(name, st.weight, st.value, st.rpe, e1rm, session.startedAt) }
        }.sortedByDescending { it.e1rm }

        val elapsed = ((session.finishedAt - session.startedAt) / 1000L).toInt()
        val volume = data.sets.filter { it.mode == SetMode.REPS }.sumOf { it.weight * it.value }
        val stats = buildList {
            add(FlexStat("Duration", formatDuration(elapsed)))
            if (volume > 0.0) add(FlexStat("Volume", "${"%,d".format(volume.roundToLong())} kg"))
            add(FlexStat("Sets", data.sets.size.toString()))
            add(FlexStat("Exercises", grouped.size.toString()))
        }

        return FlexContent(
            kicker = "Workout",
            title = session.workoutName,
            dateText = date(session.startedAt),
            exercises = exercises,
            lifts = lifts,
            stats = stats,
            photoUri = session.photoUri
        )
    }

    internal suspend fun routine(workout: WorkoutViewModel, workoutId: Long): FlexContent {
        val w = workout.getWorkout(workoutId) ?: return FlexContent(kicker = "Routine", title = "Routine")
        val view = workout.templateView(workoutId)
        val items = view.cards.map { card ->
            val ex = card.exerciseId?.let { view.exercises[it] }
            val type = ex?.type ?: ExerciseType.WEIGHTED
            val normal = card.rows.filter { it.kind == SetKind.NORMAL }
            val setCount = normal.size.coerceAtLeast(1)
            val partners = card.rows.filter { it.kind == SetKind.SUPERSET }
                .mapNotNull { r -> r.exerciseId?.let { view.exercises[it]?.name } }
                .distinct()
            val name = (listOf(ex?.name ?: "Exercise") + partners).joinToString(" + ")
            val labels = normal.mapNotNull { targetLabel(it, type, true) }.distinct()
            val target = if (labels.size == 1) "$setCount × ${labels[0].replace('-', '–')}" else null
            val tag = listOfNotNull(
                if (card.rows.any { it.kind == SetKind.SUPERSET }) "Superset" else null,
                if (card.rows.any { it.kind == SetKind.DROP }) "Drop set" else null
            ).joinToString(" · ").ifBlank { null }
            FlexRoutineItem(name, setCount, target, tag)
        }
        val totalSets = items.sumOf { it.sets }
        return FlexContent(
            kicker = "Routine",
            title = w.workout.name,
            subtitle = "${items.size} exercise${if (items.size == 1) "" else "s"} · $totalSets sets",
            routine = items
        )
    }

    private suspend fun exercise(workout: WorkoutViewModel, workoutId: Long, exerciseId: Long): FlexContent {
        val data = loadExerciseStats(workout, workoutId, exerciseId, useTargets = false)
        val points = data.points
        val best = points.maxByOrNull { it.e1rm }
        return FlexContent(
            kicker = "Estimated 1RM",
            title = data.name,
            subtitle = "${points.size} session${if (points.size == 1) "" else "s"}",
            lifts = listOfNotNull(
                best?.let { FlexLiftItem(data.name, it.topSetWeight, it.topSetReps, it.topSetRpe, it.e1rm, it.startedAt) }
            ),
            graphs = listOf(
                FlexGraphSeries(
                    id = FlexGraphLoader.exerciseId(data.name), label = "${data.name} 1RM", unit = "kg", bars = false,
                    group = FlexGraphLoader.GROUP_EXERCISE, points = points.map { FlexChartPoint(it.startedAt, it.e1rm) },
                    startAtZero = false
                )
            ).filter { it.points.isNotEmpty() }
        )
    }

    private suspend fun streakDay(streaks: StreakViewModel, streakId: Long, dateKey: String): FlexContent {
        val streak = streaks.getStreak(streakId) ?: return FlexContent(kicker = "Streak", title = "Streak")
        val day = streaks.getSavedDay(streakId, dateKey)
        val count = day?.streakCountAtSave ?: 0
        val others = streaks.savedDaysForStreak(streakId).first()
            .filter { it.photoUri != null && it.date != dateKey && it.photoUri != day?.photoUri }
            .sortedByDescending { it.date }
            .map { FlexPhoto(it.photoUri.orEmpty(), displayDate(it.date)) }
        val today = todayDayKey()
        val source = runCatching { streakSource(streaks, streak.id, streak.name, streak.startDate, today) }.getOrNull()
        return FlexContent(
            kicker = streak.name,
            title = "Day $count",
            dateText = displayDate(dateKey),
            streaks = listOf(FlexStreakItem(streak.id, streak.name, count, count, streak.photoUri)),
            dayNote = day?.remark.orEmpty(),
            photoUri = day?.photoUri,
            calendars = listOfNotNull(source),
            calendarDay = dateKey,
            calendarToday = today,
            photos = others
        )
    }
}
