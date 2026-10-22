package com.ashrdev.aznd.ui.common.share

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.ashrdev.aznd.ui.common.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The pictures a card needs, already loaded: the (blurred) background and every other image by uri
 * (streak logos, the photo block). The painter never loads anything itself.
 */
class FlexAssets(
    val background: Bitmap? = null,
    val images: Map<String, Bitmap> = emptyMap(),
    /** False while the pictures the card's blocks need are still loading (their sizes are not known yet). */
    val ready: Boolean = true
)

object FlexBitmaps {

    fun load(context: Context, uri: String, maxDimension: Int): Bitmap? =
        runCatching { PhotoStore.loadBitmap(context, uri.toUri(), maxDimension) }.getOrNull()

    /**
     * Cheap, good-looking blur: shrink the picture, box-blur the small one, stretch it back. [strength]
     * is 0..1; 0 returns [src] untouched.
     */
    fun blur(src: Bitmap, strength: Float): Bitmap {
        if (strength <= 0.01f) return src
        val factor = 1f + strength * 24f
        val w = (src.width / factor).toInt().coerceAtLeast(8)
        val h = (src.height / factor).toInt().coerceAtLeast(8)
        val small = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
        boxBlur(small, radius = 2, passes = 2)
        return Bitmap.createScaledBitmap(small, src.width, src.height, true)
    }

    private fun boxBlur(bmp: Bitmap, radius: Int, passes: Int) {
        val w = bmp.width
        val h = bmp.height
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        bmp.getPixels(a, 0, w, 0, 0, w, h)
        repeat(passes) {
            blurPass(a, b, w, h, radius, horizontal = true)
            blurPass(b, a, w, h, radius, horizontal = false)
        }
        bmp.setPixels(a, 0, w, 0, 0, w, h)
    }

    /** One sliding-window pass along every row (horizontal) or column, clamping at the edges. */
    private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val stride = if (horizontal) 1 else w
        val count = 2 * r + 1
        for (l in 0 until lines) {
            val base = if (horizontal) l * w else l
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (k in -r..r) {
                val p = src[base + k.coerceIn(0, len - 1) * stride]
                sa += p ushr 24
                sr += (p shr 16) and 0xFF
                sg += (p shr 8) and 0xFF
                sb += p and 0xFF
            }
            for (i in 0 until len) {
                dst[base + i * stride] = ((sa / count) shl 24) or ((sr / count) shl 16) or ((sg / count) shl 8) or (sb / count)
                val add = src[base + (i + r + 1).coerceIn(0, len - 1) * stride]
                val sub = src[base + (i - r).coerceIn(0, len - 1) * stride]
                sa += (add ushr 24) - (sub ushr 24)
                sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
    }
}

/** Every image uri the card currently needs besides its background. */
private fun neededImages(spec: FlexSpec?, content: FlexContent?): Set<String> {
    if (spec == null || content == null) return emptySet()
    val out = LinkedHashSet<String>()
    if (spec.blocks.any { it.enabled && it.type == FlexBlockType.STREAKS }) {
        content.streaks.forEach { s -> s.logoUri?.let { out += it } }
    }
    spec.blocks.filter { it.enabled && it.type == FlexBlockType.PHOTO }.forEach { b ->
        (b.keys?.firstOrNull() ?: content.photoUri)?.let { out += it }
    }
    return out
}

/** Loads (off the main thread) and caches what [spec] needs; updates as the spec changes. */
@Composable
fun rememberFlexAssets(spec: FlexSpec?, content: FlexContent?): FlexAssets {
    val context = LocalContext.current
    remember(context) { FlexFonts.attach(context) }
    val style = spec?.style

    val bgUri = if (style?.bgMode == FlexBgMode.PHOTO) style.photoUri ?: content?.photoUri else null
    val raw = produceState<Bitmap?>(null, bgUri) {
        value = bgUri?.let { uri -> withContext(Dispatchers.IO) { FlexBitmaps.load(context, uri, 1400) } }
    }.value

    val blurStep = ((style?.photoBlur ?: 0f) * 20f).roundToInt()
    val background = produceState<Bitmap?>(raw, raw, blurStep) {
        val src = raw
        value = if (src == null) null else withContext(Dispatchers.Default) { FlexBitmaps.blur(src, blurStep / 20f) }
    }.value

    val needed = remember(spec?.blocks, content) { neededImages(spec, content) }
    // Remembers which uris it loaded, so the assets can say when the pictures for the current blocks are in.
    val loaded = produceState<Pair<Set<String>, Map<String, Bitmap>>?>(null, needed) {
        value = needed to withContext(Dispatchers.IO) {
            needed.mapNotNull { uri -> FlexBitmaps.load(context, uri, 720)?.let { uri to it } }.toMap()
        }
    }.value
    val images = loaded?.second ?: emptyMap()
    val ready = spec != null && content != null && loaded?.first == needed

    return remember(background, images, ready) { FlexAssets(background, images, ready) }
}
