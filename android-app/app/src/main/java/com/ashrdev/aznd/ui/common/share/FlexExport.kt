package com.ashrdev.aznd.ui.common.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import com.ashrdev.aznd.ui.common.PhotoStore
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/** The file formats a poster can be exported as. */
enum class FlexImageFormat(val label: String, val extension: String, val mime: String) {
    PNG("PNG", "png", "image/png"),
    JPG("JPG", "jpg", "image/jpeg")
}

/** The sizes a poster can be exported at, as the length of its shorter side in pixels. */
val FLEX_EXPORT_SIZES = listOf(1080, 1440, 2160)

/** Turns a poster into a PNG or JPG file and hands it to the share sheet, the gallery or the clipboard (the same FileProvider paths the chart export uses). */
object FlexExport {
    private const val DIR = "shared"
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    /**
     * Draws the card with its shorter side [short] pixels long (1080: 1080 × 1350 for a post, 1920 × 1080 for 16:9).
     * The height comes from the layout: fixed by the size, or by the content for Free size, where a card shorter than
     * it is wide is scaled up to keep its height at [short] too. [opaque] fills the bitmap white first (for JPG, which
     * has no transparency).
     */
    fun render(
        spec: FlexSpec,
        content: FlexContent,
        assets: FlexAssets,
        short: Int = FLEX_CARD_SHORT,
        opaque: Boolean = false
    ): Bitmap {
        val plan = FlexPainter.plan(spec, content, assets)
        val k = short / minOf(plan.width, plan.height).coerceAtLeast(1f)
        val width = (plan.width * k).roundToInt().coerceAtLeast(1)
        val height = (plan.height * k).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (opaque) canvas.drawColor(Color.WHITE)
        FlexPainter.draw(canvas, width.toFloat(), spec, content, assets, plan)
        return bitmap
    }

    /** Writes [bitmap] into the cache folder as [format] (JPG at quality 95) and returns the file, null when it failed. Call off the main thread. */
    fun write(context: Context, bitmap: Bitmap, format: FlexImageFormat): File? = runCatching {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { f ->
            if (f.name.startsWith("flex_") && now - f.lastModified() > MAX_AGE_MS) f.delete()
        }
        var file = File(dir, "flex_$now.${format.extension}")
        var n = 1
        while (file.exists()) file = File(dir, "flex_${now}_${n++}.${format.extension}")
        val type = if (format == FlexImageFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        FileOutputStream(file).use { out ->
            if (!bitmap.compress(type, if (format == FlexImageFormat.PNG) 100 else 95, out)) error("compress failed")
        }
        file
    }.getOrNull()

    /** A FileProvider uri for a file made by [write]. */
    fun uriOf(context: Context, file: File): Uri = FileProvider.getUriForFile(context, PhotoStore.authority(context), file)

    fun writePng(context: Context, bitmap: Bitmap): Uri? =
        write(context, bitmap, FlexImageFormat.PNG)?.let { runCatching { uriOf(context, it) }.getOrNull() }

    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(uri) ?: "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Puts the picture at [uri] on the clipboard (as a content uri, so apps that take pictures can paste it). */
    fun copyToClipboard(context: Context, uri: Uri) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "Poster", uri))
    }
}
