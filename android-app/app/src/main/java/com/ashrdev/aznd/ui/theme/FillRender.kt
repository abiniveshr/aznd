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
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
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
import com.ashrdev.aznd.ui.settings.PatternMode
import com.ashrdev.aznd.ui.settings.PerformanceStore
import java.util.concurrent.ScheduledThreadPoolExecutor
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
 * corner made every card show the same piece of the tile.) That is the default ([FillAnchor.SCREEN]); with
 * [FillAnchor.ELEMENT] a pattern starts at its element's corner. Gradients default to the opposite: each one spans its own
 * element, and can span the screen instead.
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
        // A layer whose tile is not ready yet (it is being made in the background) is left out for now.
        return PatternMath.placed(p.kind, p.scale, density).mapNotNull { layer ->
            val tile = PatternTiles.tile(p.kind, p.tint, p.seed + layer.seedShift) ?: return@mapNotNull null
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
     * on one card continue the same pattern instead of each starting it again at its own corner. With
     * [FillAnchor.ELEMENT] the pattern starts at [rect]'s corner instead; with a [FillAnchor.SCREEN] gradient and a positive
     * [fieldW] and [fieldH] the gradient spans that field instead of [rect].
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
        fieldY: Float = 0f,
        fieldW: Float = 0f,
        fieldH: Float = 0f
    ) {
        val a = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        if (a <= 0) return
        // A canvas copies the paint when it records a draw, so one paint per thread can be set up again for every call.
        val paints = paintsOfThread.get()!!
        val paint = paints.base
        paint.reset()
        paint.isAntiAlias = true
        if (style.base == FillBase.GRADIENT) {
            val spanField = style.gradientAnchor == FillAnchor.SCREEN && fieldW > 0f && fieldH > 0f
            paint.shader = gradientShader(style.gradient, if (spanField) RectF(fieldX, fieldY, fieldX + fieldW, fieldY + fieldH) else rect)
        } else {
            paint.color = baseColor
        }
        paint.alpha = a
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null

        // Patterns go on one over the other, first in the list lowest.
        val active = style.activePatterns
        if (active.isEmpty()) return
        val over = paints.over
        for (p in active) {
            for (layer in movablePatternLayers(p, density)) {
                if (style.patternAnchor == FillAnchor.ELEMENT) layer.anchorAt(rect.left, rect.top) else layer.anchorAt(fieldX, fieldY)
                over.reset()
                over.isAntiAlias = true
                over.isFilterBitmap = true
                over.shader = layer.shader
                over.alpha = (p.intensity.coerceIn(0f, 1f) * layer.share * a).roundToInt()
                canvas.drawRoundRect(rect, radius, radius, over)
                over.shader = null
            }
        }
    }

    private class Paints {
        val base = Paint()
        val over = Paint()
    }

    private val paintsOfThread = object : ThreadLocal<Paints>() {
        override fun initialValue(): Paints = Paints()
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

private class FieldGradientBrush(private val g: GradientSpec, private val fieldW: Float, private val fieldH: Float) : ShaderBrush() {
    private var shader: Shader? = null
    private val base = Matrix().also {
        if (g.kind == GradientKind.SWEEP) it.postRotate(g.angle.toFloat(), max(fieldW, 1f) * g.centerX, max(fieldH, 1f) * g.centerY)
    }
    private val scratch = Matrix()

    private fun made(): Shader = shader ?: FillRender.gradientShader(g, RectF(0f, 0f, fieldW, fieldH)).also { shader = it }

    override fun createShader(size: Size): Shader = made()

    fun moveTo(x: Float, y: Float) {
        scratch.set(base)
        scratch.postTranslate(-x, -y)
        made().setLocalMatrix(scratch)
    }
}

/**
 * Paints [fill] behind this element, clipped to [shape]. A plain colour is a normal background; gradients
 * and patterns are drawn once per size and cached. Null does nothing.
 *
 * A pattern with [FillAnchor.SCREEN] (the default) is one field laid over the whole screen (the root of the
 * composition), and this element shows the piece of it under it: the same pattern on two cards shows two different
 * pieces, and a card that scrolls or moves slides over a pattern that stays put. With [FillAnchor.ELEMENT] it starts at
 * the element's corner and moves with it. A gradient spans its own element, or with [FillAnchor.SCREEN] the whole screen
 * ([LocalFillField]). A pattern whose [ScreenBakes] bake is in memory is drawn from the bake; while its set is required
 * and not baked yet only the base is drawn; otherwise the tiles are drawn live. The element's position is read only
 * when drawing, and only when an anchor in play is [FillAnchor.SCREEN].
 */
fun Modifier.azndFill(fill: ResolvedFill?, shape: Shape = RectangleShape, preview: Boolean = false): Modifier {
    if (fill == null) return this
    val mode = if (preview) PatternMode.LIVE else PerformanceStore.patternMode
    val style = fill.style
    val base = fill.base
    val set = if (mode == PatternMode.OFF) null else ScreenBakes.setOf(style)
    val gradient = style.base == FillBase.GRADIENT
    if (!gradient && set == null) return this.background(base, shape)
    val screenGradient = gradient && style.gradientAnchor == FillAnchor.SCREEN
    val screenPattern = set != null && style.patternAnchor == FillAnchor.SCREEN
    if (set == null && !screenGradient) {
        return this.drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val baseBrush: Brush = gradientBrush(style.gradient)
            onDrawBehind {
                drawOutline(outline, baseBrush)
            }
        }
    }
    return this.composed {
        val field = LocalFillField.current
        val useField = screenGradient && field.width > 0 && field.height > 0
        val watch = useField || screenPattern
        val corner = remember { mutableStateOf(Offset.Zero) }
        val watcher = if (watch) {
            Modifier.onGloballyPositioned { coordinates ->
                val at = coordinates.positionInRoot()
                if (at != corner.value) corner.value = at
            }
        } else {
            Modifier
        }
        watcher.drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val fieldBrush = if (useField) FieldGradientBrush(style.gradient, field.width.toFloat(), field.height.toFloat()) else null
            val baseBrush: Brush = when {
                !gradient -> SolidColor(base)
                fieldBrush != null -> fieldBrush
                else -> gradientBrush(style.gradient)
            }
            val bakeSize = if (set != null && mode == PatternMode.BAKED) ScreenBakes.size else null
            val bake = if (set != null && bakeSize != null) ScreenBakes.bakeFor(set, bakeSize) else null
            val bakeShader = if (bake != null) BitmapShader(bake, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR) else null
            val bakeBrush: Brush? = if (bakeShader != null) {
                object : ShaderBrush() {
                    override fun createShader(size: Size): Shader = bakeShader
                }
            } else {
                null
            }
            val live = set != null && bake == null && (mode == PatternMode.LIVE || !ScreenBakes.isRequired(set))
            val overlays = if (live && set != null) set.flatMap { p -> patternOverlays(p, density) } else emptyList()
            val bakeMatrix = Matrix()
            onDrawBehind {
                val origin = if (watch) corner.value else Offset.Zero
                fieldBrush?.moveTo(origin.x, origin.y)
                drawOutline(outline, baseBrush)
                if (bakeShader != null && bakeBrush != null) {
                    if (screenPattern) {
                        bakeMatrix.setTranslate(-origin.x.roundToInt().toFloat(), -origin.y.roundToInt().toFloat())
                    } else {
                        bakeMatrix.reset()
                    }
                    bakeShader.setLocalMatrix(bakeMatrix)
                    drawOutline(outline, bakeBrush)
                }
                for (overlay in overlays) {
                    if (screenPattern) overlay.layer.anchorAt(-origin.x, -origin.y) else overlay.layer.anchorAt(0f, 0f)
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
 * The seamless bitmaps patterns repeat (worked out by [PatternMath], kept on disk by [PatternBake]). A tile holds the
 * pattern at full strength (light and dark as alpha, or the pattern colour); the fill's intensity fades it when drawn.
 *
 * The main thread never makes or reads a tile: [tile] gives null there until a background job has put it in memory, and
 * the Compose state it reads then tells the draw that asked to run again. Other threads (Flex export, thumbnails)
 * get the tile at once, made on the spot if need be.
 */
object PatternTiles {
    /** Least recently used tiles go first (the biggest are 512 × 512), so memory stays bounded. */
    private const val MAX_TILES = 40
    private val cache = object : LinkedHashMap<String, Bitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > MAX_TILES
    }

    /** Guards [cache] and [inFlight]; never held while a tile is made, so the main thread does not wait on a bake. */
    private val lock = Any()

    /** One tile at a time is made, so two askers for the same tile make it once. */
    private val genLock = Any()
    private val inFlight = HashSet<String>()

    /** Goes up whenever a tile arrives in memory; a draw that found a tile missing has read it and runs again. */
    private val arrivals = mutableIntStateOf(0)

    private val worker = ScheduledThreadPoolExecutor(1) { r ->
        Thread(r, "pattern-tiles").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    private fun onMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    private fun watchArrivals(): Int = arrivals.intValue

    private fun cached(name: String): Bitmap? = synchronized(lock) { cache[name] }

    /**
     * The tile, or null on the main thread while it is not in memory yet (a job is started to bring it, and the caller
     * is told when it arrives, if it is drawing inside a Compose draw or cache block).
     */
    fun tile(kind: PatternKind, tint: Int, seed: Int): Bitmap? {
        val name = PatternBake.nameOf(kind, tint, seed)
        cached(name)?.let { return it }
        if (!onMainThread()) return obtain(kind, tint, seed, name)
        // Read before looking again: a tile that lands in between is then seen, not missed.
        watchArrivals()
        cached(name)?.let { return it }
        request(kind, tint, seed, name)
        return null
    }

    /** Starts bringing the tiles into memory (from disk, or made and baked) without waiting for them. */
    fun prefetch(tiles: Collection<PatternBake.TileKey>) {
        for (t in tiles) {
            val name = t.name
            if (cached(name) == null) request(t.kind, t.tint, t.seed, name)
        }
    }

    /** True while the tile called [name] is being loaded or made, so its file must not be deleted. */
    internal fun isBusy(name: String): Boolean = synchronized(lock) { name in inFlight }

    /** Runs [task] on the tile worker after [delayMs], behind the tiles already queued. */
    internal fun background(delayMs: Long, task: Runnable) {
        worker.schedule(task, delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    private fun request(kind: PatternKind, tint: Int, seed: Int, name: String) {
        synchronized(lock) { if (!inFlight.add(name)) return }
        worker.execute {
            var arrived = false
            try {
                arrived = produce(kind, tint, seed, name) != null
            } catch (_: Throwable) {
                // Out of memory or a bad disk: the layer stays out until something asks again.
            } finally {
                synchronized(lock) { inFlight.remove(name) }
                if (arrived) {
                    synchronized(lock) { arrivals.intValue = arrivals.intValue + 1 }
                }
            }
        }
    }

    private fun obtain(kind: PatternKind, tint: Int, seed: Int, name: String): Bitmap? {
        val mine = synchronized(lock) { inFlight.add(name) }
        try {
            return produce(kind, tint, seed, name)
        } finally {
            if (mine) synchronized(lock) { inFlight.remove(name) }
        }
    }

    /** Loads the tile from its file, or works it out and bakes it, and keeps it in memory. */
    private fun produce(kind: PatternKind, tint: Int, seed: Int, name: String): Bitmap? = synchronized(genLock) {
        cached(name)?.let { return@synchronized it }
        val p = PatternBake.load(name) ?: PatternMath.pixels(kind, tint, seed).also { PatternBake.save(name, it) }
        val made = Bitmap.createBitmap(p.px, p.w, p.h, Bitmap.Config.ARGB_8888)
        synchronized(lock) { cache[name] = made }
        made
    }
}
