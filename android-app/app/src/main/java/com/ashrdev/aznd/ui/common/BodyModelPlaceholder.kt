package com.ashrdev.aznd.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.Card

/**
 * Stand-in for the 3D body model. A flat figure plus a caption: [title] says what the real model
 * will show here, [detail] is the current selection (a muscle, a measurement site ...).
 * Replace the body of this composable with the real renderer later; every screen that shows the
 * model goes through it.
 */
@Composable
fun BodyModelPlaceholder(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    height: Dp = 220.dp
) {
    val figure = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .height(height)
                    .aspectRatio(0.5f)
            ) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                // head
                drawCircle(figure, radius = w * 0.14f, center = Offset(cx, h * 0.08f))
                // torso
                drawLine(figure, Offset(cx, h * 0.22f), Offset(cx, h * 0.52f), strokeWidth = w * 0.4f, cap = StrokeCap.Round)
                // arms
                drawLine(figure, Offset(w * 0.17f, h * 0.24f), Offset(w * 0.07f, h * 0.55f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
                drawLine(figure, Offset(w * 0.83f, h * 0.24f), Offset(w * 0.93f, h * 0.55f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
                // legs
                drawLine(figure, Offset(w * 0.4f, h * 0.58f), Offset(w * 0.38f, h * 0.93f), strokeWidth = w * 0.17f, cap = StrokeCap.Round)
                drawLine(figure, Offset(w * 0.6f, h * 0.58f), Offset(w * 0.62f, h * 0.93f), strokeWidth = w * 0.17f, cap = StrokeCap.Round)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "3D model coming soon",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
