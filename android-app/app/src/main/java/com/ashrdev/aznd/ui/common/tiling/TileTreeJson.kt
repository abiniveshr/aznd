package com.ashrdev.aznd.ui.common.tiling

import org.json.JSONArray
import org.json.JSONObject

/** How a tiling is saved: Flex cards and the dashboard store their hand-made layouts this way. */
object TileTreeJson {
    private const val MAX_DEPTH = 12

    fun encode(n: TileNode): JSONObject = when (n) {
        is TileLeaf -> JSONObject().put("id", n.id)
        is TileGroup -> JSONObject()
            .put("vertical", n.vertical)
            .put("weights", JSONArray().also { a -> n.weights.forEach { a.put(it.toDouble()) } })
            .put("children", JSONArray().also { a -> n.children.forEach { a.put(encode(it)) } })
    }

    fun decode(o: JSONObject, depth: Int = 0): TileNode? {
        if (depth > MAX_DEPTH) return null
        if (o.has("children")) {
            val arr = o.getJSONArray("children")
            val weights = o.optJSONArray("weights")
            val kids = ArrayList<TileNode>()
            val ws = ArrayList<Float>()
            for (i in 0 until arr.length()) {
                decode(arr.getJSONObject(i), depth + 1)?.let {
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
}
