package com.ashrdev.aznd.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.FillCopySource
import com.ashrdev.aznd.ui.common.FillEditorDialog
import com.ashrdev.aznd.ui.common.TaperLineControls
import com.ashrdev.aznd.ui.common.rememberImagePicker
import com.ashrdev.aznd.ui.common.share.ChipChoice
import com.ashrdev.aznd.ui.common.share.ColorRow
import com.ashrdev.aznd.ui.common.share.DropdownField
import com.ashrdev.aznd.ui.common.share.FlexFoldSection
import com.ashrdev.aznd.ui.common.share.FlexFont
import com.ashrdev.aznd.ui.common.share.LabeledSlider
import com.ashrdev.aznd.ui.common.share.SwitchRow
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.theme.BorderKind
import com.ashrdev.aznd.ui.theme.FillBase
import com.ashrdev.aznd.ui.theme.LocalAzndStyle
import com.ashrdev.aznd.ui.theme.ThemeColor
import kotlin.math.roundToInt

/**
 * The dashboard's look, shared by every page: Flex's Look settings without its theme presets.
 * [style] changes it (every change is saved and shows on the dashboard straight away).
 */
@Composable
fun DashLookPage(
    look: DashLook,
    style: ((DashLook) -> DashLook) -> Unit,
    onReset: () -> Unit,
    bottom: Dp
) {
    val app = LocalAzndStyle.current
    // The app theme's elements, for the Copy button of the fill dialogs.
    val themeSources = ThemeColor.entries.map { e ->
        val resolved = app.fill(e)
        FillCopySource(e.label, resolved.base.toArgb(), resolved.style)
    }
    var editBackground by remember { mutableStateOf(false) }
    var editTiles by remember { mutableStateOf(false) }
    val picker = rememberImagePicker(aspect = null, outputWidth = 1600, outputHeight = 1600) { uri ->
        style { it.copy(photoUri = uri.toString(), bgMode = DashBgMode.PHOTO) }
    }

    if (editBackground) {
        FillEditorDialog(
            title = "Background",
            baseLabel = "Colour",
            baseColor = look.bgA,
            fill = look.backgroundFill(),
            onDismiss = { editBackground = false },
            onApply = { color, fill ->
                style { l ->
                    if (fill.base == FillBase.GRADIENT) {
                        val stops = fill.gradient.cleanStops()
                        l.copy(
                            bgMode = DashBgMode.GRADIENT,
                            bgA = stops.first().color,
                            bgB = stops.last().color,
                            gradientAngle = fill.gradient.angle,
                            bgGradient = fill.gradient,
                            bgPatterns = fill.patterns
                        )
                    } else {
                        l.copy(bgMode = DashBgMode.SOLID, bgA = color, bgGradient = null, bgPatterns = fill.patterns)
                    }
                }
                editBackground = false
            },
            copyFrom = themeSources
        )
    }
    if (editTiles) {
        FillEditorDialog(
            title = "Tiles",
            baseLabel = "Tile colour",
            baseColor = look.tileColor,
            fill = look.tileFill,
            onDismiss = { editTiles = false },
            onApply = { color, fill ->
                style { it.copy(tileColor = color, tileFill = fill, customColors = true) }
                editTiles = false
            },
            copyFrom = themeSources
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "One look for every dashboard page. Changes show on the dashboard straight away.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        backgroundItems(look, style, onPickPhoto = { picker.launch() }) { editBackground = true }
        item {
            val base = (look.moduleScale * 100).roundToInt()
            FlexFoldSection("Size", "Modules $base%", initiallyOpen = true) {
                LabeledSlider("Module size", "$base%", look.moduleScale, LOOK_MODULE_MIN..LOOK_MODULE_MAX) { v ->
                    style { it.copy(moduleScale = (v * 20f).roundToInt() / 20f) }
                }
                Note("How big every tile's content is drawn, on top of each widget's own S / M / L. Smaller fits more on a page.")
            }
        }
        item {
            FlexFoldSection("Colours", if (look.customColors) "Text, accent and tiles" else "App theme") {
                SwitchRow("Custom colours", look.customColors) { on -> style { it.copy(customColors = on) } }
                if (look.customColors) {
                    ColorRow("Text", look.textColor) { c -> style { it.copy(textColor = c) } }
                    ColorRow("Accent", look.accent) { c -> style { it.copy(accent = c) } }
                    ColorRow("Tiles", look.tileColor) { c -> style { it.copy(tileColor = c) } }
                } else {
                    Note("Text, accent and tile colours follow the app theme.")
                }
            }
        }
        item {
            FlexFoldSection("Text", "${look.font.label} · ${(look.textScale * 100).roundToInt()}%") {
                DropdownField("Font", FlexFont.entries, look.font, { it.label }) { picked -> style { it.copy(font = picked) } }
                LabeledSlider("Size", "${(look.textScale * 100).roundToInt()}%", look.textScale, LOOK_TEXT_MIN..LOOK_TEXT_MAX) { v ->
                    style { it.copy(textScale = (v * 20f).roundToInt() / 20f) }
                }
            }
        }
        item {
            FlexFoldSection("Spacing", "Margins ${look.side.roundToInt()} × ${look.top.roundToInt()} · gap ${look.gap.roundToInt()}") {
                LabeledSlider("Side margins", "${look.side.roundToInt()}", look.side, 0f..LOOK_MARGIN_MAX) { v ->
                    style { it.copy(side = v.roundToInt().toFloat()) }
                }
                LabeledSlider("Top margin", "${look.top.roundToInt()}", look.top, 0f..LOOK_MARGIN_MAX) { v ->
                    style { it.copy(top = v.roundToInt().toFloat()) }
                }
                LabeledSlider("Gap between tiles", "${look.gap.roundToInt()}", look.gap, LOOK_GAP_MIN..LOOK_GAP_MAX) { v ->
                    style { it.copy(gap = v.roundToInt().toFloat()) }
                }
            }
        }
        item { TilesSection(look, style) { editTiles = true } }
        if (look.tileBorderKind == BorderKind.LINE && look.tiles != DashTileStyle.THEME) {
            item {
                FlexFoldSection("Line shape", "${look.lineTaper.shape.label} · ${look.lineTaper.region.label}") {
                    TaperLineControls(look.lineTaper, MaterialTheme.colorScheme.onSurface) { t -> style { it.copy(lineTaper = t) } }
                }
            }
        }
        item {
            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Reset to default") }
        }
    }
}

private fun LazyListScope.backgroundItems(
    look: DashLook,
    style: ((DashLook) -> DashLook) -> Unit,
    onPickPhoto: () -> Unit,
    onAdvanced: () -> Unit
) {
    item {
        FlexFoldSection("Background", look.bgMode.label, initiallyOpen = true) {
            ChipChoice(DashBgMode.entries, look.bgMode, { it.label }) { picked -> style { it.copy(bgMode = picked) } }
            when (look.bgMode) {
                DashBgMode.THEME -> Note("The app theme's background, as on every other screen.")
                DashBgMode.SOLID, DashBgMode.GRADIENT -> {
                    val advanced = look.bgMode == DashBgMode.GRADIENT && look.bgGradient != null
                    if (advanced) {
                        Note("Advanced gradient: ${look.backgroundFill().summary()}.")
                        TextButton(onClick = { style { it.copy(bgGradient = null) } }) { Text("Back to a simple two-colour gradient") }
                    } else {
                        ColorRow(if (look.bgMode == DashBgMode.GRADIENT) "Start colour" else "Colour", look.bgA) { c ->
                            style { it.copy(bgA = c) }
                        }
                        if (look.bgMode == DashBgMode.GRADIENT) {
                            ColorRow("End colour", look.bgB) { c -> style { it.copy(bgB = c) } }
                            LabeledSlider("Angle", "${look.gradientAngle}°", look.gradientAngle.toFloat(), 0f..360f) { v ->
                                style { it.copy(gradientAngle = v.roundToInt()) }
                            }
                        }
                    }
                    val patterns = look.bgPatterns.filter { it.isActive }
                    if (patterns.isNotEmpty()) Note("Patterns: ${patterns.joinToString(", ") { it.kind.label }}")
                    OutlinedButton(onClick = onAdvanced, modifier = Modifier.fillMaxWidth()) {
                        Text("Multi-colour gradient & pattern")
                    }
                }
                DashBgMode.PHOTO -> {
                    Note(if (look.photoUri != null) "Using the photo you chose." else "No photo yet. Choose one to use as the background.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = onPickPhoto) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose photo")
                        }
                        if (look.photoUri != null) {
                            TextButton(onClick = { style { it.copy(photoUri = null) } }) { Text("Clear") }
                        }
                    }
                    LabeledSlider("Blur", "${(look.photoBlur * 100).roundToInt()}%", look.photoBlur, 0f..1f) { v ->
                        style { it.copy(photoBlur = v) }
                    }
                    LabeledSlider("Darken", "${(look.photoDim * 100).roundToInt()}%", look.photoDim, 0f..0.9f) { v ->
                        style { it.copy(photoDim = v) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TilesSection(look: DashLook, style: ((DashLook) -> DashLook) -> Unit, onEditFill: () -> Unit) {
    FlexFoldSection("Tiles", look.tiles.label) {
        ChipChoice(DashTileStyle.entries, look.tiles, { it.label }) { picked -> style { it.copy(tiles = picked) } }
        if (look.tiles == DashTileStyle.THEME) {
            Note("Tiles are the app theme's cards. Pick another style to set their fill, border and corners.")
            return@FlexFoldSection
        }
        if (look.tiles == DashTileStyle.FILLED) {
            LabeledSlider("Opacity", "${(look.tileAlpha * 100).roundToInt()}%", look.tileAlpha, 0.04f..1f) { v ->
                style { it.copy(tileAlpha = v) }
            }
            Note("Fill: ${look.tileFill.summary()}" + if (look.customColors) "" else " (colour from the app theme until you pick one)")
            OutlinedButton(onClick = onEditFill, modifier = Modifier.fillMaxWidth()) { Text("Tile gradient & pattern") }
        }
        val line = look.tileBorderKind == BorderKind.LINE
        if (look.tiles != DashTileStyle.NONE) {
            Text("Tile border", style = MaterialTheme.typography.labelMedium)
            ChipChoice(BorderKind.entries, look.tileBorderKind, { it.label }) { picked -> style { it.copy(tileBorderKind = picked) } }
        } else {
            SwitchRow("Separator lines under tiles", line) { on ->
                style { it.copy(tileBorderKind = if (on) BorderKind.LINE else BorderKind.OUTLINE, tileBorder = if (on && it.tileBorder <= 0f) 1.5f else it.tileBorder) }
            }
        }
        if (look.tiles != DashTileStyle.NONE || line) {
            LabeledSlider(
                if (line) "Line thickness" else "Border",
                if (look.tileBorder <= 0f) "default" else "${(look.tileBorder * 2).roundToInt() / 2f}",
                look.tileBorder, 0f..LOOK_BORDER_MAX
            ) { v -> style { it.copy(tileBorder = (v * 2f).roundToInt() / 2f) } }
            if (look.tileBorder > 0f || line) {
                ColorRow(if (line) "Line colour" else "Border colour", look.tileBorderColor) { c -> style { it.copy(tileBorderColor = c) } }
            }
        }
        LabeledSlider("Corner radius", "${look.tileRadius.roundToInt()}", look.tileRadius, 0f..LOOK_RADIUS_MAX) { v ->
            style { it.copy(tileRadius = v.roundToInt().toFloat()) }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
