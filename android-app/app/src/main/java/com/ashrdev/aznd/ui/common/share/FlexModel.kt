package com.ashrdev.aznd.ui.common.share

import android.graphics.Typeface
import com.ashrdev.aznd.domain.formatWeight

/*
 * Flex: the card / poster maker used by every "share" button in the app.
 *
 *  - FlexModel.kt      what a card is made of: [FlexStyle] (look), [FlexBlock]s (content), [FlexContent] (data)
 *  - FlexPresets.kt    one preset per share source (home, workout, routine, ...): default blocks + look,
 *                      and the loaders that turn app data into [FlexContent]
 *  - FlexPainter.kt    draws a card with android.graphics, so the same code makes the preview and the PNG
 *  - FlexExport.kt     PNG file, share sheet, save to gallery
 *  - FlexStore.kt      remembers each preset's customisation
 *  - FlexScreen.kt     the editor
 */

/** Every card is drawn on a canvas this wide; the height follows the aspect. Export scales from it. */
const val FLEX_CARD_WIDTH = 1080

enum class FlexAspect(val label: String, val width: Int, val height: Int) {
    SQUARE("Square 1:1", FLEX_CARD_WIDTH, 1080),
    PORTRAIT("Post 4:5", FLEX_CARD_WIDTH, 1350),
    STORY("Story 9:16", FLEX_CARD_WIDTH, 1920),
    WIDE("Wide 16:9", FLEX_CARD_WIDTH, 608)
}

enum class FlexFont(val label: String) {
    SANS("Sans"),
    CONDENSED("Condensed"),
    SERIF("Serif"),
    MONO("Mono"),
    HEAVY("Heavy"),
    LIGHT("Light");

    fun face(bold: Boolean): Typeface = when (this) {
        SANS -> Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        CONDENSED -> Typeface.create("sans-serif-condensed", if (bold) Typeface.BOLD else Typeface.NORMAL)
        SERIF -> Typeface.create("serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        MONO -> Typeface.create("monospace", if (bold) Typeface.BOLD else Typeface.NORMAL)
        HEAVY -> Typeface.create(if (bold) "sans-serif-black" else "sans-serif-medium", Typeface.NORMAL)
        LIGHT -> Typeface.create(if (bold) "sans-serif-medium" else "sans-serif-light", Typeface.NORMAL)
    }
}

enum class FlexBgMode(val label: String) { SOLID("Solid"), GRADIENT("Gradient"), PHOTO("Photo") }
enum class FlexAlign(val label: String) { START("Left"), CENTER("Centre") }
enum class FlexVAlign(val label: String) { TOP("Top"), CENTER("Middle"), BOTTOM("Bottom") }
enum class FlexPanel(val label: String) { NONE("None"), FILLED("Filled"), OUTLINE("Outline"), GLASS("Glass") }

/** A colour set. Applying one replaces the background, text, accent and panel colours of a card. */
data class FlexPalette(
    val id: String,
    val label: String,
    val bgA: Int,
    val bgB: Int,
    val text: Int,
    val accent: Int,
    val panel: Int
)

object FlexPalettes {
    val Midnight = FlexPalette("midnight", "Midnight", 0xFF0F1220.toInt(), 0xFF1B2140.toInt(), 0xFFF4F6FF.toInt(), 0xFF7C9CFF.toInt(), 0xFFFFFFFF.toInt())
    val Ember = FlexPalette("ember", "Ember", 0xFF1A0B08.toInt(), 0xFF5A1E0F.toInt(), 0xFFFFF3EA.toInt(), 0xFFFF8A4C.toInt(), 0xFFFFFFFF.toInt())
    val Forest = FlexPalette("forest", "Forest", 0xFF07140E.toInt(), 0xFF14442C.toInt(), 0xFFEFFAF3.toInt(), 0xFF5CE0A0.toInt(), 0xFFFFFFFF.toInt())
    val Violet = FlexPalette("violet", "Violet", 0xFF140A24.toInt(), 0xFF4A1F7A.toInt(), 0xFFF6EEFF.toInt(), 0xFFC58CFF.toInt(), 0xFFFFFFFF.toInt())
    val Ocean = FlexPalette("ocean", "Ocean", 0xFF031B34.toInt(), 0xFF0B6E8E.toInt(), 0xFFEAF9FF.toInt(), 0xFF3DD6D0.toInt(), 0xFFFFFFFF.toInt())
    val Sunset = FlexPalette("sunset", "Sunset", 0xFF2B0F3A.toInt(), 0xFFD9482B.toInt(), 0xFFFFF7EE.toInt(), 0xFFFFD166.toInt(), 0xFFFFFFFF.toInt())
    val Paper = FlexPalette("paper", "Paper", 0xFFFAF7F0.toInt(), 0xFFEDE6D6.toInt(), 0xFF1F1B16.toInt(), 0xFFC2410C.toInt(), 0xFF000000.toInt())
    val MonoDark = FlexPalette("mono_dark", "Mono dark", 0xFF000000.toInt(), 0xFF1A1A1A.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt())
    val MonoLight = FlexPalette("mono_light", "Mono light", 0xFFFFFFFF.toInt(), 0xFFE9E9E9.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt())

