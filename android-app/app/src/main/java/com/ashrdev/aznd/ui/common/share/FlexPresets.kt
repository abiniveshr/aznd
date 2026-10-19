package com.ashrdev.aznd.ui.common.share

import android.net.Uri
import com.ashrdev.aznd.data.workout.SetMode
import com.ashrdev.aznd.domain.ExerciseType
import com.ashrdev.aznd.domain.SetKind
import com.ashrdev.aznd.domain.targetLabel
import com.ashrdev.aznd.ui.navigation.Destination
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.ui.streaks.displayDate
import com.ashrdev.aznd.ui.workout.TopExerciseStat
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import com.ashrdev.aznd.ui.workout.estimatedOneRepMax
import com.ashrdev.aznd.ui.workout.formatDuration
import com.ashrdev.aznd.ui.workout.loadExerciseStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/**
 * One preset per place a card can be shared from. A preset decides which blocks a card starts with and
 * how it looks; the person's edits are remembered per preset (see [FlexStore]). The arguments a
 * preset needs travel in the route: [a] / [b] are ids, [s] is a text key.
 *
 *  HOME        none                         the general "flex" card: streaks, top lifts
 *  WORKOUT     a = session id               one finished workout
 *  ROUTINE     a = workout id               a saved routine (template)
 *  EXERCISE    a = workout id, b = exercise id   one exercise's 1RM progress
 *  STREAK_DAY  a = streak id, s = date      one saved streak day
 */
enum class FlexPreset(val id: String, val label: String) {
    HOME("home", "Flex"),
    WORKOUT("workout", "Workout"),
    ROUTINE("routine", "Routine"),
    EXERCISE("exercise", "Exercise"),
    STREAK_DAY("streak_day", "Streak day");

    companion object {
        fun fromId(id: String?): FlexPreset = entries.firstOrNull { it.id == id } ?: HOME
    }
}

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
    text: String = ""
) = FlexBlock(id = id, type = type, enabled = enabled, variant = variant, keys = null, limit = limit, text = text, flag = flag)

/** The card a preset starts with, before the person changes anything. */
fun FlexPreset.defaultSpec(content: FlexContent): FlexSpec = when (this) {
    FlexPreset.HOME -> FlexSpec(
        flexStyleOf(FlexPalettes.Midnight, FlexAspect.PORTRAIT),
        listOf(
            block(1, FlexBlockType.HEADER),
            block(2, FlexBlockType.STREAKS, variant = 0, limit = 3),
            block(3, FlexBlockType.ONE_RM, variant = 1, limit = 3),
            block(4, FlexBlockType.TOP_SET, variant = 1, limit = 2),
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
                block(2, FlexBlockType.SESSION_STATS, variant = 0),
                block(3, FlexBlockType.TOP_SET, variant = 0, limit = 1),
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
            block(2, FlexBlockType.ONE_RM, variant = 0, limit = 1),
            block(3, FlexBlockType.CHART, variant = 0),
            block(4, FlexBlockType.TOP_SET, enabled = false, variant = 1, limit = 1)
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
        streaks: StreakViewModel
    ): FlexContent = when (preset) {
        FlexPreset.HOME -> home(workout, streaks)
        FlexPreset.WORKOUT -> workoutSession(workout, a)
        FlexPreset.ROUTINE -> routine(workout, a)
        FlexPreset.EXERCISE -> exercise(workout, a, b)
        FlexPreset.STREAK_DAY -> streakDay(streaks, a, s)
    }

    private fun date(ms: Long): String = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ms))

    private fun liftsFrom(top: List<TopExerciseStat>): List<FlexLiftItem> =
        top.groupBy { it.exerciseName }.values
            .mapNotNull { list -> list.maxByOrNull { it.bestPoint.e1rm } }
            .sortedByDescending { it.bestPoint.e1rm }
            .map {
                val p = it.bestPoint
                FlexLiftItem(it.exerciseName, p.topSetWeight, p.topSetReps, p.topSetRpe, p.e1rm, p.startedAt)
            }

    private suspend fun home(workout: WorkoutViewModel, streaks: StreakViewModel): FlexContent {
        val streakItems = streaks.streakSnapshots()
            .map { FlexStreakItem(it.streak.id, it.streak.name, it.current, it.longest, it.streak.photoUri) }
            .sortedByDescending { it.days }
        return FlexContent(
            kicker = "Flex",
            title = "My progress",
            dateText = date(System.currentTimeMillis()),
            streaks = streakItems,
            lifts = liftsFrom(workout.getTopExercises(40))
        )
    }

    private suspend fun workoutSession(workout: WorkoutViewModel, sessionId: Long): FlexContent {
        val data = workout.getSession(sessionId) ?: return FlexContent(kicker = "Workout", title = "Workout")
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

    private suspend fun routine(workout: WorkoutViewModel, workoutId: Long): FlexContent {
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
            chart = points.map { FlexChartPoint(it.startedAt, it.e1rm) },
            chartLabel = "Estimated 1RM (kg)"
        )
    }

    private suspend fun streakDay(streaks: StreakViewModel, streakId: Long, dateKey: String): FlexContent {
        val streak = streaks.getStreak(streakId) ?: return FlexContent(kicker = "Streak", title = "Streak")
        val day = streaks.getSavedDay(streakId, dateKey)
        val count = day?.streakCountAtSave ?: 0
        return FlexContent(
            kicker = streak.name,
            title = "Day $count",
            dateText = displayDate(dateKey),
            streaks = listOf(FlexStreakItem(streak.id, streak.name, count, count, streak.photoUri)),
            dayNote = day?.remark.orEmpty(),
            photoUri = day?.photoUri
        )
    }
}
