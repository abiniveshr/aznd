package com.ashrdev.aznd.ui.body

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedTextField
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

private fun dayLabel(ms: Long): String =
    if (sameDay(ms, System.currentTimeMillis())) "Today"
    else SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(Date(ms))

/** Date + time of an entry in the history lists. */
fun entryLabel(ms: Long): String = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))

/**
 * "Value + date + Log" form shared by the bodyweight and measurement screens. Rejects text that is
 * not a number or is outside [min]..[max]; accepts a comma as the decimal separator.
 */
@Composable
fun LogEntryCard(
    label: String,
    unit: String,
    min: Double,
    max: Double,
    modifier: Modifier = Modifier,
    onLog: (value: Double, atMs: Long) -> Unit
) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var dateMs by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var error by remember { mutableStateOf<String?>(null) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    label = { Text("$label ($unit)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    val v = text.trim().replace(',', '.').toDoubleOrNull()
                    when {
                        v == null -> error = "Enter a number"
                        v < min || v > max -> error = "Enter a value between ${min.toInt()} and ${max.toInt()} $unit"
                        else -> {
                            onLog(v, dateMs)
                            text = ""
                            error = null
                        }
                    }
                }) { Text("Log") }
            }
            TextButton(onClick = {
                val cal = Calendar.getInstance().apply { timeInMillis = dateMs }
                DatePickerDialog(
                    context,
                    { _, y, m, d ->
                        val now = Calendar.getInstance()
                        val picked = Calendar.getInstance().apply {
                            set(y, m, d, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        // a past day gets a neutral noon time, today keeps the current time
                        if (!sameDay(picked.timeInMillis, now.timeInMillis)) {
                            picked.set(Calendar.HOUR_OF_DAY, 12)
                            picked.set(Calendar.MINUTE, 0)
                        }
                        dateMs = picked.timeInMillis
                    },
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
            }) { Text("Date: ${dayLabel(dateMs)}") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Yes/No confirmation used before deleting an entry. */
@Composable
fun ConfirmDeleteDialog(what: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete entry?") },
        text = { Text(what) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
