package com.ashrdev.aznd.ui.common

import com.ashrdev.aznd.ui.theme.taperEdge
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
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.manage.LocalManageData
import com.ashrdev.aznd.ui.theme.LocalAzndStyle
import com.ashrdev.aznd.ui.theme.Space
import com.ashrdev.aznd.ui.theme.ThemeColor
import com.ashrdev.aznd.ui.theme.azndFill
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

val HandleAreaHeight = 20.dp
val TabsHeight = 56.dp
val BarBottomMargin: Dp get() = Space.sm
private const val FLING_VELOCITY = 800f

private val TrayMotion = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

/**
 * Pull-up state, clamped to 0..1 so a bouncy spring can never leave a dim scrim half-drawn.
 * [openInitially] starts the bar already pulled up.
 */
@Composable
fun rememberTrayState(openInitially: Boolean = false): Animatable<Float, AnimationVector1D> =
    remember { Animatable(if (openInitially) 1f else 0f).also { it.updateBounds(0f, 1f) } }

/** The bar no longer stays pulled up across screens; kept so Home can still reset it. */
object TrayMemory {
    fun clear() {}
}

suspend fun Animatable<Float, AnimationVector1D>.settleTo(target: Float) {
    animateTo(target, TrayMotion)
}

// Height of the bar when closed, including the gesture-navigation inset underneath it.
// [hasMainRow] is false for screens whose closed bar is just the pull handle.
@Composable
fun bottomBarHeight(hasMainRow: Boolean = true): Dp {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return HandleAreaHeight + (if (hasMainRow) TabsHeight else 0.dp) + BarBottomMargin + navInset
}

// How much space a page must leave at the bottom so nothing hides behind the closed bar.
@Composable
fun bottomBarClearance(hasMainRow: Boolean = true): Dp = bottomBarHeight(hasMainRow) + Space.lg

// Shared shell: rounded-top surface, drag-to-reveal handle, and the Home / Settings / Manage data
// row. `row1` is whatever a screen wants as its main row (always visible); null leaves just the
// handle. Pulling the bar up reveals the Home / Settings / Manage data row. [onHome] is null on
// the Home pager itself, which hides the Home tile. [onShare] takes that tile's place there (Flex), so the
// pull-up row is Share / Settings / Manage data on Home and Home / Settings / Manage data everywhere else.
@Composable
fun BottomBarShell(
    tray: Animatable<Float, AnimationVector1D>,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    settingsEnabled: Boolean = true,
    manageDataEnabled: Boolean = true,
    onHome: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    row1: (@Composable () -> Unit)? = null
) {
    val style = LocalAzndStyle.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val trayHeightPx = with(density) { TabsHeight.toPx() }

    val dragState = rememberDraggableState { delta ->
        // Undispatched so the snap runs inside the input event instead of a frame later.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
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
            .then(
                when {
                    style.bottomBarBorder <= 0.dp -> Modifier
                    style.bottomBarBorderLine -> Modifier.taperEdge(
                        style.borderTaper, style.outline, style.bottomBarBorder, top = true,
                        inset = (style.cardCorner * 1.33f).dp
                    )
                    else -> Modifier.topOutline(shape, style.outline, style.bottomBarBorder)
                }
            ),
        shape = shape,
        // Flat colour underneath (it also casts the shadow); the gradient / pattern fill is painted over it.
        color = style.bottomBar,
        contentColor = style.onBottomBar,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .azndFill(style.fill(ThemeColor.BOTTOM_BAR))
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

            row1?.invoke()

            QuickActionTray(
                tray = tray,
                trayHeightPx = trayHeightPx,
                onHome = onHome,
                onShare = onShare,
                onOpenSettings = onOpenSettings,
                settingsEnabled = settingsEnabled,
                manageDataEnabled = manageDataEnabled,
                collapseTray = ::collapseTray
            )
        }
    }
}

// The pulled-up row. Its revealed height follows the tray in the layout phase, and the tap gate is a
// derived flag, so a drag only re-lays-out and clips; nothing here recomposes while it moves.
@Composable
private fun QuickActionTray(
    tray: Animatable<Float, AnimationVector1D>,
    trayHeightPx: Float,
    onHome: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onOpenSettings: () -> Unit,
    settingsEnabled: Boolean,
    manageDataEnabled: Boolean,
    collapseTray: () -> Unit
) {
    val openManageData = LocalManageData.current
    // The row below is laid out at full height even while the tray is closed (it only gets clipped), and
    // clipping hides it without stopping touches: its tiles would still catch taps over the main row and
    // below the bar. So they only respond once the tray is all the way open.
    val trayOpen by remember(tray) { derivedStateOf { tray.value >= TRAY_OPEN_FOR_TAPS } }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val h = (trayHeightPx * tray.value).roundToInt()
                    .coerceIn(constraints.minHeight, constraints.maxHeight)
                val placeable = measurable.measure(
                    Constraints(constraints.minWidth, constraints.maxWidth, h, h)
                )
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .clipToBounds()
    ) {
        // Full size always; the box above just uncovers it from the top downwards.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(TabsHeight)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TabsHeight)
                    .padding(horizontal = Space.md)
            ) {
                val mutedTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                if (onHome != null) {
                    ActionTile(
                        icon = Icons.Default.Home,
                        label = "Home",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = if (trayOpen) {
                            {
                                collapseTray()
                                onHome()
                            }
                        } else null
                    )
                }
                if (onHome == null && onShare != null) {
                    ActionTile(
                        icon = Icons.Default.Share,
                        label = "Share",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = if (trayOpen) {
                            {
                                collapseTray()
                                onShare()
                            }
                        } else null
                    )
                }
                // Grayed out and inert while you're already inside Settings.
                ActionTile(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    tint = if (settingsEnabled) MaterialTheme.colorScheme.onSurfaceVariant else mutedTint,
                    onClick = if (trayOpen && settingsEnabled) {
                        {
                            collapseTray()
                            onOpenSettings()
                        }
                    } else null
                )
                ActionTile(
                    icon = Icons.Default.ImportExport,
                    label = "Manage data",
                    tint = if (manageDataEnabled && openManageData != null) MaterialTheme.colorScheme.onSurfaceVariant else mutedTint,
                    onClick = if (trayOpen && manageDataEnabled && openManageData != null) {
                        {
                            collapseTray()
                            openManageData.openForCurrentScreen()
                        }
                    } else null
                )
            }
        }
    }
}

// How far the tray must be pulled up before its tiles take taps (it settles at 0 or 1, so in practice: fully open).
private const val TRAY_OPEN_FOR_TAPS = 0.98f

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
            .padding(Space.xs)
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
