package com.ashrdev.aznd.ui.workout

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object SessionShare {
    private const val INLINE_LIMIT = 3000

    fun share(context: Context, subject: String, body: String, photoUri: Uri? = null) {
        val attachments = mutableListOf<Uri>()
        photoUri?.let { attachments += it }

        var text = body
        if (body.length > INLINE_LIMIT) {
            writeTextFile(context, subject, body)?.let { attachments += it }
            text = body.take(INLINE_LIMIT).trimEnd() + "\n…\nfull log attached as a file"
        }

        val intent = when {
            attachments.size > 1 -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(attachments))
            }
            attachments.size == 1 -> Intent(Intent.ACTION_SEND).apply {
                type = if (attachments[0] == photoUri) "image/jpeg" else "text/plain"
                putExtra(Intent.EXTRA_STREAM, attachments[0])
            }
            else -> Intent(Intent.ACTION_SEND).apply { type = "text/plain" }
        }

        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        intent.putExtra(Intent.EXTRA_TEXT, text)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Share"))
    }

    private fun writeTextFile(context: Context, subject: String, body: String): Uri? = runCatching {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val safe = subject.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
        val file = File(dir, "${safe}_${System.currentTimeMillis()}.txt")
        file.writeText(body)
        FileProvider.getUriForFile(context, PhotoStore.authority(context), file)
    }.getOrNull()
}