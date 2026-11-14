package com.ashrdev.aznd.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The high contrast theme: every surface (screen, cards, bars) pitch black, white text, and everything else a shade of
 * one base colour the person picks. Buttons and accents are the base colour itself; highlight cards and outlines are
 * shades of it (outlines strong enough to be seen on the black), made one of two ways ([ContrastShade]), as the person chooses:
 *  - opacity: the base colour at less opacity over black, so the shades are darker versions of it;
 *  - saturation: the base colour with less saturation at its own brightness, so the shades are paler and greyer.
 * Black cards on a black screen are told apart by their outlines, so borders are on. The light and the dark palette
 * are the same: the theme is black either way.
 */
object HighContrast {

    /** How strong each shade is: a share of the opacity, or of the saturation, of the base colour. */
    const val HIGHLIGHT = 0.32f
    const val OUTLINE = 0.55f

    /** What an outline needs on black to be seen (WCAG 3:1), and the little extra that keeps it clear of the line. */
    private const val OUTLINE_CONTRAST = 3.1f

    /** A shade of [base] at [k] (0..1) by its own way of shading. */
    fun shade(base: ContrastBase, k: Float): Color {
        val c = base.color.toArgb()
        return when (base.shade) {
            ContrastShade.ALPHA -> Color(mix(0xFF000000.toInt(), c or (0xFF shl 24), k))
            ContrastShade.SATURATION -> {
                val hsv = toHsv(c)
                Color(fromHsv(hsv[0], hsv[1] * k, hsv[2]))
            }
        }
    }

    /**
     * The outline shade of [base]: [OUTLINE] strong, or as much stronger as it takes to reach 3:1 on black, up to the
     * base colour itself. A dark base (deep blue, purple) would otherwise draw outlines that vanish on the black.
     */
    fun outline(base: ContrastBase): Color {
        var k = OUTLINE
        while (k < 1f) {
            val c = shade(base, k)
            if (contrastOnBlack(c) >= OUTLINE_CONTRAST) return c
            k = (k + 0.01f).coerceAtMost(1f)
        }
        return shade(base, 1f)
    }

    private fun contrastOnBlack(c: Color): Float {
        val a = c.toArgb()
        fun lin(v: Int): Float {
            val x = v / 255f
            return if (x <= 0.04045f) x / 12.92f else Math.pow(((x + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }
        val l = 0.2126f * lin((a shr 16) and 0xFF) + 0.7152f * lin((a shr 8) and 0xFF) + 0.0722f * lin(a and 0xFF)
        return (l + 0.05f) / 0.05f
    }

    /** The palette [base] makes (the same for light and dark). */
    fun palette(base: ContrastBase): Palette {
        val black = Color(0xFF000000.toInt())
        return Palette(
            background = black,
            card = black,
            topBar = black,
            text = Color(0xFFFFFFFF.toInt()),
            button = Color(base.color.toArgb() or (0xFF shl 24)),
            highlight = shade(base, HIGHLIGHT),
            outline = outline(base),
            bottomBar = black
        )
    }

    /**
     * [current] turned into the high contrast theme of [base]: its palettes remade, borders on, dark mode; its corner
     * sizes kept.
     */
    fun apply(current: ThemeSettings, base: ContrastBase): ThemeSettings {
        val p = palette(base)
        // Changing the base of a high contrast theme keeps the person's border widths; becoming one turns them on.
        val keep = current.contrast != null
        return current.copy(
            mode = ThemeMode.DARK,
            useDynamicColor = false,
            cardBorder = if (keep) current.cardBorder else 1.5f,
            topBarBorder = if (keep) current.topBarBorder else 1.5f,
            bottomBarBorder = if (keep) current.bottomBarBorder else 1.5f,
            fieldBorder = if (keep) current.fieldBorder else 1.5f,
            outlinedButtonBorder = if (keep) current.outlinedButtonBorder else 1.5f,
            filledButtonBorder = if (keep) current.filledButtonBorder else 0f,
            light = p,
            dark = p,
            contrast = base
        )
    }

    private fun mix(a: Int, b: Int, t: Float): Int = mixArgb(a, b, t)

    /** Hue (0..360), saturation and value (0..1) of an ARGB colour. */
    private fun toHsv(c: Int): FloatArray {
        val r = ((c shr 16) and 0xFF) / 255f
        val g = ((c shr 8) and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return floatArrayOf(h, if (mx == 0f) 0f else d / mx, mx)
    }

    private fun fromHsv(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r, g, b) = when ((h / 60f).toInt().coerceIn(0, 5)) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(v: Float) = ((v + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}
