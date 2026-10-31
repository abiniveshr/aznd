package com.ashrdev.aznd.ui.dashboard

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ashrdev.aznd.ui.common.share.FlexFont
import com.ashrdev.aznd.ui.common.tiling.TileNode
import com.ashrdev.aznd.ui.common.tiling.TileTree
import com.ashrdev.aznd.ui.common.tiling.TileTreeJson
import com.ashrdev.aznd.ui.theme.BorderKind
import com.ashrdev.aznd.ui.theme.fillStyleFromJson
import com.ashrdev.aznd.ui.theme.gradientFromJson
import com.ashrdev.aznd.ui.theme.patternsFromJson
import com.ashrdev.aznd.ui.theme.taperLineFromJson
import com.ashrdev.aznd.ui.theme.toJson
import com.ashrdev.aznd.ui.theme.toJsonArray
import org.json.JSONArray
import org.json.JSONObject

/**
 * The dashboard: its saved presets, which of them are shown as pages (top to bottom), and the one
 * look every page shares. Saved as one JSON document; the old single-dashboard save becomes the
 * first preset.
 */
class DashboardStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("dashboard", Context.MODE_PRIVATE)
    private val loaded = load()

    var presets: List<DashPreset> by mutableStateOf(loaded.presets)
        private set

    /** Preset ids shown as dashboard pages, top to bottom. Never empty while a preset exists. */
    var active: List<Int> by mutableStateOf(loaded.active)
        private set

    var look: DashLook by mutableStateOf(loaded.look)
        private set

    fun preset(id: Int): DashPreset? = presets.firstOrNull { it.id == id }

    val activePresets: List<DashPreset> get() = active.mapNotNull { preset(it) }

    /** Every widget on every page that is shown. */
    val shownWidgets: List<DashWidget> get() = activePresets.flatMap { it.widgets }

    // ---- presets ----

    fun newPreset(name: String, start: DashPresetStart): Int? {
        if (presets.size >= DashDefaults.MAX_PRESETS) return null
        val id = nextPresetId()
        val widgets = if (start == DashPresetStart.DEFAULTS) DashDefaults.widgets() else emptyList()
        save(presets + DashPreset(id, cleanName(name, id), widgets), active)
        return id
    }

    fun duplicate(id: Int): Int? {
        val from = preset(id) ?: return null
        if (presets.size >= DashDefaults.MAX_PRESETS) return null
        val copyId = nextPresetId()
        save(presets + from.copy(id = copyId, name = cleanName("${from.name} copy", copyId)), active)
        return copyId
    }

    fun rename(id: Int, name: String) {
        editPreset(id) { it.copy(name = cleanName(name, id)) }
    }

    /** The last preset can't be deleted. A deleted preset also leaves the pages it was shown on. */
    fun deletePreset(id: Int) {
        if (presets.size <= 1) return
        val left = presets.filterNot { it.id == id }
        save(left, active.filterNot { it == id })
    }

    // ---- pages (active presets) ----

    fun addPage(presetId: Int) {
        if (preset(presetId) == null || presetId in active) return
        save(presets, active + presetId)
    }

    fun replacePage(index: Int, presetId: Int) {
        if (index !in active.indices || preset(presetId) == null) return
        val list = active.toMutableList()
        val already = list.indexOf(presetId)
        // Picking a preset that is already a page swaps the two, so no page shows twice.
        if (already >= 0) list[already] = list[index]
        list[index] = presetId
        save(presets, list)
    }

    /** The dashboard always keeps one page. */
    fun removePage(index: Int) {
        if (index !in active.indices || active.size <= 1) return
        save(presets, active.toMutableList().apply { removeAt(index) })
    }

    fun movePage(from: Int, to: Int) {
        if (from !in active.indices || to !in active.indices) return
        save(presets, active.toMutableList().apply { add(to, removeAt(from)) })
    }

    // ---- widgets of one preset ----

    fun addWidget(presetId: Int, type: DashWidgetType): Int? {
        val p = preset(presetId) ?: return null
        if (p.widgets.size >= DashDefaults.MAX_WIDGETS) return null
        val fresh = DashDefaults.fresh(type, p.widgets)
        editPreset(presetId) { it.copy(widgets = it.widgets + fresh) }
        return fresh.id
    }

    fun editWidget(presetId: Int, id: Int, change: (DashWidget) -> DashWidget) {
        editPreset(presetId) { p -> p.copy(widgets = p.widgets.map { if (it.id == id) change(it) else it }) }
    }

    fun removeWidget(presetId: Int, id: Int) {
        editPreset(presetId) { p -> p.copy(widgets = p.widgets.filterNot { it.id == id }) }
    }

    fun moveWidget(presetId: Int, from: Int, to: Int) {
        editPreset(presetId) { p ->
            if (from !in p.widgets.indices || to !in p.widgets.indices) return@editPreset p
            val list = p.widgets.toMutableList().apply { add(to, removeAt(from)) }
            val ids = list.map { it.id }
            // Reordering the list reorders a hand-made tiling the same way: its shape stays, the
            // tiles take its places in the new reading order.
            val tree = p.layout?.let { TileTree.effective(it, ids, false) }?.let { TileTree.relabel(it, ids) }
            p.copy(widgets = list, layout = tree)
        }
    }

    /** A tiling dropped into place in edit mode. The widget list follows its reading order. */
    fun arrange(presetId: Int, tree: TileNode) {
        editPreset(presetId) { p ->
            val order = TileTree.leaves(tree)
            p.copy(
                widgets = p.widgets.sortedBy { w -> order.indexOf(w.id).let { if (it < 0) Int.MAX_VALUE else it } },
                layout = tree
            )
        }
    }

    /** Back to the tiles arranging themselves. */
    fun autoArrange(presetId: Int) {
        editPreset(presetId) { it.copy(layout = null) }
    }

    fun resetPreset(presetId: Int) {
        editPreset(presetId) { it.copy(widgets = DashDefaults.widgets(), layout = null) }
    }

    /**
     * Widget [id] carried from preset [from] to preset [to] while being dragged: it joins [to] as its
     * first row ([first]) or its last, under a new id. Returns that id, or null when [to] is full.
     */
    fun carry(from: Int, id: Int, to: Int, first: Boolean): Int? {
        val source = preset(from) ?: return null
        val target = preset(to) ?: return null
        val widget = source.widgets.firstOrNull { it.id == id } ?: return null
        if (from == to || target.widgets.size >= DashDefaults.MAX_WIDGETS) return null
        val newId = (target.widgets.maxOfOrNull { it.id } ?: 0) + 1
        val moved = widget.copy(id = newId)
        val list = presets.map { p ->
            when (p.id) {
                from -> tidy(p.copy(widgets = p.widgets.filterNot { it.id == id }))
                to -> tidy(p.copy(widgets = if (first) listOf(moved) + p.widgets else p.widgets + moved))
                else -> p
            }
        }
        save(list, active)
        return newId
    }

    // ---- look ----

    fun updateLook(change: (DashLook) -> DashLook) {
        look = change(look)
        persist()
    }

    fun resetLook() {
        updateLook { DashLook() }
    }

    // ---- saving ----

    private fun editPreset(id: Int, change: (DashPreset) -> DashPreset) {
        save(presets.map { if (it.id == id) tidy(change(it)) else it }, active)
    }

    /** Widgets capped, and a hand-made tiling kept to the widgets that are still there. */
    private fun tidy(p: DashPreset): DashPreset {
        val widgets = p.widgets.take(DashDefaults.MAX_WIDGETS)
        return p.copy(widgets = widgets, layout = p.layout?.let { TileTree.prune(it, widgets.map { w -> w.id }.toSet()) })
    }

    private fun save(list: List<DashPreset>, pages: List<Int>) {
        val ids = list.map { it.id }.toSet()
        presets = list
        active = pages.filter { it in ids }.distinct().ifEmpty { listOfNotNull(list.firstOrNull()?.id) }
        persist()
    }

    private fun persist() {
        prefs.edit().putString(KEY, encode(presets, active, look).toString()).apply()
    }

    private fun nextPresetId(): Int = (presets.maxOfOrNull { it.id } ?: 0) + 1

    private fun cleanName(name: String, id: Int): String =
        name.trim().take(DashDefaults.NAME_MAX).ifEmpty { "Page $id" }

    private class Loaded(val presets: List<DashPreset>, val active: List<Int>, val look: DashLook)

    private fun load(): Loaded {
        prefs.getString(KEY, null)?.let { raw ->
            runCatching { decode(JSONObject(raw)) }.getOrNull()?.let { return it }
        }
        // The single dashboard saved before presets: it becomes the first preset.
        val old = prefs.getString(OLD_KEY, null)?.let { raw ->
            runCatching {
                val o = JSONObject(raw)
                decodeWidgets(o.optJSONArray("widgets")) to o.optJSONObject("layout")?.let { TileTreeJson.decode(it) }
            }.getOrNull()
        }
        val first = DashPreset(
            1,
            DashDefaults.FIRST_PRESET_NAME,
            old?.first ?: DashDefaults.widgets(),
            old?.second
        )
        return Loaded(listOf(first), listOf(1), DashLook())
    }

    private companion object {
        const val KEY = "presets"
        const val OLD_KEY = "layout"
        const val VERSION = 2

        fun encode(presets: List<DashPreset>, active: List<Int>, look: DashLook): JSONObject {
            val arr = JSONArray()
            for (p in presets) {
                val o = JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("widgets", encodeWidgets(p.widgets))
                p.layout?.let { o.put("layout", TileTreeJson.encode(it)) }
                arr.put(o)
            }
            return JSONObject()
                .put("v", VERSION)
                .put("presets", arr)
                .put("active", JSONArray(active))
                .put("look", encodeLook(look))
        }

        fun decode(o: JSONObject): Loaded? {
            val arr = o.optJSONArray("presets") ?: return null
            val presets = ArrayList<DashPreset>()
            val ids = HashSet<Int>()
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val id = p.optInt("id", -1)
                if (id < 0 || !ids.add(id)) continue
                val widgets = decodeWidgets(p.optJSONArray("widgets"))
                val layout = p.optJSONObject("layout")?.let { TileTreeJson.decode(it) }
                    ?.let { TileTree.prune(it, widgets.map { w -> w.id }.toSet()) }
                presets += DashPreset(id, p.optString("name").ifEmpty { "Page $id" }, widgets, layout)
                if (presets.size >= DashDefaults.MAX_PRESETS) break
            }
            if (presets.isEmpty()) return null
            val pages = o.optJSONArray("active")
            val active = (0 until (pages?.length() ?: 0)).map { pages!!.optInt(it, -1) }.filter { it in ids }.distinct()
            val look = runCatching { decodeLook(o.optJSONObject("look")) }.getOrNull() ?: DashLook()
            return Loaded(presets, active.ifEmpty { listOf(presets.first().id) }, look)
        }

        fun encodeWidgets(list: List<DashWidget>): JSONArray {
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
            return arr
        }

        fun decodeWidgets(arr: JSONArray?): List<DashWidget> {
            if (arr == null) return DashDefaults.widgets()
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

        fun encodeLook(l: DashLook): JSONObject = JSONObject()
            .put("bg", l.bgMode.id)
            .put("bgA", l.bgA)
            .put("bgB", l.bgB)
            .put("angle", l.gradientAngle)
            .apply { l.bgGradient?.let { put("gradient", it.toJson()) } }
            .put("patterns", l.bgPatterns.toJsonArray())
            .apply { l.photoUri?.let { put("photo", it) } }
            .put("blur", l.photoBlur.toDouble())
            .put("dim", l.photoDim.toDouble())
            .put("custom", l.customColors)
            .put("text", l.textColor)
            .put("accent", l.accent)
            .put("tileColor", l.tileColor)
            .put("font", l.font.name)
            .put("textScale", l.textScale.toDouble())
            .put("module", l.moduleScale.toDouble())
            .put("side", l.side.toDouble())
            .put("top", l.top.toDouble())
            .put("gap", l.gap.toDouble())
            .put("tiles", l.tiles.id)
            .put("tileAlpha", l.tileAlpha.toDouble())
            .put("tileFill", l.tileFill.toJson())
            .put("borderKind", l.tileBorderKind.name)
            .put("border", l.tileBorder.toDouble())
            .put("borderColor", l.tileBorderColor)
            .put("radius", l.tileRadius.toDouble())
            .put("taper", l.lineTaper.toJson())

        fun decodeLook(o: JSONObject?): DashLook {
            if (o == null) return DashLook()
            val d = DashLook()
            fun f(key: String, def: Float, range: ClosedFloatingPointRange<Float>): Float =
                o.optDouble(key, def.toDouble()).toFloat().let { if (it.isFinite()) it.coerceIn(range) else def }
            return DashLook(
                bgMode = DashBgMode.fromId(o.optString("bg")),
                bgA = o.optInt("bgA", d.bgA),
                bgB = o.optInt("bgB", d.bgB),
                gradientAngle = o.optInt("angle", d.gradientAngle).coerceIn(0, 360),
                bgGradient = o.optJSONObject("gradient")?.let { gradientFromJson(it) },
                bgPatterns = patternsFromJson(o.optJSONArray("patterns")),
                photoUri = o.optString("photo").takeIf { it.isNotEmpty() },
                photoBlur = f("blur", d.photoBlur, 0f..1f),
                photoDim = f("dim", d.photoDim, 0f..0.9f),
                customColors = o.optBoolean("custom", false),
                textColor = o.optInt("text", d.textColor),
                accent = o.optInt("accent", d.accent),
                tileColor = o.optInt("tileColor", d.tileColor),
                font = FlexFont.entries.firstOrNull { it.name == o.optString("font") } ?: FlexFont.SANS,
                textScale = f("textScale", 1f, LOOK_TEXT_MIN..LOOK_TEXT_MAX),
                moduleScale = f("module", 1f, LOOK_MODULE_MIN..LOOK_MODULE_MAX),
                side = f("side", d.side, 0f..LOOK_MARGIN_MAX),
                top = f("top", d.top, 0f..LOOK_MARGIN_MAX),
                gap = f("gap", d.gap, LOOK_GAP_MIN..LOOK_GAP_MAX),
                tiles = DashTileStyle.fromId(o.optString("tiles")),
                tileAlpha = f("tileAlpha", d.tileAlpha, 0.04f..1f),
                tileFill = fillStyleFromJson(o.optJSONObject("tileFill")),
                tileBorderKind = BorderKind.entries.firstOrNull { it.name == o.optString("borderKind") } ?: BorderKind.OUTLINE,
                tileBorder = f("border", 0f, 0f..LOOK_BORDER_MAX),
                tileBorderColor = o.optInt("borderColor", d.tileBorderColor),
                tileRadius = f("radius", d.tileRadius, 0f..LOOK_RADIUS_MAX),
                lineTaper = taperLineFromJson(o.optJSONObject("taper"))
            )
        }
    }
}

// Ranges of the Look page's sliders (also what a saved look is held to).
internal const val LOOK_TEXT_MIN = 0.7f
internal const val LOOK_TEXT_MAX = 1.5f
internal const val LOOK_MODULE_MIN = 0.7f
internal const val LOOK_MODULE_MAX = 1.4f
internal const val LOOK_MARGIN_MAX = 40f
internal const val LOOK_GAP_MIN = 4f
internal const val LOOK_GAP_MAX = 32f
internal const val LOOK_BORDER_MAX = 8f
internal const val LOOK_RADIUS_MAX = 40f
