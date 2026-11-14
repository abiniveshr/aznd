package com.ashrdev.aznd.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Ready-made looks for the whole app. Most come in two versions of the same colours: a clean one, and a textured
 * one whose surfaces carry a pattern (film grain, carbon fibre, topographic lines, mist, brushed metal ...).
 *
 * Restraint is the rule: large surfaces are solid or fall off by a few lightness steps, patterns are felt more than
 * seen, and the one soft reflection (a metal button) is the only place a gradient is allowed to shine. Outlines are
 * tinted with the preset's hue and strong enough to see against the background (3:1), so they are drawn thin.
 *
 * The high contrast presets are pitch black with every accent a shade of one colour; they keep that colour as their
 * own setting ([ThemeSettings.contrast]), which the Appearance page lets the person change (see [HighContrast]).
 *
 * A preset sets every colour, fill and border width, in both the light and the dark palette. It leaves the corner
 * sizes alone (see [ThemePresets.applyTo]) and turns wallpaper colours off.
 */
data class ThemePreset(
    val id: String,
    val name: String,
    /** True for the textured version. */
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

    private fun settings(light: Palette, dark: Palette, borders: Float = 0.6f) = ThemeSettings(
        mode = ThemeMode.DARK,
        useDynamicColor = false,
        cardBorder = borders,
        topBarBorder = borders,
        bottomBarBorder = borders,
        fieldBorder = 1.5f,
        outlinedButtonBorder = 1.5f,
        filledButtonBorder = 0f,
        light = light,
        dark = dark
    )

    // ---- Building blocks -------------------------------------------------------------------

    /** A gradient through [stops] given as (position, colour) pairs. */
    private fun stops(kind: GradientKind, angle: Int, vararg stops: Pair<Float, Long>) =
        GradientSpec(kind = kind, angle = angle, stops = stops.map { GradientStop(it.first, c(it.second)) })

    /**
     * Satin metal for a button: [body] with one broad, soft [shine] above the middle and [deep] at the bottom, as a
     * gently curved surface catches the light above it. Meant for small surfaces; large ones stay nearly flat.
     */
    private fun satin(shine: Long, body: Long, deep: Long, angle: Int = 90): GradientSpec = stops(
        GradientKind.LINEAR, angle,
        0f to body, 0.38f to shine, 1f to deep
    )

    /** A quiet falloff for a large surface: [lit] at one edge to [base] at the other, nothing in between. */
    private fun falloff(lit: Long, base: Long, angle: Int = 90): GradientSpec =
        stops(GradientKind.LINEAR, angle, 0f to lit, 1f to base)

    /** A glow: brightest at its centre ([core]), through [mid] to [rim]. */
    private fun glow(core: Long, mid: Long, rim: Long, centerY: Float = 0.35f, radius: Float = 0.9f) =
        GradientSpec.radial(c(core), c(mid), c(rim), centerX = 0.5f, centerY = centerY, radius = radius)

    /** A soft screen light: [lit] from just above the top of the screen, falling off to [base]. */
    private fun screenLight(lit: Long, base: Long) =
        GradientSpec.radial(c(lit), c(base), c(base), centerX = 0.5f, centerY = -0.05f, radius = 1.15f)

    private fun pat(kind: PatternKind, intensity: Float, tint: Long = 0xFFFFFFFF, scale: Float = kind.defaultScale, seed: Int = 1) =
        PatternSpec(kind, c(tint), intensity, scale, seed)

    private fun fill(g: GradientSpec, vararg p: PatternSpec) = FillStyle(FillBase.GRADIENT, g, p.toList())
    private fun solid(vararg p: PatternSpec) = FillStyle(FillBase.SOLID, patterns = p.toList())

    private fun Palette.with(vararg f: Pair<ThemeColor, FillStyle>) = copy(fills = f.toMap())

    // ---- Red / Black: crimson on carbon ---------------------------------------------------------

    private val redDark = palette(
        background = 0xFF0B0A0B, card = 0xFF171416, topBar = 0xFF110F10, bottomBar = 0xFF131112,
        text = 0xFFF3ECEC, button = 0xFFE0242F, highlight = 0xFF3B1216, outline = 0xFFA6484F
    )
    private val redLight = palette(
        background = 0xFFFAF7F7, card = 0xFFFFFFFF, topBar = 0xFFFFFFFF, bottomBar = 0xFFFFFFFF,
        text = 0xFF1A1012, button = 0xFFC8162B, highlight = 0xFFFADCDF, outline = 0xFFA37C81
    )
    private val redButton = falloff(0xFFEC3A44, 0xFFCE1C28)

    private val redDarkFills = redDark.with(
        ThemeColor.BUTTON to fill(redButton)
    )
    private val redLightFills = redLight.with(
        ThemeColor.BUTTON to fill(redButton)
    )
    private val redDarkTextured = redDark.with(
        ThemeColor.BACKGROUND to fill(screenLight(0xFF200A0D, 0xFF0B0A0B), pat(PatternKind.NOISE, 0.07f)),
        ThemeColor.CARD to solid(pat(PatternKind.CARBON, 0.04f), pat(PatternKind.NOISE, 0.03f)),
        ThemeColor.TOP_BAR to solid(pat(PatternKind.CARBON, 0.035f)),
        ThemeColor.BOTTOM_BAR to solid(pat(PatternKind.CARBON, 0.035f)),
        ThemeColor.BUTTON to fill(redButton, pat(PatternKind.NOISE, 0.06f)),
        ThemeColor.HIGHLIGHT to fill(glow(0xFF48151A, 0xFF3B1216, 0xFF330F13), pat(PatternKind.NOISE, 0.05f))
    )
    private val redLightTextured = redLight.with(
        ThemeColor.BACKGROUND to solid(pat(PatternKind.NOISE, 0.05f)),
        ThemeColor.CARD to solid(pat(PatternKind.NOISE, 0.035f)),
        ThemeColor.BUTTON to fill(redButton, pat(PatternKind.NOISE, 0.06f)),
        ThemeColor.HIGHLIGHT to solid(pat(PatternKind.NOISE, 0.04f))
    )

    // ---- Blue / Black: midnight, with a faint map ----------------------------------------------

    private val blueDark = palette(
        background = 0xFF070A12, card = 0xFF101828, topBar = 0xFF0B111D, bottomBar = 0xFF0D1422,
        text = 0xFFE8EEFB, button = 0xFF4A86FF, highlight = 0xFF14284F, outline = 0xFF4E6899
    )
    private val blueLight = palette(
        background = 0xFFF5F8FD, card = 0xFFFFFFFF, topBar = 0xFFFFFFFF, bottomBar = 0xFFFFFFFF,
        text = 0xFF0C1322, button = 0xFF2563EB, highlight = 0xFFDCE7FD, outline = 0xFF6A86BD
    )
    private val blueButton = falloff(0xFF5E94FF, 0xFF3F78F0)
    private val blueLightButton = falloff(0xFF3B73F0, 0xFF1F58D2)

    private val blueDarkFills = blueDark.with(ThemeColor.BUTTON to fill(blueButton))
    private val blueLightFills = blueLight.with(ThemeColor.BUTTON to fill(blueLightButton))
    private val blueDarkTextured = blueDark.with(
        ThemeColor.BACKGROUND to fill(screenLight(0xFF0D1B3A, 0xFF070A12), pat(PatternKind.TOPO, 0.08f, 0xFF7FA6FF), pat(PatternKind.NOISE, 0.05f)),
        ThemeColor.CARD to solid(pat(PatternKind.NOISE, 0.045f)),
        ThemeColor.TOP_BAR to solid(pat(PatternKind.NOISE, 0.045f)),
        ThemeColor.BOTTOM_BAR to solid(pat(PatternKind.NOISE, 0.045f)),
        ThemeColor.BUTTON to fill(blueButton, pat(PatternKind.NOISE, 0.06f)),
        ThemeColor.HIGHLIGHT to fill(glow(0xFF1B3669, 0xFF14284F, 0xFF112347), pat(PatternKind.TOPO, 0.07f, 0xFF9FBEFF, seed = 3))
    )
    private val blueLightTextured = blueLight.with(
        ThemeColor.BACKGROUND to solid(pat(PatternKind.TOPO, 0.08f, 0xFF2563EB), pat(PatternKind.NOISE, 0.04f)),
        ThemeColor.CARD to solid(pat(PatternKind.NOISE, 0.035f)),
        ThemeColor.BUTTON to fill(blueLightButton, pat(PatternKind.NOISE, 0.05f)),
        ThemeColor.HIGHLIGHT to solid(pat(PatternKind.TOPO, 0.08f, 0xFF2563EB, seed = 3))
    )

    // ---- Green / Black: forest, in mist ---------------------------------------------------------

    private val greenDark = palette(
        background = 0xFF070B09, card = 0xFF101813, topBar = 0xFF0B110D, bottomBar = 0xFF0D1410,
        text = 0xFFE7F3EB, button = 0xFF34C47C, highlight = 0xFF15372A, outline = 0xFF3E7559
    )
    private val greenLight = palette(
        background = 0xFFF5F9F6, card = 0xFFFFFFFF, topBar = 0xFFFFFFFF, bottomBar = 0xFFFFFFFF,
        text = 0xFF0C1710, button = 0xFF168A4F, highlight = 0xFFD7EFE1, outline = 0xFF5B9273
    )
    private val greenButton = falloff(0xFF49CD87, 0xFF2CAE6C)
    private val greenLightButton = falloff(0xFF229B5D, 0xFF117A45)

    private val greenDarkFills = greenDark.with(ThemeColor.BUTTON to fill(greenButton))
    private val greenLightFills = greenLight.with(ThemeColor.BUTTON to fill(greenLightButton))
    private val greenDarkTextured = greenDark.with(
        ThemeColor.BACKGROUND to fill(screenLight(0xFF0E2C1F, 0xFF070B09), pat(PatternKind.CLOUDS, 0.065f, 0xFFBFEBD3), pat(PatternKind.NOISE, 0.05f)),
        ThemeColor.CARD to solid(pat(PatternKind.NOISE, 0.045f)),
        ThemeColor.TOP_BAR to solid(pat(PatternKind.NOISE, 0.045f)),
        ThemeColor.BOTTOM_BAR to solid(pat(PatternKind.NOISE, 0.045f)),
        ThemeColor.BUTTON to fill(greenButton, pat(PatternKind.NOISE, 0.06f)),
        ThemeColor.HIGHLIGHT to fill(glow(0xFF1B4331, 0xFF15372A, 0xFF12301F), pat(PatternKind.CELLS, 0.055f, 0xFFBFEBD3))
    )
    private val greenLightTextured = greenLight.with(
        ThemeColor.BACKGROUND to solid(pat(PatternKind.CLOUDS, 0.05f, 0xFF168A4F), pat(PatternKind.NOISE, 0.04f)),
        ThemeColor.CARD to solid(pat(PatternKind.NOISE, 0.035f)),
        ThemeColor.BUTTON to fill(greenLightButton, pat(PatternKind.NOISE, 0.05f)),
        ThemeColor.HIGHLIGHT to solid(pat(PatternKind.CELLS, 0.07f, 0xFF168A4F))
    )

    // ---- Titanium: cool grey metal, satin rather than chrome ------------------------------------
    // Surfaces are quiet: the background and cards fall off by a few lightness steps, with no bright band. The
    // one reflection is the button's, a broad satin highlight; its colour is the anodised accent (pale steel blue in
    // the dark palette, deep slate blue in the light one), so both palettes share one blue-grey cast.

    private val titaniumDark = palette(
        background = 0xFF0F1114, card = 0xFF1E2227, topBar = 0xFF171A1E, bottomBar = 0xFF191C21,
        text = 0xFFE9EDF2, button = 0xFFB4C4D8, highlight = 0xFF29323D, outline = 0xFF5D6A7A
    )
    private val titaniumLight = palette(
        background = 0xFFE9ECF0, card = 0xFFF7F8FA, topBar = 0xFFF2F4F6, bottomBar = 0xFFF2F4F6,
        text = 0xFF14181D, button = 0xFF3F5671, highlight = 0xFFDBE2EB, outline = 0xFF6E8098
    )
    private val titaniumDarkButton = satin(0xFFC6D4E6, 0xFFB4C4D8, 0xFF9EB0C7)
    private val titaniumLightButton = satin(0xFF566F8E, 0xFF4A6280, 0xFF384A62)

    private val titaniumDarkFills = titaniumDark.with(
        ThemeColor.BACKGROUND to fill(falloff(0xFF16191D, 0xFF0E1013, 100)),
        ThemeColor.CARD to fill(falloff(0xFF22272D, 0xFF1C2025)),
        ThemeColor.TOP_BAR to fill(falloff(0xFF1B1E23, 0xFF15181C)),
        ThemeColor.BOTTOM_BAR to fill(falloff(0xFF1B1E23, 0xFF171A1F)),
        ThemeColor.BUTTON to fill(titaniumDarkButton),
        ThemeColor.HIGHLIGHT to fill(falloff(0xFF2D3844, 0xFF262F39))
    )
    private val titaniumLightFills = titaniumLight.with(
        ThemeColor.BACKGROUND to fill(falloff(0xFFEEF1F4, 0xFFE5E8EC, 100)),
        ThemeColor.CARD to fill(falloff(0xFFFAFBFC, 0xFFF3F5F7)),
        ThemeColor.TOP_BAR to fill(falloff(0xFFF6F7F9, 0xFFEEF0F3)),
        ThemeColor.BOTTOM_BAR to fill(falloff(0xFFF6F7F9, 0xFFEEF0F3)),
        ThemeColor.BUTTON to fill(titaniumLightButton),
        ThemeColor.HIGHLIGHT to fill(falloff(0xFFDFE6EE, 0xFFD6DEE8))
    )

    /** Brushed, felt more than seen: one seed for the whole preset (each seed costs three bitmap tiles). */
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
    private val titaniumLightTextured = titaniumLightFills.brushedBy(card = 0.075f, bar = 0.075f, background = 0.065f, button = 0.08f)

    // ---- Man of Iron: oxblood and brass on gunmetal, a small arc of reactor light ---------------
    // Minimal: every surface a solid colour with at most a faint pattern; muted, not candy-bright. The one gradient
    // is the highlight's: a small cyan glow in a deep navy card, bright only near its centre.

    private val ironDark = palette(
        background = 0xFF0D0E10, card = 0xFF18191C, topBar = 0xFF6E1416, bottomBar = 0xFF141517,
        text = 0xFFE8E6E1, button = 0xFFB8954A, highlight = 0xFF0F2A3A, outline = 0xFF7A6435
    )
    private val ironLight = palette(
        background = 0xFFEEEEEC, card = 0xFFFAFAF8, topBar = 0xFF6E1416, bottomBar = 0xFFFAFAF8,
        text = 0xFF17181A, button = 0xFF8C6E32, highlight = 0xFF0F2A3A, outline = 0xFF977C47
    )

    /**
     * The reactor's light, a small arc in the lower right corner of the card, away from where the label sits: deep
     * navy everywhere else, and even at its core dark enough (about #154058) for white text to stay readable.
     */
    private val reactor = GradientSpec(
        kind = GradientKind.RADIAL,
        centerX = 0.9f, centerY = 0.95f, radius = 0.55f,
        stops = listOf(
            GradientStop(0f, c(0xFF154058)),
            GradientStop(0.45f, c(0xFF11334A)),
            GradientStop(1f, c(0xFF0F2A3A))
        )
    )

    private val ironDarkFills = ironDark.with(
        ThemeColor.BACKGROUND to solid(pat(PatternKind.HEX, 0.04f), pat(PatternKind.NOISE, 0.05f)),
        ThemeColor.CARD to solid(pat(PatternKind.BRUSHED, 0.06f, seed = 6)),
        ThemeColor.TOP_BAR to solid(pat(PatternKind.BRUSHED, 0.08f, seed = 7)),
        ThemeColor.BOTTOM_BAR to solid(pat(PatternKind.NOISE, 0.05f)),
        ThemeColor.BUTTON to solid(pat(PatternKind.BRUSHED, 0.08f, seed = 9)),
        ThemeColor.HIGHLIGHT to fill(reactor, pat(PatternKind.HEX, 0.04f))
    )
    private val ironLightFills = ironLight.with(
        ThemeColor.BACKGROUND to solid(pat(PatternKind.HEX, 0.04f, 0xFF17181A), pat(PatternKind.NOISE, 0.03f)),
        ThemeColor.CARD to solid(pat(PatternKind.NOISE, 0.025f)),
        ThemeColor.TOP_BAR to solid(pat(PatternKind.BRUSHED, 0.08f, seed = 7)),
        ThemeColor.BUTTON to solid(pat(PatternKind.BRUSHED, 0.08f, seed = 9)),
        ThemeColor.HIGHLIGHT to fill(reactor, pat(PatternKind.HEX, 0.04f))
    )

    // ---- High contrast: pitch black, one colour (see HighContrast) ------------------------------

    private fun contrast(color: Long) = HighContrast.apply(ThemeSettings(), ContrastBase(Color(color), ContrastShade.ALPHA))

    val all: List<ThemePreset> = listOf(
        ThemePreset("red_black", "Red / Black", false, settings(redLightFills, redDarkFills)),
        ThemePreset("red_black_noise", "Red / Black carbon", true, settings(redLightTextured, redDarkTextured)),
        ThemePreset("blue_black", "Blue / Black", false, settings(blueLightFills, blueDarkFills)),
        ThemePreset("blue_black_noise", "Blue / Black map", true, settings(blueLightTextured, blueDarkTextured)),
        ThemePreset("green_black", "Green / Black", false, settings(greenLightFills, greenDarkFills)),
        ThemePreset("green_black_noise", "Green / Black mist", true, settings(greenLightTextured, greenDarkTextured)),
        ThemePreset("titanium", "Titanium", false, settings(titaniumLightFills, titaniumDarkFills)),
        ThemePreset("titanium_brushed", "Titanium brushed", true, settings(titaniumLightTextured, titaniumDarkTextured)),
        ThemePreset("man_of_iron", "Man of Iron", true, settings(ironLightFills, ironDarkFills)),
        ThemePreset("contrast_cyan", "High contrast cyan", false, contrast(0xFF2EC5FF)),
        ThemePreset("contrast_red", "High contrast red", false, contrast(0xFFFF3B3B)),
        ThemePreset("contrast_green", "High contrast green", false, contrast(0xFF3DDC84)),
        ThemePreset("contrast_amber", "High contrast amber", false, contrast(0xFFFFB020)),
        ThemePreset("contrast_white", "High contrast white", false, contrast(0xFFFFFFFF))
    )
}
