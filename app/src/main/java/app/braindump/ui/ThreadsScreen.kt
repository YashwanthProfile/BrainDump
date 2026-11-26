@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.braindump.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.braindump.data.LifeThread
import app.braindump.data.Mood
import app.braindump.data.Repository
import app.braindump.data.ThreadNote
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val noteDate = DateTimeFormatter.ofPattern("d MMM yyyy")
private fun fmt(at: Long) = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate().format(noteDate)

fun moodColor(m: String): Color = when (m) {
    Mood.GLAD -> Color(0xFF6BAF8D)
    Mood.UNSURE -> Color(0xFFC9A14A)
    Mood.REGRET -> Color(0xFFC9706B)
    else -> Color.Transparent
}
fun moodLabel(m: String) = when (m) { Mood.GLAD -> "Glad"; Mood.UNSURE -> "Unsure"; Mood.REGRET -> "Regret"; else -> "" }

/** The "Days · Threads" switch under the app title. Plain words, the current one underlined. */
@Composable
fun TopTabs(threads: Boolean, onDays: () -> Unit, onThreads: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 10.dp).fillMaxWidth()
            .clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
    ) {
        Tab("Journal", !threads, onDays)
        Tab("Threads", threads, onThreads)
    }
}

@Composable
private fun RowScope.Tab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(enabled = !selected, onClick = onClick).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Threads due for a revisit: no note for longer than their revisit interval. */
fun dueThreads(threads: List<LifeThread>, notes: List<ThreadNote>): List<LifeThread> {
    val now = System.currentTimeMillis()
    return threads.filter { t ->
        if (t.revisitDays <= 0) return@filter false
        val last = notes.filter { it.threadId == t.id }.maxOfOrNull { it.at } ?: t.createdAt
        now - last > t.revisitDays * 86_400_000L
    }
}

private fun ago(at: Long): String {
    val d = ChronoUnit.DAYS.between(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate(), java.time.LocalDate.now())
    return when {
        d <= 0 -> "today"
        d == 1L -> "yesterday"
        d < 30 -> "$d days ago"
        d < 365 -> "${d / 30} ${if (d / 30 == 1L) "month" else "months"} ago"
        else -> "${d / 365} ${if (d / 365 == 1L) "year" else "years"} ago"
    }
}
private val monthYear = DateTimeFormatter.ofPattern("MMM yyyy")
private fun since(at: Long) = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate().format(monthYear).uppercase()

/**
 * Hidden threads open only after fingerprint, face or PIN, every time, even with the app already unlocked.
 * They lock again when you go back to the main list, or after the app has been away for a while.
 */
object HiddenLock {
    var unlocked by mutableStateOf(false)

    fun unlock(activity: androidx.fragment.app.FragmentActivity?, then: () -> Unit) {
        if (activity == null) return
        AppLock.authenticate(activity, "Unlock hidden threads") { ok -> if (ok) { unlocked = true; then() } }
    }
}

/** Stands in for a hidden thread's content until you unlock. Asks once on arrival; a tap asks again. */
@Composable
fun HiddenGate(onBack: () -> Unit, content: @Composable () -> Unit) {
    if (HiddenLock.unlocked) { content(); return }
    val activity = androidx.compose.ui.platform.LocalContext.current.findActivity()
    LaunchedEffect(Unit) { HiddenLock.unlock(activity) {} }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Hidden", onBack = onBack)
        Column(Modifier.fillMaxWidth().padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
            Gap(16)
            TextButton(onClick = { HiddenLock.unlock(activity) {} }) { Text("Unlock", color = Hues.Journal) }
        }
    }
}

