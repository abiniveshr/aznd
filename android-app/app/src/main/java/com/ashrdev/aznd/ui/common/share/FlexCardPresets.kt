package com.ashrdev.aznd.ui.common.share

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A saved, named card design (a template). Its [spec] is a whole poster laid out on sample data; the real data it
 * shows when used comes from the person's choices ([FlexBindings]), not from the preset.
 * [id] 0 or less means "not stored yet". [shippedKey] is the stable key of the built-in preset ([FlexComparisonPreset.key]) this
 * one is, null for the person's own presets; built-in ones cannot be deleted and can be reset to the spec they ship with.
 * [history] holds the specs the preset had before its last updates, newest first, at most [FLEX_PRESET_HISTORY].
 */
data class FlexCardPreset(
    val id: Long,
    val name: String,
    val spec: FlexSpec,
    val updatedAt: Long,
    val shippedKey: String? = null,
    val history: List<FlexSpec> = emptyList()
) {
    /** True for a built-in preset (not deletable, resettable). */
    val isShipped: Boolean get() = shippedKey != null
}

/** How many earlier versions of a preset are kept for undo. */
const val FLEX_PRESET_HISTORY = 3

/**
 * Keeps the [FlexCardPreset]s: the built-in ones (the comparison presets, added by [ensureShipped]) and the person's
 * own. Plain SharedPreferences with one JSON array string, written with the same spec encoding as [FlexStore].
 */
class FlexPresetStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Every preset: the built-in ones first (in the order of the share sources), then the person's, most recently updated first. */
    fun all(): List<FlexCardPreset> = synchronized(LOCK) {
        val order = flexComparisonPresets().map { it.key }
        val (builtIn, own) = read().partition { it.shippedKey != null }
        builtIn.sortedBy { order.indexOf(it.shippedKey) } + own.sortedByDescending { it.updatedAt }
    }

    fun get(id: Long): FlexCardPreset? = synchronized(LOCK) { read().firstOrNull { it.id == id } }

    /**
     * Adds the built-in preset of every comparison preset that has none yet (see [flexComparisonPresets]); does nothing once they are
     * all there. Nothing to load (the specs are plain data); it stays a suspend function for its callers.
     */
    suspend fun ensureShipped() {
        val all = flexComparisonPresets()
        synchronized(LOCK) {
            val list = read().toMutableList()
            val have = list.mapNotNull { it.shippedKey }.toSet()
            val missing = all.filter { it.key !in have }
            if (missing.isEmpty()) return
            val now = System.currentTimeMillis()
            missing.forEach { p ->
                list += FlexCardPreset(id = nextId(list), name = p.name, spec = p.spec, updatedAt = now, shippedKey = p.key)
            }
            write(list)
        }
    }

    /**
     * Stores [preset] (replacing the one with its id) and returns what was stored. An id of 0 or less gets a new id;
     * [FlexCardPreset.updatedAt] is always set to now. When the stored preset's spec changes, the spec it had goes on
     * its history (newest first, at most [FLEX_PRESET_HISTORY]); the history and [FlexCardPreset.shippedKey] of the stored
     * preset are kept, whatever [preset] carries.
     */
    fun save(preset: FlexCardPreset): FlexCardPreset = synchronized(LOCK) {
        val list = read().toMutableList()
        val id = if (preset.id > 0) preset.id else nextId(list)
        val at = list.indexOfFirst { it.id == id }
        val old = list.getOrNull(at)
        val history = when {
            old == null -> preset.history.take(FLEX_PRESET_HISTORY)
            old.spec != preset.spec -> (listOf(old.spec) + old.history).take(FLEX_PRESET_HISTORY)
            else -> old.history
        }
        val stored = preset.copy(
            id = id, updatedAt = System.currentTimeMillis(), history = history,
            shippedKey = if (old != null) old.shippedKey else preset.shippedKey
        )
        if (at >= 0) list[at] = stored else list += stored
        write(list)
        stored
    }

    /** Deletes the person's preset [id]; a built-in preset (or an unknown id) is left alone. True when it was deleted. */
    fun delete(id: Long): Boolean = synchronized(LOCK) {
        val list = read()
        val target = list.firstOrNull { it.id == id }
        if (target == null || target.shippedKey != null) return@synchronized false
        write(list.filter { it.id != id })
        true
    }

    /** Puts the preset back to the spec before its last update (taken off its history). Null when there is none. */
    fun undo(id: Long): FlexCardPreset? = synchronized(LOCK) {
        val list = read().toMutableList()
        val at = list.indexOfFirst { it.id == id }
        val old = list.getOrNull(at) ?: return@synchronized null
        val previous = old.history.firstOrNull() ?: return@synchronized null
        val stored = old.copy(spec = previous, history = old.history.drop(1), updatedAt = System.currentTimeMillis())
        list[at] = stored
        write(list)
        stored
    }

    /** Puts a built-in preset back to the spec it ships with (its history is cleared). Null when [id] is not a built-in one. */
    suspend fun resetShipped(id: Long): FlexCardPreset? {
        val key = get(id)?.shippedKey ?: return null
        val original = flexComparisonPresets().firstOrNull { it.key == key }?.spec ?: return null
        return synchronized(LOCK) {
            val list = read().toMutableList()
            val at = list.indexOfFirst { it.id == id }
            val old = list.getOrNull(at) ?: return@synchronized null
            val stored = old.copy(spec = original, history = emptyList(), updatedAt = System.currentTimeMillis())
            list[at] = stored
            write(list)
            stored
        }
    }

    /** A copy of the person's or a built-in preset named "<name> copy" with a new id (never built-in), null when [id] is not stored. */
    fun duplicate(id: Long): FlexCardPreset? {
        val source = get(id) ?: return null
        return save(source.copy(id = 0, name = "${source.name} copy", shippedKey = null, history = emptyList()))
    }

    /** "Preset N": the first number not used by a name already saved. */
    fun nextDefaultName(): String {
        val names = all().map { it.name }.toSet()
        var n = names.size + 1
        while ("Preset $n" in names) n++
        return "Preset $n"
    }

    private fun nextId(list: List<FlexCardPreset>): Long {
        val next = maxOf(prefs.getLong(KEY_NEXT_ID, 1L), (list.maxOfOrNull { it.id } ?: 0L) + 1L)
        prefs.edit().putLong(KEY_NEXT_ID, next + 1L).apply()
        return next
    }

    private fun read(): List<FlexCardPreset> {
        val text = prefs.getString(KEY_PRESETS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(text)
            buildList {
                for (i in 0 until arr.length()) {
                    // One broken entry is skipped, not the whole list.
                    runCatching {
                        val o = arr.getJSONObject(i)
                        // The old per-source built-in presets (the app is unreleased) are dropped; the comparison presets replace them.
                        if (o.optString("shipped", "").isNotEmpty()) return@runCatching
                        add(
                            FlexCardPreset(
                                id = o.getLong("id"),
                                name = o.optString("name", ""),
                                spec = specFromJson(o.getJSONObject("spec")),
                                updatedAt = o.optLong("updatedAt", 0L),
                                shippedKey = o.optString("shippedKey", "").takeIf { it.isNotEmpty() },
                                history = buildList {
                                    val h = o.optJSONArray("history")
                                    if (h != null) for (k in 0 until h.length()) {
                                        runCatching { add(specFromJson(h.getJSONObject(k))) }
                                    }
                                }.take(FLEX_PRESET_HISTORY)
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun write(list: List<FlexCardPreset>) {
        val arr = JSONArray()
        list.forEach { p ->
            val history = JSONArray()
            p.history.forEach { history.put(specToJson(it)) }
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("updatedAt", p.updatedAt)
                    .put("spec", specToJson(p.spec))
                    .put("history", history)
                    .put("shippedKey", p.shippedKey ?: "")
            )
        }
        prefs.edit().putString(KEY_PRESETS, arr.toString()).apply()
    }

    private companion object {
        const val FILE = "flex_card_presets"
        const val KEY_PRESETS = "presets"
        const val KEY_NEXT_ID = "next_id"
        val LOCK = Any()
    }
}
