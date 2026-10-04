package com.ashrdev.aznd.domain

import kotlin.math.ln
import kotlin.math.pow

/**
 * Estimated one-rep max maths. [load] is the effective load in kg, [r] the effective reps
 * (reps plus reps in reserve, see [E1rmCalculator.effectiveReps]).
 */
interface E1rmFormula {
    fun estimate(load: Double, r: Double): Double

    /** The load that would give [e1rm] at [r] effective reps (inverse of [estimate]). */
    fun loadFor(e1rm: Double, r: Double): Double
}

/**
 * Weight-dependent Epley generalisation from Marzagao (2026 preprint, Fitbod), fitted on 303k
 * near-failure sets across 388 exercises for cross-exercise consistency.
 *
 *   e1RM = L * (1 + (r - 1)^0.85 / k(L)),   k(L) = max(-2.55 + 4.58 * ln(L), 0.5),   L in kg
 *
 * Assumptions: sets are near-failure (with RPE tracking off, logged reps are treated as
 * near-failure); the fit used external-load lifts; it is NOT validated against tested 1RMs;
 * accuracy drops above ~12 reps.
 * [loadFor] is a numeric inverse and relies on [estimate] increasing with load, which holds
 * for loads above roughly 4 kg (below that the curve bends and results are unreliable).
 */
object WeightDependentEpley : E1rmFormula {
    private const val ALPHA = 0.85
    private const val A = -2.55
    private const val B = 4.58
    private const val K_MIN = 0.5

    private fun k(w: Double): Double = maxOf(A + B * ln(w), K_MIN)

    override fun estimate(load: Double, r: Double): Double =
        if (load <= 0.0 || r <= 1.0) load else load * (1.0 + (r - 1.0).pow(ALPHA) / k(load))

    override fun loadFor(e1rm: Double, r: Double): Double {
        if (r <= 1.0 || e1rm <= 1.0) return e1rm
        var lo = 0.5
        var hi = e1rm
        repeat(40) {
            val mid = (lo + hi) / 2.0
            if (estimate(mid, r) > e1rm) hi = mid else lo = mid
        }
        return hi
    }
}

/**
 * Classic Epley: load * (1 + r / 30). Same assumptions as above (near-failure sets, accuracy
 * drops above ~12 reps). Like [WeightDependentEpley], r <= 1 returns the load itself.
 */
object ClassicEpley : E1rmFormula {
    override fun estimate(load: Double, r: Double): Double =
        if (load <= 0.0 || r <= 1.0) load else load * (1.0 + r / 30.0)

    override fun loadFor(e1rm: Double, r: Double): Double =
        if (r <= 1.0 || e1rm <= 0.0) e1rm else e1rm / (1.0 + r / 30.0)
}

/** The one switch: change this to pick the app-wide formula. */
val DEFAULT_FORMULA: E1rmFormula = WeightDependentEpley
