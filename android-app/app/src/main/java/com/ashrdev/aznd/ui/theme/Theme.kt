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
import androidx.compose.runtime.remember
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
    /** The text colour chosen for the bottom bar's fill. */
    val onBottomBar: Color,
    val background: Color,
    /** The text colours chosen for the background, cards, buttons and highlight cards, each from its own fill. */
    val onBackground: Color,
    val onCard: Color,
    val onButton: Color,
    val onHighlight: Color,
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

/** [p] must already be [effective] (gradients worked down to one colour); [on] is the text colour of each of its surfaces. */
private fun buildColorScheme(p: Palette, dark: Boolean, on: SurfaceTexts): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.button,
        onPrimary = on.button,
        primaryContainer = p.highlight,
        onPrimaryContainer = on.highlight,
        secondaryContainer = p.highlight,
        onSecondaryContainer = on.highlight,
        background = p.background,
        onBackground = on.background,
        surface = p.background,
        onSurface = on.card,
        surfaceVariant = p.card,
        onSurfaceVariant = on.card.copy(alpha = 0.75f),
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
    val on = surfaceTextsOf(palette)
    val scheme = buildColorScheme(flat, dark, on)
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
        onTopBar = on.topBar,
        bottomBar = flat.bottomBar,
        onBottomBar = on.bottomBar,
        background = flat.background,
        onBackground = on.background,
        onCard = on.card,
        onButton = on.button,
        onHighlight = on.highlight,
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
    AzndTheme(settings, content)
}

/** The theme for [settings] (the committed ones for the app; the Appearance preview passes the draft). */
@Composable
fun AzndTheme(settings: ThemeSettings, content: @Composable () -> Unit) {
    val dark = when (settings.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = if (dark) settings.dark else settings.light
    val context = LocalContext.current
    val useDynamic = settings.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = remember(palette, dark, useDynamic, context) {
        if (useDynamic) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            buildColorScheme(palette.effective(), dark, surfaceTextsOf(palette))
        }
    }

    val style = remember(settings, dark, useDynamic, colorScheme) {
        val flat = palette.effective()
        val topBar = if (useDynamic) colorScheme.surface else flat.topBar
        val card = if (useDynamic) colorScheme.surfaceVariant else flat.card
        // Wallpaper colours bring their own text colours; the palette's fills are not in play then.
        val on = if (useDynamic) null else surfaceTextsOf(palette)
        AzndStyle(
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
            onTopBar = on?.topBar ?: readableOn(topBar),
            bottomBar = if (useDynamic) card else flat.bottomBar,
            onBottomBar = on?.bottomBar ?: colorScheme.onSurface,
            background = colorScheme.background,
            onBackground = colorScheme.onBackground,
            onCard = colorScheme.onSurface,
            onButton = colorScheme.onPrimary,
            onHighlight = colorScheme.onPrimaryContainer,
            text = colorScheme.onBackground,
            button = colorScheme.primary,
            highlight = colorScheme.primaryContainer,
            dark = dark,
            // Wallpaper colours replace the palette entirely, so its fills are off too.
            fills = if (useDynamic) emptyMap() else ThemeColor.entries.filter { it.fillable }.associateWith { palette.fillOf(it) }
        )
    }
    val shapes = remember(settings.cardCorner) { shapesFor(settings.cardCorner) }
    val fontFamily = remember(settings.appFont, context) { AppFonts.familyOf(settings.appFont, context) }
    val typography = remember(fontFamily, settings.textScale) { azndTypography(fontFamily, settings.textScale) }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = shapes,
        typography = typography
    ) {
        CompositionLocalProvider(LocalAzndStyle provides style, content = content)
    }
}
