package com.ashrdev.aznd.ui.macro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * One slice of a gauge. [weight] is its share of the arc (any positive number, shares are relative),
 * [fill] how much of the slice is filled (0 = empty, 1 = full; more than 1 is drawn as full).
 */
data class GaugeSegment(val weight: Float, val fill: Float, val color: Color)

private const val GAUGE_SWEEP = 240f
private const val GAUGE_START = 150f // 150 degrees = lower left; sweeping clockwise ends lower right
private const val SEGMENT_GAP = 5f

/**
 * A pie chart that stops short of a full circle (a 240 degree open ring with the bottom cut off).
 * Each segment draws a faint track and, on top of it, the filled part. [content] is centred in the
 * ring (put the big number there).
 */
@Composable
fun GaugeArc(
    segments: List<GaugeSegment>,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 340.dp)
            .aspectRatio(1.25f)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = size.width * 0.075f
            val diameter = size.width - stroke
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(diameter, diameter)
            val usable = GAUGE_SWEEP - SEGMENT_GAP * (segments.size - 1).coerceAtLeast(0)
            val totalWeight = segments.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(0.0001f)
            val cap = if (segments.size == 1) StrokeCap.Round else StrokeCap.Butt

            var start = GAUGE_START
            segments.forEach { seg ->
                val sweep = usable * seg.weight / totalWeight
                if (sweep > 0f) {
                    drawArc(
                        color = seg.color.copy(alpha = 0.22f),
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = cap)
                    )
                    val filled = sweep * seg.fill.coerceIn(0f, 1f)
                    if (filled > 0f) {
                        drawArc(
                            color = seg.color,
                            startAngle = start,
                            sweepAngle = filled,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = cap)
                        )
                    }
                }
                start += sweep + SEGMENT_GAP
            }
        }
        // The ring's centre sits at half the width; the box is only 0.8 of the width tall.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = maxWidth * 0.1f),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}
