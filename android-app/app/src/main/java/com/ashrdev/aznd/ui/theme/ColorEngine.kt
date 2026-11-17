package com.ashrdev.aznd.ui.theme

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object ColorEngine {
    const val BACKGROUND = 0
    const val CARD = 1
    const val TOP_BAR = 2
    const val BOTTOM_BAR = 3
    const val TEXT = 4
    const val BUTTON = 5
    const val HIGHLIGHT = 6
    const val OUTLINE = 7
    const val SIZE = 8

    const val NEUTRAL_CHROMA = 0.0015
    const val SAME_AS_BASE = 0.02
    const val EXACT = 0.002
    const val ACCENT_CONTRAST = 3.0
    const val TEXT_CONTRAST = 4.5
    const val MONO_HIGHLIGHT = 0.32
    const val MONO_OUTLINE = 0.55

    private const val L_STEP = 0.005
    private const val GAMUT_EPS = 1e-6

    private val CURVE_IN = doubleArrayOf(0.0, 0.10, 0.20, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90, 1.0)
    private val CURVE_OUT = doubleArrayOf(1.0, 0.985, 0.94, 0.89, 0.78, 0.64, 0.54, 0.45, 0.36, 0.26, 0.14)

    private const val BLACK = -0x1000000
    private const val WHITE = -1

    fun alphaOf(argb: Int): Int = (argb ushr 24) and 0xFF

    private fun withAlpha(rgb: Int, alpha: Int): Int = (rgb and 0xFFFFFF) or (alpha shl 24)

    private fun toLinear(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun toChannel(linear: Double): Int {
        val x = linear.coerceIn(0.0, 1.0)
        val c = if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1.0 / 2.4) - 0.055
        return (c * 255.0 + 0.5).toInt().coerceIn(0, 255)
    }

    fun toOklab(argb: Int): DoubleArray {
        val r = toLinear((argb shr 16) and 0xFF)
        val g = toLinear((argb shr 8) and 0xFF)
        val b = toLinear(argb and 0xFF)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        )
    }

    private fun oklabToLinear(lightness: Double, a: Double, b: Double): DoubleArray {
        val l = lightness + 0.3963377774 * a + 0.2158037573 * b
        val m = lightness - 0.1055613458 * a - 0.0638541728 * b
        val s = lightness - 0.0894841775 * a - 1.2914855480 * b
        val l3 = l * l * l
        val m3 = m * m * m
        val s3 = s * s * s
        return doubleArrayOf(
            4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3,
            -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3,
            -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3
        )
    }

    fun toOklch(argb: Int): DoubleArray {
        val lab = toOklab(argb)
        val c = sqrt(lab[1] * lab[1] + lab[2] * lab[2])
        var h = Math.toDegrees(atan2(lab[2], lab[1]))
        if (h < 0.0) h += 360.0
        return doubleArrayOf(lab[0], c, h)
    }

    private fun linearAt(l: Double, c: Double, h: Double): DoubleArray {
        val rad = Math.toRadians(h)
        return oklabToLinear(l, c * cos(rad), c * sin(rad))
    }

    private fun inGamut(rgb: DoubleArray): Boolean =
        rgb.all { it >= -GAMUT_EPS && it <= 1.0 + GAMUT_EPS }

    fun fromOklch(l: Double, c: Double, h: Double, alpha: Int = 255): Int {
        val lightness = l.coerceIn(0.0, 1.0)
        var chroma = max(0.0, c)
        var rgb = linearAt(lightness, chroma, h)
        if (!inGamut(rgb)) {
            var lo = 0.0
            var hi = chroma
            repeat(28) {
                val mid = (lo + hi) / 2.0
                if (inGamut(linearAt(lightness, mid, h))) lo = mid else hi = mid
            }
            chroma = lo
            rgb = linearAt(lightness, chroma, h)
        }
        return (alpha shl 24) or (toChannel(rgb[0]) shl 16) or (toChannel(rgb[1]) shl 8) or toChannel(rgb[2])
    }

    fun lightness(argb: Int): Double = toOklab(argb)[0]

    fun deltaE(a: Int, b: Int): Double {
        val x = toOklab(a)
        val y = toOklab(b)
        val dl = x[0] - y[0]
        val da = x[1] - y[1]
        val db = x[2] - y[2]
        return sqrt(dl * dl + da * da + db * db)
    }

    fun isNeutral(argb: Int): Boolean = toOklch(argb)[1] < NEUTRAL_CHROMA

    fun smartGrey(l: Double, alpha: Int = 255): Int = fromOklch(l, 0.0, 0.0, alpha)

    fun tone(l: Double, base: Int, alpha: Int = 255): Int {
        val b = toOklch(base)
        if (b[1] < NEUTRAL_CHROMA) return smartGrey(l, alpha)
        val lb = b[0]
        val share = if (l <= lb) {
            if (lb <= 1e-6) 0.0 else l / lb
        } else {
            if (lb >= 1.0 - 1e-6) 0.0 else (1.0 - l) / (1.0 - lb)
        }
        return fromOklch(l, b[1] * share.coerceIn(0.0, 1.0), b[2], alpha)
    }

    fun recolor(color: Int, base: Int, ref: Int, tintGreys: Boolean): Int {
        val alpha = alphaOf(color)
        val c = toOklch(color)
        if (c[1] < NEUTRAL_CHROMA) return if (tintGreys) tone(c[0], base, alpha) else color
        if (deltaE(withAlpha(color, 255), withAlpha(ref, 255)) < SAME_AS_BASE) return withAlpha(base, alpha)
        val b = toOklch(base)
        val r = toOklch(ref)
        if (r[1] < NEUTRAL_CHROMA) return fromOklch(c[0], c[1], b[2], alpha)
        val chroma = min(c[1] * b[1] / r[1], max(b[1], c[1]))
        return fromOklch(c[0], chroma, c[2] + (b[2] - r[2]), alpha)
    }

    fun unrecolor(shown: Int, base: Int, ref: Int, tintGreys: Boolean): Int {
        val alpha = alphaOf(shown)
        if (tintGreys) return smartGrey(lightness(withAlpha(shown, 255)), alpha)
        if (deltaE(withAlpha(shown, 255), withAlpha(base, 255)) < EXACT) return withAlpha(ref, alpha)
        val s = toOklch(shown)
        val b = toOklch(base)
        val r = toOklch(ref)
        if (s[1] < NEUTRAL_CHROMA || b[1] < NEUTRAL_CHROMA) return shown
        if (r[1] < NEUTRAL_CHROMA) return fromOklch(s[0], s[1], s[2], alpha)
        return fromOklch(s[0], s[1] * r[1] / b[1], s[2] - (b[2] - r[2]), alpha)
    }

    fun lightCurve(l: Double): Double {
        val x = l.coerceIn(0.0, 1.0)
        for (i in 1 until CURVE_IN.size) {
            if (x <= CURVE_IN[i]) {
                val t = (x - CURVE_IN[i - 1]) / (CURVE_IN[i] - CURVE_IN[i - 1])
                return CURVE_OUT[i - 1] + (CURVE_OUT[i] - CURVE_OUT[i - 1]) * t
            }
        }
        return CURVE_OUT.last()
    }

    fun toLight(color: Int): Int {
        val c = toOklch(color)
        return fromOklch(lightCurve(c[0]), c[1], c[2], alphaOf(color))
    }

    fun shiftLightness(color: Int, dl: Double): Int {
        if (dl == 0.0) return color
        val c = toOklch(color)
        return fromOklch(c[0] + dl, c[1], c[2], alphaOf(color))
    }

    fun gradientToLight(colors: IntArray): IntArray {
        if (colors.isEmpty()) return colors
        val lch = colors.map { toOklch(it) }
        val mean = lch.sumOf { it[0] } / lch.size
        val target = lightCurve(mean)
        return IntArray(colors.size) { i ->
            val c = lch[i]
            fromOklch(target + (c[0] - mean), c[1], c[2], alphaOf(colors[i]))
        }
    }

    fun luminance(argb: Int): Double =
        0.2126 * toLinear((argb shr 16) and 0xFF) + 0.7152 * toLinear((argb shr 8) and 0xFF) + 0.0722 * toLinear(argb and 0xFF)

    fun contrast(a: Int, b: Int): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    fun ensureContrast(color: Int, against: Int, minimum: Double): Int {
        if (contrast(color, against) >= minimum) return color
        val alpha = alphaOf(color)
        val c = toOklch(color)
        val darker = lightness(against) > 0.5
        var l = c[0]
        while (true) {
            l += if (darker) -L_STEP else L_STEP
            if (l <= 0.0 || l >= 1.0) break
            val t = fromOklch(l, c[1], c[2], alpha)
            if (contrast(t, against) >= minimum) return t
        }
        return withAlpha(if (darker) BLACK else WHITE, alpha)
    }

    fun ensureContrastAll(color: Int, against: IntArray, minimum: Double): Int {
        var out = color
        repeat(4) {
            for (a in against) out = ensureContrast(out, a, minimum)
            if (against.all { contrast(out, it) >= minimum }) return out
        }
        return out
    }

    fun recolorPalette(design: IntArray, follow: BooleanArray, base: Int, ref: Int, tintGreys: Boolean): IntArray {
        val out = IntArray(SIZE) { i -> if (follow[i]) recolor(design[i], base, ref, tintGreys) else design[i] }
        if (follow.any { it }) {
            out[BUTTON] = ensureContrast(out[BUTTON], out[BACKGROUND], ACCENT_CONTRAST)
            out[OUTLINE] = ensureContrast(out[OUTLINE], out[BACKGROUND], ACCENT_CONTRAST)
        }
        return out
    }

    fun buttonShift(dark: IntArray, light: IntArray): Double = lightness(light[BUTTON]) - lightness(dark[BUTTON])

    fun lightPalette(dark: IntArray): IntArray {
        val out = IntArray(SIZE)
        for (i in 0 until SIZE) out[i] = if (i == BUTTON) dark[i] else toLight(dark[i])
        val bg = out[BACKGROUND]
        out[BUTTON] = ensureContrast(dark[BUTTON], bg, ACCENT_CONTRAST)
        out[OUTLINE] = ensureContrast(out[OUTLINE], bg, ACCENT_CONTRAST)
        out[TEXT] = ensureContrastAll(out[TEXT], intArrayOf(bg, out[CARD], out[TOP_BAR], out[BOTTOM_BAR]), TEXT_CONTRAST)
        return out
    }

    private fun toHsv(c: Int): DoubleArray {
        val r = ((c shr 16) and 0xFF) / 255.0
        val g = ((c shr 8) and 0xFF) / 255.0
        val b = (c and 0xFF) / 255.0
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        var h = when {
            d == 0.0 -> 0.0
            mx == r -> 60.0 * (((g - b) / d) % 6.0)
            mx == g -> 60.0 * (((b - r) / d) + 2.0)
            else -> 60.0 * (((r - g) / d) + 4.0)
        }
        if (h < 0.0) h += 360.0
        return doubleArrayOf(h, if (mx == 0.0) 0.0 else d / mx, mx)
    }

    private fun fromHsv(h: Double, s: Double, v: Double): Int {
        val c = v * s
        val x = c * (1.0 - abs((h / 60.0) % 2.0 - 1.0))
        val m = v - c
        val sector = (h / 60.0).toInt().coerceIn(0, 5)
        val r: Double
        val g: Double
        val b: Double
        when (sector) {
            0 -> { r = c; g = x; b = 0.0 }
            1 -> { r = x; g = c; b = 0.0 }
            2 -> { r = 0.0; g = c; b = x }
            3 -> { r = 0.0; g = x; b = c }
            4 -> { r = x; g = 0.0; b = c }
            else -> { r = c; g = 0.0; b = x }
        }
        fun ch(v: Double) = ((v + m) * 255.0 + 0.5).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    private fun mixToward(from: Int, to: Int, t: Double): Int {
        fun ch(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * t + 0.5).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun monoShade(base: Int, k: Double, bySaturation: Boolean): Int {
        val opaque = withAlpha(base, 255)
        return if (bySaturation) {
            val hsv = toHsv(opaque)
            fromHsv(hsv[0], hsv[1] * k, hsv[2])
        } else {
            mixToward(BLACK, opaque, k)
        }
    }

    fun monoDark(base: Int, bySaturation: Boolean): IntArray {
        val out = IntArray(SIZE) { BLACK }
        out[TEXT] = WHITE
        out[BUTTON] = ensureContrast(withAlpha(base, 255), BLACK, ACCENT_CONTRAST)
        out[HIGHLIGHT] = monoShade(base, MONO_HIGHLIGHT, bySaturation)
        var k = MONO_OUTLINE
        var outline = monoShade(base, k, bySaturation)
        while (contrast(outline, BLACK) < ACCENT_CONTRAST + 0.1 && k < 1.0) {
            k = min(1.0, k + 0.01)
            outline = monoShade(base, k, bySaturation)
        }
        out[OUTLINE] = ensureContrast(outline, BLACK, ACCENT_CONTRAST)
        return out
    }
}
