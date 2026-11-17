package com.ashrdev.aznd.ui.theme

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/*
 * The procedural patterns behind [PatternKind], worked out as plain pixels (no Android), so the app's surfaces, the
 * Flex card painter and the test harness all draw the same thing.
 *
 * Every tile is seamless: it is built on a lattice that wraps around its edges (periodic gradient noise, wrapping
 * cell points, whole numbers of waves), so a tiled surface has no seams. Organic patterns (grain, clouds, brushed
 * metal, speckle) are also laid on three times, the copies turned and scaled by amounts that share no period
 * ([PatternMath.layers]), so nothing is seen to repeat however big the surface is; the patterns of big cells
 * (topographic, marble, cells, halftone) cannot be split without changing how they look, so their tiles are made as
 * large as is sensible and turned off the screen's axes instead (a copy can then only be seen on a very large
 * surface, or at a small scale).
 *
 * Every layer is anchored at the screen's origin (not at the surface it is drawn on): a pattern is one field laid
 * over the whole screen and each surface a window onto it. A tile holds its pattern as alpha: light (white) and dark
 * (black) for the neutral patterns, the pattern colour for the tinted ones; the fill's intensity fades it as drawn.
 *
 * The techniques are the standard ones of procedural texturing: Perlin gradient noise and fractal sums of it (fBm),
 * turbulence and domain warping (marble), Worley cell noise (cells), anisotropic blur of white noise (brushed
 * metal), iso-lines of a height field drawn at an even width (topographic map), and an amplitude-modulated dot
 * screen (halftone).
 */

/** A seamless tile of [w] × [h] ARGB pixels. */
class PatternPixels(val w: Int, val h: Int, val px: IntArray)

/**
 * One copy of a pattern's tile on a surface: scaled ([scaleX], [scaleY], on top of the pattern's own scale), turned by
 * [rotate] degrees and drawn at [alpha] of the fill's intensity. [seedShift] picks another tile of the same kind.
 */
class PatternLayer(val scaleX: Float, val scaleY: Float, val rotate: Float, val alpha: Float, val seedShift: Int)

object PatternMath {

    /** Bump when the pattern maths changes so old bakes are rebuilt (see [PatternBake]). */
    const val GENERATOR_VERSION: Int = 1

    /**
     * How [kind] is laid on a surface (see the file comment): the small organic patterns three times, each copy turned
     * and scaled by amounts that share no period, so no copy of one layer lines up with another and none carries more
     * than about a third of what is seen; the big-cell patterns (topographic, marble, cells, halftone) once, on a tile
     * as large as is sensible, the first three turned so that no copy of the tile sits straight below or beside the
     * original (see [LINE_TURN]); the regular patterns once, unturned.
     */
    fun layers(kind: PatternKind): List<PatternLayer> = when (kind) {
        PatternKind.NOISE -> listOf(PatternLayer(1f, 1f, 0f, 0.56f, 0), PatternLayer(1.356f, 1.356f, 34.4f, 0.56f, 1), PatternLayer(1.155f, 1.155f, 66.3f, 0.56f, 2))
        PatternKind.CLOUDS -> listOf(PatternLayer(2f, 2f, 0f, 0.545f, 0), PatternLayer(3.066f, 3.066f, 51.9f, 0.545f, 1), PatternLayer(2.532f, 2.532f, 100.5f, 0.545f, 2))
        PatternKind.SPECKLE -> listOf(PatternLayer(1f, 1f, 0f, 1f, 0), PatternLayer(1.397f, 1.397f, 26.4f, 1f, 1), PatternLayer(1.251f, 1.251f, 61.9f, 1f, 2))
        // Brushed metal keeps its grain level: three copies, each stretched along the strokes and across them by
        // unrelated factors, so no two share a vertical period and they cannot beat against each other.
        PatternKind.BRUSHED -> listOf(PatternLayer(3.6f, 0.75f, 0f, 0.6f, 0), PatternLayer(5.825f, 0.995f, 0f, 0.6f, 1),
            PatternLayer(4.6f, 1.298f, 0f, 0.6f, 2))
        PatternKind.TOPO -> listOf(PatternLayer(2f, 2f, LINE_TURN, 1f, 0))
        PatternKind.MARBLE -> listOf(PatternLayer(2f, 2f, MARBLE_TURN, 1f, 0))
        PatternKind.CELLS -> listOf(PatternLayer(1.5f, 1.5f, LINE_TURN, 1f, 0))
        // Halftone: the dot screen is one lattice, so the three layers share scale, turn and origin (their dots land on
        // the same sites after snapping); each holds its own swelling of the dots on a tile of another size
        // ([HALFTONE_TILES], picked by the seed shift), so no two of them come back together within 11600 tile pixels.
        PatternKind.HALFTONE -> listOf(PatternLayer(1f, 1f, 0f, HALFTONE_ALPHA, 0), PatternLayer(1f, 1f, 0f, HALFTONE_ALPHA, 1), PatternLayer(1f, 1f, 0f, HALFTONE_ALPHA, 2))
        else -> listOf(PatternLayer(1f, 1f, 0f, 1f, 0))
    }

