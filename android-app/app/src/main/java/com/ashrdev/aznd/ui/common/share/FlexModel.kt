package com.ashrdev.aznd.ui.common.share

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Typeface
import android.os.Build
import com.ashrdev.aznd.domain.workout.formatWeight
import com.ashrdev.aznd.ui.theme.FillBase
import com.ashrdev.aznd.ui.theme.FillStyle
import com.ashrdev.aznd.ui.theme.GradientKind
import com.ashrdev.aznd.ui.theme.GradientSpec
import com.ashrdev.aznd.ui.theme.PatternSpec
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/*
 * Flex: the card / poster maker used by every "share" button in the app.
 *
 *  - FlexModel.kt      what a card is made of: [FlexStyle] (look), [FlexBlock]s (content), [FlexContent] (data)
 *  - FlexPresets.kt    one preset per share source (home, workout, routine, ...): default blocks + look,
 *                      and the loaders that turn app data into [FlexContent]
 *  - FlexLayout.kt     the tile layout (a tree of splits, like a tiling window manager) and the editing of it
 *  - FlexPainter.kt    draws a card with android.graphics, so the same code makes the preview and the PNG
 *  - FlexExport.kt     PNG file, share sheet, save to gallery
 *  - FlexStore.kt      remembers each preset's customisation
 *  - FlexScreen.kt     the editor
 */

/**
 * Every card is laid out (in card pixels) and exported with its shorter side this long: 1080 wide for the upright
 * and square sizes, 1080 tall for 16:9 (so 1920 × 1080). A Free size card is 1080 wide; one that comes out shorter
 * than that is exported scaled up so its height is 1080.
 */
const val FLEX_CARD_SHORT = 1080

/**
 * How big blocks' content is drawn as standard, on top of each block's own size: below 1 so more blocks fit on a
 * card before any has to shrink.
 */
const val FLEX_MODULE_SCALE = 0.8f

/** The range of the Module size slider ([FlexStyle.moduleScale]). */
const val FLEX_MODULE_MIN = 0.6f
const val FLEX_MODULE_MAX = 1.5f

/** The most best fit grows a card's modules by when there is room to spare ([FlexStyle.fitBoost]). */
const val FLEX_BOOST_MAX = 2f

/**
 * Card sizes, in card pixels. How much a size holds is its content budget (see [FlexPainter.load]): every module
 * costs the room it needs at its least, and a module that would take the card past its budget is not added (the
 * editor says the card cannot fit any more content). [FREE] has no fixed height: the card grows or shrinks to fit
 * whatever is on it, up to a tallest card (its [height] is 0, the real height comes from the layout plan).
 */
enum class FlexAspect(val label: String, val width: Int, val height: Int) {
    SQUARE("Square 1:1", FLEX_CARD_SHORT, 1080),
    PORTRAIT("Post 4:5", FLEX_CARD_SHORT, 1350),
    STORY("Story 9:16", FLEX_CARD_SHORT, 1920),
    WIDE("Wide 16:9", 1920, FLEX_CARD_SHORT),
    FREE("Free size (fits content)", FLEX_CARD_SHORT, 0)
}

/** The photo shapes offered in a photo block's crop menu (those inside the card's [photoLimit]). */
enum class FlexPhotoRatio(val label: String, val ratio: Float) {
    ORIGINAL("Original", 0f),
    R1_1("1:1", 1f),
    R4_3("4:3", 4f / 3f),
    R3_4("3:4", 3f / 4f),
    R3_2("3:2", 3f / 2f),
    R2_3("2:3", 2f / 3f),
    R5_4("5:4", 5f / 4f),
    R4_5("4:5", 4f / 5f),
    R16_9("16:9", 16f / 9f),
    R9_16("9:16", 9f / 16f),
    R21_9("21:9", 21f / 9f);

    fun allowedOn(limit: FlexPhotoLimit): Boolean {
        if (this == ORIGINAL) return true
        return ratio >= limit.range.start - 0.005f && ratio <= limit.range.endInclusive + 0.005f
    }
}

/** Under the footer line of the watermark, in card pixels. */
const val FLEX_FOOTER = 56f

