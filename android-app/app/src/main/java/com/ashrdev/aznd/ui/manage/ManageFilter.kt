package com.ashrdev.aznd.ui.manage

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.ashrdev.aznd.data.manage.DataApp
import com.ashrdev.aznd.data.manage.isAll
import com.ashrdev.aznd.ui.calendar.dayKeyOf
import com.ashrdev.aznd.ui.calendar.todayDayKey
import com.ashrdev.aznd.ui.common.CustomRangeRow
import com.ashrdev.aznd.ui.common.DateRangeKind
import com.ashrdev.aznd.ui.common.DateRangeState
import com.ashrdev.aznd.ui.common.DayRange
import com.ashrdev.aznd.ui.common.DropdownField
import com.ashrdev.aznd.ui.common.describe
import com.ashrdev.aznd.ui.common.rememberDateRangeState
import com.ashrdev.aznd.ui.components.OutlinedTextField
import java.util.Calendar
import java.util.Locale

// The Manage data filter (top-bar icon). It adds no date logic of its own: every date it produces
// is a universal DayRange from a universal DateRangeState (ui.common), except the "Custom amount"
// option, which only turns "N days/weeks/months/years" into the same kind of DayRange.

enum class SinceUnit(val label: String) {
    DAYS("Days"),
    WEEKS("Weeks"),
    MONTHS("Months"),
    YEARS("Years");

    /** "day" / "days" etc. for sentences. */
    fun noun(n: Int): String = label.lowercase(Locale.ROOT).let { if (n == 1) it.dropLast(1) else it }
}

/**
 * The "Data since" dropdown: the universal presets ([kind] is the DateRangeKind they resolve
 * through) plus Custom, which has no kind because it is typed as an amount and a unit.
 */
enum class SinceChoice(val label: String, val kind: DateRangeKind?) {
    ALL("All time", DateRangeKind.ALL),
    DAYS_7("Last 7 days", DateRangeKind.DAYS_7),
    DAYS_30("Last 30 days", DateRangeKind.DAYS_30),
    DAYS_90("Last 90 days", DateRangeKind.DAYS_90),
    YEAR_1("Last year", DateRangeKind.YEAR_1),
    CUSTOM("Custom", null)
}

/**
 * Everything the filter controls. One instance is shared by the Delete, Export and Import pages.
 *
 *  - [apps]: which data categories are shown. Empty means every category.
 *  - dates (Delete / Export only), either:
 *      "Data since": a preset ([sinceDates]) or, when [sinceCustom], [customAmount] + [customUnit];
 *      or, when [useRange] is ticked, exact From / To days ([rangeDates], CUSTOM kind).
 *
 * Get one with [rememberManageFilterState].
 */
