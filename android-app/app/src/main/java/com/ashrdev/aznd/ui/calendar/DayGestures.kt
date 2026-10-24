package com.ashrdev.aznd.ui.calendar

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

internal data class DayHit(val dayKey: String, val bounds: IntRect)

internal typealias DayHitTest = (position: Offset, size: IntSize, density: Density) -> DayHit?

@Stable
internal class DayHoverState {

    var day by mutableStateOf<String?>(null)
    var bounds by mutableStateOf(IntRect.Zero)
    var pressed by mutableStateOf(false)

    fun show(hit: DayHit) {
        day = hit.dayKey
        bounds = hit.bounds
        pressed = true
    }

    fun clear() {
        day = null
        pressed = false
    }
}

private enum class PressOutcome { TAP, CANCEL }

private const val TOOLTIP_LINGER_MS = 1500L
@Composable
internal fun Modifier.dayGestures(
    hover: DayHoverState,
    showHover: Boolean,
    hitTest: DayHitTest,
    onTap: (String) -> Unit,
    onDoubleTap: ((String) -> Unit)?
): Modifier {
    val haptics = LocalHapticFeedback.current
    val currentHit by rememberUpdatedState(hitTest)
    val currentTap by rememberUpdatedState(onTap)
    val currentDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentShowHover by rememberUpdatedState(showHover)

    return this.pointerInput(hover) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val first = currentHit(down.position, size, this) ?: return@awaitEachGesture
            if (currentShowHover) hover.show(first)

            val outcome: PressOutcome? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var result: PressOutcome? = null
                while (result == null) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                    result = when {
                        change == null -> PressOutcome.CANCEL
                        !change.pressed -> if (change.isConsumed) PressOutcome.CANCEL else PressOutcome.TAP
                        change.isConsumed -> PressOutcome.CANCEL
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop ->
                            PressOutcome.CANCEL
                        else -> null
                    }
                }
                result
            }

            when (outcome) {
                PressOutcome.CANCEL -> hover.clear()

                PressOutcome.TAP -> {
                    hover.clear()
                    currentTap(first.dayKey)
                    val doubleTap = currentDoubleTap ?: return@awaitEachGesture
                    val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                        awaitFirstDown()
                    } ?: return@awaitEachGesture
                    val secondHit = currentHit(second.position, size, this) ?: return@awaitEachGesture
                    second.consume()
                    if (secondHit.dayKey != first.dayKey && currentShowHover) hover.show(secondHit)
                    val up = waitForUpOrCancellation()
                    if (up == null) {
                        hover.clear()
                        return@awaitEachGesture
                    }
                    up.consume()
                    if (secondHit.dayKey == first.dayKey) {
                        hover.clear()
                        doubleTap(first.dayKey)
                    } else {
                        hover.clear()
                        currentTap(secondHit.dayKey)
                    }
                }

                null -> {
                    if (currentShowHover) {
                        hover.show(first)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        if (!currentShowHover) continue
                        val hit = currentHit(change.position, size, this)
                        if (hit != null && hit.dayKey != hover.day) {
                            hover.show(hit)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                    hover.pressed = false
                }
            }
        }
    }
}

private const val PINCH_IN_RATIO = 1.3f
private const val PINCH_OUT_RATIO = 0.77f

@Composable
internal fun Modifier.pinchSteps(
    onPinch: (zoomIn: Boolean, centroid: Offset, density: Density) -> Unit
): Modifier {
    val currentOnPinch by rememberUpdatedState(onPinch)
    return this.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var startDistance = 0f
            var pinching = false
            var fired = false
            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val a = pressed[0].position
                    val b = pressed[1].position
                    val distance = (a - b).getDistance()
                    if (!pinching) {
                        pinching = true
                        startDistance = distance.coerceAtLeast(1f)
                    }
                    val ratio = distance / startDistance
                    if (!fired && (ratio > PINCH_IN_RATIO || ratio < PINCH_OUT_RATIO)) {
                        fired = true
                        currentOnPinch(ratio > 1f, (a + b) / 2f, this)
                    }
                }
                if (pinching) event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}

@Composable
internal fun DayHoverOverlay(hover: DayHoverState, tooltip: ((String) -> String)?) {
    LaunchedEffect(hover.day, hover.pressed) {
        if (hover.day != null && !hover.pressed) {
            delay(TOOLTIP_LINGER_MS)
            hover.clear()
        }
    }

    val day = hover.day ?: return
    if (tooltip == null) return
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val bounds = hover.bounds

    val gap = with(density) { 20.dp.roundToPx() }
    val margin = with(density) { 8.dp.roundToPx() }
    val provider = remember(bounds, gap, margin) { DayTooltipPositionProvider(bounds, gap, margin) }

    Popup(
        popupPositionProvider = provider,
        properties = PopupProperties(focusable = false)
    ) {
        Surface(
            color = colors.inverseSurface,
            contentColor = colors.inverseOnSurface,
            shape = MaterialTheme.shapes.small,
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(
                    formatDayLong(day),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inverseOnSurface.copy(alpha = 0.7f)
                )
                Text(tooltip(day), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private class DayTooltipPositionProvider(
    private val cell: IntRect,
    private val gap: Int,
    private val margin: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val centerX = anchorBounds.left + cell.center.x
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val x = (centerX - popupContentSize.width / 2).coerceIn(margin, maxX)
        val above = anchorBounds.top + cell.top - gap - popupContentSize.height
        val y = if (above >= margin) above else anchorBounds.top + cell.bottom + gap
        return IntOffset(x, y)
    }
}
