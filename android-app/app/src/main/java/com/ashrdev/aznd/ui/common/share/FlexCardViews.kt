@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ashrdev.aznd.ui.common.share

import com.ashrdev.aznd.ui.common.TaperLineControls
import com.ashrdev.aznd.ui.theme.BorderKind
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Shadow
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.FillCopySource
import com.ashrdev.aznd.ui.common.FillEditDialog
import com.ashrdev.aznd.ui.common.FontPicker
import com.ashrdev.aznd.ui.common.ReorderableItem
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.dragReorderHandle
import com.ashrdev.aznd.ui.common.dragReorderOnLongPress
import com.ashrdev.aznd.ui.common.rememberImagePicker
import com.ashrdev.aznd.ui.common.rememberReorderState
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.common.saveImageToDevice
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.ui.theme.FillBase
import com.ashrdev.aznd.ui.theme.FillStyle
import com.ashrdev.aznd.ui.theme.GradientKind
import com.ashrdev.aznd.ui.theme.GradientSpec
import com.ashrdev.aznd.ui.theme.LocalAzndStyle
import com.ashrdev.aznd.ui.theme.ResolvedFill
import com.ashrdev.aznd.ui.theme.Space
import com.ashrdev.aznd.ui.theme.ThemeColor
import com.ashrdev.aznd.ui.theme.ThemePresets
import com.ashrdev.aznd.ui.theme.azndFill
import com.ashrdev.aznd.ui.theme.azndStyleOf
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt


@Composable
private fun FillEditEntry(
    title: String,
    baseColor: Int,
    fill: FillStyle,
    onChange: (Int, FillStyle) -> Unit,
    copyFrom: List<FillCopySource>,
    chooseBase: Boolean = true
) {
    var editing by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .clip(shape)
                .azndFill(ResolvedFill(Color(baseColor), fill), shape, preview = true)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
        )
        OutlinedButton(onClick = { editing = true }) { Text("Edit") }
    }
    if (editing) {
        FillEditDialog(
            title = title,
            baseColor = baseColor,
            fill = fill,
            onChange = onChange,
            onDismiss = { editing = false },
            chooseBase = chooseBase,
            copyFrom = copyFrom
        )
    }
}

/** A page with the live card on top and one tab's controls below it. A warning appears under it when the card is too full. */
@Composable
internal fun EditorPage(
    spec: FlexSpec,
    content: FlexContent,
    assets: FlexAssets,
    plan: FlexPlan,
    veiled: Boolean,
    bottom: Dp,
    controls: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val pageHeight = maxHeight
        var open by rememberSaveable { mutableStateOf(true) }
        Column(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg, vertical = Space.sm)
            ) {
                val cardHeight = maxHeight
                FlexPinchBox(enabled = !open, oneFinger = true, modifier = Modifier.fillMaxSize()) {
                    FlexPreview(
                        spec = spec,
                        content = content,
                        assets = assets,
                        plan = plan,
                        boxHeight = cardHeight,
                        modifier = Modifier.fillMaxWidth(),
                        veiled = veiled
                    )
                }
            }
            FlexWarning(spec, plan, Modifier.padding(horizontal = Space.lg, vertical = Space.xs))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(horizontal = Space.lg, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Look",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (open) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = if (open) "Hide look controls" else "Show look controls"
                )
            }
            AnimatedVisibility(visible = open) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(pageHeight * 0.58f)
                ) { controls() }
            }
            AnimatedVisibility(visible = !open) {
                Spacer(modifier = Modifier.height(bottom))
            }
        }
    }
}

/**
 * The card, drawn by the same painter that makes the exported image. Its shape glides when the size or the
 * content's height changes (Free size follows the content) instead of snapping. [canvasModifier] takes
 * gestures; [overlay] draws on top, given the scale from card pixels to canvas pixels.
 */
