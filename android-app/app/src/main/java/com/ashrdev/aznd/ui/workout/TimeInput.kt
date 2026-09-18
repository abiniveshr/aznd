package com.ashrdev.aznd.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun TimeInput(
    stateKey: Any,
    totalSeconds: Int,
    onTotalChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var hours by remember(stateKey) {
        mutableStateOf(if (totalSeconds >= 3600) (totalSeconds / 3600).toString() else "")
    }
    var minutes by remember(stateKey) {
        mutableStateOf(if (totalSeconds >= 60) ((totalSeconds % 3600) / 60).toString() else "")
    }
    var seconds by remember(stateKey) {
        mutableStateOf(if (totalSeconds > 0) (totalSeconds % 60).toString() else "")
    }

    fun total(): Int =
        (hours.toIntOrNull() ?: 0) * 3600 +
            (minutes.toIntOrNull() ?: 0) * 60 +
            (seconds.toIntOrNull() ?: 0)

    fun normalize() {
        val t = total()
        hours = if (t >= 3600) (t / 3600).toString() else ""
        minutes = if (t >= 60) ((t % 3600) / 60).toString() else ""
        seconds = (t % 60).toString()
        onTotalChange(t)
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimePart("h", hours, { hours = it; onTotalChange(total()) }, ::normalize)
        TimePart("m", minutes, { minutes = it; onTotalChange(total()) }, ::normalize)
        TimePart("s", seconds, { seconds = it; onTotalChange(total()) }, ::normalize)
    }
}

@Composable
private fun RowScope.TimePart(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    onBlur: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .weight(1f)
            .onFocusChanged { if (!it.isFocused) onBlur() }
    )
}