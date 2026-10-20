package com.ashrdev.aznd.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Ready-made looks for the whole app. Each one has a flat version and a "pattern" version of the same
 * colours: film-grain noise over the backgrounds, cards, bars and highlights, a soft glow behind the
 * screen, and a sheen on the buttons (brushed metal for Titanium).
 *
 * A preset sets every colour, fill and border width, in both the light and the dark palette. It leaves
 * the corner sizes alone (see [ThemePresets.applyTo]) and turns wallpaper colours off.
 */
data class ThemePreset(
    val id: String,
    val name: String,
    /** True for the version with noise / brushed patterns. */
    val patterned: Boolean,
    val settings: ThemeSettings
)

object ThemePresets {

    /** Puts [preset] on top of the person's current settings, keeping their corner sizes. */
    fun applyTo(current: ThemeSettings, preset: ThemePreset): ThemeSettings = preset.settings.copy(
        useDynamicColor = false,
        cardCorner = current.cardCorner,
        buttonCorner = current.buttonCorner
    )

    private fun c(v: Long): Int = v.toInt()

    private fun palette(
        background: Long, card: Long, topBar: Long, bottomBar: Long,
        text: Long, button: Long, highlight: Long, outline: Long,
        fills: Map<ThemeColor, FillStyle> = emptyMap()
    ) = Palette(
        background = Color(background),
        card = Color(card),
        topBar = Color(topBar),
        text = Color(text),
        button = Color(button),
        highlight = Color(highlight),
        outline = Color(outline),
        bottomBar = Color(bottomBar),
        fills = fills
    )

    private fun noise(strength: Float) = FillStyle.patterned(PatternKind.NOISE, strength)
    private fun brushed(strength: Float) = FillStyle.patterned(PatternKind.BRUSHED, strength)

    /** A pattern version's fills: [glow] behind the screen, [buttonSheen] on buttons, grain over the rest. */
    private fun grainFills(
        glow: GradientSpec,
        buttonSheen: GradientSpec,
        grain: Float,
        screenGrain: Float,
        buttonGrain: Float
    ): Map<ThemeColor, FillStyle> = mapOf(
        ThemeColor.BACKGROUND to FillStyle.ofGradient(glow, PatternSpec.of(PatternKind.NOISE, screenGrain)),
        ThemeColor.CARD to noise(grain),
        ThemeColor.TOP_BAR to noise(grain + 0.02f),
        ThemeColor.BOTTOM_BAR to noise(grain + 0.02f),
        ThemeColor.BUTTON to FillStyle.ofGradient(buttonSheen, PatternSpec.of(PatternKind.NOISE, buttonGrain)),
        ThemeColor.HIGHLIGHT to noise(grain + 0.02f)
    )

    /** Light-mode fills: the same ideas, quieter, and without the dark glow. */
    private fun lightGrainFills(
        buttonSheen: GradientSpec,
        brushedMetal: Boolean = false
    ): Map<ThemeColor, FillStyle> {
        fun grain(s: Float) = if (brushedMetal) brushed(s) else noise(s)
        return mapOf(
            ThemeColor.BACKGROUND to grain(0.06f),
            ThemeColor.CARD to grain(0.06f),
            ThemeColor.TOP_BAR to grain(0.06f),
            ThemeColor.BOTTOM_BAR to grain(0.06f),
            ThemeColor.BUTTON to FillStyle.ofGradient(buttonSheen, PatternSpec.of(if (brushedMetal) PatternKind.BRUSHED else PatternKind.NOISE, 0.08f)),
            ThemeColor.HIGHLIGHT to grain(0.06f)
        )
    }

    private fun settings(light: Palette, dark: Palette) = ThemeSettings(
        mode = ThemeMode.DARK,
        useDynamicColor = false,
        cardBorder = 1f,
        topBarBorder = 1f,
        bottomBarBorder = 1f,
        fieldBorder = 1.5f,
        outlinedButtonBorder = 1.5f,
        filledButtonBorder = 0f,
        light = light,
        dark = dark
    )

    // ---- Red / Black ---------------------------------------------------------------------

