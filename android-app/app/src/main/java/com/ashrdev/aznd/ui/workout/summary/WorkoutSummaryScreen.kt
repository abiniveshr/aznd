package com.ashrdev.aznd.ui.workout.summary

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.workout.SetMode
import com.ashrdev.aznd.domain.workout.formatWeightKg
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.InfoButton
import com.ashrdev.aznd.ui.common.PhotoViewerDialog
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.SearchableDropdown
import com.ashrdev.aznd.ui.common.rememberImagePicker
import com.ashrdev.aznd.ui.common.rememberPhotoLoad
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.common.share.FlexAspect
import com.ashrdev.aznd.ui.components.AlertDialog
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField
import com.ashrdev.aznd.ui.theme.Space
import com.ashrdev.aznd.ui.workout.formatClock
import com.ashrdev.aznd.ui.workout.formatDuration
import com.ashrdev.aznd.ui.workout.formatRpe
import com.ashrdev.aznd.ui.workout.formatTimestamp

private enum class NameAction { SAVE, RENAME }

private const val PAGE_COMPARE = 0
private const val PAGE_SUMMARY = 1
private const val OVERALL = "Overall"
private const val LAST_TIME = "Last time"
private val MinPhotoHeight = 160.dp

private val SummaryPages = listOf(
    BarPage("Compare", Icons.Default.BarChart),
    BarPage("Summary", Icons.Default.Dashboard)
)

@Composable
fun WorkoutSummaryScreen(
    viewModel: WorkoutSummaryViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onShare: (FlexAspect) -> Unit,
    onShareCompare: (Long) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    when (val s = state) {
        is SummaryState.Loading -> ScreenScaffold(
            title = "Workout summary",
            onBack = onBack,
            onHome = onHome,
            onOpenSettings = onOpenSettings
        ) { Box(modifier = Modifier.fillMaxSize()) {} }
        is SummaryState.Missing -> LaunchedEffect(Unit) { onBack() }
        is SummaryState.Ready -> SummaryContent(
            data = s.data,
            viewModel = viewModel,
            onBack = onBack,
            onHome = onHome,
            onOpenSettings = onOpenSettings,
            onShare = onShare,
            onShareCompare = onShareCompare
        )
    }
}

