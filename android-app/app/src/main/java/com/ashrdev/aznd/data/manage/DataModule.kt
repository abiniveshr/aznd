package com.ashrdev.aznd.data.manage

import com.ashrdev.aznd.ui.common.DayRange
import org.json.JSONObject

class ModuleExport(val count: Int, val data: JSONObject)

class ImportOutcome(
    val added: Int = 0,
    val skipped: Int = 0,
    val notes: List<String> = emptyList()
)

/**
 * One kind of user data (workout history, routines, saved streak days, ...). Manage data lists
 * every registered module; the Delete, Export and Import pages and the local layers only ever talk
 * to this interface, never to a table.
 *
 * Delete is required. Export and Import are optional: leave [canExport] / [canImport] false and a
 * module is simply greyed out on those pages until its functions are written.
 */
interface DataModule {
    /** Stable id, written into export files. Never rename it once a release has shipped. */
    val id: String
    val app: DataApp
    val title: String
    val description: String

    /** What [count] counts, e.g. "sessions". */
    val unit: String

    /** True when [count], [delete] and [export] honour the date range. */
    val supportsDateRange: Boolean get() = false

    /** What the date is measured on, shown when a date filter is on, e.g. "by workout start date". */
    val dateRangeNote: String? get() = null

    /** Extra data that disappears with this module, shown on the Delete confirmation. */
    val deleteNote: String? get() = null

    val canExport: Boolean get() = false
    val canImport: Boolean get() = false

    /** Version of the payload layout written by [export]; raise it when the layout changes. */
    val payloadVersion: Int get() = 1

    suspend fun count(range: DayRange): Int

    suspend fun delete(range: DayRange)

    suspend fun export(range: DayRange): ModuleExport? = null

    suspend fun import(data: JSONObject, version: Int): ImportOutcome = ImportOutcome()
}
