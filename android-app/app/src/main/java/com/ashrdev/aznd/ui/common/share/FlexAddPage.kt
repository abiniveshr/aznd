package com.ashrdev.aznd.ui.common.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.Card

/** One thing the Add page offers: [build] makes the card with it added, as the last block. */
private class AddTile(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val note: String,
    val build: (FlexSpec) -> FlexSpec
)

/**
 * The Add page of the preset editor: one tile per block that can be added (those the content has something for, plus the
 * sample content's other pictures that are not on the card yet). Tapping a tile hands over the card with the new block and
 * the new block's id; the editor decides whether the card can hold it.
 * TODO(flex-redesign): richer add experience next
 */
@Composable
internal fun FlexAddPage(
    spec: FlexSpec,
    content: FlexContent,
    bottom: Dp,
    onAdd: (FlexSpec, Int) -> Unit
) {
    val tiles = remember(spec, content) {
        val list = ArrayList<AddTile>()
        FlexBlockType.entries.filter { content.has(it) }.forEach { type ->
            list += AddTile(
                key = type.name,
                icon = addIconOf(type),
                title = type.label,
                note = addNoteOf(type),
                build = { sp ->
                    sp.copy(
                        blocks = sp.blocks + FlexBlock(
                            id = sp.nextBlockId(),
                            type = type,
                            limit = if (type == FlexBlockType.ROUTINE) 10 else 3,
                            text = if (type == FlexBlockType.TEXT) "Your text here" else ""
                        )
                    )
                }
            )
        }
        // The content's other pictures that are not on the card yet, one tile each.
        val onCard = spec.blocks.mapNotNull { if (it.type == FlexBlockType.PHOTO) it.keys?.firstOrNull() else null }.toSet()
        content.photos.filter { it.uri !in onCard }.forEach { photo ->
            list += AddTile(
                key = "photo:" + photo.uri,
                icon = Icons.Default.Image,
                title = "Photo: " + photo.label,
                note = "Another picture",
                build = { sp -> sp.withPhotoBlock(photo.uri) }
            )
        }
        list
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottom + 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(tiles, key = { it.key }) { tile ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAdd(tile.build(spec), spec.nextBlockId()) }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Icon(tile.icon, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        tile.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        tile.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun addIconOf(type: FlexBlockType): ImageVector = when (type) {
    FlexBlockType.HEADER -> Icons.Default.Title
    FlexBlockType.STREAKS -> Icons.Default.LocalFireDepartment
    FlexBlockType.TOP_SET -> Icons.Default.FitnessCenter
    FlexBlockType.ONE_RM -> Icons.Default.EmojiEvents
    FlexBlockType.SESSION_STATS -> Icons.Default.BarChart
    FlexBlockType.EXERCISES -> Icons.Default.FormatListBulleted
    FlexBlockType.ROUTINE -> Icons.Default.ListAlt
    FlexBlockType.CHART -> Icons.Default.ShowChart
    FlexBlockType.DAY_NOTE -> Icons.Default.StickyNote2
    FlexBlockType.PHOTO -> Icons.Default.Image
    FlexBlockType.TEXT -> Icons.Default.TextFields
    FlexBlockType.CALENDAR -> Icons.Default.CalendarMonth
    FlexBlockType.MODEL -> Icons.Default.ViewInAr
}

private fun addNoteOf(type: FlexBlockType): String = when (type) {
    FlexBlockType.HEADER -> "Title and date"
    FlexBlockType.STREAKS -> "Current and longest runs"
    FlexBlockType.TOP_SET -> "The heaviest set"
    FlexBlockType.ONE_RM -> "Estimated one rep max"
    FlexBlockType.SESSION_STATS -> "Totals for the workout"
    FlexBlockType.EXERCISES -> "Exercises with their sets"
    FlexBlockType.ROUTINE -> "The routine's exercises"
    FlexBlockType.CHART -> "1RM over time"
    FlexBlockType.DAY_NOTE -> "The note for the day"
    FlexBlockType.PHOTO -> "A picture"
    FlexBlockType.TEXT -> "Your own words"
    FlexBlockType.CALENDAR -> "A month of days"
    FlexBlockType.MODEL -> "The 3D body model"
}
