package com.ashrdev.aznd.ui.macro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.ashrdev.aznd.ui.components.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.macro.FoodEntry
import com.ashrdev.aznd.data.macro.Ingredient
import com.ashrdev.aznd.domain.MacroStats
import com.ashrdev.aznd.ui.common.DropdownField
import com.ashrdev.aznd.ui.components.OutlinedTextField

/**
 * "Log meal": type a meal in (calories optional, worked out from the macros when empty), tap a
 * recent one to reuse it, or build it from your custom ingredients by grams.
 */
@Composable
fun LogMealDialog(
    recents: List<FoodEntry>,
    ingredients: List<Ingredient>,
    dayNote: String?,
    onDismiss: () -> Unit,
    onLog: (name: String, kcal: Double, protein: Double, carbs: Double, fat: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Ingredient?>(null) }
    var grams by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    fun addIngredient() {
        val ing = picked
        val g = parseNumber(grams)
        if (ing == null || g == null || g <= 0.0) {
            error = "Pick an ingredient and enter its grams"
            return
        }
        val f = g / 100.0
        kcal = plain((parseNumber(kcal) ?: 0.0) + ing.caloriesPer100 * f)
        protein = plain((parseNumber(protein) ?: 0.0) + ing.proteinPer100 * f)
        carbs = plain((parseNumber(carbs) ?: 0.0) + ing.carbsPer100 * f)
        fat = plain((parseNumber(fat) ?: 0.0) + ing.fatPer100 * f)
        name = if (name.isBlank()) ing.name else "$name + ${ing.name}"
        grams = ""
        error = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log meal") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (dayNote != null) Text(dayNote, style = MaterialTheme.typography.titleSmall)
                if (recents.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recents, key = { it.id }) { r ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    name = r.name
                                    kcal = plain(r.calories)
                                    protein = plain(r.proteinG)
                                    carbs = plain(r.carbsG)
                                    fat = plain(r.fatG)
                                    error = null
                                },
                                label = { Text(r.name, maxLines = 1) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Meal (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
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
                if (ingredients.isNotEmpty()) {
                    Text("Add from ingredients", style = MaterialTheme.typography.titleSmall)
                    DropdownField(
                        label = "Ingredient",
                        valueText = picked?.name ?: "choose",
                        options = ingredients,
                        optionText = { it.name },
                        onSelect = { picked = it }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = grams, onValueChange = { grams = it; error = null },
                            label = { Text("Grams") }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { addIngredient() }) { Text("Add") }
                    }
                }
                Text(
                    "Leave kcal empty to calculate it from the macros (4 / 4 / 9 per gram).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val inputs = listOf(kcal, protein, carbs, fat)
                val parsed = inputs.map { parseNumber(it) }
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
                        onLog(name.trim().ifEmpty { "Meal" }, k, p, c, f)
                        onDismiss()
                    }
                }
            }) { Text("Log") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
