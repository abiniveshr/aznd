package com.ashrdev.aznd.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ashrdev.aznd.ui.common.share.ChipChoice
import com.ashrdev.aznd.ui.common.share.LabeledSlider
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.theme.ColorEngine
import com.ashrdev.aznd.ui.theme.FillAnchor
import com.ashrdev.aznd.ui.theme.FillBase
import com.ashrdev.aznd.ui.theme.FillStyle
import com.ashrdev.aznd.ui.theme.ResolvedFill
import com.ashrdev.aznd.ui.theme.Space
import com.ashrdev.aznd.ui.theme.ThemeDerive
import com.ashrdev.aznd.ui.theme.azndFill
import com.ashrdev.aznd.ui.theme.readableOn
import java.util.Locale
import kotlin.math.roundToInt

private const val GradientAnchorInfo =
    "Stationary: one gradient laid over the whole screen; the element shows the part under it, so it stays still while the element moves or scrolls. " +
        "Mobile: it starts at the element's own corner and moves with it."

private const val PatternAnchorInfo =
    "Stationary: one pattern laid over the whole screen; the element shows the part under it, so it stays still while the element moves or scrolls. " +
        "Mobile: it starts at the element's own corner and moves with it."

private enum class FillPage { MAIN, COLOUR, PATTERNS }

private fun hexOf(argb: Int): String = String.format(Locale.US, "#%06X", 0xFFFFFF and argb)

private fun neutralOf(argb: Int): Int =
    if (ColorEngine.isNeutral(argb)) argb else ColorEngine.smartGrey(ColorEngine.lightness(argb), ColorEngine.alphaOf(argb))

@Composable
fun FillEditDialog(
    title: String,
    baseColor: Int,
    fill: FillStyle,
    onChange: (baseColor: Int, fill: FillStyle) -> Unit,
    onDismiss: () -> Unit,
    shape: Shape = RoundedCornerShape(16.dp),
    fillable: Boolean = true,
    grayscale: Boolean = false,
    chooseBase: Boolean = true,
    copyFrom: List<FillCopySource> = emptyList(),
    preview: (@Composable (ResolvedFill) -> Unit)? = null,
    colourExtra: (@Composable ColumnScope.() -> Unit)? = null
) {
    val shownColor = if (grayscale) neutralOf(baseColor) else baseColor
    val shownFill = if (grayscale) ThemeDerive.mapFill(fill) { neutralOf(it) } else fill
    val resolved = ResolvedFill(Color(shownColor), if (fillable) shownFill else FillStyle.Plain)
    var picking by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(FillPage.MAIN) }
    val colourSummary = when {
        !fillable -> hexOf(shownColor)
        fill.base == FillBase.GRADIENT -> "${fill.gradient.kind.label} gradient, ${fill.gradient.cleanStops().size} colours"
        else -> "Solid ${hexOf(shownColor)}"
    }

    BackHandler(enabled = page != FillPage.MAIN) { page = FillPage.MAIN }

    Dialog(
        onDismissRequest = { if (page != FillPage.MAIN) page = FillPage.MAIN else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f)) {
                Column(modifier = Modifier.fillMaxSize().padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (page != FillPage.MAIN) {
                            IconButton(onClick = { page = FillPage.MAIN }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                        Text(
                            when (page) {
                                FillPage.MAIN -> title
                                FillPage.COLOUR -> "Colour"
                                FillPage.PATTERNS -> "Patterns"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = onDismiss) { Text("Done") }
                    }
                    if (preview != null) {
                        preview(resolved)
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (page == FillPage.MAIN) 140.dp else 72.dp)
                                .clip(shape)
                                .azndFill(resolved, shape, preview = true)
                                .border(1.dp, MaterialTheme.colorScheme.outline, shape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Sample text", color = readableOn(resolved.representative()))
                        }
                    }
                    when (page) {
                        FillPage.MAIN -> Column(
                            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Space.md)
                        ) {
                            if (fillable && fill.base == FillBase.GRADIENT) {
                                AnchorRow("Gradient", fill.gradientAnchor, GradientAnchorInfo) {
                                    onChange(baseColor, fill.copy(gradientAnchor = it))
                                }
                            }
                            if (fillable && fill.patterns.isNotEmpty()) {
                                AnchorRow("Patterns", fill.patternAnchor, PatternAnchorInfo) {
                                    onChange(baseColor, fill.copy(patternAnchor = it))
                                }
                            }
                            PageRow("Colour", colourSummary, "Edit colour") { page = FillPage.COLOUR }
                            if (fillable) {
                                PageRow("Patterns", patternSummary(fill.patterns), "Edit patterns") { page = FillPage.PATTERNS }
                            }
                        }
                        FillPage.COLOUR -> Column(
                            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Space.md)
                        ) {
                            if (colourExtra != null) colourExtra()
                            if (fillable) {
                                FillEditor(
                                    baseLabel = title,
                                    baseColor = baseColor,
                                    fill = fill,
                                    onChange = onChange,
                                    chooseBase = chooseBase,
                                    copyFrom = copyFrom,
                                    grayscale = grayscale,
                                    showAnchors = false,
                                    section = FillSection.COLOUR
                                )
                            } else if (grayscale) {
                                val percent = (ColorEngine.lightness(shownColor) * 100.0).roundToInt().coerceIn(0, 100)
                                LabeledSlider("Brightness", "$percent", percent.toFloat(), 0f..100f) { v ->
                                    onChange(ColorEngine.smartGrey(v.roundToInt() / 100.0, ColorEngine.alphaOf(shownColor)), fill)
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .azndFill(ResolvedFill(Color(baseColor), FillStyle.Plain), CircleShape, preview = true)
                                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                            .clickable { picking = true }
                                    )
                                    Text("Colour")
                                }
                            }
                        }
                        FillPage.PATTERNS -> Column(
                            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Space.md)
                        ) {
                            FillEditor(
                                baseLabel = title,
                                baseColor = baseColor,
                                fill = fill,
                                onChange = onChange,
                                chooseBase = chooseBase,
                                copyFrom = copyFrom,
                                grayscale = grayscale,
                                showAnchors = false,
                                section = FillSection.PATTERNS
                            )
                        }
                    }
                }
            }
        }
    }

    if (picking) {
        AdvancedColorPickerDialog(
            title = title,
            initial = baseColor,
            onDismiss = { picking = false },
            onPick = { picked ->
                onChange(picked, fill)
                picking = false
            }
        )
    }
}

@Composable
private fun PageRow(label: String, summary: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun AnchorRow(label: String, selected: FillAnchor, info: String, onSelect: (FillAnchor) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            InfoButton(text = info, title = label)
        }
        ChipChoice(FillAnchor.entries, selected, { it.label }, onSelect)
    }
}
