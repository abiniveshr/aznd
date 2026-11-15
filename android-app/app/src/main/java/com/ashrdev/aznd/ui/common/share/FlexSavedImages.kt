package com.ashrdev.aznd.ui.common.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.ashrdev.aznd.ui.common.PhotoStore
import java.io.File
import java.io.FileOutputStream

/**
 * The cards the person exported (shared or saved to the gallery), kept as PNGs so the Saved page can show them again.
 *
 * They live in filesDir/session_photos/flex_saved: session_photos is the folder [PhotoStore.newPhotoUri] already
 * creates FileProvider uris in, and a FileProvider path covers everything below its folder, so no new entry in the
 * FileProvider paths file is needed. (If that file only lists session_photos by a narrower rule, add
 * `<files-path name="flex_saved" path="session_photos/flex_saved/" />`.)
 */
object FlexSavedImages {
    private const val PARENT = "session_photos"
    private const val DIR = "flex_saved"
    private const val PREFIX = "flex_"

    private fun dir(context: Context): File =
        File(File(context.filesDir, PARENT), DIR).apply { mkdirs() }

    /** Writes [bitmap] as a PNG and returns the file, null when it could not be written. Call off the main thread. */
    fun keep(context: Context, bitmap: Bitmap): File? = runCatching {
        val folder = dir(context)
        var file = File(folder, "$PREFIX${System.currentTimeMillis()}.png")
        // Two cards in the same millisecond must not overwrite each other.
        var n = 1
        while (file.exists()) file = File(folder, "$PREFIX${System.currentTimeMillis()}_${n++}.png")
        FileOutputStream(file).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) error("compress failed")
        }
        file
    }.getOrNull()

    /** The kept cards, newest first. */
    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.isFile && f.name.endsWith(".png") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun delete(file: File) {
        runCatching { file.delete() }
    }

    /** A FileProvider uri for [file], for the share sheet or saving to the gallery. */
    fun uriOf(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, PhotoStore.authority(context), file)

    /**
     * [file] decoded with its longer side at most about [maxSide] pixels (it is subsampled by powers of two, so it can
     * be up to twice that), for the grid. Null when it cannot be read. Call off the main thread.
     */
    fun thumbnail(file: File, maxSide: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (largest / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
