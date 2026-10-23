package com.ashrdev.aznd.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

/*
 * How a surface (background, cards, bars, buttons, a Flex card ...) is filled.
 *
 * A fill is a BASE (one solid colour, or a multi-colour gradient) with optional PATTERNS laid over it
 * (grain, brushed metal, clouds, topographic lines, marble ..., see PatternPixels.kt). The solid colour itself is not stored here: it is the element's
 * normal colour (a palette colour in the app, a card colour in Flex), so the plain colour pickers keep
 * working. Colours inside a fill are plain ARGB ints so the same classes serve Compose and android.graphics.
 */

enum class FillBase(val label: String) { SOLID("Solid"), GRADIENT("Gradient") }

enum class GradientKind(val label: String) { LINEAR("Linear"), RADIAL("Radial"), SWEEP("Sweep") }

/**
 * Overlay patterns. [tinted] patterns use the pattern colour; the others are neutral light/dark grain that
 * works on any base. [defaultScale] / [defaultIntensity] are what picking the pattern starts with.
 */
enum class PatternKind(
    val label: String,
    val tinted: Boolean,
    val defaultScale: Float,
    val defaultIntensity: Float,
    /** True when the pattern is random, so a new seed gives a new arrangement (the regular ones look the same). */
    val seeded: Boolean = false
) {
    NONE("None", false, 1f, 0f),
    NOISE("Noise", false, 1f, 0.16f, seeded = true),
    CLOUDS("Clouds", true, 1f, 0.20f, seeded = true),
    SPECKLE("Speckle", true, 1f, 0.70f, seeded = true),
    BRUSHED("Brushed", false, 1f, 0.16f, seeded = true),
    CARBON("Carbon", false, 1f, 0.30f),
    DOTS("Dots", true, 1.2f, 0.12f),
    LINES("Lines", true, 1.2f, 0.10f),
    GRID("Grid", true, 1.2f, 0.10f),
    TOPO("Topographic", true, 1f, 0.22f, seeded = true),
    CELLS("Cells", true, 1f, 0.16f, seeded = true),
    MARBLE("Marble", true, 1f, 0.30f, seeded = true),
    HEX("Honeycomb", true, 1.2f, 0.14f),
    WAVES("Waves", true, 1.2f, 0.14f),
    HALFTONE("Halftone", true, 1f, 0.16f, seeded = true)
}

/** A colour at a position (0..1) along a gradient. */
@Immutable
data class GradientStop(val position: Float, val color: Int)

/**
 * A multi-colour gradient. [angle] is in degrees: 0 runs left to right, 90 top to bottom (linear), and is
 * the start angle of a sweep. [centerX] / [centerY] (0..1 of the surface) place radial and sweep gradients;
 * [radius] is a share of the half-diagonal (1 reaches the corners).
 */
@Immutable
data class GradientSpec(
    val kind: GradientKind = GradientKind.LINEAR,
    val angle: Int = 90,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val radius: Float = 1f,
    val stops: List<GradientStop> = listOf(GradientStop(0f, 0xFF000000.toInt()), GradientStop(1f, 0xFF444444.toInt()))
) {
    /** Stops sorted by position, positions kept inside 0..1, never fewer than two. */
    fun cleanStops(): List<GradientStop> {
        val s = stops.map { GradientStop(it.position.coerceIn(0f, 1f), it.color) }.sortedBy { it.position }
        return when {
            s.size >= 2 -> s
            s.size == 1 -> listOf(GradientStop(0f, s[0].color), GradientStop(1f, s[0].color))
            else -> listOf(GradientStop(0f, 0xFF000000.toInt()), GradientStop(1f, 0xFF000000.toInt()))
        }
    }

    /** The gradient's colour at [t] (0..1), blended in RGB. */
    fun colorAt(t: Float): Int {
        val s = cleanStops()
        val x = t.coerceIn(0f, 1f)
        if (x <= s.first().position) return s.first().color
        if (x >= s.last().position) return s.last().color
        for (i in 0 until s.size - 1) {
            val a = s[i]
            val b = s[i + 1]
            if (x <= b.position) {
                val span = (b.position - a.position).coerceAtLeast(0.0001f)
                return mixArgb(a.color, b.color, (x - a.position) / span)
            }
        }
        return s.last().color
    }

    /** The average colour of the whole gradient: stands in for it where one flat colour is needed. */
    fun meanColor(): Int {
        val n = 16
        var r = 0f
        var g = 0f
        var b = 0f
        for (i in 0..n) {
            val c = colorAt(i / n.toFloat())
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
        }
        val k = (n + 1).toFloat()
        return (0xFF shl 24) or ((r / k).roundToInt() shl 16) or ((g / k).roundToInt() shl 8) or (b / k).roundToInt()
    }

    fun reversed(): GradientSpec = copy(stops = stops.map { GradientStop(1f - it.position, it.color) }.sortedBy { it.position })

    companion object {
        /** Evenly spaced [colors]. */
        fun of(kind: GradientKind, angle: Int, vararg colors: Int): GradientSpec {
            val n = colors.size.coerceAtLeast(2)
            return GradientSpec(
                kind = kind,
                angle = angle,
                stops = colors.mapIndexed { i, c -> GradientStop(i / (n - 1).toFloat(), c) }
            )
        }

        fun radial(vararg colors: Int, centerX: Float = 0.5f, centerY: Float = 0.4f, radius: Float = 1f): GradientSpec =
            of(GradientKind.RADIAL, 0, *colors).copy(centerX = centerX, centerY = centerY, radius = radius)
    }
}