@Stable
class ManageFilterState(
    val sinceDates: DateRangeState,
    val rangeDates: DateRangeState,
    apps: Set<DataApp> = emptySet(),
    useRange: Boolean = false,
    sinceCustom: Boolean = false,
    customAmount: String = "30",
    customUnit: SinceUnit = SinceUnit.DAYS
) {
    var apps by mutableStateOf(apps)
    var useRange by mutableStateOf(useRange)
    var sinceCustom by mutableStateOf(sinceCustom)
    var customAmount by mutableStateOf(customAmount)
    var customUnit by mutableStateOf(customUnit)

    /** What the "Data since" dropdown currently shows. */
    val sinceChoice: SinceChoice
        get() = if (sinceCustom) SinceChoice.CUSTOM
        else SinceChoice.entries.firstOrNull { it.kind == sinceDates.kind } ?: SinceChoice.ALL

    fun chooseSince(choice: SinceChoice) {
        val kind = choice.kind
        if (kind == null) {
            sinceCustom = true
        } else {
            sinceCustom = false
            sinceDates.kind = kind
        }
    }

    private val customSince: Pair<Int, SinceUnit>?
        get() = customAmount.toIntOrNull()?.takeIf { it > 0 }?.let { it to customUnit }

    /** True while Custom is picked but no valid number has been typed. */
    val incomplete: Boolean get() = !useRange && sinceCustom && customSince == null

    /** The date range to apply, or null while the filter is [incomplete]. */
    val range: DayRange?
        get() = when {
            useRange -> rangeDates.range
            sinceCustom -> customSince?.let { (n, unit) -> DayRange(sinceKey(n, unit), todayDayKey()) }
            else -> sinceDates.range
        }

    /** True when anything narrows what the pages show (drives the dot on the top-bar icon). */
    val isActive: Boolean get() = apps.isNotEmpty() || range?.isAll != true

    fun reset() {
        apps = emptySet()
        useRange = false
        sinceCustom = false
        sinceDates.kind = DateRangeKind.ALL
        customAmount = "30"
        customUnit = SinceUnit.DAYS
    }

    /** Short period text: "All time", "Last 30 days", "1 Sep 2026 – 17 Oct 2026". */
    fun periodLabel(): String = when {
        incomplete -> "Enter an amount"
        useRange -> rangeDates.describe()
        sinceCustom -> customSince!!.let { (n, unit) -> "Last $n ${unit.noun(n)}" }
        else -> sinceChoice.label
    }

    /** Period text for confirmations: a "since" choice also spells out the days it covers. */
    fun describePeriod(): String {
        val r = range ?: return periodLabel()
        return if (useRange || r.isAll) r.describe() else "${periodLabel()} (${r.describe()})"
    }

    /** One-line summary for the top-bar subtitle, or null when nothing is filtered. */
    fun summary(includeDates: Boolean): String? {
        val parts = buildList {
            if (apps.isNotEmpty()) add(apps.sortedBy { it.ordinal }.joinToString(", ") { it.label })
            if (includeDates && (incomplete || range?.isAll != true)) add(periodLabel())
        }
        return parts.joinToString(" · ").ifEmpty { null }
    }

    companion object {
        fun saver(sinceDates: DateRangeState, rangeDates: DateRangeState): Saver<ManageFilterState, Any> =
            listSaver<ManageFilterState, Any>(
                save = { s ->
                    listOf(
                        s.apps.joinToString(",") { it.id },
                        s.useRange,
                        s.sinceCustom,
                        s.customAmount,
                        s.customUnit.name
                    )
                },
                restore = { v ->
                    ManageFilterState(
                        sinceDates = sinceDates,
                        rangeDates = rangeDates,
                        apps = (v[0] as String).split(",").mapNotNull { DataApp.fromId(it) }.toSet(),
                        useRange = v[1] as Boolean,
                        sinceCustom = v[2] as Boolean,
                        customAmount = v[3] as String,
                        customUnit = SinceUnit.entries.firstOrNull { u -> u.name == v[4] as String } ?: SinceUnit.DAYS
                    )
                }
            )
    }
}

/** Survives rotation and navigating away. Starts on [initialApp]'s category, if there is one. */
@Composable
fun rememberManageFilterState(initialApp: DataApp?): ManageFilterState {
    val sinceDates = rememberDateRangeState(DateRangeKind.ALL)
    val rangeDates = rememberDateRangeState(DateRangeKind.CUSTOM)
    return rememberSaveable(sinceDates, rangeDates, saver = ManageFilterState.saver(sinceDates, rangeDates)) {
        ManageFilterState(sinceDates, rangeDates, apps = setOfNotNull(initialApp))
    }
}

/** First day of "the last [amount] [unit]s", counted the way the presets are (today included). */
private fun sinceKey(amount: Int, unit: SinceUnit): String {
    val c = Calendar.getInstance()
    when (unit) {
        SinceUnit.DAYS -> c.add(Calendar.DAY_OF_YEAR, -amount)
        SinceUnit.WEEKS -> c.add(Calendar.DAY_OF_YEAR, -7 * amount)
        SinceUnit.MONTHS -> c.add(Calendar.MONTH, -amount)
        SinceUnit.YEARS -> c.add(Calendar.YEAR, -amount)
    }
    c.add(Calendar.DAY_OF_YEAR, 1)
    // Typing 999 years must not run off the calendar.
    return if (c.get(Calendar.YEAR) < 1900) "1900-01-01" else dayKeyOf(c.timeInMillis)
}

// ---------------------------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------------------------

// Every control in the date section shares this height, so the dropdowns, the number box and the
// From / To buttons line up.
private val ControlHeight = 56.dp

