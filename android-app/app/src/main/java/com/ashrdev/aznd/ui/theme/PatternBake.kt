package com.ashrdev.aznd.ui.theme

import android.content.Context
import android.content.res.Configuration
import com.ashrdev.aznd.ui.common.share.FlexDefaultsStore
import com.ashrdev.aznd.ui.common.share.FlexPreset
import com.ashrdev.aznd.ui.common.share.FlexPresetStore
import com.ashrdev.aznd.ui.common.share.FlexSpec
import com.ashrdev.aznd.ui.common.share.FlexStore
import com.ashrdev.aznd.ui.common.share.FLEX_DEFAULT_SOURCES
import com.ashrdev.aznd.ui.dashboard.DashboardStore
import kotlinx.coroutines.runBlocking
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.Locale

/**
 * Pattern tiles kept as files in the cache folder, so a cold start loads them instead of working them out again.
 *
 * A file is named from what the tile is (kind, tint if the kind is tinted, seed, [PatternMath.GENERATOR_VERSION]), not
 * from the theme that asked for it, so every theme, dashboard look and Flex card that uses the same tile shares one
 * file. It holds the width and height as two ints, then the ARGB pixels as ints: loading needs no decoding.
 */
object PatternBake {
    private const val DIR = "pattern_bake"
    private const val EXT = ".px"
    private const val TEMP = ".tmp"
    private const val MAX_SIDE = 4096
    private const val RECENT_MS = 14L * 24 * 60 * 60 * 1000
    private const val STALE_TEMP_MS = 10L * 60 * 1000

    /** How long after the app starts the first sweep waits, so it does not compete with the first screens. */
    const val FIRST_SWEEP_DELAY_MS = 8_000L

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var dir: File? = null

