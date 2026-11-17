package com.ashrdev.aznd.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.ashrdev.aznd.ui.common.FontPicker
import com.ashrdev.aznd.ui.common.TaperLineControls
import com.ashrdev.aznd.ui.theme.MAX_TEXT_SCALE
import com.ashrdev.aznd.ui.theme.MIN_TEXT_SCALE
import com.ashrdev.aznd.ui.theme.Space
import com.ashrdev.aznd.ui.theme.ThemeLibrary
import com.ashrdev.aznd.ui.theme.ThemeStore
import com.ashrdev.aznd.ui.theme.UiDensity
import kotlin.math.roundToInt

@Composable
fun AppearanceShapePage(bottomPadding: Dp) {
    val settings by ThemeStore.draft.collectAsState()
    val working by ThemeLibrary.working.collectAsState()
    val look = working.look

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = Space.lg, end = Space.lg, top = Space.lg, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(Space.lg)
    ) {
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

        AppearanceSection("Spacing") {
            SegmentedChoice(
                options = listOf("Compact", "Regular", "Roomy"),
                selected = settings.density.ordinal,
                onSelect = { i -> ThemeStore.update { it.copy(density = UiDensity.entries[i]) } }
            )
        }

        AppearanceSection("Text") {
            FontPicker(
                "Font",
                settings.appFont,
                { font -> ThemeStore.update { s -> s.copy(appFont = font) } },
                systemDefaultLabel = "System default"
            )
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Text size")
                    Text(
                        "${(settings.textScale * 100f).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Slider(
                    value = settings.textScale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE),
                    onValueChange = { v ->
                        val stepped = ((v * 20f).roundToInt() / 20f).coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
                        ThemeStore.update { it.copy(textScale = stepped) }
                    },
                    valueRange = MIN_TEXT_SCALE..MAX_TEXT_SCALE,
                    steps = 8
                )
            }
        }

        AppearanceSection(
            "Borders",
            info = "Width of each border, drawn in the Outlines colour. 0 turns it off. Line only draws one separator line instead of the outline: under cards, fields, buttons and the top bar, above the bottom bar."
        ) {
            BorderSlider(
                "Cards", look.cardBorder, look.cardBorderLine,
                { on -> ThemeLibrary.editLook { it.copy(cardBorderLine = on) } }
            ) { v -> ThemeLibrary.editLook { it.copy(cardBorder = v) } }
            BorderSlider(
                "Top bar", look.topBarBorder, look.topBarBorderLine,
                { on -> ThemeLibrary.editLook { it.copy(topBarBorderLine = on) } }
            ) { v -> ThemeLibrary.editLook { it.copy(topBarBorder = v) } }
            BorderSlider(
                "Bottom bar", look.bottomBarBorder, look.bottomBarBorderLine,
                { on -> ThemeLibrary.editLook { it.copy(bottomBarBorderLine = on) } }
            ) { v -> ThemeLibrary.editLook { it.copy(bottomBarBorder = v) } }
            BorderSlider(
                "Text fields", look.fieldBorder, look.fieldBorderLine,
                { on -> ThemeLibrary.editLook { it.copy(fieldBorderLine = on) } }
            ) { v -> ThemeLibrary.editLook { it.copy(fieldBorder = v) } }
            BorderSlider(
                "Outlined buttons", look.outlinedButtonBorder, look.outlinedButtonBorderLine,
                { on -> ThemeLibrary.editLook { it.copy(outlinedButtonBorderLine = on) } }
            ) { v -> ThemeLibrary.editLook { it.copy(outlinedButtonBorder = v) } }
            BorderSlider(
                "Filled buttons", look.filledButtonBorder, look.filledButtonBorderLine,
                { on -> ThemeLibrary.editLook { it.copy(filledButtonBorderLine = on) } }
            ) { v -> ThemeLibrary.editLook { it.copy(filledButtonBorder = v) } }
            val anyLine = look.cardBorderLine || look.topBarBorderLine || look.bottomBarBorderLine ||
                look.fieldBorderLine || look.outlinedButtonBorderLine || look.filledButtonBorderLine
            if (anyLine) {
                Text("Line shape", style = MaterialTheme.typography.titleSmall)
                TaperLineControls(look.borderTaper, MaterialTheme.colorScheme.outline) { t ->
                    ThemeLibrary.editLook { it.copy(borderTaper = t) }
                }
            }
        }
    }
}
