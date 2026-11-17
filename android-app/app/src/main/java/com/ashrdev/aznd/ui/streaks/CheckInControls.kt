package com.ashrdev.aznd.ui.streaks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.domain.streaks.CheckInfo
import com.ashrdev.aznd.ui.components.FilledTonalButton
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.theme.LocalAzndStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn

// One ticker for every button on screen; it only runs while something is collecting it.
private val sharedNowMs = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(15_000)
    }
}.shareIn(
    CoroutineScope(SupervisorJob() + Dispatchers.Default),
    SharingStarted.WhileSubscribed(replayExpirationMillis = 0),
    replay = 1
)

@Composable
fun rememberNowMs(): Long {
    val now by sharedNowMs.collectAsState(initial = System.currentTimeMillis())
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

@Composable
fun CheckInBox(
    info: CheckInfo,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = info.state(rememberNowMs())
    val shape = LocalAzndStyle.current.buttonShape
    val accent = MaterialTheme.colorScheme.primary
    val enabled = !state.done || state.perDay
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(shape)
            .background(if (state.done) accent else androidx.compose.ui.graphics.Color.Transparent)
            .border(2.dp, if (state.done) accent else MaterialTheme.colorScheme.outline, shape)
            .clickable(enabled = enabled) { onChange(!state.done) },
        contentAlignment = Alignment.Center
    ) {
        if (state.done) {
            Icon(Icons.Default.Check, contentDescription = if (state.perDay) "Checked in" else "Done", tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