    private val redDark = palette(
        background = 0xFF0A0A0B, card = 0xFF1B1315, topBar = 0xFF120C0D, bottomBar = 0xFF150E10,
        text = 0xFFF4EBEC, button = 0xFFE11D2E, highlight = 0xFF5E1219, outline = 0xFF7C2630
    )
    private val redLight = palette(
        background = 0xFFFFF8F8, card = 0xFFF6E3E5, topBar = 0xFFFBEEEF, bottomBar = 0xFFF6E3E5,
        text = 0xFF1B0C0E, button = 0xFFC8102E, highlight = 0xFFF6C5CB, outline = 0xFFB25560
    )
    private val redGlow = GradientSpec.radial(c(0xFF3A0D13), c(0xFF160709), c(0xFF0A0A0B), centerY = 0.3f, radius = 1.1f)
    private val redSheen = GradientSpec.of(GradientKind.LINEAR, 90, c(0xFFF0303F), c(0xFFE11D2E), c(0xFFA80F1E))

    // ---- Blue / Black --------------------------------------------------------------------

    private val blueDark = palette(
        background = 0xFF05070D, card = 0xFF0F1522, topBar = 0xFF0A0F1A, bottomBar = 0xFF0C121E,
        text = 0xFFE9F0FF, button = 0xFF2F6BFF, highlight = 0xFF163A82, outline = 0xFF2B4C94
    )
    private val blueLight = palette(
        background = 0xFFF7F9FF, card = 0xFFE3EAF8, topBar = 0xFFEEF2FB, bottomBar = 0xFFE3EAF8,
        text = 0xFF0B1020, button = 0xFF1F5BFF, highlight = 0xFFC6D6FF, outline = 0xFF5F7FC0
    )
    private val blueGlow = GradientSpec.radial(c(0xFF0E2A66), c(0xFF07122B), c(0xFF05070D), centerY = 0.3f, radius = 1.1f)
    private val blueSheen = GradientSpec.of(GradientKind.LINEAR, 90, c(0xFF5A8CFF), c(0xFF2F6BFF), c(0xFF1B49C4))

    // ---- Green / Black -------------------------------------------------------------------

    private val greenDark = palette(
        background = 0xFF040A06, card = 0xFF0D1A12, topBar = 0xFF08120C, bottomBar = 0xFF0A150E,
        text = 0xFFE7F7EE, button = 0xFF1FBF6B, highlight = 0xFF0F4D2C, outline = 0xFF1F6B43
    )
    private val greenLight = palette(
        background = 0xFFF6FCF8, card = 0xFFE1F2E8, topBar = 0xFFECF7F0, bottomBar = 0xFFE1F2E8,
        text = 0xFF08160D, button = 0xFF12904F, highlight = 0xFFBFE8D0, outline = 0xFF5A9C78
    )
    private val greenGlow = GradientSpec.radial(c(0xFF0C4A2A), c(0xFF071F13), c(0xFF040A06), centerY = 0.3f, radius = 1.1f)
    private val greenSheen = GradientSpec.of(GradientKind.LINEAR, 90, c(0xFF3FD98A), c(0xFF1FBF6B), c(0xFF138C4D))

    // ---- Titanium ------------------------------------------------------------------------

    private val titaniumDark = palette(
        background = 0xFF111214, card = 0xFF24272B, topBar = 0xFF1A1C1F, bottomBar = 0xFF1D2023,
        text = 0xFFE8EAEE, button = 0xFFB5BDC9, highlight = 0xFF3A414A, outline = 0xFF6D7580
    )
    private val titaniumLight = palette(
        background = 0xFFF3F4F6, card = 0xFFDDE0E5, topBar = 0xFFE9EBEE, bottomBar = 0xFFDDE0E5,
        text = 0xFF15171A, button = 0xFF5A6572, highlight = 0xFFC5CBD3, outline = 0xFF8A929D
    )
    private fun titaniumPlate(from: Long, to: Long) =
        FillStyle.ofGradient(GradientSpec.of(GradientKind.LINEAR, 160, c(from), c(to)), PatternSpec.of(PatternKind.BRUSHED, 0.15f))
    private val titaniumFills: Map<ThemeColor, FillStyle> = mapOf(
        ThemeColor.BACKGROUND to FillStyle.ofGradient(
            GradientSpec.of(GradientKind.LINEAR, 160, c(0xFF2E3238), c(0xFF1A1C20), c(0xFF0F1012)),
            PatternSpec.of(PatternKind.BRUSHED, 0.12f)
        ),
        ThemeColor.CARD to titaniumPlate(0xFF33373D, 0xFF202327),
        ThemeColor.TOP_BAR to titaniumPlate(0xFF3A3E44, 0xFF1E2024),
        ThemeColor.BOTTOM_BAR to titaniumPlate(0xFF3A3E44, 0xFF1E2024),
        ThemeColor.BUTTON to FillStyle.ofGradient(
            GradientSpec.of(GradientKind.LINEAR, 90, c(0xFFE8ECF2), c(0xFFB5BDC9), c(0xFF8E97A3)),
            PatternSpec.of(PatternKind.BRUSHED, 0.12f)
        ),
        ThemeColor.HIGHLIGHT to titaniumPlate(0xFF4A525C, 0xFF343A42)
    )
    private val titaniumSheen = GradientSpec.of(GradientKind.LINEAR, 90, c(0xFF7C8794), c(0xFF5A6572), c(0xFF444E5A))

