package com.ashrdev.aznd.data.manage

import android.content.ContentResolver
import android.net.Uri
import com.ashrdev.aznd.ui.common.DayRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val EXPORT_FORMAT = "aznd-data"
const val EXPORT_FORMAT_VERSION = 1
const val EXPORT_MIME = "application/json"

class ExportResult(val json: String, val counts: Map<String, Int>)

class ImportSection(val id: String, val version: Int, val count: Int, val data: JSONObject)

class ImportPackage(
    val exportedAt: Long,
    val range: DayRange,
    val sections: List<ImportSection>
)

enum class ImportStatus(val reason: String?) {
    READY(null),
    UNKNOWN_MODULE("Not recognised by this version of the app"),
    NOT_SUPPORTED("Import isn't available for this yet"),
    TOO_NEW("Made by a newer version of the app")
}

class ImportReport(val title: String, val outcome: ImportOutcome?, val error: String?)

class DeleteReport(val title: String, val count: Int, val error: String?)

class ImportFormatException(message: String) : Exception(message)

class DataTransfer(val registry: DataRegistry) {

    private fun rangeFor(module: DataModule, range: DayRange) =
        if (module.supportsDateRange) range else DayRange.All

    suspend fun counts(range: DayRange): Map<String, Int> = registry.modules.associate { module ->
        module.id to runCatching { module.count(rangeFor(module, range)) }.getOrDefault(0)
    }

    suspend fun delete(moduleIds: Set<String>, range: DayRange): List<DeleteReport> =
        registry.modules.filter { it.id in moduleIds }.map { module ->
            val effective = rangeFor(module, range)
            runCatching {
                val before = module.count(effective)
                module.delete(effective)
                DeleteReport(module.title, before, null)
            }.getOrElse { DeleteReport(module.title, 0, it.message ?: "Failed") }
        }

    suspend fun export(moduleIds: Set<String>, range: DayRange): ExportResult = withContext(Dispatchers.Default) {
        val sections = JSONObject()
        val counts = LinkedHashMap<String, Int>()
        for (module in registry.modules) {
            if (module.id !in moduleIds || !module.canExport) continue
            val out = module.export(rangeFor(module, range)) ?: continue
            if (out.count == 0) continue
            sections.put(
                module.id,
                JSONObject().put("version", module.payloadVersion).put("count", out.count).put("data", out.data)
            )
            counts[module.id] = out.count
        }
        val root = JSONObject()
            .put("format", EXPORT_FORMAT)
            .put("formatVersion", EXPORT_FORMAT_VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("rangeFrom", range.startKey)
            .put("rangeTo", range.endKey)
            .put("modules", sections)
        ExportResult(root.toString(), counts)
    }

    suspend fun parse(text: String): ImportPackage = withContext(Dispatchers.Default) {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw ImportFormatException("This isn't an aznd export file.")
        }
        if (root.optString("format") != EXPORT_FORMAT) throw ImportFormatException("This isn't an aznd export file.")
        if (root.optInt("formatVersion") > EXPORT_FORMAT_VERSION) {
            throw ImportFormatException("This file was made by a newer version of the app. Update the app to import it.")
        }
        val modules = root.optJSONObject("modules") ?: JSONObject()
        val sections = modules.keys().asSequence().map { id ->
            val s = modules.getJSONObject(id)
            ImportSection(id, s.optInt("version", 1), s.optInt("count"), s.optJSONObject("data") ?: JSONObject())
        }.toList()
        val ordered = sections.sortedBy { s -> registry.modules.indexOfFirst { it.id == s.id }.let { if (it < 0) Int.MAX_VALUE else it } }
        ImportPackage(
            exportedAt = root.optLong("exportedAt"),
            range = DayRange(root.stringOrNull("rangeFrom"), root.stringOrNull("rangeTo")),
            sections = ordered
        )
    }

    fun statusOf(section: ImportSection): ImportStatus {
        val module = registry.module(section.id) ?: return ImportStatus.UNKNOWN_MODULE
        if (!module.canImport) return ImportStatus.NOT_SUPPORTED
        if (section.version > module.payloadVersion) return ImportStatus.TOO_NEW
        return ImportStatus.READY
    }

    suspend fun import(pkg: ImportPackage, moduleIds: Set<String>, replaceExisting: Boolean): List<ImportReport> {
        val reports = ArrayList<ImportReport>()
        for (section in pkg.sections) {
            if (section.id !in moduleIds || statusOf(section) != ImportStatus.READY) continue
            val module = registry.module(section.id) ?: continue
            reports += runCatching {
                if (replaceExisting) module.delete(rangeFor(module, pkg.range))
                ImportReport(module.title, module.import(section.data, section.version), null)
            }.getOrElse { ImportReport(module.title, null, it.message ?: "Failed") }
        }
        return reports
    }

    suspend fun write(resolver: ContentResolver, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val stream = resolver.openOutputStream(uri, "wt") ?: throw IllegalStateException("Could not open the file")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun read(resolver: ContentResolver, uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = resolver.openInputStream(uri) ?: throw ImportFormatException("Could not open the file.")
        stream.use { String(it.readBytes(), Charsets.UTF_8) }
    }

    companion object {
        fun suggestedFileName(): String =
            "aznd-export-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".json"
    }
}
