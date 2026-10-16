package com.ashrdev.aznd.data.streaks

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
