@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ashrdev.aznd.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.theme.TaperLine
import com.ashrdev.aznd.ui.theme.TaperRegion
import com.ashrdev.aznd.ui.theme.TaperShape
import com.ashrdev.aznd.ui.theme.drawTaperLine
import kotlin.math.roundToInt

@Composable
fun TaperLineControls(line: TaperLine, color: Color, onChange: (TaperLine) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .drawBehind {
                    val t = 8.dp.toPx()
                    drawTaperLine(line, color, 0f, (size.height - t) / 2f, size.width, t)
                }
        )
        Text("Taper", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TaperShape.entries.forEach { s ->
                FilterChip(selected = line.shape == s, onClick = { onChange(line.copy(shape = s)) }, label = { Text(s.label) })
            }
        }
        if (line.shape != TaperShape.FLAT) {
            Text("Taper region", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaperRegion.entries.forEach { r ->
                    FilterChip(selected = line.region == r, onClick = { onChange(line.copy(region = r)) }, label = { Text(r.label) })
                }
            }
            PercentSlider("Taper from", line.from) { onChange(line.copy(from = it)) }
            PercentSlider("Taper to", line.to) { onChange(line.copy(to = it)) }
            PercentSlider("Taper starts at", line.start) { onChange(line.copy(start = it)) }
            Text(
                if (line.region == TaperRegion.BOTH) "From and to are the line's thickness where the taper starts and at its ends. 0% starts the taper at the middle, 100% at the ends."
                else "From and to are the line's thickness where the taper starts and at its tapered end. 0% starts the taper at the other end, 100% at the tapered end.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PercentSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = { onChange((it * 100f).roundToInt() / 100f) },
            valueRange = 0f..1f
        )
    }
}
