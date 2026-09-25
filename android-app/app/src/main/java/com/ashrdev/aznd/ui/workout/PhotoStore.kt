package com.ashrdev.aznd.ui.workout

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object PhotoStore {
    private const val SESSION_DIR = "session_photos"
    private const val STREAK_DIR = "streak_images"

    fun authority(context: Context) = "${context.packageName}.fileprovider"

    fun newPhotoUri(context: Context): Uri {
        val dir = File(context.filesDir, SESSION_DIR).apply { mkdirs() }
        val file = File(dir, "session_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    fun newStreakPhotoUri(context: Context): Uri {
        val dir = File(context.filesDir, STREAK_DIR).apply { mkdirs() }
        val file = File(dir, "streak_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    fun loadBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1200
    ): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }

        var sample = 1
        val largest = maxOf(bounds.outWidth, bounds.outHeight)

        while (largest / sample > maxDimension) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
        }

        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }.getOrNull()
}