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
 * without a real workout or streak behind it. The pictures are generated once as PNG files in cacheDir/flex_sample
 * (referenced by file uris, which [FlexBitmaps.load] reads through the content resolver like any other uri).
 */
object FlexSampleContent {
    private const val DIR = "flex_sample"
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
     * The card a new preset starts with: a title, a photo, the top lift as a hero number and the 1RM chart, on the
     * Midnight look. A block of a type the [content] has nothing for is left off.
     */
    fun blankSpec(content: FlexContent): FlexSpec {
        val wanted = listOf(
            starter(FlexBlockType.HEADER),
            starter(FlexBlockType.PHOTO),
            starter(FlexBlockType.ONE_RM, limit = 1),
            starter(FlexBlockType.CHART)
        ).filter { content.has(it.type) }
        val blocks = wanted.mapIndexed { i, b -> b.copy(id = i + 1) }
        return FlexSpec(flexStyleOf(FlexPalettes.Midnight, FlexAspect.PORTRAIT), blocks)
    }

    private fun starter(type: FlexBlockType, limit: Int = 3) =
        FlexBlock(id = 0, type = type, enabled = true, variant = 0, keys = null, limit = limit, text = "", flag = true)

    // ---------------------------------------------------------------------------------------------

    private fun build(context: Context): FlexContent {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val main = picture(dir, "photo_main.png", 1200, 900, Color.rgb(255, 138, 76), Color.rgb(124, 58, 237), "Sample photo", Shape.SUN)
        val p1 = picture(dir, "photo_1.png", 900, 1200, Color.rgb(61, 214, 208), Color.rgb(11, 110, 142), "Sample photo 1", Shape.HILLS)
        val p2 = picture(dir, "photo_2.png", 1200, 900, Color.rgb(255, 209, 102), Color.rgb(217, 72, 43), "Sample photo 2", Shape.SUN)
        val p3 = picture(dir, "photo_3.png", 1000, 1000, Color.rgb(92, 224, 160), Color.rgb(20, 68, 44), "Sample photo 3", Shape.HILLS)
        val model = picture(dir, "model.png", 600, 900, Color.TRANSPARENT, Color.TRANSPARENT, "Sample model", Shape.FIGURE)
        fun uri(f: File) = Uri.fromFile(f).toString()

        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val today = todayDayKey()
        val chartValues = listOf(100.0, 103.5, 102.0, 107.5, 110.0, 109.0, 114.5, 117.0, 116.0, 121.5)
        val chart = chartValues.mapIndexed { i, v -> FlexChartPoint(now - (chartValues.size - 1 - i) * 7 * day, v) }

        val runStart = addDaysKey(today, -45)
        val runDays = dayKeysBetween(runStart, today).filterIndexed { i, _ -> i % 9 != 5 }
        val trainStart = addDaysKey(today, -70)
        val trained = dayKeysBetween(trainStart, today).filterIndexed { i, _ -> i % 7 in intArrayOf(0, 2, 4) }

        return FlexContent(
            kicker = "WORKOUT",
            title = "Push day",
            subtitle = "Sample content",
            dateText = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(now)),
            streaks = listOf(
                FlexStreakItem(1L, "Morning run", 32, 45),
                FlexStreakItem(2L, "Read 20 pages", 18, 18),
                FlexStreakItem(3L, "No sugar", 7, 21)
            ),
            lifts = listOf(
                FlexLiftItem("Deadlift", 180.0, 3, 8.5, 198.0, now - 2 * day),
                FlexLiftItem("Squat", 150.0, 5, 8.0, 175.0, now - 4 * day),
                FlexLiftItem("Bench Press", 100.0, 5, 9.0, 117.5, now - 1 * day),
                FlexLiftItem("Overhead Press", 60.0, 6, null, 72.0, now - 6 * day)
            ),
            exercises = listOf(
                FlexExerciseItem(
                    "Bench Press",
                    listOf(FlexSetLine(80.0, 8, rpe = 7.0), FlexSetLine(90.0, 6, rpe = 8.0), FlexSetLine(100.0, 5, rpe = 9.0)),
                    bestIndex = 2
                ),
                FlexExerciseItem(
                    "Incline Dumbbell Press",
                    listOf(FlexSetLine(30.0, 10, rpe = 7.5), FlexSetLine(32.5, 8, rpe = 8.5)),
                    bestIndex = 1
                ),
                FlexExerciseItem(
                    "Triceps Pushdown",
                    listOf(FlexSetLine(35.0, 12), FlexSetLine(40.0, 10), FlexSetLine(40.0, 9)),
                    bestIndex = 1
                ),
                FlexExerciseItem("Plank", listOf(FlexSetLine(seconds = 60), FlexSetLine(seconds = 75)), bestIndex = 1)
            ),
            routine = listOf(
                FlexRoutineItem("Bench Press", 3, "3 × 5–8"),
                FlexRoutineItem("Incline Dumbbell Press + Lateral Raise", 3, "3 × 10–12", "Superset"),
                FlexRoutineItem("Triceps Pushdown", 3, "3 × 12", "Drop set"),
                FlexRoutineItem("Plank", 2, null)
            ),
            stats = listOf(
                FlexStat("Duration", "1:12:30"),
                FlexStat("Volume", "8,450 kg"),
                FlexStat("Sets", "11"),
                FlexStat("Exercises", "4")
            ),
            chart = chart,
            chartLabel = "Estimated 1RM (kg)",
            dayNote = "Felt strong today. Keep showing up.",
            photoUri = uri(main),
            calendars = listOf(
                FlexCalendarSource("streak:1", "Morning run", runDays.associateWith { FlexDay() }, runStart),
                FlexCalendarSource("workouts", "Workout days", trained.associateWith { FlexDay() }, trainStart)
            ),
            calendarDay = "",
            calendarToday = today,
            photos = listOf(
                FlexPhoto(uri(p1), "Sample · Day 1"),
                FlexPhoto(uri(p2), "Sample · Day 2"),
                FlexPhoto(uri(p3), "Sample · Day 3")
            ),
            modelUri = uri(model)
        )
    }

    private enum class Shape { SUN, HILLS, FIGURE }

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
            Shape.SUN -> {
                paint.color = Color.argb(200, 255, 255, 255)
                c.drawCircle(w * 0.7f, h * 0.3f, h * 0.12f, paint)
                paint.color = Color.argb(90, 0, 0, 0)
                hills(c, w, h, paint, 0.72f, 0.62f)
            }
            Shape.HILLS -> {
                paint.color = Color.argb(70, 0, 0, 0)
                hills(c, w, h, paint, 0.65f, 0.55f)
                paint.color = Color.argb(110, 0, 0, 0)
                hills(c, w, h, paint, 0.8f, 0.72f)
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
        c.drawText(label, w / 2f, if (shape == Shape.FIGURE) h * 0.98f else h * 0.92f, text)
    }

    /** Two overlapping rounded hills filling the picture below [baseY] (fractions of its height). */
    private fun hills(c: Canvas, w: Float, h: Float, paint: Paint, baseY: Float, peakY: Float) {
        c.drawOval(RectF(-w * 0.2f, h * peakY, w * 0.7f, h * (baseY + 0.5f)), paint)
        c.drawOval(RectF(w * 0.3f, h * (peakY + 0.05f), w * 1.25f, h * (baseY + 0.5f)), paint)
    }
}
