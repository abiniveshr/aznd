package com.ashrdev.aznd.ui.common.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ashrdev.aznd.ui.common.tiling.TileTree
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.theme.ThemeColor
import com.ashrdev.aznd.ui.theme.themedFill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * Configure: the step-by-step popup that asks what each card of a poster shows, before the poster is used.
 * One step per card that has a choice (see dataNeed), in the order the cards are on the poster; the poster is drawn
 * on top with the current card lit and the rest dimmed. Skip puts a card back to its default (the value the caller
 * passed in as [initial]).
 */

/** The short side of the preview poster, in pixels: small, it is only a guide. */
private const val PREVIEW_SHORT = 420

/** How long a change waits for the next one before the preview is drawn again. */
private const val PREVIEW_DEBOUNCE_MS = 300L

/** The preview picture and where each card sits on it (in poster pixels, as left, top, width, height). */
private class PreviewShot(val bitmap: Bitmap, val width: Float, val height: Float, val boxes: Map<Int, FloatArray>)

/** The cards of [spec] that have a choice to make, in poster order (the tiling's order, then any it does not hold). */
private fun configureSteps(spec: FlexSpec): List<FlexBlock> {
    val byId = spec.blocks.associateBy { it.id }
    val ordered = TileTree.leaves(spec.layout).mapNotNull { byId[it] }.distinctBy { it.id }
    val rest = spec.blocks.filter { b -> ordered.none { it.id == b.id } }
    return (ordered + rest).filter { it.dataNeed() != null }
}

/** The card laid out for the content, the way the presets page fits a thumbnail. Blocking; call off the main thread. */
private fun fitForPreview(spec: FlexSpec, content: FlexContent, assets: FlexAssets): FlexSpec = runCatching {
    when (spec.style.layoutMode) {
        FlexLayoutMode.SPLIT ->
            if (spec.layout == null) FlexPainter.autoAlign(spec, content, assets, rescale = false).spec
            else FlexPainter.rescue(spec, content, assets) ?: spec
        FlexLayoutMode.FREE -> FlexPainter.placeBoxes(spec, content, assets)
    }
}.getOrDefault(spec)

/** Draws the small poster and finds the box of each of [ids]. Blocking; call off the main thread. */
private fun renderPreview(context: Context, spec: FlexSpec, content: FlexContent, ids: List<Int>): PreviewShot? = runCatching {
    val assets = loadFlexAssets(context, spec, content, backgroundMax = 480, imageMax = 360)
    val fitted = fitForPreview(spec, content, assets)
    val plan = FlexPainter.plan(fitted, content, assets)
    val k = PREVIEW_SHORT / minOf(plan.width, plan.height).coerceAtLeast(1f)
    val w = (plan.width * k).roundToInt().coerceAtLeast(1)
    val h = (plan.height * k).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    FlexPainter.draw(Canvas(bitmap), w.toFloat(), fitted, content, assets, plan)
    val boxes = HashMap<Int, FloatArray>()
    ids.forEach { id ->
        plan.box(id)?.let { boxes[id] = floatArrayOf(it.cellLeft, it.cellTop, it.cellWidth, it.cellHeight) }
    }
    PreviewShot(bitmap, plan.width, plan.height, boxes)
}.getOrNull()

/** What a step is called: the card's type, a Workout stats card by its stat. */
private fun stepName(block: FlexBlock): String =
    if (block.type == FlexBlockType.WORKOUT_STATS) block.stat.label else block.type.label

/**
 * The Configure popup (a full-screen dialog). [initial] holds each card's default choice: where the popup starts and
 * what Skip goes back to. [previewContent] makes the poster's content for the choices so far (the caller wires
 * loadBoundContent); the preview is drawn off the main thread, and again 300 ms after the last change.
 * [onDone] gets all the choices; [onCancel] is the close button, and the system back button on the first step.
 */
@Composable
internal fun FlexConfigureDialog(
    spec: FlexSpec,
    library: FlexLibrary,
    initial: FlexBindings,
    previewContent: suspend (FlexBindings) -> FlexContent,
    onDone: (FlexBindings) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val steps = remember(spec) { configureSteps(spec) }
    var bindings by remember(initial) { mutableStateOf(initial) }
    var step by remember { mutableIntStateOf(0) }

    // A poster with nothing to choose has nothing to ask.
    val finish by rememberUpdatedState(onDone)
    LaunchedEffect(steps.isEmpty()) { if (steps.isEmpty()) finish(initial) }
    if (steps.isEmpty()) return

    val index = step.coerceIn(0, steps.lastIndex)
    val block = steps[index]
    val last = index == steps.lastIndex

    // The preview: the first picture at once, later ones after a short wait so typing does not redraw it per letter.
    val content by rememberUpdatedState(previewContent)
    var shot by remember { mutableStateOf<PreviewShot?>(null) }
    LaunchedEffect(bindings, spec) {
        if (shot != null) delay(PREVIEW_DEBOUNCE_MS)
        val next = withContext(Dispatchers.Default) {
            runCatching { renderPreview(context, spec.bound(bindings), content(bindings), steps.map { it.id }) }.getOrNull()
        }
        if (next != null) shot = next
    }

    fun goBack() {
        if (index > 0) step = index - 1 else onCancel()
    }

    Dialog(
        onDismissRequest = { goBack() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().themedFill(ThemeColor.BACKGROUND),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Cancel") }
                    Text("Set up your poster", style = MaterialTheme.typography.titleLarge)
                }

                PreviewArea(shot = shot, currentId = block.id)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Card ${index + 1} of ${steps.size} · ${stepName(block)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    // Each card starts its choice fresh (lists scrolled to the top, search cleared).
                    key(block.id) {
                        FlexDataChoice(
                            block = block,
                            binding = bindings[block.id] ?: FlexBinding(),
                            library = library,
                            onChange = { nb -> bindings = bindings + (block.id to nb) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { goBack() }, enabled = index > 0) { Text("Back") }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        TextButton(onClick = {
                            // Skip: this card goes back to its default.
                            val default = initial[block.id]
                            val reset = if (default != null) bindings + (block.id to default) else bindings - block.id
                            bindings = reset
                            if (last) onDone(reset) else step = index + 1
                        }) { Text("Skip") }
                    }
                    Button(onClick = { if (last) onDone(bindings) else step = index + 1 }) { Text(if (last) "Done" else "Next") }
                }
            }
        }
    }
}