/** All threads as a quiet index: thin lines, no cards. With [hiddenOnly], the locked page of hidden threads. */
@Composable
fun ThreadsScreen(
    repo: Repository, onDays: () -> Unit, onOpen: (String) -> Unit, onSearch: () -> Unit, startSelecting: Boolean = false,
    hiddenOnly: Boolean = false, onHidden: () -> Unit = {}, onBack: () -> Unit = {},
) {
    if (hiddenOnly && !HiddenLock.unlocked) { HiddenGate(onBack) {}; return }
    // Back on the main list, hidden threads lock again.
    if (!hiddenOnly) LaunchedEffect(Unit) { HiddenLock.unlocked = false }
    val activity = androidx.compose.ui.platform.LocalContext.current.findActivity()
    val everything by repo.threads().collectAsStateWithLifecycle(emptyList())
    val threads = everything.filter { it.hidden == hiddenOnly }
    val hiddenCount = everything.count { it.hidden }
    var noScreenLock by remember { mutableStateOf(false) }
    val notes by repo.allThreadNotes().collectAsStateWithLifecycle(emptyList())
    var creating by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf("recent") }
    var selecting by rememberSaveable { mutableStateOf(startSelecting) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var deleting by remember { mutableStateOf<List<LifeThread>>(emptyList()) }
    var revisiting by remember { mutableStateOf<LifeThread?>(null) }
    BackHandler(enabled = selecting) { selecting = false; selected = emptySet() }
    val scope = rememberCoroutineScope()
    val lastAt = { t: LifeThread -> notes.filter { it.threadId == t.id }.maxOfOrNull { it.at } ?: t.createdAt }
    val sorted = when (sort) {
        "oldest" -> threads.sortedBy { it.createdAt }
        else -> threads.sortedByDescending { lastAt(it) }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
            item {
                if (hiddenOnly) {
                    ScreenHeader("Hidden", onBack = onBack)
                    Text("Only you can see these. They lock again when you leave.", style = QuietText, modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp))
                } else {
                    ScreenHeader("BrainDump", action = {
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Rounded.Search, "Search threads", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    })
                    TopTabs(threads = true, onDays = onDays, onThreads = {})
                }
            }
            if (threads.isEmpty() && hiddenOnly) item {
                Text("Nothing hidden.", style = QuietText, modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp))
            } else if (threads.isEmpty()) item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                    Text("Decisions, questions, beliefs.", style = ProseText)
                    Gap(6)
                    Text("Write down why you chose something. Come back later and add how you see it now.", style = QuietText)
                }
            } else item {
                val quietLabel = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                @Composable fun Pick(l: String, color: Color, onClick: () -> Unit) = Text(
                    l, style = MaterialTheme.typography.labelLarge, color = color,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selecting) {
                        Text(if (selected.isEmpty()) "Tap threads to select" else "${selected.size} selected", style = QuietText, modifier = Modifier.padding(start = 8.dp).weight(1f))
                        Pick("Cancel", quietLabel) { selecting = false; selected = emptySet() }
                        Pick("Delete", if (selected.isEmpty()) quietLabel else MaterialTheme.colorScheme.error) {
                            if (selected.isNotEmpty()) deleting = threads.filter { it.id in selected }
                        }
                    } else {
                        Pick("Select", quietLabel) { selecting = true }
                        Spacer(Modifier.weight(1f))
                        listOf("recent" to "Recent", "oldest" to "Oldest").forEach { (k, l) ->
                            Pick(l, if (sort == k) Hues.Journal else quietLabel) { sort = k }
                        }
                    }
                }
            }
            listOf(true to "BIG DECISIONS", false to "SMALL DECISIONS").forEach { (big, label) ->
                val group = sorted.filter { it.major == big }
                if (group.isNotEmpty()) {
                    item(key = label) {
                        if (!big && sorted.any { it.major }) {
                            Box(Modifier.padding(horizontal = 24.dp).padding(top = 18.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)))
                        }
                        Text(label, style = MarkText, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 4.dp))
                    }
                    itemsIndexed(group, key = { _, t -> t.id }) { i, t ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        val isSel = t.id in selected
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (selecting) selected = if (isSel) selected - t.id else selected + t.id else onOpen(t.id)
                            }.padding(start = 24.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selecting) {
                                Icon(
                                    if (isSel) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                                    tint = if (isSel) Hues.Journal else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(end = 16.dp).size(22.dp),
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(t.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Gap(4)
                                Text("Since ${since(t.createdAt).lowercase().replaceFirstChar { it.uppercase() }}", style = QuietText)
                            }
                            if (!selecting) Box {
                                var menu by remember { mutableStateOf(false) }
                                IconButton(onClick = { menu = true }) {
                                    Icon(Icons.Rounded.MoreVert, "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                }
                                DropdownMenu(menu, { menu = false }) {
                                    if (hiddenOnly) {
                                        DropdownMenuItem(text = { Text("Unhide") }, onClick = { menu = false; scope.launch { repo.saveThread(t.copy(hidden = false)) } })
                                        DropdownMenuItem(text = { Text("Revisit reminder") }, onClick = { menu = false; revisiting = t })
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text(if (t.major) "Move to small decisions" else "Move to big decisions") },
                                            onClick = { menu = false; scope.launch { repo.saveThread(t.copy(major = !t.major)) } },
                                        )
                                        DropdownMenuItem(text = { Text("Revisit reminder") }, onClick = { menu = false; revisiting = t })
                                        DropdownMenuItem(text = { Text("Hide thread") }, onClick = {
                                            menu = false
                                            if (activity != null && AppLock.available(activity)) scope.launch { repo.saveThread(t.copy(hidden = true)) }
                                            else noScreenLock = true
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // A faint way in to hidden threads, only when there are some.
            if (!hiddenOnly && !selecting && hiddenCount > 0) item(key = "hidden-link") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 36.dp), horizontalArrangement = Arrangement.Center,
                ) {
                    Row(
                        Modifier.clip(CircleShape).clickable { HiddenLock.unlock(activity, onHidden) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val faint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        Icon(Icons.Rounded.Lock, null, tint = faint, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Hidden", style = MaterialTheme.typography.labelLarge, color = faint)
                    }
                }
            }
        }
        if (!selecting && !hiddenOnly) ExtendedFloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
            containerColor = Hues.Journal, contentColor = Color.White,
            icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New thread") },
        )
    }

    if (deleting.isNotEmpty()) {
        val list = deleting
        AlertDialog(
            onDismissRequest = { deleting = emptyList() },
            title = { Text(if (list.size == 1) "Delete this thread?" else "Delete ${list.size} threads?") },
            text = {
                Text(
                    (if (list.size == 1) "\u201c${list[0].title}\u201d and all its notes" else list.joinToString("\n") { "\u2022 ${it.title}" } + "\n\nand all their notes") +
                        " will be gone for good.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = emptyList(); selecting = false; selected = emptySet()
                    scope.launch { list.forEach { repo.deleteThread(it.id) } }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = emptyList() }) { Text("Keep") } },
        )
    }
    revisiting?.let { t ->
        val opts = listOf(0 to "Never", 14 to "In 2 weeks", 30 to "In 1 month", 90 to "In 3 months", 180 to "In 6 months")
        AlertDialog(
            onDismissRequest = { revisiting = null },
            title = { Text("Remind me to revisit") },
            text = {
                Column {
                    opts.forEach { (d, l) ->
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable {
                                revisiting = null; scope.launch { repo.saveThread(t.copy(revisitDays = d)) }
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = t.revisitDays == d, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Hues.Journal))
                            Spacer(Modifier.width(12.dp))
                            Text(l)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { revisiting = null }) { Text("Close") } },
        )
    }

    if (noScreenLock) AlertDialog(
        onDismissRequest = { noScreenLock = false },
        title = { Text("Set a screen lock first") },
        text = { Text("Hidden threads open with your fingerprint, face or PIN. Set a screen lock on your phone, then try again.") },
        confirmButton = { TextButton(onClick = { noScreenLock = false }) { Text("OK") } },
    )
    if (creating) NewThreadSheet(onDismiss = { creating = false }) { title, why, expect, major, decided ->
        creating = false
        scope.launch { onOpen(repo.startThread(title, why, expect, major = major, decidedAt = decided)) }
    }
}

/** Plain, borderless writing field with a soft placeholder. */
@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, style: TextStyle, minLines: Int = 1) {
    BasicTextField(
        value, onChange, textStyle = style.copy(color = if (style.color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else style.color), minLines = minLines,
        cursorBrush = SolidColor(Hues.Journal),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)))
                inner()
            }
        },
    )
}

