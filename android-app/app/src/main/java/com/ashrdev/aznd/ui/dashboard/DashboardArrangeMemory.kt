package com.ashrdev.aznd.ui.dashboard

import android.content.Context
import com.ashrdev.aznd.ui.common.tiling.TileNode
import com.ashrdev.aznd.ui.common.tiling.TileTree
import com.ashrdev.aznd.ui.common.tiling.TileTreeJson
import org.json.JSONArray
import org.json.JSONObject

/**
 * The tiling last solved for each automatically arranged page, kept across app starts so a page can
 * be shown at once while its real search runs. Keyed by the page's widgets and the room it has, so a
 * different widget set never finds another's tiling. Only the tree is kept: sizes are placed again.
 */
internal object DashboardArrangeMemory {
    private const val FILE = "dashboard"
    private const val KEY = "arranged"
    private const val MAX = 12

    private val trees = LinkedHashMap<String, TileNode>()
    private var loaded = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        val raw = prefs(context).getString(KEY, null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val tree = o.optJSONObject("tree")?.let { TileTreeJson.decode(it) } ?: continue
                trees[o.optString("key")] = tree
            }
        }
    }

    fun get(context: Context, key: String): TileNode? = synchronized(this) {
        load(context)
        trees[key]
    }

    /** Remembers [tree] under [key] and writes it. Call off the main thread: it encodes JSON. */
    fun put(context: Context, key: String, tree: TileNode) {
        val text = synchronized(this) {
            load(context)
            val had = trees[key]
            if (had != null && TileTree.sameShape(had, tree)) return
            trees.remove(key)
            trees[key] = tree
            while (trees.size > MAX) trees.remove(trees.keys.first())
            val arr = JSONArray()
            for ((k, t) in trees) arr.put(JSONObject().put("key", k).put("tree", TileTreeJson.encode(t)))
            arr.toString()
        }
        prefs(context).edit().putString(KEY, text).apply()
    }
}
