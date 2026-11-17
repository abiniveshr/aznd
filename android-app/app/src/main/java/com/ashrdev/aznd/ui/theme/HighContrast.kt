package com.ashrdev.aznd.ui.theme

import androidx.compose.ui.graphics.Color

object HighContrast {

    val HIGHLIGHT = ColorEngine.MONO_HIGHLIGHT.toFloat()
    val OUTLINE = ColorEngine.MONO_OUTLINE.toFloat()

    fun shade(base: Int, shade: ContrastShade, k: Float): Color =
        Color(ColorEngine.monoShade(base, k.toDouble(), shade == ContrastShade.SATURATION))

    fun palette(base: Int, shade: ContrastShade): Palette {
        val c = ColorEngine.monoDark(base, shade == ContrastShade.SATURATION)
        return ThemeDerive.paletteOf(c, false, emptyMap())
    }
}
