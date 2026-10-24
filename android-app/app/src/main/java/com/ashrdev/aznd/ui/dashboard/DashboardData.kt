package com.ashrdev.aznd.ui.dashboard

import com.ashrdev.aznd.data.body.BodyMeasurement
import com.ashrdev.aznd.data.body.BodyWeightEntry
import com.ashrdev.aznd.data.macro.FoodEntry
import com.ashrdev.aznd.data.workout.SessionWithSets
import com.ashrdev.aznd.data.workout.SetMode
import com.ashrdev.aznd.data.workout.WorkoutWithExercises
import com.ashrdev.aznd.domain.GraphMode
import com.ashrdev.aznd.domain.GraphPoint
import com.ashrdev.aznd.domain.GraphStyle
import com.ashrdev.aznd.domain.GraphWindow
import com.ashrdev.aznd.domain.body.MeasurementSite
import com.ashrdev.aznd.domain.body.PLACEHOLDER_BODYWEIGHT_KG
import com.ashrdev.aznd.domain.macro.CalorieGoal
import com.ashrdev.aznd.domain.macro.CalorieTarget
import com.ashrdev.aznd.domain.macro.FoodLog
import com.ashrdev.aznd.domain.macro.MacroGoals
import com.ashrdev.aznd.domain.macro.MacroStats
import com.ashrdev.aznd.domain.macro.MacroTotals
import com.ashrdev.aznd.ui.streaks.StreakWithCount
import com.ashrdev.aznd.ui.workout.TopExerciseStat
import com.ashrdev.aznd.ui.workout.formatClock
import com.ashrdev.aznd.ui.workout.stats.formatE1rm
import com.ashrdev.aznd.ui.workout.stats.formatTopSet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class TodayCalories(
    val totals: MacroTotals,
    val burnt: Double,
    val target: Double?,
    val goals: MacroGoals
) {
    val allowance: Double get() = (target ?: 0.0) + burnt
    val left: Double get() = allowance - totals.calories
    val over: Boolean get() = allowance > 0.0 && totals.calories > allowance
}

class DashboardData(
    val streaks: List<StreakWithCount>,
    val topLifts: List<TopExerciseStat>,
    val sessions: List<SessionWithSets>,
    val workouts: List<WorkoutWithExercises>,
    val today: TodayCalories,
    val modes: List<GraphMode>,
    val nowMs: Long
) {
    val ranked: List<StreakWithCount> = streaks.sortedWith(compareByDescending<StreakWithCount> { it.currentStreak }.thenBy { it.streak.name.lowercase() })

    fun streakFor(w: DashWidget): StreakWithCount? =
        if (w.streakId >= 0) streaks.firstOrNull { it.streak.id == w.streakId }
        else ranked.filter { it.currentStreak > 0 }.getOrNull(w.rank - 1)

    fun liftFor(w: DashWidget): TopExerciseStat? = topLifts.getOrNull(w.rank - 1)

    fun modesFor(w: DashWidget): List<GraphMode> = w.variables.mapNotNull { id -> modes.firstOrNull { it.id == id } }

    fun workoutName(id: Long): String? = workouts.firstOrNull { it.workout.id == id }?.workout?.name
}

object DashboardSeries {
    const val CALORIES = "calories"
    const val PROTEIN = "protein"
    const val CARBS = "carbs"
    const val FAT = "fat"
    const val BURNED = "burned"
    const val BODYWEIGHT = "bodyweight"
    const val BODY_FAT = "body_fat"
    const val VOLUME = "volume"
    const val MINUTES = "workout_minutes"
    const val SETS = "sets"

    fun today(food: List<FoodEntry>, burnt: Map<Long, Double>, weights: List<BodyWeightEntry>, measurements: List<BodyMeasurement>, goal: CalorieGoal, advancedOffset: Int, proteinG: Int, carbsG: Int, fatG: Int, nowMs: Long): TodayCalories {
        val day = MacroStats.dayStart(nowMs)
        val totals = food.filter { MacroStats.dayStart(it.eatenAt) == day }
            .fold(MacroTotals()) { acc, e -> acc + MacroTotals(e.calories, e.proteinG, e.carbsG, e.fatG) }
        val weight = weights.firstOrNull()?.weightKg ?: PLACEHOLDER_BODYWEIGHT_KG
        val bodyFat = measurements.firstOrNull { it.site == MeasurementSite.BODY_FAT.name }?.valueCm
        val target = CalorieTarget.target(weight, bodyFat, goal, advancedOffset)
        return TodayCalories(totals, burnt[day] ?: 0.0, target, MacroGoals.resolve(target, proteinG, carbsG, fatG))
    }

