package com.ashrdev.aznd.ui.macro

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.macro.FoodEntry
import com.ashrdev.aznd.domain.macro.MacroGoals
import com.ashrdev.aznd.domain.macro.MacroStats
import com.ashrdev.aznd.domain.macro.MacroTotals
import com.ashrdev.aznd.ui.components.Card
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class TodayView(val label: String) { NET("Net"), EATEN("Eaten") }

/**
 * The centre page of the Macros screen: the day's target and progress on an open ring, with two
 * views. Net = what you may eat (target + burned) against what you ate. Eaten = the protein /
 * carbs / fat split, goal against eaten, with the day's meals below and a button to log a meal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroTodayPage(
    day: Long,
    today: Long,
    onDayChange: (Long) -> Unit,
    totals: MacroTotals,
    burntKcal: Double,
    targetKcal: Double?,
    goals: MacroGoals,
    meals: List<FoodEntry>,
    onLogMeal: () -> Unit,
    onDeleteMeal: (FoodEntry) -> Unit,
    bottomPadding: Dp
) {
    var viewName by rememberSaveable { mutableStateOf(TodayView.NET.name) }
    val view = TodayView.entries.firstOrNull { it.name == viewName } ?: TodayView.NET

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp,
                // Extra room so the last meal can scroll clear of the floating button.
                bottom = bottomPadding + if (view == TodayView.EATEN) 72.dp else 0.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "day") { DayNavigator(day, today, onDayChange) }
            item(key = "toggle") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TodayView.entries.forEachIndexed { index, v ->
                        SegmentedButton(
                            selected = v == view,
                            onClick = { viewName = v.name },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = TodayView.entries.size)
                        ) { Text(v.label) }
                    }
                }
            }
            if (view == TodayView.NET) {
                item(key = "net") { NetView(totals, burntKcal, targetKcal) }
            } else {
                item(key = "eaten") { EatenView(totals, goals) }
                item(key = "meals-title") {
                    Text(
                        if (day == today) "Today's meals" else "Meals this day",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (meals.isEmpty()) {
                    item(key = "meals-empty") {
                        Text(
                            "Nothing logged yet. Tap Log meal to add something.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(meals, key = { it.id }) { e -> MealCard(e, onDelete = { onDeleteMeal(e) }) }
                }
            }
        }

        if (view == TodayView.EATEN) {
            ExtendedFloatingActionButton(
                onClick = onLogMeal,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Log meal") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = bottomPadding)
            )
        }
    }
}

@Composable
private fun NetView(totals: MacroTotals, burntKcal: Double, targetKcal: Double?) {
    val target = targetKcal ?: 0.0
    val allowance = target + burntKcal
    val consumed = totals.calories
    val left = allowance - consumed
    val over = allowance > 0.0 && consumed > allowance
    val ring = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GaugeArc(
                segments = listOf(
                    GaugeSegment(1f, if (allowance > 0.0) (consumed / allowance).toFloat() else 0f, ring)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(n(consumed), style = MaterialTheme.typography.displaySmall)
                    Text(
                        if (allowance > 0.0) "of ${n(allowance.roundToInt().toDouble())} kcal" else "kcal eaten",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Figure("Target", if (targetKcal != null) "${n(target.roundToInt().toDouble())}" else "—", Modifier.weight(1f))
                Figure("Burned", n(burntKcal), Modifier.weight(1f))
                Figure("Total", if (targetKcal != null) n(allowance.roundToInt().toDouble()) else "—", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Figure("Consumed", n(consumed), Modifier.weight(1f))
                Figure(
                    if (left >= 0) "Remaining" else "Over",
                    if (targetKcal != null) n(abs(left).roundToInt().toDouble()) else "—",
                    Modifier.weight(1f),
                    valueColor = if (over) MaterialTheme.colorScheme.error else null
                )
            }
            Text(
                "Total = target + calories burned in workouts. Burned is an estimate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EatenView(totals: MacroTotals, goals: MacroGoals) {
    val protein = MaterialTheme.colorScheme.primary
    val carbs = MaterialTheme.colorScheme.tertiary
    val fat = MaterialTheme.colorScheme.secondary
    // Slice widths follow the goal's calories (4 / 4 / 9 per gram), so the ring shows the planned split.
    fun fill(eaten: Double, goal: Int) = if (goal > 0) (eaten / goal).toFloat() else 0f
    val hasGoals = goals.proteinG > 0 || goals.carbsG > 0 || goals.fatG > 0
    val segments = if (hasGoals) {
        listOf(
            GaugeSegment((goals.proteinG * 4f).coerceAtLeast(1f), fill(totals.proteinG, goals.proteinG), protein),
            GaugeSegment((goals.carbsG * 4f).coerceAtLeast(1f), fill(totals.carbsG, goals.carbsG), carbs),
            GaugeSegment((goals.fatG * 9f).coerceAtLeast(1f), fill(totals.fatG, goals.fatG), fat)
        )
    } else {
        listOf(GaugeSegment(1f, 0f, protein), GaugeSegment(1f, 0f, carbs), GaugeSegment(1f, 0f, fat))
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GaugeArc(segments = segments) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(n(totals.calories), style = MaterialTheme.typography.displaySmall)
                    Text(
                        if (hasGoals) "of ${n(goals.kcal)} kcal" else "kcal eaten",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MacroLegend("Protein", totals.proteinG, goals.proteinG, protein, Modifier.weight(1f))
                MacroLegend("Carbs", totals.carbsG, goals.carbsG, carbs, Modifier.weight(1f))
                MacroLegend("Fat", totals.fatG, goals.fatG, fat, Modifier.weight(1f))
            }
            if (!hasGoals) {
                Text(
                    "Set a goal under Edit > Goal to see your split.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun MacroLegend(label: String, eaten: Double, goal: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            if (goal > 0) "${n(eaten)} / $goal g" else "${n(eaten)} g",
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
private fun Figure(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color? = null
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MealCard(e: FoodEntry, onDelete: () -> Unit) {
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
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete ${e.name}", tint = MaterialTheme.colorScheme.error)
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
