package com.ashrdev.aznd.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

object ThemeDerive {

    private val elements = ThemeColor.entries

    fun colorsOf(p: Palette): IntArray = IntArray(ColorEngine.SIZE) { p.colorOf(elements[it]).toArgb() }

    fun paletteOf(c: IntArray, unspecifiedBottom: Boolean, fills: Map<ThemeColor, FillStyle>): Palette = Palette(
        background = Color(c[ColorEngine.BACKGROUND]),
        card = Color(c[ColorEngine.CARD]),
        topBar = Color(c[ColorEngine.TOP_BAR]),
        text = Color(c[ColorEngine.TEXT]),
        button = Color(c[ColorEngine.BUTTON]),
        highlight = Color(c[ColorEngine.HIGHLIGHT]),
        outline = Color(c[ColorEngine.OUTLINE]),
        bottomBar = if (unspecifiedBottom) Color.Unspecified else Color(c[ColorEngine.BOTTOM_BAR]),
        fills = fills
    )

    fun mapFill(fill: FillStyle, transform: (Int) -> Int): FillStyle = fill.copy(
        gradient = fill.gradient.copy(stops = fill.gradient.stops.map { GradientStop(it.position, transform(it.color)) }),
        patterns = fill.patterns.map { it.copy(tint = transform(it.tint)) }
    )

    fun dark(def: ThemeDef, base: Int, shade: ContrastShade): Palette = when (def.kind) {
        ThemeKind.WALLPAPER -> DefaultDarkPalette
        ThemeKind.MONO -> paletteOf(ColorEngine.monoDark(base, shade == ContrastShade.SATURATION), false, emptyMap())
        ThemeKind.TEMPLATE -> template(def, base)
    }

    private fun template(def: ThemeDef, base: Int): Palette {
        val design = def.design
        val designColors = colorsOf(design)
        val ref = def.refBase
        val tint = def.tintGreys
        val follow = BooleanArray(ColorEngine.SIZE) { def.variable && elements[it] !in def.excluded }
        val bottomOnCards = design.bottomBar == Color.Unspecified &&
            follow[ColorEngine.BOTTOM_BAR] == follow[ColorEngine.CARD]
        val out = ColorEngine.recolorPalette(designColors, follow, base, ref, tint)
        val before = if (follow[ColorEngine.BUTTON]) ColorEngine.recolor(designColors[ColorEngine.BUTTON], base, ref, tint)
        else designColors[ColorEngine.BUTTON]
        val shift = ColorEngine.lightness(out[ColorEngine.BUTTON]) - ColorEngine.lightness(before)
        val fills = LinkedHashMap<ThemeColor, FillStyle>()
        for (e in elements) {
            if (!e.fillable) continue
            val source = design.fills[e]
                ?: if (e == ThemeColor.BOTTOM_BAR && design.bottomBar == Color.Unspecified && !bottomOnCards) design.fills[ThemeColor.CARD] else null
            if (source == null) continue
            var fill = if (follow[e.ordinal]) mapFill(source) { ColorEngine.recolor(it, base, ref, tint) } else source
            if (e == ThemeColor.BUTTON && shift != 0.0) fill = mapFill(fill) { ColorEngine.shiftLightness(it, shift) }
            fills[e] = fill
        }
        return paletteOf(out, bottomOnCards, fills)
    }

    fun light(dark: Palette): Palette {
        val d = colorsOf(dark)
        val l = ColorEngine.lightPalette(d)
        val shift = ColorEngine.buttonShift(d, l)
        val fills = LinkedHashMap<ThemeColor, FillStyle>()
        for ((e, fill) in dark.fills) {
            fills[e] = if (e == ThemeColor.BUTTON) mapFill(fill) { ColorEngine.shiftLightness(it, shift) } else lightFill(fill)
        }
        return paletteOf(l, dark.bottomBar == Color.Unspecified, fills)
    }

    private fun lightFill(fill: FillStyle): FillStyle {
        val stops = fill.gradient.stops
        val colors = ColorEngine.gradientToLight(IntArray(stops.size) { stops[it].color })
        return fill.copy(
            gradient = fill.gradient.copy(stops = stops.mapIndexed { i, s -> GradientStop(s.position, colors[i]) }),
            patterns = fill.patterns.map { it.copy(tint = ColorEngine.toLight(it.tint)) }
        )
    }

    fun palette(def: ThemeDef, base: Int, shade: ContrastShade, dark: Boolean): Palette {
        val d = dark(def, base, shade)
        return if (dark) d else lightOf(def, d)
    }

    private fun lightOf(def: ThemeDef, dark: Palette): Palette = when (def.kind) {
        ThemeKind.WALLPAPER -> DefaultLightPalette
        ThemeKind.MONO -> light(dark)
        ThemeKind.TEMPLATE -> light(dark)
    }

    fun look(s: ThemeSettings): ThemeLook = ThemeLook(
        cardBorder = s.cardBorder,
        topBarBorder = s.topBarBorder,
        bottomBarBorder = s.bottomBarBorder,
        fieldBorder = s.fieldBorder,
        outlinedButtonBorder = s.outlinedButtonBorder,
        filledButtonBorder = s.filledButtonBorder,
        cardBorderLine = s.cardBorderLine,
        topBarBorderLine = s.topBarBorderLine,
        bottomBarBorderLine = s.bottomBarBorderLine,
        fieldBorderLine = s.fieldBorderLine,
        outlinedButtonBorderLine = s.outlinedButtonBorderLine,
        filledButtonBorderLine = s.filledButtonBorderLine,
        borderTaper = s.borderTaper
    )

    fun withLook(s: ThemeSettings, look: ThemeLook): ThemeSettings = s.copy(
        cardBorder = look.cardBorder,
        topBarBorder = look.topBarBorder,
        bottomBarBorder = look.bottomBarBorder,
        fieldBorder = look.fieldBorder,
        outlinedButtonBorder = look.outlinedButtonBorder,
        filledButtonBorder = look.filledButtonBorder,
        cardBorderLine = look.cardBorderLine,
        topBarBorderLine = look.topBarBorderLine,
        bottomBarBorderLine = look.bottomBarBorderLine,
        fieldBorderLine = look.fieldBorderLine,
        outlinedButtonBorderLine = look.outlinedButtonBorderLine,
        filledButtonBorderLine = look.filledButtonBorderLine,
        borderTaper = look.borderTaper
    )

    fun apply(current: ThemeSettings, def: ThemeDef, base: Int, shade: ContrastShade): ThemeSettings {
        val darkPalette = dark(def, base, shade)
        val lightPalette = lightOf(def, darkPalette)
        return withLook(current, def.look).copy(
            useDynamicColor = def.kind == ThemeKind.WALLPAPER,
            light = lightPalette,
            dark = darkPalette,
            themeId = def.id,
            baseColor = base,
            shade = shade
        )
    }
}