@Composable
fun FlexPreview(
    spec: FlexSpec,
    content: FlexContent,
    assets: FlexAssets,
    plan: FlexPlan,
    boxHeight: Dp,
    modifier: Modifier = Modifier,
    canvasModifier: Modifier = Modifier,
    overlay: (DrawScope.(scale: Float) -> Unit)? = null,
    /** Read while drawing (so changes redraw without recomposing): blocks being held or gliding. */
    poses: (() -> FlexPoses?)? = null,
    /**
     * The tiling is being worked out (see [FlexVeil]): the card blurs (and dims, which older Android versions without
     * blur still show), "aznding" pulses over it, and it snaps back sharp when the new tiling is in.
     */
    veiled: Boolean = false,
    /**
     * Something is being resized by hand: the card takes its new shape at once, as the tiles do (an eased shape would
     * trail behind the content and the finger). Otherwise it eases into it with the tiles' glide (see FlexViewPage).
     */
    instantShape: Boolean = false,
    /**
     * Composables laid over the card (buttons on its tiles), given the scale from card pixels to canvas pixels. They sit
     * above the canvas, so only the pixels they cover stop taking the canvas's gestures.
     */
    foreground: (@Composable BoxScope.(scale: Float) -> Unit)? = null
) {
    // The height is what changes (Free size follows its content), so the height is eased, on the tiles' timing: the
    // card's edge then moves in step with the tiles gliding inside it (easing the width / height ratio would not).
    // It is read while laying out (below), so the glide re-measures the card without recomposing this function.
    val tall = animateFloatAsState(
        targetValue = plan.height / plan.width,
        animationSpec = if (instantShape) snap() else flexGlide(FLEX_GLIDE_RATIO),
        label = "flexTall"
    )
    // Into the blur smoothly, out of it at a snap.
    val veil by animateFloatAsState(
        targetValue = if (veiled) 1f else 0f,
        animationSpec = tween(if (veiled) VEIL_IN_MS.toInt() else VEIL_OUT_MS),
        label = "flexVeil"
    )
    BoxWithConstraints(
        modifier = modifier.height(boxHeight),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.layout { m, c ->
                val t = tall.value
                val w = minOf(c.maxWidth.toFloat(), boxHeight.roundToPx() / t).roundToInt()
                val h = (w * t).roundToInt()
                val p = m.measure(Constraints.fixed(w, h))
                layout(w, h) { p.place(0, 0) }
            },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (veil > 0.01f) Modifier.blur(VEIL_BLUR * veil) else Modifier)
                    .then(canvasModifier)
            ) {
                val k = size.width / plan.width
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.clipRect(0f, 0f, size.width, size.height)
                    FlexPainter.draw(native, size.width, spec, content, assets, plan, poses?.invoke())
                    native.restore()
                }
                overlay?.invoke(this, k)
                if (veil > 0.01f) drawRect(Color.Black.copy(alpha = 0.28f * veil))
            }
            if (foreground != null) {
                BoxWithConstraints(modifier = Modifier.matchParentSize()) {
                    foreground(this, constraints.maxWidth.toFloat() / plan.width)
                }
            }
            if (veil > 0.01f) AzndingLabel(Modifier.alpha(veil))
        }
    }
}

/** "aznding": what the card says while its tiling is being worked out, pulsing gently. */
@Composable
private fun AzndingLabel(modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "aznding")
    val glow by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "azndingGlow"
    )
    Text(
        "aznding",
        style = MaterialTheme.typography.titleLarge.copy(
            color = Color.White,
            letterSpacing = 1.5.sp,
            shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 12f)
        ),
        modifier = modifier.alpha(glow)
    )
}

/**
 * The veil over the card preview while a tiling change is worked out (a fit, a mode switch): up while
 * any job holds it. A job that is quick never raises it until its change is ready; then it comes up, the change is
 * made under it, and it stays up at least [VEIL_MIN_MS] in all. A job that takes long raises it after [VEIL_SLOW_MS].
 */
@Stable
internal class FlexVeil {
    var up by mutableStateOf(false)
        private set
    private var holders = 0

    fun raise(): Long {
        holders++
        up = true
        return System.currentTimeMillis()
    }

    /** Lowers the veil raised at [since], once it has been up [VEIL_MIN_MS]. */
    suspend fun lower(since: Long) {
        val left = VEIL_MIN_MS - (System.currentTimeMillis() - since)
        try {
            if (left > 0) delay(left)
        } finally {
            holders = (holders - 1).coerceAtLeast(0)
            up = holders > 0
        }
    }

