package com.ashrdev.aznd.ui.dashboard

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

class DashboardStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("dashboard", Context.MODE_PRIVATE)

    var widgets: List<DashWidget> by mutableStateOf(load())
        private set

    fun update(list: List<DashWidget>) {
        widgets = list.take(DashDefaults.MAX_WIDGETS)
        prefs.edit().putString(KEY, encode(widgets).toString()).apply()
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
        update(widgets.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun reset() {
        update(DashDefaults.widgets())
    }

    private fun load(): List<DashWidget> {
        val raw = prefs.getString(KEY, null) ?: return DashDefaults.widgets()
        return runCatching { decode(JSONObject(raw)) }.getOrNull() ?: DashDefaults.widgets()
    }

    private companion object {
        const val KEY = "layout"
        const val VERSION = 1

        fun encode(list: List<DashWidget>): JSONObject {
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
            return JSONObject().put("v", VERSION).put("widgets", arr)
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
