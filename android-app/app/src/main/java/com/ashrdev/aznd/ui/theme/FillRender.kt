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
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

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
     * The shaders a pattern is drawn with, each with how strongly (a share of the pattern's intensity): one for most
     * patterns, two for the organic ones (see [PatternMath.layers]: the second copy is turned and scaled by an odd
     * amount, so the tile is never seen to repeat). [density] is pixels per dp of the canvas (the pattern keeps the
     * same physical size on every screen); the tiles start at ([anchorX], [anchorY]).
     */
    fun patternLayers(p: PatternSpec, density: Float, anchorX: Float = 0f, anchorY: Float = 0f): List<Pair<Shader, Float>> {
        if (p.kind == PatternKind.NONE) return emptyList()
        val s = (p.scale * density / 2f).coerceAtLeast(0.05f)
        return PatternMath.layers(p.kind).map { layer ->
            val tile = PatternTiles.tile(p.kind, p.tint, p.seed + layer.seedShift)
            val shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            val m = Matrix()
            m.setScale(s * layer.scaleX, s * layer.scaleY)
            if (layer.rotate != 0f) m.postRotate(layer.rotate)
            m.postTranslate(anchorX, anchorY)
            shader.setLocalMatrix(m)
            shader to layer.alpha
        }
    }

    /** The first of [patternLayers]: one shader for the pattern, or null for none. */
    fun patternShader(p: PatternSpec, density: Float, anchorX: Float = 0f, anchorY: Float = 0f): Shader? =
        patternLayers(p, density, anchorX, anchorY).firstOrNull()?.first

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
            for ((shader, share) in patternLayers(p, density, rect.left, rect.top)) {
                val over = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                over.shader = shader
                over.alpha = (p.intensity.coerceIn(0f, 1f) * share * a).roundToInt()
                canvas.drawRoundRect(rect, radius, radius, over)
            }
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

/** One layer of a pattern as a brush (see FillRender.patternLayers), with the share of the intensity it is drawn at. */
private fun patternBrushes(p: PatternSpec, density: Float): List<Pair<Brush, Float>> =
    FillRender.patternLayers(p, density).map { (shader, share) ->
        object : ShaderBrush() {
            override fun createShader(size: Size): Shader = shader
        } to share
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
        val overlays = style.activePatterns.flatMap { p -> patternBrushes(p, density).map { (brush, share) -> brush to (p.intensity * share).coerceIn(0f, 1f) } }
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
 * The seamless bitmaps patterns repeat (worked out by [PatternMath]). A tile holds the pattern at full strength (light
 * and dark as alpha, or the pattern colour); the fill's intensity fades it when drawn. Made on first use and kept.
 */
object PatternTiles {
    /** Least recently used tiles go first (the biggest are 512 × 512), so memory stays bounded. */
    private const val MAX_TILES = 40
    private val cache = object : LinkedHashMap<String, Bitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > MAX_TILES
    }

    @Synchronized
    fun tile(kind: PatternKind, tint: Int, seed: Int): Bitmap {
        val key = "${kind.name}|${if (kind.tinted) tint and 0xFFFFFF else 0}|$seed"
        cache[key]?.let { return it }
        val p = PatternMath.pixels(kind, tint, seed)
        val made = Bitmap.createBitmap(p.px, p.w, p.h, Bitmap.Config.ARGB_8888)
        cache[key] = made
        return made
    }
}
