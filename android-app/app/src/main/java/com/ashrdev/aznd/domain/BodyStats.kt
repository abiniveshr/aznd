package com.ashrdev.aznd.domain

/**
 * Things measured on the body: tape sites in centimetres, height, and body fat in percent.
 * Body fat belongs to the Composition page of the Body screen; everything else is on Measurements.
 */
enum class MeasurementSite(val label: String, val unit: String = "cm") {
    BODY_FAT("Body fat", "%"),
    HEIGHT("Height"),
    NECK("Neck"),
    SHOULDERS("Shoulders"),
    CHEST("Chest"),
    WAIST("Waist"),
    HIPS("Hips"),
    BICEP_LEFT("Left bicep"),
    BICEP_RIGHT("Right bicep"),
    FOREARM_LEFT("Left forearm"),
    FOREARM_RIGHT("Right forearm"),
    THIGH_LEFT("Left thigh"),
    THIGH_RIGHT("Right thigh"),
    CALF_LEFT("Left calf"),
    CALF_RIGHT("Right calf");

    companion object {
        fun fromName(name: String?): MeasurementSite? = values().firstOrNull { it.name == name }
    }
}

object BodyStats {
    private const val DAY_MS = 86_400_000L

    /** Plausible ranges, used to reject typos like 750 kg. */
    const val MIN_WEIGHT_KG = 20.0
    const val MAX_WEIGHT_KG = 500.0
    const val MIN_CM = 5.0
    const val MAX_CM = 300.0
    const val MIN_BODY_FAT = 2.0
    const val MAX_BODY_FAT = 70.0
    const val MIN_HEIGHT_CM = 50.0
    const val MAX_HEIGHT_CM = 260.0

    fun min(site: MeasurementSite) = when (site) {
        MeasurementSite.BODY_FAT -> MIN_BODY_FAT
        MeasurementSite.HEIGHT -> MIN_HEIGHT_CM
        else -> MIN_CM
    }

    fun max(site: MeasurementSite) = when (site) {
        MeasurementSite.BODY_FAT -> MAX_BODY_FAT
        MeasurementSite.HEIGHT -> MAX_HEIGHT_CM
        else -> MAX_CM
    }

    /** Fat part of the bodyweight, from a bodyweight in kg and a body fat percentage. */
    fun fatMassKg(weightKg: Double, bodyFatPercent: Double): Double = weightKg * bodyFatPercent / 100.0

    /**
     * The bodyweight you would have at [goalBodyFatPercent] if your lean mass stayed exactly where it
     * is now (goal weight = lean mass / (1 - goal body fat)). Null for an impossible goal.
     */
    fun goalWeightKg(weightKg: Double, bodyFatPercent: Double, goalBodyFatPercent: Double): Double? {
        if (goalBodyFatPercent < MIN_BODY_FAT || goalBodyFatPercent > MAX_BODY_FAT) return null
        return leanMassKg(weightKg, bodyFatPercent) / (1.0 - goalBodyFatPercent / 100.0)
    }

    /**
     * Lean mass at each weight reading, using the latest body fat reading taken at or before it (the
     * earliest reading for weights that come before any). Both lists oldest first; empty without any body fat.
     */
    fun leanMassSeries(weights: List<GraphPoint>, bodyFat: List<GraphPoint>): List<GraphPoint> {
        if (bodyFat.isEmpty()) return emptyList()
        return weights.map { w ->
            val fat = bodyFat.lastOrNull { it.timeMs <= w.timeMs } ?: bodyFat.first()
            GraphPoint(w.timeMs, leanMassKg(w.value, fat.value))
        }
    }

    /** Weight minus fat, from a bodyweight in kg and a body fat percentage. */
    fun leanMassKg(weightKg: Double, bodyFatPercent: Double): Double = weightKg * (1.0 - bodyFatPercent / 100.0)

    /** Average of every reading in the [windowDays] days up to and including each point. [points] oldest first. */
    fun rollingAverage(points: List<GraphPoint>, windowDays: Int = 7): List<GraphPoint> =
        points.mapIndexed { i, p ->
            val from = p.timeMs - windowDays * DAY_MS
            val inWindow = (0..i).map { points[it] }.filter { it.timeMs > from }
            GraphPoint(p.timeMs, inWindow.map { it.value }.average())
        }

    /** "+1.2" / "−0.8" / "0.0". */
    fun signed(delta: Double): String {
        val v = String.format(java.util.Locale.getDefault(), "%.1f", kotlin.math.abs(delta))
        return when {
            kotlin.math.abs(delta) < 0.05 -> "0.0"
            delta > 0 -> "+$v"
            else -> "−$v"
        }
    }
}