/**
 * The panel opened by the top-bar filter icon. Changes apply live to the page behind it.
 * [page] decides whether the date section shows (Import has none: the file decides what comes in).
 *
 * The body is a fixed height and scrolls, so the dialog never jumps in size when options appear
 * or disappear; the page behind it is blurred (see [BlurBehindDialog]).
 */
@Composable
fun ManageFilterDialog(
    state: ManageFilterState,
    apps: List<DataApp>,
    page: ManagePage,
    onDismiss: () -> Unit
) {
    val bodyHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).coerceAtMost(440f).dp
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(0.94f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Filter") },
        text = {
            BlurBehindDialog()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bodyHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CategorySection(state, apps)
                if (page == ManagePage.IMPORT) {
                    Hint("Dates don't apply to Import: the file decides what comes in.")
                } else {
                    DateSection(state, page)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = state::reset, enabled = state.isActive) { Text("Reset") } }
    )
}

@Composable
private fun CategorySection(state: ManageFilterState, apps: List<DataApp>) {
    Column {
        Text("Data categories", style = MaterialTheme.typography.titleMedium)
        CheckRow(
            label = "All categories",
            checked = state.apps.isEmpty(),
            onToggle = { state.apps = emptySet() }
        )
        apps.forEach { app ->
            CheckRow(
                label = app.label,
                checked = app in state.apps,
                onToggle = { state.apps = if (app in state.apps) state.apps - app else state.apps + app }
            )
        }
    }
}

@Composable
private fun DateSection(state: ManageFilterState, page: ManagePage) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Date", style = MaterialTheme.typography.titleMedium)

        if (state.useRange) {
            CustomRangeRow(state.rangeDates, buttonModifier = Modifier.height(ControlHeight), stacked = true)
        } else {
            // Custom splits the line in half: the option on the left, its unit on the right.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField(
                    label = "",
                    valueText = state.sinceChoice.label,
                    options = SinceChoice.entries,
                    optionText = { it.label },
                    onSelect = state::chooseSince,
                    modifier = Modifier.weight(1f),
                    buttonModifier = Modifier.height(ControlHeight)
                )
                if (state.sinceCustom) {
                    DropdownField(
                        label = "",
                        valueText = state.customUnit.label,
                        options = SinceUnit.entries,
                        optionText = { it.label },
                        onSelect = { state.customUnit = it },
                        modifier = Modifier.weight(1f),
                        buttonModifier = Modifier.height(ControlHeight)
                    )
                }
            }
            if (state.sinceCustom) {
                OutlinedTextField(
                    value = state.customAmount,
                    onValueChange = { state.customAmount = it.filter(Char::isDigit).take(3) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().height(ControlHeight)
                )
            }
        }

        if (state.incomplete) {
            Text("Enter a number above 0", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        } else {
            state.range?.let {
                // With no label on the number box, "Last 30 days (...)" tells what the number means.
                val caption = if (state.sinceCustom) state.describePeriod() else it.describe()
                Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        CheckRow(
            label = "Time range",
            supporting = "Pick exact From and To dates instead",
            checked = state.useRange,
            onToggle = { state.useRange = !state.useRange }
        )

        Hint(
            if (page == ManagePage.DELETE) "Data without dates is switched off while a date filter is on."
            else "Data without dates is always exported in full."
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    supporting: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) Hint(supporting)
        }
    }
}

/**
 * Blurs whatever is behind the dialog this is placed in. It asks the window compositor to do it
 * (Android 12+, when the device allows it), so it costs nothing in Compose: no re-drawing the page,
 * no extra layers. Where blur isn't available the normal dim is left exactly as it was.
 */
@Composable
private fun BlurBehindDialog(radius: Dp = 10.dp) {
    val view = LocalView.current
    val radiusPx = with(LocalDensity.current) { radius.roundToPx() }
    SideEffect {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@SideEffect
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        val windowManager = view.context.getSystemService(WindowManager::class.java) ?: return@SideEffect
        if (!windowManager.isCrossWindowBlurEnabled) return@SideEffect
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.also { it.blurBehindRadius = radiusPx }
        window.setDimAmount(0.25f)
    }
}
