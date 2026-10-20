package com.ashrdev.aznd.ui.common.share

import android.content.Context
import com.ashrdev.aznd.ui.theme.fillStyleFromJson
import com.ashrdev.aznd.ui.theme.gradientFromJson
import com.ashrdev.aznd.ui.theme.patternsFromJson
import com.ashrdev.aznd.ui.theme.toJson
import com.ashrdev.aznd.ui.theme.toJsonArray
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers each preset's customisation (look and blocks) so a card comes back the way it was left.
 * Plain SharedPreferences with one JSON string per preset, like the theme and graph settings.
 */
class FlexStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(preset: FlexPreset): FlexSpec? =
        prefs.getString(key(preset), null)?.let { runCatching { specFromJson(JSONObject(it)) }.getOrNull() }

    fun save(preset: FlexPreset, spec: FlexSpec) {
        prefs.edit().putString(key(preset), specToJson(spec).toString()).apply()
    }

    fun reset(preset: FlexPreset) {
        prefs.edit().remove(key(preset)).apply()
    }

    private fun key(preset: FlexPreset) = "spec_${preset.id}"

    private companion object {
        const val FILE = "flex_presets"
    }
}

private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

private fun JSONObject.stringOrNull(name: String): String? =
    if (has(name) && !isNull(name)) getString(name) else null

private fun specToJson(spec: FlexSpec): JSONObject {
    val s = spec.style
    val style = JSONObject()
        .put("aspect", s.aspect.name)
        .put("bgMode", s.bgMode.name)
        .put("bgA", s.bgA)
        .put("bgB", s.bgB)
        .put("gradientAngle", s.gradientAngle)
        .put("bgGradient", s.bgGradient?.toJson() ?: JSONObject.NULL)
        .put("bgPatterns", s.bgPatterns.toJsonArray())
        .put("photoUri", s.photoUri ?: JSONObject.NULL)
        .put("photoBlur", s.photoBlur.toDouble())
        .put("photoDim", s.photoDim.toDouble())
        .put("textColor", s.textColor)
        .put("accent", s.accent)
        .put("font", s.font.name)
        .put("textScale", s.textScale.toDouble())
        .put("align", s.align.name)
        .put("vAlign", s.vAlign.name)
        .put("padding", s.padding)
        .put("spacing", s.spacing)
        .put("panel", s.panel.name)
        .put("panelColor", s.panelColor)
        .put("panelAlpha", s.panelAlpha.toDouble())
        .put("panelRadius", s.panelRadius)
        .put("panelFill", s.panelFill.toJson())
        .put("panelBorderWidth", s.panelBorderWidth)
        .put("panelBorderColor", s.panelBorderColor)
        .put("cardRadius", s.cardRadius)
        .put("borderWidth", s.borderWidth)
        .put("borderColor", s.borderColor)
        .put("watermark", s.watermark)
        .put("watermarkText", s.watermarkText)
    val blocks = JSONArray()
    spec.blocks.forEach { b ->
        blocks.put(
            JSONObject()
                .put("id", b.id)
                .put("type", b.type.name)
                .put("enabled", b.enabled)
                .put("variant", b.variant)
                .put("keys", b.keys?.let { JSONArray(it) } ?: JSONObject.NULL)
                .put("limit", b.limit)
                .put("text", b.text)
                .put("flag", b.flag)
                .put("size", b.size.toDouble())
                .put("place", b.place.name)
                .put("width", b.width.toDouble())
                .put("photoRatio", b.photoRatio.toDouble())
                .put("photoZoom", b.photoZoom.toDouble())
                .put("photoX", b.photoX.toDouble())
                .put("photoY", b.photoY.toDouble())
        )
    }
    return JSONObject().put("style", style).put("blocks", blocks)
}

private fun specFromJson(o: JSONObject): FlexSpec {
    val d = FlexStyle()
    val s = o.getJSONObject("style")
    val style = FlexStyle(
        aspect = enumOr(s.optString("aspect"), d.aspect),
        bgMode = enumOr(s.optString("bgMode"), d.bgMode),
        bgA = s.optInt("bgA", d.bgA),
        bgB = s.optInt("bgB", d.bgB),
        gradientAngle = s.optInt("gradientAngle", d.gradientAngle),
        bgGradient = s.optJSONObject("bgGradient")?.let { gradientFromJson(it) },
        bgPatterns = patternsFromJson(s, "bgPatterns", "bgPattern"),
        photoUri = s.stringOrNull("photoUri"),
        photoBlur = s.optDouble("photoBlur", d.photoBlur.toDouble()).toFloat(),
        photoDim = s.optDouble("photoDim", d.photoDim.toDouble()).toFloat(),
        textColor = s.optInt("textColor", d.textColor),
        accent = s.optInt("accent", d.accent),
        font = enumOr(s.optString("font"), d.font),
        textScale = s.optDouble("textScale", d.textScale.toDouble()).toFloat(),
        align = enumOr(s.optString("align"), d.align),
        vAlign = enumOr(s.optString("vAlign"), d.vAlign),
        padding = s.optInt("padding", d.padding),
        spacing = s.optInt("spacing", d.spacing),
        panel = enumOr(s.optString("panel"), d.panel),
        panelColor = s.optInt("panelColor", d.panelColor),
        panelAlpha = s.optDouble("panelAlpha", d.panelAlpha.toDouble()).toFloat(),
        panelRadius = s.optInt("panelRadius", d.panelRadius),
        panelFill = fillStyleFromJson(s.optJSONObject("panelFill")),
        panelBorderWidth = s.optInt("panelBorderWidth", d.panelBorderWidth),
        panelBorderColor = s.optInt("panelBorderColor", d.panelBorderColor),
        cardRadius = s.optInt("cardRadius", d.cardRadius),
        borderWidth = s.optInt("borderWidth", d.borderWidth),
        borderColor = s.optInt("borderColor", d.borderColor),
        watermark = s.optBoolean("watermark", d.watermark),
        watermarkText = s.optString("watermarkText", d.watermarkText)
    )
    val arr = o.getJSONArray("blocks")
    val blocks = ArrayList<FlexBlock>()
    for (i in 0 until arr.length()) {
        val b = arr.getJSONObject(i)
        val type = FlexBlockType.entries.firstOrNull { it.name == b.optString("type") } ?: continue
        val keys = if (b.has("keys") && !b.isNull("keys")) {
            val k = b.getJSONArray("keys")
            List(k.length()) { idx -> k.getString(idx) }
        } else null
        blocks += FlexBlock(
            id = b.optInt("id", i + 1),
            type = type,
            enabled = b.optBoolean("enabled", true),
            variant = b.optInt("variant", 0).coerceIn(0, type.variants.size - 1),
            keys = keys,
            limit = b.optInt("limit", 3),
            text = b.optString("text", ""),
            flag = b.optBoolean("flag", true),
            size = b.optDouble("size", 1.0).toFloat().coerceIn(0.5f, 2f),
            place = enumOr(b.optString("place"), FlexPlace.AUTO),
            width = b.optDouble("width", 1.0).toFloat().coerceIn(0.05f, 20f),
            photoRatio = b.optDouble("photoRatio", 0.0).toFloat().coerceAtLeast(0f),
            photoZoom = b.optDouble("photoZoom", 1.0).toFloat().coerceIn(1f, 3f),
            photoX = b.optDouble("photoX", 0.5).toFloat().coerceIn(0f, 1f),
            photoY = b.optDouble("photoY", 0.5).toFloat().coerceIn(0f, 1f)
        )
    }
    return FlexSpec(style, blocks)
}
