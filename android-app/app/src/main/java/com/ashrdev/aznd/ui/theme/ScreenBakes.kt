package com.ashrdev.aznd.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.IntSize
import com.ashrdev.aznd.ui.dashboard.DashboardStore
import com.ashrdev.aznd.ui.settings.PatternMode
import com.ashrdev.aznd.ui.settings.PerformanceStore
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

typealias PatternSet = List<PatternSpec>

data class BakeSize(val width: Int, val height: Int, val density: Float)

val LocalFillField: ProvidableCompositionLocal<IntSize> = compositionLocalOf { IntSize.Zero }

object ScreenBakes {
    private const val DIR = "screen_bake"
    private const val EXT = ".bake"
    private const val TEMP = ".tmp"
    private const val SIZES = "sizes"
    private const val MAGIC = 0x42414B31
    private const val MAX_SIDE = 16384
    private const val MAX_BYTES = 256L * 1024 * 1024
    private const val MAX_FOLDER_BYTES = 96L * 1024 * 1024
    private const val STALE_TEMP_MS = 10L * 60 * 1000
    private const val MAX_SIZES = 8
    private const val IO_BUFFER = 64 * 1024

    private class BakeKey(val size: BakeSize, val set: PatternSet) {
        override fun equals(other: Any?): Boolean = other is BakeKey && other.size == size && other.set == set
        override fun hashCode(): Int = size.hashCode() * 31 + set.hashCode()
    }

    private class Pending {
        val waiters = ArrayList<CancellableContinuation<Unit>>()
    }

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var dir: File? = null

    private val lock = Any()
    private val memory = HashMap<BakeKey, Bitmap>()
    private val inFlight = HashMap<BakeKey, Pending>()
    private val sizesLock = Any()
    private val version = mutableIntStateOf(0)

    private val sizeState = mutableStateOf<BakeSize?>(null)
    private val themeRequired = mutableStateOf<Set<PatternSet>>(emptySet())
    private val owners = LinkedHashMap<Any, Set<PatternSet>>()
    private val requiredState = mutableStateOf<Set<PatternSet>>(emptySet())

    private val mainHandler = Handler(Looper.getMainLooper())

