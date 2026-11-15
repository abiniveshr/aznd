package com.ashrdev.aznd.ui.common.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import com.ashrdev.aznd.domain.streaks.addDaysKey
import com.ashrdev.aznd.domain.streaks.dayKeysBetween
import com.ashrdev.aznd.ui.calendar.todayDayKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Placeholder content for the preset editor: every block type has something to show, so a card can be designed
 * without a real workout or streak behind it. It is unmistakably sample data (Sample workout, Exercise A to D, Streak A
 * to C, round numbers, grey placeholder pictures); nothing in it reads like a real person's log. Its calendar days cover
 * [CALENDAR_DAYS] days back, so any month, quadrant or year a card is set to shows highlighted days. The pictures are generated once as PNG files in cacheDir/flex_sample
 * (referenced by file uris, which [FlexBitmaps.load] reads through the content resolver like any other uri).
 */
object FlexSampleContent {
    private const val DIR = "flex_sample"

    /** How far back the sample calendar days reach (about 2.2 years): every period a card can be set to has highlights. */
    private const val CALENDAR_DAYS = 800

    /** Streak A slips every this many days (counting back from today), so its current run is STREAK_PERIOD - 1 days. */
    private const val STREAK_PERIOD = 11
    private val mutex = Mutex()

    @Volatile
    private var cached: FlexContent? = null

    /** True for a uri of one of the placeholder pictures (so a template never keeps one on a real card, see [appliedTo]). */
    fun isSampleUri(uri: String?): Boolean = uri != null && uri.contains("/$DIR/")

