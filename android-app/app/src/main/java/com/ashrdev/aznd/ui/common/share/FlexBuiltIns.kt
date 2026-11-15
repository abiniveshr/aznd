package com.ashrdev.aznd.ui.common.share

import com.ashrdev.aznd.ui.common.tiling.TileGroup
import com.ashrdev.aznd.ui.common.tiling.TileLeaf
import com.ashrdev.aznd.ui.common.tiling.TileNode

// ---------------------------------------------------------------------------------------------
// The built-in designs: the Defaults blueprints (one per screen with a Share button) and the comparison presets of the Presets
// list. Pure data (no Android): the JVM harness compiles this file. Every spec is explicit. The four blueprints leave the layout
// to the tiler (layout = null, auto align on open); the comparison presets whose story depends on the arrangement (two columns,
// two equal heroes) ship a hand-made tree, which the editor keeps (it only auto-aligns a spec without a layout).
// ---------------------------------------------------------------------------------------------

/**
 * One preset per place a card can be shared from. A preset decides which blocks a card starts with and
 * how it looks; the person's edits are remembered per preset (see [FlexStore]). The arguments a
 * preset needs travel in the route: [a] / [b] are ids, [s] is a text key.
 *
 *  HOME        none                         the general "flex" card: streaks, top lifts
 *  WORKOUT     a = session id               one finished workout
 *  ROUTINE     a = workout id               a saved routine (template)
 *  EXERCISE    a = workout id, b = exercise id   one exercise's 1RM progress
 *  STREAK_DAY  a = streak id, s = date      one saved streak day
 */
enum class FlexPreset(val id: String, val label: String) {
    HOME("home", "Flex"),
    WORKOUT("workout", "Workout"),
    ROUTINE("routine", "Routine"),
    EXERCISE("exercise", "Exercise"),
    STREAK_DAY("streak_day", "Streak day");

    companion object {
        fun fromId(id: String?): FlexPreset = entries.firstOrNull { it.id == id } ?: HOME
    }
}

/** A built-in multi-screen preset of the Presets list: a stable [key] (stored on the preset), its [name] and the [spec] it ships with. */
data class FlexComparisonPreset(val key: String, val name: String, val spec: FlexSpec)

private fun leaf(id: Int): TileNode = TileLeaf(id)

/** A column of tiles (heights follow the content). */
private fun col(vararg n: TileNode): TileNode = TileGroup(true, n.toList(), List(n.size) { 0f })

/** A column of tiles with the given height weights. */
private fun weighted(vararg w: Float): (Array<out TileNode>) -> TileNode = { n -> TileGroup(true, n.toList(), w.toList()) }

/** A row of tiles with equal widths. */
private fun evenRow(vararg n: TileNode): TileNode = TileGroup(false, n.toList(), List(n.size) { 1f })

/** A row of tiles whose widths follow their content. */
private fun row(vararg n: TileNode): TileNode = TileGroup(false, n.toList(), List(n.size) { 0f })

private fun builtIn(
    id: Int,
    type: FlexBlockType,
    enabled: Boolean = true,
    variant: Int = 0,
    limit: Int = 3,
    flag: Boolean = true,
    text: String = "",
    stat: FlexWorkoutStat = FlexWorkoutStat.TOP_SET,
    graph: FlexGraphConfig = FlexGraphConfig(),
    cal: FlexCalendarSpec = FlexCalendarSpec()
) = FlexBlock(
    id = id, type = type, enabled = enabled, variant = variant, keys = null, limit = limit, text = text, flag = flag,
    stat = stat, graph = graph, cal = cal
)

/**
 * The blueprint a screen ships with: hand-designed, at most one photo slot (a slot turns itself off when the item has no picture),
 * the optional extras (Text, a second stat) off. [FlexPreset.HOME] has no blueprint (its Share opens the Presets list); it gets
 * a small overview only so the function is total.
 */
