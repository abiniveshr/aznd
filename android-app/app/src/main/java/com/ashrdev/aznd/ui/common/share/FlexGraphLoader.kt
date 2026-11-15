package com.ashrdev.aznd.ui.common.share

import android.content.Context
import com.ashrdev.aznd.data.AppDatabase
import com.ashrdev.aznd.data.body.RoomBodyweightProvider
import com.ashrdev.aznd.data.streaks.parseMilestones
import com.ashrdev.aznd.data.workout.TrainingRepository
import com.ashrdev.aznd.data.workout.WorkoutSettingsStore
import com.ashrdev.aznd.domain.GraphMode
import com.ashrdev.aznd.domain.GraphStyle
import com.ashrdev.aznd.domain.macro.MacroStats
import com.ashrdev.aznd.domain.streaks.computeStreakStats
import com.ashrdev.aznd.domain.workout.E1rmCalculator
import com.ashrdev.aznd.domain.workout.SessionStatsCalculator
import com.ashrdev.aznd.ui.calendar.todayDayKey
import com.ashrdev.aznd.ui.dashboard.DashboardSeries
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.ui.streaks.streakGraphModes
import com.ashrdev.aznd.ui.workout.stats.allSessionSetPoints
import com.ashrdev.aznd.ui.workout.stats.buildSessionE1rmPoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Every graph the app has, as [FlexGraphSeries] for the Graph card: the dashboard's variables (same ids as the
 * dashboard's own graph modes), each exercise's estimated 1RM over time, and each streak's progress and runs.
 * Read straight from the app's database (the same sources the dashboard reads) off the main thread.
 */
internal object FlexGraphLoader {
    const val GROUP_NUTRITION = "Nutrition"
    const val GROUP_BODY = "Body"
    const val GROUP_WORKOUT = "Workout"
    const val GROUP_EXERCISE = "Exercise 1RM"
    const val GROUP_STREAKS = "Streaks"

    /** The id of an exercise's 1RM series. */
    fun exerciseId(exerciseName: String): String = "e1rm:$exerciseName"

    /** The id of one of a streak's graphs ([graph] is "progress" or "runs"). */
    fun streakId(streakId: Long, graph: String): String = "streak:$streakId:$graph"

    private fun groupOf(dashboardId: String): String = when (dashboardId) {
        DashboardSeries.CALORIES, DashboardSeries.PROTEIN, DashboardSeries.CARBS, DashboardSeries.FAT, DashboardSeries.BURNED -> GROUP_NUTRITION
        DashboardSeries.BODYWEIGHT, DashboardSeries.BODY_FAT -> GROUP_BODY
        else -> GROUP_WORKOUT
    }

    /** One of the app's graphs as a series (the app's [GraphMode] and its points, without the readout details). */
    fun series(mode: GraphMode, id: String, label: String, group: String): FlexGraphSeries =
        FlexGraphSeries(
            id = id, label = label, unit = mode.unit, bars = mode.style == GraphStyle.BARS, group = group,
            points = mode.points.map { FlexChartPoint(it.timeMs, it.value) }, startAtZero = mode.startAtZero
        )

    suspend fun load(context: Context, streaks: StreakViewModel): List<FlexGraphSeries> = withContext(Dispatchers.Default) {
        val db = AppDatabase.getInstance(context)
        val out = ArrayList<FlexGraphSeries>()
        val repository = TrainingRepository(
            db.exerciseDao(), db.templateDao(), db.sessionDao(),
            bodyweight = RoomBodyweightProvider(db.bodyDao()), legacy = db.workoutDao()
        )
        val rows = runCatching { repository.loggedSetData() }.getOrDefault(emptyList())

        // The dashboard's variables, with its ids.
        runCatching {
            val food = db.foodDao().observeAll().first()
            val weights = db.bodyDao().observeWeights().first()
            val measurements = db.bodyDao().observeMeasurements().first()
            val sessions = db.workoutDao().getSessions().first()
            val trackRpe = WorkoutSettingsStore(context).settings.value.trackRpe
            val burnt = MacroStats.burntByDay(
                SessionStatsCalculator.all(rows, repository.statsExercises(), E1rmCalculator(rpeTracking = trackRpe))
            )
            DashboardSeries.modes(food, weights, measurements, sessions, burnt).forEach {
                out += series(it, it.id, it.label, groupOf(it.id))
            }
        }

        // Estimated 1RM of every exercise with history.
        runCatching {
            val byExercise = rows.groupBy { it.exerciseId }
            val found = ArrayList<FlexGraphSeries>()
            for (ex in db.exerciseDao().allOnce()) {
                val own = byExercise[ex.id] ?: continue
                val points = buildSessionE1rmPoints(allSessionSetPoints(ex, own))
                if (points.isEmpty()) continue
                found += FlexGraphSeries(
                    id = exerciseId(ex.name), label = "${ex.name} 1RM", unit = "kg", bars = false, group = GROUP_EXERCISE,
                    points = points.map { FlexChartPoint(it.startedAt, it.e1rm) }, startAtZero = false
                )
            }
            out += found.sortedBy { it.label.lowercase() }
        }

        // Each streak's progress and runs.
        runCatching {
            val today = todayDayKey()
            val tracks = streaks.snapshot.value?.tracks.orEmpty()
            for (snap in streaks.streakSnapshots()) {
                val st = snap.streak
                val track = tracks[st.id]
                val breaks: Set<String> = track?.tracked?.breaks ?: streaks.breaksForStreak(st.id).first().toSet()
                val asOf = track?.tracked?.asOf ?: today
                val milestones = parseMilestones(st.milestones)
                val stats = computeStreakStats(st.startDate, breaks, milestones, asOf)
                streakGraphModes(stats, milestones).forEach { m ->
                    out += series(m, streakId(st.id, m.id), "${st.name} ${m.label.lowercase()}", GROUP_STREAKS)
                }
            }
        }
        out
    }
}
