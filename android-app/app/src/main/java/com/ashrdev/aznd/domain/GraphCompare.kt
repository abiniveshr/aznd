package com.ashrdev.aznd.domain

import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

enum class GraphCombine { AVERAGE, SUM, LAST, MAX, MIN }

enum class CompareBucket(val label: String, val fillGap: Int) {
    DAY("Daily", 7),
    WEEK("Weekly", 2)
}

enum class CompareView(val label: String) { OVERLAY("Over time"), SCATTER("Scatter") }

class CompareRow(val timeMs: Long, val values: DoubleArray, val filled: BooleanArray) {
    fun has(i: Int): Boolean = !values[i].isNaN()
    fun real(i: Int): Boolean = has(i) && !filled[i]
}

class ComparePair(val timeMs: Long, val x: Double, val y: Double, val estimated: Boolean)

class CompareTable(val modes: List<GraphMode>, val rows: List<CompareRow>) {

    fun pairs(i: Int, j: Int): List<ComparePair> = rows
        .filter { it.has(i) && it.has(j) && (it.real(i) || it.real(j)) }
        .map { ComparePair(it.timeMs, it.values[i], it.values[j], it.filled[i] || it.filled[j]) }

    fun correlation(i: Int, j: Int): Correlation? {
        val p = pairs(i, j)
        return GraphCompare.correlate(DoubleArray(p.size) { p[it].x }, DoubleArray(p.size) { p[it].y }, p.count { it.estimated })
    }

    fun matrix(): List<List<Correlation?>> = modes.indices.map { i ->
        modes.indices.map { j -> if (i == j) null else correlation(i, j) }
    }

    fun range(i: Int): Pair<Double, Double>? {
        var lo = Double.POSITIVE_INFINITY
        var hi = Double.NEGATIVE_INFINITY
        for (r in rows) if (r.has(i)) {
            lo = minOf(lo, r.values[i])
            hi = maxOf(hi, r.values[i])
        }
        return if (lo <= hi) lo to hi else null
    }
}

enum class CorrelationStrength(val label: String) {
    NONE("No clear link"),
    WEAK("Weak"),
    MODERATE("Moderate"),
    STRONG("Strong"),
    VERY_STRONG("Very strong")
}

class Correlation(
    val n: Int,
    val estimated: Int,
    val pearson: Double,
    val spearman: Double,
    val pValue: Double,
    val slope: Double,
    val intercept: Double
) {
    val rSquared: Double get() = pearson * pearson

    val strength: CorrelationStrength
        get() {
            val a = abs(pearson)
            return when {
                a < 0.1 -> CorrelationStrength.NONE
                a < 0.3 -> CorrelationStrength.WEAK
                a < 0.5 -> CorrelationStrength.MODERATE
                a < 0.7 -> CorrelationStrength.STRONG
                else -> CorrelationStrength.VERY_STRONG
            }
        }

    val enough: Boolean get() = n >= GraphCompare.MIN_PAIRS

    val significant: Boolean get() = enough && pValue < 0.05

    fun summary(): String {
        if (!enough) return "Too few points to tell (${n} paired)"
        val s = strength
        if (s == CorrelationStrength.NONE) return s.label
        val dir = if (pearson > 0) "positive" else "negative"
        return "${s.label} $dir"
    }

    fun caution(): String? = when {
        !enough -> "Log more days where both are recorded."
        !significant -> "Could be chance (p = ${GraphCompare.formatP(pValue)})."
        else -> null
    }

    fun yAt(x: Double): Double = intercept + slope * x
}

object GraphCompare {
    const val MIN_PAIRS = 5
    const val MAX_SERIES = 4
    private const val DAY_MS = 86_400_000L

    fun combineOf(mode: GraphMode): GraphCombine =
        mode.combine ?: if (mode.style == GraphStyle.BARS) GraphCombine.SUM else GraphCombine.AVERAGE

    fun bucketStart(timeMs: Long, bucket: CompareBucket): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = timeMs
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (bucket == CompareBucket.WEEK) {
            val back = (c.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
            c.add(Calendar.DAY_OF_YEAR, -back)
        }
        return c.timeInMillis
    }

