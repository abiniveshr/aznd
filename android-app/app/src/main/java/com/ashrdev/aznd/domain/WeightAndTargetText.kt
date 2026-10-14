package com.ashrdev.aznd.domain

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * THE one place a weight becomes text (kg only for now; lb will plug in here later).
 * Max 2 decimals, no trailing zeros, always a dot: 52.5, 50, 0.25. Negative zero prints as "0".
 * ASSISTED weights are stored negative: pass abs(weightKg) when showing them as assistance.
 */
fun formatWeight(kg: Double): String {
    if (kg.isNaN() || kg.isInfinite()) return "0"
    val hundredths = (kg * 100.0).roundToLong()
    if (hundredths == 0L) return "0"
    val sign = if (hundredths < 0) "-" else ""
    val a = abs(hundredths)
    val whole = a / 100
    val frac = (a % 100).toInt()
    return when {
        frac == 0 -> "$sign$whole"
        frac % 10 == 0 -> "$sign$whole.${frac / 10}"
        else -> "$sign$whole.${frac.toString().padStart(2, '0')}"
    }
}

/** "52.5 kg" */
fun formatWeightKg(kg: Double): String = "${formatWeight(kg)} kg"

private const val MAX_TARGET = 999

/** Stored target as text for the builder field: null/null = "", min == max = "8", else "8-12". */
fun formatTarget(min: Int?, max: Int?): String = when {
    min == null && max == null -> ""
    min == null -> max.toString()
    max == null || max == min -> min.toString()
    else -> "$min-$max"
}

/** Result of reading the target text. [Incomplete] and [Invalid] mean: keep the previously stored target. */
sealed interface TargetParse {
    data class Valid(val min: Int?, val max: Int?) : TargetParse
    data object Incomplete : TargetParse
    data object Invalid : TargetParse
}

/**
 * Accepts "", "8" (min = max = 8), "8-12" (also en/em dash, spaces allowed).
 * "8-" is Incomplete (user is still typing); "12-8", "0", letters, > 999 are Invalid.
 */
fun parseTarget(text: String): TargetParse {
    val t = text.trim().replace('–', '-').replace('—', '-').replace(" ", "")
    if (t.isEmpty()) return TargetParse.Valid(null, null)
    val parts = t.split("-")
    if (parts.size > 2) return TargetParse.Invalid
    fun num(s: String): Int? =
        if (s.isNotEmpty() && s.length <= 3 && s.all { it in '0'..'9' }) s.toInt().takeIf { it in 1..MAX_TARGET } else null
    val a = num(parts[0]) ?: return TargetParse.Invalid
    if (parts.size == 1) return TargetParse.Valid(a, a)
    if (parts[1].isEmpty()) return TargetParse.Incomplete
    val b = num(parts[1]) ?: return TargetParse.Invalid
    return if (b < a) TargetParse.Invalid else TargetParse.Valid(a, b)
}