    /** Share of the fill's intensity of each of the three halftone layers (their dots add up). */
    private const val HALFTONE_ALPHA = 0.52f

    /** The halftone's tile sizes, all multiples of its 16 px period (so every tile holds whole dot lattices), chosen so that no two layers' multiples come near each other within a screen at any scale; [halftone] takes one by the seed modulo 3, so the seed shifts 0, 1, 2 of a pattern always get three different ones. */

    private val HALFTONE_TILES = intArrayOf(512, 400, 464)

    /**
     * The turn (degrees) of the single-layer line patterns. A square tile lattice turned by about 35 degrees puts its
     * nearest copies at about (0.82, 0.57) of a period: neither straight down nor straight across, and for a period
     * of 1400 px or more far enough off a 1080 x 2400 screen to leave under 15 % of it overlapping.
     */
    private const val LINE_TURN = 35f

    /** Marble's turn: [LINE_TURN] taken to the angle of a 3-4-5 triangle, with the veins drawn to meet it (see [marble]). */
    private const val MARBLE_TURN = 36.87f

    /**
     * The period of a regular pattern's cell in tile pixels, across and down, or null for the organic patterns: what
     * [placed] makes a whole number of screen pixels. The weave of carbon fibre is taken over its full 16 px (a twill
     * repeats every two strands), the halftone over two dot pitches (16 px along the tile's axes; one pitch of 8 px
     * would cost up to 6 % in size), honeycomb over its 42 x 36 cell and waves over one wave (192 x 24).
     */
    fun period(kind: PatternKind): IntArray? = when (kind) {
        PatternKind.CARBON -> intArrayOf(16, 16)
        PatternKind.DOTS -> intArrayOf(12, 12)
        PatternKind.LINES -> intArrayOf(16, 16)
        PatternKind.GRID -> intArrayOf(24, 24)
        PatternKind.HEX -> intArrayOf(42, 36)
        PatternKind.WAVES -> intArrayOf(192, 24)
        PatternKind.HALFTONE -> intArrayOf(16, 16)
        else -> null
    }

    /**
     * [layers] for a pattern drawn at [scale] (the fill's own scale, which the drawing code multiplies every layer by):
     * the place for a pattern to answer its scale in its own way. This is what the drawing code calls.
     */
    fun layers(kind: PatternKind, scale: Float): List<PatternLayer> {
        if (kind != PatternKind.BRUSHED) return layers(kind)
        // The drawing code multiplies both axes by the scale; brushing answers it mostly with longer streaks and only
        // gently with thicker ones, so the rows stay fine however large the fill is set.
        val k = scale.coerceIn(0.25f, 6f)
        val along = k.toDouble().pow(-0.6).toFloat()
        val across = k.toDouble().pow(-0.75).toFloat()
        return layers(kind).map { PatternLayer(it.scaleX * along, it.scaleY * across, it.rotate, it.alpha, it.seedShift) }
    }

    /**
     * The layers of [kind] as they are drawn at the fill's [scale] on a canvas of [density] pixels per dp: [layers],
     * with every layer's scaleX / scaleY already the screen pixels one tile pixel covers (the pattern keeps the same
     * physical size on every screen). The app's drawing code and the test harness both place tiles by this alone.
     *
     * The regular patterns (see [period]) are snapped: each axis' scale is rounded so that one period of the cell is a
     * whole number of screen pixels (at least 2), which makes every line and dot land on the pixel grid the same way
     * instead of some being crisp and some blurred. That changes their size by at most half a pixel in a period (under
     * 4 % for a period of 13 px or more, which is every regular pattern at scale 1 or more at densities 2 to 3.5;
     * the worst case is 3.9 %, the 12 px dots); the organic ones are left alone.
     */
    fun placed(kind: PatternKind, scale: Float, density: Float): List<PatternLayer> {
        val s = (scale * density / 2f).coerceAtLeast(0.05f)
        val period = period(kind)
        return layers(kind, scale).map { l ->
            if (period == null) PatternLayer(s * l.scaleX, s * l.scaleY, l.rotate, l.alpha, l.seedShift)
            else PatternLayer(snap(s * l.scaleX, period[0]), snap(s * l.scaleY, period[1]), l.rotate, l.alpha, l.seedShift)
        }
    }

