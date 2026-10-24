package com.ashrdev.aznd.ui.common.share

import com.ashrdev.aznd.ui.theme.borderKindOf
import com.ashrdev.aznd.ui.theme.taperLineFromJson
import android.content.Context
import com.ashrdev.aznd.ui.theme.fillStyleFromJson
import com.ashrdev.aznd.ui.theme.gradientFromJson
import com.ashrdev.aznd.ui.theme.patternsFromJson
import com.ashrdev.aznd.ui.theme.toJson
import com.ashrdev.aznd.ui.theme.toJsonArray
import com.ashrdev.aznd.ui.common.tiling.TileGroup
import com.ashrdev.aznd.ui.common.tiling.TileLeaf
import com.ashrdev.aznd.ui.common.tiling.TileNode
import com.ashrdev.aznd.ui.common.tiling.TileTree
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
        .put("padding", s.padding)
        .put("paddingV", s.paddingV)
        .put("spacing", s.spacing)
        .put("panel", s.panel.name)
        .put("panelColor", s.panelColor)
        .put("panelAlpha", s.panelAlpha.toDouble())
        .put("panelRadius", s.panelRadius)
        .put("panelFill", s.panelFill.toJson())
        .put("panelBorderWidth", s.panelBorderWidth)
        .put("panelBorderColor", s.panelBorderColor)
        .put("panelBorderKind", s.panelBorderKind.name)
        .put("cardRadius", s.cardRadius)
        .put("borderWidth", s.borderWidth)
        .put("borderColor", s.borderColor)
        .put("borderKind", s.borderKind.name)
        .put("lineTaper", s.lineTaper.toJson())
        .put("watermark", s.watermark)
        .put("watermarkText", s.watermarkText)
        .put("watermarkAlign", s.watermarkAlign.name)
        .put("moduleScale", s.moduleScale.toDouble())
        .put("layoutMode", s.layoutMode.name)
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
                .put("hAlign", b.hAlign.name)
                .put("vAlign", b.vAlign.name)
                .put("photoRatio", b.photoRatio.toDouble())
                .put("photoZoom", b.photoZoom.toDouble())
                .put("photoX", b.photoX.toDouble())
                .put("photoY", b.photoY.toDouble())
                .put("freeX", b.freeX.toDouble())
                .put("freeY", b.freeY.toDouble())
                .put("freeW", b.freeW.toDouble())
                .put("freeH", b.freeH.toDouble())
        )
    }
    val out = JSONObject().put("v", STORE_VERSION).put("style", style).put("blocks", blocks)
    spec.layout?.let { out.put("layout", nodeToJson(it)) }
    return out
}

private fun nodeToJson(n: TileNode): JSONObject = when (n) {
    is TileLeaf -> JSONObject().put("id", n.id)
    is TileGroup -> JSONObject()
        .put("vertical", n.vertical)
        .put("weights", JSONArray().also { a -> n.weights.forEach { a.put(it.toDouble()) } })
        .put("children", JSONArray().also { a -> n.children.forEach { a.put(nodeToJson(it)) } })
}

private fun nodeFromJson(o: JSONObject, depth: Int = 0): TileNode? {
    if (depth > 12) return null
    if (o.has("children")) {
        val arr = o.getJSONArray("children")
        val weights = o.optJSONArray("weights")
        val kids = ArrayList<TileNode>()
        val ws = ArrayList<Float>()
        for (i in 0 until arr.length()) {
            nodeFromJson(arr.getJSONObject(i), depth + 1)?.let {
                kids.add(it)
                ws.add((weights?.optDouble(i, 0.0) ?: 0.0).toFloat().let { w -> if (w.isFinite() && w > 0f) w else 0f })
            }
        }
        if (kids.isEmpty()) return null
        if (kids.size == 1) return kids[0]
        return TileGroup(o.optBoolean("vertical", true), kids, ws)
    }
    return if (o.has("id")) TileLeaf(o.getInt("id")) else null
}

/**
 * Cards saved before tiling placed blocks in rows ("place" beside or below the one before, and a "width" share).
 * Turns such a card's rows into a tiling; null when every block simply sat under the previous one.
 */
