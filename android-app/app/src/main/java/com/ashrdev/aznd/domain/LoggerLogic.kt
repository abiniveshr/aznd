package com.ashrdev.aznd.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/** Every input a logger row can show. */
enum class LogField { WEIGHT, ASSIST, EXTRA_KG, REPS, SECONDS, RPE }

/** Exercise metadata used by the logger UI and calculations. */
data class ExerciseMeta(
    val type: ExerciseType,
    val secondaryMuscleCount: Int = 0,
    val bodyweightShare: Double = 1.0
) {
    val profile: ExerciseProfile get() = ExerciseProfile(type, bodyweightShare)
}

/** Layout of one set card: [big] is typed every set (full width); [second] share row 2. */
data class FieldLayout(val big: LogField, val second: List<LogField>)

/**
 * WEIGHTED: big weight, row 2 Reps | RPE.  ASSISTED: big "Assist kg", row 2 Reps | RPE.
 * BODYWEIGHT_REPS: big Reps, row 2 "+ kg" | RPE.  TIME_HELD: big Seconds, row 2 "+ kg" | RPE.
 * With RPE off the RPE field disappears and the other field takes the whole row.
 */
fun fieldLayout(type: ExerciseType, rpeOn: Boolean): FieldLayout {
    val rpe = if (rpeOn) listOf(LogField.RPE) else emptyList()
    return when (type) {
        ExerciseType.WEIGHTED -> FieldLayout(LogField.WEIGHT, listOf(LogField.REPS) + rpe)
        ExerciseType.ASSISTED_BODYWEIGHT -> FieldLayout(LogField.ASSIST, listOf(LogField.REPS) + rpe)
        ExerciseType.BODYWEIGHT_REPS -> FieldLayout(LogField.REPS, listOf(LogField.EXTRA_KG) + rpe)
        ExerciseType.TIME_HELD -> FieldLayout(LogField.SECONDS, listOf(LogField.EXTRA_KG) + rpe)
    }
}

fun LogField.label(): String = when (this) {
    LogField.WEIGHT -> "Weight kg"
    LogField.ASSIST -> "Assist kg"
    LogField.EXTRA_KG -> "+ kg"
    LogField.REPS -> "Reps"
    LogField.SECONDS -> "Seconds"
    LogField.RPE -> "RPE"
}

/** The field a weight suggestion fills. Null for TIME_HELD (never suggested). */
fun suggestionField(type: ExerciseType): LogField? = when (type) {
    ExerciseType.WEIGHTED -> LogField.WEIGHT
    ExerciseType.BODYWEIGHT_REPS -> LogField.EXTRA_KG
    ExerciseType.ASSISTED_BODYWEIGHT -> LogField.ASSIST
    ExerciseType.TIME_HELD -> null
}

// ---------------------------------------------------------------- parsing

private const val MAX_KG = 2000.0
private const val MAX_COUNT = 99_999

/** "52,5" or "52.5" or "7." -> number. Null for anything else (blank, letters, "-", "."). */
fun parseDecimal(text: String): Double? {
    val t = text.trim().replace(',', '.')
    if (t.isEmpty() || t.count { it == '.' } > 1) return null
    if (!t.all { it in '0'..'9' || it == '.' }) return null
    if (t.none { it in '0'..'9' }) return null
    return t.toDoubleOrNull()
}

/** RPE 5 to 10 in halves. */
fun parseRpe(text: String): Double? {
    val v = parseDecimal(text) ?: return null
    return if (v in 5.0..10.0 && v * 2 == Math.rint(v * 2)) v else null
}

fun parseCount(text: String): Int? {
    val t = text.trim()
    if (t.isEmpty() || t.length > 5 || !t.all { it in '0'..'9' }) return null
    return t.toInt().takeIf { it <= MAX_COUNT }
}

// ---------------------------------------------------------------- row <-> text

