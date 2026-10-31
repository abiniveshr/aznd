package com.ashrdev.aznd.ui.dashboard

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ashrdev.aznd.ui.common.tiling.TileNode
import com.ashrdev.aznd.ui.common.tiling.TileTree
import com.ashrdev.aznd.ui.common.tiling.TileTreeJson
import org.json.JSONArray
import org.json.JSONObject

class DashboardStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("dashboard", Context.MODE_PRIVATE)
    private val loaded = load()

    var widgets: List<DashWidget> by mutableStateOf(loaded.first)
        private set

    /**
     * The tiling arranged by hand in the dashboard's edit mode, or null to let the tiles arrange
     * themselves. Widgets added later join it as new rows; removed ones drop out of it.
     */
    var layout: TileNode? by mutableStateOf(loaded.second)
        private set

    fun update(list: List<DashWidget>, tree: TileNode? = layout) {
        widgets = list.take(DashDefaults.MAX_WIDGETS)
        layout = tree?.let { TileTree.prune(it, widgets.map { w -> w.id }.toSet()) }
        prefs.edit().putString(KEY, encode(widgets, layout).toString()).apply()
    }

    /** A tiling dropped into place in edit mode. The widget list follows its reading order. */
    fun arrange(tree: TileNode) {
        val order = TileTree.leaves(tree)
        update(widgets.sortedBy { w -> order.indexOf(w.id).let { if (it < 0) Int.MAX_VALUE else it } }, tree)
    }

    /** Back to the automatic arrangement. */
    fun autoArrange() {
        update(widgets, null)
    }

    fun edit(id: Int, change: (DashWidget) -> DashWidget) {
        update(widgets.map { if (it.id == id) change(it) else it })
    }

    fun add(type: DashWidgetType) {
        if (widgets.size >= DashDefaults.MAX_WIDGETS) return
        update(widgets + DashDefaults.fresh(type, widgets))
    }

    fun remove(id: Int) {
        update(widgets.filterNot { it.id == id })
    }

    fun move(from: Int, to: Int) {
        if (from !in widgets.indices || to !in widgets.indices) return
        val list = widgets.toMutableList().apply { add(to, removeAt(from)) }
        val ids = list.map { it.id }
        // Reordering the list reorders the hand-made tiling the same way: its shape stays, the tiles
        // take its places in the new reading order.
        val tree = layout?.let { TileTree.effective(it, ids, false) }?.let { TileTree.relabel(it, ids) }
        update(list, tree)
    }

    fun reset() {
        update(DashDefaults.widgets(), null)
    }

    private fun load(): Pair<List<DashWidget>, TileNode?> {
        val raw = prefs.getString(KEY, null) ?: return DashDefaults.widgets() to null
        return runCatching {
            val o = JSONObject(raw)
            decode(o) to o.optJSONObject("layout")?.let { TileTreeJson.decode(it) }
        }.getOrNull() ?: (DashDefaults.widgets() to null)
    }

    private companion object {
        const val KEY = "layout"
        const val VERSION = 1

        fun encode(list: List<DashWidget>, layout: TileNode?): JSONObject {
            val arr = JSONArray()
            for (w in list) {
                arr.put(
                    JSONObject()
                        .put("id", w.id)
                        .put("type", w.type.id)
                        .put("size", w.size.id)
                        .put("rank", w.rank)
                        .put("streak", w.streakId)
                        .put("vars", JSONArray(w.variables))
                        .put("shortcut", w.shortcut.id)
                        .put("workout", w.workoutId)
                        .put("cal", w.calorieView.id)
                        .put("model", w.model.id)
                )
            }
            val out = JSONObject().put("v", VERSION).put("widgets", arr)
            layout?.let { out.put("layout", TileTreeJson.encode(it)) }
            return out
        }

        fun decode(o: JSONObject): List<DashWidget> {
            val arr = o.optJSONArray("widgets") ?: return DashDefaults.widgets()
            val out = ArrayList<DashWidget>()
            val ids = HashSet<Int>()
            for (i in 0 until arr.length()) {
                val w = arr.optJSONObject(i) ?: continue
                val type = DashWidgetType.fromId(w.optString("type")) ?: continue
                val id = w.optInt("id", -1)
                if (id < 0 || !ids.add(id)) continue
                val vars = w.optJSONArray("vars")
                out += DashWidget(
                    id = id,
                    type = type,
                    size = DashSize.fromId(w.optString("size")),
                    rank = w.optInt("rank", 1).coerceIn(1, DashDefaults.MAX_RANK),
                    streakId = w.optLong("streak", -1L),
                    variables = if (vars == null) emptyList() else (0 until vars.length()).mapNotNull { vars.optString(it).takeIf { s -> s.isNotEmpty() } }.take(DashDefaults.MAX_VARIABLES),
                    shortcut = DashShortcut.fromId(w.optString("shortcut")),
                    workoutId = w.optLong("workout", -1L),
                    calorieView = DashCalorieView.fromId(w.optString("cal")),
                    model = DashModel.fromId(w.optString("model"))
                )
                if (out.size >= DashDefaults.MAX_WIDGETS) break
            }
            return out
        }
    }
}