    val all = listOf(Midnight, Ember, Forest, Violet, Ocean, Sunset, Paper, MonoDark, MonoLight)
}

/** Everything about how a card looks, nothing about what it says. All sizes are in card pixels (1080 wide). */
data class FlexStyle(
    val aspect: FlexAspect = FlexAspect.PORTRAIT,
    val bgMode: FlexBgMode = FlexBgMode.GRADIENT,
    val bgA: Int = FlexPalettes.Midnight.bgA,
    val bgB: Int = FlexPalettes.Midnight.bgB,
    val gradientAngle: Int = 135,
    /** A photo the person picked. For [FlexBgMode.PHOTO] it wins over the share's own photo (e.g. the workout photo). */
    val photoUri: String? = null,
    val photoBlur: Float = 0.5f,
    val photoDim: Float = 0.5f,
    val textColor: Int = FlexPalettes.Midnight.text,
    val accent: Int = FlexPalettes.Midnight.accent,
    val font: FlexFont = FlexFont.SANS,
    val textScale: Float = 1f,
    val align: FlexAlign = FlexAlign.START,
    val vAlign: FlexVAlign = FlexVAlign.TOP,
    val padding: Int = 72,
    val spacing: Int = 28,
    val panel: FlexPanel = FlexPanel.FILLED,
    val panelColor: Int = FlexPalettes.Midnight.panel,
    val panelAlpha: Float = 0.12f,
    val panelRadius: Int = 36,
    val cardRadius: Int = 0,
    val borderWidth: Int = 0,
    val borderColor: Int = FlexPalettes.Midnight.accent,
    val watermark: Boolean = true,
    val watermarkText: String = "aznd"
) {
    fun withPalette(p: FlexPalette): FlexStyle = copy(
        bgA = p.bgA, bgB = p.bgB, textColor = p.text, accent = p.accent,
        panelColor = p.panel, borderColor = p.accent
    )
}

fun flexStyleOf(palette: FlexPalette, aspect: FlexAspect = FlexAspect.PORTRAIT): FlexStyle =
    FlexStyle(aspect = aspect).withPalette(palette)

/** What a block shows. Each type reads its own part of [FlexContent]. */
enum class FlexBlockType(
    val label: String,
    val variants: List<String>,
    /** Label of the block's on/off detail switch, null when it has none. */
    val flagLabel: String?,
    /** True for blocks that show a list of items (so "max items" and item picking make sense). */
    val listy: Boolean
) {
    HEADER("Title", listOf("Standard", "Huge", "Compact"), "Show date", false),
    STREAKS("Streaks", listOf("Cards", "Grid", "Hero number"), "Show longest run", true),
    TOP_SET("Top set", listOf("Hero", "Rows"), "Show RPE and date", true),
    ONE_RM("1 rep max", listOf("Hero", "Rows", "Tiles"), "Show the set it came from", true),
    SESSION_STATS("Workout stats", listOf("Tiles", "One line"), null, false),
    EXERCISES("Exercises", listOf("Detailed", "Compact", "Best set only"), "Show RPE", true),
    ROUTINE("Routine", listOf("Detailed", "Compact"), "Show targets", true),
    CHART("1RM chart", listOf("Line", "Bars"), "Show axis labels", false),
    DAY_NOTE("Day note", listOf("Quote", "Plain"), "Show date", false),
    PHOTO("Photo", listOf("Rounded", "Circle", "Full width"), null, false),
    TEXT("Text", listOf("Plain", "Quote", "Big"), null, false)
}

/**
 * One piece of a card. A block is generic on purpose (every field is a plain value), so a card can be
 * saved as JSON and a preset can be rebuilt from it.
 */
data class FlexBlock(
    val id: Int,
    val type: FlexBlockType,
    val enabled: Boolean = true,
    val variant: Int = 0,
    /** Chosen items (see [FlexContent.choices]); null means "automatic": the first [limit] items. */
    val keys: List<String>? = null,
    val limit: Int = 3,
    /** TEXT: the text. HEADER: replaces the title when not blank. */
    val text: String = "",
    /** The block's detail switch, see [FlexBlockType.flagLabel]. */
    val flag: Boolean = true
)

data class FlexSpec(val style: FlexStyle, val blocks: List<FlexBlock>) {
    fun nextBlockId(): Int = (blocks.maxOfOrNull { it.id } ?: 0) + 1
}

// ---------------------------------------------------------------------------------------------
// Content: the data a card can show. Screens do not build this; the preset loaders do.
// ---------------------------------------------------------------------------------------------

data class FlexStreakItem(
    val id: Long,
    val name: String,
    val days: Int,
    val longest: Int,
    val logoUri: String? = null
)

/** A heaviest-set style fact: one set and the 1RM estimated from it. */
data class FlexLiftItem(
    val name: String,
    val weightKg: Double,
    val reps: Int,
    val rpe: Double?,
    val e1rm: Double,
    val dateMs: Long
)

data class FlexSetLine(
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val seconds: Int = 0,
    val rpe: Double? = null
) {
    fun text(showRpe: Boolean): String {
        val main = when {
            seconds > 0 -> "$seconds s"
            weightKg > 0.0 -> "${formatWeight(weightKg)} kg × $reps"
            else -> "$reps reps"
        }
        val withWeight = if (seconds > 0 && weightKg > 0.0) "$main @ ${formatWeight(weightKg)} kg" else main
        return if (showRpe && rpe != null) "$withWeight · RPE ${formatWeight(rpe)}" else withWeight
    }
}

data class FlexExerciseItem(val name: String, val sets: List<FlexSetLine>, val bestIndex: Int = 0)

data class FlexRoutineItem(val name: String, val sets: Int, val target: String?, val tag: String? = null)

data class FlexStat(val label: String, val value: String)

data class FlexChartPoint(val timeMs: Long, val value: Double)

data class FlexContent(
    /** Small accent line above the title, e.g. "WORKOUT". */
    val kicker: String = "",
    val title: String = "",
    val subtitle: String = "",
    val dateText: String = "",
    val streaks: List<FlexStreakItem> = emptyList(),
    /** Best set per exercise, strongest first. Feeds the Top set and 1 rep max blocks. */
    val lifts: List<FlexLiftItem> = emptyList(),
    val exercises: List<FlexExerciseItem> = emptyList(),
    val routine: List<FlexRoutineItem> = emptyList(),
    val stats: List<FlexStat> = emptyList(),
    val chart: List<FlexChartPoint> = emptyList(),
    val chartLabel: String = "",
    val dayNote: String = "",
    /** The share's own photo (workout photo, saved-day photo). */
    val photoUri: String? = null
) {
    /** True when a block of [type] has something to show here. */
    fun has(type: FlexBlockType): Boolean = when (type) {
        FlexBlockType.HEADER, FlexBlockType.TEXT, FlexBlockType.PHOTO -> true
        FlexBlockType.STREAKS -> streaks.isNotEmpty()
        FlexBlockType.TOP_SET, FlexBlockType.ONE_RM -> lifts.isNotEmpty()
        FlexBlockType.SESSION_STATS -> stats.isNotEmpty()
        FlexBlockType.EXERCISES -> exercises.isNotEmpty()
        FlexBlockType.ROUTINE -> routine.isNotEmpty()
        FlexBlockType.CHART -> chart.isNotEmpty()
        FlexBlockType.DAY_NOTE -> dayNote.isNotBlank()
    }

    /** The items a person can pick between for [type], as (key, label). Empty when the block is not pickable. */
    fun choices(type: FlexBlockType): List<Pair<String, String>> = when (type) {
        FlexBlockType.STREAKS -> streaks.map { streakKey(it) to it.name }
        FlexBlockType.TOP_SET, FlexBlockType.ONE_RM -> lifts.map { it.name to it.name }
        FlexBlockType.EXERCISES -> exercises.map { it.name to it.name }
        else -> emptyList()
    }

    companion object {
        fun streakKey(s: FlexStreakItem) = "s${s.id}"
    }
}
