@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ashrdev.aznd.ui.common.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.shape.CircleShape
import com.ashrdev.aznd.ui.components.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.Card

/** A titled card that groups related controls. */
@Composable
fun FlexSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

/** Pick one of [options] with chips. */
@Composable
fun <T> ChipChoice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) }
            )
        }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Swatch(color: Int, selected: Boolean, size: Dp = 32.dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

private val QuickColors = listOf(
    0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFF4F1EA.toInt(), 0xFFFF5A5F.toInt(),
    0xFFFF8A4C.toInt(), 0xFFFFD166.toInt(), 0xFF5CE0A0.toInt(), 0xFF3DD6D0.toInt(),
    0xFF7C9CFF.toInt(), 0xFFC58CFF.toInt(), 0xFFFF7EB6.toInt(), 0xFF9AA0A6.toInt()
)

/** A colour with a row of quick swatches and a "Custom" button that opens the colour picker. */
@Composable
fun ColorRow(label: String, color: Int, onChange: (Int) -> Unit) {
    var dialog by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Swatch(color, selected = false, size = 28.dp) { dialog = true }
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { dialog = true }) { Text("Custom") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(QuickColors) { c -> Swatch(c, selected = c == color) { onChange(c) } }
        }
    }
    if (dialog) {
        ColorPickerDialog(initial = color, onDismiss = { dialog = false }) {
            onChange(it)
            dialog = false
        }
    }
}

/** The app-wide colour picker (square, hue bar, RGB, hex, swatches). Returns an opaque ARGB colour. */
@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    com.ashrdev.aznd.ui.common.AdvancedColorPickerDialog(
        title = "Pick a colour",
        initial = initial,
        onDismiss = onDismiss,
        onPick = onPick
    )
}

// ---------------------------------------------------------------------------------------------
// Dropdowns
// ---------------------------------------------------------------------------------------------

/** The closed look of a dropdown: the current value and an arrow, outlined. */
@Composable
private fun DropdownBox(text: String, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
    }
}

/** Pick one of [options] from a dropdown. */
@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            DropdownBox(optionLabel(selected)) { open = true }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        trailingIcon = if (option == selected) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null,
                        onClick = {
                            open = false
                            onSelect(option)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Pick which items a block shows: "Auto" (the first few) or any number of [choices] as (key, label).
 * [keys] null = Auto. The menu stays open while ticking so several can be picked in one go.
 */
@Composable
fun MultiSelectDropdown(
    label: String,
    choices: List<Pair<String, String>>,
    keys: List<String>?,
    onChange: (List<String>?) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val summary = when {
        keys == null -> "Auto"
        keys.size <= 2 -> choices.filter { it.first in keys }.joinToString(", ") { it.second }.ifBlank { "Auto" }
        else -> "${keys.size} selected"
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            DropdownBox(summary) { open = true }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text("Auto") },
                    leadingIcon = { Checkbox(checked = keys == null, onCheckedChange = null) },
                    onClick = { onChange(null) }
                )
                choices.forEach { (key, name) ->
                    val on = keys?.contains(key) == true
                    DropdownMenuItem(
                        text = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Checkbox(checked = on, onCheckedChange = null) },
                        onClick = {
                            val current = keys ?: emptyList()
                            val next = if (on) current - key else current + key
                            onChange(next.ifEmpty { null })
                        }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Crowding warning
// ---------------------------------------------------------------------------------------------

/** What is wrong with how full the card is, in plain words. Empty when it is fine. */
fun flexWarnings(spec: FlexSpec, plan: FlexPlan): List<String> {
    val out = ArrayList<String>()
    if (plan.hidden.isNotEmpty()) {
        val names = spec.blocks.filter { it.id in plan.hidden }.joinToString(", ") { it.type.label }
        out += "Too many blocks for ${spec.style.aspect.label}: it fits ${plan.limit}. Not shown: $names. " +
            "Turn some off or switch to Free size."
    }
    if (plan.clipped) {
        out += "Some content is too big for its tile and is cut off at the tile's edge. Give that tile more room, show fewer items, or use Free size."
    } else if (plan.minScale < FlexPainter.CROWDED_SCALE) {
        out += "Crowded: some content was shrunk to ${(plan.minScale * 100).toInt()}% to fit its tile. Give it more room, show fewer items or use a taller size."
    }
    if (plan.tooTall) {
        out += "This card is very tall (${plan.height.toInt()} px). Some apps crop or reject images this long."
    }
    return out
}

@Composable
fun FlexWarning(spec: FlexSpec, plan: FlexPlan, modifier: Modifier = Modifier) {
    val messages = flexWarnings(spec, plan)
    if (messages.isEmpty()) return
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = "Warning",
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            messages.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}
