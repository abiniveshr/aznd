package com.ashrdev.aznd.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The elements of a palette. [fillable] ones can also be a gradient or carry a pattern (see [FillStyle]);
 * their palette colour is then the solid colour / the colour under the pattern.
 */
enum class ThemeColor(val label: String, val fillable: Boolean) {
    BACKGROUND("Background", true),
    CARD("Cards, menus, dialogs", true),
    TOP_BAR("Top bar", true),
    BOTTOM_BAR("Bottom bar", true),
    TEXT("Text", false),
    BUTTON("Buttons and accents", true),
    HIGHLIGHT("Highlight cards", true),
    OUTLINE("Outlines and borders", false)
}

data class Palette(
    val background: Color,
    val card: Color,
    val topBar: Color,
    val text: Color,
    val button: Color,
    val highlight: Color,
    val outline: Color,
    /** [Color.Unspecified] = the bottom bar follows the cards (colour and fill), as it always did. */
    val bottomBar: Color = Color.Unspecified,
    /** Gradient / pattern per element. An element without an entry is its plain colour. */
    val fills: Map<ThemeColor, FillStyle> = emptyMap()
)

fun Palette.colorOf(element: ThemeColor): Color = when (element) {
    ThemeColor.BACKGROUND -> background
    ThemeColor.CARD -> card
    ThemeColor.TOP_BAR -> topBar
    ThemeColor.BOTTOM_BAR -> if (bottomBar == Color.Unspecified) card else bottomBar
    ThemeColor.TEXT -> text
    ThemeColor.BUTTON -> button
    ThemeColor.HIGHLIGHT -> highlight
    ThemeColor.OUTLINE -> outline
}

fun Palette.withColor(element: ThemeColor, color: Color): Palette = when (element) {
    ThemeColor.BACKGROUND -> copy(background = color)
    ThemeColor.CARD -> copy(card = color)
    ThemeColor.TOP_BAR -> copy(topBar = color)
    ThemeColor.BOTTOM_BAR -> copy(bottomBar = color)
    ThemeColor.TEXT -> copy(text = color)
    ThemeColor.BUTTON -> copy(button = color)
    ThemeColor.HIGHLIGHT -> copy(highlight = color)
    ThemeColor.OUTLINE -> copy(outline = color)
}

/** True while the bottom bar has neither its own colour nor its own fill. */
fun Palette.bottomBarFollowsCards(): Boolean =
    bottomBar == Color.Unspecified && !fills.containsKey(ThemeColor.BOTTOM_BAR)

fun Palette.fillOf(element: ThemeColor): FillStyle {
    if (!element.fillable) return FillStyle.Plain
    fills[element]?.let { return it }
    return if (element == ThemeColor.BOTTOM_BAR && bottomBar == Color.Unspecified) fills[ThemeColor.CARD] ?: FillStyle.Plain
    else FillStyle.Plain
}

fun Palette.withFill(element: ThemeColor, fill: FillStyle): Palette {
    if (!element.fillable) return this
    // The bottom bar keeps an explicit plain fill, otherwise it would fall back to following the cards.
    val keep = element == ThemeColor.BOTTOM_BAR || !fill.isPlain
    return copy(fills = if (keep) fills + (element to fill) else fills - element)
}

fun Palette.resolved(element: ThemeColor): ResolvedFill = ResolvedFill(colorOf(element), fillOf(element))

/**
 * The palette as flat colours: where an element is a gradient its colour becomes the gradient's average.
 * That is what text contrast, dialogs, menus and Material's own colour roles are worked out from.
 */
fun Palette.effective(): Palette {
    fun flat(e: ThemeColor): Color = resolved(e).representative()
    return copy(
        background = flat(ThemeColor.BACKGROUND),
        card = flat(ThemeColor.CARD),
        topBar = flat(ThemeColor.TOP_BAR),
        bottomBar = flat(ThemeColor.BOTTOM_BAR),
        button = flat(ThemeColor.BUTTON),
        highlight = flat(ThemeColor.HIGHLIGHT)
    )
}

