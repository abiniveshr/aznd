package com.ashrdev.aznd.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ashrdev.aznd.ui.common.share.FlexFont
import com.ashrdev.aznd.ui.common.share.FlexFonts
import java.util.concurrent.ConcurrentHashMap

// The Material 3 defaults, so the platform and line-height settings of each style stay as Material has them.
private val MaterialDefaults = Typography()

private fun TextStyle.sized(
    family: FontFamily,
    weight: FontWeight,
    size: TextUnit,
    lineHeight: TextUnit,
    letterSpacing: TextUnit,
    scale: Float
): TextStyle = copy(
    // The system default keeps the family Material gave the style.
    fontFamily = if (family == FontFamily.Default) fontFamily else family,
    fontWeight = weight,
    fontSize = size * scale,
    lineHeight = lineHeight * scale,
    letterSpacing = letterSpacing
)

/** aznd's type scale in [family], sizes and line heights multiplied by [scale]. Weights and letter spacing are Material 3's. */
fun azndTypography(family: FontFamily, scale: Float = 1f): Typography = MaterialDefaults.let { m ->
    Typography(
        displayLarge = m.displayLarge.sized(family, FontWeight.Normal, 57.sp, 64.sp, (-0.25).sp, scale),
        displayMedium = m.displayMedium.sized(family, FontWeight.Normal, 45.sp, 52.sp, 0.sp, scale),
        displaySmall = m.displaySmall.sized(family, FontWeight.Normal, 36.sp, 44.sp, 0.sp, scale),
        headlineLarge = m.headlineLarge.sized(family, FontWeight.Normal, 32.sp, 40.sp, 0.sp, scale),
        headlineMedium = m.headlineMedium.sized(family, FontWeight.Normal, 28.sp, 36.sp, 0.sp, scale),
        headlineSmall = m.headlineSmall.sized(family, FontWeight.Normal, 24.sp, 32.sp, 0.sp, scale),
        titleLarge = m.titleLarge.sized(family, FontWeight.Normal, 22.sp, 28.sp, 0.sp, scale),
        titleMedium = m.titleMedium.sized(family, FontWeight.Medium, 16.sp, 24.sp, 0.15.sp, scale),
        titleSmall = m.titleSmall.sized(family, FontWeight.Medium, 14.sp, 20.sp, 0.1.sp, scale),
        // The one style aznd has always set itself: a plain TextStyle, not Material's.
        bodyLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp * scale,
            lineHeight = 24.sp * scale,
            letterSpacing = 0.5.sp
        ),
        bodyMedium = m.bodyMedium.sized(family, FontWeight.Normal, 14.sp, 20.sp, 0.25.sp, scale),
        bodySmall = m.bodySmall.sized(family, FontWeight.Normal, 12.sp, 16.sp, 0.4.sp, scale),
        labelLarge = m.labelLarge.sized(family, FontWeight.Medium, 14.sp, 20.sp, 0.1.sp, scale),
        labelMedium = m.labelMedium.sized(family, FontWeight.Medium, 12.sp, 16.sp, 0.5.sp, scale),
        labelSmall = m.labelSmall.sized(family, FontWeight.Medium, 11.sp, 16.sp, 0.5.sp, scale)
    )
}

/** Builds and keeps the Compose font family of each font the app can be set to. */
object AppFonts {
    private val cache = ConcurrentHashMap<FlexFont, FontFamily>()

    /** The family for [font]; null is the system default. Never throws: a font that cannot load gives the system default. */
    fun familyOf(font: FlexFont?, context: Context): FontFamily {
        if (font == null) return FontFamily.Default
        cache[font]?.let { return it }
        FlexFonts.attach(context)
        val family = runCatching { build(font, context) }.getOrNull() ?: FontFamily.Default
        // Not kept when it fell back, so a later try (assets attached by then) can still succeed.
        if (family != FontFamily.Default) cache[font] = family
        return family
    }

    private fun build(font: FlexFont, context: Context): FontFamily {
        when (font) {
            FlexFont.SANS -> return FontFamily.SansSerif
            FlexFont.SERIF -> return FontFamily.Serif
            FlexFont.MONO -> return FontFamily.Monospace
            else -> Unit
        }
        val bold = font.boldAsset
        val asset = font.asset
        // Real regular and bold files, when both are there and load.
        if (asset != null && bold != null && FlexFonts.load(font, false) != null && FlexFonts.load(font, true) != null) {
            val assets = context.applicationContext.assets
            return FontFamily(
                Font(asset, assets, FontWeight.Normal),
                Font(bold, assets, FontWeight.Bold)
            )
        }
        // One face for every weight; FlexFont falls back to Sans itself when a file is missing.
        return FontFamily(androidx.compose.ui.text.font.Typeface(font.face(false)))
    }
}