/**
 * How big a photo may be: at most half of the card. Of the two halves (top or bottom; left or right) the one
 * whose shape is closer to a square is used (the top half when they tie), so a portrait card allows a wide
 * photo across half its height and a wide card one in half its width. [range] is the shapes (width / height)
 * that suit that half, from three quarters to four thirds of its own shape.
 */
class FlexPhotoLimit(val maxWidth: Float, val maxHeight: Float, val range: ClosedFloatingPointRange<Float>)

fun FlexStyle.photoLimit(): FlexPhotoLimit {
    val pad = padding.toFloat()
    val footer = if (watermark && watermarkText.isNotBlank()) FLEX_FOOTER else 0f
    val w = (aspect.width - 2 * pad).coerceAtLeast(100f)
    // Free size has no height of its own: judge it as a 4:5 post.
    val cardH = if (aspect.height > 0) aspect.height else FlexAspect.PORTRAIT.height
    val h = (cardH - 2 * pad - footer).coerceAtLeast(100f)
    val top = w / (h / 2f)
    val side = (w / 2f) / h
    val useTop = abs(ln(top)) <= abs(ln(side)) + 0.0001f
    val maxW = if (useTop) w else w / 2f
    val maxH = if (useTop) h / 2f else h
    val shape = maxW / maxH
    return FlexPhotoLimit(maxW, maxH, max(0.5f, shape * 0.75f)..min(2.4f, shape * 4f / 3f))
}

/**
 * Text styles. The first six use the fonts Android ships with. The rest are open-source families (all SIL
 * Open Font License) bundled as files in assets/fonts/; if a file is missing the card falls back to Sans.
 * [boldAsset] is a separate bold file; [variable] means one variable font whose weight is set on load.
 */
