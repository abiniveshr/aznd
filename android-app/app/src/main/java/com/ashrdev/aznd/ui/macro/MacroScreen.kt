package com.ashrdev.aznd.ui.macro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.macro.FoodEntry
import com.ashrdev.aznd.data.macro.MacroTargetsStore
import com.ashrdev.aznd.domain.CalorieTarget
import com.ashrdev.aznd.domain.FoodLog
import com.ashrdev.aznd.domain.GraphMode
import com.ashrdev.aznd.domain.GraphPoint
import com.ashrdev.aznd.domain.GraphStyle
import com.ashrdev.aznd.domain.MacroGoals
import com.ashrdev.aznd.domain.MacroStats
import com.ashrdev.aznd.domain.MacroTotals
import com.ashrdev.aznd.domain.PLACEHOLDER_BODYWEIGHT_KG
import com.ashrdev.aznd.ui.common.BarPage
import com.ashrdev.aznd.ui.common.ScreenScaffold
import com.ashrdev.aznd.ui.common.StatsGraph
import com.ashrdev.aznd.ui.common.rememberSubPageBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PAGE_GRAPHS = 0
private const val PAGE_TODAY = 1
private const val PAGE_EDIT = 2

private val MacroPages = listOf(
    BarPage("Graphs", Icons.Default.BarChart),
    BarPage("Today", Icons.Default.Today),
    BarPage("Edit", Icons.Default.Tune)
)

/**
 * Macros: three swipable pages, opening on the middle one.
 *   Graphs | Today | Edit
 *  - Today: target and progress on an open ring (Net / Eaten), the meals, a button to log a meal.
 *  - Graphs (swipe left): per-day history.
 *  - Edit (swipe right): Goal, Meals, Ingredients and Picture to Calories tuning.
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
    val body by viewModel.body.collectAsState()
    val ingredients by viewModel.ingredients.collectAsState()
    val today = MacroStats.dayStart(System.currentTimeMillis())
    var day by rememberSaveable { mutableStateOf(today) }
    var showLog by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<FoodEntry?>(null) }
    val pagerState = rememberPagerState(initialPage = PAGE_TODAY) { MacroPages.size }
    val subPages = rememberSubPageBar(pagerState, MacroPages)

    val all = entries.orEmpty()
    val byDay = remember(entries) {
        MacroStats.totalsByDay(all.map { FoodLog(it.eatenAt, it.calories, it.proteinG, it.carbsG, it.fatG) })
    }
    val totals = byDay[day] ?: MacroTotals()
    val dayEntries = remember(entries, day) { all.filter { MacroStats.dayStart(it.eatenAt) == day } }
    val recents = remember(entries) { all.distinctBy { it.name.trim().lowercase() }.take(8) }
    val weightKg = body?.weightKg ?: PLACEHOLDER_BODYWEIGHT_KG
    val targetKcal = if (body == null) null else CalorieTarget.target(weightKg, body?.bodyFatPercent, targets.goal, targets.advancedOffset)
    val goals = MacroGoals.resolve(targetKcal, targets.proteinG, targets.carbsG, targets.fatG)

    if (showLog) {
        LogMealDialog(
            recents = recents,
            ingredients = ingredients,
            dayNote = if (day == today) null else "Adding to ${SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(Date(day))}",
            onDismiss = { showLog = false },
            onLog = { name, kcal, p, c, f ->
                val at = if (day == today) System.currentTimeMillis() else day + HALF_DAY_MS
                viewModel.add(name, kcal, p, c, f, at)
            }
        )
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

    ScreenScaffold(
        title = when (pagerState.currentPage) {
            PAGE_GRAPHS -> "Macros • Graphs"
            PAGE_EDIT -> "Macros • Edit"
            else -> "Macros"
        },
        onBack = onBack,
        onHome = onHome,
        onOpenSettings = onOpenSettings,
        subPages = subPages
    ) { padding ->
        val bottom = padding.calculateBottomPadding() + 16.dp
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                PAGE_GRAPHS -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottom)),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (byDay.isNotEmpty() || burnt.isNotEmpty()) {
                        MacroGraph(byDay, burnt)
                    } else {
                        Text("Nothing logged yet.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                PAGE_EDIT -> MacroEditPage(
                    targets = targets,
                    body = body,
                    ingredients = ingredients,
                    onAddIngredient = { viewModel.addIngredient(it) },
                    onDeleteIngredient = { viewModel.deleteIngredient(it.id) },
                    onLogMeal = { showLog = true },
                    bottomPadding = bottom
                )
                else -> MacroTodayPage(
                    day = day,
                    today = today,
                    onDayChange = { day = it },
                    totals = totals,
                    burntKcal = burnt[day] ?: 0.0,
                    targetKcal = targetKcal,
                    goals = goals,
                    meals = dayEntries,
                    onLogMeal = { showLog = true },
                    onDeleteMeal = { toDelete = it },
                    bottomPadding = bottom
                )
            }
        }
    }
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
