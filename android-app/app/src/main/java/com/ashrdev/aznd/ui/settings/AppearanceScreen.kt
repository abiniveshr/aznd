package com.ashrdev.aznd.ui.settings

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.AdvancedColorPickerDialog
import com.ashrdev.aznd.ui.common.FillCopySource
import com.ashrdev.aznd.ui.common.FillEditorDialog
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.components.AlertDialog
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField
import com.ashrdev.aznd.ui.theme.ContrastBase
import com.ashrdev.aznd.ui.theme.ContrastShade
import com.ashrdev.aznd.ui.theme.HighContrast
import com.ashrdev.aznd.ui.theme.MAX_BORDER_DP
import com.ashrdev.aznd.ui.theme.Palette
import com.ashrdev.aznd.ui.theme.ThemeColor
import com.ashrdev.aznd.ui.theme.ThemeMode
import com.ashrdev.aznd.ui.theme.ThemePreset
import com.ashrdev.aznd.ui.theme.ThemePresets
import com.ashrdev.aznd.ui.theme.ThemeStore
import com.ashrdev.aznd.ui.theme.azndFill
import com.ashrdev.aznd.ui.theme.colorOf
import com.ashrdev.aznd.ui.theme.fillOf
import com.ashrdev.aznd.ui.theme.resolved
import com.ashrdev.aznd.ui.theme.themeFromJson
import com.ashrdev.aznd.ui.theme.toHexString
import com.ashrdev.aznd.ui.theme.toJson
import com.ashrdev.aznd.ui.theme.withColor
import com.ashrdev.aznd.ui.theme.withFill
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val settings by ThemeStore.settings.collectAsState()
    var editingDark by remember { mutableStateOf(false) }
    var pickerFor by remember { mutableStateOf<ThemeColor?>(null) }
    var fillFor by remember { mutableStateOf<ThemeColor?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmPreset by remember { mutableStateOf<ThemePreset?>(null) }
    var pickContrast by remember { mutableStateOf(false) }

    fun editPalette(change: (Palette) -> Palette) {
        ThemeStore.update { s ->
            if (editingDark) s.copy(dark = change(s.dark)) else s.copy(light = change(s.light))
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(ThemeStore.settings.value.toJson().toByteArray())
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
                themeFromJson(text)
            }.onSuccess {
                ThemeStore.replace(it)
                Toast.makeText(context, "Theme imported", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Not a valid aznd theme file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val palette = if (editingDark) settings.dark else settings.light

    pickerFor?.let { element ->
        AdvancedColorPickerDialog(
            title = element.label,
            initial = palette.colorOf(element).toArgb(),
            onDismiss = { pickerFor = null },
            onPick = { picked ->
                editPalette { it.withColor(element, Color(picked)) }
                pickerFor = null
            }
        )
    }

    val contrast = settings.contrast
    if (pickContrast && contrast != null) {
        AdvancedColorPickerDialog(
            title = "Base colour",
            initial = contrast.color.toArgb(),
            onDismiss = { pickContrast = false },
            onPick = { picked ->
                ThemeStore.update { HighContrast.apply(it, contrast.copy(color = Color(picked))) }
                pickContrast = false
            }
        )
    }

    fillFor?.let { element ->
        FillEditorDialog(
            title = element.label,
            baseLabel = "Colour",
            baseColor = palette.colorOf(element).toArgb(),
            fill = palette.fillOf(element),
            onDismiss = { fillFor = null },
            onApply = { color, fill ->
                editPalette { it.withColor(element, Color(color)).withFill(element, fill) }
                fillFor = null
            },
            // Every other element of the palette being edited (light or dark), as it is now.
            copyFrom = ThemeColor.entries.filter { it != element }.map { other ->
                FillCopySource(other.label, palette.colorOf(other).toArgb(), palette.fillOf(other))
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset theme?") },
            text = { Text("All colours, gradients, patterns, corners and borders go back to the defaults.") },
            confirmButton = {
                TextButton(onClick = {
                    ThemeStore.reset()
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            }
        )
    }

    confirmPreset?.let { preset ->
        AlertDialog(
            onDismissRequest = { confirmPreset = null },
            title = { Text(preset.name) },
            text = { Text("Replace your colours, patterns and borders with this theme? Your corner sizes stay as they are.") },
            confirmButton = {
                TextButton(onClick = {
                    ThemeStore.update { ThemePresets.applyTo(it, preset) }
                    confirmPreset = null
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { confirmPreset = null }) { Text("Cancel") }
            }
        )
    }

    ScreenScaffold(
        title = "Appearance",
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        settingsEnabled = false
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize()) {
            // Pinned preview: stays put while the options below scroll.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Preview", style = MaterialTheme.typography.titleMedium)
                        Card(highlight = true) {
                            Text(
                                "Highlight",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = padding.calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SectionTitle("Preset themes")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(ThemePresets.all) { preset ->
                        PresetTile(preset) { confirmPreset = preset }
                    }
                }

                // The high contrast theme's own setting: one base colour, and how its shades are made.
                if (contrast != null) {
                    HighContrastCard(
                        base = contrast,
                        onPickColor = { pickContrast = true },
                        onShade = { shade -> ThemeStore.update { HighContrast.apply(it, contrast.copy(shade = shade)) } }
                    )
                }

                SectionTitle("Theme mode")
                SegmentedChoice(
                    options = listOf("System", "Light", "Dark"),
                    selected = settings.mode.ordinal,
                    onSelect = { i -> ThemeStore.update { it.copy(mode = ThemeMode.entries[i]) } }
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchRow(
                        label = "Use system wallpaper colours",
                        checked = settings.useDynamicColor,
                        onChange = { v -> ThemeStore.update { it.copy(useDynamicColor = v) } }
                    )
                }

                AppearanceSection("Corners") {
                    SliderRow(
                        label = "Cards, fields and dialogs",
                        value = settings.cardCorner,
                        range = 0..32,
                        onChange = { v -> ThemeStore.update { it.copy(cardCorner = v) } }
                    )
                    SliderRow(
                        label = "Buttons",
                        value = settings.buttonCorner,
                        range = 0..50,
                        onChange = { v -> ThemeStore.update { it.copy(buttonCorner = v) } }
                    )
                }

                AppearanceSection("Borders") {
                    Text(
                        "Width of each border, drawn in the Outlines colour. 0 turns it off.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    BorderSlider("Cards", settings.cardBorder) { v -> ThemeStore.update { it.copy(cardBorder = v) } }
                    BorderSlider("Top bar", settings.topBarBorder) { v -> ThemeStore.update { it.copy(topBarBorder = v) } }
                    BorderSlider("Bottom bar", settings.bottomBarBorder) { v -> ThemeStore.update { it.copy(bottomBarBorder = v) } }
                    BorderSlider("Text fields", settings.fieldBorder) { v -> ThemeStore.update { it.copy(fieldBorder = v) } }
                    BorderSlider("Outlined buttons", settings.outlinedButtonBorder) { v ->
                        ThemeStore.update { it.copy(outlinedButtonBorder = v) }
                    }
                    BorderSlider("Filled buttons", settings.filledButtonBorder) { v ->
                        ThemeStore.update { it.copy(filledButtonBorder = v) }
                    }
                }

                AppearanceSection("Colours, gradients and patterns") {
                    SegmentedChoice(
                        options = listOf("Editing light", "Editing dark"),
                        selected = if (editingDark) 1 else 0,
                        onSelect = { i -> editingDark = i == 1 }
                    )
                    if (settings.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Text(
                            "Colours, gradients and patterns below are ignored while system wallpaper colours are on.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    ThemeColor.entries.forEach { element ->
                        ElementRow(
                            element = element,
                            palette = palette,
                            onClick = { if (element.fillable) fillFor = element else pickerFor = element }
                        )
                    }
                }

                AppearanceSection("Data") {
                    // Wraps onto a second line on a narrow screen rather than squeezing the buttons.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { exportLauncher.launch("aznd-theme.json") }) { Text("Export") }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Import") }
                        OutlinedButton(onClick = { confirmReset = true }) { Text("Reset") }
                    }
                }
            }
        }
    }
}

/**
 * The high contrast theme's settings: the base colour (tap the swatch to change it) and whether its shades are
 * made by opacity or by saturation. Everything else in the theme is worked out from these two.
 */
@Composable
private fun HighContrastCard(
    base: ContrastBase,
    onPickColor: () -> Unit,
    onShade: (ContrastShade) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("High contrast", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onPickColor)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Base colour")
                    Text(base.color.toHexString(), style = MaterialTheme.typography.bodySmall)
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(base.color)
                        .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                )
            }
            Text("Shades by", style = MaterialTheme.typography.bodyMedium)
            SegmentedChoice(
                options = ContrastShade.entries.map { it.label },
                selected = base.shade.ordinal,
                onSelect = { i -> onShade(ContrastShade.entries[i]) }
            )
            // The shades the choice makes, so the person sees what each does before leaving the page.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1f, HighContrast.OUTLINE, HighContrast.HIGHLIGHT).forEach { k ->
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(HighContrast.shade(base, k))
                    )
                }
            }
            Text(
                if (base.shade == ContrastShade.ALPHA) "Highlights and outlines are the base colour at less opacity over black."
                else "Highlights and outlines are the base colour with less saturation.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

/**
 * A section that opens and closes like a drop-down: tap its header to show or hide what is inside.
 * Starts closed; whether it is open survives rotating the phone.
 */
@Composable
private fun AppearanceSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = if (open) "Collapse $title" else "Expand $title",
                modifier = Modifier.rotate(if (open) 180f else 0f)
            )
        }
        AnimatedVisibility(visible = open) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentedChoice(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = selected == i,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size)
            ) { Text(label) }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label)
            Text("$value dp", style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat()
        )
    }
}

