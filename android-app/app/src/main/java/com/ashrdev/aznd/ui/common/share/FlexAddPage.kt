package com.ashrdev.aznd.ui.common.share

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton

/**
 * The Add screen: every kind of card the poster can get (those the content has something for), under the category
 * headings (empty categories are left out). Picking one opens its settings in a popup on a draft of the poster with the
 * new card in it; Add there hands the draft to [onAdd] (which returns false when the poster cannot hold it: the popup
 * then stays), Cancel changes nothing. [onAdd] gets the draft and the id of the card to select.
 */
@Composable
internal fun FlexAddPage(
    spec: FlexSpec,
    content: FlexContent,
    onAdd: (draft: FlexSpec, newId: Int) -> Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val groups = remember(content) {
        FlexCategory.entries.mapNotNull { cat ->
            val types = FlexBlockType.entries.filter { it.category == cat && content.has(it) }
            if (types.isEmpty()) null else cat to types
        }
    }
    var picked by remember { mutableStateOf<FlexBlockType?>(null) }
    BackHandler { onBack() }

    ScreenScaffold(
        title = "Add a card",
        subtitle = "Preset",
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = padding.calculateBottomPadding() + 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            groups.forEach { (cat, types) ->
                item(key = "cat:" + cat.name) {
                    Text(
                        cat.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }
                types.forEach { type ->
                    item(key = type.name) {
                        Card(modifier = Modifier.fillMaxWidth().clickable { picked = type }) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(addIconOf(type), contentDescription = null, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(type.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        addNoteOf(type),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val type = picked
    if (type != null) {
        val newId = spec.nextBlockId()
        AddCardDialog(
            start = spec.copy(blocks = spec.blocks + newCardOf(type, spec, newId)),
            newId = newId,
            content = content,
            onCancel = { picked = null },
            onAdd = onAdd
        )
    }
}

/** A new card of [type] with its starting settings; [spec] is the poster it is added to. */
private fun newCardOf(type: FlexBlockType, spec: FlexSpec, id: Int): FlexBlock {
    val stat = FlexWorkoutStat.entries.firstOrNull { s ->
        spec.blocks.none { it.type == FlexBlockType.WORKOUT_STATS && it.stat == s }
    } ?: FlexWorkoutStat.entries.first()
    return FlexBlock(
        id = id,
        type = type,
        limit = if (type == FlexBlockType.ROUTINE) 10 else 3,
        text = if (type == FlexBlockType.TEXT) "Your text here" else "",
        stat = if (type == FlexBlockType.WORKOUT_STATS) stat else FlexWorkoutStat.entries.first(),
        graph = FlexGraphConfig(),
        cal = FlexCalendarSpec(source = ""),
        keys = null
    )
}

/**
 * The popup of the Add screen: the new card's settings on the draft poster ([start]: the poster plus the card).
 * The poster itself is not touched until Add; [onAdd] returning false (no room) leaves the popup open.
 */
@Composable
private fun AddCardDialog(
    start: FlexSpec,
    newId: Int,
    content: FlexContent,
    onCancel: () -> Unit,
    onAdd: (FlexSpec, Int) -> Boolean
) {
    var draft by remember { mutableStateOf(start) }
    var focus by remember { mutableIntStateOf(newId) }
    val assets = rememberFlexAssets(draft, content)
    val shown = focus.takeIf { id -> draft.blocks.any { it.id == id } } ?: newId
    val label = draft.blocks.firstOrNull { it.id == shown }?.type?.label ?: ""

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
        ) {
            Text(
                "Add: $label",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                FlexCardConfig(
                    blockId = shown,
                    spec = draft,
                    content = content,
                    assets = assets,
                    mode = FlexConfigMode.ADD,
                    onChange = { _, transform -> draft = transform(draft) },
                    onSelect = { id -> if (id != null && draft.blocks.any { it.id == id }) focus = id }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { onAdd(draft, shown) }, modifier = Modifier.weight(1f)) { Text("Add") }
            }
        }
    }
}

private fun addIconOf(type: FlexBlockType): ImageVector = when (type) {
    FlexBlockType.HEADER -> Icons.Default.Title
    FlexBlockType.STREAKS -> Icons.Default.LocalFireDepartment
    FlexBlockType.WORKOUT_STATS -> Icons.Default.FitnessCenter
    FlexBlockType.EXERCISES -> Icons.Default.FormatListBulleted
    FlexBlockType.ROUTINE -> Icons.Default.ListAlt
    FlexBlockType.GRAPH -> Icons.Default.ShowChart
    FlexBlockType.DAY_NOTE -> Icons.Default.StickyNote2
    FlexBlockType.PHOTO -> Icons.Default.Image
    FlexBlockType.TEXT -> Icons.Default.TextFields
    FlexBlockType.CALENDAR -> Icons.Default.CalendarMonth
    FlexBlockType.MODEL -> Icons.Default.ViewInAr
}

private fun addNoteOf(type: FlexBlockType): String = when (type) {
    FlexBlockType.HEADER -> "Title and date"
    FlexBlockType.STREAKS -> "Current and longest runs"
    FlexBlockType.WORKOUT_STATS -> "Top set, one rep max or session totals"
    FlexBlockType.EXERCISES -> "Exercises with their sets"
    FlexBlockType.ROUTINE -> "The routine's exercises"
    FlexBlockType.GRAPH -> "Any variable over time, with an optional second"
    FlexBlockType.DAY_NOTE -> "The note for the day"
    FlexBlockType.PHOTO -> "A picture"
    FlexBlockType.TEXT -> "Your own words"
    FlexBlockType.CALENDAR -> "A month of days with highlights"
    FlexBlockType.MODEL -> "The 3D body model"
}