    /** One tiling job: started with the job, [finish]ed with its change (or none). Its coroutine scope is [scope]. */
    inner class Run(scope: CoroutineScope) {
        private var since = -1L
        private val early = scope.launch {
            delay(VEIL_SLOW_MS)
            since = raise()
        }

        /** The job is done. [change], if any, is made under the veil (brought up first, if it is not up yet). */
        suspend fun finish(change: (() -> Unit)?) {
            early.cancel()
            if (change != null) {
                if (since < 0) {
                    since = raise()
                    delay(VEIL_IN_MS)
                }
                change()
            }
            if (since >= 0) {
                val s = since
                since = -1
                lower(s)
            }
        }
    }
}

/** About how long tiles glide to new places, and the card's shape with them (see [flexGlide], a spring that settles in this time). */
internal const val FLEX_GLIDE_MS = 260

/** The veil: how long it takes to come in, the least it stays up, how quickly it snaps away, and how blurred. */
private const val VEIL_IN_MS = 140L
private const val VEIL_MIN_MS = 500L
private const val VEIL_SLOW_MS = 120L
private const val VEIL_OUT_MS = 110
private val VEIL_BLUR = 14.dp

// ---------------------------------------------------------------------------------------------
// Look tab: colours, text, panels, card shape
// ---------------------------------------------------------------------------------------------