    private fun nextBucket(start: Long, bucket: CompareBucket): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = start
        c.add(Calendar.DAY_OF_YEAR, if (bucket == CompareBucket.WEEK) 7 else 1)
        return c.timeInMillis
    }

    fun buckets(points: List<GraphPoint>, combine: GraphCombine, bucket: CompareBucket): Map<Long, Double> {
        val groups = java.util.TreeMap<Long, MutableList<GraphPoint>>()
        for (p in points) groups.getOrPut(bucketStart(p.timeMs, bucket)) { ArrayList() }.add(p)
        val out = java.util.TreeMap<Long, Double>()
        for ((k, list) in groups) {
            out[k] = when (combine) {
                GraphCombine.SUM -> list.sumOf { it.value }
                GraphCombine.AVERAGE -> list.sumOf { it.value } / list.size
                GraphCombine.LAST -> list.maxBy { it.timeMs }.value
                GraphCombine.MAX -> list.maxOf { it.value }
                GraphCombine.MIN -> list.minOf { it.value }
            }
        }
        return out
    }

    fun windowStart(modes: List<GraphMode>, range: GraphRange, nowMs: Long): Long? {
        val all = modes.flatMap { it.points }
        if (all.isEmpty()) return null
        val first = all.minOf { it.timeMs }
        val months = range.months
        val recent = range.recent
        return when {
            months != null -> maxOf(GraphWindow.monthsBack(nowMs, months), first)
            recent != null -> modes.firstOrNull()?.points?.map { it.timeMs }?.sorted()?.takeLast(recent)?.firstOrNull() ?: first
            else -> first
        }
    }

    fun table(modes: List<GraphMode>, bucket: CompareBucket, startMs: Long? = null, endMs: Long? = null): CompareTable {
        val m = modes.size
        val series = modes.map { mode ->
            val pts = mode.points.filter { (startMs == null || it.timeMs >= startMs) && (endMs == null || it.timeMs <= endMs) }
            buckets(pts, combineOf(mode), bucket)
        }
        val keys = java.util.TreeSet<Long>()
        series.forEach { keys.addAll(it.keys) }
        if (keys.isEmpty()) return CompareTable(modes, emptyList())
        val slots = ArrayList<Long>()
        var k = keys.first()
        val last = keys.last()
        while (k <= last) {
            slots += k
            k = nextBucket(k, bucket)
        }
        val values = Array(m) { DoubleArray(slots.size) { Double.NaN } }
        val filled = Array(m) { BooleanArray(slots.size) }
        for (i in 0 until m) {
            slots.forEachIndexed { s, key -> series[i][key]?.let { values[i][s] = it } }
            if (combineOf(modes[i]) != GraphCombine.SUM) fillGaps(values[i], filled[i], bucket.fillGap)
        }
        val half = if (bucket == CompareBucket.WEEK) DAY_MS * 7 / 2 else DAY_MS / 2
        val rows = slots.indices
            .filter { s -> (0 until m).any { i -> !values[i][s].isNaN() } }
            .map { s -> CompareRow(slots[s] + half, DoubleArray(m) { values[it][s] }, BooleanArray(m) { filled[it][s] }) }
        return CompareTable(modes, rows)
    }

    private fun fillGaps(v: DoubleArray, filled: BooleanArray, maxGap: Int) {
        var prev = -1
        for (s in v.indices) {
            if (v[s].isNaN()) continue
            if (prev >= 0 && s - prev > 1 && s - prev - 1 <= maxGap) {
                val a = v[prev]
                val b = v[s]
                for (g in prev + 1 until s) {
                    v[g] = a + (b - a) * (g - prev).toDouble() / (s - prev)
                    filled[g] = true
                }
            }
            prev = s
        }
    }

    fun correlate(xs: DoubleArray, ys: DoubleArray, estimated: Int = 0): Correlation? {
        val n = xs.size
        if (n != ys.size || n < 2) return null
        val mx = xs.average()
        val my = ys.average()
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        for (k in 0 until n) {
            val dx = xs[k] - mx
            val dy = ys[k] - my
            sxy += dx * dy
            sxx += dx * dx
            syy += dy * dy
        }
        if (sxx <= 0.0 || syy <= 0.0) return null
        val r = (sxy / sqrt(sxx * syy)).coerceIn(-1.0, 1.0)
        val rho = pearsonOf(ranks(xs), ranks(ys))
        val slope = sxy / sxx
        return Correlation(n, estimated, r, rho, pValue(r, n), slope, my - slope * mx)
    }

    private fun pearsonOf(a: DoubleArray, b: DoubleArray): Double {
        val ma = a.average()
        val mb = b.average()
        var sab = 0.0
        var saa = 0.0
        var sbb = 0.0
        for (k in a.indices) {
            sab += (a[k] - ma) * (b[k] - mb)
            saa += (a[k] - ma) * (a[k] - ma)
            sbb += (b[k] - mb) * (b[k] - mb)
        }
        return if (saa <= 0.0 || sbb <= 0.0) 0.0 else (sab / sqrt(saa * sbb)).coerceIn(-1.0, 1.0)
    }

    fun ranks(v: DoubleArray): DoubleArray {
        val order = v.indices.sortedBy { v[it] }
        val out = DoubleArray(v.size)
        var i = 0
        while (i < order.size) {
            var j = i
            while (j + 1 < order.size && v[order[j + 1]] == v[order[i]]) j++
            val rank = (i + j) / 2.0 + 1.0
            for (k in i..j) out[order[k]] = rank
            i = j + 1
        }
        return out
    }

    fun pValue(r: Double, n: Int): Double {
        if (n < 3) return 1.0
        val df = n - 2.0
        val r2 = r * r
        if (r2 >= 1.0) return 0.0
        val t2 = r2 * df / (1.0 - r2)
        return incompleteBeta(df / 2.0, 0.5, df / (df + t2)).coerceIn(0.0, 1.0)
    }

    private fun logGamma(x: Double): Double {
        val g = doubleArrayOf(
            76.18009172947146, -86.50532032941677, 24.01409824083091,
            -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5
        )
        var y = x
        val tmp = x + 5.5 - (x + 0.5) * ln(x + 5.5)
        var ser = 1.000000000190015
        for (c in g) {
            y += 1.0
            ser += c / y
        }
        return -tmp + ln(2.5066282746310005 * ser / x)
    }

    fun incompleteBeta(a: Double, b: Double, x: Double): Double {
        if (x <= 0.0) return 0.0
        if (x >= 1.0) return 1.0
        val front = exp(logGamma(a + b) - logGamma(a) - logGamma(b) + a * ln(x) + b * ln(1.0 - x))
        return if (x < (a + 1.0) / (a + b + 2.0)) front * betaFraction(a, b, x) / a
        else 1.0 - front * betaFraction(b, a, 1.0 - x) / b
    }

    private fun betaFraction(a: Double, b: Double, x: Double): Double {
        val tiny = 1e-300
        val qab = a + b
        val qap = a + 1.0
        val qam = a - 1.0
        var c = 1.0
        var d = 1.0 - qab * x / qap
        if (abs(d) < tiny) d = tiny
        d = 1.0 / d
        var h = d
        for (m in 1..300) {
            val m2 = 2.0 * m
            var aa = m * (b - m) * x / ((qam + m2) * (a + m2))
            d = 1.0 + aa * d
            if (abs(d) < tiny) d = tiny
            c = 1.0 + aa / c
            if (abs(c) < tiny) c = tiny
            d = 1.0 / d
            h *= d * c
            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2))
            d = 1.0 + aa * d
            if (abs(d) < tiny) d = tiny
            c = 1.0 + aa / c
            if (abs(c) < tiny) c = tiny
            d = 1.0 / d
            val del = d * c
            h *= del
            if (abs(del - 1.0) < 1e-12) break
        }
        return h
    }

    fun formatP(p: Double): String = when {
        p < 0.001 -> "< 0.001"
        p < 0.01 -> String.format(Locale.getDefault(), "%.3f", p)
        else -> String.format(Locale.getDefault(), "%.2f", p)
    }

    fun formatR(r: Double): String = String.format(Locale.getDefault(), "%+.2f", r)
}
