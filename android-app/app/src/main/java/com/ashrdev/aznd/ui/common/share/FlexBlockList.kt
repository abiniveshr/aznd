@file:OptIn(ExperimentalMaterial3Api::class)

package com.ashrdev.aznd.ui.common.share

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.common.ReorderableItem
import com.ashrdev.aznd.ui.common.dragReorderHandle
import com.ashrdev.aznd.ui.common.dragReorderOnLongPress
import com.ashrdev.aznd.ui.common.rememberReorderState
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.common.tiling.TileTree

/**
 * The card's blocks as a compact list under the card on the Flex page (this used to be the Content page).
 * Switch a block on or off, drag the handle (or hold the row) to reorder, tap a row to select it on the card;
 * the selected row shows its Edit button. Which block is selected is shared with the card above.
 * The order here is the order of the tiles (left to right, top to bottom): reordering swaps the blocks between tiles.
 */
@Composable
internal fun BlockListPanel(
    spec: FlexSpec,
    content: FlexContent,
    assets: FlexAssets,
    plan: FlexPlan,
    selectedId: Int?,
    onSelect: (Int?) -> Unit,
    onEdit: (Int) -> Unit,
    update: ((FlexSpec) -> FlexSpec) -> Unit,
    reorder: ((FlexSpec) -> FlexSpec) -> Unit,
    modifier: Modifier = Modifier
) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val checked = remember(spec, content, assets) { HashMap<Long, Boolean>() }
    val toldAt = remember { LongArray(1) }

    val listState = rememberLazyListState()
    val listOrder = rememberReorderState(
        listState = listState,
        firstItemIndex = 0,
        itemCount = spec.blocks.size,
        canMove = { from, to ->
            spec.style.layoutMode != FlexLayoutMode.SPLIT || checked.getOrPut(from * 4096L + to) {
                val ok = runCatching { FlexPainter.holdsAsMuch(spec, reordered(spec, from, to), content, assets) }.getOrDefault(false)
                val now = System.currentTimeMillis()
                if (!ok && now - toldAt[0] > REFUSED_NOTE_MS) {
                    toldAt[0] = now
                    Toast.makeText(context, FLEX_NO_ROOM, Toast.LENGTH_SHORT).show()
                }
                ok
            }
        }
    ) { from, to -> reorder { sp -> reordered(sp, from, to) } }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(spec.blocks, key = { it.id }) { block ->
            ReorderableItem(listOrder, block.id) {
                BlockRow(
                    block = block,
                    content = content,
                    plan = plan,
                    selected = selectedId == block.id,
                    onSelect = { onSelect(if (selectedId == block.id) null else block.id) },
                    onEdit = { onEdit(block.id) },
                    onEnabled = { on ->
                        update { sp -> sp.copy(blocks = sp.blocks.map { if (it.id == block.id) it.copy(enabled = on) else it }) }
                    },
                    dragModifier = Modifier.dragReorderOnLongPress(listOrder, block.id),
                    handleModifier = Modifier.dragReorderHandle(listOrder, block.id)
                )
            }
        }
        item(key = "add") {
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
                                val newId = spec.nextBlockId()
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
                                onSelect(newId)
                            }
                        )
                    }
                }
            }
        }
    }
}

private const val REFUSED_NOTE_MS = 2500L

private fun reordered(sp: FlexSpec, from: Int, to: Int): FlexSpec {
    if (from !in sp.blocks.indices || to !in sp.blocks.indices) return sp
    val moved = sp.blocks.toMutableList().also { it.add(to, it.removeAt(from)) }
    return sp.copy(blocks = moved, layout = sp.layout?.let { TileTree.relabel(it, moved.map { b -> b.id }) })
}

@Composable
private fun BlockRow(
    block: FlexBlock,
    content: FlexContent,
    plan: FlexPlan,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    /** Put on the row: hold it, then drag the block to a new place. */
    dragModifier: Modifier = Modifier,
    /** Put on the hamburger icon: drag straight away. */
    handleModifier: Modifier = Modifier
) {
    val type = block.type
    Card(modifier = Modifier.fillMaxWidth(), highlight = selected) {
        Row(
            modifier = dragModifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Menu,
                contentDescription = "Drag to reorder",
                modifier = handleModifier
                    .size(40.dp)
                    .padding(8.dp)
            )
            Switch(checked = block.enabled, onCheckedChange = onEnabled)
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onSelect)
                    .padding(vertical = 6.dp)
            ) {
                Text(type.label, style = MaterialTheme.typography.titleMedium)
                val note = when {
                    !content.has(type) -> "Nothing to show here"
                    block.enabled && block.id in plan.hidden -> "Not shown: this card cannot fit any more content"
                    else -> null
                }
                if (note != null) {
                    Text(
                        note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Tapping a block (here or on the card) offers its edit page.
            if (selected) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit ${type.label}")
                }
            }
        }
    }
}
