package com.ashrdev.aznd.ui.streaks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ashrdev.aznd.data.routines.ReminderEntity
import com.ashrdev.aznd.data.streaks.DEFAULT_MILESTONES
import com.ashrdev.aznd.data.streaks.StreakEntity
import com.ashrdev.aznd.data.streaks.defaultMilestonesFor
import com.ashrdev.aznd.data.streaks.formatMilestones
import com.ashrdev.aznd.data.streaks.parseMilestones
import com.ashrdev.aznd.domain.streaks.ProgressUnit
import com.ashrdev.aznd.domain.streaks.TrackMode
import com.ashrdev.aznd.domain.streaks.Weekdays
import com.ashrdev.aznd.domain.streaks.dateKey
import com.ashrdev.aznd.domain.streaks.displayDate
import com.ashrdev.aznd.domain.streaks.parseKey
import com.ashrdev.aznd.domain.streaks.todayKey
import com.ashrdev.aznd.reminders.rememberNotificationPermission
import com.ashrdev.aznd.ui.common.rememberImagePicker
import com.ashrdev.aznd.ui.components.Card
import com.ashrdev.aznd.ui.components.OutlinedButton
import com.ashrdev.aznd.ui.components.OutlinedTextField
import java.util.Calendar
import java.util.Locale

const val STREAK_QUOTE_LIMIT = 80
const val DEFAULT_REMINDER_MINUTE = 20 * 60

@Stable
class ReminderDraft(minuteOfDay: Int = DEFAULT_REMINDER_MINUTE, weekdays: Int = 0, message: String = "") {
    var minuteOfDay by mutableIntStateOf(minuteOfDay)
    var weekdays by mutableIntStateOf(weekdays)
    var message by mutableStateOf(message)

    fun toEntity(): ReminderEntity = ReminderEntity(ownerType = "", ownerId = 0, minuteOfDay = minuteOfDay, weekdays = weekdays, message = message.trim())

    companion object {
        fun of(e: ReminderEntity) = ReminderDraft(e.minuteOfDay, e.weekdays, e.message)
    }
}

@Stable
class StreakDraft {
    var original: StreakEntity? = null
    var name by mutableStateOf("")
    var quote by mutableStateOf("")
    var startDate by mutableStateOf(todayKey())
    var photoUri by mutableStateOf<String?>(null)
    var bannerUri by mutableStateOf<String?>(null)
    var trackMode by mutableStateOf(TrackMode.AUTO)
    var milestoneAlerts by mutableStateOf(false)
    val milestones = mutableStateListOf<Int>().apply { addAll(DEFAULT_MILESTONES) }
    val reminders = mutableStateListOf<ReminderDraft>()

    val canSave: Boolean get() = name.isNotBlank()

    fun load(streak: StreakEntity, saved: List<ReminderEntity>) {
        original = streak
        name = streak.name
        quote = streak.quote.orEmpty()
        startDate = streak.startDate
        photoUri = streak.photoUri
        bannerUri = streak.bannerUri
        trackMode = TrackMode.fromId(streak.trackMode)
        milestoneAlerts = streak.milestoneAlerts
        milestones.clear()
        milestones.addAll(parseMilestones(streak.milestones))
        reminders.clear()
        reminders.addAll(saved.map { ReminderDraft.of(it) })
    }

    fun toEntity(): StreakEntity = (original ?: StreakEntity(name = "", startDate = startDate)).copy(
        name = name.trim(),
        startDate = startDate,
        photoUri = photoUri,
        bannerUri = bannerUri,
        quote = quote.trim().takeIf { it.isNotEmpty() },
        milestones = formatMilestones(milestones.toList()),
        trackMode = trackMode.id,
        milestoneAlerts = milestoneAlerts
    )
}

@Composable
fun rememberStreakDraft(key: Any? = null): StreakDraft = remember(key) { StreakDraft() }

@Composable
fun EditorColumn(bottomPadding: Dp, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) { content() }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun ImagesPage(
    bannerUri: String?,
    photoUri: String?,
    onBanner: (String?) -> Unit,
    onLogo: (String?) -> Unit,
    bottomPadding: Dp
) {
    val logoPicker = rememberImagePicker(
        aspect = STREAK_LOGO_ASPECT to 1f,
        outputWidth = STREAK_LOGO_PX,
        outputHeight = STREAK_LOGO_PX,
        onImagePicked = { onLogo(it.toString()) }
    )
    val bannerPicker = rememberImagePicker(
        aspect = 16f to 9f,
        outputWidth = STREAK_BANNER_WIDTH_PX,
        outputHeight = STREAK_BANNER_HEIGHT_PX,
        onImagePicked = { onBanner(it.toString()) }
    )
    EditorColumn(bottomPadding) {
        SectionTitle("Banner")
        StreakBannerImage(bannerUri = bannerUri, onClick = { bannerPicker.launch() })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { bannerPicker.launch() }) { Text(if (bannerUri != null) "Replace banner" else "Add banner") }
            if (bannerUri != null) TextButton(onClick = { onBanner(null) }) { Text("Remove") }
        }
        SectionTitle("Logo")
        Row(verticalAlignment = Alignment.CenterVertically) {
            StreakLogo(photoUri = photoUri, size = 80.dp, modifier = Modifier.clickable { logoPicker.launch() })
            Spacer(Modifier.width(16.dp))
            Column {
                OutlinedButton(onClick = { logoPicker.launch() }) { Text(if (photoUri != null) "Replace logo" else "Add logo") }
                if (photoUri != null) TextButton(onClick = { onLogo(null) }) { Text("Remove logo") }
            }
        }
        Hint("Both are optional. The banner sits at the top of the streak's page, the logo next to its name.")
    }
}