val DefaultLightPalette = Palette(
    background = Color(0xFFFFFBFE),
    card = Color(0xFFEDE7F3),
    topBar = Color(0xFFF7F2FA),
    text = Color(0xFF1C1B1F),
    button = Color(0xFF6650A4),
    highlight = Color(0xFFD9CCF5),
    outline = Color(0xFF79747E)
)

val DefaultDarkPalette = Palette(
    background = Color(0xFF141218),
    card = Color(0xFF2B2930),
    topBar = Color(0xFF1D1B20),
    text = Color(0xFFE6E1E5),
    button = Color(0xFFD0BCFF),
    highlight = Color(0xFF4A4458),
    outline = Color(0xFF938F99)
)

/** How the high contrast theme makes its shades from its base colour (see [HighContrast]). */
enum class ContrastShade(val label: String) {
    /** The base colour at less and less opacity over black: darker shades of the same colour. */
    ALPHA("Opacity"),
    /** The base colour with less and less saturation: paler, greyer shades at the same brightness. */
    SATURATION("Saturation")
}

/**
 * The high contrast theme's own setting: every surface pitch black, every accent a shade of [color], made by
 * [shade]. A theme that has one was made by [HighContrast] and is remade from it whenever it changes.
 */
data class ContrastBase(val color: Color, val shade: ContrastShade = ContrastShade.ALPHA)

/** Largest border width the Appearance sliders offer, in dp. */
const val MAX_BORDER_DP = 8f

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val cardCorner: Int = 12,
    val buttonCorner: Int = 50,
    /** Border widths in dp; 0 = no border. All of them are drawn in the palette's outline colour. */
    val cardBorder: Float = 0f,
    val topBarBorder: Float = 0f,
    val bottomBarBorder: Float = 0f,
    val fieldBorder: Float = 1f,
    val outlinedButtonBorder: Float = 1f,
    val filledButtonBorder: Float = 0f,
    val light: Palette = DefaultLightPalette,
    val dark: Palette = DefaultDarkPalette,
    /** Set only for the high contrast theme: the base colour its palette is made from (see [HighContrast]). */
    val contrast: ContrastBase? = null
)

fun Color.toHexString(): String = String.format(Locale.US, "#%06X", 0xFFFFFF and toArgb())

private fun parseHex(hex: String?, fallback: Color): Color {
    if (hex == null) return fallback
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
}

private fun JSONObject.str(key: String): String? = if (has(key)) optString(key) else null

private fun Palette.toJson(): JSONObject = JSONObject().apply {
    put("background", background.toHexString())
    put("card", card.toHexString())
    put("topBar", topBar.toHexString())
    put("text", text.toHexString())
    put("button", button.toHexString())
    put("highlight", highlight.toHexString())
    put("outline", outline.toHexString())
    if (bottomBar != Color.Unspecified) put("bottomBar", bottomBar.toHexString())
    if (fills.isNotEmpty()) {
        put("fills", JSONObject().also { o ->
            fills.forEach { (element, fill) -> o.put(element.name, fill.toJson()) }
        })
    }
}

private fun paletteFrom(o: JSONObject?, fb: Palette): Palette {
    if (o == null) return fb
    val fills = HashMap<ThemeColor, FillStyle>()
    o.optJSONObject("fills")?.let { f ->
        ThemeColor.entries.filter { it.fillable }.forEach { element ->
            f.optJSONObject(element.name)?.let { fills[element] = fillStyleFromJson(it) }
        }
    }
    return Palette(
        background = parseHex(o.str("background"), fb.background),
        card = parseHex(o.str("card"), fb.card),
        topBar = parseHex(o.str("topBar"), fb.topBar),
        text = parseHex(o.str("text"), fb.text),
        button = parseHex(o.str("button"), fb.button),
        highlight = parseHex(o.str("highlight"), fb.highlight),
        outline = parseHex(o.str("outline"), fb.outline),
        bottomBar = parseHex(o.str("bottomBar"), Color.Unspecified),
        fills = fills
    )
}