    /** [scale] (screen pixels per tile pixel) rounded so that [period] tile pixels make a whole number (at least 2) of screen pixels. */
    private fun snap(scale: Float, period: Int): Float = max(2, (scale * period).roundToInt()).toFloat() / period

    /** The tile of [kind] in colour [tint] (RGB; tinted patterns only), arrangement [seed]. */
    fun pixels(kind: PatternKind, tint: Int, seed: Int): PatternPixels {
        val rgb = tint and 0xFFFFFF
        return when (kind) {
            PatternKind.NONE -> PatternPixels(1, 1, intArrayOf(0))
            PatternKind.NOISE -> grain(seed)
            PatternKind.CLOUDS -> clouds(seed, rgb)
            PatternKind.SPECKLE -> speckle(seed, rgb)
            PatternKind.BRUSHED -> brushed(seed)
            PatternKind.CARBON -> carbon()
            PatternKind.DOTS -> dots(rgb)
            PatternKind.LINES -> lines(rgb)
            PatternKind.GRID -> grid(rgb)
            PatternKind.TOPO -> topo(seed, rgb)
            PatternKind.CELLS -> cells(seed, rgb)
            PatternKind.MARBLE -> marble(seed, rgb)
            PatternKind.HEX -> hex(rgb)
            PatternKind.WAVES -> waves(rgb)
            PatternKind.HALFTONE -> halftone(seed, rgb)
        }
    }

    // ------------------------------------------------------------------------------------------
    // Pixels
    // ------------------------------------------------------------------------------------------