@Composable
fun DateButton(label: String, date: String, onDate: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val cal = parseKey(date)
            DatePickerDialog(
                context,
                { _, year, month, day -> onDate(dateKey(year, month, day)) },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        },
        modifier = modifier.fillMaxWidth()
    ) { Text("$label: ${displayDate(date)}") }
}

@Composable
fun TrackModeOptions(selected: TrackMode, onSelect: (TrackMode) -> Unit) {
    Column {
        for (mode in TrackMode.entries) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = mode == selected, onClick = { onSelect(mode) })
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = mode == selected, onClick = { onSelect(mode) })
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                    Hint(mode.blurb)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MilestonesEditor(milestones: SnapshotStateList<Int>, enabled: Boolean = true, unit: ProgressUnit = ProgressUnit.DAYS) {
    var input by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SectionTitle("Milestones (${unit.plural})")
        TextButton(
            enabled = enabled,
            onClick = {
                milestones.clear()
                milestones.addAll(defaultMilestonesFor(unit))
            }
        ) { Text("Reset to default") }
    }
    if (milestones.isEmpty()) Hint("No milestones. Add one below.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        milestones.forEach { days ->
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.clickable(enabled = enabled) { milestones.remove(days) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                ) {
                    Text("$days", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Close, contentDescription = "Remove $days", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) input = it },
            label = { Text("Add milestone") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            onClick = {
                input.toIntOrNull()?.let { d -> if (d > 0 && d !in milestones) { milestones.add(d); milestones.sort() } }
                input = ""
            },
            enabled = enabled && (input.toIntOrNull() ?: 0) > 0
        ) { Text("Add") }
    }
}

fun formatMinute(context: android.content.Context, minuteOfDay: Int): String {
    val h = minuteOfDay / 60
    val m = minuteOfDay % 60
    if (DateFormat.is24HourFormat(context)) return String.format(Locale.getDefault(), "%02d:%02d", h, m)
    val h12 = if (h % 12 == 0) 12 else h % 12
    return String.format(Locale.getDefault(), "%d:%02d %s", h12, m, if (h < 12) "AM" else "PM")
}

@Composable
fun NotificationsEditor(
    milestoneAlerts: Boolean,
    onMilestoneAlerts: (Boolean) -> Unit,
    alertsAvailable: Boolean,
    reminders: SnapshotStateList<ReminderDraft>,
    reminderHint: String
) {
    val ask = rememberNotificationPermission()
    SectionTitle("Notifications")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("Milestone alerts", style = MaterialTheme.typography.bodyLarge)
            Hint(if (alertsAvailable) "A notification on the morning a milestone is reached" else "Needs streak tracking")
        }
        Switch(
            checked = milestoneAlerts && alertsAvailable,
            enabled = alertsAvailable,
            onCheckedChange = {
                onMilestoneAlerts(it)
                if (it) ask()
            }
        )
    }
    reminders.forEachIndexed { index, r ->
        ReminderRow(r, onRemove = { reminders.removeAt(index) })
    }
    OutlinedButton(
        onClick = {
            reminders.add(ReminderDraft())
            ask()
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Add reminder") }
    Hint(reminderHint)
}

@Composable
private fun ReminderRow(r: ReminderDraft, onRemove: () -> Unit) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    TimePickerDialog(
                        context,
                        { _, h, m -> r.minuteOfDay = h * 60 + m },
                        r.minuteOfDay / 60,
                        r.minuteOfDay % 60,
                        DateFormat.is24HourFormat(context)
                    ).show()
                }) { Text(formatMinute(context, r.minuteOfDay)) }
                Spacer(Modifier.width(8.dp))
                Text(Weekdays.label(r.weekdays), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Remove reminder") }
            }
            WeekdayPicker(mask = r.weekdays, onChange = { r.weekdays = it }, emptyMeansAll = true)
            OutlinedTextField(
                value = r.message,
                onValueChange = { if (it.length <= 80) r.message = it },
                label = { Text("Message (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun WeekdayPicker(mask: Int, onChange: (Int) -> Unit, emptyMeansAll: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Weekdays.SHORT.forEachIndexed { i, label ->
            val on = (emptyMeansAll && mask == 0) || mask and Weekdays.bit(i) != 0
            FilterChip(
                selected = on,
                onClick = {
                    val base = if (emptyMeansAll && mask == 0) Weekdays.ALL else mask
                    val next = base xor Weekdays.bit(i)
                    if (next != 0) onChange(if (emptyMeansAll && next == Weekdays.ALL) 0 else next)
                },
                label = { Text(label) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
