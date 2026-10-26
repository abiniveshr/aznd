package com.ashrdev.aznd.ui.streaks

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ashrdev.aznd.domain.streaks.CheckInfo
import com.ashrdev.aznd.ui.components.FilledTonalButton
import com.ashrdev.aznd.ui.components.OutlinedButton
import kotlinx.coroutines.delay

@Composable
fun rememberNowMs(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
fun CheckInButton(
    info: CheckInfo,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false
) {
    val state = info.state(rememberNowMs())
    val label = when {
        !state.done -> if (outlined) "Check in today" else "Check in"
        !state.perDay -> "Done"
        outlined -> "Undo check-in"
        else -> "Checked in"
    }
    val enabled = !state.done || state.perDay
    val click = { onChange(!state.done) }
    if (outlined) OutlinedButton(onClick = click, enabled = enabled, modifier = modifier) { Text(label, maxLines = 1) }
    else FilledTonalButton(onClick = click, enabled = enabled, modifier = modifier) { Text(label, maxLines = 1) }
}