/** A border width in half-dp steps, 0 to [MAX_BORDER_DP]. */
@Composable
private fun BorderSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label)
            Text(
                if (value <= 0f) "Off" else String.format(Locale.US, "%.1f dp", value),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Slider(
            value = value.coerceIn(0f, MAX_BORDER_DP),
            onValueChange = { onChange((it * 2f).roundToInt() / 2f) },
            valueRange = 0f..MAX_BORDER_DP,
            steps = (MAX_BORDER_DP * 2).toInt() - 1
        )
    }
}

/** One palette element: its swatch (showing the gradient / pattern), name, what it currently is, and its colour. Tapping it opens its editor. */
@Composable
private fun ElementRow(
    element: ThemeColor,
    palette: Palette,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .azndFill(palette.resolved(element), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(element.label)
                if (element.fillable) {
                    Text(
                        palette.fillOf(element).summary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(palette.colorOf(element).toHexString(), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** A small picture of a preset: its screen, top bar, a card and a button, with their fills. */
@Composable
private fun PresetTile(preset: ThemePreset, onClick: () -> Unit) {
    val pal = preset.settings.dark
    val frame = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .width(124.dp)
                .height(96.dp)
                .clip(frame)
                .azndFill(pal.resolved(ThemeColor.BACKGROUND), frame)
                .border(1.dp, pal.outline, frame)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .azndFill(pal.resolved(ThemeColor.TOP_BAR))
            )
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val cardShape = RoundedCornerShape(6.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(cardShape)
                        .azndFill(pal.resolved(ThemeColor.CARD), cardShape)
                        .border(1.dp, pal.outline, cardShape)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val pill = RoundedCornerShape(50)
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(16.dp)
                            .clip(pill)
                            .azndFill(pal.resolved(ThemeColor.BUTTON), pill)
                    )
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(16.dp)
                            .clip(cardShape)
                            .azndFill(pal.resolved(ThemeColor.HIGHLIGHT), cardShape)
                    )
                }
            }
        }
        Text(preset.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
