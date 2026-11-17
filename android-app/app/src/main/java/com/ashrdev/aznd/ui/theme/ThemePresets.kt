package com.ashrdev.aznd.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

data class ThemePreset(
    val id: String,
    val name: String,
    val patterned: Boolean,
    val settings: ThemeSettings
)

object ThemePresets {

    private fun c(v: Long): Int = v.toInt()

    private fun palette(
        background: Long, card: Long, topBar: Long, bottomBar: Long,
        text: Long, button: Long, highlight: Long, outline: Long
    ) = Palette(
        background = Color(background),
        card = Color(card),
        topBar = Color(topBar),
        text = Color(text),
        button = Color(button),
        highlight = Color(highlight),
        outline = Color(outline),
        bottomBar = Color(bottomBar)
    )

    private fun stops(kind: GradientKind, angle: Int, vararg stops: Pair<Float, Long>) =
        GradientSpec(kind = kind, angle = angle, stops = stops.map { GradientStop(it.first, c(it.second)) })

    private fun satin(shine: Long, body: Long, deep: Long, angle: Int = 90): GradientSpec = stops(
        GradientKind.LINEAR, angle,
        0f to body, 0.38f to shine, 1f to deep
    )

    private fun falloff(lit: Long, base: Long, angle: Int = 90): GradientSpec =
        stops(GradientKind.LINEAR, angle, 0f to lit, 1f to base)

    private fun glow(core: Long, mid: Long, rim: Long, centerY: Float = 0.35f, radius: Float = 0.9f) =
        GradientSpec.radial(c(core), c(mid), c(rim), centerX = 0.5f, centerY = centerY, radius = radius)

    private fun screenLight(lit: Long, base: Long) =
        GradientSpec.radial(c(lit), c(base), c(base), centerX = 0.5f, centerY = -0.05f, radius = 1.15f)

    private fun pat(kind: PatternKind, intensity: Float, tint: Long = 0xFFFFFFFF, scale: Float = kind.defaultScale, seed: Int = 1) =
        PatternSpec(kind, c(tint), intensity, scale, seed)

    private fun fill(g: GradientSpec, vararg p: PatternSpec) = FillStyle(FillBase.GRADIENT, g, p.toList())
    private fun solid(vararg p: PatternSpec) = FillStyle(FillBase.SOLID, patterns = p.toList())

    private fun Palette.with(vararg f: Pair<ThemeColor, FillStyle>) = copy(fills = f.toMap())

    private val redDark = palette(
        background = 0xFF0B0A0B, card = 0xFF171416, topBar = 0xFF110F10, bottomBar = 0xFF131112,
        text = 0xFFF3ECEC, button = 0xFFE0242F, highlight = 0xFF3B1216, outline = 0xFFA6484F
    )
    private val redButton = falloff(0xFFEC3A44, 0xFFCE1C28)

    private val redDarkTextured = redDark.with(
        ThemeColor.BACKGROUND to fill(screenLight(0xFF200A0D, 0xFF0B0A0B), pat(PatternKind.NOISE, 0.07f)),
        ThemeColor.CARD to solid(pat(PatternKind.CARBON, 0.04f), pat(PatternKind.NOISE, 0.03f)),
        ThemeColor.TOP_BAR to solid(pat(PatternKind.CARBON, 0.035f)),
        ThemeColor.BOTTOM_BAR to solid(pat(PatternKind.CARBON, 0.035f)),
        ThemeColor.BUTTON to fill(redButton, pat(PatternKind.NOISE, 0.06f)),
        ThemeColor.HIGHLIGHT to fill(glow(0xFF48151A, 0xFF3B1216, 0xFF330F13), pat(PatternKind.NOISE, 0.05f))
    )

    private val blueDark = palette(
        background = 0xFF070A12, card = 0xFF101828, topBar = 0xFF0B111D, bottomBar = 0xFF0D1422,
        text = 0xFFE8EEFB, button = 0xFF4A86FF, highlight = 0xFF14284F, outline = 0xFF4E6899
    )
    private val blueButton = falloff(0xFF5E94FF, 0xFF3F78F0)
    private val blueDarkTextured = blueDark.with(
        ThemeColor.BACKGROUND to fill(screenLight(0xFF0D1B3A, 0xFF070A12), pat(PatternKind.TOPO, 0.08f, 0xFF7FA6FF), pat(PatternKind.NOISE, 0.05f)),
        ThemeColor.CARD to solid(pat(PatternKind.NOISE, 0.045f)),
        ThemeColor.TOP_BAR to solid(pat(PatternKind.NOISE, 0.045f)),
        ThemeColor.BOTTOM_BAR to solid(pat(PatternKind.NOISE, 0.045f)),
        ThemeColor.BUTTON to fill(blueButton, pat(PatternKind.NOISE, 0.06f)),
        ThemeColor.HIGHLIGHT to fill(glow(0xFF1B3669, 0xFF14284F, 0xFF112347), pat(PatternKind.TOPO, 0.07f, 0xFF9FBEFF, seed = 3))
    )

