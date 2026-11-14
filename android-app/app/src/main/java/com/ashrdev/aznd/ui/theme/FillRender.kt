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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
 *
 * A pattern is one field laid over the whole screen (or the whole Flex card), and each element is a window onto it:
 * the tiles start at the screen's corner, not at each element's own. Two cards with the same pattern therefore show
 * different pieces of it and the pattern runs on from one to the next. (Starting every element's pattern at its own
 * corner made every card show the same piece of the tile.) Gradients are the opposite: each one spans its own element.
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
     * The shaders a pattern is drawn with, each with how strongly (a share of the pattern's intensity): one for the regular
     * patterns, several for the organic ones (see [PatternMath.placed]: the copies are turned and scaled by unrelated
     * amounts, so the tile is never seen to repeat). [density] is pixels per dp of the canvas (the pattern keeps the
     * same physical size on every screen). [anchorX], [anchorY] is the origin of the pattern field in the canvas the
     * shaders are used on: the tiles start there, so a shader meant for an element that is not at the canvas's corner
     * takes the field's origin, not the element's, to show its own piece of the field.
     */
    fun patternLayers(p: PatternSpec, density: Float, anchorX: Float = 0f, anchorY: Float = 0f): List<Pair<Shader, Float>> =
        movablePatternLayers(p, density).map { layer ->
            layer.anchorAt(anchorX, anchorY)
            layer.shader to layer.share
        }

    /**
     * The same layers as [patternLayers], but each can be moved: [MovablePattern.anchorAt] puts the pattern field's
     * origin somewhere else without making the shader again. For an element that moves or scrolls while its pattern
     * stays fixed to the screen.
     */
    fun movablePatternLayers(p: PatternSpec, density: Float): List<MovablePattern> {
        if (p.kind == PatternKind.NONE) return emptyList()
        return PatternMath.placed(p.kind, p.scale, density).map { layer ->
            val tile = PatternTiles.tile(p.kind, p.tint, p.seed + layer.seedShift)
            val shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            val m = Matrix()
            m.setScale(layer.scaleX, layer.scaleY)
            if (layer.rotate != 0f) m.postRotate(layer.rotate)
            MovablePattern(shader, m, layer.alpha)
        }
    }

    /** The first of [patternLayers]: one shader for the pattern, or null for none. */
    fun patternShader(p: PatternSpec, density: Float, anchorX: Float = 0f, anchorY: Float = 0f): Shader? =
        patternLayers(p, density, anchorX, anchorY).firstOrNull()?.first

    /**
     * Fills a rounded rect on [canvas]: base colour or gradient, then the pattern. [alpha] (0..1) fades
     * the whole fill, like the opacity of a Flex panel. The gradient spans [rect]; the pattern is a window onto a
     * field that starts at ([fieldX], [fieldY]) in the canvas's coordinates (the card's corner, for Flex), so panels
     * on one card continue the same pattern instead of each starting it again at its own corner.
     */
    fun drawRoundRect(
        canvas: Canvas,
        style: FillStyle,
        baseColor: Int,
        rect: RectF,
        radius: Float,
        density: Float,
        alpha: Float = 1f,
        fieldX: Float = 0f,
        fieldY: Float = 0f
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
            for ((shader, share) in patternLayers(p, density, fieldX, fieldY)) {
                val over = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                over.shader = shader
                over.alpha = (p.intensity.coerceIn(0f, 1f) * share * a).roundToInt()
                canvas.drawRoundRect(rect, radius, radius, over)
            }
        }
    }
}

/**
 * One layer of a pattern whose field origin can be moved: the shader, its matrix without the origin (scale and
 * turn only), and the [share] of the pattern's intensity it is drawn at. Not thread safe: it belongs to one element.
 */
class MovablePattern internal constructor(val shader: BitmapShader, private val base: Matrix, val share: Float) {
    private val scratch = Matrix()

    /** Puts the pattern field's origin at ([x], [y]) in the canvas the shader is drawn on, from the next draw on. */
    fun anchorAt(x: Float, y: Float) {
        scratch.set(base)
        scratch.postTranslate(x, y)
        shader.setLocalMatrix(scratch)
    }
}

// ---------------------------------------------------------------------------------------------
// Compose side
// ---------------------------------------------------------------------------------------------

private fun gradientBrush(g: GradientSpec): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader =
        FillRender.gradientShader(g, RectF(0f, 0f, size.width, size.height))
}

/** One layer of a pattern, with the brush that draws it and how strongly (already the share of the fill's intensity). */
private class PatternOverlay(val layer: MovablePattern, val brush: Brush, val alpha: Float)

/** The layers of [p] as overlays; each brush hands out its layer's own shader, so moving the layer moves what is drawn. */
private fun patternOverlays(p: PatternSpec, density: Float): List<PatternOverlay> =
    FillRender.movablePatternLayers(p, density).map { layer ->
        val brush = object : ShaderBrush() {
            override fun createShader(size: Size): Shader = layer.shader
        }
        PatternOverlay(layer, brush, (p.intensity * layer.share).coerceIn(0f, 1f))
    }

/**
 * Paints [fill] behind this element, clipped to [shape]. A plain colour is a normal background; gradients
 * and patterns are drawn once per size and cached. Null does nothing.
 *
 * A gradient spans this element. A pattern does not start at the element's corner: it is one field laid over the
 * whole screen (the root of the composition), and this element shows the piece of it under it. So the same pattern
 * on two cards shows two different pieces, and a card that scrolls or moves slides over a pattern that stays put.
 * The element's position is read only when drawing, so moving it redraws it and recomposes nothing. Fills without a
 * pattern are drawn as they always were, without watching the position.
 */
fun Modifier.azndFill(fill: ResolvedFill?, shape: Shape = RectangleShape): Modifier {
    if (fill == null) return this
    if (fill.isPlain) return this.background(fill.base, shape)
    val style = fill.style
    val base = fill.base
    if (style.activePatterns.isEmpty()) {
        return this.drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val baseBrush: Brush = if (style.base == FillBase.GRADIENT) gradientBrush(style.gradient) else SolidColor(base)
            onDrawBehind {
                drawOutline(outline, baseBrush)
            }
        }
    }
    return this.composed {
        val corner = remember { mutableStateOf(Offset.Zero) }
        Modifier
            .onGloballyPositioned { coordinates ->
                val at = coordinates.positionInRoot()
                if (at != corner.value) corner.value = at
            }
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val baseBrush: Brush = if (style.base == FillBase.GRADIENT) gradientBrush(style.gradient) else SolidColor(base)
                val overlays = style.activePatterns.flatMap { p -> patternOverlays(p, density) }
                onDrawBehind {
                    val origin = corner.value
                    drawOutline(outline, baseBrush)
                    for (overlay in overlays) {
                        overlay.layer.anchorAt(-origin.x, -origin.y)
                        drawOutline(outline, overlay.brush, alpha = overlay.alpha)
                    }
                }
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
