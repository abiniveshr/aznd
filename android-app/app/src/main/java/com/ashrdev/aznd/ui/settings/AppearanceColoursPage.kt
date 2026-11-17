package com.ashrdev.aznd.ui.settings

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.FillCopySource
import com.ashrdev.aznd.ui.common.FillEditDialog
import com.ashrdev.aznd.ui.common.InfoButton
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.theme.Palette
import com.ashrdev.aznd.ui.theme.ResolvedFill
import com.ashrdev.aznd.ui.theme.Space
import com.ashrdev.aznd.ui.theme.ThemeColor
import com.ashrdev.aznd.ui.theme.ThemeDef
import com.ashrdev.aznd.ui.theme.ThemeKind
import com.ashrdev.aznd.ui.theme.ThemeLibrary
import com.ashrdev.aznd.ui.theme.ThemeStore
import com.ashrdev.aznd.ui.theme.azndFill
import com.ashrdev.aznd.ui.theme.colorOf
import com.ashrdev.aznd.ui.theme.fillOf
import com.ashrdev.aznd.ui.theme.readableOn
import com.ashrdev.aznd.ui.theme.resolved
import com.ashrdev.aznd.ui.theme.toHexString

@Composable
fun AppearanceColoursPage(bottomPadding: Dp) {
    val def by ThemeLibrary.working.collectAsState()
    val applied by ThemeStore.draft.collectAsState()
    val base = remember(def.id, applied.baseColor) { ThemeLibrary.baseOf(def.id) }
    val shade = remember(def.id, applied.shade) { ThemeLibrary.shadeOf(def.id) }
    val shown = remember(def, base, shade) { ThemeLibrary.shown(def, base, shade, dark = true) }
    var fillOpen by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = Space.lg, end = Space.lg, top = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.lg)
    ) {
        when (def.kind) {
            ThemeKind.WALLPAPER -> InfoCard(
                "Colours come from your wallpaper",
                "System Wallpaper takes its colours from your phone's wallpaper. Pick another theme, or add a custom one, to edit colours."
            )
            ThemeKind.MONO -> InfoCard(
                "Mono is made from its base colour",
                "Mono is made from its base colour and shade, on the Theme page."
            )
            ThemeKind.TEMPLATE -> TemplateColours(def, shown, fillOpen) { fillOpen = it }
        }
        Spacer(Modifier.height(bottomPadding))
    }
}

@Composable
private fun TemplateColours(def: ThemeDef, shown: Palette, fillOpen: String?, onFillOpen: (String?) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            SectionTitle("Colours")
            if (!def.builtIn) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Variable base colour", modifier = Modifier.weight(1f))
                    InfoButton(
                        text = "Colours follow the base colour picked on the Theme page. You design in greys; each grey becomes the base colour at the same brightness.",
                        title = "Variable base colour"
                    )
                    Switch(checked = def.variable, onCheckedChange = { ThemeLibrary.setVariable(it) })
                }
            }
            ThemeColor.entries.forEach { element ->
                ElementBlock(
                    element = element,
                    def = def,
                    shown = shown,
                    open = fillOpen == element.name,
                    onToggle = { onFillOpen(if (fillOpen == element.name) null else element.name) }
                )
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = Space.lg, top = Space.sm, bottom = Space.sm, end = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            InfoButton(text = text, title = title)
        }
    }
}

@Composable
private fun ElementPreview(
    element: ThemeColor,
    background: ResolvedFill,
    fill: ResolvedFill,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val card = RoundedCornerShape(12.dp)
    val pill = RoundedCornerShape(50)
    val bar = RoundedCornerShape(4.dp)
    val line = MaterialTheme.colorScheme.outline
    when (element) {
        ThemeColor.TEXT -> Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .clip(card)
                .azndFill(background, card, preview = true)
                .border(1.dp, line, card),
            contentAlignment = Alignment.Center
        ) {
            Text("Sample text", color = fill.base)
        }
        ThemeColor.OUTLINE -> Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .clip(card)
                .azndFill(background, card, preview = true)
                .padding(Space.md),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height / 2)
                    .border(3.dp, fill.base, RoundedCornerShape(8.dp))
            )
        }
        else -> {
            val shape = when (element) {
                ThemeColor.BUTTON -> pill
                ThemeColor.TOP_BAR, ThemeColor.BOTTOM_BAR -> bar
                else -> card
            }
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(height)
                    .clip(shape)
                    .azndFill(fill, shape, preview = true)
                    .border(1.dp, line, shape),
                contentAlignment = Alignment.Center
            ) {
                Text(element.label, color = readableOn(fill.representative()), maxLines = 1)
            }
        }
    }
}

@Composable
private fun ElementBlock(
    element: ThemeColor,
    def: ThemeDef,
    shown: Palette,
    open: Boolean,
    onToggle: () -> Unit
) {
    val following = def.variable && element !in def.excluded
    val greys = following && def.tintGreys
    val palette = if (greys) def.design else shown
    val color = palette.colorOf(element)
    val fill = palette.fillOf(element)
    var editing by remember { mutableStateOf(false) }
    val copyFrom = remember(palette, element) {
        ThemeColor.entries.filter { it != element }.map { other ->
            FillCopySource(other.label, palette.colorOf(other).toArgb(), palette.fillOf(other))
        }
    }

    if (editing) {
        FillEditDialog(
            title = element.label,
            baseColor = color.toArgb(),
            fill = fill,
            onChange = { c, f ->
                ThemeLibrary.setElementColor(element, c)
                if (element.fillable) ThemeLibrary.setElementFill(element, f)
            },
            onDismiss = { editing = false },
            fillable = element.fillable,
            grayscale = greys,
            chooseBase = true,
            copyFrom = copyFrom,
            preview = { r -> ElementPreview(element, shown.resolved(ThemeColor.BACKGROUND), r, 110.dp) },
            colourExtra = {
                if (def.variable) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Follow base colour", modifier = Modifier.weight(1f))
                        InfoButton(
                            text = "On: this colour follows the theme's base colour, keeping its brightness. Off: it keeps exactly the colour you set.",
                            title = "Follow base colour"
                        )
                        Switch(checked = following, onCheckedChange = { ThemeLibrary.setFollow(element, it) })
                    }
                }
            }
        )
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(element.label)
                if (element.fillable) {
                    Text(
                        fill.summary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(color.toHexString(), style = MaterialTheme.typography.bodySmall)
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = if (open) "Close ${element.label} preview" else "Show ${element.label} preview",
                modifier = Modifier
                    .padding(start = Space.sm)
                    .rotate(if (open) 180f else 0f)
            )
        }
        AnimatedVisibility(visible = open) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                ElementPreview(element, shown.resolved(ThemeColor.BACKGROUND), palette.resolved(element), 56.dp, Modifier.weight(1f))
                OutlinedButton(onClick = { editing = true }) { Text("Edit") }
            }
        }
    }
}
