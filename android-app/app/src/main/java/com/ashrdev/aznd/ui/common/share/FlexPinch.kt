package com.ashrdev.aznd.ui.common.share

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Stable
internal class FlexPinch {
    var scale by mutableStateOf(1f)
        private set
    var pan by mutableStateOf(Offset.Zero)
        private set
    val zoomed: Boolean get() = scale > PINCH_REST

    fun reset() {
        scale = 1f
        pan = Offset.Zero
    }

    fun move(zoom: Float, by: Offset, around: Offset, width: Float, height: Float) {
        val next = (scale * zoom).coerceIn(1f, PINCH_MAX)
        val p = around - (around - pan) * (next / scale) + by
        scale = next
        pan = Offset(p.x.coerceIn(width * (1f - next), 0f), p.y.coerceIn(height * (1f - next), 0f))
    }
}

@Composable
internal fun FlexPinchBox(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    oneFinger: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val pinch = remember { FlexPinch() }
    LaunchedEffect(enabled) { if (!enabled) pinch.reset() }
    val oneFingerNow by rememberUpdatedState(oneFinger)
    val gestures = if (!enabled) Modifier else Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var travelled = 0f
            var panning = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val down = event.changes.count { it.pressed }
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                if (down >= 2) {
                    pinch.move(event.calculateZoom(), event.calculatePan(), event.calculateCentroid(), w, h)
                    event.changes.forEach { it.consume() }
                } else if (down == 1 && oneFingerNow && pinch.zoomed) {
                    val step = event.calculatePan()
                    travelled += step.getDistance()
                    if (panning || travelled > viewConfiguration.touchSlop) {
                        panning = true
                        pinch.move(1f, step, Offset.Zero, w, h)
                        event.changes.forEach { it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
    Box(modifier = modifier.clipToBounds().then(gestures)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = pinch.scale
                    scaleY = pinch.scale
                    translationX = pinch.pan.x
                    translationY = pinch.pan.y
                    transformOrigin = TransformOrigin(0f, 0f)
                },
            contentAlignment = Alignment.Center,
            content = content
        )
        if (pinch.zoomed) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
            ) {
                IconButton(onClick = { pinch.reset() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "Fit the card")
                }
            }
        }
    }
}

private const val PINCH_MAX = 4f
private const val PINCH_REST = 1.01f
