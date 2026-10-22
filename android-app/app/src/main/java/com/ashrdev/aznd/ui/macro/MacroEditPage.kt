package com.ashrdev.aznd.ui.macro

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.ashrdev.aznd.data.macro.Ingredient
import com.ashrdev.aznd.data.macro.MacroTargetsStore
import com.ashrdev.aznd.domain.macro.BodyInputs
import com.ashrdev.aznd.domain.body.BodyStats
import com.ashrdev.aznd.domain.macro.CalorieGoal
import com.ashrdev.aznd.domain.macro.CalorieTarget
import com.ashrdev.aznd.domain.macro.MacroStats
import com.ashrdev.aznd.domain.body.PLACEHOLDER_BODYWEIGHT_KG
import com.ashrdev.aznd.ui.components.Button
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class EditSection(val title: String, val icon: ImageVector) {
    GOAL("Goal", Icons.Default.Flag),
    MEALS("Meals", Icons.Default.Restaurant),
    INGREDIENTS("Ingredients", Icons.Default.Fastfood),
    TUNING("Picture to Calories", Icons.Default.PhotoCamera)
}

/**
 * The right page of the Macros screen: a menu of everything you can set up. Tapping an entry
 * opens it in place; Back (system) returns to the menu.
 */
@Composable
fun MacroEditPage(
    targets: MacroTargetsStore,
    body: BodyInputs?,
    ingredients: List<Ingredient>,
    onAddIngredient: (Ingredient) -> Unit,
    onDeleteIngredient: (Ingredient) -> Unit,
    onLogMeal: () -> Unit,
    bottomPadding: Dp
) {
    var sectionName by rememberSaveable { mutableStateOf<String?>(null) }
    val section = EditSection.entries.firstOrNull { it.name == sectionName }
    BackHandler(enabled = section != null) { sectionName = null }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (section == null) {
            items(EditSection.entries.toList(), key = { it.name }) { s ->
                val subtitle = when (s) {
                    EditSection.GOAL -> "${targets.goal.label} · ${CalorieTarget.offsetLabel(CalorieTarget.offset(targets.goal, targets.advancedOffset))} kcal"
                    EditSection.MEALS -> "Presets and custom meals"
                    EditSection.INGREDIENTS -> if (ingredients.isEmpty()) "Add your own ingredients" else "${ingredients.size} saved"
                    EditSection.TUNING -> "Coming soon"
                }
                Card(modifier = Modifier.fillMaxWidth().clickable { sectionName = s.name }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(s.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = MaterialTheme.typography.titleMedium)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        } else {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { sectionName = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to menu")
                    }
                    Text(section.title, style = MaterialTheme.typography.titleLarge)
                }
            }
            when (section) {
                EditSection.GOAL -> item(key = "goal") { GoalSection(targets, body, onSaved = { sectionName = null }) }
                EditSection.MEALS -> item(key = "meals") { MealsSection(onLogMeal) }
                EditSection.INGREDIENTS -> ingredientsSection(ingredients, onAddIngredient, onDeleteIngredient)
                EditSection.TUNING -> item(key = "tuning") {
                    PlaceholderCard("Picture to Calories", "Tuning for the photo-to-calories system is coming soon.")
                }
            }
        }
    }
}

// ---- Goal ----