/** A two-option switch that clearly looks like a choice. */
@Composable
private fun BigSmall(major: Boolean, onPick: (Boolean) -> Unit) {
    Row(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(3.dp)) {
        listOf(true to "Big decision", false to "Small decision").forEach { (v, l) ->
            val on = major == v
            Text(
                l, style = MaterialTheme.typography.labelLarge,
                color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(CircleShape).background(if (on) Hues.Journal else Color.Transparent)
                    .clickable { onPick(v) }.padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun NewThreadSheet(onDismiss: () -> Unit, onCreate: (String, String, String, Boolean, Long) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        NewThreadForm(onCreate)
    }
}

@Composable
fun NewThreadForm(onCreate: (String, String, String, Boolean, Long) -> Unit) {
    var decided by remember { mutableStateOf(java.time.LocalDate.now()) }
    var pickingDate by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var why by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    var expect by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    var major by remember { mutableStateOf(true) }
    val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Hues.Journal, focusedLabelColor = Hues.Journal, cursorColor = Hues.Journal)
    run {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).imePadding().verticalScroll(rememberScrollState())) {
            Text("New thread", style = MaterialTheme.typography.headlineSmall)
            Gap(16)
            BigSmall(major) { major = it }
            Gap(18)
            OutlinedTextField(title, { title = it }, label = { Text("Name") }, singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth())
            Gap(10)
            OutlinedTextField(why, { why = continueLists(why, it) }, label = { Text("Description") }, minLines = 3, colors = colors, modifier = Modifier.fillMaxWidth())
            Gap(10)
            OutlinedTextField(expect, { expect = continueLists(expect, it) }, label = { Text("Expectation (optional)") }, minLines = 2, colors = colors, modifier = Modifier.fillMaxWidth())
            Gap(6)
            val isToday = decided == java.time.LocalDate.now()
            Row(
                Modifier.clip(CircleShape).clickable { pickingDate = true }.padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (isToday) "Decided today" else "Decided ${decided.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}", style = QuietText)
                Text("  \u25BE", style = QuietText)
            }
            Gap(12)
            Button(
                onClick = {
                    val at = if (isToday) System.currentTimeMillis() else decided.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    onCreate(title.trim(), why.text.trim(), expect.text.trim(), major, at)
                }, enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Hues.Journal, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Start thread") }
        }
    }
    if (pickingDate) PastDayPicker(decided, onDismiss = { pickingDate = false }) { d -> decided = java.time.LocalDate.parse(d); pickingDate = false }
}

