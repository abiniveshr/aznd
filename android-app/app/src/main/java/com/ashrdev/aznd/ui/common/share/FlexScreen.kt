package com.ashrdev.aznd.ui.common.share

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

/** Left to right: pictures made before, card presets, per-menu presets. Opens on the middle one. */
private val FlexRootPages = listOf(
    BarPage("Saved", Icons.Default.PhotoLibrary),
    BarPage("Presets", Icons.Default.Dashboard),
    BarPage("Menus", Icons.Default.Menu)
)
private const val PAGE_PRESETS = 1

/** What the editor was opened for: [initial] null is a new preset, else that preset (id 0: made from Default, not stored yet). */
private class EditTarget(val initial: FlexCardPreset?)

/**
 * Flex: the card maker behind every share button. Three swipeable pages, also tabs in the bottom bar: Saved
 * (cards made before), Presets (the card designs, drawn with this [preset]'s real content) and Menus (a placeholder).
 * The + in the top bar, or Edit on a preset, replaces the screen with the preset editor while it is open.
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
    val presetStore = remember { FlexPresetStore(context) }
    val content by produceState<FlexContent?>(null, preset, a, b, s) {
        value = FlexContentLoader.load(preset, a, b, s, workoutViewModel, streakViewModel)
    }
    var presets by remember { mutableStateOf(presetStore.all()) }
    var savedKey by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<EditTarget?>(null) }

    val pagerState = rememberPagerState(initialPage = PAGE_PRESETS, pageCount = { FlexRootPages.size })
    val bar = rememberSubPageBar(pagerState, FlexRootPages)

    val target = editing
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
                    source = preset,
                    content = content,
                    presets = presets,
                    onEdit = { editing = EditTarget(it) },
                    onChanged = { presets = presetStore.all() },
                    onKept = { savedKey++ }
                )
                else -> FlexMenusPage(bottom = bottom)
            }
        }
    }
}