@Composable
private fun SummaryContent(
    data: SummaryData,
    viewModel: WorkoutSummaryViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onShare: (FlexAspect) -> Unit,
    onShareCompare: (Long) -> Unit
) {
    val context = LocalContext.current
    var shareOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var nameAction by remember { mutableStateOf<NameAction?>(null) }
    var confirmUpdate by remember { mutableStateOf(false) }
    val photoPicker = rememberImagePicker { uri -> viewModel.setPhoto(uri.toString()) }
    val pagerState = rememberPagerState(initialPage = PAGE_SUMMARY) { SummaryPages.size }
    val subPages = rememberSubPageBar(pagerState, SummaryPages)
    val elapsed = ((data.finishedAt - data.startedAt) / 1000L).toInt().coerceAtLeast(0)
    val hasMenu = data.canSaveAsPreset || data.canUpdatePreset || data.canRename

    if (shareOpen) {
        AlertDialog(
            onDismissRequest = { shareOpen = false },
            title = { Text("Share format") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    FlexAspect.entries.forEach { aspect ->
                        OutlinedButton(
                            onClick = {
                                shareOpen = false
                                onShare(aspect)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.md)
                            ) {
                                AspectShape(aspect)
                                Text(aspect.label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { shareOpen = false }) { Text("Cancel") } }
        )
    }

    nameAction?.let { action ->
        NameDialog(
            title = if (action == NameAction.SAVE) "Save as preset" else "Rename preset",
            initial = if (action == NameAction.SAVE) data.suggestedName else data.title,
            confirmLabel = "Save",
            onConfirm = { name ->
                nameAction = null
                if (action == NameAction.SAVE) viewModel.saveAsPreset(name) else viewModel.renamePreset(name)
            },
            onDismiss = { nameAction = null }
        )
    }

    if (confirmUpdate) {
        AlertDialog(
            onDismissRequest = { confirmUpdate = false },
            title = { Text("Update preset?") },
            text = { Text("\"${data.title}\" will use the exercises and sets of this session from now on.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUpdate = false
                    viewModel.updatePreset()
                }) { Text("Update") }
            },
            dismissButton = { TextButton(onClick = { confirmUpdate = false }) { Text("Cancel") } }
        )
    }

    ScreenScaffold(
        title = data.title,
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = subPages,
        topBarActions = {
            IconButton(onClick = {
                if (pagerState.currentPage == PAGE_COMPARE) {
                    val thenId = data.compareWithId ?: data.lastTimeId ?: data.compareOptions.firstOrNull()?.id
                    if (thenId == null) {
                        Toast.makeText(context, "Nothing to compare with yet", Toast.LENGTH_SHORT).show()
                    } else {
                        onShareCompare(thenId)
                    }
                } else {
                    shareOpen = true
                }
            }) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }
            if (hasMenu) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (data.canUpdatePreset) {
                            DropdownMenuItem(
                                text = { Text("Update preset to this session") },
                                onClick = {
                                    menuOpen = false
                                    confirmUpdate = true
                                }
                            )
                        }
                        if (data.canRename) {
                            DropdownMenuItem(
                                text = { Text("Rename preset") },
                                onClick = {
                                    menuOpen = false
                                    nameAction = NameAction.RENAME
                                }
                            )
                        }
                        if (data.canSaveAsPreset) {
                            DropdownMenuItem(
                                text = { Text(if (data.freestyle) "Save as preset" else "Save as new preset") },
                                onClick = {
                                    menuOpen = false
                                    nameAction = NameAction.SAVE
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        val bottom = padding.calculateBottomPadding()
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                PAGE_COMPARE -> ComparePage(
                    data = data,
                    bottomPadding = bottom,
                    onCompareWith = { viewModel.setCompareWith(it) }
                )
                else -> SummaryPage(
                    data = data,
                    elapsed = elapsed,
                    bottomPadding = bottom,
                    onPickPhoto = { photoPicker.launch() },
                    onDeletePhoto = { viewModel.setPhoto(null) }
                )
            }
        }
    }
}

@Composable
private fun SummaryPage(
    data: SummaryData,
    elapsed: Int,
    bottomPadding: Dp,
    onPickPhoto: () -> Unit,
    onDeletePhoto: () -> Unit
) {
    val density = LocalDensity.current
    var topHeightPx by remember { mutableIntStateOf(0) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val topHeight = with(density) { topHeightPx.toDp() }
        val photoHeight = (maxHeight - Space.lg - topHeight - Space.md - bottomPadding).coerceAtLeast(MinPhotoHeight)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = Space.lg, end = Space.lg, top = Space.lg, bottom = bottomPadding)
        ) {
            Column(
                modifier = Modifier.onSizeChanged { topHeightPx = it.height },
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                Column {
                    Text(formatTimestamp(data.startedAt), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Finished ${formatClock(data.finishedAt)} · ${formatDuration(elapsed)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                data.totals.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(Space.md)
                    ) {
                        pair.forEach { total ->
                            TotalTile(total, Modifier.weight(1f).fillMaxHeight())
                        }
                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(Space.md))
            SummaryPhoto(
                uri = data.photoUri,
                onPick = onPickPhoto,
                onDelete = onDeletePhoto,
                modifier = Modifier.fillMaxWidth().height(photoHeight)
            )
        }
    }
}

@Composable
private fun SummaryPhoto(uri: String?, onPick: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val load = rememberPhotoLoad(uri, maxDimension = 1600)
    var showFull by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val bitmap = load.bitmap
    if (uri != null && bitmap != null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
                    .clip(shape)
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Session photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { showFull = true }
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Space.sm),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    IconButton(
                        onClick = onPick,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = "Change photo")
                    }
                    IconButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove photo")
                    }
                }
            }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Remove photo?") },
                text = { Text("The photo is removed from this workout.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        onDelete()
                    }) { Text("Remove") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
            )
        }
        if (showFull) {
            PhotoViewerDialog(uriString = uri, onDismiss = { showFull = false })
        }
    } else if (uri != null && load.loading) {
        Card(modifier = modifier) {}
    } else {
        val outline = MaterialTheme.colorScheme.outline
        Box(
            modifier = modifier
                .clip(shape)
                .clickable { onPick() }
                .drawBehind {
                    val stroke = 1.5.dp.toPx()
                    drawRoundRect(
                        color = outline,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                        style = Stroke(
                            width = stroke,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Icon(
                    Icons.Default.AddAPhoto,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Add photo",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ComparePage(data: SummaryData, bottomPadding: Dp, onCompareWith: (Long?) -> Unit) {
    val compareLabels = remember(data.compareOptions) { listOf(LAST_TIME) + data.compareOptions.map { it.label } }
    val compareSelected = data.compareOptions.firstOrNull { it.id == data.compareWithId }?.label ?: LAST_TIME
    var selected by rememberSaveable { mutableStateOf(OVERALL) }
    val options = remember(data.exercises) { listOf(OVERALL) + data.exercises.map { it.name } }
    val exercise = remember(selected, data.exercises) { data.exercises.firstOrNull { it.name == selected } }
    val up = MaterialTheme.colorScheme.primary
    val down = MaterialTheme.colorScheme.error
    val flat = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = Space.lg, end = Space.lg, top = Space.lg)
    ) {
        SearchableDropdown(
            options = compareLabels,
            selected = compareSelected,
            onSelect = { label -> onCompareWith(data.compareOptions.firstOrNull { it.label == label }?.id) },
            modifier = Modifier.fillMaxWidth(),
            label = "Compare with",
            emptyText = "No sessions match"
        )
        Spacer(modifier = Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchableDropdown(
                options = options,
                selected = if (exercise == null) OVERALL else selected,
                onSelect = { selected = it },
                modifier = Modifier.weight(1f),
                emptyText = "No exercises match"
            )
            InfoButton(
                title = "Reading the comparison",
                text = "Each exercise is compared with the last time you did it, or with the earlier session you pick under Compare with; " +
                    "exercises missing from that session have nothing to compare with. " +
                    "Share on this page opens a Then vs now card with that session beside this one. " +
                    "The ± values show how this session differs from that one, for sets, volume and e1RM. " +
                    "e1RM is the estimated one-rep max. For bodyweight exercises it is the added weight you could handle for one rep."
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = Space.md, bottom = bottomPadding + Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            if (exercise == null) {
                items(data.exercises, key = { it.name }) { item ->
                    ExerciseOverviewCard(item, up, down, flat)
                }
            } else {
                item(key = "overview") { ExerciseOverviewCard(exercise, up, down, flat) }
                item(key = "sets") { SetTable(exercise, exercise.comparison, up, down, flat) }
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TotalTile(total: SummaryTotal, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg, vertical = Space.md)
        ) {
            Text(
                total.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(total.value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun AspectShape(aspect: FlexAspect) {
    val color = MaterialTheme.colorScheme.onSurface
    val dashed = aspect.height <= 0
    val boxWidth: Dp
    val boxHeight: Dp
    if (dashed) {
        boxWidth = 22.dp
        boxHeight = 32.dp
    } else {
        val ratio = aspect.width.toFloat() / aspect.height.toFloat()
        if (ratio >= 1f) {
            boxWidth = 40.dp
            boxHeight = (40f / ratio).dp
        } else {
            boxWidth = (40f * ratio).dp
            boxHeight = 40.dp
        }
    }
    Box(modifier = Modifier.width(44.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(boxWidth, boxHeight)) {
            val stroke = 1.5.dp.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(3.dp.toPx()),
                style = Stroke(
                    width = stroke,
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null
                )
            )
        }
    }
}

@Composable
private fun ExerciseOverviewCard(exercise: ExerciseSummary, up: Color, down: Color, flat: Color) {
    val comparison = exercise.comparison
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
            val summary = buildAnnotatedString {
                fun part(value: String, delta: Delta?) {
                    if (length > 0) append("   ")
                    append(value)
                    if (delta != null) {
                        append(" (")
                        withStyle(SpanStyle(color = if (delta.direction > 0) up else if (delta.direction < 0) down else flat)) {
                            append(delta.text)
                        }
                        append(")")
                    }
                }
                part("${exercise.sets.size} sets", comparison?.setCount)
                if (exercise.volumeKg > 0.0) part(formatWeightKg(roundKg(exercise.volumeKg)), comparison?.volume)
                exercise.bestE1rm?.let { part("e1RM ${formatE1rmText(it, exercise.e1rmContext)}", comparison?.e1rm) }
            }
            Text(summary, style = MaterialTheme.typography.bodyMedium)
            if (comparison == null) {
                Text(
                    if (exercise.notInCompared) "Not in that session" else "First time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "vs ${formatTimestamp(comparison.previousAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SetTable(
    exercise: ExerciseSummary,
    comparison: ExerciseComparison?,
    up: Color,
    down: Color,
    flat: Color
) {
    val shape = RoundedCornerShape(10.dp)
    val allTimed = exercise.sets.isNotEmpty() && exercise.sets.all { it.mode == SetMode.TIME }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val stripe = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        SetTableRow(
            number = "#",
            value = if (allTimed) "Time" else "Weight × Reps",
            rpe = "RPE",
            e1rm = "e1RM",
            delta = "±",
            color = flat,
            deltaColor = flat,
            header = true,
            background = MaterialTheme.colorScheme.surfaceVariant
        )
        exercise.sets.forEachIndexed { index, set ->
            val delta = comparison?.sets?.getOrNull(index)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SetTableRow(
                number = "${index + 1}",
                value = formatSetValueText(set),
                rpe = set.rpe?.let { formatRpe(it) } ?: "—",
                e1rm = setE1rm(set, exercise.e1rmContext)?.let { formatE1rmText(it, exercise.e1rmContext) } ?: "—",
                delta = delta?.text ?: "",
                color = onSurface,
                deltaColor = when {
                    delta == null -> flat
                    delta.direction > 0 -> up
                    delta.direction < 0 -> down
                    else -> flat
                },
                header = false,
                background = if (index % 2 == 1) stripe else Color.Transparent
            )
        }
    }
}

@Composable
private fun SetTableRow(
    number: String,
    value: String,
    rpe: String,
    e1rm: String,
    delta: String,
    color: Color,
    deltaColor: Color,
    header: Boolean,
    background: Color
) {
    val style = (if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium)
        .copy(fontFeatureSettings = "tnum")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = Space.sm, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(number, style = style, color = color, modifier = Modifier.width(32.dp))
        Text(value, style = style, color = color, modifier = Modifier.weight(1f))
        Text(rpe, style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
        Text(e1rm, style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.width(76.dp))
        Text(delta, style = style, color = deltaColor, textAlign = TextAlign.End, modifier = Modifier.width(72.dp))
    }
}
