package com.ashrdev.aznd.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What the app's own components (see ui/components) need from the theme beyond Material's colour scheme:
 * border widths, button shape, the surfaces' fills, and the palette they were worked out from (which Flex
 * copies when a card is set to "App theme").
 */
data class AzndStyle(
    val cardBorder: Dp,
    val topBarBorder: Dp,
    val bottomBarBorder: Dp,
    val fieldBorder: Dp,
    val outlinedButtonBorder: Dp,
    val filledButtonBorder: Dp,
    val cardBorderLine: Boolean,
    val topBarBorderLine: Boolean,
    val bottomBarBorderLine: Boolean,
    val fieldBorderLine: Boolean,
    val outlinedButtonBorderLine: Boolean,
    val filledButtonBorderLine: Boolean,
    val borderTaper: TaperLine,
    val buttonCorner: Int,
    val buttonShape: Shape,
    val cardCorner: Int,
    val outline: Color,
    val card: Color,
    val topBar: Color,
    val onTopBar: Color,
    val bottomBar: Color,
    val background: Color,
    val text: Color,
    val button: Color,
    val highlight: Color,
    val dark: Boolean,
    /** The palette's gradients / patterns; empty while wallpaper colours are on. */
    val fills: Map<ThemeColor, FillStyle>
) {
    /** The base colour of [element] with its fill. */
    fun fill(element: ThemeColor): ResolvedFill {
        val base = when (element) {
            ThemeColor.BACKGROUND -> background
            ThemeColor.CARD -> card
            ThemeColor.TOP_BAR -> topBar
            ThemeColor.BOTTOM_BAR -> bottomBar
            ThemeColor.BUTTON -> button
            ThemeColor.HIGHLIGHT -> highlight
            ThemeColor.TEXT -> text
            ThemeColor.OUTLINE -> outline
        }
        return ResolvedFill(base, fills[element] ?: FillStyle.Plain)
    }
}

val LocalAzndStyle = staticCompositionLocalOf<AzndStyle> { error("AzndTheme is missing") }

/** Black or white, whichever reads better on [background] (the one with the higher contrast). */
fun readableOn(background: Color): Color =
    if (background.luminance() > 0.179f) Color.Black else Color.White

private fun shapesFor(r: Int) = Shapes(
    extraSmall = RoundedCornerShape((r * 0.33f).dp),
    small = RoundedCornerShape((r * 0.67f).dp),
    medium = RoundedCornerShape(r.dp),
    large = RoundedCornerShape((r * 1.33f).dp),
    extraLarge = RoundedCornerShape((r * 2.33f).dp)
)

/** [p] must already be [effective] (gradients worked down to one colour). */
private fun buildColorScheme(p: Palette, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.button,
        onPrimary = readableOn(p.button),
        primaryContainer = p.highlight,
        onPrimaryContainer = readableOn(p.highlight),
        secondaryContainer = p.highlight,
        onSecondaryContainer = readableOn(p.highlight),
        background = p.background,
        onBackground = p.text,
        surface = p.background,
        onSurface = p.text,
        surfaceVariant = p.card,
        onSurfaceVariant = p.text.copy(alpha = 0.75f),
        surfaceContainerLowest = p.background,
        surfaceContainerLow = p.card,
        surfaceContainer = p.card,
        surfaceContainerHigh = p.card,
        surfaceContainerHighest = p.card,
        outline = p.outline,
        outlineVariant = p.outline.copy(alpha = 0.5f)
    )
}

/**
 * The style [settings] give in light or dark, without wallpaper colours. [AzndTheme] uses it, and Flex uses
 * it to copy a theme (the live one, or a preset's) onto a card.
 */
fun azndStyleOf(settings: ThemeSettings, dark: Boolean): AzndStyle {
    val palette = if (dark) settings.dark else settings.light
    val flat = palette.effective()
    val scheme = buildColorScheme(flat, dark)
    return AzndStyle(
        cardBorder = settings.cardBorder.dp,
        topBarBorder = settings.topBarBorder.dp,
        bottomBarBorder = settings.bottomBarBorder.dp,
        fieldBorder = settings.fieldBorder.dp,
        outlinedButtonBorder = settings.outlinedButtonBorder.dp,
        filledButtonBorder = settings.filledButtonBorder.dp,
        cardBorderLine = settings.cardBorderLine,
        topBarBorderLine = settings.topBarBorderLine,
        bottomBarBorderLine = settings.bottomBarBorderLine,
        fieldBorderLine = settings.fieldBorderLine,
        outlinedButtonBorderLine = settings.outlinedButtonBorderLine,
        filledButtonBorderLine = settings.filledButtonBorderLine,
        borderTaper = settings.borderTaper,
        buttonCorner = settings.buttonCorner,
        buttonShape = RoundedCornerShape(settings.buttonCorner.dp),
        cardCorner = settings.cardCorner,
        outline = scheme.outline,
        card = flat.card,
        topBar = flat.topBar,
        onTopBar = readableOn(flat.topBar),
        bottomBar = flat.bottomBar,
        background = flat.background,
        text = flat.text,
        button = flat.button,
        highlight = flat.highlight,
        dark = dark,
        fills = ThemeColor.entries.filter { it.fillable }.associateWith { palette.fillOf(it) }
    )
}

@Composable
fun AzndTheme(content: @Composable () -> Unit) {
    val settings by ThemeStore.settings.collectAsState()
    val dark = when (settings.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = if (dark) settings.dark else settings.light
    val flat = palette.effective()
    val context = LocalContext.current
    val useDynamic = settings.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = if (useDynamic) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        buildColorScheme(flat, dark)
    }

    val topBar = if (useDynamic) colorScheme.surface else flat.topBar
    val card = if (useDynamic) colorScheme.surfaceVariant else flat.card
    val style = AzndStyle(
        cardBorder = settings.cardBorder.dp,
        topBarBorder = settings.topBarBorder.dp,
        bottomBarBorder = settings.bottomBarBorder.dp,
        fieldBorder = settings.fieldBorder.dp,
        outlinedButtonBorder = settings.outlinedButtonBorder.dp,
        filledButtonBorder = settings.filledButtonBorder.dp,
        cardBorderLine = settings.cardBorderLine,
        topBarBorderLine = settings.topBarBorderLine,
        bottomBarBorderLine = settings.bottomBarBorderLine,
        fieldBorderLine = settings.fieldBorderLine,
        outlinedButtonBorderLine = settings.outlinedButtonBorderLine,
        filledButtonBorderLine = settings.filledButtonBorderLine,
        borderTaper = settings.borderTaper,
        buttonCorner = settings.buttonCorner,
        buttonShape = RoundedCornerShape(settings.buttonCorner.dp),
        cardCorner = settings.cardCorner,
        outline = colorScheme.outline,
        card = card,
        topBar = topBar,
        onTopBar = readableOn(topBar),
        bottomBar = if (useDynamic) card else flat.bottomBar,
        background = colorScheme.background,
        text = colorScheme.onBackground,
        button = colorScheme.primary,
        highlight = colorScheme.primaryContainer,
        dark = dark,
        // Wallpaper colours replace the palette entirely, so its fills are off too.
        fills = if (useDynamic) emptyMap() else ThemeColor.entries.filter { it.fillable }.associateWith { palette.fillOf(it) }
    )

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = shapesFor(settings.cardCorner),
        typography = Typography
    ) {
        CompositionLocalProvider(LocalAzndStyle provides style, content = content)
    }
}
