package com.ashrdev.aznd.ui.common.share

import android.widget.Toast
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Left to right: pictures made before, card presets, the per-screen defaults (blueprints). Opens on the middle one. */
private val FlexRootPages = listOf(
    BarPage("Saved", Icons.Default.PhotoLibrary),
    BarPage("Presets", Icons.Default.Dashboard),
    BarPage("Defaults", Icons.Default.Tune)
)
private const val PAGE_PRESETS = 1

/** What the editor was opened for: [initial] null is a new preset, else that preset (edited on sample data). */
private class EditTarget(val initial: FlexCardPreset?)

/** The Defaults editor: the [source] screen's blueprint [preset] ("<Name> default"), edited on sample data. */
private class DefaultTarget(val source: FlexPreset, val preset: FlexCardPreset)

/** The Use screen: [preset] with the choices Configure ended with. */
private class UseTarget(val preset: FlexCardPreset, val bindings: FlexBindings)

/**
 * Flex: the poster maker behind every share button. Three swipeable pages, also tabs in the bottom bar: Saved
 * (posters made before), Presets (the designs, drawn with the person's real data; tap one to Configure it and then
 * Use it) and Defaults (one blueprint for each screen with a Share button, edited on sample data).
 * The + in the top bar, or Edit on a preset, replaces the screen with the preset editor while it is open.
 *
 * [preset] is the screen Flex was opened from. With [a], [b] and [s] it tells Configure which workout, exercise,
 * routine or streak day the share started at (see [FlexShareContext]); opened from Home there is no such context.
 * Opened from any other screen than Home, Flex skips the pages: it goes straight to the Use screen with that screen's
 * blueprint filled with that screen's data ([FlexScreenShareFlow]); back leaves Flex.
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
    // A share from a screen (workout, routine, exercise, streak day) has no pages: straight to the Use screen.
    if (preset != FlexPreset.HOME) {
        FlexScreenShareFlow(
            source = preset,
            a = a,
            b = b,
            s = s,
            workoutViewModel = workoutViewModel,
            streakViewModel = streakViewModel,
            onBack = onBack,
            onHome = onHome,
            onOpenSettings = onOpenSettings
        )
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presetStore = remember { FlexPresetStore(context) }
    // The person's data to choose from: loaded once per visit, off the main thread.
    val library by produceState<FlexLibrary?>(null) {
        value = runCatching {
            withContext(Dispatchers.IO) { FlexLibrary.load(context, workoutViewModel, streakViewModel) }
        }.getOrNull()
    }
    // A share from a specific screen (a workout, an exercise ...) carries ids; from Home there is nothing to start from.
    val share = remember(preset, a, b, s) {
        if (preset == FlexPreset.HOME || (a < 0L && b < 0L && s.isBlank())) null else FlexShareContext(preset, a, b, s)
    }
    var presets by remember { mutableStateOf(presetStore.all()) }
    // The built-in presets are made once (on sample data), the first time Flex opens.
    LaunchedEffect(Unit) {
        presetStore.ensureShipped()
        presets = presetStore.all()
    }
    var savedKey by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var configuring by remember { mutableStateOf<FlexCardPreset?>(null) }
    var using by remember { mutableStateOf<UseTarget?>(null) }
    var editingDefault by remember { mutableStateOf<DefaultTarget?>(null) }
    /** Changes when a default was saved or reset, so the Defaults page draws its pictures again. */
    var defaultsKey by remember { mutableIntStateOf(0) }

    val pagerState = rememberPagerState(initialPage = PAGE_PRESETS, pageCount = { FlexRootPages.size })
    val bar = rememberSubPageBar(pagerState, FlexRootPages)

    val target = editing
    val useTarget = using
    val defaultTarget = editingDefault
    if (defaultTarget != null) {
        FlexPresetEditor(
            initial = defaultTarget.preset,
            onSaved = {
                defaultsKey++
                editingDefault = null
            },
            onClose = { editingDefault = null },
            onHome = onHome,
            onOpenSettings = onOpenSettings,
            allowed = defaultTarget.source
        )
        return
    }
    if (target != null) {
        FlexPresetEditor(
            initial = target.initial,
            onSaved = {
                presets = presetStore.all()
                editing = null
            },
            onClose = { editing = null },
            onHome = onHome,
            onOpenSettings = onOpenSettings
        )
        return
    }
    if (useTarget != null) {
        FlexPresetEditor(
            initial = useTarget.preset,
            // "Update preset to current" stores the preset and stays on the Use screen.
            onSaved = { presets = presetStore.all() },
            onClose = { using = null },
            onHome = onHome,
            onOpenSettings = onOpenSettings,
            mode = FlexEditorMode.USE,
            library = library,
            initialBindings = useTarget.bindings,
            workoutViewModel = workoutViewModel,
            streakViewModel = streakViewModel,
            onKept = { savedKey++ }
        )
        return
    }

    ScreenScaffold(
        title = "Flex",
        subtitle = preset.label,
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = bar,
        topBarActions = {
            // Only the Presets page makes presets.
            if (pagerState.currentPage == PAGE_PRESETS) {
                IconButton(onClick = { editing = EditTarget(null) }) {
                    Icon(Icons.Default.Add, contentDescription = "New preset")
                }
            }
        }
    ) { padding ->
        val bottom = padding.calculateBottomPadding()
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> FlexSavedPage(bottom = bottom, refreshKey = savedKey)
                PAGE_PRESETS -> FlexPresetsPage(
                    bottom = bottom,
                    library = library,
                    share = share,
                    workoutViewModel = workoutViewModel,
                    streakViewModel = streakViewModel,
                    presets = presets,
                    onConfigure = { chosen ->
                        if (library == null) {
                            Toast.makeText(context, "Still loading your data", Toast.LENGTH_SHORT).show()
                        } else {
                            configuring = chosen
                        }
                    },
                    onEdit = { editing = EditTarget(it) },
                    onChanged = { presets = presetStore.all() }
                )
                else -> FlexDefaultsPage(
                    bottom = bottom,
                    refreshKey = defaultsKey,
                    onEdit = { source ->
                        // The blueprint is read off the main thread; the editor opens once it is there.
                        scope.launch {
                            val spec = runCatching { FlexDefaultsStore.load(context, source) }.getOrNull()
                            if (spec == null) {
                                Toast.makeText(context, "Couldn't open the default", Toast.LENGTH_SHORT).show()
                            } else {
                                editingDefault = DefaultTarget(
                                    source,
                                    FlexCardPreset(id = 0L, name = source.defaultsName() + " default", spec = spec, updatedAt = 0L)
                                )
                            }
                        }
                    },
                    onChanged = { defaultsKey++ }
                )
            }
        }
    }

    val configTarget = configuring
    val lib = library
    if (configTarget != null && lib != null) {
        val start = remember(configTarget, lib, share) {
            defaultBindings(configTarget.spec, lib, FlexBindingStore.load(context, configTarget.id), share)
        }
        FlexConfigureDialog(
            spec = configTarget.spec,
            library = lib,
            initial = start,
            previewContent = { chosen ->
                loadBoundContent(context, configTarget.spec, chosen, lib, workoutViewModel, streakViewModel)
            },
            onDone = { chosen ->
                configuring = null
                using = UseTarget(configTarget, chosen)
            },
            onCancel = { configuring = null }
        )
    }
}