/** One thread, read top to bottom like an essay that grows over time. */
@Composable
fun ThreadScreen(repo: Repository, id: String, onBack: () -> Unit, startEditing: Boolean = false) {
    val thread by repo.thread(id).collectAsStateWithLifecycle(null)
    val loadedNotes by repo.threadNotes(id).collectAsStateWithLifecycle(null)
    val notes = loadedNotes ?: emptyList()
    val scope = rememberCoroutineScope()
    var edit by rememberSaveable { mutableStateOf(startEditing) }
    BackHandler(enabled = edit) { edit = false }
    var adding by remember { mutableStateOf(false) }
    var newDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<ThreadNote?>(null) }
    val t = thread ?: return
    if (t.hidden && !HiddenLock.unlocked) { HiddenGate(onBack) {}; return }
    // The decision card is permanent: a thread always has its first note, even if it's empty.
    LaunchedEffect(t.id, loadedNotes) {
        if (loadedNotes != null && loadedNotes!!.isEmpty()) {
            repo.saveThreadNote(ThreadNote(threadId = t.id, text = "", at = t.createdAt))
        }
    }
    val accent = Hues.Journal
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val expStyle = ProseText.copy(fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 26.sp, color = quiet)
    // Edit in place: each note becomes a plain field that saves as you type.
    @Composable fun NoteBody(n: ThreadNote, placeholder: String = "Write…", empty: String? = null) {
        if (edit) {
            var text by remember(n.id) { mutableStateOf(n.text) }
            ListField(text, { text = it; scope.launch { repo.saveThreadNote(n.copy(text = it)) } }, placeholder, ProseText)
        } else if (n.text.isBlank() && empty != null) {
            Text(empty, style = ProseText.copy(color = quiet.copy(alpha = 0.6f), fontSize = 16.sp))
        } else FormattedText(n.text, style = ProseText)
    }

    Box(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
            item {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
            }
            item {
                Column(Modifier.padding(horizontal = 28.dp)) {
                    val titleStyle = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp, lineHeight = 36.sp)
                    if (edit) {
                        var title by remember(t.id) { mutableStateOf(t.title) }
                        Field(title, { title = it; scope.launch { repo.saveThread(t.copy(title = it)) } }, "Title", titleStyle)
                    } else Text(t.title, style = titleStyle)
                    Gap(24)
                }
            }
            // The decision itself, and what you expected: the only card on the page.
            notes.firstOrNull()?.let { n ->
                item(key = n.id) {
                    Column(
                        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(22.dp),
                    ) {
                        if (edit) {
                            Text(
                                fmt(n.at).uppercase() + "  \u270E", style = MarkText.copy(color = accent),
                                modifier = Modifier.clip(CircleShape).clickable { pickingDate = true }.padding(vertical = 4.dp),
                            )
                        } else Text(fmt(n.at).uppercase(), style = MarkText)
                        Gap(10)
                        NoteBody(n, placeholder = "Description", empty = if (t.expectation.isBlank()) "Nothing written yet. Tap Edit to add a description or an expectation." else null)
                        if (edit) {
                            Gap(16)
                            var exp by remember(t.id) { mutableStateOf(t.expectation) }
                            ListField(exp, { exp = it; scope.launch { repo.saveThread(t.copy(expectation = it)) } }, "Expectation", expStyle)
                        } else if (t.expectation.isNotBlank()) {
                            Gap(16)
                            FormattedText(t.expectation, style = expStyle)
                        }
                    }
                }
            }
            val later = notes.drop(1)
            if (later.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 30.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text("SINCE THEN", style = MarkText, modifier = Modifier.padding(horizontal = 28.dp))
                    Gap(16)
                }
                itemsIndexed(later, key = { _, n -> n.id }) { i, n ->
                    LaterNote(fmt(n.at), last = i == later.lastIndex) {
                        NoteBody(n)
                        if (edit) Text(
                            "Remove", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 6.dp).clip(CircleShape).clickable { removing = n }.padding(vertical = 4.dp),
                        )
                    }
                }
            } else item { Gap(12) }
            item {
                if (adding) {
                    var text by rememberSaveable { mutableStateOf("") }
                    Column(Modifier.padding(horizontal = 28.dp, vertical = 14.dp)) {
                        Text("TODAY", style = MarkText)
                        Gap(6)
                        ListField(text, { text = it }, if (notes.isEmpty()) "Why? Your reasoning…" else "How do you see it now?", ProseText, minLines = 3)
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { adding = false }) { Text("Cancel", color = quiet) }
                            TextButton(enabled = text.isNotBlank(), onClick = {
                                val v = text; adding = false
                                scope.launch { repo.addThreadNote(t, v, "") }
                            }) { Text("Save", color = if (text.isNotBlank()) accent else quiet.copy(alpha = 0.5f)) }
                        }
                    }
                } else if (!edit) Text(
                    "+  Add how you see it now", style = MaterialTheme.typography.bodyLarge, color = accent,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).clip(CircleShape).clickable { adding = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { edit = !edit },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
            containerColor = if (edit) accent else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (edit) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            icon = { Icon(if (edit) Icons.Rounded.Check else Icons.Rounded.Edit, null) },
            text = { Text(if (edit) "Done" else "Edit") },
        )
    }

    notes.firstOrNull()?.let { first ->
        val current = java.time.Instant.ofEpochMilli(first.at).atZone(ZoneId.systemDefault()).toLocalDate()
        if (pickingDate) PastDayPicker(current, onDismiss = { pickingDate = false }) { d ->
            pickingDate = false
            val picked = java.time.LocalDate.parse(d)
            if (picked != current) newDate = picked
        }
        newDate?.let { d ->
            val latestLater = notes.drop(1).minOfOrNull { it.at }
            val at = d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val tooLate = latestLater != null && at > latestLater
            AlertDialog(
                onDismissRequest = { newDate = null },
                title = { Text("Change the decision date?") },
                text = {
                    Text(
                        if (tooLate) "That's after your first follow-up note (${fmt(latestLater!!)}). Pick a date on or before it."
                        else "From ${fmt(first.at)} to ${d.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}.",
                    )
                },
                confirmButton = {
                    if (!tooLate) TextButton(onClick = {
                        newDate = null
                        scope.launch {
                            repo.saveThreadNote(first.copy(at = at))
                            repo.saveThread(t.copy(createdAt = at))
                        }
                    }) { Text("Change", color = accent) }
                },
                dismissButton = { TextButton(onClick = { newDate = null }) { Text(if (tooLate) "OK" else "Keep") } },
            )
        }
    }
    removing?.let { n ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove this note?") },
            text = { Text("The note from ${fmt(n.at)} will be gone for good.") },
            confirmButton = {
                TextButton(onClick = { removing = null; scope.launch { repo.deleteThreadNote(n) } }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } },
        )
    }
}

/** A later note: a quiet dot and line on the left, the date, then your words. No card. */
@Composable
private fun LaterNote(date: String, last: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val accent = Hues.Journal
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).then(modifier).padding(horizontal = 28.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(12.dp).fillMaxHeight()) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            if (!last) Box(Modifier.padding(top = 4.dp).width(1.5.dp).weight(1f).background(accent.copy(alpha = 0.25f)))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(bottom = if (last) 8.dp else 26.dp)) {
            Text(date.uppercase(), style = MarkText)
            Gap(6)
            content()
        }
    }
}
