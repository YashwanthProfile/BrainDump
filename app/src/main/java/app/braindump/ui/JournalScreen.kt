@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.braindump.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.braindump.data.Repository
import app.braindump.data.Dates
import app.braindump.data.ThemeMode
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val monthName = DateTimeFormatter.ofPattern("MMMM")
private val weekday = DateTimeFormatter.ofPattern("EEE")

/** The journal's front page: one year, each month drawn as a little field of squares. */
@Composable
fun JournalScreen(repo: Repository, onOpenMonth: (String) -> Unit, onSearch: () -> Unit = {}, onThreads: () -> Unit = {}, onOpenThread: (String) -> Unit = {}) {
    val threads by repo.threads().collectAsStateWithLifecycle(emptyList())
    val threadNotes by repo.allThreadNotes().collectAsStateWithLifecycle(emptyList())
    val due = remember(threads, threadNotes) { dueThreads(threads.filterNot { it.hidden }, threadNotes) }
    val counts by repo.entryCounts("0000-01-01", "9999-12-31").collectAsStateWithLifecycle(emptyList())
    val days by repo.daysBetween("0000-01-01", "9999-12-31").collectAsStateWithLifecycle(emptyList())
    val byDate = remember(counts, days) {
        counts.associate { it.date to it.n } +
            days.filter { (it.notes.isNotBlank() || it.title.isNotBlank()) && counts.none { c -> c.date == it.date } }.associate { it.date to 1 }
    }

    var showSettings by remember { mutableStateOf(false) }
    val settings by repo.settings.settings.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var exporting by remember { mutableStateOf(false) }
    if (exporting) ExportDialog(onDismiss = { exporting = false })
    if (showSettings && settings != null) {
        val dark = isDark(settings!!.theme)
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Settings") },
            text = {
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Dark mode", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = dark, onCheckedChange = { on ->
                            scope.launch { repo.settings.update(settings!!.copy(theme = if (on) ThemeMode.DARK else ThemeMode.LIGHT)) }
                        })
                    }
                    // App lock with the phone's own fingerprint / face / PIN.
                    // Popups wrap the context, so walk back to the activity.
                    val activity = androidx.compose.ui.platform.LocalContext.current.findActivity()
                    val canLock = activity != null && AppLock.available(activity)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("App lock", style = MaterialTheme.typography.bodyLarge)
                            if (!canLock) Text("(set a screen lock on your phone first)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = settings!!.appLock, enabled = canLock, onCheckedChange = { on ->
                            // Confirm it's you before turning the lock on or off.
                            AppLock.authenticate(activity!!) { ok ->
                                if (ok) scope.launch { repo.settings.update(settings!!.copy(appLock = on)) }
                            }
                        })
                    }
                    // Text size: three steps; pinching on a day page fine-tunes it.
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Text size", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Row(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(3.dp)) {
                            listOf(0.9f to "S", 1f to "M", 1.15f to "L").forEach { (k, l) ->
                                val on = kotlin.math.abs(settings!!.textScale - k) < 0.04f
                                Box(
                                    Modifier.size(width = 40.dp, height = 32.dp).clip(CircleShape)
                                        .background(if (on) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent)
                                        .clickable { scope.launch { repo.settings.update(settings!!.copy(textScale = k)) } },
                                    contentAlignment = Alignment.Center,
                                ) { Text(l, style = MaterialTheme.typography.labelLarge, color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                    Text("(or pinch on a day to zoom)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Text("REMINDERS", style = MarkText)
                    Gap(4)
                    // One row, one value: tap to choose from a short menu.
                    var open by remember { mutableStateOf(false) }
                    val fmt = { m: Int -> if (m < 60) "$m min" else if (m % 60 == 0) "${m / 60} hr" else "${m / 60}.${m % 60 * 10 / 60} hr" }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Remind me every", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { open = true }) { Text(fmt(settings!!.intervalMinutes)) }
                            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                listOf(30, 60, 90, 120, 180, 240).forEach { m ->
                                    DropdownMenuItem(text = { Text(fmt(m)) }, onClick = {
                                        open = false
                                        scope.launch {
                                            val s2 = settings!!.copy(intervalMinutes = m)
                                            repo.settings.update(s2)
                                            app.braindump.notify.Alarms.scheduleAll(ctx, s2)
                                        }
                                    })
                                }
                            }
                        }
                    }
                    Text(
                        "(counted from your last log)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.offset(y = (-6).dp),
                    )
                    // Waking hours: reminders only come between these two times.
                    var pickStart by remember { mutableStateOf(false) }
                    var pickEnd by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Between", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { pickStart = true }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(Dates.formatMinute(settings!!.startMinute)) }
                        Text("–", style = QuietText)
                        TextButton(onClick = { pickEnd = true }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(Dates.formatMinute(settings!!.endMinute)) }
                    }
                    fun saveHours(s2: app.braindump.data.Settings) = scope.launch {
                        repo.settings.update(s2); app.braindump.notify.Alarms.scheduleAll(ctx, s2)
                    }
                    if (pickStart) TimeDialog(settings!!.startMinute, onDismiss = { pickStart = false }) { m ->
                        pickStart = false; if (m < settings!!.endMinute) saveHours(settings!!.copy(startMinute = m))
                    }
                    if (pickEnd) TimeDialog(settings!!.endMinute, onDismiss = { pickEnd = false }) { m ->
                        pickEnd = false; if (m > settings!!.startMinute) saveHours(settings!!.copy(endMinute = m))
                    }
                    HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Text("YOUR DATA", style = MarkText)
                    Gap(6)
                    Text("Kept privately inside BrainDump on this phone. Nothing leaves unless you export it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Export", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { exporting = true }) { Text("Choose…") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } },
        )
    }
    // Only months you actually wrote in, newest first.
    val months = remember(byDate) { byDate.keys.map { YearMonth.parse(it.take(7)) }.distinct().sortedDescending() }

    LazyColumn {
        item {
            ScreenHeader("BrainDump", action = {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            })
            TopTabs(threads = false, onDays = {}, onThreads = onThreads)
            // One quiet line when a thread is due for another look.
            due.firstOrNull()?.let { t ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenThread(t.id) }.padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Hues.Journal))
                    Spacer(Modifier.width(10.dp))
                    Text("Time to revisit: ", style = QuietText)
                    Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
        if (months.isEmpty()) item { EmptyNote("Nothing here yet. Tap Today to start.") }
        items(months, key = { it.toString() }) { m ->
            val prefix = m.toString()
            val days = byDate.count { it.key.startsWith(prefix) }
            SoftCard(onClick = { onOpenMonth(prefix) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.format(monthName), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "${m.year} · $days ${if (days == 1) "day" else "days"}",
                            style = QuietText,
                        )
                    }
                    PixelMonth(m, byDate, Hues.Journal)
                }
            }
        }
        item { Gap(24) }
    }
}

