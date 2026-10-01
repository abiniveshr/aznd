package com.ashrdev.aznd.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.theme.LocalAzndStyle
import kotlinx.coroutines.launch

val HandleAreaHeight = 20.dp
val TabsHeight = 56.dp
val BarBottomMargin = 8.dp
private const val FLING_VELOCITY = 800f

private val TrayMotion = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

/** Pull-up state, clamped to 0..1 so a bouncy spring can never leave a dim scrim half-drawn. */
@Composable
fun rememberTrayState(): Animatable<Float, AnimationVector1D> =
    remember { Animatable(0f).also { it.updateBounds(0f, 1f) } }

suspend fun Animatable<Float, AnimationVector1D>.settleTo(target: Float) {
    animateTo(target, TrayMotion)
}

// Height of the bar when closed, including the gesture-navigation inset underneath it.
@Composable
fun bottomBarHeight(): Dp {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return HandleAreaHeight + TabsHeight + BarBottomMargin + navInset
}

// How much space a page must leave at the bottom so nothing hides behind the closed bar.
@Composable
fun bottomBarClearance(): Dp = bottomBarHeight() + 16.dp

// Shared shell: rounded-top surface, drag-to-reveal handle, and the Settings / Export / Delete
// row. `row1` is whatever a screen wants as its main row.
@Composable
fun BottomBarShell(
    tray: Animatable<Float, AnimationVector1D>,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    settingsEnabled: Boolean = true,
    row1: @Composable () -> Unit
) {
    val style = LocalAzndStyle.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val trayHeightPx = with(density) { TabsHeight.toPx() }

    val dragState = rememberDraggableState { delta ->
        scope.launch {
            tray.snapTo((tray.value - delta / trayHeightPx).coerceIn(0f, 1f))
        }
    }

    fun collapseTray() {
        scope.launch { tray.settleTo(0f) }
    }

    val shape = MaterialTheme.shapes.large.copy(
        bottomStart = ZeroCornerSize,
        bottomEnd = ZeroCornerSize
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (style.cardOutline) Modifier.topOutline(shape, style.outline, 1.dp) else Modifier),
        shape = shape,
        color = style.card,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = BarBottomMargin)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        val target = when {
                            velocity < -FLING_VELOCITY -> 1f
                            velocity > FLING_VELOCITY -> 0f
                            tray.value >= 0.5f -> 1f
                            else -> 0f
                        }
                        tray.settleTo(target)
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HandleAreaHeight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Show or hide quick actions"
                    ) {
                        scope.launch { tray.settleTo(if (tray.value > 0.5f) 0f else 1f) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                )
            }

            row1()

            val trayHeight = with(density) { (trayHeightPx * tray.value).toDp() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trayHeight)
                    .clipToBounds()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TabsHeight)
                        .padding(horizontal = 12.dp)
                ) {
                    val mutedTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    // Grayed out and inert while you're already inside Settings.
                    ActionTile(
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        tint = if (settingsEnabled) MaterialTheme.colorScheme.onSurfaceVariant else mutedTint,
                        onClick = if (settingsEnabled) {
                            {
                                collapseTray()
                                onOpenSettings()
                            }
                        } else null
                    )
                    ActionTile(
                        icon = Icons.Default.ImportExport,
                        label = "Import / Export",
                        tint = mutedTint
                    ) {
                        collapseTray()
                        // TODO: export / import data
                    }
                    ActionTile(
                        icon = Icons.Default.Delete,
                        label = "Delete data",
                        tint = mutedTint
                    ) {
                        collapseTray()
                        // TODO: delete data
                    }
                }
            }
        }
    }
}

// Shared tile used by every row in every bottom bar. onClick = null renders an inert tile.
@Composable
fun RowScope.ActionTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: (() -> Unit)? = null
) {
    val style = LocalAzndStyle.current
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(3.dp)
            .clip(style.buttonShape)
            .then(clickModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

// Draws the outline along the top edge and the two rounded top corners only. The sides and the
// bottom are left open, so nothing gets clipped or pops in around a curved screen corner.
private fun Modifier.topOutline(shape: CornerBasedShape, color: Color, width: Dp): Modifier =
    drawWithContent {
        drawContent()
        val stroke = width.toPx()
        val half = stroke / 2f
        val maxRadius = size.width / 2f
        val left = (shape.topStart.toPx(size, this) - half).coerceIn(0f, maxRadius)
        val right = (shape.topEnd.toPx(size, this) - half).coerceIn(0f, maxRadius)
        val path = Path().apply {
            moveTo(half, half + left)
            if (left > 0f) {
                arcTo(Rect(half, half, half + 2 * left, half + 2 * left), 180f, 90f, false)
            }
            lineTo(size.width - half - right, half)
            if (right > 0f) {
                arcTo(
                    Rect(size.width - half - 2 * right, half, size.width - half, half + 2 * right),
                    270f, 90f, false
                )
            }
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Butt))
    }
