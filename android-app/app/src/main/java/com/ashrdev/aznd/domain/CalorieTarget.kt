package com.ashrdev.aznd.domain

/**
 * The names stay as they are because they are stored in settings; only the labels changed
 * (INTENSE_BULK = Brutal bulk, ADVANCED = Custom).
 */
enum class CalorieGoal(val label: String, val offsetKcal: Int?) {
    INTENSE_BULK("Brutal bulk", 500),
    LEAN_BULK("Lean bulk", 250),
    MAINTAIN("Maintenance", 0),
    CUT("Cut", -500),
    ADVANCED("Custom", null)
}

/** Daily macro goals in grams. */
data class MacroGoals(val proteinG: Int, val carbsG: Int, val fatG: Int) {
    val kcal: Double get() = proteinG * 4.0 + carbsG * 4.0 + fatG * 9.0

    companion object {
        /**
         * The goals to show: the saved grams when any were set, otherwise a 30 / 40 / 30 split of
         * [targetKcal] (protein / carbs / fat by calories), or zeros while there is no target yet.
         */
        fun resolve(targetKcal: Double?, savedProteinG: Int, savedCarbsG: Int, savedFatG: Int): MacroGoals {
            if (savedProteinG > 0 || savedCarbsG > 0 || savedFatG > 0) {
                return MacroGoals(savedProteinG, savedCarbsG, savedFatG)
            }
            if (targetKcal == null || targetKcal <= 0.0) return MacroGoals(0, 0, 0)
            return MacroGoals(
                proteinG = Math.round(targetKcal * 0.30 / 4.0).toInt(),
                carbsG = Math.round(targetKcal * 0.40 / 4.0).toInt(),
                fatG = Math.round(targetKcal * 0.30 / 9.0).toInt()
            )
        }
    }
}

data class BodyInputs(val weightKg: Double?, val bodyFatPercent: Double?)

object CalorieTarget {
    const val ACTIVITY_FACTOR = 1.4
    const val MIN_TARGET_KCAL = 1000.0
    const val MAX_ADVANCED_OFFSET = 2000
    private const val KCAL_PER_KG_NO_BODY_FAT = 22.0

    fun bmr(weightKg: Double, bodyFatPercent: Double?): Double {
        if (bodyFatPercent != null && bodyFatPercent > 0.0 && bodyFatPercent < 100.0) {
            val leanKg = weightKg * (1.0 - bodyFatPercent / 100.0)
            return 370.0 + 21.6 * leanKg
        }
        return KCAL_PER_KG_NO_BODY_FAT * weightKg
    }

    fun maintenance(weightKg: Double, bodyFatPercent: Double?): Double =
        bmr(weightKg, bodyFatPercent) * ACTIVITY_FACTOR

    fun offset(goal: CalorieGoal, advancedOffset: Int): Int =
        goal.offsetKcal ?: advancedOffset.coerceIn(-MAX_ADVANCED_OFFSET, MAX_ADVANCED_OFFSET)

    fun target(weightKg: Double, bodyFatPercent: Double?, goal: CalorieGoal, advancedOffset: Int): Double =
        (maintenance(weightKg, bodyFatPercent) + offset(goal, advancedOffset)).coerceAtLeast(MIN_TARGET_KCAL)

    fun offsetLabel(offset: Int): String = if (offset > 0) "+$offset" else offset.toString()
}