fun ThemeSettings.toJson(): String = JSONObject().apply {
    put("format", "aznd-theme")
    put("version", 2)
    put("mode", mode.name)
    put("useDynamicColor", useDynamicColor)
    put("cardCorner", cardCorner)
    put("buttonCorner", buttonCorner)
    put("cardBorder", cardBorder.toDouble())
    put("topBarBorder", topBarBorder.toDouble())
    put("bottomBarBorder", bottomBarBorder.toDouble())
    put("fieldBorder", fieldBorder.toDouble())
    put("outlinedButtonBorder", outlinedButtonBorder.toDouble())
    put("filledButtonBorder", filledButtonBorder.toDouble())
    put("light", light.toJson())
    put("dark", dark.toJson())
    contrast?.let { put("contrast", JSONObject().put("color", it.color.toHexString()).put("shade", it.shade.name)) }
}.toString(2)

/** Reads a border width: the new key if there is one, else the old on/off switch (on = 1 dp). */
private fun JSONObject.border(key: String, oldSwitch: String, fallback: Float): Float {
    val width = when {
        has(key) -> optDouble(key, fallback.toDouble()).toFloat()
        has(oldSwitch) -> if (optBoolean(oldSwitch)) 1f else 0f
        else -> fallback
    }
    return width.coerceIn(0f, MAX_BORDER_DP)
}

fun themeFromJson(text: String): ThemeSettings {
    val o = JSONObject(text)
    require(o.optString("format") == "aznd-theme") { "Not an aznd theme file" }
    val d = ThemeSettings()
    val cardBorder = o.border("cardBorder", "cardOutline", d.cardBorder)
    return ThemeSettings(
        mode = runCatching { ThemeMode.valueOf(o.optString("mode")) }.getOrDefault(d.mode),
        useDynamicColor = o.optBoolean("useDynamicColor", d.useDynamicColor),
        cardCorner = o.optInt("cardCorner", d.cardCorner).coerceIn(0, 32),
        buttonCorner = o.optInt("buttonCorner", d.buttonCorner).coerceIn(0, 50),
        cardBorder = cardBorder,
        topBarBorder = o.border("topBarBorder", "topBarOutline", d.topBarBorder),
        // Files from before the bottom bar had its own width drew its outline with the cards' switch.
        bottomBarBorder = if (o.has("bottomBarBorder")) o.border("bottomBarBorder", "", d.bottomBarBorder) else cardBorder,
        fieldBorder = o.border("fieldBorder", "fieldOutline", d.fieldBorder),
        outlinedButtonBorder = o.border("outlinedButtonBorder", "outlinedButtonOutline", d.outlinedButtonBorder),
        filledButtonBorder = o.border("filledButtonBorder", "filledButtonOutline", d.filledButtonBorder),
        light = paletteFrom(o.optJSONObject("light"), d.light),
        dark = paletteFrom(o.optJSONObject("dark"), d.dark),
        contrast = o.optJSONObject("contrast")?.let { c ->
            ContrastBase(
                color = parseHex(c.str("color"), Color.White),
                shade = runCatching { ContrastShade.valueOf(c.optString("shade")) }.getOrDefault(ContrastShade.ALPHA)
            )
        }
    )
}

object ThemeStore {
    private const val PREFS = "aznd_theme"
    private const val KEY = "settings"

    private val _settings = MutableStateFlow(ThemeSettings())
    val settings: StateFlow<ThemeSettings> = _settings.asStateFlow()
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val saved = p.getString(KEY, null)
        if (saved != null) {
            runCatching { themeFromJson(saved) }.getOrNull()?.let { _settings.value = it }
        }
    }

    fun update(transform: (ThemeSettings) -> ThemeSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs?.edit()?.putString(KEY, next.toJson())?.apply()
    }

    fun replace(next: ThemeSettings) = update { next }

    fun reset() = update { ThemeSettings() }
}
