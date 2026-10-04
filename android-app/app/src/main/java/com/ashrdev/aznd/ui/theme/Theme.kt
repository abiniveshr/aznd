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
import androidx.compose.ui.unit.dp

data class AzndStyle(
    val cardOutline: Boolean,
    val topBarOutline: Boolean,
    val fieldOutline: Boolean,
    val outlinedButtonOutline: Boolean,
    val filledButtonOutline: Boolean,
    val buttonShape: Shape,
    val outline: Color,
    val card: Color,
    val topBar: Color,
    val onTopBar: Color
)

val LocalAzndStyle = staticCompositionLocalOf<AzndStyle> { error("AzndTheme is missing") }

fun readableOn(background: Color): Color =
    if (background.luminance() > 0.5f) Color.Black else Color.White

private fun shapesFor(r: Int) = Shapes(
    extraSmall = RoundedCornerShape((r * 0.33f).dp),
    small = RoundedCornerShape((r * 0.67f).dp),
    medium = RoundedCornerShape(r.dp),
    large = RoundedCornerShape((r * 1.33f).dp),
    extraLarge = RoundedCornerShape((r * 2.33f).dp)
)

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

@Composable
fun AzndTheme(content: @Composable () -> Unit) {
    val settings by ThemeStore.settings.collectAsState()
    val dark = when (settings.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = if (dark) settings.dark else settings.light
    val context = LocalContext.current
    val useDynamic = settings.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = if (useDynamic) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        buildColorScheme(palette, dark)
    }

    val topBar = if (useDynamic) colorScheme.surface else palette.topBar
    val style = AzndStyle(
        cardOutline = settings.cardOutline,
        topBarOutline = settings.topBarOutline,
        fieldOutline = settings.fieldOutline,
        outlinedButtonOutline = settings.outlinedButtonOutline,
        filledButtonOutline = settings.filledButtonOutline,
        buttonShape = RoundedCornerShape(settings.buttonCorner.dp),
        outline = colorScheme.outline,
        card = if (useDynamic) colorScheme.surfaceVariant else palette.card,
        topBar = topBar,
        onTopBar = readableOn(topBar)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = shapesFor(settings.cardCorner),
        typography = Typography
    ) {
        CompositionLocalProvider(LocalAzndStyle provides style, content = content)
    }
}
