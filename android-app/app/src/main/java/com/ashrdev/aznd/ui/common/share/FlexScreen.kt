@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ashrdev.aznd.ui.common.share

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.rememberImagePicker
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.common.saveImageToDevice
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private val FlexBarPages = listOf(
    BarPage("Content", Icons.Default.Widgets),
    BarPage("Look", Icons.Default.Palette),
    BarPage("Background", Icons.Default.Image)
)

/** What the shared image picker is currently choosing a photo for. */
private sealed interface PickTarget {
    data object Background : PickTarget
    data class BlockPhoto(val blockId: Int) : PickTarget
}

/**
 * Flex: the card maker behind every share button. Shows a live preview of the card on top and, below it,
 * three tabs (Content, Look, Background) in the bottom bar. Share and Save sit in the top bar.
 * The card starts from the [preset]'s defaults and comes back the way it was last left.
 */
@Composable
fun FlexScreen(
    preset: FlexPreset,
    a: Long,
    b: Long,
    s: String,
    workoutViewModel: WorkoutViewModel,
    streakViewModel: StreakViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { FlexStore(context) }

    val content by produceState<FlexContent?>(null, preset, a, b, s) {
        value = FlexContentLoader.load(preset, a, b, s, workoutViewModel, streakViewModel)
    }
    val defaultSpec = remember(content) { content?.let { preset.defaultSpec(it) } }

    var spec by remember(preset) { mutableStateOf<FlexSpec?>(null) }
    LaunchedEffect(content) {
        val c = content ?: return@LaunchedEffect
        if (spec == null) spec = store.load(preset)?.reconciled() ?: preset.defaultSpec(c)
    }

    // Edits are kept per preset. A card still equal to the preset's default is not stored, so a later
    // workout with a photo still starts from the photo look.
    fun persist(sp: FlexSpec) {
        if (sp == defaultSpec) store.reset(preset) else store.save(preset, sp)
    }
    LaunchedEffect(spec) {
        val sp = spec ?: return@LaunchedEffect
        delay(400.milliseconds)
        persist(sp)
    }
    val latestSpec by rememberUpdatedState(spec)
    val latestDefault by rememberUpdatedState(defaultSpec)
    DisposableEffect(preset) {
        onDispose {
            latestSpec?.let { sp -> if (sp == latestDefault) store.reset(preset) else store.save(preset, sp) }
        }
    }

    val assets = rememberFlexAssets(spec, content)

    fun update(transform: (FlexSpec) -> FlexSpec) {
        spec = spec?.let(transform)
    }
    fun updateStyle(transform: (FlexStyle) -> FlexStyle) = update { it.copy(style = transform(it.style)) }

    var pickTarget by remember { mutableStateOf<PickTarget?>(null) }
    val picker = rememberImagePicker(aspect = null, outputWidth = 1600, outputHeight = 1600) { uri ->
        val uriText = uri.toString()
        when (val target = pickTarget) {
            PickTarget.Background -> updateStyle { it.copy(photoUri = uriText, bgMode = FlexBgMode.PHOTO) }
            is PickTarget.BlockPhoto -> update { sp ->
                sp.copy(blocks = sp.blocks.map { bl -> if (bl.id == target.blockId) bl.copy(keys = listOf(uriText)) else bl })
            }
            null -> Unit
        }
    }

    val pagerState = rememberPagerState(pageCount = { FlexBarPages.size })
    val bar = rememberSubPageBar(pagerState, FlexBarPages)
    val ready = spec != null && content != null

    fun export(then: suspend (Uri) -> Unit) {
        val sp = spec ?: return
        val ct = content ?: return
        scope.launch {
            val uri = withContext(Dispatchers.Default) {
                FlexExport.writePng(context, FlexExport.render(sp, ct, assets))
            }
            if (uri == null) Toast.makeText(context, "Couldn't make the image", Toast.LENGTH_SHORT).show()
            else then(uri)
        }
    }

    ScreenScaffold(
        title = "Flex",
        subtitle = preset.label,
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = bar,
        topBarActions = {
            IconButton(enabled = ready, onClick = { export { uri -> saveImageToDevice(context, uri) } }) {
                Icon(Icons.Default.Download, contentDescription = "Save to gallery")
            }
            IconButton(enabled = ready, onClick = { export { uri -> FlexExport.share(context, uri) } }) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }
        }
    ) { padding ->
        val sp = spec
        val ct = content
        if (sp == null || ct == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val bottom = padding.calculateBottomPadding()
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val boxMaxHeight = maxHeight
                Column(modifier = Modifier.fillMaxSize()) {
                    FlexPreview(
                        spec = sp,
                        content = ct,
                        assets = assets,
                        boxHeight = boxMaxHeight * 0.4f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) { page ->
                        when (page) {
                            0 -> ContentTab(
                                spec = sp,
                                content = ct,
                                bottom = bottom,
                                update = { update(it) },
                                onPickPhoto = { blockId ->
                                    pickTarget = PickTarget.BlockPhoto(blockId)
                                    picker.launch()
                                }
                            )
                            1 -> LookTab(
                                spec = sp,
                                bottom = bottom,
                                style = { updateStyle(it) },
                                onReset = {
                                    defaultSpec?.let { spec = it }
                                    store.reset(preset)
                                }
                            )
                            else -> BackgroundTab(
                                spec = sp,
                                hasDefaultPhoto = ct.photoUri != null,
                                bottom = bottom,
                                style = { updateStyle(it) },
                                onPickPhoto = {
                                    pickTarget = PickTarget.Background
                                    picker.launch()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The card, drawn by the same painter that makes the exported image. */
@Composable
fun FlexPreview(
    spec: FlexSpec,
    content: FlexContent,
    assets: FlexAssets,
    boxHeight: Dp,
    modifier: Modifier = Modifier
) {
    val ratio = spec.style.aspect.width.toFloat() / spec.style.aspect.height
    BoxWithConstraints(
        modifier = modifier.height(boxHeight),
        contentAlignment = Alignment.Center
    ) {
        val width = minOf(maxWidth, boxHeight * ratio)
        Canvas(
            modifier = Modifier
                .width(width)
                .aspectRatio(ratio)
        ) {
            drawIntoCanvas { canvas ->
                FlexPainter.draw(canvas.nativeCanvas, size.width, spec, content, assets)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Content tab: the blocks
// ---------------------------------------------------------------------------------------------

@Composable
private fun ContentTab(
    spec: FlexSpec,
    content: FlexContent,
    bottom: Dp,
    update: ((FlexSpec) -> FlexSpec) -> Unit,
    onPickPhoto: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(spec.blocks, key = { _, block -> block.id }) { index, block ->
            BlockCard(
                block = block,
                index = index,
                count = spec.blocks.size,
                content = content,
                expanded = expanded == block.id,
                onToggleExpanded = { expanded = if (expanded == block.id) null else block.id },
                onChange = { changed ->
                    update { sp -> sp.copy(blocks = sp.blocks.map { if (it.id == block.id) changed else it }) }
                },
                onMove = { delta ->
                    update { sp ->
                        val target = index + delta
                        if (target !in sp.blocks.indices) sp
                        else {
                            val list = sp.blocks.toMutableList()
                            val moved = list.removeAt(index)
                            list.add(target, moved)
                            sp.copy(blocks = list)
                        }
                    }
                },
                onDelete = {
                    update { sp -> sp.copy(blocks = sp.blocks.filter { it.id != block.id }) }
                },
                onPickPhoto = { onPickPhoto(block.id) }
            )
        }
        item {
            Box {
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add block")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    FlexBlockType.entries.filter { content.has(it) }.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label) },
                            onClick = {
                                menu = false
                                update { sp ->
                                    sp.copy(
                                        blocks = sp.blocks + FlexBlock(
                                            id = sp.nextBlockId(),
                                            type = type,
                                            limit = if (type == FlexBlockType.ROUTINE) 10 else 3,
                                            text = if (type == FlexBlockType.TEXT) "Your text here" else ""
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockCard(
    block: FlexBlock,
    index: Int,
    count: Int,
    content: FlexContent,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onChange: (FlexBlock) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onPickPhoto: () -> Unit
) {
    val type = block.type
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = block.enabled, onCheckedChange = { onChange(block.copy(enabled = it)) })
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggleExpanded)) {
                    Text(type.label, style = MaterialTheme.typography.titleMedium)
                    if (!content.has(type)) {
                        Text(
                            "Nothing to show here",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { onMove(-1) }, enabled = index > 0) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = { onMove(1) }, enabled = index < count - 1) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                }
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Hide options" else "Show options"
                    )
                }
            }

            if (expanded) {
                if (type.variants.size > 1) {
                    ChipChoice(
                        options = type.variants.indices.toList(),
                        selected = block.variant,
                        label = { type.variants[it] },
                        onSelect = { onChange(block.copy(variant = it)) }
                    )
                }
                if (type.listy) {
                    val choices = content.choices(type)
                    if (choices.isNotEmpty()) {
                        Text("Show", style = MaterialTheme.typography.labelMedium)
                        SelectionChips(choices.take(24), block.keys) { onChange(block.copy(keys = it)) }
                    }
                    if (block.keys == null) {
                        LabeledSlider(
                            label = "Max items",
                            valueText = block.limit.toString(),
                            value = block.limit.toFloat(),
                            range = 1f..20f,
                            steps = 18
                        ) { onChange(block.copy(limit = it.roundToInt())) }
                    }
                }
                type.flagLabel?.let { label ->
                    SwitchRow(label, block.flag) { onChange(block.copy(flag = it)) }
                }
                if (type == FlexBlockType.HEADER || type == FlexBlockType.TEXT) {
                    OutlinedTextField(
                        value = block.text,
                        onValueChange = { onChange(block.copy(text = it.take(300))) },
                        label = { Text(if (type == FlexBlockType.TEXT) "Text" else "Title (empty = default)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (type == FlexBlockType.PHOTO) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = onPickPhoto) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose photo")
                        }
                        if (block.keys != null) {
                            TextButton(onClick = { onChange(block.copy(keys = null)) }) { Text("Use default") }
                        }
                    }
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Remove block", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SelectionChips(choices: List<Pair<String, String>>, keys: List<String>?, onChange: (List<String>?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(selected = keys == null, onClick = { onChange(null) }, label = { Text("Auto") })
        choices.forEach { (key, label) ->
            FilterChip(
                selected = keys?.contains(key) == true,
                onClick = {
                    val current = keys ?: emptyList()
                    val next = if (key in current) current - key else current + key
                    onChange(next.ifEmpty { null })
                },
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Look tab: colours, text, panels, card shape
// ---------------------------------------------------------------------------------------------

@Composable
private fun LookTab(
    spec: FlexSpec,
    bottom: Dp,
    style: ((FlexStyle) -> FlexStyle) -> Unit,
    onReset: () -> Unit
) {
    val st = spec.style
    val scheme = MaterialTheme.colorScheme
    val appPalette = FlexPalette(
        id = "app",
        label = "App theme",
        bgA = scheme.background.toArgb(),
        bgB = scheme.surfaceVariant.toArgb(),
        text = scheme.onBackground.toArgb(),
        accent = scheme.primary.toArgb(),
        panel = scheme.onBackground.toArgb()
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            FlexSection("Starter looks") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(listOf(appPalette) + FlexPalettes.all) { p ->
                        PaletteChip(p) { style { it.withPalette(p) } }
                    }
                }
            }
        }
        item {
            FlexSection("Format") {
                ChipChoice(FlexAspect.entries, st.aspect, { it.label }) { picked -> style { it.copy(aspect = picked) } }
            }
        }
        item {
            FlexSection("Colours") {
                ColorRow("Text", st.textColor) { c -> style { it.copy(textColor = c) } }
                ColorRow("Accent", st.accent) { c -> style { it.copy(accent = c) } }
                ColorRow("Panels", st.panelColor) { c -> style { it.copy(panelColor = c) } }
            }
        }
        item {
            FlexSection("Text") {
                ChipChoice(FlexFont.entries, st.font, { it.label }) { picked -> style { it.copy(font = picked) } }
                LabeledSlider("Size", "${(st.textScale * 100).roundToInt()}%", st.textScale, 0.7f..1.5f) { v ->
                    style { it.copy(textScale = v) }
                }
                Text("Align", style = MaterialTheme.typography.labelMedium)
                ChipChoice(FlexAlign.entries, st.align, { it.label }) { picked -> style { it.copy(align = picked) } }
                Text("Vertical position", style = MaterialTheme.typography.labelMedium)
                ChipChoice(FlexVAlign.entries, st.vAlign, { it.label }) { picked -> style { it.copy(vAlign = picked) } }
            }
        }
        item {
            FlexSection("Spacing") {
                LabeledSlider("Margins", "${st.padding}", st.padding.toFloat(), 24f..160f) { v ->
                    style { it.copy(padding = v.roundToInt()) }
                }
                LabeledSlider("Gap between blocks", "${st.spacing}", st.spacing.toFloat(), 8f..72f) { v ->
                    style { it.copy(spacing = v.roundToInt()) }
                }
            }
        }
        item {
            FlexSection("Panels") {
                ChipChoice(FlexPanel.entries, st.panel, { it.label }) { picked -> style { it.copy(panel = picked) } }
                if (st.panel == FlexPanel.FILLED) {
                    LabeledSlider("Opacity", "${(st.panelAlpha * 100).roundToInt()}%", st.panelAlpha, 0.04f..0.6f) { v ->
                        style { it.copy(panelAlpha = v) }
                    }
                }
                if (st.panel != FlexPanel.NONE) {
                    LabeledSlider("Corner radius", "${st.panelRadius}", st.panelRadius.toFloat(), 0f..80f) { v ->
                        style { it.copy(panelRadius = v.roundToInt()) }
                    }
                }
            }
        }
        item {
            FlexSection("Card") {
                LabeledSlider("Corner radius", "${st.cardRadius}", st.cardRadius.toFloat(), 0f..160f) { v ->
                    style { it.copy(cardRadius = v.roundToInt()) }
                }
                LabeledSlider("Border", if (st.borderWidth == 0) "off" else "${st.borderWidth}", st.borderWidth.toFloat(), 0f..24f) { v ->
                    style { it.copy(borderWidth = v.roundToInt()) }
                }
                if (st.borderWidth > 0) {
                    ColorRow("Border colour", st.borderColor) { c -> style { it.copy(borderColor = c) } }
                }
                SwitchRow("Watermark", st.watermark) { on -> style { it.copy(watermark = on) } }
                if (st.watermark) {
                    OutlinedTextField(
                        value = st.watermarkText,
                        onValueChange = { text -> style { it.copy(watermarkText = text.take(40)) } },
                        label = { Text("Watermark text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        item {
            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Reset to default") }
        }
    }
}

@Composable
private fun PaletteChip(p: FlexPalette, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(p.bgA), Color(p.bgB))))
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(p.accent))
            )
        }
        Text(p.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Background tab
// ---------------------------------------------------------------------------------------------

@Composable
private fun BackgroundTab(
    spec: FlexSpec,
    hasDefaultPhoto: Boolean,
    bottom: Dp,
    style: ((FlexStyle) -> FlexStyle) -> Unit,
    onPickPhoto: () -> Unit
) {
    val st = spec.style
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            FlexSection("Background") {
                ChipChoice(FlexBgMode.entries, st.bgMode, { it.label }) { picked -> style { it.copy(bgMode = picked) } }
            }
        }
        if (st.bgMode != FlexBgMode.PHOTO) {
            item {
                FlexSection(if (st.bgMode == FlexBgMode.GRADIENT) "Gradient" else "Colour") {
                    ColorRow(if (st.bgMode == FlexBgMode.GRADIENT) "Start colour" else "Colour", st.bgA) { c ->
                        style { it.copy(bgA = c) }
                    }
                    if (st.bgMode == FlexBgMode.GRADIENT) {
                        ColorRow("End colour", st.bgB) { c -> style { it.copy(bgB = c) } }
                        LabeledSlider("Angle", "${st.gradientAngle}°", st.gradientAngle.toFloat(), 0f..360f) { v ->
                            style { it.copy(gradientAngle = v.roundToInt()) }
                        }
                    }
                }
            }
        } else {
            item {
                FlexSection("Photo") {
                    val note = when {
                        st.photoUri != null -> "Using the photo you chose."
                        hasDefaultPhoto -> "Using the photo from this share. Choose another to replace it."
                        else -> "No photo yet. Choose one to use as the background."
                    }
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = onPickPhoto) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose photo")
                        }
                        if (st.photoUri != null) {
                            TextButton(onClick = { style { it.copy(photoUri = null) } }) { Text("Clear") }
                        }
                    }
                    LabeledSlider("Blur", "${(st.photoBlur * 100).roundToInt()}%", st.photoBlur, 0f..1f) { v ->
                        style { it.copy(photoBlur = v) }
                    }
                    LabeledSlider("Darken", "${(st.photoDim * 100).roundToInt()}%", st.photoDim, 0f..0.9f) { v ->
                        style { it.copy(photoDim = v) }
                    }
                }
            }
        }
    }
}