private fun legacyLayout(arr: JSONArray, blocks: List<FlexBlock>): TileNode? {
    val rows = ArrayList<MutableList<Pair<Int, Float>>>()
    val enabled = blocks.filter { it.enabled }.map { it.id }.toSet()
    for (i in 0 until arr.length()) {
        val b = arr.getJSONObject(i)
        val id = b.optInt("id", i + 1)
        if (id !in enabled) continue
        val width = b.optDouble("width", 1.0).toFloat().coerceIn(0.05f, 20f)
        if (b.optString("place") == "BESIDE" && rows.isNotEmpty() && rows.last().size < 3) {
            rows.last().add(id to width)
        } else {
            rows.add(mutableListOf(id to width))
        }
    }
    if (rows.none { it.size > 1 }) return null
    val tiles: List<TileNode> = rows.map { row ->
        if (row.size == 1) TileLeaf(row[0].first)
        else TileGroup(false, row.map { TileLeaf(it.first) }, row.map { it.second })
    }
    return if (tiles.size == 1) tiles[0] else TileGroup(true, tiles, tiles.map { 0f })
}

private const val STORE_VERSION = 2

private fun distinctLeaves(n: TileNode?, seen: MutableSet<Int> = HashSet()): TileNode? = when (n) {
    null -> null
    is TileLeaf -> if (seen.add(n.id)) n else null
    is TileGroup -> {
        val kids = ArrayList<TileNode>()
        val ws = ArrayList<Float>()
        n.children.forEachIndexed { i, c ->
            distinctLeaves(c, seen)?.let {
                kids += it
                ws += n.weights.getOrElse(i) { 0f }
            }
        }
        when (kids.size) {
            0 -> null
            1 -> kids[0]
            else -> TileGroup(n.vertical, kids, ws)
        }
    }
}

private fun specFromJson(o: JSONObject): FlexSpec {
    val legacy = o.optInt("v", 1) < STORE_VERSION
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
        padding = s.optInt("padding", d.padding),
        paddingV = s.optInt("paddingV", s.optInt("padding", d.paddingV)),
        spacing = s.optInt("spacing", d.spacing),
        panel = enumOr(s.optString("panel"), d.panel),
        panelColor = s.optInt("panelColor", d.panelColor),
        panelAlpha = s.optDouble("panelAlpha", d.panelAlpha.toDouble()).toFloat(),
        panelRadius = s.optInt("panelRadius", d.panelRadius),
        panelFill = fillStyleFromJson(s.optJSONObject("panelFill")),
        panelBorderWidth = s.optInt("panelBorderWidth", d.panelBorderWidth),
        panelBorderColor = s.optInt("panelBorderColor", d.panelBorderColor),
        panelBorderKind = borderKindOf(s.optString("panelBorderKind"), d.panelBorderKind),
        cardRadius = s.optInt("cardRadius", d.cardRadius),
        borderWidth = s.optInt("borderWidth", d.borderWidth),
        borderColor = s.optInt("borderColor", d.borderColor),
        borderKind = borderKindOf(s.optString("borderKind"), d.borderKind),
        lineTaper = taperLineFromJson(s.optJSONObject("lineTaper")),
        watermark = s.optBoolean("watermark", d.watermark),
        watermarkText = s.optString("watermarkText", d.watermarkText),
        watermarkAlign = enumOr(s.optString("watermarkAlign"), d.watermarkAlign),
        moduleScale = s.optDouble("moduleScale", d.moduleScale.toDouble()).toFloat().coerceIn(FLEX_MODULE_MIN, FLEX_MODULE_MAX),
        layoutMode = enumOr(s.optString("layoutMode"), d.layoutMode)
    )
    // Before alignment belonged to each block it was one setting for the whole card: carry it over.
    val oldAlign = enumOr(s.optString("align"), FlexAlign.START)
    val oldVAlign = enumOr(s.optString("vAlign"), FlexVAlign.TOP)
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
            hAlign = enumOr(b.optString("hAlign"), oldAlign),
            vAlign = enumOr(b.optString("vAlign"), oldVAlign),
            photoRatio = if (legacy) 0f else b.optDouble("photoRatio", 0.0).toFloat().let { if (it.isNaN()) 0f else if (it < 0f) -1f else it.coerceAtMost(4f) },
            photoZoom = b.optDouble("photoZoom", 1.0).toFloat().coerceIn(1f, 3f),
            photoX = b.optDouble("photoX", 0.5).toFloat().coerceIn(0f, 1f),
            photoY = b.optDouble("photoY", 0.5).toFloat().coerceIn(0f, 1f),
            freeX = b.optDouble("freeX", 0.0).toFloat(),
            freeY = b.optDouble("freeY", 0.0).toFloat(),
            freeW = b.optDouble("freeW", 0.0).toFloat().coerceAtLeast(0f),
            freeH = b.optDouble("freeH", 0.0).toFloat().coerceAtLeast(0f)
        )
    }
    val layout = distinctLeaves(o.optJSONObject("layout")?.let { nodeFromJson(it) } ?: legacyLayout(arr, blocks))
    return FlexSpec(style, blocks, if (legacy) TileTree.relaxed(layout) else layout)
}
