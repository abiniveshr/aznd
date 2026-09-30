package com.ashrdev.aznd.ui.settings

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField
import com.ashrdev.aznd.ui.theme.ThemeColor
import com.ashrdev.aznd.ui.theme.ThemeMode
import com.ashrdev.aznd.ui.theme.ThemeStore
import com.ashrdev.aznd.ui.theme.colorOf
import com.ashrdev.aznd.ui.theme.themeFromJson
import com.ashrdev.aznd.ui.theme.toHexString
import com.ashrdev.aznd.ui.theme.toJson
import com.ashrdev.aznd.ui.theme.withColor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val settings by ThemeStore.settings.collectAsState()
    var editingDark by remember { mutableStateOf(false) }
    var pickerFor by remember { mutableStateOf<ThemeColor?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

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

    pickerFor?.let { element ->
        val palette = if (editingDark) settings.dark else settings.light
        ColorPickerDialog(
            title = element.label,
            initial = palette.colorOf(element),
            onDismiss = { pickerFor = null },
            onPick = { picked ->
                ThemeStore.update { s ->
                    if (editingDark) s.copy(dark = s.dark.withColor(element, picked))
                    else s.copy(light = s.light.withColor(element, picked))
                }
                pickerFor = null
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset theme?") },
            text = { Text("All colours, corners and outline settings go back to the defaults.") },
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
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
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

                SectionTitle("Corners")
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

                SectionTitle("Outlines")
                SwitchRow("Cards", settings.cardOutline) { v -> ThemeStore.update { it.copy(cardOutline = v) } }
                SwitchRow("Top bar", settings.topBarOutline) { v -> ThemeStore.update { it.copy(topBarOutline = v) } }
                SwitchRow("Text fields", settings.fieldOutline) { v -> ThemeStore.update { it.copy(fieldOutline = v) } }
                SwitchRow("Outlined buttons", settings.outlinedButtonOutline) { v ->
                    ThemeStore.update { it.copy(outlinedButtonOutline = v) }
                }
                SwitchRow("Filled buttons", settings.filledButtonOutline) { v ->
                    ThemeStore.update { it.copy(filledButtonOutline = v) }
                }

                SectionTitle("Colours")
                SegmentedChoice(
                    options = listOf("Editing light", "Editing dark"),
                    selected = if (editingDark) 1 else 0,
                    onSelect = { i -> editingDark = i == 1 }
                )
                Text(
                    "The preview shows the palette for your current Theme mode.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (settings.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Text(
                        "Colours below are ignored while system wallpaper colours are on.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                val palette = if (editingDark) settings.dark else settings.light
                ThemeColor.entries.forEach { element ->
                    ColorRow(
                        label = element.label,
                        color = palette.colorOf(element),
                        onClick = { pickerFor = element }
                    )
                }

                SectionTitle("Share")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("aznd-theme.json") }) { Text("Export") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Import") }
                    OutlinedButton(onClick = { confirmReset = true }) { Text("Reset") }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun ColorRow(label: String, color: Color, onClick: () -> Unit) {
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
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, modifier = Modifier.weight(1f))
        Text(color.toHexString(), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ColorPickerDialog(
    title: String,
    initial: Color,
    onDismiss: () -> Unit,
    onPick: (Color) -> Unit
) {
    var r by remember { mutableStateOf(initial.red * 255f) }
    var g by remember { mutableStateOf(initial.green * 255f) }
    var b by remember { mutableStateOf(initial.blue * 255f) }
    var hex by remember { mutableStateOf(initial.toHexString()) }

    fun current() = Color(r.roundToInt(), g.roundToInt(), b.roundToInt())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(current())
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                )
                Text("Red", style = MaterialTheme.typography.bodySmall)
                Slider(value = r, onValueChange = { r = it; hex = current().toHexString() }, valueRange = 0f..255f)
                Text("Green", style = MaterialTheme.typography.bodySmall)
                Slider(value = g, onValueChange = { g = it; hex = current().toHexString() }, valueRange = 0f..255f)
                Text("Blue", style = MaterialTheme.typography.bodySmall)
                Slider(value = b, onValueChange = { b = it; hex = current().toHexString() }, valueRange = 0f..255f)
                OutlinedTextField(
                    value = hex,
                    onValueChange = { input ->
                        hex = input
                        val parsed = runCatching { Color(android.graphics.Color.parseColor(input)) }.getOrNull()
                        if (parsed != null) {
                            r = parsed.red * 255f
                            g = parsed.green * 255f
                            b = parsed.blue * 255f
                        }
                    },
                    label = { Text("Hex, e.g. #6650A4") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(current()) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
