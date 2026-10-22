package com.ashrdev.aznd.data.manage

import com.ashrdev.aznd.data.AppDatabase
import com.ashrdev.aznd.data.body.BodyMeasurement
import com.ashrdev.aznd.data.body.BodyWeightEntry
import com.ashrdev.aznd.data.macro.FoodEntry
import com.ashrdev.aznd.data.macro.Ingredient
import com.ashrdev.aznd.data.macro.MacroTargetsStore
import com.ashrdev.aznd.domain.macro.CalorieGoal
import com.ashrdev.aznd.ui.common.DayRange
import org.json.JSONObject

class BodyweightModule(private val db: AppDatabase) : DataModule {
    override val id = "bodyweight"
    override val app = DataApp.BODY
    override val title = "Bodyweight log"
    override val description = "Every bodyweight reading you logged"
    override val unit = "readings"
    override val supportsDateRange = true
    override val dateRangeNote = "by the day it was measured"
    override val canExport = true
    override val canImport = true

    private val dao get() = db.manageDao()

    override suspend fun count(range: DayRange): Int = dao.countWeights(range.fromMillis, range.toMillisExclusive)

    override suspend fun delete(range: DayRange) = dao.deleteWeights(range.fromMillis, range.toMillisExclusive)

    override suspend fun export(range: DayRange): ModuleExport {
        val list = dao.weights(range.fromMillis, range.toMillisExclusive)
        return ModuleExport(
            list.size,
            JSONObject().put(
                "weights",
                list.toJsonArray { JSONObject().put("measuredAt", it.measuredAt).put("weightKg", it.weightKg) }
            )
        )
    }

    override suspend fun import(data: JSONObject, version: Int): ImportOutcome {
        var added = 0
        var skipped = 0
        for (w in data.arrayOrEmpty("weights").mapObjects { it }) {
            val at = w.getLong("measuredAt")
            val kg = w.getDouble("weightKg")
            if (dao.weightExists(at, kg) > 0) {
                skipped++
            } else {
                db.bodyDao().insertWeight(BodyWeightEntry(measuredAt = at, weightKg = kg))
                added++
            }
        }
        return ImportOutcome(added, skipped)
    }
}

class BodyMeasurementsModule(private val db: AppDatabase, private val bodyFat: Boolean) : DataModule {
    override val id = if (bodyFat) "body_fat" else "body_measurements"
    override val app = DataApp.BODY
    override val title = if (bodyFat) "Body fat log" else "Body measurements"
    override val description = if (bodyFat) "Every body fat % reading you logged" else "Height and tape measurements (chest, waist, arms, legs and so on)"
    override val unit = "readings"
    override val supportsDateRange = true
    override val dateRangeNote = "by the day it was measured"
    override val canExport = true
    override val canImport = true

    private val dao get() = db.manageDao()

    override suspend fun count(range: DayRange): Int =
        dao.countMeasurements(bodyFat, range.fromMillis, range.toMillisExclusive)

    override suspend fun delete(range: DayRange) =
        dao.deleteMeasurements(bodyFat, range.fromMillis, range.toMillisExclusive)

    override suspend fun export(range: DayRange): ModuleExport {
        val list = dao.measurements(bodyFat, range.fromMillis, range.toMillisExclusive)
        return ModuleExport(
            list.size,
            JSONObject().put(
                "measurements",
                list.toJsonArray {
                    JSONObject().put("site", it.site).put("measuredAt", it.measuredAt).put("value", it.valueCm)
                }
            )
        )
    }

    override suspend fun import(data: JSONObject, version: Int): ImportOutcome {
        var added = 0
        var skipped = 0
        for (m in data.arrayOrEmpty("measurements").mapObjects { it }) {
            val site = m.getString("site")
            val isFat = site == "BODY_FAT"
            if (isFat != bodyFat) {
                skipped++
                continue
            }
            val at = m.getLong("measuredAt")
            val value = m.getDouble("value")
            if (dao.measurementExists(site, at, value) > 0) {
                skipped++
            } else {
                db.bodyDao().insertMeasurement(BodyMeasurement(site = site, measuredAt = at, valueCm = value))
                added++
            }
        }
        return ImportOutcome(added, skipped)
    }
}

class FoodLogModule(private val db: AppDatabase) : DataModule {
    override val id = "food_log"
    override val app = DataApp.MACROS
    override val title = "Food log"
    override val description = "Everything you logged as eaten, with calories and macros"
    override val unit = "entries"
    override val supportsDateRange = true
    override val dateRangeNote = "by the day it was eaten"
    override val canExport = true
    override val canImport = true

    private val dao get() = db.manageDao()

    override suspend fun count(range: DayRange): Int = dao.countFood(range.fromMillis, range.toMillisExclusive)

    override suspend fun delete(range: DayRange) = dao.deleteFood(range.fromMillis, range.toMillisExclusive)

