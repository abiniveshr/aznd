package com.ashrdev.aznd.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ashrdev.aznd.ui.common.share.FlexFont
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

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

/** How the Mono theme makes its shades from its base colour (see [HighContrast]). */
enum class ContrastShade(val label: String) {
    /** The base colour at less and less opacity over black: darker shades of the same colour. */
    ALPHA("Opacity"),
    /** The base colour with less and less saturation: paler, greyer shades at the same brightness. */
    SATURATION("Saturation")
}

/** Largest border width the Appearance sliders offer, in dp. */
const val MAX_BORDER_DP = 8f

const val MIN_TEXT_SCALE = 0.85f
const val MAX_TEXT_SCALE = 1.3f

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
    val cardBorderLine: Boolean = false,
    val topBarBorderLine: Boolean = false,
    val bottomBarBorderLine: Boolean = false,
    val fieldBorderLine: Boolean = false,
    val outlinedButtonBorderLine: Boolean = false,
    val filledButtonBorderLine: Boolean = false,
    val borderTaper: TaperLine = TaperLine(),
    val light: Palette = DefaultLightPalette,
    val dark: Palette = DefaultDarkPalette,
    val themeId: String = "carbon",
    val baseColor: Int? = null,
    val shade: ContrastShade = ContrastShade.ALPHA,
    val textScale: Float = 1f,
    /** The font of the whole app: one of Flex's fonts, or null for the system default. */
    val appFont: FlexFont? = null,
    val density: UiDensity = UiDensity.REGULAR
)

fun Color.toHexString(): String = String.format(Locale.US, "#%06X", 0xFFFFFF and toArgb())

private fun hex(argb: Int): String = String.format(Locale.US, "#%06X", 0xFFFFFF and argb)

private fun parseHex(hex: String?, fallback: Color): Color {
    if (hex == null) return fallback
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
}

private fun JSONObject.str(key: String): String? = if (has(key)) optString(key) else null

internal fun Palette.toJson(): JSONObject = JSONObject().apply {
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

internal fun paletteFrom(o: JSONObject?, fb: Palette): Palette {
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
    put("cardBorderLine", cardBorderLine)
    put("topBarBorderLine", topBarBorderLine)
    put("bottomBarBorderLine", bottomBarBorderLine)
    put("fieldBorderLine", fieldBorderLine)
    put("outlinedButtonBorderLine", outlinedButtonBorderLine)
    put("filledButtonBorderLine", filledButtonBorderLine)
    put("borderTaper", borderTaper.toJson())
    put("light", light.toJson())
    put("dark", dark.toJson())
    appFont?.let { put("appFont", it.name) }
    put("density", density.name)
    put("themeId", themeId)
    baseColor?.let { put("baseColor", hex(it)) }
    put("shade", shade.name)
    put("textScale", textScale.toDouble())
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
        cardBorderLine = o.optBoolean("cardBorderLine", d.cardBorderLine),
        topBarBorderLine = o.optBoolean("topBarBorderLine", d.topBarBorderLine),
        bottomBarBorderLine = o.optBoolean("bottomBarBorderLine", d.bottomBarBorderLine),
        fieldBorderLine = o.optBoolean("fieldBorderLine", d.fieldBorderLine),
        outlinedButtonBorderLine = o.optBoolean("outlinedButtonBorderLine", d.outlinedButtonBorderLine),
        filledButtonBorderLine = o.optBoolean("filledButtonBorderLine", d.filledButtonBorderLine),
        borderTaper = taperLineFromJson(o.optJSONObject("borderTaper")),
        light = paletteFrom(o.optJSONObject("light"), d.light),
        dark = paletteFrom(o.optJSONObject("dark"), d.dark),
        appFont = FlexFont.entries.firstOrNull { it.name == o.optString("appFont") },
        density = UiDensity.entries.firstOrNull { it.name == o.optString("density") } ?: d.density,
        themeId = o.optString("themeId", d.themeId).ifEmpty { d.themeId },
        baseColor = if (o.has("baseColor")) parseHex(o.str("baseColor"), Color.Unspecified).takeIf { it != Color.Unspecified }?.toArgb() else null,
        shade = ContrastShade.entries.firstOrNull { it.name == o.optString("shade") } ?: d.shade,
        textScale = o.optDouble("textScale", d.textScale.toDouble()).toFloat().coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
    )
}