    /**
     * Placeholder content where every [FlexBlockType] has data. Generates the pictures on first use (on
     * Dispatchers.IO) and remembers the result; safe to call again and from several places.
     */
    suspend fun load(context: Context): FlexContent {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: withContext(Dispatchers.IO) { build(context.applicationContext) }.also { cached = it }
        }
    }

    /**
     * [content] with the header of the subject a blueprint is for, so the Title card of an editor on sample data reads like the real
     * one: Workout "WORKOUT / Sample workout", Routine "ROUTINE / Sample routine", Exercise "EXERCISE / Exercise A", Streak day
     * "STREAK A / Day 10". A preset ([source] null or HOME) has no subject: no kicker, the card's own text is its title.
     */
    fun forSubject(content: FlexContent, source: FlexPreset?): FlexContent = when (source) {
        FlexPreset.WORKOUT -> content.copy(kicker = "WORKOUT", title = "Sample workout", subtitle = "Sample content")
        FlexPreset.ROUTINE -> content.copy(kicker = "ROUTINE", title = "Sample routine", subtitle = "4 exercises \u00b7 11 sets")
        FlexPreset.EXERCISE -> content.copy(kicker = "EXERCISE", title = "Exercise A", subtitle = "16 sessions")
        FlexPreset.STREAK_DAY -> content.copy(kicker = "STREAK A", title = "Day 10", subtitle = "")
        FlexPreset.HOME, null -> content.copy(kicker = "", title = "Sample workout", subtitle = "")
    }

    /**
     * The card a new preset starts with: a title, a photo, the top lift as a hero number and a 1RM graph, on the
     * Midnight look. A block of a type the [content] has nothing for is left off.
     */
    fun blankSpec(content: FlexContent): FlexSpec {
        val wanted = listOf(
            starter(FlexBlockType.HEADER),
            starter(FlexBlockType.PHOTO),
            starter(FlexBlockType.WORKOUT_STATS, limit = 1, stat = FlexWorkoutStat.ONE_RM),
            starter(FlexBlockType.GRAPH, graph = FlexGraphConfig(listOf(FlexGraphLoader.exerciseId("Exercise A")), FlexGraphRange.MONTHS_6))
        ).filter { content.has(it.type) }
        val blocks = wanted.mapIndexed { i, b -> b.copy(id = i + 1) }
        return FlexSpec(flexStyleOf(FlexPalettes.Midnight, FlexAspect.PORTRAIT), blocks)
    }

    private fun starter(
        type: FlexBlockType,
        limit: Int = 3,
        stat: FlexWorkoutStat = FlexWorkoutStat.TOP_SET,
        graph: FlexGraphConfig = FlexGraphConfig()
    ) = FlexBlock(id = 0, type = type, enabled = true, variant = 0, keys = null, limit = limit, text = "", flag = true, stat = stat, graph = graph)

    // ---------------------------------------------------------------------------------------------

    private fun build(context: Context): FlexContent {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val main = picture(dir, "sample_photo_main.png", 1200, 900, Color.rgb(150, 156, 170), Color.rgb(96, 102, 118), "Sample photo", Shape.PLACEHOLDER)
        val p1 = picture(dir, "sample_photo_1.png", 900, 1200, Color.rgb(150, 156, 170), Color.rgb(96, 102, 118), "Sample photo 1", Shape.PLACEHOLDER)
        val p2 = picture(dir, "sample_photo_2.png", 1200, 900, Color.rgb(150, 156, 170), Color.rgb(96, 102, 118), "Sample photo 2", Shape.PLACEHOLDER)
        val p3 = picture(dir, "sample_photo_3.png", 1000, 1000, Color.rgb(150, 156, 170), Color.rgb(96, 102, 118), "Sample photo 3", Shape.PLACEHOLDER)
        val model = picture(dir, "sample_model.png", 600, 900, Color.TRANSPARENT, Color.TRANSPARENT, "Sample model", Shape.FIGURE)
        fun uri(f: File) = Uri.fromFile(f).toString()

        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val today = todayDayKey()
        val graphs = sampleGraphs(now, day)

        // Streak A: kept every day except a slip every 11th day back from today, so its current run is 10 days (the graph
        // below follows the same rule). Workout days: Mon/Wed/Fri-like, three a week. Both reach CALENDAR_DAYS back.
        val firstDay = addDaysKey(today, -(CALENDAR_DAYS - 1))
        val allDays = dayKeysBetween(firstDay, today)
        val keptDays = allDays.filterIndexed { i, _ -> (allDays.size - 1 - i) % STREAK_PERIOD != STREAK_PERIOD - 1 }
        val trained = allDays.filterIndexed { i, _ -> i % 7 in intArrayOf(0, 2, 4) }

        return FlexContent(
            kicker = "WORKOUT",
            title = "Sample workout",
            subtitle = "Sample content",
            dateText = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(now)),
            streaks = listOf(
                FlexStreakItem(1L, "Streak A", 10, 10),
                FlexStreakItem(2L, "Streak B", 20, 30),
                FlexStreakItem(3L, "Streak C", 5, 50)
            ),
            lifts = listOf(
                FlexLiftItem("Exercise A", 100.0, 5, 8.0, 120.0, now - 1 * day),
                FlexLiftItem("Exercise B", 80.0, 8, 8.0, 100.0, now - 2 * day),
                FlexLiftItem("Exercise C", 60.0, 10, null, 80.0, now - 3 * day),
                FlexLiftItem("Exercise D", 40.0, 12, null, 60.0, now - 4 * day)
            ),
            exercises = listOf(
                FlexExerciseItem(
                    "Exercise A",
                    listOf(FlexSetLine(60.0, 10, rpe = 7.0), FlexSetLine(80.0, 8, rpe = 8.0), FlexSetLine(100.0, 5, rpe = 9.0)),
                    bestIndex = 2
                ),
                FlexExerciseItem(
                    "Exercise B",
                    listOf(FlexSetLine(40.0, 10, rpe = 7.0), FlexSetLine(50.0, 8, rpe = 8.0)),
                    bestIndex = 1
                ),
                FlexExerciseItem(
                    "Exercise C",
                    listOf(FlexSetLine(30.0, 12), FlexSetLine(30.0, 10), FlexSetLine(30.0, 10)),
                    bestIndex = 0
                ),
                FlexExerciseItem("Exercise D", listOf(FlexSetLine(seconds = 60), FlexSetLine(seconds = 90)), bestIndex = 1)
            ),
            routine = listOf(
                FlexRoutineItem("Exercise A", 3, "3 × 5–8"),
                FlexRoutineItem("Exercise B + Exercise C", 3, "3 × 10", "Superset"),
                FlexRoutineItem("Exercise D", 3, "3 × 12", "Drop set"),
                FlexRoutineItem("Exercise E", 2, null)
            ),
            stats = listOf(
                FlexStat("Duration", "1:00:00"),
                FlexStat("Volume", "10,000 kg"),
                FlexStat("Sets", "10"),
                FlexStat("Exercises", "4")
            ),
            graphs = graphs,
            dayNote = "Sample note",
            photoUri = uri(main),
            calendars = listOf(
                FlexCalendarSource("streak:1", "Streak A", keptDays.associateWith { FlexDay() }, firstDay),
                FlexCalendarSource("workouts", "Workout days", trained.associateWith { FlexDay() }, firstDay)
            ),
            calendarDay = today,
            calendarToday = today,
            photos = listOf(
                FlexPhoto(uri(p1), "Sample photo 1"),
                FlexPhoto(uri(p2), "Sample photo 2"),
                FlexPhoto(uri(p3), "Sample photo 3")
            ),
            modelUri = uri(model)
        )
    }

    /**
     * A plausible series for every variable the real loader can produce ([FlexGraphLoader]): the dashboard's ids, Exercises A to D's 1RM
     * and Streak A, over the last months.
     */
    private fun sampleGraphs(now: Long, day: Long): List<FlexGraphSeries> {
        fun daily(days: Int, f: (Int) -> Double) =
            (0 until days).map { i -> FlexChartPoint(now - (days - 1 - i) * day, f(i)) }
        fun every(step: Int, count: Int, f: (Int) -> Double) =
            (0 until count).map { i -> FlexChartPoint(now - (count - 1 - i).toLong() * step * day, f(i)) }
        fun wave(i: Int, mid: Double, amp: Double, period: Double = 6.0): Double = mid + amp * Math.sin(i * 2.0 * Math.PI / period)
        fun series(id: String, label: String, unit: String, bars: Boolean, group: String, points: List<FlexChartPoint>, zero: Boolean = true) =
            FlexGraphSeries(id, label, unit, bars, group, points, zero)

        val out = ArrayList<FlexGraphSeries>()
        val nutrition = FlexGraphLoader.GROUP_NUTRITION
        val body = FlexGraphLoader.GROUP_BODY
        val workout = FlexGraphLoader.GROUP_WORKOUT
        out += series("calories", "Calories eaten", "kcal", true, nutrition, daily(60) { wave(it, 2000.0, 300.0) })
        out += series("protein", "Protein", "g", true, nutrition, daily(60) { wave(it, 150.0, 20.0, 5.0) })
        out += series("carbs", "Carbs", "g", true, nutrition, daily(60) { wave(it, 250.0, 40.0, 7.0) })
        out += series("fat", "Fat", "g", true, nutrition, daily(60) { wave(it, 70.0, 10.0, 4.0) })
        out += series("burned", "Calories burned", "kcal", true, nutrition, every(2, 40) { wave(it, 400.0, 100.0) })
        out += series("bodyweight", "Bodyweight", "kg", false, body, every(3, 40) { 80.0 - it * 0.1 + wave(it, 0.0, 0.4, 4.0) }, zero = false)
        out += series("body_fat", "Body fat", "%", false, body, every(7, 16) { 20.0 - it * 0.15 + wave(it, 0.0, 0.2, 3.0) }, zero = false)
        out += series("volume", "Workout volume", "kg", true, workout, every(2, 40) { wave(it, 10000.0, 2000.0) })
        out += series("workout_minutes", "Workout time", "min", true, workout, every(2, 40) { wave(it, 60.0, 10.0, 5.0) })
        out += series("sets", "Sets", "sets", true, workout, every(2, 40) { wave(it, 20.0, 4.0, 4.0) })

        val lifts = listOf(
            Triple("Exercise A", 105.0, 1.0), Triple("Exercise B", 85.0, 0.35),
            Triple("Exercise C", 70.0, 0.5), Triple("Exercise D", 50.0, 0.5)
        )
        for ((name, start, step) in lifts) {
            out += series(
                FlexGraphLoader.exerciseId(name), "$name 1RM", "kg", false, FlexGraphLoader.GROUP_EXERCISE,
                every(7, 16) { start + it * step + wave(it, 0.0, 1.5, 3.0) }, zero = false
            )
        }

        // Streak A with slips: the run climbs a day at a time and falls to zero on a slip (every STREAK_PERIOD days back
        // from today, as in the calendar), so it ends on a run of STREAK_PERIOD - 1 days.
        val streakDays = 45
        val progress = ArrayList<FlexChartPoint>()
        val runs = ArrayList<FlexChartPoint>()
        var run = 0
        for (i in 0 until streakDays) {
            val t = now - (streakDays - 1 - i) * day
            if ((streakDays - 1 - i) % STREAK_PERIOD == STREAK_PERIOD - 1) {
                if (run > 0) {
                    progress += FlexChartPoint(t, run.toDouble())
                    runs += FlexChartPoint(t - day, run.toDouble())
                }
                run = 0
            } else {
                run++
            }
            progress += FlexChartPoint(t, run.toDouble())
        }
        if (run > 0) runs += FlexChartPoint(now, run.toDouble())
        val streaks = FlexGraphLoader.GROUP_STREAKS
        out += series(FlexGraphLoader.streakId(1L, "progress"), "Streak A progress", "days", false, streaks, progress)
        out += series(FlexGraphLoader.streakId(1L, "runs"), "Streak A runs", "days", true, streaks, runs)
        return out
    }

    private enum class Shape { PLACEHOLDER, FIGURE }

    /** Writes the picture once: an existing non-empty file is reused. */
    private fun picture(dir: File, name: String, w: Int, h: Int, top: Int, bottom: Int, label: String, shape: Shape): File {
        val file = File(dir, name)
        if (file.exists() && file.length() > 0L) return file
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            draw(Canvas(bmp), w.toFloat(), h.toFloat(), top, bottom, label, shape)
            val tmp = File(dir, "$name.tmp")
            FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        } finally {
            bmp.recycle()
        }
        return file
    }

    private fun draw(c: Canvas, w: Float, h: Float, top: Int, bottom: Int, label: String, shape: Shape) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (shape != Shape.FIGURE) {
            paint.shader = LinearGradient(0f, 0f, w, h, top, bottom, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        }
        when (shape) {
            Shape.PLACEHOLDER -> {
                // The classic placeholder: a frame with both diagonals, over a faint grid, so it can't pass for a photo.
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = minOf(w, h) * 0.004f
                paint.color = Color.argb(45, 255, 255, 255)
                val step = minOf(w, h) / 8f
                var x = step
                while (x < w) { c.drawLine(x, 0f, x, h, paint); x += step }
                var y = step
                while (y < h) { c.drawLine(0f, y, w, y, paint); y += step }
                paint.strokeWidth = minOf(w, h) * 0.008f
                paint.color = Color.argb(130, 255, 255, 255)
                c.drawLine(0f, 0f, w, h, paint)
                c.drawLine(w, 0f, 0f, h, paint)
                val inset = minOf(w, h) * 0.03f
                c.drawRect(inset, inset, w - inset, h - inset, paint)
                paint.style = Paint.Style.FILL
            }
            Shape.FIGURE -> {
                // A plain body silhouette on a transparent background, like a snapshot of the 3D model.
                paint.color = Color.argb(235, 200, 208, 224)
                c.drawCircle(w / 2f, h * 0.14f, w * 0.11f, paint)
                c.drawRoundRect(RectF(w * 0.3f, h * 0.27f, w * 0.7f, h * 0.62f), w * 0.1f, w * 0.1f, paint)
                c.drawRoundRect(RectF(w * 0.12f, h * 0.28f, w * 0.27f, h * 0.58f), w * 0.07f, w * 0.07f, paint)
                c.drawRoundRect(RectF(w * 0.73f, h * 0.28f, w * 0.88f, h * 0.58f), w * 0.07f, w * 0.07f, paint)
                c.drawRoundRect(RectF(w * 0.32f, h * 0.6f, w * 0.48f, h * 0.93f), w * 0.06f, w * 0.06f, paint)
                c.drawRoundRect(RectF(w * 0.52f, h * 0.6f, w * 0.68f, h * 0.93f), w * 0.06f, w * 0.06f, paint)
            }
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (shape == Shape.FIGURE) Color.argb(220, 120, 130, 160) else Color.WHITE
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textSize = minOf(w, h) * 0.07f
            textAlign = Paint.Align.CENTER
            if (shape != Shape.FIGURE) setShadowLayer(8f, 0f, 2f, Color.argb(120, 0, 0, 0))
        }
        if (shape != Shape.FIGURE) {
            // A plate behind the words so they read over the diagonals.
            val half = text.measureText(label) / 2f + text.textSize
            paint.color = Color.argb(150, 40, 44, 56)
            c.drawRoundRect(RectF(w / 2f - half, h / 2f - text.textSize * 1.3f, w / 2f + half, h / 2f + text.textSize * 0.7f), text.textSize * 0.5f, text.textSize * 0.5f, paint)
            c.drawText(label, w / 2f, h / 2f + text.textSize * 0.3f, text)
        } else {
            c.drawText(label, w / 2f, h * 0.98f, text)
        }
    }
}
