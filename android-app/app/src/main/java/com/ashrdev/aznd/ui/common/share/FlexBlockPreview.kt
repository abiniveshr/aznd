package com.ashrdev.aznd.ui.common.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Shown inside an opened block in the Content list: that block drawn on its own (same look as the card,
 * but only this block), and a small map of the card showing where it sits among the others.
 */
@Composable
internal fun BlockPreview(
    spec: FlexSpec,
    content: FlexContent,
    assets: FlexAssets,
    plan: FlexPlan,
    block: FlexBlock
) {
    if (!content.has(block.type)) {
        Text(
            "Nothing to show for this block here.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val soloSpec = remember(spec.style, block) {
        FlexSpec(
            spec.style.copy(
                aspect = FlexAspect.FREE,
                padding = 36,
                watermark = false,
                cardRadius = minOf(spec.style.cardRadius, 36),
                borderWidth = 0
            ),
            listOf(block.copy(enabled = true))
        )
    }
    val soloPlan = remember(soloSpec, content, assets) { FlexPainter.plan(soloSpec, content, assets) }
    val position = plan.boxes.indexOfFirst { it.id == block.id }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(0.62f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "This block",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val ratio = soloPlan.width / soloPlan.height
                FlexPreview(
                    spec = soloSpec,
                    content = content,
                    assets = assets,
                    plan = soloPlan,
                    boxHeight = minOf(maxWidth / ratio, 200.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Column(modifier = Modifier.weight(0.38f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Position",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PositionMap(plan, block.id)
            Text(
                if (position >= 0) "${position + 1} of ${plan.boxes.size}" else "Not shown on the card",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** A tiny outline of the card with every block as a bar; the one with [highlightId] is coloured. */
@Composable
private fun PositionMap(plan: FlexPlan, highlightId: Int) {
    val ratio = plan.width / plan.height
    val outline = MaterialTheme.colorScheme.outline
    val bar = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val highlight = MaterialTheme.colorScheme.primary
    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val h = minOf(maxWidth / ratio, 170.dp)
        Canvas(modifier = Modifier
            .width(h * ratio)
            .height(h)) {
            val k = size.width / plan.width
            drawRoundRect(outline, style = Stroke(width = 1.dp.toPx()), cornerRadius = CornerRadius(6.dp.toPx()))
            plan.boxes.forEach { b ->
                drawRoundRect(
                    color = if (b.id == highlightId) highlight else bar,
                    topLeft = Offset(b.left * k, b.top * k),
                    size = Size(b.width * k, b.height * k),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
            }
        }
    }
}
