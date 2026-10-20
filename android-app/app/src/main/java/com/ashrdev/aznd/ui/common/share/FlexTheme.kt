package com.ashrdev.aznd.ui.common.share

import androidx.compose.ui.graphics.toArgb
import com.ashrdev.aznd.ui.theme.AzndStyle
import com.ashrdev.aznd.ui.theme.FillBase
import com.ashrdev.aznd.ui.theme.ThemeColor
import kotlin.math.roundToInt

/** Card pixels per dp when an app measurement (corner, border) is carried onto a Flex card. */
private const val PX_PER_DP = 3f

/**
 * Makes a card look like the app: the System theme starter look. The screen's colour, gradient and
 * pattern become the card's background; the Cards fill becomes the panels' (opaque, with the same gradient
 * or pattern); the Outlines colour and the border widths, corner size, text and accent colours follow.
 *
 * The app's borders are carried over scaled to the card: panels get the Cards border and the card itself
 * the widest of the card / top bar / bottom bar borders (the card is the whole "screen"), at 3 card pixels
 * per dp (outer border doubled, so it still reads on a 1080 px poster). Layout, font, size and photo
 * choices are left alone.
 */
fun FlexStyle.withAppTheme(app: AzndStyle): FlexStyle {
    val bg = app.fill(ThemeColor.BACKGROUND)
    val card = app.fill(ThemeColor.CARD)
    val gradient = bg.style.gradient
    val isGradient = bg.style.base == FillBase.GRADIENT
    val stops = gradient.cleanStops()
    val outline = app.outline.toArgb()
    val outer = maxOf(app.cardBorder, app.topBarBorder, app.bottomBarBorder)
    return copy(
        bgMode = if (isGradient) FlexBgMode.GRADIENT else FlexBgMode.SOLID,
        bgA = if (isGradient) stops.first().color else bg.base.toArgb(),
        bgB = if (isGradient) stops.last().color else card.base.toArgb(),
        gradientAngle = gradient.angle,
        bgGradient = if (isGradient) gradient else null,
        bgPatterns = bg.style.patterns,
        textColor = app.text.toArgb(),
        accent = app.button.toArgb(),
        panel = FlexPanel.FILLED,
        panelColor = card.base.toArgb(),
        panelAlpha = 1f,
        panelFill = card.style,
        panelRadius = (app.cardCorner * PX_PER_DP).roundToInt(),
        panelBorderWidth = (app.cardBorder.value * PX_PER_DP).roundToInt(),
        panelBorderColor = outline,
        borderWidth = (outer.value * PX_PER_DP * 2f).roundToInt(),
        borderColor = outline
    )
}
