package com.ashrdev.aznd.ui.manage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.manage.DataApp
import com.ashrdev.aznd.data.manage.DataModule
import com.ashrdev.aznd.data.manage.DataTransfer
import com.ashrdev.aznd.data.manage.EXPORT_MIME
import com.ashrdev.aznd.data.manage.ImportPackage
import com.ashrdev.aznd.data.manage.ImportStatus
import com.ashrdev.aznd.data.manage.isAll
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.DayRange
import com.ashrdev.aznd.ui.common.PrimaryAction
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.components.Card
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ManageBarPages = ManagePage.entries.map { BarPage(it.title, it.icon) }

private class ManageRow(
    val id: String,
    val app: DataApp?,
    val title: String,
    val description: String,
    val note: String?,
    val trailing: String,
    val enabled: Boolean
)

private class ExportRequest(val ids: Set<String>, val range: DayRange)

@Composable
fun ManageDataScreen(
    viewModel: ManageDataViewModel,
    initialApp: DataApp?,
    initialPage: ManagePage,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val transfer = viewModel.transfer
    val registry = transfer.registry
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = initialPage.ordinal) { ManagePage.entries.size }
    val subPages = rememberSubPageBar(pagerState, ManageBarPages)

    // One filter (top-bar icon) shared by the Delete, Export and Import pages. It starts on the
    // app Manage data was opened from, if any.
    val filter = rememberManageFilterState(initialApp)
    var showFilter by rememberSaveable { mutableStateOf(false) }
    val selectedApps = filter.apps
    val visible = remember(selectedApps) { registry.forApps(selectedApps) }

    var deleteSel by remember { mutableStateOf(emptySet<String>()) }
    var exportSel by remember { mutableStateOf(emptySet<String>()) }
    val pkg = viewModel.importPackage
    var importSel by remember(pkg) { mutableStateOf(readyIds(transfer, pkg)) }
    var replaceExisting by rememberSaveable { mutableStateOf(false) }

    // The date part of the filter. Null while "Custom" has no valid number yet: Delete and Export
    // stay switched off until it does, so nothing is ever run against a half-set period.
    val filterRange = filter.range
    val rangeReady = filterRange != null
    val range = filterRange ?: DayRange.All
    val dateActive = !range.isAll

    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(range, viewModel.version) { counts = viewModel.counts(range) }

    var confirmDelete by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<ExportRequest?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(EXPORT_MIME)) { uri ->
        val request = pendingExport
        pendingExport = null
        if (uri != null && request != null) {
            viewModel.export(request.ids, request.range, context.contentResolver, uri)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.loadFile(context.contentResolver, uri)
    }

    fun deleteEnabled(m: DataModule) =
        rangeReady && (counts[m.id] ?: 0) > 0 && (!dateActive || m.supportsDateRange)

    fun exportEnabled(m: DataModule) = rangeReady && m.canExport && (counts[m.id] ?: 0) > 0

    val deleteIds = visible.filter { it.id in deleteSel && deleteEnabled(it) }.map { it.id }.toSet()
    val exportIds = visible.filter { it.id in exportSel && exportEnabled(it) }.map { it.id }.toSet()
    val importIds = pkg?.sections.orEmpty()
        .filter { it.id in importSel && transfer.statusOf(it) == ImportStatus.READY }
        .filter { s -> selectedApps.isEmpty() || registry.module(s.id)?.app in selectedApps }
        .map { it.id }.toSet()

    val page = ManagePage.entries[pagerState.currentPage]
    val primary = when (page) {
        ManagePage.DELETE -> PrimaryAction(
            Icons.Default.Delete,
            if (deleteIds.isEmpty()) "Delete selected" else "Delete selected (${deleteIds.size})",
            enabled = deleteIds.isNotEmpty() && !viewModel.busy
        ) { confirmDelete = true }
        ManagePage.EXPORT -> PrimaryAction(
            Icons.Default.FileUpload,
            if (exportIds.isEmpty()) "Export selected" else "Export selected (${exportIds.size})",
            enabled = exportIds.isNotEmpty() && !viewModel.busy
        ) {
            pendingExport = ExportRequest(exportIds, range)
            exportLauncher.launch(DataTransfer.suggestedFileName())
        }
        ManagePage.IMPORT -> if (pkg == null) {
            PrimaryAction(Icons.Default.FileDownload, "Choose file", enabled = !viewModel.busy) {
                importLauncher.launch(arrayOf("*/*"))
            }
        } else {
            PrimaryAction(
                Icons.Default.FileDownload,
                if (importIds.isEmpty()) "Import selected" else "Import selected (${importIds.size})",
                enabled = importIds.isNotEmpty() && !viewModel.busy
            ) { if (replaceExisting) confirmReplace = true else viewModel.runImport(importIds, false) }
        }
    }

    if (confirmDelete) {
        val lines = registry.modules.filter { it.id in deleteIds }.map { m ->
            "${m.title}: ${counts[m.id] ?: 0} ${m.unit}"
        }
        val notes = registry.modules.filter { it.id in deleteIds }.mapNotNull { m -> m.deleteNote?.let { "${m.title}: $it" } }
        val period = if (dateActive) filter.describePeriod() else "All time"
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Period: $period", style = MaterialTheme.typography.labelLarge)
                    lines.forEach { Text("• $it") }
                    notes.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("This can't be undone. Export it first if you might want it back.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(deleteIds, range)
                    deleteSel = emptySet()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    if (confirmReplace) {
        val titles = registry.modules.filter { it.id in importIds }.joinToString { it.title }
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace existing data?") },
            text = { Text("Your current $titles will be deleted first (only the period covered by the file, where it has dates) and replaced by the file's. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReplace = false
                    viewModel.runImport(importIds, true)
                }) { Text("Replace", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } }
        )
    }

    viewModel.report?.let { r ->
        AlertDialog(
            onDismissRequest = viewModel::dismissReport,
            title = { Text(r.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { r.lines.forEach { Text(it) } }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissReport) { Text("OK") } }
        )
    }

    if (showFilter) {
        ManageFilterDialog(
            state = filter,
            apps = registry.apps,
            page = page,
            onDismiss = { showFilter = false }
        )
    }

    ScreenScaffold(
        title = "Manage data",
        subtitle = filter.summary(includeDates = page != ManagePage.IMPORT),
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        primaryAction = primary,
        reservePrimarySpace = true,
        subPages = subPages,
        manageDataEnabled = false,
        topBarActions = {
            IconButton(onClick = { showFilter = true }) {
                BadgedBox(badge = { if (filter.isActive) Badge() }) {
                    Icon(Icons.Default.FilterList, contentDescription = "Filter")
                }
            }
        }
    ) { padding ->
        val bottom = padding.calculateBottomPadding() + 16.dp
        Column(modifier = Modifier.fillMaxSize()) {
            if (viewModel.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
                when (ManagePage.entries[index]) {
                    ManagePage.DELETE -> ModuleList(
                        rows = visible.map { m ->
                            val n = counts[m.id] ?: 0
                            ManageRow(
                                id = m.id, app = m.app, title = m.title, description = m.description,
                                note = when {
                                    !rangeReady -> "Finish the date filter to continue"
                                    dateActive && !m.supportsDateRange -> "Has no dates, so it can't be limited to a period"
                                    dateActive && m.dateRangeNote != null -> "Period ${m.dateRangeNote}"
                                    else -> m.deleteNote
                                },
                                trailing = if (n == 0) "Empty" else "$n ${m.unit}",
                                enabled = deleteEnabled(m)
                            )
                        },
                        selected = deleteSel,
                        onSelectedChange = { deleteSel = it },
                        emptyText = "Nothing here yet.",
                        bottomPadding = bottom
                    )
                    ManagePage.EXPORT -> ModuleList(
                        rows = visible.map { m ->
                            val n = counts[m.id] ?: 0
                            ManageRow(
                                id = m.id, app = m.app, title = m.title, description = m.description,
                                note = when {
                                    !m.canExport -> "Export isn't available for this yet"
                                    !rangeReady -> "Finish the date filter to continue"
                                    dateActive && !m.supportsDateRange -> "Has no dates: always exported in full"
                                    dateActive && m.dateRangeNote != null -> "Period ${m.dateRangeNote}"
                                    else -> null
                                },
                                trailing = if (n == 0) "Empty" else "$n ${m.unit}",
                                enabled = exportEnabled(m)
                            )
                        },
                        selected = exportSel,
                        onSelectedChange = { exportSel = it },
                        emptyText = "Nothing here yet.",
                        bottomPadding = bottom
                    )
                    ManagePage.IMPORT -> if (pkg == null) {
                        ImportStart(viewModel.importError, bottom)
                    } else {
                        ModuleList(
                            rows = pkg.sections.mapNotNull { s ->
                                val m = registry.module(s.id)
                                if (selectedApps.isNotEmpty() && m?.app !in selectedApps) return@mapNotNull null
                                val status = transfer.statusOf(s)
                                ManageRow(
                                    id = s.id, app = m?.app, title = m?.title ?: s.id,
                                    description = m?.description ?: "",
                                    note = status.reason,
                                    trailing = "${s.count} ${m?.unit.orEmpty()}".trim(),
                                    enabled = status == ImportStatus.READY
                                )
                            },
                            selected = importSel,
                            onSelectedChange = { importSel = it },
                            emptyText = "The file has nothing for this filter.",
                            bottomPadding = bottom,
                            header = {
                                ImportHeader(
                                    pkg = pkg,
                                    replaceExisting = replaceExisting,
                                    onReplaceChange = { replaceExisting = it },
                                    onChooseOther = { importLauncher.launch(arrayOf("*/*")) },
                                    onClear = viewModel::clearImportFile
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun readyIds(transfer: DataTransfer, pkg: ImportPackage?): Set<String> =
    pkg?.sections.orEmpty().filter { transfer.statusOf(it) == ImportStatus.READY }.map { it.id }.toSet()

@Composable
private fun ModuleList(
    rows: List<ManageRow>,
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    emptyText: String,
    bottomPadding: Dp,
    header: (@Composable () -> Unit)? = null
) {
    val groups = remember(rows) {
        rows.groupBy { it.app }.entries.sortedBy { it.key?.ordinal ?: Int.MAX_VALUE }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (header != null) item(key = "header") { header() }
        if (rows.isEmpty()) {
            item(key = "empty") { Text(emptyText, style = MaterialTheme.typography.bodyMedium) }
        }
        for ((groupApp, groupRows) in groups) {
            item(key = "group_${groupApp?.id}") {
                val usable = groupRows.filter { it.enabled }.map { it.id }
                val chosen = usable.count { it in selected }
                val state = when {
                    chosen == 0 -> ToggleableState.Off
                    chosen == usable.size -> ToggleableState.On
                    else -> ToggleableState.Indeterminate
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        groupApp?.label ?: "Other",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Text("Select all", style = MaterialTheme.typography.labelMedium)
                    TriStateCheckbox(
                        state = state,
                        enabled = usable.isNotEmpty(),
                        onClick = {
                            onSelectedChange(if (chosen == usable.size) selected - usable.toSet() else selected + usable)
                        }
                    )
                }
            }
            items(groupRows, key = { it.id }) { row ->
                val checked = row.id in selected && row.enabled
                val toggle = {
                    onSelectedChange(if (row.id in selected) selected - row.id else selected + row.id)
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = row.enabled, onClick = toggle)
                            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, enabled = row.enabled, onCheckedChange = { toggle() })
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                row.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (row.enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            if (row.description.isNotEmpty()) {
                                Text(
                                    row.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            row.note?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                        Text(
                            row.trailing,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportStart(error: String?, bottomPadding: Dp) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Import from a file", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Choose an aznd export file. You then pick exactly which kinds of data to bring in. " +
                        "Anything you already have is kept and duplicates are skipped.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ImportHeader(
    pkg: ImportPackage,
    replaceExisting: Boolean,
    onReplaceChange: (Boolean) -> Unit,
    onChooseOther: () -> Unit,
    onClear: () -> Unit
) {
    val format = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Export file", style = MaterialTheme.typography.titleMedium)
                if (pkg.exportedAt > 0) Text("Made ${format.format(Date(pkg.exportedAt))}", style = MaterialTheme.typography.bodyMedium)
                if (pkg.range != DayRange.All) {
                    Text(
                        "Dated data covers ${pkg.range.startKey ?: "the start"} to ${pkg.range.endKey ?: "today"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    TextButton(onClick = onChooseOther) { Text("Choose another file") }
                    TextButton(onClick = onClear) { Text("Close file") }
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Replace existing data", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Off: add to what you have, skipping duplicates. On: delete the matching data first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = replaceExisting, onCheckedChange = onReplaceChange)
            }
        }
    }
}
