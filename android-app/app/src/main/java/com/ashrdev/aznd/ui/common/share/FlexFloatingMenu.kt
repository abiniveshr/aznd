package com.ashrdev.aznd.ui.common.share

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** One entry of the floating menu. */
internal class FlexMenuAction(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * A small round button that floats over the card and opens a drop-down of [actions] (undo, redo, auto align ...).
 * Place it with [modifier] (e.g. `Modifier.align(Alignment.TopEnd)` inside a Box). The menu closes after an entry is chosen.
 */
@Composable
internal fun FlexFloatingMenu(actions: List<FlexMenuAction>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.size(40.dp)
        ) {
            IconButton(onClick = { open = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Card tools")
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                    enabled = action.enabled,
                    onClick = {
                        open = false
                        action.onClick()
                    }
                )
            }
        }
    }
}
