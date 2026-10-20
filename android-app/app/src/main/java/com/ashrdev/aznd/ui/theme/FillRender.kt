package com.ashrdev.aznd.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns a [FillStyle] into things that can be drawn: gradient and pattern shaders (plain android.graphics, so
 * the app's surfaces and the Flex card painter share one implementation), a Compose modifier, and a helper
 * that fills a rounded rect on any android Canvas.
 */
object FillRender {

    /** A gradient shader laid over [rect] (absolute coordinates in the canvas the paint is used on). */
    fun gradientShader(g: GradientSpec, rect: RectF): Shader {
        val stops = g.cleanStops()
        val colors = IntArray(stops.size) { stops[it].color }
        val positions = FloatArray(stops.size) { stops[it].position }
        val w = max(rect.width(), 1f)
        val h = max(rect.height(), 1f)
        val cx = rect.left + w * g.centerX
        val cy = rect.top + h * g.centerY
        return when (g.kind) {
            GradientKind.LINEAR -> {
                val rad = Math.toRadians(g.angle.toDouble())
                val dx = cos(rad).toFloat()
                val dy = sin(rad).toFloat()
                val half = (abs(w * dx) + abs(h * dy)) / 2f
                val mx = rect.left + w / 2f
                val my = rect.top + h / 2f
                LinearGradient(mx - dx * half, my - dy * half, mx + dx * half, my + dy * half, colors, positions, Shader.TileMode.CLAMP)
            }

            GradientKind.RADIAL -> {
                val r = max(1f, g.radius * hypot(w, h) / 2f)
                RadialGradient(cx, cy, r, colors, positions, Shader.TileMode.CLAMP)
            }

            GradientKind.SWEEP -> {
                SweepGradient(cx, cy, colors, positions).also { sweep ->
                    val m = Matrix()
                    m.postRotate(g.angle.toFloat(), cx, cy)
                    sweep.setLocalMatrix(m)
                }
            }
        }
    }

    /**
     * A repeating pattern shader, or null for no pattern. [density] is pixels per dp of the canvas (the
     * pattern keeps the same physical size on every screen); the tile starts at ([anchorX], [anchorY]).
     */
    fun patternShader(p: PatternSpec, density: Float, anchorX: Float = 0f, anchorY: Float = 0f): Shader? {
        if (p.kind == PatternKind.NONE) return null
        val tile = PatternTiles.tile(p.kind, p.tint, p.seed)
        val s = (p.scale * density / 2f).coerceAtLeast(0.05f)
        val shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val m = Matrix()
        m.setScale(s, s)
        m.postTranslate(anchorX, anchorY)
        shader.setLocalMatrix(m)
        return shader
    }

    /**
     * Fills a rounded rect on [canvas]: base colour or gradient, then the pattern. [alpha] (0..1) fades
     * the whole fill, like the opacity of a Flex panel.
     */
    fun drawRoundRect(
        canvas: Canvas,
        style: FillStyle,
        baseColor: Int,
        rect: RectF,
        radius: Float,
        density: Float,
        alpha: Float = 1f
    ) {
        val a = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        if (a <= 0) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (style.base == FillBase.GRADIENT) {
            paint.shader = gradientShader(style.gradient, rect)
        } else {
            paint.color = baseColor
        }
        paint.alpha = a
        canvas.drawRoundRect(rect, radius, radius, paint)

        // Patterns go on one over the other, first in the list lowest.
        for (p in style.activePatterns) {
            val shader = patternShader(p, density, rect.left, rect.top) ?: continue
            val over = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            over.shader = shader
            over.alpha = (p.intensity.coerceIn(0f, 1f) * a).roundToInt()
            canvas.drawRoundRect(rect, radius, radius, over)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Compose side
// ---------------------------------------------------------------------------------------------

private fun gradientBrush(g: GradientSpec): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader =
        FillRender.gradientShader(g, RectF(0f, 0f, size.width, size.height))
}

private fun patternBrush(p: PatternSpec, density: Float): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader =
        FillRender.patternShader(p, density) ?: BitmapShader(
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
            Shader.TileMode.REPEAT,
            Shader.TileMode.REPEAT
        )
}

/**
 * Paints [fill] behind this element, clipped to [shape]. A plain colour is a normal background; gradients
 * and patterns are drawn once per size and cached. Null does nothing.
 */
fun Modifier.azndFill(fill: ResolvedFill?, shape: Shape = RectangleShape): Modifier {
    if (fill == null) return this
    if (fill.isPlain) return this.background(fill.base, shape)
    val style = fill.style
    val base = fill.base
    return this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val baseBrush: Brush = if (style.base == FillBase.GRADIENT) gradientBrush(style.gradient) else SolidColor(base)
        val overlays = style.activePatterns.map { patternBrush(it, density) to it.intensity.coerceIn(0f, 1f) }
        onDrawBehind {
            drawOutline(outline, baseBrush)
            for ((brush, alpha) in overlays) drawOutline(outline, brush, alpha = alpha)
        }
    }
}

/** [azndFill] for one of the theme's surfaces (background, cards, top bar ...), taken from the current theme. */
@Composable
fun Modifier.themedFill(element: ThemeColor, shape: Shape = RectangleShape): Modifier =
    azndFill(LocalAzndStyle.current.fill(element), shape)

// ---------------------------------------------------------------------------------------------
// Pattern tiles
// ---------------------------------------------------------------------------------------------

/**
 * The small seamless bitmaps patterns repeat. A tile holds the pattern at full strength (light and dark
 * specks as alpha); the fill's intensity fades it when drawn. Made on first use and kept.
 */
object PatternTiles {
    /** Least recently used tiles go first, so a surface with many patterns does not rebuild them on every draw. */
    private const val MAX_TILES = 96
    private val cache = object : LinkedHashMap<String, Bitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > MAX_TILES
    }

    @Synchronized
    fun tile(kind: PatternKind, tint: Int, seed: Int): Bitmap {
        val key = "${kind.name}|${if (kind.tinted) tint else 0}|$seed"
        cache[key]?.let { return it }
        val made = build(kind, tint and 0xFFFFFF, seed)
        cache[key] = made
        return made
    }

    private fun argb(alpha: Float, rgb: Int): Int =
        ((alpha * 255f).roundToInt().coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

    /** Light for s > 0, dark for s < 0, strength |s|. */
    private fun signed(s: Float, light: Int): Int = argb(abs(s), if (s > 0f) light else 0x000000)

    private fun bitmap(w: Int, h: Int, px: IntArray): Bitmap =
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)

    private fun build(kind: PatternKind, tint: Int, seed: Int): Bitmap {
        val rnd = java.util.Random(seed.toLong() * 7919L + kind.ordinal)
        return when (kind) {
            PatternKind.NONE -> bitmap(1, 1, intArrayOf(0))
            PatternKind.NOISE -> noise(rnd)
            PatternKind.CLOUDS -> clouds(rnd, tint)
            PatternKind.SPECKLE -> speckle(rnd, tint)
            PatternKind.BRUSHED -> brushed(rnd)
            PatternKind.CARBON -> carbon()
            PatternKind.DOTS -> dots(tint)
            PatternKind.LINES -> lines(tint)
            PatternKind.GRID -> grid(tint)
        }
    }

    /** Fine film grain: soft-edged random light and dark pixels. */
    private fun noise(rnd: java.util.Random): Bitmap {
        val n = 256
        val px = IntArray(n * n)
        for (i in px.indices) {
            val v = (rnd.nextFloat() + rnd.nextFloat() + rnd.nextFloat()) / 3f
            px[i] = signed(((v - 0.5f) * 2f * 1.15f).coerceIn(-1f, 1f), 0xFFFFFF)
        }
        return bitmap(n, n, px)
    }

    /** Soft cloudy blotches: four layers of value noise on a wrapping lattice. */
    private fun clouds(rnd: java.util.Random, tint: Int): Bitmap {
        val n = 256
        val acc = FloatArray(n * n)
        val cells = intArrayOf(64, 32, 16, 8)
        val weights = floatArrayOf(0.50f, 0.25f, 0.15f, 0.10f)
        for (o in cells.indices) {
            val cell = cells[o]
            val g = n / cell
            val lattice = FloatArray(g * g) { rnd.nextFloat() }
            for (y in 0 until n) {
                val fy0 = y / cell.toFloat()
                val y0 = floor(fy0).toInt()
                var fy = fy0 - y0
                fy = fy * fy * (3f - 2f * fy)
                for (x in 0 until n) {
                    val fx0 = x / cell.toFloat()
                    val x0 = floor(fx0).toInt()
                    var fx = fx0 - x0
                    fx = fx * fx * (3f - 2f * fx)
                    val a = lattice[(y0 % g) * g + (x0 % g)]
                    val b = lattice[(y0 % g) * g + ((x0 + 1) % g)]
                    val c = lattice[((y0 + 1) % g) * g + (x0 % g)]
                    val d = lattice[((y0 + 1) % g) * g + ((x0 + 1) % g)]
                    val top = a + (b - a) * fx
                    val bottom = c + (d - c) * fx
                    acc[y * n + x] += weights[o] * (top + (bottom - top) * fy)
                }
            }
        }
        val px = IntArray(n * n) { signed(((acc[it] - 0.5f) * 3.2f).coerceIn(-1f, 1f), tint) }
        return bitmap(n, n, px)
    }

    /** Sparse bright specks with a faint glow, like stars or terrazzo. */
    private fun speckle(rnd: java.util.Random, tint: Int): Bitmap {
        val n = 256
        val a = FloatArray(n * n)
        for (i in a.indices) {
            if (rnd.nextFloat() < 0.010f) a[i] = 0.4f + 0.6f * rnd.nextFloat()
        }
        val out = a.copyOf()
        for (y in 0 until n) {
            for (x in 0 until n) {
                val v = a[y * n + x]
                if (v <= 0f) continue
                val glow = v * 0.35f
                val l = y * n + (x + n - 1) % n
                val r = y * n + (x + 1) % n
                val u = ((y + n - 1) % n) * n + x
                val d = ((y + 1) % n) * n + x
                out[l] = max(out[l], glow)
                out[r] = max(out[r], glow)
                out[u] = max(out[u], glow)
                out[d] = max(out[d], glow)
            }
        }
        return bitmap(n, n, IntArray(n * n) { argb(out[it], tint) })
    }

    /** Brushed metal: long horizontal streaks of light and dark. */
    private fun brushed(rnd: java.util.Random): Bitmap {
        val n = 256
        val window = 44
        val px = IntArray(n * n)
        val row = FloatArray(n)
        val boost = sqrt(window.toFloat()) * 0.8f
        for (y in 0 until n) {
            for (x in 0 until n) row[x] = rnd.nextFloat()
            for (x in 0 until n) {
                var sum = 0f
                for (k in -window / 2 until window - window / 2) sum += row[(x + k + n) % n]
                val avg = sum / window
                val streak = 0.5f + (avg - 0.5f) * boost
                val v = 0.78f * streak + 0.22f * rnd.nextFloat()
                px[y * n + x] = signed(((v - 0.5f) * 2.4f).coerceIn(-1f, 1f), 0xFFFFFF)
            }
        }
        return bitmap(n, n, px)
    }

    /** Carbon fibre: a 2x2 twill of rounded strands. */
    private fun carbon(): Bitmap {
        val c = 16
        val h = c / 2
        val px = IntArray(c * c)
        for (y in 0 until c) {
            for (x in 0 until c) {
                val even = ((x / h) + (y / h)) % 2 == 0
                val t = if (even) ((y % h) + 0.5f) / h else ((x % h) + 0.5f) / h
                val shade = 1f - abs(t - 0.5f) * 2f
                var s = (shade - 0.45f) * 2f
                if (!even) s *= 0.8f
                px[y * c + x] = signed(s.coerceIn(-1f, 1f), 0xFFFFFF)
            }
        }
        return bitmap(c, c, px)
    }

    private fun dots(tint: Int): Bitmap {
        val n = 12
        val r = 2.6f
        val px = IntArray(n * n)
        for (y in 0 until n) {
            for (x in 0 until n) {
                val d = hypot(x + 0.5f - n / 2f, y + 0.5f - n / 2f)
                px[y * n + x] = argb((r + 0.5f - d).coerceIn(0f, 1f), tint)
            }
        }
        return bitmap(n, n, px)
    }

    private fun lines(tint: Int): Bitmap {
        val n = 16
        val width = 3f
        val px = IntArray(n * n)
        for (y in 0 until n) {
            for (x in 0 until n) {
                val d = abs(((x + y) % n) - n / 2f)
                px[y * n + x] = argb((width / 2f + 0.5f - d).coerceIn(0f, 1f), tint)
            }
        }
        return bitmap(n, n, px)
    }

    private fun grid(tint: Int): Bitmap {
        val n = 24
        val px = IntArray(n * n)
        for (y in 0 until n) {
            for (x in 0 until n) {
                px[y * n + x] = if (x == 0 || y == 0) argb(1f, tint) else 0
            }
        }
        return bitmap(n, n, px)
    }
}