    fun modes(food: List<FoodEntry>, weights: List<BodyWeightEntry>, measurements: List<BodyMeasurement>, sessions: List<SessionWithSets>, burnt: Map<Long, Double>): List<GraphMode> {
        val byDay = MacroStats.totalsByDay(food.map { FoodLog(it.eatenAt, it.calories, it.proteinG, it.carbsG, it.fatG) })
        val days = byDay.keys.sorted()
        fun daily(f: (MacroTotals) -> Double) = days.map { GraphPoint(it, f(byDay.getValue(it))) }
        val done = sessions.filter { it.sets.isNotEmpty() }.sortedBy { it.session.startedAt }
        return listOf(
            GraphMode(CALORIES, "Calories eaten", "kcal", GraphStyle.BARS, daily { it.calories }),
            GraphMode(PROTEIN, "Protein", "g", GraphStyle.BARS, daily { it.proteinG }),
            GraphMode(CARBS, "Carbs", "g", GraphStyle.BARS, daily { it.carbsG }),
            GraphMode(FAT, "Fat", "g", GraphStyle.BARS, daily { it.fatG }),
            GraphMode(BURNED, "Calories burned", "kcal", GraphStyle.BARS, burnt.keys.sorted().map { GraphPoint(it, burnt.getValue(it)) }),
            GraphMode(BODYWEIGHT, "Bodyweight", "kg", GraphStyle.LINE, weights.sortedBy { it.measuredAt }.map { GraphPoint(it.measuredAt, it.weightKg) }, startAtZero = false),
            GraphMode(
                BODY_FAT, "Body fat", "%", GraphStyle.LINE,
                measurements.filter { it.site == MeasurementSite.BODY_FAT.name }.sortedBy { it.measuredAt }.map { GraphPoint(it.measuredAt, it.valueCm) },
                startAtZero = false
            ),
            GraphMode(VOLUME, "Workout volume", "kg", GraphStyle.BARS, done.map { s -> GraphPoint(s.session.startedAt, volumeOf(s), detail = s.session.workoutName) }),
            GraphMode(MINUTES, "Workout time", "min", GraphStyle.BARS, done.map { s -> GraphPoint(s.session.startedAt, minutesOf(s), detail = s.session.workoutName) }),
            GraphMode(SETS, "Sets", "sets", GraphStyle.BARS, done.map { s -> GraphPoint(s.session.startedAt, s.sets.size.toDouble(), detail = s.session.workoutName) })
        )
    }

    fun volumeOf(s: SessionWithSets): Double = s.sets.filter { it.mode == SetMode.REPS }.sumOf { it.weight * it.value }

    fun minutesOf(s: SessionWithSets): Double = ((s.session.finishedAt - s.session.startedAt) / 60_000.0).coerceAtLeast(0.0)
}

object DashboardText {
    private const val DAY_MS = 86_400_000L

    fun streak(w: DashWidget, data: DashboardData): StreakContent {
        val s = data.streakFor(w)
        if (s == null) {
            val name = if (w.streakId >= 0) "Streak removed" else if (w.rank == 1) "No streak running" else "Streak #${w.rank}"
            return StreakContent(name, "–", "Add one")
        }
        return StreakContent(s.streak.name, s.currentStreak.toString(), if (s.currentStreak == 1) "day" else "days")
    }

    fun workout(data: DashboardData): WorkoutContent {
        val s = data.sessions.firstOrNull() ?: return WorkoutContent("Latest workout", "No workouts yet", "Tap to start one", emptyList())
        val minutes = DashboardSeries.minutesOf(s).roundToInt()
        val volume = DashboardSeries.volumeOf(s)
        return WorkoutContent(
            "Latest workout",
            s.session.workoutName.ifBlank { "Workout" },
            whenText(s.session.startedAt, data.nowMs),
            listOf(
                WorkoutFigure("Duration", if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "$minutes min"),
                WorkoutFigure("Sets", s.sets.size.toString()),
                WorkoutFigure("Volume", "${GraphWindow.formatValue(volume)} kg")
            )
        )
    }

    fun whenText(atMs: Long, nowMs: Long): String {
        val days = ((MacroStats.dayStart(nowMs) - MacroStats.dayStart(atMs)) / DAY_MS).toInt()
        return when {
            days <= 0 -> "Today · ${formatClock(atMs)}"
            days == 1 -> "Yesterday · ${formatClock(atMs)}"
            days < 7 -> "$days days ago"
            else -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(atMs))
        }
    }

    fun shortcut(w: DashWidget, data: DashboardData): ShortcutContent = ShortcutContent(
        if (w.shortcut == DashShortcut.ROUTINE) data.workoutName(w.workoutId) ?: "Routine" else w.shortcut.label
    )

    fun lift(w: DashWidget, data: DashboardData): LiftContent {
        val l = data.liftFor(w) ?: return LiftContent("Top lift #${w.rank}", "No lifts yet", "–", "Log a session")
        return LiftContent("Top lift #${w.rank}", l.exerciseName, formatE1rm(l.bestPoint.e1rm), formatTopSet(l.bestPoint))
    }

    fun graphTitle(w: DashWidget, data: DashboardData): String {
        val modes = data.modesFor(w)
        return if (modes.isEmpty()) "Graph" else modes.joinToString(" vs ") { it.label }
    }

    fun shape(w: DashWidget, data: DashboardData, m: DashMeasure): DashShape {
        val f = w.size.factor
        return when (w.type) {
            DashWidgetType.CALORIES -> DashboardShapes.calories(f)
            DashWidgetType.STREAK -> DashboardShapes.streak(streak(w, data), m, f)
            DashWidgetType.LATEST_WORKOUT -> DashboardShapes.workout(workout(data), m, f)
            DashWidgetType.GRAPH -> DashboardShapes.graph(f)
            DashWidgetType.BODY_MODEL -> DashboardShapes.model(f)
            DashWidgetType.SHORTCUT -> DashboardShapes.shortcut(shortcut(w, data), m, f)
            DashWidgetType.TOP_LIFT -> DashboardShapes.lift(lift(w, data), m, f)
        }
    }

    fun signature(w: DashWidget, data: DashboardData): String = buildString {
        append(w.id).append('|').append(w.type.id).append('|').append(w.size.id).append('|')
        when (w.type) {
            DashWidgetType.STREAK -> streak(w, data).let { append(it.name).append(it.days).append(it.unit) }
            DashWidgetType.LATEST_WORKOUT -> workout(data).let { c -> append(c.name).append(c.whenText); c.figures.forEach { append(it.value) } }
            DashWidgetType.SHORTCUT -> append(shortcut(w, data).label)
            DashWidgetType.TOP_LIFT -> lift(w, data).let { append(it.name).append(it.e1rm).append(it.topSet) }
            else -> Unit
        }
    }
}
