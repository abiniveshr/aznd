package com.ashrdev.aznd.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where a photo is: [loading] until the decode finishes, then either a [bitmap] or (loading false,
 * bitmap null) a failed decode.
 */
@Immutable
class PhotoLoad internal constructor(val bitmap: ImageBitmap?, val loading: Boolean)

private val PhotoLoading = PhotoLoad(null, true)
private val PhotoFailed = PhotoLoad(null, false)

/**
 * One cache for every photo shown in the UI, bounded by bytes. Photo files are never rewritten
 * (each pick gets a new timestamped name), so an entry never goes stale. Failed decodes are not kept.
 */
private object PhotoBitmaps {
    private const val MAX_BYTES = 24 * 1024 * 1024L

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(MAX_BYTES).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun peek(key: String): Bitmap? = cache.get(key)

    fun decodeAndKeep(key: String, decode: () -> Bitmap?): Bitmap? {
        cache.get(key)?.let { return it }
        val bitmap = decode() ?: return null
        cache.put(key, bitmap)
        return bitmap
    }
}

/** The photo at [uri] decoded to at most [maxDimension] px, or null while it loads or if it can't be read. */
@Composable
fun rememberPhotoBitmap(uri: String?, maxDimension: Int): ImageBitmap? =
    rememberPhotoLoad(uri, maxDimension).bitmap

/** Like [rememberPhotoBitmap], but tells a photo that is still loading from one that failed. */
@Composable
fun rememberPhotoLoad(uri: String?, maxDimension: Int): PhotoLoad {
    val key = uri?.let { "u:$maxDimension:$it" }
    return rememberCachedLoad(key) { context ->
        PhotoStore.loadBitmap(context, uri!!.toUri(), maxDimension)
    }
}

/**
 * A square-ish thumbnail of the file at [path], down-sampled so neither side drops below [targetPx].
 * Null while it loads or if the file can't be read.
 */
@Composable
fun rememberFileThumb(path: String?, targetPx: Int): ImageBitmap? {
    val key = path?.let { "f:$targetPx:$it" }
    return rememberCachedLoad(key) { decodeThumb(path!!, targetPx) }.bitmap
}

@Composable
private fun rememberCachedLoad(key: String?, decode: (Context) -> Bitmap?): PhotoLoad {
    val context = LocalContext.current.applicationContext
    // A cache hit is ready in the first composition, so a photo that comes back into view never flashes empty.
    var load by remember(key) {
        mutableStateOf(
            when {
                key == null -> PhotoFailed
                else -> PhotoBitmaps.peek(key)?.let { PhotoLoad(it.asImageBitmap(), false) } ?: PhotoLoading
            }
        )
    }
    LaunchedEffect(key) {
        if (key == null || !load.loading) return@LaunchedEffect
        val bitmap = withContext(Dispatchers.IO) {
            PhotoBitmaps.decodeAndKeep(key) { decode(context) }
        }
        load = if (bitmap != null) PhotoLoad(bitmap.asImageBitmap(), false) else PhotoFailed
    }
    return load
}

private fun decodeThumb(path: String, targetPx: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()
