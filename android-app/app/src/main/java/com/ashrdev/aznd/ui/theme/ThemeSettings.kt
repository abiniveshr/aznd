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

enum class ThemeColor(val label: String) {
    BACKGROUND("Background"),
    CARD("Cards, menus, dialogs"),
    TOP_BAR("Top bar"),
    TEXT("Text"),
    BUTTON("Buttons and accents"),
    HIGHLIGHT("Highlight cards"),
    OUTLINE("Outlines")
}

data class Palette(
    val background: Color,
    val card: Color,
    val topBar: Color,
    val text: Color,
    val button: Color,
    val highlight: Color,
    val outline: Color
)

fun Palette.colorOf(element: ThemeColor): Color = when (element) {
    ThemeColor.BACKGROUND -> background
    ThemeColor.CARD -> card
    ThemeColor.TOP_BAR -> topBar
    ThemeColor.TEXT -> text
    ThemeColor.BUTTON -> button
    ThemeColor.HIGHLIGHT -> highlight
    ThemeColor.OUTLINE -> outline
}

fun Palette.withColor(element: ThemeColor, color: Color): Palette = when (element) {
    ThemeColor.BACKGROUND -> copy(background = color)
    ThemeColor.CARD -> copy(card = color)
    ThemeColor.TOP_BAR -> copy(topBar = color)
    ThemeColor.TEXT -> copy(text = color)
    ThemeColor.BUTTON -> copy(button = color)
    ThemeColor.HIGHLIGHT -> copy(highlight = color)
    ThemeColor.OUTLINE -> copy(outline = color)
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

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val cardCorner: Int = 12,
    val buttonCorner: Int = 50,
    val cardOutline: Boolean = false,
    val topBarOutline: Boolean = false,
    val fieldOutline: Boolean = true,
    val outlinedButtonOutline: Boolean = true,
    val filledButtonOutline: Boolean = false,
    val light: Palette = DefaultLightPalette,
    val dark: Palette = DefaultDarkPalette
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
}

private fun paletteFrom(o: JSONObject?, fb: Palette): Palette {
    if (o == null) return fb
    return Palette(
        background = parseHex(o.str("background"), fb.background),
        card = parseHex(o.str("card"), fb.card),
        topBar = parseHex(o.str("topBar"), fb.topBar),
        text = parseHex(o.str("text"), fb.text),
        button = parseHex(o.str("button"), fb.button),
        highlight = parseHex(o.str("highlight"), fb.highlight),
        outline = parseHex(o.str("outline"), fb.outline)
    )
}

fun ThemeSettings.toJson(): String = JSONObject().apply {
    put("format", "aznd-theme")
    put("version", 1)
    put("mode", mode.name)
    put("useDynamicColor", useDynamicColor)
    put("cardCorner", cardCorner)
    put("buttonCorner", buttonCorner)
    put("cardOutline", cardOutline)
    put("topBarOutline", topBarOutline)
    put("fieldOutline", fieldOutline)
    put("outlinedButtonOutline", outlinedButtonOutline)
    put("filledButtonOutline", filledButtonOutline)
    put("light", light.toJson())
    put("dark", dark.toJson())
}.toString(2)

fun themeFromJson(text: String): ThemeSettings {
    val o = JSONObject(text)
    require(o.optString("format") == "aznd-theme") { "Not an aznd theme file" }
    val d = ThemeSettings()
    return ThemeSettings(
        mode = runCatching { ThemeMode.valueOf(o.optString("mode")) }.getOrDefault(d.mode),
        useDynamicColor = o.optBoolean("useDynamicColor", d.useDynamicColor),
        cardCorner = o.optInt("cardCorner", d.cardCorner).coerceIn(0, 32),
        buttonCorner = o.optInt("buttonCorner", d.buttonCorner).coerceIn(0, 50),
        cardOutline = o.optBoolean("cardOutline", d.cardOutline),
        topBarOutline = o.optBoolean("topBarOutline", d.topBarOutline),
        fieldOutline = o.optBoolean("fieldOutline", d.fieldOutline),
        outlinedButtonOutline = o.optBoolean("outlinedButtonOutline", d.outlinedButtonOutline),
        filledButtonOutline = o.optBoolean("filledButtonOutline", d.filledButtonOutline),
        light = paletteFrom(o.optJSONObject("light"), d.light),
        dark = paletteFrom(o.optJSONObject("dark"), d.dark)
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