fun flexBuiltInBlueprint(source: FlexPreset): FlexSpec = when (source) {
    FlexPreset.WORKOUT -> FlexSpec(
        flexStyleOf(FlexPalettes.Ember, FlexAspect.PORTRAIT),
        listOf(
            builtIn(1, FlexBlockType.HEADER, variant = 0),
            builtIn(2, FlexBlockType.PHOTO, variant = 0),
            builtIn(3, FlexBlockType.WORKOUT_STATS, variant = 0, flag = false, stat = FlexWorkoutStat.SESSION),
            builtIn(4, FlexBlockType.WORKOUT_STATS, variant = 0, limit = 1, stat = FlexWorkoutStat.TOP_SET),
            builtIn(5, FlexBlockType.EXERCISES, variant = 2, limit = 6, flag = false),
            builtIn(6, FlexBlockType.TEXT, enabled = false, text = "Another one done.")
        )
    )

    FlexPreset.ROUTINE -> FlexSpec(
        flexStyleOf(FlexPalettes.Forest, FlexAspect.PORTRAIT),
        listOf(
            builtIn(1, FlexBlockType.HEADER, variant = 0),
            builtIn(2, FlexBlockType.ROUTINE, variant = 0, limit = 10, flag = true),
            builtIn(3, FlexBlockType.TEXT, enabled = false, text = "Try it with me.")
        )
    )

    FlexPreset.EXERCISE -> FlexSpec(
        flexStyleOf(FlexPalettes.Ocean, FlexAspect.PORTRAIT),
        listOf(
            builtIn(1, FlexBlockType.HEADER, variant = 2),
            builtIn(2, FlexBlockType.WORKOUT_STATS, variant = 0, limit = 1, stat = FlexWorkoutStat.ONE_RM),
            // "This exercise's 1RM": the editor draws the sample exercise's series, sharing swaps in the shared exercise's own.
            builtIn(3, FlexBlockType.GRAPH, graph = FlexGraphConfig(listOf(FLEX_SCREEN_EXERCISE_1RM), FlexGraphRange.ALL)),
            builtIn(4, FlexBlockType.WORKOUT_STATS, enabled = false, variant = 1, limit = 3, stat = FlexWorkoutStat.TOP_SET),
            builtIn(5, FlexBlockType.TEXT, enabled = false, text = "New best.")
        )
    )

    FlexPreset.STREAK_DAY -> FlexSpec(
        flexStyleOf(FlexPalettes.Violet, FlexAspect.PORTRAIT),
        listOf(
            builtIn(1, FlexBlockType.HEADER, variant = 1),
            builtIn(2, FlexBlockType.STREAKS, variant = 2, limit = 1),
            builtIn(3, FlexBlockType.PHOTO, variant = 0),
            builtIn(4, FlexBlockType.DAY_NOTE, variant = 0),
            builtIn(5, FlexBlockType.TEXT, enabled = false, text = "Showing up.")
        ),
        // The streak and the photo are equal tiles side by side (the photo is a real tile, not a strip), the note under them.
        layout = col(leaf(1), evenRow(leaf(2), leaf(3)), leaf(4))
    )

    FlexPreset.HOME -> FlexSpec(
        flexStyleOf(FlexPalettes.Midnight, FlexAspect.PORTRAIT),
        listOf(
            builtIn(1, FlexBlockType.HEADER),
            builtIn(2, FlexBlockType.STREAKS, variant = 0, limit = 3),
            builtIn(3, FlexBlockType.WORKOUT_STATS, variant = 1, limit = 3, stat = FlexWorkoutStat.ONE_RM)
        )
    )
}

/**
 * The presets that ship in the Presets list, in list order. Their cards carry sample ids (Exercise A / B, Streak A, the dashboard's
 * volume and bodyweight series); [defaultBindings] turns those into the person's own data (two different workouts, their top two
 * lifts, their top streak ...).
 */
