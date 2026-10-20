package com.ashrdev.aznd.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.AlertDialog
import com.ashrdev.aznd.ui.components.OutlinedTextField
import java.util.Locale
import kotlin.math.roundToInt

private val DefaultSwatches = listOf(
    0xFFFFFFFF.toInt(), 0xFFE5E7EB.toInt(), 0xFF9AA0A6.toInt(), 0xFF3C4043.toInt(), 0xFF000000.toInt(),
    0xFFFF5A5F.toInt(), 0xFFE11D2E.toInt(), 0xFF7A0F1A.toInt(), 0xFFFF8A4C.toInt(), 0xFFFFD166.toInt(),
    0xFFF2B705.toInt(), 0xFF5CE0A0.toInt(), 0xFF1FBF6B.toInt(), 0xFF0F4D2C.toInt(), 0xFF3DD6D0.toInt(),
    0xFF9CD8FF.toInt(), 0xFF7C9CFF.toInt(), 0xFF2F6BFF.toInt(), 0xFF163A82.toInt(), 0xFFC58CFF.toInt(),
    0xFF6650A4.toInt(), 0xFFFF7EB6.toInt()
)

private fun hexOf(argb: Int): String = String.format(Locale.US, "#%06X", 0xFFFFFF and argb)

private fun hsvToArgb(h: Float, s: Float, v: Float): Int =
    android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))

/**
 * Colour picker used everywhere in the app: a saturation / brightness square, a hue bar, exact red / green /
 * blue sliders, a hex field and swatches ([swatches] first, then a fixed set). Returns an opaque ARGB int.
 */
@Composable
fun AdvancedColorPickerDialog(
    title: String,
    initial: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
    swatches: List<Int> = emptyList()
) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableStateOf(start[0]) }
    var sat by remember { mutableStateOf(start[1]) }
    var value by remember { mutableStateOf(start[2]) }
    var hex by remember { mutableStateOf(hexOf(initial)) }

    val argb = hsvToArgb(hue, sat, value)

    fun setHsv(h: Float, s: Float, v: Float) {
        hue = h
        sat = s
        value = v
        hex = hexOf(hsvToArgb(h, s, v))
    }

    fun setArgb(c: Int) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c, hsv)
        // A grey or black colour has no meaningful hue: keep the one the person had.
        setHsv(if (hsv[1] > 0.005f && hsv[2] > 0.005f) hsv[0] else hue, hsv[1], hsv[2])
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Saturation (left to right) and brightness (bottom to top).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { o ->
                                setHsv(hue, (o.x / size.width).coerceIn(0f, 1f), 1f - (o.y / size.height).coerceIn(0f, 1f))
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { o ->
                                    setHsv(hue, (o.x / size.width).coerceIn(0f, 1f), 1f - (o.y / size.height).coerceIn(0f, 1f))
                                }
                            ) { change, _ ->
                                change.consume()
                                val o = change.position
                                setHsv(hue, (o.x / size.width).coerceIn(0f, 1f), 1f - (o.y / size.height).coerceIn(0f, 1f))
                            }
                        }
                        .drawBehind {
                            drawRect(Brush.horizontalGradient(listOf(Color.White, Color(hsvToArgb(hue, 1f, 1f)))))
                            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                            val c = Offset(sat * size.width, (1f - value) * size.height)
                            drawCircle(Color.Black, 11.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
                            drawCircle(Color.White, 9.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                        }
                )

                // Hue.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { o -> setHsv((o.x / size.width).coerceIn(0f, 1f) * 360f, sat, value) }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { o -> setHsv((o.x / size.width).coerceIn(0f, 1f) * 360f, sat, value) }
                            ) { change, _ ->
                                change.consume()
                                setHsv((change.position.x / size.width).coerceIn(0f, 1f) * 360f, sat, value)
                            }
                        }
                        .drawBehind {
                            drawRect(
                                Brush.horizontalGradient(
                                    List(7) { Color(hsvToArgb(it * 60f, 1f, 1f)) }
                                )
                            )
                            val x = hue / 360f * size.width
                            drawCircle(Color.Black, 12.dp.toPx(), Offset(x, size.height / 2f), style = Stroke(1.5.dp.toPx()))
                            drawCircle(Color.White, 10.dp.toPx(), Offset(x, size.height / 2f), style = Stroke(2.dp.toPx()))
                        }
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { input ->
                            hex = input
                            val parsed = runCatching { android.graphics.Color.parseColor(input) }.getOrNull()
                            if (parsed != null) {
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(parsed, hsv)
                                hue = if (hsv[1] > 0.005f && hsv[2] > 0.005f) hsv[0] else hue
                                sat = hsv[1]
                                value = hsv[2]
                            }
                        },
                        label = { Text("Hex, e.g. #6650A4") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                ChannelSlider("Red", r) { setArgb((0xFF shl 24) or (it shl 16) or (g shl 8) or b) }
                ChannelSlider("Green", g) { setArgb((0xFF shl 24) or (r shl 16) or (it shl 8) or b) }
                ChannelSlider("Blue", b) { setArgb((0xFF shl 24) or (r shl 16) or (g shl 8) or it) }

                val all = (swatches + DefaultSwatches).distinct()
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(all) { c ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { setArgb(c) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(argb) }) { Text("Use colour") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ChannelSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(48.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        )
        Text(value.toString(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(32.dp))
    }
}