object ThemeStore {
    private const val PREFS = "aznd_theme"
    private const val KEY = "settings"

    /** How long after the last edit the theme is committed. */
    private const val COMMIT_DELAY_MS = 250L

    private val _settings = MutableStateFlow(ThemeSettings())

    /** The committed theme: what the whole app draws with. */
    val settings: StateFlow<ThemeSettings> = _settings.asStateFlow()

    private val _draft = MutableStateFlow(ThemeSettings())

    /** The theme as it is being edited: changes on every edit, and is committed to [settings] a moment after the last one, once the patterns it uses are baked. */
    val draft: StateFlow<ThemeSettings> = _draft.asStateFlow()

    private var prefs: SharedPreferences? = null

    internal var startupJson: JSONObject? = null
    private val handler = Handler(Looper.getMainLooper())
    private val commitTask = Runnable { if (ScreenBakes.readyFor(_draft.value)) commitNow() }

    private val _pending = MutableStateFlow(false)

    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    // One thread, so the saves land in the order they were committed.
    private val saver = Executors.newSingleThreadExecutor { r ->
        Thread(r, "theme-save").apply { isDaemon = true }
    }

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        PatternBake.init(context)
        ScreenBakes.init(context)
        val saved = p.getString(KEY, null)
        if (saved != null) {
            runCatching { themeFromJson(saved) }.getOrNull()?.let {
                _settings.value = it
                _draft.value = it
                startupJson = runCatching { JSONObject(saved) }.getOrNull()
            }
        }
        ThemeLibrary.init(context)
        startupJson = null
        publishDensity(_settings.value)
        val startDark = when (_settings.value.mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> ScreenBakes.systemDark()
        }
        ScreenBakes.setThemeRequired(ScreenBakes.themeSets(_settings.value, startDark))
        PatternBake.prewarm(_settings.value)
    }

    /** Hands the committed density to the spacing tokens, on the main thread. */
    private fun publishDensity(committed: ThemeSettings) {
        if (Looper.myLooper() == Looper.getMainLooper()) applyUiDensity(committed.density)
        else handler.post { applyUiDensity(committed.density) }
    }

    /** Changes the draft now and commits it [COMMIT_DELAY_MS] after the last call. */
    fun update(transform: (ThemeSettings) -> ThemeSettings) {
        _draft.value = transform(_draft.value)
        _pending.value = _draft.value != _settings.value
        handler.removeCallbacks(commitTask)
        handler.postDelayed(commitTask, COMMIT_DELAY_MS)
    }

    /** Publishes the draft as the committed theme and saves it; the Applying screen (see [ThemeHost]) covers any baking it needs. */
    fun commitNow() {
        handler.removeCallbacks(commitTask)
        val next = _draft.value
        _pending.value = false
        if (next == _settings.value) return
        _settings.value = next
        val p = prefs
        saver.execute {
            if (p != null) p.edit().putString(KEY, next.toJson()).apply()
            PatternBake.afterCommit(next)
        }
    }

    fun replace(next: ThemeSettings) {
        _draft.value = next
        commitNow()
    }

    internal fun adopt(next: ThemeSettings) {
        _draft.value = next
        _settings.value = next
        _pending.value = false
        val p = prefs
        saver.execute { p?.edit()?.putString(KEY, next.toJson())?.apply() }
    }

    fun reset() {
        ThemeLibrary.select(ThemeLibrary.ID_CARBON)
        val def = ThemeLibrary.working.value
        replace(ThemeDerive.apply(ThemeSettings(), def, ThemeLibrary.baseOf(def.id), ThemeLibrary.shadeOf(def.id)))
    }
}
