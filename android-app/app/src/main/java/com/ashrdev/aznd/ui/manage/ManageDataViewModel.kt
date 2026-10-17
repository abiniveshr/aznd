package com.ashrdev.aznd.ui.manage

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashrdev.aznd.data.manage.DataTransfer
import com.ashrdev.aznd.data.manage.ImportFormatException
import com.ashrdev.aznd.data.manage.ImportPackage
import com.ashrdev.aznd.ui.common.DayRange
import kotlinx.coroutines.launch

class ManageReport(val title: String, val lines: List<String>)

class ManageDataViewModel(val transfer: DataTransfer) : ViewModel() {

    var busy by mutableStateOf(false)
        private set

    var version by mutableIntStateOf(0)
        private set

    var report by mutableStateOf<ManageReport?>(null)
        private set

    var importPackage by mutableStateOf<ImportPackage?>(null)
        private set

    var importError by mutableStateOf<String?>(null)
        private set

    fun dismissReport() {
        report = null
    }

    suspend fun counts(range: DayRange): Map<String, Int> = transfer.counts(range)

    fun delete(moduleIds: Set<String>, range: DayRange) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val results = transfer.delete(moduleIds, range)
            val lines = results.map { r ->
                if (r.error != null) "${r.title}: failed (${r.error})" else "${r.title}: ${r.count} removed"
            }
            report = ManageReport("Deleted", lines)
            version++
            busy = false
        }
    }

    fun export(moduleIds: Set<String>, range: DayRange, resolver: ContentResolver, uri: Uri) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            report = try {
                val result = transfer.export(moduleIds, range)
                if (result.counts.isEmpty()) {
                    ManageReport("Nothing to export", listOf("The selected data is empty for this period."))
                } else {
                    transfer.write(resolver, uri, result.json)
                    ManageReport(
                        "Exported",
                        result.counts.map { (id, n) ->
                            val module = transfer.registry.module(id)
                            "${module?.title ?: id}: $n ${module?.unit.orEmpty()}".trim()
                        }
                    )
                }
            } catch (e: Exception) {
                ManageReport("Export failed", listOf(e.message ?: "Unknown error"))
            }
            busy = false
        }
    }

    fun loadFile(resolver: ContentResolver, uri: Uri) {
        if (busy) return
        busy = true
        importError = null
        viewModelScope.launch {
            try {
                importPackage = transfer.parse(transfer.read(resolver, uri))
            } catch (e: ImportFormatException) {
                importPackage = null
                importError = e.message
            } catch (e: Exception) {
                importPackage = null
                importError = "Could not read this file."
            }
            busy = false
        }
    }

    fun clearImportFile() {
        importPackage = null
        importError = null
    }

    fun runImport(moduleIds: Set<String>, replaceExisting: Boolean) {
        val pkg = importPackage ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            val results = transfer.import(pkg, moduleIds, replaceExisting)
            report = ManageReport("Imported", importLines(results))
            importPackage = null
            version++
            busy = false
        }
    }

    companion object {
        fun factory(transfer: DataTransfer): ViewModelProvider.Factory =
            viewModelFactory { initializer { ManageDataViewModel(transfer) } }
    }
}

internal fun importLines(results: List<com.ashrdev.aznd.data.manage.ImportReport>): List<String> =
    results.flatMap { r ->
        val head = when {
            r.error != null -> "${r.title}: failed (${r.error})"
            else -> "${r.title}: ${r.outcome?.added ?: 0} added, ${r.outcome?.skipped ?: 0} skipped"
        }
        listOf(head) + r.outcome?.notes.orEmpty().map { "   $it" }
    }
