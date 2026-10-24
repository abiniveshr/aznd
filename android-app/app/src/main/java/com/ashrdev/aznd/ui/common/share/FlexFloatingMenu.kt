package com.ashrdev.aznd.ui.common.share

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal class FlexMenuAction(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
internal fun FlexFloatingMenu(
    actions: List<FlexMenuAction>,
    modifier: Modifier = Modifier,
    hidden: Boolean = false
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var spotX by rememberSaveable { mutableStateOf(1f) }
    var spotY by rememberSaveable { mutableStateOf(0f) }
    var area by remember { mutableStateOf(IntSize(0, 0)) }
    var self by remember { mutableStateOf(IntSize(0, 0)) }
    Box(modifier = modifier.onSizeChanged { area = it }) {
        if (!hidden) {
            val freeW = (area.width - self.width).coerceAtLeast(0)
            val freeH = (area.height - self.height).coerceAtLeast(0)
            val low = spotY > 0.5f
            Surface(
                shape = if (open) RoundedCornerShape(22.dp) else CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .offset { IntOffset((spotX * freeW).roundToInt(), (spotY * freeH).roundToInt()) }
                    .onSizeChanged { self = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { spotX = if (spotX < 0.5f) 0f else 1f },
                            onDragCancel = { spotX = if (spotX < 0.5f) 0f else 1f }
                        ) { change, step ->
                            change.consume()
                            val w = (area.width - self.width).coerceAtLeast(1)
                            val h = (area.height - self.height).coerceAtLeast(1)
                            spotX = (spotX + step.x / w).coerceIn(0f, 1f)
                            spotY = (spotY + step.y / h).coerceIn(0f, 1f)
                        }
                    }
            ) {
                if (!open) {
                    IconButton(onClick = { open = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Card tools")
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        val toggle: @Composable () -> Unit = {
                            IconButton(onClick = { open = false }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Hide card tools")
                            }
                        }
                        if (!low) toggle()
                        val shown = if (low) actions.reversed() else actions
                        shown.forEach { action ->
                            IconButton(onClick = action.onClick, enabled = action.enabled, modifier = Modifier.size(40.dp)) {
                                Icon(action.icon, contentDescription = action.label)
                            }
                        }
                        if (low) toggle()
                    }
                }
            }
        }
    }
}