    /** One tile: [tint] is already 0 for a kind that is not tinted. */
    class TileKey(val kind: PatternKind, tint: Int, val seed: Int) {
        val tint: Int = if (kind.tinted) tint and 0xFFFFFF else 0
        val name: String get() = nameOf(kind, tint, seed)
        override fun equals(other: Any?): Boolean = other is TileKey && other.kind == kind && other.tint == tint && other.seed == seed
        override fun hashCode(): Int = (kind.ordinal * 31 + tint) * 31 + seed
    }

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        dir = File(app.cacheDir, DIR)
    }

    /** The file name (without extension) of a tile; also the key of the memory cache. */
    fun nameOf(kind: PatternKind, tint: Int, seed: Int): String {
        val colour = if (kind.tinted) String.format(Locale.US, "%06X", tint and 0xFFFFFF) else "0"
        return "${kind.name}_${colour}_${seed}_v${PatternMath.GENERATOR_VERSION}"
    }

    /** The baked pixels, or null when there is no file or it is not a whole, sane one. */
    fun load(name: String): PatternPixels? {
        val d = dir ?: return null
        val f = File(d, name + EXT)
        return try {
            if (!f.isFile) return null
            val len = f.length()
            if (len < 8 || len > Int.MAX_VALUE) return null
            val bytes = ByteArray(len.toInt())
            DataInputStream(FileInputStream(f)).use { it.readFully(bytes) }
            val buf = ByteBuffer.wrap(bytes)
            val w = buf.getInt(0)
            val h = buf.getInt(4)
            if (w <= 0 || h <= 0 || w > MAX_SIDE || h > MAX_SIDE) return null
            if (len != 8L + w.toLong() * h * 4L) return null
            val px = IntArray(w * h)
            buf.position(8)
            buf.asIntBuffer().get(px)
            PatternPixels(w, h, px)
        } catch (_: Exception) {
            null
        }
    }

    /** Writes the tile; a failure only means it is made again next time. */
    fun save(name: String, p: PatternPixels) {
        val d = dir ?: return
        val temp = File(d, "$name.${System.nanoTime()}$TEMP")
        try {
            d.mkdirs()
            val buf = ByteBuffer.allocate(8 + p.px.size * 4)
            buf.putInt(p.w)
            buf.putInt(p.h)
            buf.asIntBuffer().put(p.px)
            FileOutputStream(temp).use { it.write(buf.array()) }
            if (!temp.renameTo(File(d, name + EXT))) temp.delete()
        } catch (_: Exception) {
            temp.delete()
        }
    }

    // ------------------------------------------------------------------------------------------
    // Which tiles a look needs
    // ------------------------------------------------------------------------------------------

    private fun addTiles(out: MutableCollection<TileKey>, patterns: List<PatternSpec>, activeOnly: Boolean) {
        for (p in patterns) {
            if (p.kind == PatternKind.NONE || (activeOnly && !p.isActive)) continue
            for (l in PatternMath.layers(p.kind)) out += TileKey(p.kind, p.tint, p.seed + l.seedShift)
        }
    }

    private fun addTiles(out: MutableCollection<TileKey>, palette: Palette, activeOnly: Boolean) {
        for (fill in palette.fills.values) addTiles(out, fill.patterns, activeOnly)
    }

    /** The tiles [settings] draws with: the palette that is showing now first, then the other. */
    fun themeTiles(settings: ThemeSettings, activeOnly: Boolean = true): Set<TileKey> {
        val out = LinkedHashSet<TileKey>()
        val dark = when (settings.mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> {
                val ui = appContext?.resources?.configuration?.uiMode ?: 0
                (ui and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
        }
        val first = if (dark) settings.dark else settings.light
        val second = if (dark) settings.light else settings.dark
        addTiles(out, first, activeOnly)
        addTiles(out, second, activeOnly)
        return out
    }

    private fun addTiles(out: MutableCollection<TileKey>, spec: FlexSpec) {
        addTiles(out, spec.style.bgPatterns, false)
        addTiles(out, spec.style.panelFill.patterns, false)
    }

    /** Adds what the dashboard look and the Flex stores use; false if one of them could not be read. */
    private fun addOtherTiles(out: MutableCollection<TileKey>, context: Context): Boolean {
        var complete = true
        // Reading only: the constructors and getters below do not write or touch the UI.
        runCatching {
            val look = DashboardStore(context).look
            addTiles(out, look.bgPatterns, false)
            addTiles(out, look.tileFill.patterns, false)
        }.onFailure { complete = false }
        runCatching {
            for (preset in FlexPresetStore(context).all()) {
                addTiles(out, preset.spec)
                for (old in preset.history) addTiles(out, old)
            }
        }.onFailure { complete = false }
        runCatching {
            // load() is a suspend function that never suspends: it only reads SharedPreferences.
            for (source in FLEX_DEFAULT_SOURCES) addTiles(out, runBlocking { FlexDefaultsStore.load(context, source) })
        }.onFailure { complete = false }
        runCatching {
            val store = FlexStore(context)
            for (preset in FlexPreset.entries) store.load(preset)?.let { addTiles(out, it) }
        }.onFailure { complete = false }
        return complete
    }

    /**
     * Deletes the baked files nothing uses any more: not the committed theme's (light and dark), the dashboard look's
     * or the Flex cards', and not one that is being loaded or made right now. If one of the stores cannot be read, a
     * file touched in the last 14 days stays. Runs on a background thread.
     */
    fun sweep(settings: ThemeSettings) {
        val context = appContext ?: return
        val d = dir ?: return
        val files = d.listFiles() ?: return
        val keep = HashSet<String>()
        for (t in themeTiles(settings, activeOnly = false)) keep += t.name
        val others = LinkedHashSet<TileKey>()
        val complete = addOtherTiles(others, context)
        for (t in others) keep += t.name
        val now = System.currentTimeMillis()
        for (f in files) {
            val n = f.name
            if (n.endsWith(TEMP)) {
                if (now - f.lastModified() > STALE_TEMP_MS) f.delete()
                continue
            }
            if (!n.endsWith(EXT)) continue
            val base = n.removeSuffix(EXT)
            if (base in keep || PatternTiles.isBusy(base)) continue
            if (!complete && now - f.lastModified() < RECENT_MS) continue
            f.delete()
        }
    }

    /** Brings the committed theme's tiles into memory (baking the new ones), then sweeps once they are done. */
    fun afterCommit(settings: ThemeSettings) {
        PatternTiles.prefetch(themeTiles(settings))
        PatternTiles.background(0L) { sweep(ThemeStore.settings.value) }
    }

    /** At start: loads (or bakes) the tiles of the saved theme, and sweeps a little later. */
    fun prewarm(settings: ThemeSettings) {
        PatternTiles.prefetch(themeTiles(settings))
        PatternTiles.background(FIRST_SWEEP_DELAY_MS) { sweep(ThemeStore.settings.value) }
    }
}
