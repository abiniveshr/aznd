package com.ashrdev.aznd.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Ready-made looks for the whole app. Most come in two versions of the same colours: a clean one, and a textured
 * one whose surfaces carry a pattern (film grain, carbon fibre, topographic lines, mist, brushed metal ...).
 *
 * Metals are drawn the way metal looks: a gradient with a bright reflection band across it (not a two-colour fade),
 * brushed along its length in the textured version. Light is a glow: a radial gradient brightest in its middle.
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

    private fun settings(light: Palette, dark: Palette, borders: Float = 1f) = ThemeSettings(
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
     * Polished metal: [deep] at the edges, [body] in between and a bright [shine] band a little above the middle, as a
     * curved metal surface reflects the light above it. [angle]: the direction across the band.
     */
    private fun metal(shine: Long, body: Long, deep: Long, angle: Int = 90): GradientSpec = stops(
        GradientKind.LINEAR, angle,
        0f to body, 0.30f to shine, 0.46f to body, 0.78f to deep, 1f to body
    )

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
        text = 0xFFF3ECEC, button = 0xFFE0242F, highlight = 0xFF3B1216, outline = 0xFF3A2C2F
    )
    private val redLight = palette(
        background = 0xFFFAF7F7, card = 0xFFFFFFFF, topBar = 0xFFFFFFFF, bottomBar = 0xFFFFFFFF,
        text = 0xFF1A1012, button = 0xFFC8162B, highlight = 0xFFFADCDF, outline = 0xFFE2D3D5
    )
    private val redButton = stops(GradientKind.LINEAR, 90, 0f to 0xFFF0434C, 0.5f to 0xFFE0242F, 1f to 0xFFB4141F)

    private val redDarkFills = redDark.with(
        ThemeColor.BUTTON to fill(redButton)
    )
    private val redLightFills = redLight.with(
        ThemeColor.BUTTON to fill(redButton)
    )
    private val redDarkTextured = redDark.with(
        ThemeColor.BACKGROUND to fill(screenLight(0xFF2A0B0F, 0xFF0B0A0B), pat(PatternKind.NOISE, 0.10f)),
        ThemeColor.CARD to solid(pat(PatternKind.CARBON, 0.12f), pat(PatternKind.NOISE, 0.05f)),
        ThemeColor.TOP_BAR to solid(pat(PatternKind.CARBON, 0.10f)),
        ThemeColor.BOTTOM_BAR to solid(pat(PatternKind.CARBON, 0.10f)),
        ThemeColor.BUTTON to fill(redButton, pat(PatternKind.NOISE, 0.08f)),
        ThemeColor.HIGHLIGHT to fill(glow(0xFF5A161C, 0xFF3B1216, 0xFF2A0D10), pat(PatternKind.NOISE, 0.08f))
    )
    private val redLightTextured = redLight.with(
        ThemeColor.BACKGROUND to solid(pat(PatternKind.NOISE, 0.05f)),
        ThemeColor.CARD to solid(pat(PatternKind.NOISE, 0.035f)),
        ThemeColor.BUTTON to fill(redButton, pat(PatternKind.NOISE, 0.07f)),
        ThemeColor.HIGHLIGHT to solid(pat(PatternKind.NOISE, 0.04f))
    )

    // ---- Blue / Black: midnight, with a faint map ----------------------------------------------

    private val blueDark = palette(
        background = 0xFF070A12, card = 0xFF101828, topBar = 0xFF0B111D, bottomBar = 0xFF0D1422,
        text = 0xFFE8EEFB, button = 0xFF4A86FF, highlight = 0xFF14284F, outline = 0xFF22324F
    )
    private val blueLight = palette(
        background = 0xFFF5F8FD, card = 0xFFFFFFFF, topBar = 0xFFFFFFFF, bottomBar = 0xFFFFFFFF,
        text = 0xFF0C1322, button = 0xFF2563EB, highlight = 0xFFDCE7FD, outline = 0xFFD3DCEB
    )
    private val blueButton = stops(GradientKind.LINEAR, 90, 0f to 0xFF6B9DFF, 0.5f to 0xFF4A86FF, 1f to 0xFF2F66E0)
    private val blueLightButton = stops(GradientKind.LINEAR, 90, 0f to 0xFF4C80F2, 0.5f to 0xFF2563EB, 1f to 0xFF1B4FC4)

    private val blueDarkFills = blueDark.with(ThemeColor.BUTTON to fill(blueButton))
    private val blueLightFills = blueLight.with(ThemeColor.BUTTON to fill(blueLightButton))
    private val blueDarkTextured = blueDark.with(
        ThemeColor.BACKGROUND to fill(screenLight(0xFF0F2045, 0xFF070A12), pat(PatternKind.TOPO, 0.10f, 0xFF7FA6FF), pat(PatternKind.NOISE, 0.07f)),
        ThemeColor.CARD to solid(pat(PatternKind.NOISE, 0.06f)),
        ThemeColor.TOP_BAR to solid(pat(PatternKind.NOISE, 0.06f)),
        ThemeColor.BOTTOM_BAR to solid(pat(PatternKind.NOISE, 0.06f)),
        ThemeColor.BUTTON to fill(blueButton, pat(PatternKind.NOISE, 0.07f)),
        ThemeColor.HIGHLIGHT to fill(glow(0xFF1E3D78, 0xFF14284F, 0xFF0F1E3C), pat(PatternKind.TOPO, 0.10f, 0xFF9FBEFF, seed = 3))
    )
    private val blueLightTextured = blueLight.with(
        ThemeColor.BACKGROUND to solid(pat(PatternKind.TOPO, 0.10f, 0xFF2563EB), pat(PatternKind.NOISE, 0.04f)),
        ThemeColor.CARD to solid(pat(PatternKind.NOISE, 0.035f)),
        ThemeColor.BUTTON to fill(blueLightButton, pat(PatternKind.NOISE, 0.06f)),
        ThemeColor.HIGHLIGHT to solid(pat(PatternKind.TOPO, 0.10f, 0xFF2563EB, seed = 3))
    )

    // ---- Green / Black: forest, in mist ---------------------------------------------------------

    private val greenDark = palette(
        background = 0xFF070B09, card = 0xFF101813, topBar = 0xFF0B110D, bottomBar = 0xFF0D1410,
        text = 0xFFE7F3EB, button = 0xFF34C47C, highlight = 0xFF15372A, outline = 0xFF22362B
    )
    private val greenLight = palette(
        background = 0xFFF5F9F6, card = 0xFFFFFFFF, topBar = 0xFFFFFFFF, bottomBar = 0xFFFFFFFF,
        text = 0xFF0C1710, button = 0xFF168A4F, highlight = 0xFFD7EFE1, outline = 0xFFD2E2D8
    )
    private val greenButton = stops(GradientKind.LINEAR, 90, 0f to 0xFF55D894, 0.5f to 0xFF34C47C, 1f to 0xFF239C5E)
    private val greenLightButton = stops(GradientKind.LINEAR, 90, 0f to 0xFF2BA566, 0.5f to 0xFF168A4F, 1f to 0xFF0F6E3E)

    private val greenDarkFills = greenDark.with(ThemeColor.BUTTON to fill(greenButton))
    private val greenLightFills = greenLight.with(ThemeColor.BUTTON to fill(greenLightButton))
    private val greenDarkTextured = greenDark.with(
        ThemeColor.BACKGROUND to fill(screenLight(0xFF103424, 0xFF070B09), pat(PatternKind.CLOUDS, 0.09f, 0xFFBFEBD3), pat(PatternKind.NOISE, 0.07f)),
        ThemeColor.CARD to solid(pat(PatternKind.NOISE, 0.06f)),
        ThemeColor.TOP_BAR to solid(pat(PatternKind.NOISE, 0.06f)),
        ThemeColor.BOTTOM_BAR to solid(pat(PatternKind.NOISE, 0.06f)),
        ThemeColor.BUTTON to fill(greenButton, pat(PatternKind.NOISE, 0.07f)),
        ThemeColor.HIGHLIGHT to fill(glow(0xFF1F4D3A, 0xFF15372A, 0xFF0F281F), pat(PatternKind.CELLS, 0.08f, 0xFFBFEBD3))
    )
    private val greenLightTextured = greenLight.with(
        ThemeColor.BACKGROUND to solid(pat(PatternKind.CLOUDS, 0.05f, 0xFF168A4F), pat(PatternKind.NOISE, 0.04f)),
        ThemeColor.CARD to solid(pat(PatternKind.NOISE, 0.035f)),
        ThemeColor.BUTTON to fill(greenLightButton, pat(PatternKind.NOISE, 0.06f)),
        ThemeColor.HIGHLIGHT to solid(pat(PatternKind.CELLS, 0.08f, 0xFF168A4F))
    )

    // ---- Titanium: brushed grey metal --------------------------------------------------------

    private val titaniumDark = palette(
        background = 0xFF141518, card = 0xFF24272C, topBar = 0xFF1C1E22, bottomBar = 0xFF1E2125,
        text = 0xFFECEEF1, button = 0xFFC3C9D2, highlight = 0xFF3A4048, outline = 0xFF4A5059
    )
    private val titaniumLight = palette(
        background = 0xFFEDEFF2, card = 0xFFF8F9FA, topBar = 0xFFF4F5F7, bottomBar = 0xFFF4F5F7,
        text = 0xFF15171A, button = 0xFF4E5865, highlight = 0xFFD9DDE3, outline = 0xFFC4C9D0
    )
    private val titaniumBright = metal(0xFFF2F4F7, 0xFFC3C9D2, 0xFF8D96A2)
    private val titaniumDarkButton = metal(0xFF7D8896, 0xFF4E5865, 0xFF353C45)

    private val titaniumDarkFills = titaniumDark.with(
        ThemeColor.BACKGROUND to fill(stops(GradientKind.LINEAR, 100, 0f to 0xFF22252A, 0.5f to 0xFF17191C, 1f to 0xFF101113)),
        ThemeColor.CARD to fill(metal(0xFF353A41, 0xFF262A2F, 0xFF1D2024)),
        ThemeColor.TOP_BAR to fill(metal(0xFF30343A, 0xFF22252A, 0xFF191B1F)),
        ThemeColor.BOTTOM_BAR to fill(metal(0xFF30343A, 0xFF22252A, 0xFF191B1F)),
        ThemeColor.BUTTON to fill(titaniumBright),
        ThemeColor.HIGHLIGHT to fill(metal(0xFF5A626C, 0xFF3D434B, 0xFF2D3238))
    )
    private val titaniumLightFills = titaniumLight.with(
        ThemeColor.CARD to fill(metal(0xFFFFFFFF, 0xFFF4F5F7, 0xFFE4E7EB)),
        ThemeColor.TOP_BAR to fill(metal(0xFFFFFFFF, 0xFFF0F2F4, 0xFFE1E4E8)),
        ThemeColor.BOTTOM_BAR to fill(metal(0xFFFFFFFF, 0xFFF0F2F4, 0xFFE1E4E8)),
        ThemeColor.BUTTON to fill(titaniumDarkButton),
        ThemeColor.HIGHLIGHT to fill(metal(0xFFEEF0F3, 0xFFD9DDE3, 0xFFC2C8D0))
    )
    private fun FillStyle.brushed(intensity: Float, seed: Int = 1) = copy(patterns = patterns + pat(PatternKind.BRUSHED, intensity, seed = seed))
    private val titaniumDarkTextured = titaniumDark.copy(fills = titaniumDarkFills.fills.mapValues { (k, f) ->
        f.brushed(if (k == ThemeColor.BUTTON) 0.10f else 0.10f, seed = k.ordinal + 1)
    })
    private val titaniumLightTextured = titaniumLight.copy(fills = titaniumLightFills.fills.mapValues { (k, f) ->
        f.brushed(if (k == ThemeColor.BUTTON) 0.12f else 0.09f, seed = k.ordinal + 1)
    } + (ThemeColor.BACKGROUND to solid(pat(PatternKind.BRUSHED, 0.07f))))

    // ---- Man of Iron: oxblood and brass on gunmetal, a small arc of reactor light ---------------
    // Minimal: every surface a solid colour with at most a faint pattern; muted, not candy-bright. The one gradient
    // is the highlight's: a small cyan glow in a deep navy card, bright only near its centre.

    private val ironDark = palette(
        background = 0xFF0D0E10, card = 0xFF18191C, topBar = 0xFF6E1416, bottomBar = 0xFF141517,
        text = 0xFFE8E6E1, button = 0xFFB8954A, highlight = 0xFF0F2A3A, outline = 0xFF4A4030
    )
    private val ironLight = palette(
        background = 0xFFEEEEEC, card = 0xFFFAFAF8, topBar = 0xFF6E1416, bottomBar = 0xFFFAFAF8,
        text = 0xFF17181A, button = 0xFF8C6E32, highlight = 0xFF0F2A3A, outline = 0xFFCDBE9A
    )

    /** The reactor's light, kept small: cyan only within the inner part of the card, deep navy everywhere else. */
    private val reactor = GradientSpec(
        kind = GradientKind.RADIAL,
        centerX = 0.5f, centerY = 0.5f, radius = 0.55f,
        stops = listOf(
            GradientStop(0f, c(0xFF5FC9E6)),
            GradientStop(0.22f, c(0xFF2A7E9C)),
            GradientStop(0.55f, c(0xFF12384A)),
            GradientStop(1f, c(0xFF0F2A3A))
        )
    )

    private val ironDarkFills = ironDark.with(
        ThemeColor.BACKGROUND to solid(pat(PatternKind.HEX, 0.04f), pat(PatternKind.NOISE, 0.05f)),
        ThemeColor.CARD to solid(pat(PatternKind.BRUSHED, 0.06f, seed = 6)),
        ThemeColor.TOP_BAR to solid(pat(PatternKind.BRUSHED, 0.08f, seed = 7)),
        ThemeColor.BOTTOM_BAR to solid(pat(PatternKind.NOISE, 0.05f)),
        ThemeColor.BUTTON to solid(pat(PatternKind.BRUSHED, 0.08f, seed = 9)),
        ThemeColor.HIGHLIGHT to fill(reactor, pat(PatternKind.HEX, 0.06f))
    )
    private val ironLightFills = ironLight.with(
        ThemeColor.BACKGROUND to solid(pat(PatternKind.HEX, 0.04f, 0xFF17181A), pat(PatternKind.NOISE, 0.03f)),
        ThemeColor.CARD to solid(pat(PatternKind.NOISE, 0.025f)),
        ThemeColor.TOP_BAR to solid(pat(PatternKind.BRUSHED, 0.08f, seed = 7)),
        ThemeColor.BUTTON to solid(pat(PatternKind.BRUSHED, 0.08f, seed = 9)),
        ThemeColor.HIGHLIGHT to fill(reactor, pat(PatternKind.HEX, 0.06f))
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
        ThemePreset("man_of_iron", "Man of Iron", true, settings(ironLightFills, ironDarkFills, borders = 1f)),
        ThemePreset("contrast_cyan", "High contrast cyan", false, contrast(0xFF2EC5FF)),
        ThemePreset("contrast_red", "High contrast red", false, contrast(0xFFFF3B3B)),
        ThemePreset("contrast_green", "High contrast green", false, contrast(0xFF3DDC84)),
        ThemePreset("contrast_amber", "High contrast amber", false, contrast(0xFFFFB020)),
        ThemePreset("contrast_white", "High contrast white", false, contrast(0xFFFFFFFF))
    )
}
