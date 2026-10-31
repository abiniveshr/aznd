package com.ashrdev.aznd.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.PrimaryAction
import com.ashrdev.aznd.ui.common.ReorderableItem
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.dragReorderHandle
import com.ashrdev.aznd.ui.common.dragReorderOnLongPress
import com.ashrdev.aznd.ui.common.rememberReorderState
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.components.AlertDialog
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedTextField

private const val PAGE_LOOK = 0
private const val PAGE_PRESETS = 1
private const val PAGE_ACTIVE = 2

private val SettingsPages = listOf(
    BarPage("Look", Icons.Default.Palette),
    BarPage("Presets", Icons.Default.Dashboard),
    BarPage("Pages", Icons.AutoMirrored.Filled.ViewList)
)

/**
 * Dashboard settings, three swipeable pages, opening on the middle one:
 *   Look | Presets | Pages
 *  - Presets: every saved dashboard. Tap one to edit it; rename, duplicate or delete from its menu.
 *  - Look (swipe left): background, tiles, text, spacing and size, shared by every page.
 *  - Pages (swipe right): which presets the dashboard shows, top to bottom.
 */
@Composable
fun DashboardSettingsScreen(
    store: DashboardStore,
    onEditPreset: (Int) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = PAGE_PRESETS) { SettingsPages.size }
    val subPages = rememberSubPageBar(pagerState, SettingsPages)
    // Dialogs: naming a new preset (and whether it then becomes a page), renaming, deleting, picking.
    var creating by remember { mutableStateOf<Boolean?>(null) }
    var renaming by remember { mutableStateOf<DashPreset?>(null) }
    var deleting by remember { mutableStateOf<DashPreset?>(null) }
    var picking by remember { mutableStateOf<PagePick?>(null) }
    var resettingLook by remember { mutableStateOf(false) }
    val presetsFull = store.presets.size >= DashDefaults.MAX_PRESETS
    val allShown = store.presets.all { it.id in store.active }

    creating?.let { asPage ->
        NewPresetDialog(
            suggested = "Page ${store.presets.size + 1}",
            onDismiss = { creating = null },
            onCreate = { name, start ->
                creating = null
                store.newPreset(name, start)?.let { id -> if (asPage) store.addPage(id) }
            }
        )
    }
    renaming?.let { p ->
        NameDialog(
            title = "Rename preset",
            initial = p.name,
            confirm = "Rename",
            onDismiss = { renaming = null },
            onDone = { name ->
                renaming = null
                store.rename(p.id, name)
            }
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${p.name}?") },
            text = { Text("Its widgets and tiling are deleted, and it leaves the dashboard if it is a page. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    store.deletePreset(p.id)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
    picking?.let { pick ->
        PresetPickerDialog(
            title = if (pick.index == null) "Add page" else "Change page",
            presets = store.presets,
            active = store.active,
            current = pick.index?.let { store.active.getOrNull(it) },
            canCreate = !presetsFull,
            onDismiss = { picking = null },
            onPick = { id ->
                picking = null
                val at = pick.index
                if (at == null) store.addPage(id) else store.replacePage(at, id)
            },
            onNew = {
                picking = null
                creating = true
            }
        )
    }
    if (resettingLook) {
        AlertDialog(
            onDismissRequest = { resettingLook = false },
            title = { Text("Reset the look?") },
            text = { Text("Background, tiles, colours, text, spacing and size go back to following the app theme.") },
            confirmButton = {
                TextButton(onClick = {
                    resettingLook = false
                    store.resetLook()
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { resettingLook = false }) { Text("Cancel") } }
        )
    }

    ScreenScaffold(
        title = "Dashboard",
        pageTitle = { page ->
            when (page) {
                PAGE_LOOK -> "Dashboard look"
                PAGE_ACTIVE -> "Dashboard pages"
                else -> "Dashboard presets"
            }
        },
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = subPages,
        pagePrimaryActions = { page ->
            when (page) {
                PAGE_PRESETS -> listOf(
                    PrimaryAction(Icons.Default.Add, if (presetsFull) "Presets are full" else "New preset", enabled = !presetsFull) {
                        creating = false
                    }
                )
                PAGE_ACTIVE -> listOf(
                    PrimaryAction(Icons.Default.Add, "Add page", enabled = !allShown || !presetsFull) {
                        picking = PagePick(null)
                    }
                )
                else -> emptyList()
            }
        }
    ) { padding ->
        val bottom = padding.calculateBottomPadding()
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                PAGE_LOOK -> DashLookPage(
                    look = store.look,
                    style = { change -> store.updateLook(change) },
                    onReset = { resettingLook = true },
                    bottom = bottom
                )
                PAGE_ACTIVE -> ActivePagesList(
                    store = store,
                    bottom = bottom,
                    onChange = { index -> picking = PagePick(index) }
                )
                else -> PresetList(
                    store = store,
                    bottom = bottom,
                    onEdit = onEditPreset,
                    onRename = { renaming = it },
                    onDuplicate = { store.duplicate(it.id) },
                    onDelete = { deleting = it },
                    canDuplicate = !presetsFull
                )
            }
        }
    }
}

/** Picking a preset for a new page ([index] null) or for page [index]. */
private class PagePick(val index: Int?)

// ---- Presets page ----

@Composable
private fun PresetList(
    store: DashboardStore,
    bottom: Dp,
    onEdit: (Int) -> Unit,
    onRename: (DashPreset) -> Unit,
    onDuplicate: (DashPreset) -> Unit,
    onDelete: (DashPreset) -> Unit,
    canDuplicate: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottom + 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "intro") {
            Text(
                "Saved dashboards. Tap one to edit its widgets and tiling. Choose which ones the dashboard shows on Pages; " +
                    "long-press the dashboard to edit the page you're on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(store.presets, key = { it.id }) { p ->
            val page = store.active.indexOf(p.id)
            var menu by remember { mutableStateOf(false) }
            Card(modifier = Modifier.fillMaxWidth().clickable { onEdit(p.id) }, highlight = page >= 0) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            presetSummary(p, page),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onEdit(p.id) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit ${p.name}")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More for ${p.name}")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onRename(p)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                enabled = canDuplicate,
                                onClick = {
                                    menu = false
                                    onDuplicate(p)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = if (store.presets.size > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                enabled = store.presets.size > 1,
                                onClick = {
                                    menu = false
                                    onDelete(p)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun presetSummary(p: DashPreset, page: Int): String {
    val count = p.widgets.size
    val widgets = if (count == 1) "1 widget" else "$count widgets"
    val tiling = if (p.layout != null) "arranged by hand" else "auto-arranged"
    val where = if (page >= 0) "page ${page + 1}" else "not shown"
    return "$widgets · $tiling · $where"
}

// ---- Pages page ----

@Composable
private fun ActivePagesList(store: DashboardStore, bottom: Dp, onChange: (Int) -> Unit) {
    val pages = store.active
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState, firstItemIndex = 1, itemCount = pages.size) { from, to -> store.movePage(from, to) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottom + 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "intro") {
            Text(
                "The dashboard shows these presets, top to bottom: scroll it up and down to move between them. " +
                    "Hold a page or drag its handle to reorder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        itemsIndexed(pages, key = { _, id -> id }) { index, id ->
            val p = store.preset(id) ?: return@itemsIndexed
            ReorderableItem(reorder, id) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.dragReorderOnLongPress(reorder, id).padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Drag to reorder",
                            modifier = Modifier.dragReorderHandle(reorder, id).size(40.dp).padding(8.dp)
                        )
                        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text("${index + 1}. ${p.name}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (p.widgets.size == 1) "1 widget" else "${p.widgets.size} widgets",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onChange(index) }) {
                            Icon(Icons.Default.SwapVert, contentDescription = "Change page")
                        }
                        IconButton(onClick = { store.removePage(index) }, enabled = pages.size > 1) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove page")
                        }
                    }
                }
            }
        }
        if (pages.size == 1) {
            item(key = "one") {
                Text(
                    "The dashboard always keeps one page.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---- dialogs ----

/** Pick a preset for a page. Presets already shown on another page are marked; picking one swaps the two. */
@Composable
private fun PresetPickerDialog(
    title: String,
    presets: List<DashPreset>,
    active: List<Int>,
    current: Int?,
    canCreate: Boolean,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
    onNew: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Adding a page only offers presets that aren't shown yet.
                val choices = presets.filter { if (current == null) it.id !in active else it.id != current }
                for (p in choices) {
                    val shownAt = active.indexOf(p.id)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(p.id) }
                            .padding(vertical = 10.dp)
                    ) {
                        Text(p.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (shownAt >= 0) "On page ${shownAt + 1}: the two swap" else "${p.widgets.size} widgets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (choices.isEmpty()) {
                    Text(
                        "Every preset is already a page.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canCreate) {
                    TextButton(onClick = onNew) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("New preset")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun NewPresetDialog(suggested: String, onDismiss: () -> Unit, onCreate: (String, DashPresetStart) -> Unit) {
    var name by remember { mutableStateOf(suggested) }
    var start by remember { mutableStateOf(DashPresetStart.DEFAULTS) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(DashDefaults.NAME_MAX) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Start with", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in DashPresetStart.entries) {
                        FilterChip(selected = s == start, onClick = { start = s }, label = { Text(s.label) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, start) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(DashDefaults.NAME_MAX) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onDone(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
