package com.ashrdev.aznd.ui.macro

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.macro.FoodEntry
import com.ashrdev.aznd.data.macro.MacroTargetsStore
import com.ashrdev.aznd.domain.FoodLog
import com.ashrdev.aznd.domain.GraphMode
import com.ashrdev.aznd.domain.GraphPoint
import com.ashrdev.aznd.domain.GraphStyle
import com.ashrdev.aznd.domain.GraphWindow
import com.ashrdev.aznd.domain.MacroStats
import com.ashrdev.aznd.domain.MacroTotals
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.StatsGraph
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedTextField
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val HALF_DAY_MS = 12 * 3_600_000L

private fun n(v: Double) = GraphWindow.formatValue(v)

/**
 * Simple macro tracker: log foods (calories, protein, carbs, fat), see the day against optional
 * targets, and see the calories your finished workouts burnt (estimated by the workout module).
 */
@Composable
fun MacroScreen(
    viewModel: MacroViewModel,
    targets: MacroTargetsStore,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    val burnt by viewModel.burntByDay.collectAsState()
    val today = MacroStats.dayStart(System.currentTimeMillis())
    var day by rememberSaveable { mutableStateOf(today) }
    var showTargets by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<FoodEntry?>(null) }

    val all = entries.orEmpty()
    val byDay = remember(entries) {
        MacroStats.totalsByDay(all.map { FoodLog(it.eatenAt, it.calories, it.proteinG, it.carbsG, it.fatG) })
    }
    val totals = byDay[day] ?: MacroTotals()
    val dayEntries = remember(entries, day) { all.filter { MacroStats.dayStart(it.eatenAt) == day } }
    val recents = remember(entries) { all.distinctBy { it.name.trim().lowercase() }.take(8) }

    if (showTargets) {
        TargetsDialog(targets, onDismiss = { showTargets = false })
    }
    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete entry?") },
            text = { Text("${e.name}: ${n(e.calories)} kcal") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(e.id)
                    toDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }

    ScreenScaffold(title = "Macros", onBack = onBack, onHome = onHome, onOpenSettings = onOpenSettings) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp, bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { DayNavigator(day, today, onChange = { day = it }) }
            item {
                SummaryCard(totals, burnt[day] ?: 0.0, targets, onEditTargets = { showTargets = true })
            }
            item {
                AddFoodCard(recents) { name, kcal, p, c, f ->
                    val at = if (day == today) System.currentTimeMillis() else day + HALF_DAY_MS
                    viewModel.add(name, kcal, p, c, f, at)
                }
            }
            if (dayEntries.isNotEmpty()) {
                item { Text("Eaten ${if (day == today) "today" else "this day"}", style = MaterialTheme.typography.titleMedium) }
                items(dayEntries, key = { it.id }) { e ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(e.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${n(e.calories)} kcal  •  P ${n(e.proteinG)}  C ${n(e.carbsG)}  F ${n(e.fatG)} g",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { toDelete = e }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete ${e.name}", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            if (byDay.isNotEmpty() || burnt.isNotEmpty()) {
                item { MacroGraph(byDay, burnt) }
            }
        }
    }
}