    private fun argb(alpha: Float, rgb: Int): Int =
        ((alpha * 255f).roundToInt().coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

    /** Light for [s] > 0, dark for [s] < 0, at strength |s| (clamped to 1). */
    private fun signed(s: Float, light: Int = 0xFFFFFF): Int = argb(min(1f, abs(s)), if (s > 0f) light else 0x000000)

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Values brought to mean 0 and standard deviation 1 (in place). */
    private fun standardise(a: FloatArray) {
        var mean = 0.0
        for (v in a) mean += v
        mean /= a.size
        var sd = 0.0
        for (v in a) sd += (v - mean) * (v - mean)
        sd = sqrt(sd / a.size).coerceAtLeast(1e-6)
        for (i in a.indices) a[i] = ((a[i] - mean) / sd).toFloat()
    }

    // ------------------------------------------------------------------------------------------
    // Noise: hashing, periodic Perlin noise, fractal sums
    // ------------------------------------------------------------------------------------------

    private fun hash(x: Int, y: Int, seed: Int): Int {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    private fun hash01(x: Int, y: Int, seed: Int): Float = (hash(x, y, seed) ushr 8) / 16777216f

    /** 256 unit gradient directions for [PerlinGrid]. */
    private val GX = FloatArray(256) { cos(2.0 * PI * it / 256).toFloat() }
    private val GY = FloatArray(256) { sin(2.0 * PI * it / 256).toFloat() }

    private fun fade(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)

    private fun wrap(i: Int, p: Int): Int = ((i % p) + p) % p

    /**
     * Perlin gradient noise on a lattice that repeats every [period] cells: the gradient of every lattice point is
     * looked up in a table made once, which is what keeps a 512 px tile quick. [at] is about -0.7..0.7.
     */
    private class PerlinGrid(val period: Int, seed: Int) {
        private val gradient = IntArray(period * period) { (hash(it % period, it / period, seed) ushr 8) and 255 }

        fun at(x: Float, y: Float): Float {
            val x0 = floor(x).toInt()
            val y0 = floor(y).toInt()
            val fx = x - x0
            val fy = y - y0
            // x0 and y0 are in 0 until period, so +1 needs at most one wrap.
            val x1 = if (x0 + 1 == period) 0 else x0 + 1
            val y1 = if (y0 + 1 == period) 0 else y0 + 1
            val g00 = gradient[y0 * period + x0]
            val g10 = gradient[y0 * period + x1]
            val g01 = gradient[y1 * period + x0]
            val g11 = gradient[y1 * period + x1]
            val n00 = GX[g00] * fx + GY[g00] * fy
            val n10 = GX[g10] * (fx - 1f) + GY[g10] * fy
            val n01 = GX[g01] * fx + GY[g01] * (fy - 1f)
            val n11 = GX[g11] * (fx - 1f) + GY[g11] * (fy - 1f)
            val u = fade(fx)
            val v = fade(fy)
            val a = n00 + (n10 - n00) * u
            val b = n01 + (n11 - n01) * u
            return a + (b - a) * v
        }
    }

    /**
     * A fractal sum of [octaves] Perlin noises at u, v in 0..1 of the tile, the first with [cells] lattice cells
     * across it, each next twice as fine and [gain] as strong. Seamless. [ridged]: turbulence (absolute values).
     */
    private class Fbm(cells: Int, private val octaves: Int, seed: Int, private val gain: Float = 0.5f, private val ridged: Boolean = false) {
        private val grids = Array(octaves) { PerlinGrid(cells shl it, seed + it * 1013) }

        fun at(u: Float, v: Float): Float {
            var sum = 0f
            var amp = 1f
            var norm = 0f
            for (o in 0 until octaves) {
                val c = grids[o].period
                val n = grids[o].at(u * c, v * c)
                sum += amp * (if (ridged) abs(n) else n)
                norm += amp
                amp *= gain
            }
            return sum / norm
        }
    }

    /** A field of fBm over an [n] × [n] tile, standardised. */
    private fun fbmField(n: Int, cells: Int, octaves: Int, seed: Int, gain: Float = 0.5f): FloatArray {
        val a = FloatArray(n * n)
        val f = Fbm(cells, octaves, seed, gain)
        for (y in 0 until n) for (x in 0 until n) a[y * n + x] = f.at(x / n.toFloat(), y / n.toFloat())
        standardise(a)
        return a
    }

    /**
     * Gaussian blur along rows (wrapping), [sigma] in pixels: three box blurs in a row (the usual fast stand-in), so
     * the cost does not grow with [sigma].
     */
    private fun blurRows(a: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
        if (sigma < 1.5f) return gaussRows(a, w, h, sigma)
        var cur = a
        val r = boxRadius(sigma)
        repeat(3) {
            val out = FloatArray(cur.size)
            val n = 2 * r + 1
            for (y in 0 until h) {
                val row = y * w
                var sum = 0f
                for (i in -r..r) sum += cur[row + wrap(i, w)]
                for (x in 0 until w) {
                    out[row + x] = sum / n
                    sum += cur[row + wrap(x + r + 1, w)] - cur[row + wrap(x - r, w)]
                }
            }
            cur = out
        }
        return cur
    }

    /** The same down columns. */
    private fun blurCols(a: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
        if (sigma < 1.5f) return transpose(gaussRows(transpose(a, w, h), h, w, sigma), h, w)
        var cur = a
        val r = boxRadius(sigma)
        repeat(3) {
            val out = FloatArray(cur.size)
            val n = 2 * r + 1
            for (x in 0 until w) {
                var sum = 0f
                for (i in -r..r) sum += cur[wrap(i, h) * w + x]
                for (y in 0 until h) {
                    out[y * w + x] = sum / n
                    sum += cur[wrap(y + r + 1, h) * w + x] - cur[wrap(y - r, h) * w + x]
                }
            }
            cur = out
        }
        return cur
    }

    /** A small Gaussian blur along rows (wrapping), for spreads too small for [blurRows]' boxes. */
    private fun gaussRows(a: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
        val r = (sigma * 3f).roundToInt().coerceAtLeast(1)
        val k = FloatArray(2 * r + 1) { val d = it - r; exp(-(d * d) / (2f * sigma * sigma)) }
        val ks = k.sum()
        val out = FloatArray(a.size)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var sum = 0f
                for (i in k.indices) sum += k[i] * a[row + wrap(x + i - r, w)]
                out[row + x] = sum / ks
            }
        }
        return out
    }

    private fun transpose(a: FloatArray, w: Int, h: Int): FloatArray {
        val out = FloatArray(a.size)
        for (y in 0 until h) for (x in 0 until w) out[x * h + y] = a[y * w + x]
        return out
    }

    /** The radius of a box blur that, three times over, spreads like a Gaussian of [sigma] (at least 1 px wide). */
    private fun boxRadius(sigma: Float): Int = (sqrt(4f * sigma * sigma + 1f) / 2f - 0.5f).roundToInt().coerceAtLeast(1)

    /** White noise with a bell-shaped spread (sum of four uniforms), mean 0. */
    private fun gaussianNoise(size: Int, rnd: java.util.Random): FloatArray =
        FloatArray(size) { (rnd.nextFloat() + rnd.nextFloat() + rnd.nextFloat() + rnd.nextFloat()) - 2f }

    // ------------------------------------------------------------------------------------------
    // The patterns
    // ------------------------------------------------------------------------------------------

