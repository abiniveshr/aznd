package com.ashrdev.aznd.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun DrawScope.drawTaperLine(
    line: TaperLine,
    color: Color,
    x: Float,
    y: Float,
    length: Float,
    thickness: Float,
    vertical: Boolean = false
) {
    if (length <= 0f || thickness <= 0f) return
    val pts = line.outline(x, y, length, thickness, vertical)
    val path = Path()
    path.moveTo(pts[0], pts[1])
    var i = 2
    while (i < pts.size) {
        path.lineTo(pts[i], pts[i + 1])
        i += 2
    }
    path.close()
    drawPath(path, color)
}

fun Modifier.taperEdge(line: TaperLine, color: Color, width: Dp, top: Boolean, inset: Dp = 0.dp): Modifier =
    drawWithContent {
        drawContent()
        val t = width.toPx()
        val side = inset.toPx().coerceIn(0f, size.width / 4f)
        drawTaperLine(line, color, side, if (top) 0f else size.height - t, size.width - 2f * side, t)
    }
