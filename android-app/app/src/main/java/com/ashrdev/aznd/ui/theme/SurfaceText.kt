package com.ashrdev.aznd.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/** The contrast ratio text needs against every colour its surface can show (WCAG AA for body text). */
private const val MIN_CONTRAST = 4.5f

/**
 * The darkest and lightest relative luminance [fill] can show anywhere on its surface: the base colour, or every
 * point along its gradient, then each pushed by its patterns. A pattern layer drawn at alpha a moves a pixel up to
 * a of the way towards white and towards black (neutral patterns) or towards its tint (tinted ones; clouds also
 * darken to black). Every layer is taken at its strongest on every pixel at once, which is more than any real pixel
 * gets, so the range errs on the wide side.
 */
internal fun surfaceLuminanceRange(fill: ResolvedFill): ClosedFloatingPointRange<Float> {
    val style = fill.style
    var colors: List<Color> = if (style.base == FillBase.GRADIENT) {
        val g = style.gradient
        g.cleanStops().map { Color(it.color) } + (0..16).map { Color(g.colorAt(it / 16f)) }
    } else {
        listOf(fill.base)
    }
    colors = extremes(colors)
    for (p in style.activePatterns) {
        val targets = when {
            !p.kind.tinted -> listOf(Color.White, Color.Black)
            p.kind == PatternKind.CLOUDS -> listOf(Color(p.tint), Color.Black)
            else -> listOf(Color(p.tint))
        }
        for (layer in PatternMath.layers(p.kind)) {
            val a = (p.intensity * layer.alpha).coerceIn(0f, 1f)
            if (a <= 0f) continue
            val next = ArrayList<Color>(colors)
            for (c in colors) for (t in targets) {
                // Half way too: a colour can be darker part of the way to a target than at either end.
                next += towards(c, t, a * 0.5f)
                next += towards(c, t, a)
            }
            colors = extremes(next)
        }
    }
    val lums = colors.map { it.luminance() }
    return (lums.min())..(lums.max())
}

private fun towards(c: Color, target: Color, a: Float) = Color(
    red = c.red + (target.red - c.red) * a,
    green = c.green + (target.green - c.green) * a,
    blue = c.blue + (target.blue - c.blue) * a,
    alpha = 1f
)

/** Just the darkest and the lightest of [colors]: all the contrast check looks at. */
private fun extremes(colors: List<Color>): List<Color> {
    val dark = colors.minBy { it.luminance() }
    val light = colors.maxBy { it.luminance() }
    return listOf(dark, light)
}

private fun contrastBetween(a: Float, b: Float): Float = (max(a, b) + 0.05f) / (min(a, b) + 0.05f)

/** The lowest contrast [text] has against any luminance in [range]: 1 when the text sits inside it. */
private fun worstContrast(text: Color, range: ClosedFloatingPointRange<Float>): Float {
    val l = text.luminance()
    if (l >= range.start && l <= range.endInclusive) return 1f
    return min(contrastBetween(l, range.start), contrastBetween(l, range.endInclusive))
}

/**
 * The text colour for a surface painted with [fill]. [preferred] stays when it reads at 4.5:1 against everything
 * the surface can show; otherwise the best of [preferred], [paletteText], white and black (the first wins a tie).
 */
internal fun textColorOn(fill: ResolvedFill, preferred: Color, paletteText: Color): Color {
    val range = surfaceLuminanceRange(fill)
    if (worstContrast(preferred, range) >= MIN_CONTRAST) return preferred
    return listOf(preferred, paletteText, Color.White, Color.Black).maxBy { worstContrast(it, range) }
}

/** The text colour chosen for each surface of a palette. */
internal class SurfaceTexts(
    val background: Color,
    val card: Color,
    val topBar: Color,
    val bottomBar: Color,
    val button: Color,
    val highlight: Color
)

/**
 * Picks the text colour of each surface of [palette] from its real fill. Cards, background and bottom bar prefer the
 * palette's text colour; the top bar, buttons and highlight cards prefer black or white by their base colour, as
 * they always have.
 */
internal fun surfaceTextsOf(palette: Palette): SurfaceTexts {
    fun plain(e: ThemeColor): Color = textColorOn(palette.resolved(e), palette.text, palette.text)
    fun readable(e: ThemeColor): Color {
        val fill = palette.resolved(e)
        return textColorOn(fill, readableOn(fill.representative()), palette.text)
    }
    return SurfaceTexts(
        background = plain(ThemeColor.BACKGROUND),
        card = plain(ThemeColor.CARD),
        topBar = readable(ThemeColor.TOP_BAR),
        bottomBar = plain(ThemeColor.BOTTOM_BAR),
        button = readable(ThemeColor.BUTTON),
        highlight = readable(ThemeColor.HIGHLIGHT)
    )
}