fun flexComparisonPresets(): List<FlexComparisonPreset> {
    val exA = FlexGraphLoader.exerciseId("Exercise A")
    val exB = FlexGraphLoader.exerciseId("Exercise B")
    val streakProgress = FlexGraphLoader.streakId(1L, "progress")
    return listOf(
        FlexComparisonPreset(
            "then_vs_now", "Then vs now",
            FlexSpec(
                flexStyleOf(FlexPalettes.Midnight, FlexAspect.PORTRAIT),
                listOf(
                    builtIn(1, FlexBlockType.HEADER, text = "Then vs now"),
                    // Two parallel columns, "then" on the left: the Session stats say which workout and day they are ("Show workout & date").
                    builtIn(2, FlexBlockType.WORKOUT_STATS, variant = 0, flag = true, stat = FlexWorkoutStat.SESSION),
                    builtIn(3, FlexBlockType.EXERCISES, variant = 2, limit = 4, flag = false),
                    builtIn(4, FlexBlockType.WORKOUT_STATS, variant = 0, flag = true, stat = FlexWorkoutStat.SESSION),
                    builtIn(5, FlexBlockType.EXERCISES, variant = 2, limit = 4, flag = false)
                ),
                layout = weighted(1f, 6f)(arrayOf(leaf(1), evenRow(col(leaf(2), leaf(3)), col(leaf(4), leaf(5)))))
            )
        ),
        FlexComparisonPreset(
            "lift_vs_lift", "Lift vs lift",
            FlexSpec(
                flexStyleOf(FlexPalettes.Ocean, FlexAspect.PORTRAIT),
                listOf(
                    builtIn(1, FlexBlockType.HEADER, text = "Strength check"),
                    builtIn(2, FlexBlockType.WORKOUT_STATS, variant = 0, limit = 1, stat = FlexWorkoutStat.ONE_RM),
                    builtIn(3, FlexBlockType.WORKOUT_STATS, variant = 0, limit = 1, stat = FlexWorkoutStat.ONE_RM),
                    builtIn(4, FlexBlockType.GRAPH, graph = FlexGraphConfig(listOf(exA, exB), FlexGraphRange.MONTHS_6))
                ),
                // Two equal heroes side by side, the compare graph across the full width under them.
                layout = col(leaf(1), evenRow(leaf(2), leaf(3)), leaf(4))
            )
        ),
        FlexComparisonPreset(
            "progress", "Progress",
            FlexSpec(
                flexStyleOf(FlexPalettes.Midnight, FlexAspect.WIDE),
                listOf(
                    builtIn(1, FlexBlockType.HEADER, text = "Progress"),
                    builtIn(2, FlexBlockType.GRAPH, graph = FlexGraphConfig(listOf("volume"), FlexGraphRange.MONTHS_3)),
                    builtIn(3, FlexBlockType.GRAPH, graph = FlexGraphConfig(listOf("bodyweight"), FlexGraphRange.MONTHS_3)),
                    builtIn(4, FlexBlockType.WORKOUT_STATS, variant = 1, limit = 3, stat = FlexWorkoutStat.ONE_RM)
                )
            )
        ),
        FlexComparisonPreset(
            "streak_board", "Streak board",
            FlexSpec(
                flexStyleOf(FlexPalettes.Violet, FlexAspect.PORTRAIT),
                listOf(
                    builtIn(1, FlexBlockType.HEADER, text = "Streaks"),
                    builtIn(2, FlexBlockType.STREAKS, variant = 0, limit = 3),
                    builtIn(3, FlexBlockType.CALENDAR, cal = FlexCalendarSpec(source = "streak:1")),
                    builtIn(4, FlexBlockType.GRAPH, graph = FlexGraphConfig(listOf(streakProgress), FlexGraphRange.MONTHS_3))
                )
            )
        ),
        FlexComparisonPreset(
            "month_in_review", "Month in review",
            FlexSpec(
                flexStyleOf(FlexPalettes.Sunset, FlexAspect.STORY),
                listOf(
                    builtIn(1, FlexBlockType.HEADER, text = "This month"),
                    builtIn(2, FlexBlockType.GRAPH, graph = FlexGraphConfig(listOf("volume"), FlexGraphRange.WEEKS_4)),
                    builtIn(3, FlexBlockType.CALENDAR, cal = FlexCalendarSpec(source = "workouts")),
                    builtIn(4, FlexBlockType.WORKOUT_STATS, variant = 1, limit = 3, stat = FlexWorkoutStat.ONE_RM),
                    builtIn(5, FlexBlockType.STREAKS, variant = 0, limit = 3)
                ),
                // The calendar next to the 1RM rows and the streaks, so its tile is about as tall as it is.
                layout = col(leaf(1), leaf(2), row(leaf(3), leaf(4)), leaf(5))
            )
        )
    )
}