/** The small poster with the card of [currentId] lit: everything else dimmed, the card outlined. */
@Composable
private fun PreviewArea(shot: PreviewShot?, currentId: Int) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier.fillMaxWidth().height(230.dp).padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (shot == null) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        } else {
            Box(modifier = Modifier.aspectRatio(shot.width / shot.height.coerceAtLeast(1f))) {
                Image(
                    bitmap = shot.bitmap.asImageBitmap(),
                    contentDescription = "Poster preview",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val r = shot.boxes[currentId] ?: return@Canvas
                    val k = size.width / shot.width.coerceAtLeast(1f)
                    val radius = CornerRadius(6.dp.toPx())
                    val hole = Path().apply {
                        addRoundRect(RoundRect(r[0] * k, r[1] * k, (r[0] + r[2]) * k, (r[1] + r[3]) * k, radius))
                    }
                    clipPath(hole, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.55f)) }
                    outlineArea(r, k, primary, 0f, radius)
                }
            }
        }
    }
}

/**
 * One card's data choice, by what the card needs (see [dataNeed]): a title and date, a text, a workout, the lifts, a
 * routine, streaks, a graph's variables, a photo, a calendar's days and period, or a day note's streak. Draws nothing
 * for a card with nothing to choose. [binding] is the card's current choice; [onChange] gets the changed copy.
 */
@Composable
internal fun FlexDataChoice(
    block: FlexBlock,
    binding: FlexBinding,
    library: FlexLibrary,
    onChange: (FlexBinding) -> Unit,
    modifier: Modifier = Modifier,
    /** A Graph card offers only its range (its variable is fixed by the screen the poster was shared from). */
    graphRangeOnly: Boolean = false
) {
    val need = block.dataNeed() ?: return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (need) {
            FlexDataNeed.TITLE -> FlexTitlePicker(block, binding, onChange)
            FlexDataNeed.TEXT -> FlexTextPicker(block, binding, onChange)
            FlexDataNeed.SESSION -> FlexSessionPicker(
                sessions = library.sessions,
                selectedId = binding.sessionId ?: library.sessions.firstOrNull()?.id,
                onPick = { id -> onChange(binding.copy(sessionId = id)) }
            )
            FlexDataNeed.SESSION_EXERCISES -> {
                val selected = binding.sessionId ?: library.sessions.firstOrNull()?.id
                FlexSessionPicker(
                    sessions = library.sessions,
                    selectedId = selected,
                    // Another workout has other exercises: back to all of them.
                    onPick = { id -> onChange(binding.copy(sessionId = id, exerciseNames = null)) }
                )
                FlexSessionExercisesPicker(
                    session = library.sessions.firstOrNull { it.id == selected },
                    names = binding.exerciseNames,
                    onChange = { names -> onChange(binding.copy(exerciseNames = names)) }
                )
            }
            FlexDataNeed.LIFTS -> FlexLiftsPicker(
                exercises = library.exercisesWithData,
                names = binding.exerciseNames,
                single = block.variantLabels.getOrNull(block.variant) == "Hero",
                autoLimit = block.limit,
                onChange = { names -> onChange(binding.copy(exerciseNames = names)) }
            )
            FlexDataNeed.ROUTINE -> FlexRoutinePicker(
                routines = library.routines,
                selectedId = binding.routineId,
                onPick = { id -> onChange(binding.copy(routineId = id)) }
            )
            FlexDataNeed.STREAKS -> FlexStreaksPicker(
                streaks = library.streaks,
                ids = binding.streakIds,
                autoLimit = block.limit,
                onChange = { ids -> onChange(binding.copy(streakIds = ids)) }
            )
            FlexDataNeed.GRAPH -> FlexGraphPicker(block, binding, library.graphs, onChange, rangeOnly = graphRangeOnly)
            FlexDataNeed.PHOTO -> FlexPhotoPicker(
                photos = library.photos,
                uri = binding.photoUri,
                onPick = { uri -> onChange(binding.copy(photoUri = uri)) }
            )
            FlexDataNeed.CALENDAR -> FlexCalendarPicker(block, binding, library.calendars, onChange)
            FlexDataNeed.DAY_NOTE -> FlexDayNotePicker(
                streaks = library.streaks,
                selectedId = binding.noteStreakId,
                onPick = { id -> onChange(binding.copy(noteStreakId = id)) }
            )
        }
    }
}
