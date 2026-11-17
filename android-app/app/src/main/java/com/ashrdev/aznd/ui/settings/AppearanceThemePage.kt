package com.ashrdev.aznd.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.AdvancedColorPickerDialog
import com.ashrdev.aznd.ui.common.InfoButton
import com.ashrdev.aznd.ui.common.share.DropdownField
import com.ashrdev.aznd.ui.components.AlertDialog
import com.ashrdev.aznd.ui.theme.ContrastShade
import com.ashrdev.aznd.ui.theme.HighContrast
import com.ashrdev.aznd.ui.theme.Space
import com.ashrdev.aznd.ui.theme.ThemeKind
import com.ashrdev.aznd.ui.theme.ThemeLibrary
import com.ashrdev.aznd.ui.theme.ThemeMode
import com.ashrdev.aznd.ui.theme.ThemeStore
import com.ashrdev.aznd.ui.theme.toHexString

private const val NEW_THEME_TARGET = "+new"

@Composable
fun AppearanceThemePage(bottomPadding: Dp, onCreated: () -> Unit) {
    val themes by ThemeLibrary.themes.collectAsState()
    val working by ThemeLibrary.working.collectAsState()
    val dirty by ThemeLibrary.dirty.collectAsState()
    val settings by ThemeStore.draft.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var askBefore by remember { mutableStateOf<String?>(null) }
    var heldBase by remember { mutableStateOf<Int?>(null) }
    var pickBaseFor by remember { mutableStateOf<String?>(null) }
    var pickActive by remember { mutableStateOf(false) }

    fun choose(id: String) {
        ThemeLibrary.select(id)
        ThemeStore.commitNow()
    }

    fun proceed(target: String) {
        val color = heldBase
        heldBase = null
        when {
            target == NEW_THEME_TARGET -> creating = true
            color != null -> {
                ThemeLibrary.setBaseFor(target, color)
                ThemeStore.commitNow()
            }
            else -> choose(target)
        }
    }

    val wheelSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val followsBase = working.kind == ThemeKind.MONO || (working.kind == ThemeKind.TEMPLATE && working.variable)
    val base = remember(working.id, settings.baseColor) { opaque(ThemeLibrary.baseOf(working.id)) }
    val shade = remember(working.id, settings.shade) { ThemeLibrary.shadeOf(working.id) }

    if (creating) {
        ThemeNameDialog(
            title = "New theme",
            initial = nextThemeName(themes),
            onDismiss = { creating = false },
            onConfirm = { name ->
                creating = false
                ThemeLibrary.create(name)
                ThemeStore.commitNow()
                onCreated()
            }
        )
    }

    askBefore?.let { target ->
        AlertDialog(
            onDismissRequest = {
                askBefore = null
                heldBase = null
            },
            title = { Text("Save changes to ${working.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    ThemeLibrary.save()
                    askBefore = null
                    proceed(target)
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        askBefore = null
                        heldBase = null
                    }) { Text("Cancel") }
                    TextButton(onClick = {
                        askBefore = null
                        proceed(target)
                    }) { Text("Discard") }
                }
            }
        )
    }

    pickBaseFor?.let { id ->
        AdvancedColorPickerDialog(
            title = "Base colour",
            initial = opaque(ThemeLibrary.baseOf(id)),
            onDismiss = { pickBaseFor = null },
            onPick = { picked ->
                if (id != working.id && dirty) {
                    heldBase = picked
                    askBefore = id
                } else {
                    ThemeLibrary.setBaseFor(id, picked)
                    ThemeStore.commitNow()
                }
                pickBaseFor = null
            }
        )
    }

    if (pickActive) {
        AdvancedColorPickerDialog(
            title = "Base colour",
            initial = base,
            onDismiss = { pickActive = false },
            onPick = { picked ->
                ThemeLibrary.setBase(picked)
                pickActive = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = Space.lg, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(Space.lg)
    ) {
        Box(modifier = Modifier.padding(horizontal = Space.lg)) { SectionTitle("Themes") }
        LazyRow(
            contentPadding = PaddingValues(horizontal = Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            items(themes, key = { it.id }) { listed ->
                val def = if (listed.id == working.id) working else listed
                val tileFollowsBase = def.kind == ThemeKind.MONO || (def.kind == ThemeKind.TEMPLATE && def.variable)
                val enabled = def.kind != ThemeKind.WALLPAPER || wheelSupported
                ThemeTile(
                    def = def,
                    selected = def.id == working.id,
                    enabled = enabled,
                    onClick = {
                        if (def.id != working.id) {
                            if (dirty) askBefore = def.id else choose(def.id)
                        }
                    },
                    onPickBase = if (tileFollowsBase && enabled) ({ pickBaseFor = def.id }) else null
                )
            }
            item(key = "add") { AddThemeTile(onClick = {
                if (dirty) askBefore = NEW_THEME_TARGET else creating = true
            }) }
        }

        Column(
            modifier = Modifier.padding(horizontal = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            if (followsBase) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { pickActive = true }
                        .padding(vertical = Space.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(base))
                            .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = Space.lg)) {
                        Text("Base colour")
                        Text(Color(base).toHexString(), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { pickActive = true }) { Text("Change") }
                }
            }

            if (working.kind == ThemeKind.MONO) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(modifier = Modifier.weight(1f)) {
                        DropdownField(
                            label = "Shades by",
                            options = ContrastShade.entries.toList(),
                            selected = shade,
                            optionLabel = { it.label },
                            onSelect = { ThemeLibrary.setShade(it) }
                        )
                    }
                    InfoButton(
                        if (shade == ContrastShade.ALPHA) "Highlights and outlines are the base colour at less opacity over black."
                        else "Highlights and outlines are the base colour with less saturation.",
                        title = "Shades by"
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    listOf(1f, HighContrast.OUTLINE, HighContrast.HIGHLIGHT).forEach { k ->
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(HighContrast.shade(base, shade, k))
                        )
                    }
                }
            }

            SectionTitle("Theme mode")
            SegmentedChoice(
                options = listOf("System", "Light", "Dark"),
                selected = settings.mode.ordinal,
                onSelect = { i -> ThemeStore.update { it.copy(mode = ThemeMode.entries[i]) } }
            )
        }
    }
}