    // ---- Red / Gold / Ice ----------------------------------------------------------------

    private val royalDark = palette(
        background = 0xFF24060A, card = 0xFF3A0A12, topBar = 0xFF2F080F, bottomBar = 0xFF33090F,
        text = 0xFFFFF1DA, button = 0xFFF2B705, highlight = 0xFF9CD8FF, outline = 0xFFD4A017
    )
    private val royalLight = palette(
        background = 0xFFFFF6F4, card = 0xFFFBE3E1, topBar = 0xFFFFEDEA, bottomBar = 0xFFFBE3E1,
        text = 0xFF2A0A0E, button = 0xFFD9A400, highlight = 0xFFBFE4FF, outline = 0xFFC08A00
    )
    private val royalGlow = GradientSpec.radial(c(0xFF7A1220), c(0xFF3A0A12), c(0xFF1C0408), centerY = 0.3f, radius = 1.1f)
    private val royalSheen = GradientSpec.of(GradientKind.LINEAR, 90, c(0xFFFFD54A), c(0xFFF2B705), c(0xFFC98F00))
    private val royalLightSheen = GradientSpec.of(GradientKind.LINEAR, 90, c(0xFFFFCB3D), c(0xFFD9A400), c(0xFFB88700))

    val all: List<ThemePreset> = listOf(
        ThemePreset("red_black", "Red / Black", false, settings(redLight, redDark)),
        ThemePreset(
            "red_black_noise", "Red / Black noise", true,
            settings(
                redLight.copy(fills = lightGrainFills(redSheen)),
                redDark.copy(fills = grainFills(redGlow, redSheen, grain = 0.10f, screenGrain = 0.14f, buttonGrain = 0.10f))
            )
        ),
        ThemePreset("blue_black", "Blue / Black", false, settings(blueLight, blueDark)),
        ThemePreset(
            "blue_black_noise", "Blue / Black noise", true,
            settings(
                blueLight.copy(fills = lightGrainFills(blueSheen)),
                blueDark.copy(fills = grainFills(blueGlow, blueSheen, grain = 0.10f, screenGrain = 0.14f, buttonGrain = 0.10f))
            )
        ),
        ThemePreset("green_black", "Green / Black", false, settings(greenLight, greenDark)),
        ThemePreset(
            "green_black_noise", "Green / Black noise", true,
            settings(
                greenLight.copy(fills = lightGrainFills(greenSheen)),
                greenDark.copy(fills = grainFills(greenGlow, greenSheen, grain = 0.10f, screenGrain = 0.14f, buttonGrain = 0.10f))
            )
        ),
        ThemePreset("titanium", "Titanium", false, settings(titaniumLight, titaniumDark)),
        ThemePreset(
            "titanium_brushed", "Titanium brushed", true,
            settings(
                titaniumLight.copy(fills = lightGrainFills(titaniumSheen, brushedMetal = true)),
                titaniumDark.copy(fills = titaniumFills)
            )
        ),
        ThemePreset("red_gold_ice", "Red / Gold / Ice", false, settings(royalLight, royalDark)),
        ThemePreset(
            "red_gold_ice_noise", "Red / Gold / Ice noise", true,
            settings(
                royalLight.copy(fills = lightGrainFills(royalLightSheen)),
                royalDark.copy(fills = grainFills(royalGlow, royalSheen, grain = 0.10f, screenGrain = 0.14f, buttonGrain = 0.08f))
            )
        )
    )
}