enum class FlexFont(
    val label: String,
    val asset: String? = null,
    val boldAsset: String? = null,
    val variable: Boolean = false
) {
    SANS("Sans"),
    CONDENSED("Condensed"),
    SERIF("Serif"),
    MONO("Mono"),
    HEAVY("Heavy"),
    LIGHT("Light"),
    BEBAS("Bebas Neue", asset = "fonts/BebasNeue-Regular.ttf"),
    ANTON("Anton", asset = "fonts/Anton-Regular.ttf"),
    ARCHIVO_BLACK("Archivo Black", asset = "fonts/ArchivoBlack-Regular.ttf"),
    SPACE_MONO("Space Mono", asset = "fonts/SpaceMono-Regular.ttf", boldAsset = "fonts/SpaceMono-Bold.ttf"),
    PLAYFAIR("Playfair Display", asset = "fonts/PlayfairDisplay-Variable.ttf", variable = true),
    DM_SERIF("DM Serif Display", asset = "fonts/DMSerifDisplay-Regular.ttf");

    fun face(bold: Boolean): Typeface = when (this) {
        SANS -> Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        CONDENSED -> Typeface.create("sans-serif-condensed", if (bold) Typeface.BOLD else Typeface.NORMAL)
        SERIF -> Typeface.create("serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        MONO -> Typeface.create("monospace", if (bold) Typeface.BOLD else Typeface.NORMAL)
        HEAVY -> Typeface.create(if (bold) "sans-serif-black" else "sans-serif-medium", Typeface.NORMAL)
        LIGHT -> Typeface.create(if (bold) "sans-serif-medium" else "sans-serif-light", Typeface.NORMAL)
        else -> FlexFonts.load(this, bold) ?: SANS.face(bold)
    }
}

/** Loads and caches the bundled fonts. [attach] is called once with any context before a card is drawn. */
object FlexFonts {
    @Volatile
    private var assets: AssetManager? = null
    private val cache = ConcurrentHashMap<String, Typeface>()

    fun attach(context: Context) {
        if (assets == null) assets = context.applicationContext.assets
    }

    internal fun load(font: FlexFont, bold: Boolean): Typeface? {
        val am = assets ?: return null
        val path = (if (bold) font.boldAsset else null) ?: font.asset ?: return null
        val key = "$path|$bold"
        cache[key]?.let { return it }
        val face = runCatching {
            if (font.variable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Typeface.Builder(am, path)
                    .setFontVariationSettings(if (bold) "'wght' 700" else "'wght' 400")
                    .build()
            } else {
                Typeface.createFromAsset(am, path)
            }
        }.getOrNull() ?: return null
        cache[key] = face
        return face
    }
}

enum class FlexBgMode(val label: String) { SOLID("Solid"), GRADIENT("Gradient"), PHOTO("Photo") }
enum class FlexAlign(val label: String) { START("Left"), CENTER("Centre"), END("Right") }
enum class FlexVAlign(val label: String) { TOP("Top"), CENTER("Middle"), BOTTOM("Bottom") }
enum class FlexPanel(val label: String) { NONE("None"), FILLED("Filled"), OUTLINE("Outline"), GLASS("Glass") }

/**
 * How blocks are placed on a card.
 *  - [SPLIT]: tiles that fill the card; a drop splits the tile it lands on (or a whole row or column), and the card is
 *    fitted by itself on every change (nearest fit, modules grown into spare room).
 *  - [FLOW] (the default): blocks in rows in their order, a row each until the person drops one beside another
 *    (then they share the row, if both keep their least widths, [FlexBoxLimits]). A held block dropped in a gap goes
 *    there; dropped on a tile, beside it; the others flow round it to their places.
 *  - [FREE]: every block is a box placed and sized by hand anywhere on the card, overlapping if wanted, the later
 *    in the list on top (like slides in a presentation); nothing is fitted by itself.
 */
enum class FlexLayoutMode(val label: String, val note: String) {
    FLOW("Flow", "A row for each block, in order. Drop a block in a gap to move it there, or on a tile to put it beside that tile if both fit."),
    SPLIT("Tiles", "Drop a block on a side of another to split it. The card fits itself after every change."),
    FREE("Free", "Place and size every block anywhere, like a slide. Blocks can overlap; later ones sit on top.")
}

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

/** Everything about how a card looks, nothing about what it says. All sizes are in card pixels (see [FLEX_CARD_SHORT]). */
data class FlexStyle(
    val aspect: FlexAspect = FlexAspect.PORTRAIT,
    val bgMode: FlexBgMode = FlexBgMode.GRADIENT,
    val bgA: Int = FlexPalettes.Midnight.bgA,
    val bgB: Int = FlexPalettes.Midnight.bgB,
    val gradientAngle: Int = 135,
    /** An advanced gradient (any number of colours, linear / radial / sweep). Null = the two-colour one from [bgA], [bgB] and [gradientAngle]. */
    val bgGradient: GradientSpec? = null,
    /** Patterns (noise, brushed ...) over a solid or gradient background, first = lowest. */
    val bgPatterns: List<PatternSpec> = emptyList(),
    /** A photo the person picked. For [FlexBgMode.PHOTO] it wins over the share's own photo (e.g. the workout photo). */
    val photoUri: String? = null,
    val photoBlur: Float = 0.5f,
    val photoDim: Float = 0.5f,
    val textColor: Int = FlexPalettes.Midnight.text,
    val accent: Int = FlexPalettes.Midnight.accent,
    val font: FlexFont = FlexFont.SANS,
    val textScale: Float = 1f,
    val padding: Int = 72,
    val spacing: Int = 28,
    val panel: FlexPanel = FlexPanel.FILLED,
    val panelColor: Int = FlexPalettes.Midnight.panel,
    val panelAlpha: Float = 0.12f,
    val panelRadius: Int = 36,
    /** Gradient / pattern for Filled panels; [panelColor] is the solid colour under it. Plain = just [panelColor]. */
    val panelFill: FillStyle = FillStyle.Plain,
    /** Border around every panel in card pixels; 0 keeps each panel type's own look (none, 3 px outline, 2 px glass edge). */
    val panelBorderWidth: Int = 0,
    val panelBorderColor: Int = FlexPalettes.Midnight.accent,
    val cardRadius: Int = 0,
    val borderWidth: Int = 0,
    val borderColor: Int = FlexPalettes.Midnight.accent,
    val watermark: Boolean = true,
    val watermarkText: String = "aznd",
    /** Where the watermark sits along the bottom of the card. */
    val watermarkAlign: FlexAlign = FlexAlign.START,
    /** Module size, the person's: every block's content is drawn at this on top of [FLEX_MODULE_SCALE] and its own size. */
    val moduleScale: Float = 1f,
    /**
     * Best fit's own growth on top of [moduleScale], 1 or more: when a fixed-size tiled card has room to spare, best
     * fit draws its modules bigger to fill it ([FlexLayoutMode.SPLIT] only). Not a control; set by best fit.
     */
    val fitBoost: Float = 1f,
    /** How blocks are placed: flowing rows, split tiles, or free boxes (see [FlexLayoutMode]). */
    val layoutMode: FlexLayoutMode = FlexLayoutMode.FLOW
) {
    /** How big modules are drawn: the person's module size times best fit's growth. */
    val moduleSize: Float get() = moduleScale * fitBoost

    /**
     * The air a title keeps above it when it is the first tile at the top edge: about half the card padding,
     * at least 24 px. It counts in the title's tile height, and gives way before the title would shrink below 0.92.
     */
    val titleInset: Float get() = max(24f, padding / 2f)

    fun withPalette(p: FlexPalette): FlexStyle = copy(
        bgA = p.bgA, bgB = p.bgB, textColor = p.text, accent = p.accent,
        panelColor = p.panel, borderColor = p.accent,
        // A starter look is plain colours: it clears gradients, patterns and panel borders.
        bgGradient = null, bgPatterns = emptyList(), panelFill = FillStyle.Plain,
        panelBorderWidth = 0, panelBorderColor = p.accent
    )

    /** The background as a fill (for Solid and Gradient modes; a photo is drawn separately). */
    fun backgroundFill(): FillStyle = FillStyle(
        base = if (bgMode == FlexBgMode.GRADIENT) FillBase.GRADIENT else FillBase.SOLID,
        gradient = bgGradient ?: GradientSpec.of(GradientKind.LINEAR, gradientAngle, bgA, bgB),
        patterns = bgPatterns
    )
}

fun flexStyleOf(palette: FlexPalette, aspect: FlexAspect = FlexAspect.PORTRAIT): FlexStyle =
    FlexStyle(aspect = aspect).withPalette(palette)

/**
 * How a block takes its tile: the rules it is measured, scaled, fitted and priced by. The painter's plan and
 * the auto aligner both go through [FlexFit] with one function per class, so the two never disagree.
 *
 *  - [TITLE]      read first; never shrunk if it can help it, wraps only when it must; at the top edge of the card
 *                 it keeps a little air above it (see [FlexStyle.titleInset]).
 *  - [DRAWABLE]   a picture of some kind: a photo the person added, a chart, a calendar, a snapshot of the 3D body
 *                 model. No text scale; it has a shape (a range of aspect ratios), min / max sizes and a least size at
 *                 which it still reads as a picture ([FlexPainter.VISIBLE_AREA]). It fills its cell edge to edge within
 *                 them, hugs the cell's sides when it nearly reaches them, and sits in the middle when it does not.
 *  - [STATISTIC]  numbers with units and labels: nothing ever wraps; the block scales as one unit (one scale for
 *                 every number in it) and reflows its columns (3 → 2 → 1) before it shrinks by more than a little.
 *  - [TEXT]       running text: wraps on whole words only (never mid-word, never an ellipsis), and when the cell is
 *                 narrower than its longest word it scales down until that word fits.
 */
enum class FlexBlockClass { TITLE, DRAWABLE, STATISTIC, TEXT }

/** What a block shows. Each type reads its own part of [FlexContent]; [blockClass] says how its tile behaves. */
enum class FlexBlockType(
    val label: String,
    val variants: List<String>,
    /** Label of the block's on/off detail switch, null when it has none. */
    val flagLabel: String?,
    /** True for blocks that show a list of items (so "max items" and item picking make sense). */
    val listy: Boolean,
    val blockClass: FlexBlockClass
) {
    HEADER("Title", listOf("Standard", "Huge", "Compact"), "Show date", false, FlexBlockClass.TITLE),
    STREAKS("Streaks", listOf("Cards", "Grid", "Hero number"), "Show longest run", true, FlexBlockClass.STATISTIC),
    TOP_SET("Top set", listOf("Hero", "Rows"), "Show RPE and date", true, FlexBlockClass.STATISTIC),
    ONE_RM("1 rep max", listOf("Hero", "Rows", "Tiles"), "Show \"calculated from\"", true, FlexBlockClass.STATISTIC),
    SESSION_STATS("Workout stats", listOf("Tiles", "One line"), null, false, FlexBlockClass.STATISTIC),
    // Lists of names: they wrap like text (on whole words).
    EXERCISES("Exercises", listOf("Detailed", "Compact", "Best set only"), "Show RPE", true, FlexBlockClass.TEXT),
    ROUTINE("Routine", listOf("Detailed", "Compact"), "Show targets", true, FlexBlockClass.TEXT),
    CHART("1RM chart", listOf("Line", "Bars"), "Show axis labels", false, FlexBlockClass.DRAWABLE),
    DAY_NOTE("Day note", listOf("Quote", "Plain"), "Show date", false, FlexBlockClass.TEXT),
    PHOTO("Photo", listOf("Rounded", "Circle", "Full width"), null, false, FlexBlockClass.DRAWABLE),
    TEXT("Text", listOf("Plain", "Quote", "Big"), null, false, FlexBlockClass.TEXT),
    CALENDAR("Calendar", listOf("Month", "Dots"), "Show weekdays", false, FlexBlockClass.DRAWABLE),
    MODEL("3D model", listOf("Plain", "On a panel"), null, false, FlexBlockClass.DRAWABLE)
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
    val flag: Boolean = true,
    /**
     * How big the block's content is drawn inside its tile: 1 = natural, 0.5 to 2 (with the module size). Everything in it
     * (text, numbers, images) scales together. Content takes up its tile: it shrinks when the tile is too small and grows
     * when the tile has room, up to [FlexFit.FILL_MAX] times this, so this (and the module size) is also how big it may get.
     */
    val size: Float = 1f,
    /** Where the content sits inside the block's tile, across and down. Every tile has its own. */
    val hAlign: FlexAlign = FlexAlign.START,
    val vAlign: FlexVAlign = FlexVAlign.TOP,
    /** Text-like blocks: the tile takes the whole width of its cell instead of only what its content needs. Auto align sets it, so modules line up edge to edge. */
    val fillWidth: Boolean = false,
    /** PHOTO: the crop shape (width / height); 0 = the photo's own shape. Limited by [FlexStyle.photoLimit]. */
    val photoRatio: Float = 0f,
    /** PHOTO: zoom into the picture, 1 to 3. */
    val photoZoom: Float = 1f,
    /** PHOTO: where the crop sits, 0 to 1 from left to right and from top to bottom (0.5 = centre). */
    val photoX: Float = 0.5f,
    val photoY: Float = 0.5f,
    /**
     * Free mode ([FlexLayoutMode.FREE]): the block's box on the card, in card pixels from the card's top left corner.
     * [freeW] 0 or less: not placed yet (it is put below the others).
     */
    val freeX: Float = 0f,
    val freeY: Float = 0f,
    val freeW: Float = 0f,
    val freeH: Float = 0f
) {
    /** Free mode: whether the block has a box of its own yet. */
    val placed: Boolean get() = freeW > 0f && freeH > 0f
}

/**
 * A card: its look, its blocks and how the blocks are tiled on it. [layout] is the tiling (see FlexLayout.kt);
 * null means automatic (the blocks one under the other, in list order).
 */
data class FlexSpec(val style: FlexStyle, val blocks: List<FlexBlock>, val layout: FlexNode? = null) {
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

/**
 * A month for the Calendar block: [days] days, the first a [firstWeekday] (0 = Monday ... 6 = Sunday), the [marked]
 * ones (days of the month, 1 based) highlighted, [today] ringed (0 = not this month). [label] is the line above it,
 * e.g. the streak's name.
 */
data class FlexCalendar(
    val title: String,
    val days: Int,
    val firstWeekday: Int,
    val marked: Set<Int>,
    val today: Int = 0,
    val label: String = ""
) {
    /** How many week rows the month takes. */
    val weeks: Int get() = (firstWeekday + days + 6) / 7
}

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
    val photoUri: String? = null,
    /** The month the Calendar block shows (kept days of a streak, days trained). */
    val calendar: FlexCalendar? = null,
    /** A snapshot of the 3D body model, as an image uri (the 3D model block). */
    val modelUri: String? = null
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
        FlexBlockType.CALENDAR -> calendar != null && calendar.days > 0
        FlexBlockType.MODEL -> modelUri != null
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
