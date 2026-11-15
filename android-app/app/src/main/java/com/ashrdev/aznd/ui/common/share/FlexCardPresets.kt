package com.ashrdev.aznd.ui.common.share

import android.content.Context
import com.ashrdev.aznd.ui.common.tiling.TileNode
import com.ashrdev.aznd.ui.common.tiling.TileTree
import org.json.JSONArray
import org.json.JSONObject

/**
 * A saved, named card design the person made (a template). Its [spec] is a whole card; photo blocks that show
 * pictures of a screen are kept pointing at the placeholder pictures of [FlexSampleContent] and are re-pointed at
 * real ones by [appliedTo] when the card is made for a share source. [id] 0 or less means "not stored yet".
 */
data class FlexCardPreset(val id: Long, val name: String, val spec: FlexSpec, val updatedAt: Long)

/**
 * Keeps the person's [FlexCardPreset]s. Plain SharedPreferences with one JSON array string, written with the same
 * spec encoding as [FlexStore].
 */
class FlexPresetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Every saved preset, the most recently updated first. */
    fun all(): List<FlexCardPreset> = synchronized(LOCK) { read().sortedByDescending { it.updatedAt } }

    fun get(id: Long): FlexCardPreset? = synchronized(LOCK) { read().firstOrNull { it.id == id } }

    /**
     * Stores [preset] (replacing the one with its id) and returns what was stored. An id of 0 or less gets a new id;
     * [FlexCardPreset.updatedAt] is always set to now.
     */
    fun save(preset: FlexCardPreset): FlexCardPreset = synchronized(LOCK) {
        val list = read().toMutableList()
        val id = if (preset.id > 0) preset.id else nextId(list)
        val stored = preset.copy(id = id, updatedAt = System.currentTimeMillis())
        val at = list.indexOfFirst { it.id == id }
        if (at >= 0) list[at] = stored else list += stored
        write(list)
        stored
    }

    fun delete(id: Long) = synchronized(LOCK) {
        write(read().filter { it.id != id })
    }

    /** A copy named "<name> copy" with a new id, null when [id] is not stored. */
    fun duplicate(id: Long): FlexCardPreset? {
        val source = get(id) ?: return null
        return save(source.copy(id = 0, name = "${source.name} copy"))
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
                        add(
                            FlexCardPreset(
                                id = o.getLong("id"),
                                name = o.optString("name", ""),
                                spec = specFromJson(o.getJSONObject("spec")),
                                updatedAt = o.optLong("updatedAt", 0L)
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
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("updatedAt", p.updatedAt)
                    .put("spec", specToJson(p.spec))
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

/**
 * A template spec fitted to real content. A photo block shows its picture from one of three places:
 *  - no keys: the share's own photo ([FlexContent.photoUri]); that follows the content by itself and is left alone;
 *  - keys + [FLEX_SCREEN_PHOTO]: one of the screen's pictures ([FlexContent.photos]);
 *  - keys, no marker: a picture the person picked themselves; real, so it is kept as it is.
 * A block whose picture is one of the placeholder pictures ([FlexSampleContent.isSampleUri]) is re-pointed, in
 * block order, at the content's pictures: the screen's own photo first (unless an enabled photo block without keys
 * already shows it), then [FlexContent.photos]; pictures a block of the person's already shows are skipped. Blocks
 * left over when the content runs out of pictures are dropped. A background photo that is a placeholder is cleared,
 * so the card's background follows the content's own photo again. The result is [reconciled].
 */
fun FlexSpec.appliedTo(content: FlexContent): FlexSpec {
    val ownShown = blocks.any { it.type == FlexBlockType.PHOTO && it.enabled && it.keys == null }
    val taken = blocks.mapNotNull { b ->
        if (b.type == FlexBlockType.PHOTO) b.keys?.firstOrNull()?.takeIf { !FlexSampleContent.isSampleUri(it) } else null
    }.toSet()
    val pool = buildList {
        if (!ownShown) content.photoUri?.let { add(it) }
        content.photos.forEach { add(it.uri) }
    }.distinct().filter { it !in taken }
    val next = pool.iterator()
    val fitted = blocks.mapNotNull { b ->
        if (b.type == FlexBlockType.PHOTO && FlexSampleContent.isSampleUri(b.keys?.firstOrNull())) {
            if (next.hasNext()) b.copy(keys = listOf(next.next()), text = FLEX_SCREEN_PHOTO) else null
        } else b
    }
    val look = if (FlexSampleContent.isSampleUri(style.photoUri)) style.copy(photoUri = null) else style
    return copy(style = look, blocks = fitted, layout = prunedLayout(fitted)).reconciled()
}

/**
 * The inverse, for making a preset from a real card: each screen-picture block ([FLEX_SCREEN_PHOTO] with keys) is
 * re-pointed, in block order, at the placeholder pictures ([FlexContent.photos] of [sample]); blocks beyond the
 * placeholders are dropped. Photo blocks without keys already follow the content, and a picture the person picked
 * themselves is real and stays. Item choices ([FlexBlock.keys] of lists) and the background photo are kept: the
 * choices fall back to automatic where the content has no such item, and a background photo is only ever a picture the
 * person chose (a share's own photo is used while it is not set), so it stays too. The result is [reconciled].
 */
fun FlexSpec.asTemplate(sample: FlexContent): FlexSpec {
    val next = sample.photos.map { it.uri }.iterator()
    val fitted = blocks.mapNotNull { b ->
        val isScreen = b.type == FlexBlockType.PHOTO && b.text == FLEX_SCREEN_PHOTO && b.keys != null
        if (isScreen && !FlexSampleContent.isSampleUri(b.keys?.firstOrNull())) {
            if (next.hasNext()) b.copy(keys = listOf(next.next())) else null
        } else b
    }
    return copy(blocks = fitted, layout = prunedLayout(fitted)).reconciled()
}

/** The tiling without the tiles of blocks that were dropped, so no tile points at a block the card no longer has. */
private fun FlexSpec.prunedLayout(kept: List<FlexBlock>): TileNode? =
    if (kept.size == blocks.size) layout else layout?.let { TileTree.prune(it, kept.map { b -> b.id }.toSet()) }