/** Text shown in [field] for this row ("" when not logged). ASSISTED weights show positive. */
fun fieldText(row: SetRow, field: LogField): String = when (field) {
    LogField.WEIGHT, LogField.EXTRA_KG -> row.weightKg?.let { formatWeight(it) }
    LogField.ASSIST -> row.weightKg?.let { formatWeight(abs(it)) }
    LogField.REPS -> row.reps?.toString()
    LogField.SECONDS -> row.durationSec?.toString()
    LogField.RPE -> row.rpe?.let { formatWeight(it) }
}.orEmpty()

/**
 * The row after the user typed [text] into [field]; blank clears the value; null = invalid text,
 * keep the old value. ASSISTED rows store the weight NEGATIVE.
 */
fun applyFieldText(row: SetRow, field: LogField, text: String): SetRow? {
    val blank = text.isBlank()
    return when (field) {
        LogField.WEIGHT, LogField.EXTRA_KG -> {
            if (blank) return row.copy(weightKg = null)
            val v = parseDecimal(text)?.takeIf { it <= MAX_KG } ?: return null
            row.copy(weightKg = v)
        }
        LogField.ASSIST -> {
            if (blank) return row.copy(weightKg = null)
            val v = parseDecimal(text)?.takeIf { it <= MAX_KG } ?: return null
            row.copy(weightKg = if (v == 0.0) 0.0 else -v)
        }
        LogField.REPS -> if (blank) row.copy(reps = null) else parseCount(text)?.let { row.copy(reps = it) }
        LogField.SECONDS -> if (blank) row.copy(durationSec = null) else parseCount(text)?.let { row.copy(durationSec = it) }
        LogField.RPE -> if (blank) row.copy(rpe = null) else parseRpe(text)?.let { row.copy(rpe = it) }
    }
}

/**
 * Normal form of what the user typed ("52,50" -> "52.5"), or null if it would be rejected.
 * The UI overwrites its text box only when the STORED value changed and differs from this
 * (e.g. a suggestion was tapped), so typing "1" on the way to "10" is never disturbed.
 */
fun canonicalFieldText(field: LogField, text: String): String? =
    applyFieldText(SetRow(0), field, text)?.let { fieldText(it, field) }

/** Fill the suggested value into the right field of [row]. */
fun applySuggestionTo(row: SetRow, type: ExerciseType, fieldKg: Double): SetRow = when (type) {
    ExerciseType.WEIGHTED, ExerciseType.BODYWEIGHT_REPS -> row.copy(weightKg = fieldKg)
    ExerciseType.ASSISTED_BODYWEIGHT -> row.copy(weightKg = if (fieldKg == 0.0) 0.0 else -abs(fieldKg))
    ExerciseType.TIME_HELD -> row
}

// ---------------------------------------------------------------- labels

/** "Suggest 52.5 kg", "Suggest +5 kg", "Suggest assist 20 kg". */
fun suggestionText(type: ExerciseType, fieldKg: Double): String = when (type) {
    ExerciseType.ASSISTED_BODYWEIGHT -> "Suggest assist ${formatWeightKg(fieldKg)}"
    ExerciseType.BODYWEIGHT_REPS -> "Suggest +${formatWeightKg(fieldKg)}"
    else -> "Suggest ${formatWeightKg(fieldKg)}"
}

/** "8-12" or "45 s"; null when the row has no target, is a DROP row, or targets are off. */
fun targetLabel(row: SetRow, type: ExerciseType, targetsOn: Boolean): String? {
    if (!targetsOn || row.kind == SetKind.DROP) return null
    val t = formatTarget(row.targetMin, row.targetMax)
    if (t.isEmpty()) return null
    return if (type == ExerciseType.TIME_HELD) "$t s" else t
}

/** The field label with the target appended: "Reps • 8-12". */
fun fieldLabelWithTarget(field: LogField, target: String?): String =
    if (target != null && (field == LogField.REPS || field == LogField.SECONDS)) "${field.label()} • $target" else field.label()

