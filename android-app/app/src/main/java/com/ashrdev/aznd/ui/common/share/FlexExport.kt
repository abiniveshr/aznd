package com.ashrdev.aznd.ui.common.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.core.content.FileProvider
import com.ashrdev.aznd.ui.workout.PhotoStore
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/** Turns a card into a PNG and hands it to the share sheet (the same FileProvider paths the chart export uses). */
object FlexExport {
    private const val DIR = "shared"
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    /** Draws the card at [width] pixels wide (1080 is native; the height follows the aspect). */
    fun render(spec: FlexSpec, content: FlexContent, assets: FlexAssets, width: Int = FLEX_CARD_WIDTH): Bitmap {
        val height = (spec.style.aspect.height * width / FLEX_CARD_WIDTH.toFloat()).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        FlexPainter.draw(Canvas(bitmap), width.toFloat(), spec, content, assets)
        return bitmap
    }

    fun writePng(context: Context, bitmap: Bitmap): Uri? = runCatching {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { f ->
            if (f.name.startsWith("flex_") && now - f.lastModified() > MAX_AGE_MS) f.delete()
        }
        val file = File(dir, "flex_$now.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, PhotoStore.authority(context), file)
    }.getOrNull()

    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
