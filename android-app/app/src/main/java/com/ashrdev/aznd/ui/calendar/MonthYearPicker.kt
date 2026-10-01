package com.ashrdev.aznd.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.theme.LocalAzndStyle

// Scrollable list of the months that have data, grouped by year, newest year first.
@Composable
fun MonthYearPickerDialog(
    months: List<MonthKey>,
    selected: MonthKey,
    onPick: (MonthKey) -> Unit,
    onDismiss: () -> Unit
) {
    val years = remember(months) {
        months.groupBy { it.year }.toList().sortedByDescending { it.first }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jump to month") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                years.forEach { (year, list) ->
                    item(key = "year-$year") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(year.toString(), style = MaterialTheme.typography.titleSmall)
                            list.sortedBy { it.month }.chunked(4).forEach { rowMonths ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowMonths.forEach { m ->
                                        MonthChip(
                                            label = m.shortName(),
                                            isSelected = m == selected,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onPick(m) }
                                        )
                                    }
                                    repeat(4 - rowMonths.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun MonthChip(
    label: String,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val style = LocalAzndStyle.current
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(style.buttonShape)
            .background(if (isSelected) colors.primaryContainer else colors.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) colors.onPrimaryContainer else colors.onSurface
        )
    }
}