@Composable
internal fun LookTab(
    spec: FlexSpec,
    hasDefaultPhoto: Boolean,
    bottom: Dp,
    style: ((FlexStyle) -> FlexStyle) -> Unit,
    onMode: (FlexLayoutMode) -> Unit,
    onPickPhoto: () -> Unit,
    onReset: () -> Unit
) {
    val st = spec.style
    val app = LocalAzndStyle.current
    // The app theme's elements, for the Copy button of the fill dialogs.
    val themeSources = ThemeColor.entries.map { e ->
        val resolved = app.fill(e)
        FillCopySource(e.label, resolved.base.toArgb(), resolved.style)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.lg, end = Space.lg, top = Space.sm, bottom = bottom + Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        item {
            FlexFoldSection("Starter looks", "Palettes and app themes") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    item {
                        // The app's own theme: screen fill, card fill, borders, corners and colours carry over.
                        val bg = app.fill(ThemeColor.BACKGROUND)
                        LookChip("System theme", bg, app.button.toArgb()) { style { it.withAppTheme(app) } }
                    }
                    items(FlexPalettes.all) { p ->
                        val fill = ResolvedFill(
                            Color(p.bgA),
                            FillStyle.ofGradient(GradientSpec.of(GradientKind.LINEAR, 135, p.bgA, p.bgB))
                        )
                        LookChip(p.label, fill, p.accent) { style { it.withPalette(p) } }
                    }
                }
                Text("App theme presets", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    items(ThemePresets.all) { preset ->
                        val presetStyle = azndStyleOf(preset.settings, dark = true)
                        val look = FlexStyle().withAppTheme(presetStyle)
                        LookChip(preset.name, ResolvedFill(Color(look.bgA), look.backgroundFill()), look.accent) {
                            style { it.withAppTheme(presetStyle) }
                        }
                    }
                }
            }
        }
        backgroundItems(st, hasDefaultPhoto, style, onPickPhoto, themeSources)
        item {
            val base = (st.moduleScale * 100).roundToInt()
            FlexFoldSection("Layout", "${st.layoutMode.label} · ${st.aspect.label} · modules $base%", initiallyOpen = true) {
                Text("Layout mode", style = MaterialTheme.typography.labelMedium)
                ChipChoice(FlexLayoutMode.entries, st.layoutMode, { it.label }) { picked -> onMode(picked) }
                Text(
                    st.layoutMode.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DropdownField("Poster size", FlexAspect.entries, st.aspect, { it.label }) { picked ->
                    style { it.copy(aspect = picked) }
                }
                Text(
                    when {
                        st.aspect == FlexAspect.FREE -> "Grows or shrinks with the content, so nothing is squeezed."
                        st.layoutMode == FlexLayoutMode.FREE -> "Cards off the poster's edges are cut off."
                        else -> "Holds as much as fits without squeezing; the poster tells you when it is full."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        LabeledSlider(
                            "Card size",
                            "$base%",
                            st.moduleScale,
                            FLEX_MODULE_MIN..FLEX_MODULE_MAX
                        ) { v -> style { it.copy(moduleScale = (v * 20f).roundToInt() / 20f, moduleScaleSet = true) } }
                    }
                    // Moving the slider fixes the size; Auto gives it back to best fit.
                    if (st.moduleScaleSet) {
                        TextButton(onClick = { style { it.copy(moduleScale = FLEX_MODULE_DEFAULT, moduleScaleSet = false) } }) { Text("Auto") }
                    }
                }
                Text(
                    if (st.layoutMode == FlexLayoutMode.SPLIT) "The size every card starts from. Tiles then grow or shrink it together to fill the poster; smaller fits more cards."
                    else "How big every card is drawn. Smaller fits more cards.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            FlexFoldSection("Colours", "Text, accent and panels") {
                ColorRow("Text", st.textColor) { c -> style { it.copy(textColor = c) } }
                ColorRow("Accent", st.accent) { c -> style { it.copy(accent = c) } }
                ColorRow("Panels", st.panelColor) { c -> style { it.copy(panelColor = c) } }
            }
        }
        item {
            FlexFoldSection("Text", "${st.font.label} · ${(st.textScale * 100).roundToInt()}%") {
                FontPicker("Font", st.font, { picked -> if (picked != null) style { it.copy(font = picked) } })
                LabeledSlider("Size", "${(st.textScale * 100).roundToInt()}%", st.textScale, 0.7f..1.5f) { v ->
                    style { it.copy(textScale = v) }
                }
            }
        }
        item {
            FlexFoldSection("Spacing", "Margins ${st.padding} × ${st.paddingV} · gap ${st.spacing}") {
                LabeledSlider("Side margins", "${st.padding}", st.padding.toFloat(), 24f..160f) { v ->
                    style { it.copy(padding = v.roundToInt()) }
                }
                LabeledSlider("Top and bottom margins", "${st.paddingV}", st.paddingV.toFloat(), 24f..160f) { v ->
                    style { it.copy(paddingV = v.roundToInt()) }
                }
                LabeledSlider("Gap between tiles", "${st.spacing}", st.spacing.toFloat(), 8f..72f) { v ->
                    style { it.copy(spacing = v.roundToInt()) }
                }
            }
        }
        item {
            FlexFoldSection("Panels", st.panel.label) {
                ChipChoice(FlexPanel.entries, st.panel, { it.label }) { picked -> style { it.copy(panel = picked) } }
                if (st.panel == FlexPanel.FILLED) {
                    // The slider stops at 60% for the see-through look; a themed panel can be fully opaque.
                    LabeledSlider("Opacity", "${(st.panelAlpha * 100).roundToInt()}%", st.panelAlpha, 0.04f..1f) { v ->
                        style { it.copy(panelAlpha = v) }
                    }
                    FillEditEntry(
                        title = "Panel colour",
                        baseColor = st.panelColor,
                        fill = st.panelFill,
                        onChange = { color, fill -> style { it.copy(panelColor = color, panelFill = fill) } },
                        copyFrom = themeSources
                    )
                }
                if (st.panel != FlexPanel.NONE) {
                    Text("Panel border", style = MaterialTheme.typography.labelMedium)
                    ChipChoice(BorderKind.entries, st.panelBorderKind, { it.label }) { picked -> style { it.copy(panelBorderKind = picked) } }
                } else {
                    SwitchRow("Separator lines between tiles", st.panelBorderKind == BorderKind.LINE) { on ->
                        style { it.copy(panelBorderKind = if (on) BorderKind.LINE else BorderKind.OUTLINE) }
                    }
                }
                if (st.panel != FlexPanel.NONE || st.panelBorderKind == BorderKind.LINE) {
                    val line = st.panelBorderKind == BorderKind.LINE
                    LabeledSlider(
                        if (line) "Line thickness" else "Border",
                        if (st.panelBorderWidth == 0) "default" else "${st.panelBorderWidth}",
                        st.panelBorderWidth.toFloat(), 0f..24f
                    ) { v -> style { it.copy(panelBorderWidth = v.roundToInt()) } }
                    if (st.panelBorderWidth > 0 || line) {
                        ColorRow(if (line) "Line colour" else "Panel border colour", st.panelBorderColor) { c -> style { it.copy(panelBorderColor = c) } }
                    }
                    if (line) {
                        Text(
                            "A line runs along every gap between tiles, shaped by Line shape below.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
            FlexFoldSection("Poster", "Corners ${st.cardRadius} · watermark ${if (st.watermark) st.watermarkAlign.label.lowercase() else "off"}") {
                LabeledSlider("Corner radius", "${st.cardRadius}", st.cardRadius.toFloat(), 0f..160f) { v ->
                    style { it.copy(cardRadius = v.roundToInt()) }
                }
                Text("Poster border", style = MaterialTheme.typography.labelMedium)
                ChipChoice(BorderKind.entries, st.borderKind, { it.label }) { picked -> style { it.copy(borderKind = picked) } }
                val cardLine = st.borderKind == BorderKind.LINE
                LabeledSlider(if (cardLine) "Line thickness" else "Border", if (st.borderWidth == 0) "off" else "${st.borderWidth}", st.borderWidth.toFloat(), 0f..48f) { v ->
                    style { it.copy(borderWidth = v.roundToInt()) }
                }
                if (st.borderWidth > 0) {
                    ColorRow(if (cardLine) "Line colour" else "Border colour", st.borderColor) { c -> style { it.copy(borderColor = c) } }
                    if (cardLine) {
                        Text(
                            "A line across the top and the bottom margin, shaped by Line shape below.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                    Text("Watermark position", style = MaterialTheme.typography.labelMedium)
                    ChipChoice(FlexAlign.entries, st.watermarkAlign, { it.label }) { picked -> style { it.copy(watermarkAlign = picked) } }
                }
            }
        }
        if (st.panelBorderKind == BorderKind.LINE || (st.borderKind == BorderKind.LINE && st.borderWidth > 0)) {
            item {
                FlexFoldSection("Line shape", "${st.lineTaper.shape.label} · ${st.lineTaper.region.label}") {
                    TaperLineControls(st.lineTaper, MaterialTheme.colorScheme.onSurface) { t -> style { it.copy(lineTaper = t) } }
                }
            }
        }
        item {
            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Reset to default") }
        }
    }
}

@Composable
private fun LookChip(label: String, fillOf: ResolvedFill, accent: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .azndFill(fillOf, CircleShape, preview = true)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(accent))
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Background (part of the Look tab)
// ---------------------------------------------------------------------------------------------

/** The background section of the Look tab: solid / gradient / photo and the settings of the one picked. */
private fun LazyListScope.backgroundItems(
    st: FlexStyle,
    hasDefaultPhoto: Boolean,
    style: ((FlexStyle) -> FlexStyle) -> Unit,
    onPickPhoto: () -> Unit,
    themeSources: List<FillCopySource>
) {
    item {
        FlexFoldSection("Background", st.bgMode.label) {
            ChipChoice(FlexBgMode.entries, st.bgMode, { it.label }) { picked -> style { it.copy(bgMode = picked) } }
            if (st.bgMode != FlexBgMode.PHOTO) {
                // Solid or gradient, with its patterns: edited right here. Solid / Gradient is the chips above.
                FillEditEntry(
                    title = "Colour",
                    baseColor = st.bgA,
                    fill = st.backgroundFill(),
                    chooseBase = false,
                    onChange = { color, fill ->
                        style { s ->
                            if (fill.base == FillBase.GRADIENT) {
                                val stops = fill.gradient.cleanStops()
                                s.copy(
                                    bgA = stops.first().color,
                                    bgB = stops.last().color,
                                    gradientAngle = fill.gradient.angle,
                                    bgGradient = fill.gradient,
                                    bgPatterns = fill.patterns
                                )
                            } else {
                                s.copy(bgA = color, bgPatterns = fill.patterns)
                            }
                        }
                    },
                    copyFrom = themeSources
                )
            } else {
                val note = when {
                    st.photoUri != null -> "Using the photo you chose."
                    hasDefaultPhoto -> "Using the photo from this share. Choose another to replace it."
                    else -> "No photo yet. Choose one to use as the background."
                }
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onPickPhoto) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(modifier = Modifier.width(Space.sm))
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
