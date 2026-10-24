package com.ashrdev.aznd.ui.theme

import org.json.JSONObject

fun TaperLine.toJson(): JSONObject = JSONObject()
    .put("shape", shape.name)
    .put("region", region.name)
    .put("from", from.toDouble())
    .put("to", to.toDouble())
    .put("start", start.toDouble())

fun taperLineFromJson(o: JSONObject?): TaperLine {
    val d = TaperLine()
    if (o == null) return d
    return TaperLine(
        shape = TaperShape.entries.firstOrNull { it.name == o.optString("shape") } ?: d.shape,
        region = TaperRegion.entries.firstOrNull { it.name == o.optString("region") } ?: d.region,
        from = o.optDouble("from", d.from.toDouble()).toFloat(),
        to = o.optDouble("to", d.to.toDouble()).toFloat(),
        start = o.optDouble("start", d.start.toDouble()).toFloat()
    ).clean()
}

fun borderKindOf(name: String?, fallback: BorderKind = BorderKind.OUTLINE): BorderKind =
    BorderKind.entries.firstOrNull { it.name == name } ?: fallback