@Immutable
data class PatternSpec(
    val kind: PatternKind = PatternKind.NONE,
    /** Colour of [PatternKind.tinted] patterns. */
    val tint: Int = 0xFFFFFFFF.toInt(),
    /** 0..1, how strongly the pattern shows. */
    val intensity: Float = 0.16f,
    /** 0.5..4, how big its grain / cells are. */
    val scale: Float = 1f,
    /** Another number, another random arrangement of the same pattern. */
    val seed: Int = 1,
    /** A switched-off pattern stays in the list (with its settings) but is not drawn. */
    val enabled: Boolean = true
) {
    /** Whether this pattern puts anything on the surface. */
    val isActive: Boolean get() = enabled && kind != PatternKind.NONE && intensity > 0f

    companion object {
        fun of(kind: PatternKind, intensity: Float = kind.defaultIntensity, scale: Float = kind.defaultScale, tint: Int = 0xFFFFFFFF.toInt()) =
            PatternSpec(kind, tint, intensity, scale)
    }
}

@Immutable
data class FillStyle(
    val base: FillBase = FillBase.SOLID,
    val gradient: GradientSpec = GradientSpec(),
    /** Any number of patterns, drawn over the base in this order (first = lowest). */
    val patterns: List<PatternSpec> = emptyList()
) {
    /** The patterns that are switched on and actually draw something, in drawing order. */
    val activePatterns: List<PatternSpec> get() = patterns.filter { it.isActive }

    /** Just a flat colour: nothing to draw beyond it. */
    val isPlain: Boolean get() = base == FillBase.SOLID && activePatterns.isEmpty()

    /** One flat ARGB colour that stands in for the fill (text contrast, dialogs and menus that can only be flat). */
    fun representative(solid: Int): Int = if (base == FillBase.GRADIENT) gradient.meanColor() else solid

    /** Short description for a settings row. */
    fun summary(): String {
        val baseText = when (base) {
            FillBase.SOLID -> "Solid"
            FillBase.GRADIENT -> "${gradient.kind.label} gradient, ${gradient.cleanStops().size} colours"
        }
        val active = activePatterns
        return when {
            active.isEmpty() -> baseText
            active.size == 1 -> "$baseText + ${active[0].kind.label.lowercase(Locale.US)}"
            else -> "$baseText + ${active.size} patterns"
        }
    }

    companion object {
        val Plain = FillStyle()

        /** Solid colour with a pattern over it. */
        fun patterned(kind: PatternKind, intensity: Float = kind.defaultIntensity, scale: Float = kind.defaultScale, tint: Int = 0xFFFFFFFF.toInt()) =
            FillStyle(patterns = listOf(PatternSpec.of(kind, intensity, scale, tint)))

        /** A gradient, optionally with a pattern over it. */
        fun ofGradient(g: GradientSpec, pattern: PatternSpec = PatternSpec()) =
            FillStyle(
                base = FillBase.GRADIENT,
                gradient = g,
                patterns = if (pattern.kind == PatternKind.NONE) emptyList() else listOf(pattern)
            )
    }
}

/** A fill together with the solid colour it sits on: everything needed to paint a surface. */
@Immutable
data class ResolvedFill(val base: Color, val style: FillStyle = FillStyle.Plain) {
    val isPlain: Boolean get() = style.isPlain

    /** One flat colour standing in for the whole fill. */
    fun representative(): Color = Color(style.representative(base.toArgb()))
}

