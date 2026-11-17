package com.ashrdev.aznd.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.theme.ScreenBakes
import com.ashrdev.aznd.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BAKED_TEXT =
    "Each theme pattern is drawn once at your screen's resolution and kept as a ready-made image. " +
        "Scrolling, swiping and animations stay smooth, and this uses the least battery. " +
        "In return it holds about 4 to 18 MB of memory for every different pattern on screen (more on sharper screens) " +
        "and usually takes 10 to 50 MB of storage. " +
        "Changing patterns shows a short Applying theme screen while the new images are made."

private const val LIVE_TEXT =
    "Patterns are redrawn from small tiles on every frame. " +
        "Nothing extra is stored and pattern changes apply instantly, but the graphics chip works harder the whole time: " +
        "more battery use and warmth, and older phones can stutter while scrolling or swiping. " +
        "Choosing this deletes the stored pattern images."

private const val OFF_TEXT =
    "Theme and dashboard patterns are not drawn; colours and gradients stay exactly as they are. " +
        "This is the lightest option, with no extra memory, storage or battery use, and the best choice for older phones. " +
        "Choosing this deletes the stored pattern images. " +
        "Posters made in Flex still show their patterns, and the theme editor still previews them."

private const val THREE_D_TEXT =
    "Body screens show the body as a 3D model you can turn and zoom to see every muscle from any side. " +
        "It looks best but uses the most graphics power, memory and battery, and can feel slow on older phones."

private const val TWO_D_TEXT =
    "Body screens show a flat drawing of the body instead of the 3D model. " +
        "It opens instantly and uses far less graphics power, memory and battery: the better choice for older phones."

private val PATTERN_LABELS = listOf("Baked", "Live", "Off")
private val BODY_LABELS = listOf("3D", "2D")

@Composable
fun PerformanceScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val patterns = PerformanceStore.patternMode
    val bodyModel = PerformanceStore.bodyModel
    ScreenScaffold(
        title = "Performance",
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = Space.lg, end = Space.lg, top = Space.lg, bottom = padding.calculateBottomPadding() + Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.xl)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text("Theme patterns", style = MaterialTheme.typography.titleMedium)
                ChoiceRow(
                    options = PATTERN_LABELS,
                    selected = patterns.ordinal,
                    onSelect = { PerformanceStore.setPatterns(PatternMode.entries[it]) }
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(Space.lg),
                        verticalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        Text(
                            when (patterns) {
                                PatternMode.BAKED -> BAKED_TEXT
                                PatternMode.LIVE -> LIVE_TEXT
                                PatternMode.OFF -> OFF_TEXT
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (patterns == PatternMode.BAKED) {
                            val bytes by produceState(0L, patterns) {
                                value = withContext(Dispatchers.IO) { ScreenBakes.storedBytes() }
                            }
                            Text(
                                "Stored on this phone now: ${"%.1f".format(bytes / (1024.0 * 1024.0))} MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text("Body model", style = MaterialTheme.typography.titleMedium)
                ChoiceRow(
                    options = BODY_LABELS,
                    selected = bodyModel.ordinal,
                    onSelect = { PerformanceStore.setBodyModel(BodyModelMode.entries[it]) }
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (bodyModel == BodyModelMode.THREE_D) THREE_D_TEXT else TWO_D_TEXT,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(Space.lg)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
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
