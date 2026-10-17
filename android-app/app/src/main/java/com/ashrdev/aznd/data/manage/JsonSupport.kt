package com.ashrdev.aznd.data.manage

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

fun JSONObject.stringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

fun JSONObject.longOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) getLong(key) else null

fun JSONObject.intOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) getInt(key) else null

fun JSONObject.doubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) getDouble(key) else null

fun JSONObject.arrayOrEmpty(key: String): JSONArray = optJSONArray(key) ?: JSONArray()

inline fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> =
    List(length()) { block(getJSONObject(it)) }

fun <T> Iterable<T>.toJsonArray(block: (T) -> JSONObject): JSONArray =
    JSONArray().also { array -> forEach { array.put(block(it)) } }

inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

class PhotoFiles(private val context: Context) {
    private val folders = listOf("session_photos", "streak_images", "picked_images", "exercise_photos")

    fun deletePath(path: String?) {
        if (path == null) return
        val file = File(path)
        val inside = folders.any { file.parentFile?.absolutePath == File(context.filesDir, it).absolutePath }
        if (inside && file.isFile) file.delete()
    }

    fun delete(uris: Collection<String?>) {
        for (raw in uris.filterNotNull().distinct()) {
            val name = runCatching { Uri.parse(raw).lastPathSegment }.getOrNull()
                ?.takeIf { it.isNotBlank() && !it.contains('/') && !it.contains("..") } ?: continue
            for (folder in folders) {
                val file = File(File(context.filesDir, folder), name)
                if (file.isFile) file.delete()
            }
        }
    }
}