    private val greenDark = palette(
        background = 0xFF070B09, card = 0xFF101813, topBar = 0xFF0B110D, bottomBar = 0xFF0D1410,
        text = 0xFFE7F3EB, button = 0xFF34C47C, highlight = 0xFF15372A, outline = 0xFF3E7559
    )
    private val greenButton = falloff(0xFF49CD87, 0xFF2CAE6C)
    private val greenDarkTextured = greenDark.with(
        ThemeColor.BACKGROUND to fill(screenLight(0xFF0E2C1F, 0xFF070B09), pat(PatternKind.CLOUDS, 0.065f, 0xFFBFEBD3), pat(PatternKind.NOISE, 0.05f)),
        ThemeColor.CARD to solid(pat(PatternKind.NOISE, 0.045f)),
        ThemeColor.TOP_BAR to solid(pat(PatternKind.NOISE, 0.045f)),
        ThemeColor.BOTTOM_BAR to solid(pat(PatternKind.NOISE, 0.045f)),
        ThemeColor.BUTTON to fill(greenButton, pat(PatternKind.NOISE, 0.06f)),
        ThemeColor.HIGHLIGHT to fill(glow(0xFF1B4331, 0xFF15372A, 0xFF12301F), pat(PatternKind.CELLS, 0.055f, 0xFFBFEBD3))
    )

    private val titaniumDark = palette(
        background = 0xFF0F1114, card = 0xFF1E2227, topBar = 0xFF171A1E, bottomBar = 0xFF191C21,
        text = 0xFFE9EDF2, button = 0xFFB4C4D8, highlight = 0xFF29323D, outline = 0xFF5D6A7A
    )
    private val titaniumDarkButton = satin(0xFFC6D4E6, 0xFFB4C4D8, 0xFF9EB0C7)
    private val titaniumDarkFills = titaniumDark.with(
        ThemeColor.BACKGROUND to fill(falloff(0xFF16191D, 0xFF0E1013, 100)),
        ThemeColor.CARD to fill(falloff(0xFF22272D, 0xFF1C2025)),
        ThemeColor.TOP_BAR to fill(falloff(0xFF1B1E23, 0xFF15181C)),
        ThemeColor.BOTTOM_BAR to fill(falloff(0xFF1B1E23, 0xFF171A1F)),
        ThemeColor.BUTTON to fill(titaniumDarkButton),
        ThemeColor.HIGHLIGHT to fill(falloff(0xFF2D3844, 0xFF262F39))
    )
    private fun Palette.brushedBy(card: Float, bar: Float, background: Float, button: Float): Palette = copy(
        fills = fills.mapValues { (k, f) ->
            val amount = when (k) {
                ThemeColor.BACKGROUND -> background
                ThemeColor.BUTTON -> button
                ThemeColor.TOP_BAR, ThemeColor.BOTTOM_BAR -> bar
                else -> card
            }
            f.copy(patterns = f.patterns + pat(PatternKind.BRUSHED, amount, seed = 3))
        }
    )
    private val titaniumDarkTextured = titaniumDarkFills.brushedBy(card = 0.065f, bar = 0.065f, background = 0.06f, button = 0.08f)

    private val template = ThemeLook(
        cardBorder = 0.6f, topBarBorder = 0.6f, bottomBarBorder = 0.6f,
        fieldBorder = 1.5f, outlinedButtonBorder = 1.5f, filledButtonBorder = 0f,
        cardBorderLine = false, topBarBorderLine = false, bottomBarBorderLine = false,
        fieldBorderLine = false, outlinedButtonBorderLine = false, filledButtonBorderLine = false,
        borderTaper = TaperLine()
    )

    private val plain = template.copy(
        cardBorder = 0f, topBarBorder = 0f, bottomBarBorder = 0f, fieldBorder = 1f, outlinedButtonBorder = 1f
    )

    private val mono = template.copy(
        cardBorder = 1.5f, topBarBorder = 1.5f, bottomBarBorder = 1.5f
    )

    private val MONO_BASE = 0xFF2EC5FF.toInt()

    private fun built(id: String, name: String, design: Palette) = ThemeDef(
        id = id,
        name = name,
        builtIn = true,
        kind = ThemeKind.TEMPLATE,
        design = design,
        variable = true,
        refBase = design.button.toArgb(),
        tintGreys = false,
        excluded = emptySet(),
        look = template
    )

    val builtIns: List<ThemeDef> = listOf(
        ThemeDef(
            id = "wallpaper",
            name = "System Wallpaper",
            builtIn = true,
            kind = ThemeKind.WALLPAPER,
            design = DefaultDarkPalette,
            variable = false,
            refBase = DefaultDarkPalette.button.toArgb(),
            tintGreys = false,
            excluded = emptySet(),
            look = plain
        ),
        built("carbon", "Carbon", redDarkTextured),
        built("map", "Map", blueDarkTextured),
        built("mist", "Mist", greenDarkTextured),
        built("metal", "Metal", titaniumDarkTextured),
        ThemeDef(
            id = "mono",
            name = "Mono",
            builtIn = true,
            kind = ThemeKind.MONO,
            design = HighContrast.palette(MONO_BASE, ContrastShade.ALPHA),
            variable = true,
            refBase = MONO_BASE,
            tintGreys = false,
            excluded = emptySet(),
            look = mono
        )
    )

    fun byId(id: String): ThemeDef? = builtIns.firstOrNull { it.id == id }

    val all: List<ThemePreset> by lazy {
        builtIns.filter { it.kind != ThemeKind.WALLPAPER }.map {
            ThemePreset(
                it.id,
                it.name,
                it.kind == ThemeKind.TEMPLATE,
                ThemeDerive.apply(ThemeSettings(mode = ThemeMode.DARK), it, it.refBase, ContrastShade.ALPHA)
            )
        }
    }
}