/**
 * "Previous:" text for one earlier row, e.g. "8 reps @ 50 kg", "8 reps @ +5 kg", "45 s @ +10 kg",
 * "10 reps @ assist 20 kg", plus ", RPE 8" when [showRpe]. Null if the earlier row had nothing logged.
 */
fun formatPrevious(type: ExerciseType, row: SetRow, showRpe: Boolean): String? {
    val main = when (type) {
        ExerciseType.TIME_HELD -> row.durationSec?.takeIf { it > 0 }?.let { "$it s" }
        else -> row.reps?.takeIf { it > 0 }?.let { "$it reps" }
    }
    val w = row.weightKg
    val weight = when (type) {
        ExerciseType.WEIGHTED -> w?.takeIf { it != 0.0 }?.let { formatWeightKg(it) }
        ExerciseType.BODYWEIGHT_REPS, ExerciseType.TIME_HELD -> w?.takeIf { it > 0.0 }?.let { "+${formatWeightKg(it)}" }
        ExerciseType.ASSISTED_BODYWEIGHT -> w?.takeIf { it != 0.0 }?.let { "assist ${formatWeightKg(abs(it))}" }
    }
    if (main == null && weight == null) return null
    val core = listOfNotNull(main, weight).joinToString(" @ ")
    val rpe = if (showRpe) row.rpe?.let { ", RPE ${formatWeight(it)}" }.orEmpty() else ""
    return core + rpe
}

/**
 * The earlier row this row is compared with: the row at the SAME POSITION among this exercise's
 * rows of the same kind (DROP vs not DROP) in the previous completed session of the same workout.
 * Both lists are depth-first. Null if there is none.
 */
fun previousRowFor(row: SetRow, current: List<SetRow>, previous: List<SetRow>): SetRow? {
    val ex = row.exerciseId ?: return null
    val drop = row.kind == SetKind.DROP
    fun same(r: SetRow) = r.exerciseId == ex && (r.kind == SetKind.DROP) == drop
    val index = current.filter(::same).indexOfFirst { it.id == row.id }
    if (index < 0) return null
    return previous.filter(::same).getOrNull(index)
}

// ---------------------------------------------------------------- calories

/** Estimated kcal of these rows (grouped per exercise; drop and superset rows included). */
fun rowsKcal(rows: List<SetRow>, meta: (Long) -> ExerciseMeta?, bodyweightKg: Double): Double {
    val works = rows.filter { it.exerciseId != null }.groupBy { it.exerciseId!! }.mapNotNull { (id, list) ->
        meta(id)?.let { ExerciseWork(it.type, it.secondaryMuscleCount, list.map { r -> r.toEntry() }) }
    }
    return CalorieCalculator.sessionKcal(works, bodyweightKg)
}

fun formatKcal(kcal: Double): String = "${kcal.roundToInt()} kcal"

// ---------------------------------------------------------------- suggestions

/**
 * Row id -> weight suggestion, for every non-DROP, non-TIME_HELD row that has a target.
 * Empty when [targetsOn] is false. [history] gives completed-session history per exercise.
 */
fun buildSuggestions(
    rows: List<SetRow>,
    meta: (Long) -> ExerciseMeta?,
    history: (Long) -> List<SessionHistory>,
    workoutId: Long,
    bodyweightKg: Double,
    calculator: E1rmCalculator,
    targetsOn: Boolean
): Map<Long, Suggestion> {
    if (!targetsOn) return emptyMap()
    val engine = SuggestionEngine(calculator)
    val sources = HashMap<Long, SuggestionSource?>()
    val out = HashMap<Long, Suggestion>()
    for (r in rows) {
        val ex = r.exerciseId ?: continue
        if (r.kind == SetKind.DROP || r.targetMin == null) continue
        val m = meta(ex) ?: continue
        if (m.type == ExerciseType.TIME_HELD) continue
        val source = sources.getOrPut(ex) { engine.findSource(m.profile, workoutId, history(ex)) }
        engine.suggest(m.profile, r.targetMin, source, bodyweightKg)?.let { out[r.id] = it }
    }
    return out
}