/** One month as a column of day tiles, newest first. Only days with something in them appear. */
@Composable
fun MonthScreen(repo: Repository, ym: String, onBack: () -> Unit, onOpenDay: (String) -> Unit) {
    val month = remember(ym) { YearMonth.parse(ym) }
    val from = month.atDay(1).toString()
    val to = month.atEndOfMonth().toString()
    val entries by repo.entriesBetween(from, to).collectAsStateWithLifecycle(emptyList())
    val photos by repo.dayPhotosBetween(from, to).collectAsStateWithLifecycle(emptyList())
    val notes by repo.daysBetween(from, to).collectAsStateWithLifecycle(emptyList())

    val byDay = entries.groupBy { it.date }
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        val init = if (month == YearMonth.now()) LocalDate.now() else month.atEndOfMonth()
        PastDayPicker(init, onDismiss = { picking = false }) { picking = false; onOpenDay(it) }
    }
    val dates = (byDay.keys + photos.map { it.ownerId } + notes.filter { it.notes.isNotBlank() || it.title.isNotBlank() }.map { it.date })
        .distinct().sortedDescending()

    LazyColumn {
        item {
            ScreenHeader(month.format(monthName), "${month.year} · ${dates.size} ${if (dates.size == 1) "day" else "days"}", onBack = onBack, action = {
                // Write about any past day, even one with nothing in it yet.
                if (!month.isAfter(YearMonth.now())) {
                    Box(
                        Modifier.padding(end = 8.dp).size(40.dp).clip(CircleShape).background(Hues.Journal.copy(alpha = 0.16f)).clickable { picking = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Add, contentDescription = "Add a day", tint = Hues.Journal) }
                }
            })
        }
        if (dates.isEmpty()) item { EmptyNote("Nothing written this month.") }
        items(dates, key = { it }) { date ->
            val d = LocalDate.parse(date)
            val n = byDay[date].orEmpty().size
            val title = notes.firstOrNull { it.date == date }?.title.orEmpty()
            SoftCard(onClick = { onOpenDay(date) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
                        Text("${d.dayOfMonth}", style = MaterialTheme.typography.displaySmall, color = Hues.Journal)
                        Text(d.format(weekday), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        if (title.isNotBlank()) {
                            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        } else {
                            Text("Untitled", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                        if (n > 0) Text("$n ${if (n == 1) "moment" else "moments"}", style = QuietText)
                    }
                }
            }
        }
        item { Gap(24) }
    }
}

/** A calendar for opening any day up to today. Future days can't be picked. */
@Composable
fun PastDayPicker(initial: LocalDate, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val today = LocalDate.now()
    val utc = java.time.ZoneOffset.UTC
    val todayMillis = today.atStartOfDay(utc).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = minOf(initial, today).atStartOfDay(utc).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(java.time.Instant.ofEpochMilli(it).atZone(utc).toLocalDate().toString()) }
            }) { Text("Open") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state, showModeToggle = false) }
}

internal tailrec fun android.content.Context.findActivity(): androidx.fragment.app.FragmentActivity? = when (this) {
    is androidx.fragment.app.FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