fun mixArgb(a: Int, b: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    fun ch(shift: Int): Int {
        val x = (a shr shift) and 0xFF
        val y = (b shr shift) and 0xFF
        return (x + (y - x) * k).roundToInt().coerceIn(0, 255)
    }
    return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

// ---------------------------------------------------------------------------------------------
// JSON (theme files and Flex cards store fills the same way)
// ---------------------------------------------------------------------------------------------

private fun hex(c: Int): String = String.format(Locale.US, "#%06X", 0xFFFFFF and c)

private fun parseColorInt(text: String?, fallback: Int): Int {
    if (text == null) return fallback
    return runCatching { android.graphics.Color.parseColor(text) }.getOrDefault(fallback)
}

private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

fun GradientSpec.toJson(): JSONObject = JSONObject().apply {
    put("kind", kind.name)
    put("angle", angle)
    put("centerX", centerX.toDouble())
    put("centerY", centerY.toDouble())
    put("radius", radius.toDouble())
    put("stops", JSONArray().also { arr ->
        cleanStops().forEach { arr.put(JSONObject().put("at", it.position.toDouble()).put("color", hex(it.color))) }
    })
}

fun gradientFromJson(o: JSONObject?, fallback: GradientSpec = GradientSpec()): GradientSpec {
    if (o == null) return fallback
    val stops = ArrayList<GradientStop>()
    val arr = o.optJSONArray("stops")
    if (arr != null) {
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            stops += GradientStop(
                position = s.optDouble("at", 0.0).toFloat().coerceIn(0f, 1f),
                color = parseColorInt(s.optString("color"), 0xFF000000.toInt())
            )
        }
    }
    return GradientSpec(
        kind = enumOrDefault(o.optString("kind"), fallback.kind),
        angle = o.optInt("angle", fallback.angle),
        centerX = o.optDouble("centerX", fallback.centerX.toDouble()).toFloat().coerceIn(0f, 1f),
        centerY = o.optDouble("centerY", fallback.centerY.toDouble()).toFloat().coerceIn(0f, 1f),
        radius = o.optDouble("radius", fallback.radius.toDouble()).toFloat().coerceIn(0.1f, 3f),
        stops = if (stops.size >= 2) stops else fallback.stops
    )
}

fun PatternSpec.toJson(): JSONObject = JSONObject().apply {
    put("kind", kind.name)
    put("tint", hex(tint))
    put("intensity", intensity.toDouble())
    put("scale", scale.toDouble())
    put("seed", seed)
    put("enabled", enabled)
}

fun patternFromJson(o: JSONObject?): PatternSpec {
    if (o == null) return PatternSpec()
    val d = PatternSpec()
    return PatternSpec(
        kind = enumOrDefault(o.optString("kind"), d.kind),
        tint = parseColorInt(o.optString("tint"), d.tint),
        intensity = o.optDouble("intensity", d.intensity.toDouble()).toFloat().coerceIn(0f, 1f),
        scale = o.optDouble("scale", d.scale.toDouble()).toFloat().coerceIn(0.25f, 6f),
        seed = o.optInt("seed", d.seed),
        enabled = o.optBoolean("enabled", d.enabled)
    )
}

fun List<PatternSpec>.toJsonArray(): JSONArray = JSONArray().also { arr -> forEach { arr.put(it.toJson()) } }

/** Reads a list of patterns. Anything that is not an object is skipped. */
fun patternsFromJson(arr: JSONArray?): List<PatternSpec> {
    if (arr == null) return emptyList()
    val out = ArrayList<PatternSpec>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        out += patternFromJson(o)
    }
    return out
}

/**
 * Reads the patterns of a fill or Flex card: the "patterns" list, or the single "pattern" object that
 * files saved before several patterns were possible have ([legacyKey]).
 */
fun patternsFromJson(o: JSONObject?, listKey: String, legacyKey: String): List<PatternSpec> {
    if (o == null) return emptyList()
    o.optJSONArray(listKey)?.let { return patternsFromJson(it) }
    o.optJSONObject(legacyKey)?.let { legacy ->
        val p = patternFromJson(legacy)
        return if (p.kind == PatternKind.NONE) emptyList() else listOf(p)
    }
    return emptyList()
}

fun FillStyle.toJson(): JSONObject = JSONObject().apply {
    put("base", base.name)
    put("gradient", gradient.toJson())
    put("patterns", patterns.toJsonArray())
}

fun fillStyleFromJson(o: JSONObject?): FillStyle {
    if (o == null) return FillStyle.Plain
    return FillStyle(
        base = enumOrDefault(o.optString("base"), FillBase.SOLID),
        gradient = gradientFromJson(o.optJSONObject("gradient")),
        patterns = patternsFromJson(o, "patterns", "pattern")
    )
}