    override suspend fun export(range: DayRange): ModuleExport {
        val list = dao.foodEntries(range.fromMillis, range.toMillisExclusive)
        return ModuleExport(
            list.size,
            JSONObject().put(
                "entries",
                list.toJsonArray {
                    JSONObject()
                        .put("eatenAt", it.eatenAt)
                        .put("name", it.name)
                        .put("calories", it.calories)
                        .put("proteinG", it.proteinG)
                        .put("carbsG", it.carbsG)
                        .put("fatG", it.fatG)
                }
            )
        )
    }

    override suspend fun import(data: JSONObject, version: Int): ImportOutcome {
        var added = 0
        var skipped = 0
        for (e in data.arrayOrEmpty("entries").mapObjects { it }) {
            val at = e.getLong("eatenAt")
            val name = e.getString("name")
            val calories = e.getDouble("calories")
            if (dao.foodExists(at, name, calories) > 0) {
                skipped++
            } else {
                db.foodDao().insert(
                    FoodEntry(
                        eatenAt = at,
                        name = name,
                        calories = calories,
                        proteinG = e.optDouble("proteinG", 0.0),
                        carbsG = e.optDouble("carbsG", 0.0),
                        fatG = e.optDouble("fatG", 0.0)
                    )
                )
                added++
            }
        }
        return ImportOutcome(added, skipped)
    }
}

class CustomIngredientsModule(private val db: AppDatabase) : DataModule {
    override val id = "custom_ingredients"
    override val app = DataApp.MACROS
    override val title = "Custom ingredients"
    override val description = "Ingredients you added, with their calories and macros per 100 g"
    override val unit = "ingredients"
    override val canExport = true
    override val canImport = true

    private val dao get() = db.manageDao()

    override suspend fun count(range: DayRange): Int = dao.countIngredients()

    override suspend fun delete(range: DayRange) = dao.deleteAllIngredients()

    override suspend fun export(range: DayRange): ModuleExport {
        val list = dao.ingredients()
        return ModuleExport(
            list.size,
            JSONObject().put(
                "ingredients",
                list.toJsonArray {
                    JSONObject()
                        .put("name", it.name)
                        .put("caloriesPer100", it.caloriesPer100)
                        .put("proteinPer100", it.proteinPer100)
                        .put("carbsPer100", it.carbsPer100)
                        .put("fatPer100", it.fatPer100)
                }
            )
        )
    }

    override suspend fun import(data: JSONObject, version: Int): ImportOutcome {
        var added = 0
        var skipped = 0
        for (i in data.arrayOrEmpty("ingredients").mapObjects { it }) {
            val name = i.getString("name")
            if (dao.ingredientExists(name) > 0) {
                skipped++
            } else {
                db.ingredientDao().insert(
                    Ingredient(
                        name = name,
                        caloriesPer100 = i.optDouble("caloriesPer100", 0.0),
                        proteinPer100 = i.optDouble("proteinPer100", 0.0),
                        carbsPer100 = i.optDouble("carbsPer100", 0.0),
                        fatPer100 = i.optDouble("fatPer100", 0.0)
                    )
                )
                added++
            }
        }
        return ImportOutcome(added, skipped)
    }
}

class MacroGoalsModule(private val store: MacroTargetsStore) : DataModule {
    override val id = "macro_goals"
    override val app = DataApp.MACROS
    override val title = "Macro goals"
    override val description = "Your calorie goal, protein, carbs and fat targets and goal body fat"
    override val unit = "goal sets"
    override val deleteNote = "Resets the goal to Maintain with no targets."
    override val canExport = true
    override val canImport = true

    private fun isSet(): Boolean =
        store.goal != CalorieGoal.MAINTAIN || store.advancedOffset != 0 ||
            store.proteinG + store.carbsG + store.fatG > 0 || store.goalBodyFat != null

    override suspend fun count(range: DayRange): Int = if (isSet()) 1 else 0

    override suspend fun delete(range: DayRange) {
        store.save(CalorieGoal.MAINTAIN, 0, 0, 0, 0)
        store.saveGoalBodyFat(null)
    }

    override suspend fun export(range: DayRange): ModuleExport? {
        if (!isSet()) return ModuleExport(0, JSONObject())
        return ModuleExport(
            1,
            JSONObject()
                .put("goal", store.goal.name)
                .put("advancedOffset", store.advancedOffset)
                .put("proteinG", store.proteinG)
                .put("carbsG", store.carbsG)
                .put("fatG", store.fatG)
                .put("goalBodyFat", store.goalBodyFat)
        )
    }

    override suspend fun import(data: JSONObject, version: Int): ImportOutcome {
        val goalName = data.stringOrNull("goal") ?: return ImportOutcome(skipped = 1)
        val goal = enumOrNull<CalorieGoal>(goalName) ?: return ImportOutcome(skipped = 1)
        store.save(
            goal,
            data.optInt("advancedOffset"),
            data.optInt("proteinG"),
            data.optInt("carbsG"),
            data.optInt("fatG")
        )
        store.saveGoalBodyFat(data.doubleOrNull("goalBodyFat"))
        return ImportOutcome(added = 1)
    }
}
