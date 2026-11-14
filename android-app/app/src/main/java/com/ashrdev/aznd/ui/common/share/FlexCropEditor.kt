package com.ashrdev.aznd.ui.common.share

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** The tallest the crop editor's picture is drawn. */
private val CROP_EDITOR_MAX_HEIGHT = 280.dp

/** How far from a corner or an edge a finger still grabs it, each way (so a corner's touch target is 56 dp square). */
private val CROP_REACH = 28.dp

/**
 * The editor of a free crop ([FlexPhotoRatio.FREE]): the whole [photo] fitted into a box as wide as the controls (at most
 * [CROP_EDITOR_MAX_HEIGHT] tall), the part outside the crop dimmed, and a frame with a handle on each corner and each
 * edge. Dragging a corner or an edge changes the frame, dragging inside it moves it. Every drag step reports the block
 * with its new crop through [onChange] as a continuous change (one undo step per drag, like a slider). Without a
 * [photo] (not loaded yet) the frame is shown over a plain box of a 4:3 shape.
 */
@Composable
internal fun FlexCropEditor(
    block: FlexBlock,
    photo: Bitmap?,
    onChange: (changed: FlexBlock, continuous: Boolean) -> Unit
) {
    val image = remember(photo) { photo?.asImageBitmap() }
    val aspect = if (photo != null && photo.width > 0 && photo.height > 0) photo.width.toFloat() / photo.height else 4f / 3f
    val blockNow by rememberUpdatedState(block)
    val changeNow by rememberUpdatedState(onChange)
    val crop = FlexCrop.normalise(block.cropL, block.cropT, block.cropR, block.cropB)
    val whole = crop.l <= 0f && crop.t <= 0f && crop.r >= 1f && crop.b >= 1f
    val neutral = MaterialTheme.colorScheme.surfaceVariant
    val dim = Color.Black.copy(alpha = 0.55f)
    val line = Color.White

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val boxW = minOf(maxWidth, CROP_EDITOR_MAX_HEIGHT * aspect)
            val boxH = boxW / aspect
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(
                    modifier = Modifier
                        .size(boxW, boxH)
                        .pointerInput(Unit) {
                            var handle: FlexCropHandle? = null
                            var start = FlexCropBox(0f, 0f, 1f, 1f)
                            var tx = 0f
                            var ty = 0f
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val b = blockNow
                                    start = FlexCrop.normalise(b.cropL, b.cropT, b.cropR, b.cropB)
                                    tx = 0f
                                    ty = 0f
                                    val reach = CROP_REACH.toPx()
                                    handle = FlexCrop.handleAt(
                                        start.l, start.t, start.r, start.b,
                                        pos.x / size.width, pos.y / size.height,
                                        reach / size.width, reach / size.height
                                    )
                                },
                                onDrag = { change, amount ->
                                    val h = handle
                                    if (h != null) {
                                        change.consume()
                                        tx += amount.x / size.width
                                        ty += amount.y / size.height
                                        val d = FlexCrop.drag(start.l, start.t, start.r, start.b, h, tx, ty)
                                        changeNow(blockNow.copy(cropL = d.l, cropT = d.t, cropR = d.r, cropB = d.b), true)
                                    }
                                },
                                onDragEnd = { handle = null },
                                onDragCancel = { handle = null }
                            )
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    if (image != null) {
                        drawImage(image, dstSize = IntSize(w.roundToInt(), h.roundToInt()))
                    } else {
                        drawRect(neutral, Offset.Zero, Size(w, h))
                    }
                    val l = crop.l * w
                    val t = crop.t * h
                    val r = crop.r * w
                    val b = crop.b * h
                    // Everything outside the frame, dimmed: a band above, below, and one each side of it.
                    drawRect(dim, Offset(0f, 0f), Size(w, t))
                    drawRect(dim, Offset(0f, b), Size(w, h - b))
                    drawRect(dim, Offset(0f, t), Size(l, b - t))
                    drawRect(dim, Offset(r, t), Size(w - r, b - t))
                    val thin = 1.dp.toPx()
                    val third = line.copy(alpha = 0.35f)
                    for (k in 1..2) {
                        drawLine(third, Offset(l + (r - l) * k / 3f, t), Offset(l + (r - l) * k / 3f, b), strokeWidth = thin)
                        drawLine(third, Offset(l, t + (b - t) * k / 3f), Offset(r, t + (b - t) * k / 3f), strokeWidth = thin)
                    }
                    drawRect(line, Offset(l, t), Size(r - l, b - t), style = Stroke(width = 2.dp.toPx()))
                    // The edge handles: a short bar in the middle of each edge.
                    val barLong = 20.dp.toPx()
                    val barShort = 4.dp.toPx()
                    val midX = (l + r) / 2f
                    val midY = (t + b) / 2f
                    drawRect(line, Offset(midX - barLong / 2f, t - barShort / 2f), Size(barLong, barShort))
                    drawRect(line, Offset(midX - barLong / 2f, b - barShort / 2f), Size(barLong, barShort))
                    drawRect(line, Offset(l - barShort / 2f, midY - barLong / 2f), Size(barShort, barLong))
                    drawRect(line, Offset(r - barShort / 2f, midY - barLong / 2f), Size(barShort, barLong))
                    // The corner handles.
                    val dot = 7.dp.toPx()
                    drawCircle(line, dot, Offset(l, t))
                    drawCircle(line, dot, Offset(r, t))
                    drawCircle(line, dot, Offset(l, b))
                    drawCircle(line, dot, Offset(r, b))
                }
            }
        }
        Text(
            "Drag a corner or an edge to frame any part of the photo, or drag inside the frame to move it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = { onChange(block.copy(cropL = 0f, cropT = 0f, cropR = 1f, cropB = 1f), false) },
            enabled = !whole
        ) { Text("Reset crop") }
    }
}
