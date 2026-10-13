package com.ashrdev.aznd.ui.macro

import com.ashrdev.aznd.domain.GraphWindow
import java.util.Locale

internal const val HALF_DAY_MS = 12 * 3_600_000L

/** 1850.0 -> "1,850", 12.4 -> "12.4" (for display). */
internal fun n(v: Double): String = GraphWindow.formatValue(v)

/** 12.0 -> "12", 12.4 -> "12.4", never with a thousands separator (for text fields that get parsed again). */
internal fun plain(v: Double): String =
    if (kotlin.math.abs(v - Math.rint(v)) < 0.05) String.format(Locale.US, "%.0f", v)
    else String.format(Locale.US, "%.1f", v)

/** Parses a typed number (comma or dot); blank or junk = null. */
internal fun parseNumber(s: String): Double? = s.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()
