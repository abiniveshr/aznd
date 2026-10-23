package com.ashrdev.aznd.ui.theme

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
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
 * metal, speckle) are also laid on twice, the second copy turned and scaled by an odd amount ([PatternMath.layers]),
 * so nothing is seen to repeat however big the surface is. A tile holds its pattern as alpha: light (white) and dark
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

    /** How [kind] is laid on a surface: organic patterns twice (see the file comment), the others once. */
    fun layers(kind: PatternKind): List<PatternLayer> = when (kind) {
        PatternKind.NOISE -> listOf(PatternLayer(1f, 1f, 0f, 0.75f, 0), PatternLayer(1.37f, 1.37f, 31f, 0.6f, 1))
        PatternKind.CLOUDS -> listOf(PatternLayer(2f, 2f, 0f, 0.7f, 0), PatternLayer(3.06f, 3.06f, 47f, 0.55f, 1))
        PatternKind.SPECKLE -> listOf(PatternLayer(1f, 1f, 0f, 1f, 0), PatternLayer(1.41f, 1.41f, 23f, 1f, 1))
        // Brushed metal keeps its grain level: the second copy is only stretched along the strokes.
        PatternKind.BRUSHED -> listOf(PatternLayer(1f, 1f, 0f, 0.75f, 0), PatternLayer(1.618f, 1f, 0f, 0.6f, 1))
        PatternKind.TOPO, PatternKind.MARBLE -> listOf(PatternLayer(2f, 2f, 0f, 1f, 0))
        PatternKind.CELLS -> listOf(PatternLayer(1.5f, 1.5f, 0f, 1f, 0))
        else -> listOf(PatternLayer(1f, 1f, 0f, 1f, 0))
    }

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

    /** 256 unit gradient directions for [perlin]. */
    private val GX = FloatArray(256) { cos(2.0 * PI * it / 256).toFloat() }
    private val GY = FloatArray(256) { sin(2.0 * PI * it / 256).toFloat() }

    private fun fade(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)

    private fun wrap(i: Int, p: Int): Int = ((i % p) + p) % p

    /** Perlin gradient noise at ([x], [y]) on a lattice that repeats every [period] cells. About -0.7..0.7. */
    private fun perlin(x: Float, y: Float, period: Int, seed: Int): Float {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        fun corner(ix: Int, iy: Int, dx: Float, dy: Float): Float {
            val g = (hash(wrap(ix, period), wrap(iy, period), seed) ushr 8) and 255
            return GX[g] * dx + GY[g] * dy
        }
        val n00 = corner(x0, y0, fx, fy)
        val n10 = corner(x0 + 1, y0, fx - 1f, fy)
        val n01 = corner(x0, y0 + 1, fx, fy - 1f)
        val n11 = corner(x0 + 1, y0 + 1, fx - 1f, fy - 1f)
        val u = fade(fx)
        val v = fade(fy)
        val a = n00 + (n10 - n00) * u
        val b = n01 + (n11 - n01) * u
        return a + (b - a) * v
    }

    /**
     * A fractal sum of [octaves] Perlin noises at [u], [v] in 0..1 of the tile, the first with [cells] lattice cells
     * across it, each next twice as fine and [gain] as strong. Seamless. [ridged]: turbulence (absolute values).
     */
    private fun fbm(u: Float, v: Float, cells: Int, octaves: Int, seed: Int, gain: Float = 0.5f, ridged: Boolean = false): Float {
        var sum = 0f
        var amp = 1f
        var norm = 0f
        var c = cells
        for (o in 0 until octaves) {
            val n = perlin(u * c, v * c, c, seed + o * 1013)
            sum += amp * (if (ridged) abs(n) else n)
            norm += amp
            amp *= gain
            c *= 2
        }
        return sum / norm
    }

    /** A field of fBm over an [n] × [n] tile, standardised. */
    private fun fbmField(n: Int, cells: Int, octaves: Int, seed: Int, gain: Float = 0.5f): FloatArray {
        val a = FloatArray(n * n)
        for (y in 0 until n) for (x in 0 until n) a[y * n + x] = fbm(x / n.toFloat(), y / n.toFloat(), cells, octaves, seed, gain)
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

    /** Sparse specks of varied size and brightness with a faint glow, like stars or terrazzo. */
    private fun speckle(seed: Int, tint: Int): PatternPixels {
        val n = 256
        val rnd = java.util.Random(seed * 7919L + 23)
        val a = FloatArray(n * n)
        repeat((n * n * 0.004f).toInt()) {
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
     * Brushed metal, the way it is made: white noise smeared hard along the strokes (an anisotropic blur) and barely
     * across them, so every line is a thread of slightly varying brightness that fades in and out; a finer grain
     * over it, and now and then a longer, brighter scratch. Low contrast: real brushing is felt more than seen.
     */
    private fun brushed(seed: Int): PatternPixels {
        val w = 512
        val h = 256
        val rnd = java.util.Random(seed * 7919L + 37)
        var long = gaussianNoise(w * h, rnd)
        long = blurRows(long, w, h, 70f)
        long = blurCols(long, w, h, 0.5f)
        standardise(long)
        var fine = gaussianNoise(w * h, rnd)
        fine = blurRows(fine, w, h, 14f)
        standardise(fine)
        // Each row a little lighter or darker as a whole: the brush's passes.
        val pass = FloatArray(h) { rnd.nextFloat() * 2f - 1f }
        val passes = FloatArray(h) { y -> (pass[wrap(y - 1, h)] + 2f * pass[y] + pass[wrap(y + 1, h)]) / 4f }
        val v = FloatArray(w * h) { i -> 0.8f * long[i] + 0.3f * fine[i] + 0.35f * passes[i / w] }
        // Scratches: long thin bright lines that fade at both ends.
        repeat(26) {
            val y = rnd.nextInt(h)
            val x0 = rnd.nextInt(w)
            val len = 80 + rnd.nextInt(260)
            val s = 0.9f + 1.2f * rnd.nextFloat()
            for (i in 0 until len) {
                val t = i / len.toFloat()
                val e = sin(PI.toFloat() * t)
                v[y * w + wrap(x0 + i, w)] += s * e
            }
        }
        return PatternPixels(w, h, IntArray(w * h) { signed(tanh(v[it] * 0.36f)) })
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
     */
    private fun cells(seed: Int, tint: Int): PatternPixels {
        val n = 256
        val g = 7
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
     * turbulence, with finer veins branching off them; the rest stays clear.
     */
    private fun marble(seed: Int, tint: Int): PatternPixels {
        val n = 512
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val u = x / n.toFloat()
            val v = y / n.toFloat()
            // Smooth turbulence (few octaves, quick fall-off) bends the bands; a gentle warp keeps them from looking combed.
            val t = fbm(u, v, 2, 4, seed + 401, gain = 0.45f, ridged = true)
            val warp = fbm(u, v, 2, 2, seed + 409)
            val phase = 2f * PI.toFloat() * (u + 2f * v) + 6.5f * t + 2.2f * warp
            val s = abs(sin(phase))
            // A main vein where the sine crosses zero, softly widening; a thin sister vein beside some of them.
            val main = (1f - s).let { val q = it * it; q * q * q }
            val sister = abs(sin(phase * 2f + 1.3f + 4f * t))
            val fine = 0.4f * (1f - sister).let { val q = it * it * it * it; q * q * q } * smooth(0.2f, 0.6f, 1f - s)
            val cloud = 0.08f * (0.5f + fbm(u, v, 4, 2, seed + 419))
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
     * loupe.
     */
    private fun halftone(seed: Int, tint: Int): PatternPixels {
        val n = 256
        val pitch = 8f
        val f = fbmField(n, 2, 3, seed + 503)
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            // Lattice along the diagonals: wraps with the tile because n / pitch is whole.
            val u = (x + y + 1f) / pitch
            val v = (x - y) / pitch
            val du = u - floor(u + 0.5f)
            val dv = v - floor(v + 0.5f)
            val d = sqrt(du * du + dv * dv) * pitch / sqrt(2f)
            val local = 0.5f + 0.5f * tanh(f[y * n + x] * 0.9f)
            val r = 0.4f + 2.4f * local
            px[y * n + x] = argb((r + 0.5f - d).coerceIn(0f, 1f), tint)
        }
        return PatternPixels(n, n, px)
    }
}