    /**
     * Film grain: bell-shaped noise softened to about a pixel and a half (grains, not single pixels), clumped by a
     * faint coarse noise as real grain is, and kept gentle at its extremes (no harsh white or black specks).
     */
    private fun grain(seed: Int): PatternPixels {
        val n = 256
        val rnd = java.util.Random(seed * 7919L + 11)
        var g = gaussianNoise(n * n, rnd)
        g = blurCols(blurRows(g, n, n, 0.7f), n, n, 0.7f)
        standardise(g)
        val clump = fbmField(n, 16, 2, seed + 5)
        val px = IntArray(n * n) { i ->
            val v = g[i] * (0.8f + 0.25f * clump[i])
            signed(tanh(v * 0.42f))
        }
        return PatternPixels(n, n, px)
    }

    /**
     * Clouds: fractal Perlin noise (six octaves), soft-edged, light and dark around the base colour. Laid on twice at
     * odd scales and angles, so no shape comes back.
     */
    private fun clouds(seed: Int, tint: Int): PatternPixels {
        val n = 256
        val f = fbmField(n, 4, 6, seed + 101, gain = 0.55f)
        return PatternPixels(n, n, IntArray(n * n) { signed(tanh(f[it] * 0.55f), tint) })
    }

    /**
     * Sparse specks of varied size and brightness with a faint glow, like stars or terrazzo. A tile holds two thirds of
     * the specks the pattern shows, as it is laid on three times.
     */
    private fun speckle(seed: Int, tint: Int): PatternPixels {
        val n = 256
        val rnd = java.util.Random(seed * 7919L + 23)
        val a = FloatArray(n * n)
        repeat((n * n * 0.0027f).toInt()) {
            val cx = rnd.nextFloat() * n
            val cy = rnd.nextFloat() * n
            val big = rnd.nextFloat()
            val r = 0.6f + 1.6f * big * big
            val v = 0.35f + 0.65f * rnd.nextFloat()
            val reach = (r + 2f).toInt() + 1
            for (dy in -reach..reach) for (dx in -reach..reach) {
                val x = wrap(cx.toInt() + dx, n)
                val y = wrap(cy.toInt() + dy, n)
                val d = sqrt((cx.toInt() + dx + 0.5f - cx).let { it * it } + (cy.toInt() + dy + 0.5f - cy).let { it * it })
                val core = (r + 0.5f - d).coerceIn(0f, 1f)
                val glow = 0.3f * exp(-(d - r).coerceAtLeast(0f) * 1.4f)
                a[y * n + x] = max(a[y * n + x], v * max(core, glow))
            }
        }
        return PatternPixels(n, n, IntArray(n * n) { argb(a[it], tint) })
    }

