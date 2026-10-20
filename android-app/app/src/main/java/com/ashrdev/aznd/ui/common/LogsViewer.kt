package com.ashrdev.aznd.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import com.ashrdev.aznd.ui.components.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.calendar.monthKeyOf
import com.ashrdev.aznd.ui.calendar.dayKeyOf
import com.ashrdev.aznd.ui.components.Card
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One line in a [LogsViewer]. The screen that owns the data maps its entries to these, so the
 * viewer never knows whether it is showing weights, body fat, meals or anything else.
 *
 *  - [key] must be unique and stable (e.g. "w12").
 *  - [title] is the main text (the value), [detail] an optional extra line under it; the date and
 *    time of [timeMs] is always shown.
 *  - [onDelete] non-null shows a delete button; the viewer asks for confirmation first.
 */
data class LogItem(
    val key: String,
    val timeMs: Long,
    val title: String,
    val detail: String? = null,
    val onDelete: (() -> Unit)? = null
)

private val logTimeFormat = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault())

/**
 * The common logs list: a [DateRangeSelector] on top, then every log inside the chosen range,
 * newest first. Use it as a page inside a pager, or let [LogsViewerScreen] wrap it in a scaffold.
 *
 * [logs] null means "still loading". [summary] optionally adds a line about the shown logs
 * (e.g. an average); it gets the filtered list, newest first.
 */
@Composable
fun LogsViewer(
    logs: List<LogItem>?,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    emptyText: String = "No logs yet.",
    state: DateRangeState = rememberDateRangeState(),
    summary: ((List<LogItem>) -> String?)? = null
) {
    var toDelete by remember { mutableStateOf<LogItem?>(null) }

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete entry?") },
            text = { Text("${item.title} on ${logTimeFormat.format(Date(item.timeMs))}") },
            confirmButton = {
                TextButton(onClick = {
                    item.onDelete?.invoke()
                    toDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }

    val all = logs.orEmpty()
    val availableMonths = remember(all) { all.map { monthKeyOf(dayKeyOf(it.timeMs)) }.distinct() }
    val range = state.range
    val shown = remember(all, range) {
        all.filterInRange(range) { it.timeMs }.sortedByDescending { it.timeMs }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "range-selector") {
            DateRangeSelector(state = state, availableMonths = availableMonths)
        }

        if (logs == null) return@LazyColumn

        item(key = "range-summary") {
            val count = "${shown.size} ${if (shown.size == 1) "log" else "logs"}"
            val extra = summary?.invoke(shown)
            Text(
                if (extra.isNullOrBlank()) count else "$count · $extra",
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (shown.isEmpty()) {
            item(key = "range-empty") {
                Text(
                    if (all.isEmpty()) emptyText else "Nothing logged in this range.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(shown, key = { it.key }) { item ->
                LogRow(item = item, onDelete = if (item.onDelete != null) ({ toDelete = item }) else null)
            }
        }
    }
}

@Composable
private fun LogRow(item: LogItem, onDelete: (() -> Unit)?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = if (onDelete != null) 4.dp else 16.dp)
        ) {
            Column(Modifier.weight(1f).padding(vertical = if (onDelete != null) 0.dp else 8.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    logTimeFormat.format(Date(item.timeMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.detail != null) {
                    Text(
                        item.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete entry", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** [LogsViewer] as a full screen with the standard scaffold, for a standalone logs page. */
@Composable
fun LogsViewerScreen(
    title: String,
    logs: List<LogItem>?,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    emptyText: String = "No logs yet.",
    summary: ((List<LogItem>) -> String?)? = null
) {
    ScreenScaffold(
        title = title,
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings
    ) { padding ->
        LogsViewer(
            logs = logs,
            bottomPadding = padding.calculateBottomPadding() + 16.dp,
            emptyText = emptyText,
            summary = summary
        )
    }
}