/** What the screen-share flow starts from: the person's data, the stored blueprint and the blueprint filled for this item. */
private class ScreenStart(val library: FlexLibrary, val blueprint: FlexSpec, val shared: FlexScreenShare)

/**
 * A share started on a screen ([source] is not Home; [a], [b], [s] identify the item, see [FlexShareContext]): no pages,
 * no presets list, no Configure. It loads the person's data and the screen's blueprint ([FlexDefaultsStore.load]), fills
 * the blueprint with the item ([prepareScreenShare]) and opens the very same Use screen as a preset ([FlexPresetEditor]
 * in [FlexEditorMode.USE]) on it, titled "<Name> default". Share goes to the Export screen as usual. Nothing is remembered
 * between such shares (no [FlexBindingStore]); back leaves Flex ([onBack]).
 */
@Composable
private fun FlexScreenShareFlow(
    source: FlexPreset,
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
    val share = remember(source, a, b, s) { FlexShareContext(source, a, b, s) }
    var failed by remember { mutableStateOf(false) }
    val start by produceState<ScreenStart?>(null, share) {
        value = runCatching {
            withContext(Dispatchers.IO) {
                val library = FlexLibrary.load(context, workoutViewModel, streakViewModel)
                val blueprint = FlexDefaultsStore.load(context, source)
                val shared = prepareScreenShare(context, blueprint, share, library, workoutViewModel, streakViewModel)
                ScreenStart(library, blueprint, shared)
            }
        }.getOrNull()
        if (value == null) failed = true
    }
    LaunchedEffect(failed) {
        if (failed) {
            Toast.makeText(context, "Couldn't open the poster maker", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }
    val ready = start
    if (ready == null) {
        ScreenScaffold(
            title = source.defaultsName() + " default",
            subtitle = "Use preset",
            onBack = onBack,
            onHome = onHome,
            onOpenSettings = onOpenSettings
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }
    val preset = remember(ready) {
        FlexCardPreset(id = 0L, name = source.defaultsName() + " default", spec = ready.shared.spec, updatedAt = 0L)
    }
    FlexPresetEditor(
        initial = preset,
        onSaved = {},
        onClose = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        mode = FlexEditorMode.USE,
        library = ready.library,
        initialBindings = ready.shared.bindings,
        workoutViewModel = workoutViewModel,
        streakViewModel = streakViewModel,
        allowed = source,
        blueprint = ready.blueprint,
        share = share
    )
}