@Composable
private fun DayNavigator(day: Long, today: Long, onChange: (Long) -> Unit) {
    val context = LocalContext.current
    val label = when (day) {
        today -> "Today"
        MacroStats.shiftDays(today, -1) -> "Yesterday"
        else -> SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(Date(day))
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = { onChange(MacroStats.shiftDays(day, -1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
        }
        TextButton(
            onClick = {
                val cal = Calendar.getInstance().apply { timeInMillis = day }
                DatePickerDialog(
                    context,
                    { _, y, m, d ->
                        val picked = Calendar.getInstance().apply {
                            set(y, m, d, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        onChange(minOf(picked, today))
                    },
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
            },
            modifier = Modifier.weight(1f)
        ) { Text(label, style = MaterialTheme.typography.titleMedium) }
        IconButton(onClick = { onChange(MacroStats.shiftDays(day, 1)) }, enabled = day < today) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
        }
    }
}

@Composable
private fun SummaryCard(totals: MacroTotals, burntKcal: Double, targets: MacroTargetsStore, onEditTargets: () -> Unit) {
    val net = totals.calories - burntKcal
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${n(totals.calories)} kcal eaten", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Burnt (workouts)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${n(burntKcal)} kcal", style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f)) {
                    Text("Net", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${n(net)} kcal", style = MaterialTheme.typography.titleMedium)
                }
                if (targets.calories > 0) {
                    val left = targets.calories - net
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (left >= 0) "Remaining" else "Over",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${n(kotlin.math.abs(left))} kcal",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (left >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            MacroBar("Calories", totals.calories, targets.calories, "kcal")
            MacroBar("Protein", totals.proteinG, targets.proteinG, "g")
            MacroBar("Carbs", totals.carbsG, targets.carbsG, "g")
            MacroBar("Fat", totals.fatG, targets.fatG, "g")
            Text(
                "Burnt calories are estimated from your finished workouts. Remaining = target − net.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onEditTargets) { Text(if (targets.calories > 0) "Edit targets" else "Set daily targets") }
        }
    }
}

@Composable
private fun MacroBar(label: String, value: Double, target: Int, unit: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                if (target > 0) "${n(value)} / $target $unit" else "${n(value)} $unit",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (target > 0) {
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction = (value / target).toFloat().coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(if (value > target) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun AddFoodCard(recents: List<FoodEntry>, onLog: (String, Double, Double, Double, Double) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    fun num(s: String): Double? = s.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Add food", style = MaterialTheme.typography.titleMedium)
            if (recents.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recents, key = { it.id }) { r ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                name = r.name
                                kcal = n(r.calories)
                                protein = n(r.proteinG)
                                carbs = n(r.carbsG)
                                fat = n(r.fatG)
                                error = null
                            },
                            label = { Text(r.name, maxLines = 1) }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Food (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = kcal, onValueChange = { kcal = it; error = null },
                    label = { Text("kcal") }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = protein, onValueChange = { protein = it; error = null },
                    label = { Text("Protein g") }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = carbs, onValueChange = { carbs = it; error = null },
                    label = { Text("Carbs g") }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = fat, onValueChange = { fat = it; error = null },
                    label = { Text("Fat g") }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.weight(1f)
                )
            }
            Text(
                "Leave kcal empty to calculate it from the macros (4 / 4 / 9 per gram).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = {
                val inputs = listOf(kcal, protein, carbs, fat)
                val parsed = inputs.map { num(it) }
                val bad = inputs.indices.any { inputs[it].isNotBlank() && parsed[it] == null }
                val p = parsed[1] ?: 0.0
                val c = parsed[2] ?: 0.0
                val f = parsed[3] ?: 0.0
                val k = parsed[0] ?: MacroStats.kcalFromMacros(p, c, f)
                when {
                    bad -> error = "Enter numbers only"
                    k <= 0.0 && p <= 0.0 && c <= 0.0 && f <= 0.0 -> error = "Enter calories or at least one macro"
                    k > 10_000 || p > 2_000 || c > 2_000 || f > 2_000 -> error = "That looks too large for one entry"
                    else -> {
                        onLog(name.trim().ifEmpty { "Food" }, k, p, c, f)
                        name = ""; kcal = ""; protein = ""; carbs = ""; fat = ""
                        error = null
                    }
                }
            }) { Text("Log") }
        }
    }
}

@Composable
private fun TargetsDialog(targets: MacroTargetsStore, onDismiss: () -> Unit) {
    fun show(v: Int) = if (v > 0) v.toString() else ""
    var kcal by remember { mutableStateOf(show(targets.calories)) }
    var protein by remember { mutableStateOf(show(targets.proteinG)) }
    var carbs by remember { mutableStateOf(show(targets.carbsG)) }
    var fat by remember { mutableStateOf(show(targets.fatG)) }
    val numbers = KeyboardOptions(keyboardType = KeyboardType.Number)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily targets") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Leave a field empty for no target.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = kcal, onValueChange = { kcal = it.filter(Char::isDigit) }, label = { Text("Calories (kcal)") }, singleLine = true, keyboardOptions = numbers, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = protein, onValueChange = { protein = it.filter(Char::isDigit) }, label = { Text("Protein (g)") }, singleLine = true, keyboardOptions = numbers, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = carbs, onValueChange = { carbs = it.filter(Char::isDigit) }, label = { Text("Carbs (g)") }, singleLine = true, keyboardOptions = numbers, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = fat, onValueChange = { fat = it.filter(Char::isDigit) }, label = { Text("Fat (g)") }, singleLine = true, keyboardOptions = numbers, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                fun v(s: String) = s.toIntOrNull()?.coerceAtMost(20_000) ?: 0
                targets.save(v(kcal), v(protein), v(carbs), v(fat))
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Per-day graph with the shared global range; modes are local to this page. */
@Composable
private fun MacroGraph(byDay: Map<Long, MacroTotals>, burnt: Map<Long, Double>) {
    val modes = remember(byDay, burnt) {
        val days = byDay.keys.sorted()
        fun foodMode(id: String, label: String, unit: String, value: (MacroTotals) -> Double, detail: (MacroTotals) -> String) =
            GraphMode(id, label, unit, GraphStyle.BARS, days.map { d -> byDay.getValue(d).let { GraphPoint(d + HALF_DAY_MS, value(it), detail(it)) } })
        val macrosText = { t: MacroTotals -> "P ${n(t.proteinG)}  C ${n(t.carbsG)}  F ${n(t.fatG)} g" }
        val kcalText = { t: MacroTotals -> "${n(t.calories)} kcal" }
        listOf(
            foodMode("kcal", "Calories", "kcal", { it.calories }, macrosText),
            GraphMode(
                "net", "Net calories", "kcal", GraphStyle.BARS,
                days.map { d ->
                    val t = byDay.getValue(d)
                    val b = burnt[d] ?: 0.0
                    GraphPoint(d + HALF_DAY_MS, t.calories - b, "${n(t.calories)} eaten − ${n(b)} burnt")
                },
                startAtZero = false
            ),
            GraphMode(
                "burnt", "Burnt (workouts)", "kcal", GraphStyle.BARS,
                burnt.keys.sorted().map { d -> GraphPoint(d + HALF_DAY_MS, burnt.getValue(d)) }
            ),
            foodMode("protein", "Protein", "g", { it.proteinG }, kcalText),
            foodMode("carbs", "Carbs", "g", { it.carbsG }, kcalText),
            foodMode("fat", "Fat", "g", { it.fatG }, kcalText)
        )
    }
    StatsGraph(
        modes = modes,
        title = "Per day",
        footnote = "Net = eaten − burnt. Burnt calories are estimates from finished workouts."
    )
}
