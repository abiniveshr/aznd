package com.ashrdev.aznd.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.share.FlexFloatingMenu
import com.ashrdev.aznd.ui.common.share.FlexMenuAction
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.components.AlertDialog
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField
import com.ashrdev.aznd.ui.theme.AzndTheme
import com.ashrdev.aznd.ui.theme.Space
import com.ashrdev.aznd.ui.theme.ThemeKind
import com.ashrdev.aznd.ui.theme.ThemeLibrary
import com.ashrdev.aznd.ui.theme.ThemeMode
import com.ashrdev.aznd.ui.theme.ThemeStore
import kotlinx.coroutines.launch

private val AppearancePages = listOf(
    BarPage("Shape", Icons.Default.Category),
    BarPage("Theme", Icons.Default.Style),
    BarPage("Colours", Icons.Default.Palette)
)

private const val PAGE_SHAPE = 0
private const val PAGE_THEME = 1
private const val PAGE_COLOURS = 2

private enum class ThemeDialog { SAVE_AS, RENAME, RESET, DELETE, IMPORT_UNSAVED }

@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by ThemeStore.draft.collectAsState()
    val pending by ThemeStore.pending.collectAsState()
    val working by ThemeLibrary.working.collectAsState()
    val dirty by ThemeLibrary.dirty.collectAsState()
    val appDark = appShowsDark()
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<ThemeDialog?>(null) }
    val pagerState = rememberPagerState(initialPage = PAGE_THEME) { AppearancePages.size }
    val subPages = rememberSubPageBar(pagerState, AppearancePages)

    DisposableEffect(Unit) { onDispose { ThemeStore.commitNow() } }

    val previewSettings = remember(settings, appDark) {
        settings.copy(mode = if (appDark) ThemeMode.DARK else ThemeMode.LIGHT)
    }

    val bubbleActions = remember(working.kind) {
        buildList {
            add(FlexMenuAction(Icons.Default.Save, "Save") {
                ThemeLibrary.save()
                ThemeStore.commitNow()
            })
            add(FlexMenuAction(Icons.AutoMirrored.Filled.Undo, "Discard") {
                ThemeLibrary.discard()
                ThemeStore.commitNow()
            })
            if (working.kind == ThemeKind.TEMPLATE) {
                add(FlexMenuAction(Icons.Default.SaveAs, "Save as new") { dialog = ThemeDialog.SAVE_AS })
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(ThemeLibrary.exportJson().toByteArray())
                } ?: error("no output stream")
            }.isSuccess
            Toast.makeText(
                context,
                if (ok) "Theme exported" else "Couldn't export the theme",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: error("no input stream")
                ThemeLibrary.importJson(text)
            }.onSuccess {
                ThemeStore.commitNow()
                Toast.makeText(context, "Theme imported", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "That file isn't an aznd theme", Toast.LENGTH_SHORT).show()
            }
        }
    }

    when (dialog) {
        ThemeDialog.SAVE_AS -> ThemeNameDialog(
            title = "Save as new theme",
            initial = "${working.name} copy",
            onDismiss = { dialog = null },
            onConfirm = { name ->
                dialog = null
                ThemeLibrary.saveAsNew(name)
                ThemeStore.commitNow()
            }
        )
        ThemeDialog.RENAME -> ThemeNameDialog(
            title = "Rename theme",
            initial = working.name,
            onDismiss = { dialog = null },
            onConfirm = { name ->
                dialog = null
                ThemeLibrary.rename(working.id, name)
            }
        )
        ThemeDialog.RESET -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Reset ${working.name}?") },
            text = { Text("Its colours, gradients, patterns and borders go back to how they shipped.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    ThemeLibrary.resetBuiltIn()
                    ThemeStore.commitNow()
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
        )
        ThemeDialog.DELETE -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete ${working.name}?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    ThemeLibrary.delete(working.id)
                    ThemeStore.commitNow()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
        )
        ThemeDialog.IMPORT_UNSAVED -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Save changes to ${working.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    ThemeLibrary.save()
                    importLauncher.launch(arrayOf("*/*"))
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { dialog = null }) { Text("Cancel") }
                    TextButton(onClick = {
                        dialog = null
                        ThemeLibrary.discard()
                        importLauncher.launch(arrayOf("*/*"))
                    }) { Text("Discard") }
                }
            }
        )
        null -> {}
    }

    val goToColours = remember(pagerState, scope) {
        { scope.launch { pagerState.animateScrollToPage(PAGE_COLOURS) }; Unit }
    }

    ScreenScaffold(
        title = "Appearance",
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        settingsEnabled = false,
        subPages = subPages,
        topBarActions = {
            if (pending) {
                IconButton(onClick = { ThemeStore.commitNow() }) {
                    Icon(Icons.Default.Check, contentDescription = "Apply")
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Manage theme")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val userTheme = !working.builtIn
                    if (working.kind == ThemeKind.TEMPLATE) {
                        DropdownMenuItem(
                            text = { Text("Save as new theme…") },
                            onClick = {
                                menuOpen = false
                                dialog = ThemeDialog.SAVE_AS
                            }
                        )
                    }
                    if (userTheme) {
                        DropdownMenuItem(
                            text = { Text("Rename theme…") },
                            onClick = {
                                menuOpen = false
                                dialog = ThemeDialog.RENAME
                            }
                        )
                    }
                    if (working.builtIn && ThemeLibrary.isModified(working.id)) {
                        DropdownMenuItem(
                            text = { Text("Reset theme") },
                            onClick = {
                                menuOpen = false
                                dialog = ThemeDialog.RESET
                            }
                        )
                    }
                    if (userTheme) {
                        DropdownMenuItem(
                            text = { Text("Delete theme") },
                            onClick = {
                                menuOpen = false
                                dialog = ThemeDialog.DELETE
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Export theme") },
                        onClick = {
                            menuOpen = false
                            val safe = working.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(' ', '-')
                            exportLauncher.launch("${safe.ifEmpty { "aznd-theme" }}.json")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Import theme") },
                        onClick = {
                            menuOpen = false
                            if (dirty) dialog = ThemeDialog.IMPORT_UNSAVED
                            else importLauncher.launch(arrayOf("*/*"))
                        }
                    )
                }
            }
        }
    ) { padding ->
        val bottom = padding.calculateBottomPadding() + Space.lg
        Column(modifier = Modifier.fillMaxSize()) {
            AzndTheme(previewSettings) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Space.lg, end = Space.lg, top = Space.sm, bottom = Space.xs)
                ) {
                    Column(
                        modifier = Modifier.padding(Space.md),
                        verticalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Preview",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Card(highlight = true) {
                                Text(
                                    "Highlight",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm)
                                )
                            }
                            IconButton(onClick = {
                                ThemeStore.update { it.copy(mode = if (appDark) ThemeMode.LIGHT else ThemeMode.DARK) }
                                ThemeStore.commitNow()
                            }) {
                                Icon(
                                    if (appDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = if (appDark) "Switch to light mode" else "Switch to dark mode"
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Button(onClick = {}) { Text("Button") }
                            OutlinedButton(onClick = {}) { Text("Outlined") }
                        }
                        OutlinedTextField(
                            value = "Text field",
                            onValueChange = {},
                            label = { Text("Label") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        PAGE_SHAPE -> AppearanceShapePage(bottomPadding = bottom)
                        PAGE_THEME -> AppearanceThemePage(bottomPadding = bottom, onCreated = goToColours)
                        else -> AppearanceColoursPage(bottomPadding = bottom)
                    }
                }
                FlexFloatingMenu(
                    actions = bubbleActions,
                    hidden = !dirty,
                    label = "Theme changes",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Space.xs)
                )
            }
        }
    }
}