    private val executor: ThreadPoolExecutor = run {
        val n = max(1, Runtime.getRuntime().availableProcessors() - 1)
        ThreadPoolExecutor(n, n, 30L, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>()) { r ->
            Thread(r, "screen-bake").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }.apply { allowCoreThreadTimeOut(true) }
    }

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        dir = File(app.filesDir, DIR)
    }

    var size: BakeSize?
        get() = sizeState.value
        set(value) {
            sizeState.value = value
        }

    fun setOf(style: FillStyle): PatternSet? {
        val active = style.activePatterns
        return if (active.isEmpty()) null else active
    }

    fun themeSets(settings: ThemeSettings, dark: Boolean): Set<PatternSet> {
        if (settings.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return emptySet()
        val palette = if (dark) settings.dark else settings.light
        val out = LinkedHashSet<PatternSet>()
        for (element in ThemeColor.entries) {
            if (!element.fillable) continue
            setOf(palette.fillOf(element))?.let { out += it }
        }
        return out
    }

    fun systemDark(): Boolean {
        val ui = appContext?.resources?.configuration?.uiMode ?: 0
        return (ui and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun readyFor(settings: ThemeSettings): Boolean {
        if (PerformanceStore.patternMode != PatternMode.BAKED) return true
        val dark = when (settings.mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> systemDark()
        }
        val sets = themeSets(settings, dark)
        if (sets.isEmpty()) return true
        val s = size ?: return false
        version.intValue
        return synchronized(lock) { sets.all { memory.containsKey(BakeKey(s, it)) } }
    }

    fun setThemeRequired(sets: Set<PatternSet>) {
        themeRequired.value = sets
        refreshRequired()
    }

    fun require(owner: Any, sets: Set<PatternSet>) {
        synchronized(owners) { owners[owner] = sets }
        refreshRequired()
    }

    fun release(owner: Any) {
        synchronized(owners) { owners.remove(owner) }
        refreshRequired()
    }

    private fun refreshRequired() {
        val out = LinkedHashSet<PatternSet>(themeRequired.value)
        synchronized(owners) { for (v in owners.values) out += v }
        requiredState.value = out
    }

    val required: Set<PatternSet> get() = requiredState.value

    internal fun isRequired(set: PatternSet): Boolean = set in requiredState.value

    internal fun bakeFor(set: PatternSet, size: BakeSize): Bitmap? {
        version.intValue
        return synchronized(lock) { memory[BakeKey(size, set)] }
    }

    fun missing(sets: Set<PatternSet>, size: BakeSize): Set<PatternSet> {
        version.intValue
        return synchronized(lock) { sets.filterTo(LinkedHashSet()) { !memory.containsKey(BakeKey(size, it)) } }
    }

    suspend fun ensure(sets: Set<PatternSet>, size: BakeSize, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }) {
        val todo = missing(sets, size).toList()
        if (todo.isEmpty()) return
        val total = todo.size
        val done = AtomicInteger(0)
        coroutineScope {
            for (set in todo) {
                launch {
                    await(BakeKey(size, set))
                    val n = done.incrementAndGet()
                    onMain { onProgress(n, total) }
                }
            }
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }

    private suspend fun await(key: BakeKey) {
        suspendCancellableCoroutine<Unit> { cont ->
            var ready = false
            var begin = false
            var pending: Pending? = null
            synchronized(lock) {
                if (memory.containsKey(key)) {
                    ready = true
                } else {
                    val existing = inFlight[key]
                    if (existing != null) {
                        existing.waiters.add(cont)
                        pending = existing
                    } else {
                        val made = Pending()
                        made.waiters.add(cont)
                        inFlight[key] = made
                        pending = made
                        begin = true
                    }
                }
            }
            val p = pending
            if (ready || p == null) {
                cont.resume(Unit)
            } else {
                cont.invokeOnCancellation { synchronized(lock) { p.waiters.remove(cont) } }
                if (begin) {
                    try {
                        executor.execute { runBake(key, p) }
                    } catch (_: Throwable) {
                        finish(key, p, null, false)
                    }
                }
            }
        }
    }

    private fun runBake(key: BakeKey, pending: Pending) {
        var bitmap: Bitmap? = null
        var fresh = false
        try {
            bitmap = loadFile(key)
            if (bitmap == null) {
                bitmap = composite(key)
                fresh = true
            }
        } catch (_: Throwable) {
            bitmap = null
            fresh = false
        }
        finish(key, pending, bitmap, fresh)
    }

    private fun finish(key: BakeKey, pending: Pending, bitmap: Bitmap?, fresh: Boolean) {
        val waiters: List<CancellableContinuation<Unit>>
        synchronized(lock) {
            if (bitmap != null) memory[key] = bitmap
            inFlight.remove(key)
            waiters = ArrayList(pending.waiters)
            pending.waiters.clear()
        }
        if (bitmap != null) version.intValue = version.intValue + 1
        for (w in waiters) {
            runCatching { if (w.isActive) w.resume(Unit) }
        }
        if (fresh && bitmap != null) runCatching { save(key, bitmap) }
    }

    fun prebake(sets: Set<PatternSet>, size: BakeSize) {
        for (set in sets) {
            val key = BakeKey(size, set)
            try {
                executor.execute { runCatching { prebakeOne(key) } }
            } catch (_: Throwable) {
            }
        }
    }

    private fun prebakeOne(key: BakeKey) {
        val f = fileOf(key) ?: return
        if (f.isFile) return
        val held = synchronized(lock) { if (inFlight.containsKey(key)) return else memory[key] }
        if (held != null) {
            save(key, held)
            return
        }
        val made = composite(key)
        try {
            save(key, made)
        } finally {
            made.recycle()
        }
    }

    fun retainOnly(sets: Set<PatternSet>, size: BakeSize) {
        val changed = synchronized(lock) {
            val iter = memory.keys.iterator()
            var any = false
            while (iter.hasNext()) {
                val k = iter.next()
                if (k.size != size || k.set !in sets) {
                    iter.remove()
                    any = true
                }
            }
            any
        }
        if (changed) version.intValue = version.intValue + 1
    }

    private fun composite(key: BakeKey): Bitmap {
        val w = key.size.width
        val h = key.size.height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val paint = Paint()
            paint.isAntiAlias = true
            paint.isFilterBitmap = true
            for (p in key.set) {
                if (p.kind == PatternKind.NONE) continue
                for (layer in PatternMath.placed(p.kind, p.scale, key.size.density)) {
                    val tile = PatternTiles.tile(p.kind, p.tint, p.seed + layer.seedShift) ?: continue
                    val shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                    val m = Matrix()
                    m.setScale(layer.scaleX, layer.scaleY)
                    if (layer.rotate != 0f) m.postRotate(layer.rotate)
                    shader.setLocalMatrix(m)
                    paint.shader = shader
                    paint.alpha = ((p.intensity * layer.alpha).coerceIn(0f, 1f) * 255f).roundToInt()
                    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                }
            }
            paint.shader = null
        } catch (t: Throwable) {
            bitmap.recycle()
            throw t
        }
        return bitmap
    }

    private fun canonical(set: PatternSet, size: BakeSize): String =
        "v${PatternMath.GENERATOR_VERSION}|${size.width}x${size.height}|${size.density}|" + set.toJsonArray().toString()

    private fun nameOf(set: PatternSet, size: BakeSize): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(canonical(set, size).toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append("0123456789abcdef"[v shr 4]).append("0123456789abcdef"[v and 0xF])
        }
        return sb.toString()
    }

    private fun fileOf(key: BakeKey): File? {
        val d = dir ?: return null
        return File(d, nameOf(key.set, key.size) + EXT)
    }

    private fun loadFile(key: BakeKey): Bitmap? {
        val f = fileOf(key) ?: return null
        if (!f.isFile) return null
        val w = key.size.width
        val h = key.size.height
        val bytes = w.toLong() * h * 4L
        if (w <= 0 || h <= 0 || w > MAX_SIDE || h > MAX_SIDE || bytes > MAX_BYTES) return null
        val inflater = Inflater()
        return try {
            BufferedInputStream(FileInputStream(f), IO_BUFFER).use { raw ->
                val head = DataInputStream(raw)
                if (head.readInt() != MAGIC || head.readInt() != w || head.readInt() != h) {
                    f.delete()
                    null
                } else {
                    val pixels = ByteArray(bytes.toInt())
                    DataInputStream(InflaterInputStream(raw, inflater, IO_BUFFER)).readFully(pixels)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels))
                    f.setLastModified(System.currentTimeMillis())
                    bitmap
                }
            }
        } catch (e: OutOfMemoryError) {
            throw e
        } catch (_: Throwable) {
            f.delete()
            null
        } finally {
            inflater.end()
        }
    }

    private fun save(key: BakeKey, bitmap: Bitmap) {
        if (PerformanceStore.patternMode != PatternMode.BAKED) return
        val d = dir ?: return
        val name = nameOf(key.set, key.size)
        val temp = File(d, "$name.${System.nanoTime()}$TEMP")
        val deflater = Deflater(Deflater.BEST_SPEED)
        try {
            d.mkdirs()
            val buffer = ByteBuffer.allocate(bitmap.byteCount)
            bitmap.copyPixelsToBuffer(buffer)
            val out = DataOutputStream(BufferedOutputStream(FileOutputStream(temp), IO_BUFFER))
            out.use {
                it.writeInt(MAGIC)
                it.writeInt(bitmap.width)
                it.writeInt(bitmap.height)
                val z = DeflaterOutputStream(it, deflater, IO_BUFFER)
                z.write(buffer.array(), 0, buffer.capacity())
                z.finish()
            }
            if (temp.renameTo(File(d, name + EXT))) recordSize(d, key.size) else temp.delete()
        } catch (_: Throwable) {
            temp.delete()
        } finally {
            deflater.end()
        }
    }

    private fun readSizes(d: File): List<BakeSize> {
        val f = File(d, SIZES)
        if (!f.isFile) return emptyList()
        val out = ArrayList<BakeSize>()
        runCatching {
            for (line in f.readLines()) {
                val parts = line.trim().split(' ')
                if (parts.size != 3) continue
                val w = parts[0].toIntOrNull() ?: continue
                val h = parts[1].toIntOrNull() ?: continue
                val dens = parts[2].toFloatOrNull() ?: continue
                out += BakeSize(w, h, dens)
            }
        }
        return out
    }

    private fun recordSize(d: File, size: BakeSize) {
        synchronized(sizesLock) {
            runCatching {
                val all = ArrayList(readSizes(d))
                all.remove(size)
                all.add(size)
                val kept = all.takeLast(MAX_SIZES)
                File(d, SIZES).writeText(kept.joinToString("\n") { "${it.width} ${it.height} ${it.density}" })
            }
        }
    }

    fun clearAll() {
        val d = dir
        synchronized(lock) { memory.clear() }
        version.intValue = version.intValue + 1
        if (d == null) return
        try {
            executor.execute { runCatching { d.deleteRecursively() } }
        } catch (_: Throwable) {
        }
    }

    fun storedBytes(): Long {
        val d = dir ?: return 0L
        return d.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun sweepAsync(settings: ThemeSettings) {
        val context = appContext ?: return
        try {
            executor.execute { runCatching { sweep(context, settings) } }
        } catch (_: Throwable) {
        }
    }

    private fun sweep(context: Context, settings: ThemeSettings) {
        val d = dir ?: return
        val files = d.listFiles() ?: return
        val now = System.currentTimeMillis()
        val bakes = ArrayList<File>()
        for (f in files) {
            val n = f.name
            if (n.endsWith(TEMP)) {
                if (now - f.lastModified() > STALE_TEMP_MS) f.delete()
            } else if (n.endsWith(EXT)) {
                bakes += f
            }
        }
        val look = runCatching { DashboardStore(context).look }.getOrNull() ?: return
        val keepSets = LinkedHashSet<PatternSet>()
        keepSets += themeSets(settings, false)
        keepSets += themeSets(settings, true)
        setOf(look.backgroundFill())?.let { keepSets += it }
        setOf(look.tileFill)?.let { keepSets += it }
        val current = size
        val sizes = LinkedHashSet<BakeSize>(readSizes(d))
        if (current != null) sizes += current
        val keep = HashSet<String>()
        for (s in sizes) for (set in keepSets) keep += nameOf(set, s) + EXT
        val currentNames = HashSet<String>()
        if (current != null) for (set in keepSets) currentNames += nameOf(set, current) + EXT
        val kept = ArrayList<File>()
        var total = 0L
        for (f in bakes) {
            if (f.name in keep) {
                kept += f
                total += f.length()
            } else {
                f.delete()
            }
        }
        if (total <= MAX_FOLDER_BYTES) return
        val candidates = kept.filter { it.name !in currentNames }.sortedBy { it.lastModified() }
        for (f in candidates) {
            if (total <= MAX_FOLDER_BYTES) break
            val len = f.length()
            if (f.delete()) total -= len
        }
    }
}
