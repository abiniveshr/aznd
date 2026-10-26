package com.ashrdev.aznd.data.streaks

import com.ashrdev.aznd.domain.streaks.ProgressUnit

const val DEFAULT_MILESTONES_CSV = "10,25,50,100,200,300,400,500,1000"

val DEFAULT_MILESTONES: List<Int> = parseMilestones(DEFAULT_MILESTONES_CSV)

/** "10,25,50" -> [10, 25, 50]. Ignores junk, zero/negative values and duplicates; always sorted. */
fun parseMilestones(csv: String): List<Int> =
    csv.split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it > 0 }
        .distinct()
        .sorted()

fun formatMilestones(milestones: List<Int>): String =
    milestones.filter { it > 0 }.distinct().sorted().joinToString(",")

/** The first milestone strictly above [days], or null when every milestone has been reached. */
fun nextMilestone(days: Int, milestones: List<Int>): Int? = milestones.firstOrNull { it > days }

fun defaultMilestonesFor(unit: ProgressUnit): List<Int> = when (unit) {
    ProgressUnit.DAYS -> DEFAULT_MILESTONES
    ProgressUnit.WEEKS -> listOf(2, 4, 8, 12, 26, 52, 104)
    ProgressUnit.MONTHS -> listOf(1, 3, 6, 12, 24, 36, 60)
}

fun milestonesInDays(csv: String, unit: ProgressUnit): String =
    formatMilestones(parseMilestones(csv).map { unit.toDays(it) })
