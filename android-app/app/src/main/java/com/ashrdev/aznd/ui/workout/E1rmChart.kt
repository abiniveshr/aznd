package com.ashrdev.aznd.ui.workout

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun drawE1rmChart(
    canvas: AndroidCanvas,
    width: Float,
    height: Float,
    points: List<SessionE1rmPoint>,
    lineColor: Int,
    textColor: Int,
    gridColor: Int
) {
    if (points.isEmpty()) return

    val paddingLeft = 70f
    val paddingBottom = 50f
    val paddingTop = 46f
    val paddingRight = 20f
    val chartWidth = width - paddingLeft - paddingRight
    val chartHeight = height - paddingTop - paddingBottom

    val minY = points.minOf { it.e1rm } * 0.9
    val maxY = points.maxOf { it.e1rm } * 1.1
    val range = (maxY - minY).let { if (it > 0.0) it else 1.0 }

    fun xFor(i: Int) = paddingLeft + if (points.size == 1) chartWidth / 2 else chartWidth * i / (points.size - 1).toFloat()
    fun yFor(v: Double) = paddingTop + chartHeight - ((v - minY) / range * chartHeight).toFloat()

    val gridPaint = Paint().apply { color = gridColor; strokeWidth = 2f; alpha = 120 }
    val textPaint = Paint().apply { color = textColor; textSize = 26f; isAntiAlias = true }
    val labelPaint = Paint().apply { color = textColor; textSize = 28f; isAntiAlias = true; isFakeBoldText = true }
    val linePaint = Paint().apply {
        color = lineColor; strokeWidth = 6f; style = Paint.Style.STROKE; isAntiAlias = true
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    val dotPaint = Paint().apply { color = lineColor; isAntiAlias = true }

    canvas.drawText("Estimated 1RM (kg)", paddingLeft, 30f, labelPaint)

    val gridLines = 4
    for (g in 0..gridLines) {
        val v = minY + range * g / gridLines
        val y = yFor(v)
        canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridPaint)
        canvas.drawText(v.toInt().toString(), 8f, y + 9f, textPaint)
    }

    val path = Path()
    points.forEachIndexed { i, p ->
        val x = xFor(i)
        val y = yFor(p.e1rm)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    canvas.drawPath(path, linePaint)

    val dateFmt = SimpleDateFormat("d MMM", Locale.getDefault())
    val labelEvery = if (points.size <= 6) 1 else (points.size / 5).coerceAtLeast(1)
    points.forEachIndexed { i, p ->
        val x = xFor(i)
        val y = yFor(p.e1rm)
        canvas.drawCircle(x, y, 8f, dotPaint)
        if (i % labelEvery == 0 || i == points.size - 1) {
            canvas.drawText(dateFmt.format(Date(p.startedAt)), x - 30f, height - 12f, textPaint)
        }
    }
}

@Composable
fun E1rmChart(points: List<SessionE1rmPoint>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary.toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.toArgb()
    Canvas(modifier = modifier) {
        drawIntoCanvas { canvas ->
            drawE1rmChart(canvas.nativeCanvas, size.width, size.height, points, lineColor, textColor, gridColor)
        }
    }
}

object ChartExport {
    fun exportChart(
        context: Context,
        points: List<SessionE1rmPoint>,
        lineColor: Int,
        textColor: Int,
        gridColor: Int,
        backgroundColor: Int
    ): Uri? = runCatching {
        val width = 1080
        val height = 720
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(backgroundColor)
        drawE1rmChart(canvas, width.toFloat(), height.toFloat(), points, lineColor, textColor, gridColor)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "chart_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, PhotoStore.authority(context), file)
    }.getOrNull()
}