    /**
     * Brushed metal, the way it is made: thousands of short and long strokes laid on single rows, each of its own
     * length, brightness and thickness, with both ends tapered. Row means and a wide vertical blur are taken out, so
     * no row is lighter than its neighbours as a whole and no blotch remains; what is left is fine, level grain that
     * is felt more than seen. There are no single scratches, because the eye catches those when they repeat.
     */
    private fun brushed(seed: Int): PatternPixels {
        val w = 512
        val h = 256
        val rnd = java.util.Random(seed * 7919L + 37)
        val v = FloatArray(w * h)
        val logMin = ln(80f)
        val logMax = ln(800f)
        repeat(2400) {
            val y = rnd.nextFloat() * h
            val x0 = rnd.nextInt(w)
            val len = exp(logMin + (logMax - logMin) * rnd.nextFloat())
            val n = len.toInt() + 1
            val amp = (if (rnd.nextBoolean()) 1f else -1f) * (0.25f + abs(rnd.nextGaussian().toFloat()) * 0.7f)
            val thick = rnd.nextFloat() < 0.25f
            val y0 = y.toInt()
            val f = y - y0
            // Rows the stroke lands on, with their shares of its strength.
            val rA = ((y0 - 1) and (h - 1)) * w
            val rB = y0 * w
            val rC = ((y0 + 1) and (h - 1)) * w
            val rD = ((y0 + 2) and (h - 1)) * w
            val wA = if (thick) 0.3f * (1f - f) * amp else 0f
            val wB = if (thick) (0.6f + 0.1f * (1f - f)) * amp else (1f - f) * amp
            val wC = if (thick) (0.6f + 0.1f * f) * amp else f * amp
            val wD = if (thick) 0.3f * f * amp else 0f
            // Smooth ramps over the first and last 30 % of the stroke, flat between.
            val ramp = 0.3f * len
            val inv = 1f / ramp
            var x = x0
            for (i in 0 until n) {
                val d = min(i.toFloat(), len - i)
                val e = if (d >= ramp) 1f else if (d <= 0f) 0f else { val t = d * inv; t * t * (3f - 2f * t) }
                if (thick) {
                    v[rA + x] += wA * e
                    v[rD + x] += wD * e
                }
                v[rB + x] += wB * e
                v[rC + x] += wC * e
                if (++x == w) x = 0
            }
        }
        // Light and dark strokes must balance within a stretch of a row too, or a long row reads as one lighter band.
        // (1 - G)^3, with G a Gaussian along the row, takes the long waves out and leaves the streaks their length.
        // Those waves are smooth, so they are found on a copy squeezed 8 times along the rows.
        val cw = w / 8
        val low = squeeze(v, w, h, 8)
        val g1 = blurRows(low, cw, h, 70f / 8f)
        val g2 = blurRows(low, cw, h, 99f / 8f)
        val g3 = blurRows(low, cw, h, 121f / 8f)
        for (i in g1.indices) g1[i] = -3f * g1[i] + 3f * g2[i] - g3[i]
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = (x - 3.5f) / 8f
                val c0 = floor(c).toInt()
                val t = c - c0
                val r = y * cw
                v[y * w + x] += g1[r + (c0 and (cw - 1))] * (1f - t) + g1[r + ((c0 + 1) and (cw - 1))] * t
            }
        }
        standardise(v)
        // Fine, short grain over the strokes: triangular noise from one random int per pixel, smoothed over 5 pixels.
        val fine = FloatArray(w * h)
        for (i in fine.indices) {
            val r = rnd.nextInt()
            fine[i] = ((r and 0xFFFF) + (r ushr 16)) * (1f / 65536f) - 1f
        }
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                v[row + x] += 0.17f * (fine[row + ((x - 2) and (w - 1))] + 2f * fine[row + ((x - 1) and (w - 1))] +
                    3f * fine[row + x] + 2f * fine[row + ((x + 1) and (w - 1))] + fine[row + ((x + 2) and (w - 1))])
            }
        }
        // No row lighter or darker as a whole, and no soft vertical swell either.
        for (y in 0 until h) {
            var m = 0f
            for (x in 0 until w) m += v[y * w + x]
            m /= w
            for (x in 0 until w) v[y * w + x] -= m
        }
        val swell = blurCols(squeeze(v, w, h, 8), cw, h, 12f)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = (x - 3.5f) / 8f
                val c0 = floor(c).toInt()
                val t = c - c0
                val r = y * cw
                v[y * w + x] -= swell[r + (c0 and (cw - 1))] * (1f - t) + swell[r + ((c0 + 1) and (cw - 1))] * t
            }
        }
        standardise(v)
        // Soft clip through a table (|tanh| in 256 steps of alpha) instead of a tanh per pixel.
        val steps = 1024
        val clip = IntArray(steps + 1) { (tanh(it * (4f / steps)) * 255f).roundToInt() }
        val gain = 0.23f
        return PatternPixels(w, h, IntArray(w * h) {
            val x = v[it]
            val a = clip[min(steps, (abs(x) * gain * (steps / 4f)).toInt())]
            (a shl 24) or (if (x > 0f) 0xFFFFFF else 0)
        })
    }

    /** [a] averaged in groups of [k] pixels along each row ([w] must divide by [k]). */
    private fun squeeze(a: FloatArray, w: Int, h: Int, k: Int): FloatArray {
        val cw = w / k
        val out = FloatArray(cw * h)
        for (y in 0 until h) {
            for (x in 0 until w) out[y * cw + x / k] += a[y * w + x]
        }
        for (i in out.indices) out[i] /= k
        return out
    }

    /** Carbon fibre: a 2x2 twill of rounded strands. */
    private fun carbon(): PatternPixels {
        val c = 16
        val half = c / 2
        val px = IntArray(c * c)
        for (y in 0 until c) for (x in 0 until c) {
            val even = ((x / half) + (y / half)) % 2 == 0
            val t = if (even) ((y % half) + 0.5f) / half else ((x % half) + 0.5f) / half
            val shade = 1f - abs(t - 0.5f) * 2f
            var s = (shade - 0.45f) * 2f
            if (!even) s *= 0.8f
            px[y * c + x] = signed(s.coerceIn(-1f, 1f))
        }
        return PatternPixels(c, c, px)
    }

    private fun dots(tint: Int): PatternPixels {
        val n = 12
        val r = 2.6f
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val d = sqrt((x + 0.5f - n / 2f).let { it * it } + (y + 0.5f - n / 2f).let { it * it })
            px[y * n + x] = argb((r + 0.5f - d).coerceIn(0f, 1f), tint)
        }
        return PatternPixels(n, n, px)
    }

    private fun lines(tint: Int): PatternPixels {
        val n = 16
        val width = 3f
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val d = abs(((x + y) % n) - n / 2f)
            px[y * n + x] = argb((width / 2f + 0.5f - d).coerceIn(0f, 1f), tint)
        }
        return PatternPixels(n, n, px)
    }

    private fun grid(tint: Int): PatternPixels {
        val n = 24
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) px[y * n + x] = if (x == 0 || y == 0) argb(1f, tint) else 0
        return PatternPixels(n, n, px)
    }

    /**
     * A topographic map: the contour lines of a smooth fractal height field, every line the same width whatever the
     * slope (the distance to the line is measured through the field's gradient), every fifth one bolder.
     */
    private fun topo(seed: Int, tint: Int): PatternPixels {
        val n = 512
        val f = fbmField(n, 3, 4, seed + 211, gain = 0.45f)
        val levels = 2.6f
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val v = f[y * n + x] * levels
            val gx = (f[y * n + wrap(x + 1, n)] - f[y * n + wrap(x - 1, n)]) * 0.5f * levels
            val gy = (f[wrap(y + 1, n) * n + x] - f[wrap(y - 1, n) * n + x]) * 0.5f * levels
            val g = sqrt(gx * gx + gy * gy).coerceAtLeast(1e-4f)
            val k = floor(v + 0.5f)
            val d = abs(v - k) / g
            val bold = ((k.toInt() % 5) + 5) % 5 == 0
            val width = if (bold) 1.1f else 0.55f
            px[y * n + x] = argb((1f - smooth(width - 0.6f, width + 0.6f, d)) * (if (bold) 1f else 0.75f), tint)
        }
        return PatternPixels(n, n, px)
    }

    /**
     * Cells (Worley / Voronoi noise): a wrapping set of random points; each pixel's two nearest give the cell walls
     * (where they are about as near as each other), drawn as fine lines, with each cell shaded faintly toward its middle.
     * The tile is 512 px with 14 x 14 cells of 36.6 px (the cells' size is what it always was; the tile is twice as wide
     * so that it comes back half as often).
     */
    private fun cells(seed: Int, tint: Int): PatternPixels {
        val n = 512
        val g = 14
        val size = n / g.toFloat()
        val pts = Array(g * g) { i -> floatArrayOf(((i % g) + 0.15f + 0.7f * hash01(i % g, i / g, seed + 307)) * size, ((i / g) + 0.15f + 0.7f * hash01(i % g, i / g, seed + 311)) * size) }
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val cx = (x / size).toInt()
            val cy = (y / size).toInt()
            var d1 = Float.MAX_VALUE
            var d2 = Float.MAX_VALUE
            for (oy in -1..1) for (ox in -1..1) {
                val gx = cx + ox
                val gy = cy + oy
                val p = pts[wrap(gy, g) * g + wrap(gx, g)]
                val px0 = p[0] + floor(gx / g.toFloat()) * n
                val py0 = p[1] + floor(gy / g.toFloat()) * n
                val dx = x + 0.5f - px0
                val dy = y + 0.5f - py0
                val d = sqrt(dx * dx + dy * dy)
                if (d < d1) {
                    d2 = d1
                    d1 = d
                } else if (d < d2) d2 = d
            }
            val wall = 1f - smooth(0.4f, 1.6f, (d2 - d1) / 2f)
            val shade = 0.12f * (1f - (d1 / (size * 0.8f)).coerceIn(0f, 1f))
            px[y * n + x] = argb(max(wall, shade), tint)
        }
        return PatternPixels(n, n, px)
    }

    /**
     * Marble: the classic turbulence-warped sine. A few soft veins run across the tile, pushed about by fractal
     * turbulence, with finer veins branching off them; the rest stays clear. The bands run along (2, 1) in the tile so
     * that, with the layer turned by [MARBLE_TURN] (the angle of (4, 3)) to keep tile copies off the screen's axes, they
     * still run at the angle (63 degrees from horizontal) they always had.
     */
    private fun marble(seed: Int, tint: Int): PatternPixels {
        val n = 512
        val px = IntArray(n * n)
        val turbulence = Fbm(2, 4, seed + 401, gain = 0.45f, ridged = true)
        val warpField = Fbm(2, 2, seed + 409)
        val cloudField = Fbm(4, 2, seed + 419)
        for (y in 0 until n) for (x in 0 until n) {
            val u = x / n.toFloat()
            val v = y / n.toFloat()
            // Smooth turbulence (few octaves, quick fall-off) bends the bands; a gentle warp keeps them from looking combed.
            val t = turbulence.at(u, v)
            val warp = warpField.at(u, v)
            val phase = 2f * PI.toFloat() * (2f * u + v) + 6.5f * t + 2.2f * warp
            val s = abs(sin(phase))
            // A main vein where the sine crosses zero, softly widening; a thin sister vein beside some of them.
            val main = (1f - s).let { val q = it * it; q * q * q }
            val sister = abs(sin(phase * 2f + 1.3f + 4f * t))
            val fine = 0.4f * (1f - sister).let { val q = it * it * it * it; q * q * q } * smooth(0.2f, 0.6f, 1f - s)
            val cloud = 0.08f * (0.5f + cloudField.at(u, v))
            px[y * n + x] = argb((0.9f * main + fine + cloud).coerceIn(0f, 1f), tint)
        }
        return PatternPixels(n, n, px)
    }

    /** Honeycomb: the edges of a hexagon grid, fine and even. */
    private fun hex(tint: Int): PatternPixels {
        val r = 12f
        val cw = sqrt(3f) * r
        val w = 42
        val h = 36
        val sx = 2f * cw / w
        val px = IntArray(w * h)
        val s3 = sqrt(3f)
        for (y in 0 until h) for (x in 0 until w) {
            val qx = (x + 0.5f) * sx
            val qy = y + 0.5f
            // Distance to the nearest hexagon centre, then to that hexagon's edge.
            var best = Float.MAX_VALUE
            var bx = 0f
            var by = 0f
            for (row in -1..3) for (col in -1..3) {
                val cx = col * cw + (if (row % 2 != 0) cw / 2f else 0f)
                val cy = row * 1.5f * r
                val d = (qx - cx) * (qx - cx) + (qy - cy) * (qy - cy)
                if (d < best) {
                    best = d
                    bx = qx - cx
                    by = qy - cy
                }
            }
            // Pointy-top hexagon: flat sides left and right at the apothem, the slanted ones meeting at the top.
            val ax = abs(bx)
            val ay = abs(by)
            val apothem = r * s3 / 2f
            val dd = apothem - max(ax, ax * 0.5f + ay * s3 / 2f)
            px[y * w + x] = argb(1f - smooth(0.35f, 1.3f, dd), tint)
        }
        return PatternPixels(w, h, px)
    }

    /** Waves: evenly spaced lines that rise and fall gently. */
    private fun waves(tint: Int): PatternPixels {
        val w = 192
        val h = 24
        val px = IntArray(w * h)
        val amp = 5f
        for (y in 0 until h) for (x in 0 until w) {
            val c = h / 2f + amp * sin(2f * PI.toFloat() * x / w + 0.0f) + 1.4f * sin(2f * PI.toFloat() * 3f * x / w)
            var d = Float.MAX_VALUE
            for (k in -1..1) d = min(d, abs(y + 0.5f - (c + k * h)))
            // Thickness measured across the slope, so the line is even where it climbs.
            val slope = 2f * PI.toFloat() / w * (amp * cos(2f * PI.toFloat() * x / w) + 1.4f * 3f * cos(2f * PI.toFloat() * 3f * x / w))
            val across = d / sqrt(1f + slope * slope)
            px[y * w + x] = argb(1f - smooth(0.5f, 1.4f, across), tint)
        }
        return PatternPixels(w, h, px)
    }

    /**
     * Halftone: a dot screen turned 45 degrees, its dots grown and shrunk by a soft fractal noise, like print under a
     * loupe. The dots swell and shrink with noise cells of about 128 px. The tile is 512, 400 or 464 px by the seed modulo 3
     * (the three layers of [layers] use one each, on the same dot sites, and their dots add up), so the swelling comes
     * back far less often than a single tile's.
     */
    private fun halftone(seed: Int, tint: Int): PatternPixels {
        val n = HALFTONE_TILES[((seed % 3) + 3) % 3]
        val pitch = 8f
        val f = fbmField(n, Math.round(n / 128f), 3, seed + 503)
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            // Lattice along the diagonals: wraps with the tile because n / pitch is whole.
            val u = (x + y + 1f) / pitch
            val v = (x - y) / pitch
            val du = u - floor(u + 0.5f)
            val dv = v - floor(v + 0.5f)
            val d = sqrt(du * du + dv * dv) * pitch / sqrt(2f)
            val soft = 0.5f + 0.5f * tanh(f[y * n + x] * 0.9f)
            // Squared: three layers' dots add up, so each holds fewer big dots (the three together keep the old mean size and spread).
            val local = soft * soft
            val r = 0.4f + 2.4f * local
            px[y * n + x] = argb((r + 0.5f - d).coerceIn(0f, 1f), tint)
        }
        return PatternPixels(n, n, px)
    }
}