@Composable
private fun GoalSection(targets: MacroTargetsStore, body: BodyInputs?, onSaved: () -> Unit) {
    fun show(v: Int) = if (v > 0) v.toString() else ""
    var goal by remember { mutableStateOf(targets.goal) }
    var custom by remember { mutableStateOf(if (targets.advancedOffset == 0) "" else targets.advancedOffset.toString()) }
    var protein by remember { mutableStateOf(show(targets.proteinG)) }
    var carbs by remember { mutableStateOf(show(targets.carbsG)) }
    var fat by remember { mutableStateOf(show(targets.fatG)) }
    var goalFat by remember { mutableStateOf(targets.goalBodyFat?.let { plain(it) } ?: "") }
    val numbers = KeyboardOptions(keyboardType = KeyboardType.Number)

    val weightKg = body?.weightKg
    val bodyFat = body?.bodyFatPercent
    val customValue = custom.toIntOrNull() ?: 0
    val preview = CalorieTarget.target(weightKg ?: PLACEHOLDER_BODYWEIGHT_KG, bodyFat, goal, customValue)
    val goalFatValue = parseNumber(goalFat)
    val goalWeight = if (weightKg != null && bodyFat != null && goalFatValue != null) {
        BodyStats.goalWeightKg(weightKg, bodyFat, goalFatValue)
    } else null

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CalorieGoal.entries.forEach { g ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().selectable(selected = goal == g, onClick = { goal = g }, role = Role.RadioButton)
                    ) {
                        RadioButton(selected = goal == g, onClick = null)
                        Text(
                            if (g.offsetKcal != null) "${g.label} (${CalorieTarget.offsetLabel(g.offsetKcal)})" else g.label,
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
                        )
                    }
                }
                if (goal == CalorieGoal.ADVANCED) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { v -> custom = v.filterIndexed { i, c -> c.isDigit() || (i == 0 && (c == '-' || c == '+')) } },
                        label = { Text("Surplus or deficit (kcal), e.g. +500 or -300") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text("Daily target ${n(preview.roundToInt().toDouble())} kcal", style = MaterialTheme.typography.titleMedium)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Macro targets", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = protein, onValueChange = { protein = it.filter(Char::isDigit) }, label = { Text("Protein (g)") }, singleLine = true, keyboardOptions = numbers, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = carbs, onValueChange = { carbs = it.filter(Char::isDigit) }, label = { Text("Carbs (g)") }, singleLine = true, keyboardOptions = numbers, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = fat, onValueChange = { fat = it.filter(Char::isDigit) }, label = { Text("Fat (g)") }, singleLine = true, keyboardOptions = numbers, modifier = Modifier.fillMaxWidth())
                Text(
                    "Leave all three empty for a 30 / 40 / 30 split of your target.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Goal bodyweight", style = MaterialTheme.typography.titleMedium)
                if (weightKg == null || bodyFat == null) {
                    Text(
                        "Log your bodyweight and body fat % on Body > Composition and the goal weight is estimated here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Now: ${n(weightKg)} kg at ${n(bodyFat)}% body fat, lean mass ${n(BodyStats.leanMassKg(weightKg, bodyFat))} kg.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                OutlinedTextField(
                    value = goalFat,
                    onValueChange = { goalFat = it },
                    label = { Text("Goal body fat (%)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (goalWeight != null && weightKg != null) {
                    val diff = goalWeight - weightKg
                    Text("Estimated goal weight ${n(goalWeight)} kg", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${if (abs(diff) < 0.05) "No change" else BodyStats.signed(diff) + " kg"} from now, keeping your lean mass.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (goalFat.isNotBlank() && goalFatValue == null) {
                    Text("Enter a number", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Button(
            onClick = {
                fun v(s: String) = s.toIntOrNull()?.coerceAtMost(20_000) ?: 0
                targets.save(goal, customValue, v(protein), v(carbs), v(fat))
                val gf = goalFatValue
                targets.saveGoalBodyFat(
                    if (gf != null && gf >= BodyStats.MIN_BODY_FAT && gf <= BodyStats.MAX_BODY_FAT) gf else null
                )
                onSaved()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save goal") }
    }
}

// ---- Meals ----

@Composable
private fun MealsSection(onLogMeal: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlaceholderCard("Presets", "Ready-made meals you can log in one tap. Coming soon.")
        PlaceholderCard("Custom meal", "Build and save your own meals from ingredients. Coming soon.")
        OutlinedButton(onClick = onLogMeal, modifier = Modifier.fillMaxWidth()) { Text("Log a meal now") }
    }
}

@Composable
private fun PlaceholderCard(title: String, text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- Ingredients ----

private fun androidx.compose.foundation.lazy.LazyListScope.ingredientsSection(
    ingredients: List<Ingredient>,
    onAdd: (Ingredient) -> Unit,
    onDelete: (Ingredient) -> Unit
) {
    item(key = "ing-form") { AddIngredientCard(onAdd) }
    if (ingredients.isEmpty()) {
        item(key = "ing-empty") {
            Text("No ingredients yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        items(ingredients, key = { "i${it.id}" }) { ing ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(ing.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "per 100 g: ${n(ing.caloriesPer100)} kcal · P ${n(ing.proteinPer100)} C ${n(ing.carbsPer100)} F ${n(ing.fatPer100)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onDelete(ing) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete ${ing.name}", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddIngredientCard(onAdd: (Ingredient) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Add ingredient (values per 100 g)", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = name, onValueChange = { name = it; error = null }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = kcal, onValueChange = { kcal = it; error = null }, label = { Text("kcal") }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.weight(1f))
                OutlinedTextField(value = protein, onValueChange = { protein = it; error = null }, label = { Text("Protein g") }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = carbs, onValueChange = { carbs = it; error = null }, label = { Text("Carbs g") }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.weight(1f))
                OutlinedTextField(value = fat, onValueChange = { fat = it; error = null }, label = { Text("Fat g") }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.weight(1f))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = {
                val inputs = listOf(kcal, protein, carbs, fat)
                val parsed = inputs.map { parseNumber(it) }
                val bad = inputs.indices.any { inputs[it].isNotBlank() && parsed[it] == null }
                val p = parsed[1] ?: 0.0
                val c = parsed[2] ?: 0.0
                val f = parsed[3] ?: 0.0
                val k = parsed[0] ?: MacroStats.kcalFromMacros(p, c, f)
                when {
                    name.isBlank() -> error = "Give the ingredient a name"
                    bad -> error = "Enter numbers only"
                    k <= 0.0 && p <= 0.0 && c <= 0.0 && f <= 0.0 -> error = "Enter calories or at least one macro"
                    k > 1_000 || p > 100 || c > 100 || f > 100 -> error = "Values are per 100 g, so that looks too large"
                    else -> {
                        onAdd(Ingredient(name = name.trim(), caloriesPer100 = k, proteinPer100 = p, carbsPer100 = c, fatPer100 = f))
                        name = ""; kcal = ""; protein = ""; carbs = ""; fat = ""
                        error = null
                    }
                }
            }) { Text("Add ingredient") }
        }
    }
}
