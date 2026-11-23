@file:OptIn(ExperimentalMaterial3Api::class)

package app.braindump.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.braindump.data.Attachment
import app.braindump.data.Dates
import app.braindump.data.Entry
import app.braindump.data.Repository
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayName = DateTimeFormatter.ofPattern("EEEE")
private val dayLong = DateTimeFormatter.ofPattern("d MMMM yyyy")

/**
 * One day, in two halves:
 *  - the moments, as a timeline (with a small + to add a moment or a photo),
 *  - the write-up underneath, read like a blog post.
 * Opens in Read mode so nothing changes by accident; the Edit button bottom-right switches modes.
 */
@Composable
fun DayScreen(repo: Repository, date: String, onBack: () -> Unit, startEditing: Boolean = false) {
    val entries by repo.entries(date).collectAsStateWithLifecycle(emptyList())
    val day by repo.day(date).collectAsStateWithLifecycle(null)
    val notePhotos by repo.dayPhotos(date).collectAsStateWithLifecycle(emptyList())
    val entryPhotos by repo.entryPhotos(date).collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val accent = Hues.Journal
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant

    var edit by rememberSaveable { mutableStateOf(startEditing) }
    BackHandler(enabled = edit) { edit = false }

    var editing by remember { mutableStateOf<Entry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var removingPhoto by remember { mutableStateOf<Attachment?>(null) }

    // Title and write-up are edited locally and saved shortly after typing pauses.
    var title by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(300); loaded = true }
    LaunchedEffect(day, loaded) {
        if (notes == null && (day != null || loaded)) { notes = day?.notes ?: ""; title = day?.title ?: "" }
    }
    LaunchedEffect(notes) {
        val n = notes ?: return@LaunchedEffect
        if (n == (day?.notes ?: "")) return@LaunchedEffect
        delay(500); repo.saveDayNotes(date, n)
    }
    LaunchedEffect(title) {
        val t = title ?: return@LaunchedEffect
        if (t == (day?.title ?: "")) return@LaunchedEffect
        delay(500); repo.saveDayTitle(date, t)
    }

    // While editing, the write-up is blocks of text with photos between them.
    val writeUp = remember(notes == null) { WriteUpState(notes ?: "") }
    val placed = remember(notes) { photoIds(notes ?: "").toSet() }
    val unplaced = notePhotos.filter { it.id !in placed }

    // Pinch with two fingers to make the writing bigger or smaller; it's remembered.
    val savedScale = LocalTextScale.current
    var scale by remember(savedScale) { mutableStateOf(savedScale) }

    val nowAt = { if (date == Dates.today()) System.currentTimeMillis() else Dates.at(date, 12 * 60) }
    val pickMomentPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { repo.addPhotoMoment(date, nowAt(), uri) }
    }
    val pickNotePhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val id = repo.addDayPhoto(date, uri) ?: return@launch
            writeUp.insertPhoto(id); notes = writeUp.text()
        }
    }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    val d = LocalDate.parse(date)

    androidx.compose.runtime.CompositionLocalProvider(LocalTextScale provides scale) {
    val prose = ProseText.copy(color = MaterialTheme.colorScheme.onSurface)
    Box(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp),
            modifier = Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    var pinched = false
                    do {
                        val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        if (ev.changes.count { it.pressed } >= 2) {
                            val z = ev.calculateZoom()
                            if (z != 1f) {
                                scale = (scale * z).coerceIn(0.8f, 1.4f); pinched = true
                                ev.changes.forEach { it.consume() }
                            }
                        }
                    } while (ev.changes.any { it.pressed })
                    if (pinched) scope.launch { repo.settings.update(repo.settings.current().copy(textScale = scale)) }
                }
            },
        ) {
            item {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            }
            // Header: date, then the day's title.
            item {
                Column(Modifier.padding(horizontal = 22.dp).padding(top = 4.dp, bottom = 18.dp)) {
                    Text("${d.format(dayName).uppercase()}  ·  ${d.format(dayLong).uppercase()}", style = MarkText)
                    Gap(10)
                    val titleStyle = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp, lineHeight = 38.sp)
                    if (edit) {
                        PlainField(title ?: "", { title = it }, "Give the day a title", titleStyle.copy(color = MaterialTheme.colorScheme.onSurface), singleLine = true)
                    } else {
                        Text(
                            (title ?: "").ifBlank { d.format(dayLong) },
                            style = titleStyle,
                            color = if ((title ?: "").isBlank()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // First: the write-up, read like a blog post.
            item {
                Column(Modifier.padding(horizontal = 22.dp)) {
                    if (edit) {
                        WriteUpEditor(writeUp, notePhotos, repo, prose, onChange = { notes = writeUp.text() }, onPhotoTap = { removingPhoto = it })
                    } else if (!notes.isNullOrBlank()) {
                        WriteUpReader(notes!!, notePhotos, repo, prose)
                    } else if (unplaced.isEmpty()) {
                        Text("Nothing written yet. Tap Edit to write about the day.", style = prose.copy(color = quiet.copy(alpha = 0.7f)))
                    }
                }
            }
            // Photos from before they could sit inside the writing.
            items(unplaced, key = { it.id }) { p ->
                AsyncImage(
                    model = repo.fileFor(p), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp).fillMaxWidth().heightIn(max = 340.dp)
                        .clip(MaterialTheme.shapes.small)
                        .then(if (edit) Modifier.clickable { removingPhoto = p } else Modifier),
                )
            }
            // The divide between the two halves.
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(3) {
                        Box(Modifier.padding(horizontal = 6.dp).size(4.dp).clip(CircleShape).background(quiet.copy(alpha = 0.5f)))
                    }
                }
            }

            // Below: the moments, as a timeline.
            items(entries, key = { it.id }) { e ->
                Moment(
                    e, entryPhotos.filter { it.ownerId == e.id }, repo,
                    onClick = if (edit) ({ editing = e }) else null,
                    accent = accent,
                )
            }
            item {
                Row(Modifier.padding(start = 22.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)).clickable { addMenu = true },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Add, contentDescription = "Add a moment", tint = accent, modifier = Modifier.size(20.dp)) }
                        DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                            DropdownMenuItem(text = { Text("Moment") }, onClick = { addMenu = false; adding = true })
                            DropdownMenuItem(text = { Text("Photo") }, onClick = { addMenu = false; pickMomentPhoto.launch(imageOnly) })
                        }
                    }
                    if (entries.isEmpty()) {
                        Spacer(Modifier.width(12.dp))
                        Text("Add what happened today", style = QuietText)
                    }
                }
            }

        }

        if (edit) FormatBar(
            onBold = { writeUp.format("**"); notes = writeUp.text() },
            onItalic = { writeUp.format("_"); notes = writeUp.text() },
            onHighlight = { writeUp.format("=="); notes = writeUp.text() },
            onPhoto = { pickNotePhoto.launch(imageOnly) },
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(20.dp),
        )
        // Read / Edit switch.
        ExtendedFloatingActionButton(
            onClick = { edit = !edit },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
            containerColor = if (edit) accent else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (edit) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            icon = { Icon(if (edit) Icons.Rounded.Check else Icons.Rounded.Edit, contentDescription = null) },
            text = { Text(if (edit) "Done" else "Edit") },
        )
    }
    }

    editing?.let { e ->
        EntryDialog(
            title = "Edit moment", initialText = e.text, initialDetails = e.details,
            initialMinute = Dates.minuteOfDay(e.happenedAt),
            onDismiss = { editing = null },
            onDelete = { scope.launch { repo.deleteEntry(e) }; editing = null },
            allowEmpty = e.kind == "photo",
        ) { text, details, minute ->
            scope.launch { repo.saveEntry(e.copy(text = text.trim(), details = details.trim(), happenedAt = Dates.at(date, minute))) }
            editing = null
        }
    }
    if (adding) {
        EntryDialog(
            title = "Add a moment", initialText = "", initialDetails = "",
            initialMinute = Dates.minuteOfDay(nowAt()),
            onDismiss = { adding = false },
        ) { text, details, minute ->
            scope.launch { repo.saveEntry(Entry(date = date, happenedAt = Dates.at(date, minute), text = text.trim(), details = details.trim())) }
            adding = false
        }
    }
    removingPhoto?.let { p ->
        AlertDialog(
            onDismissRequest = { removingPhoto = null },
            text = { Text("Remove this photo?") },
            confirmButton = {
                TextButton(onClick = {
                    writeUp.removePhoto(p.id); if (edit) notes = writeUp.text()
                    else notes = fromBlocks(toBlocks(notes ?: "").filterNot { it is Block.Photo && it.id == p.id })
                    scope.launch { repo.deletePhoto(p) }; removingPhoto = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removingPhoto = null }) { Text("Keep") } },
        )
    }
}

/** A text area with no box around it, like a notes app. */
@Composable
private fun PlainField(value: String, onChange: (String) -> Unit, placeholder: String, style: TextStyle, singleLine: Boolean = false) {
    BasicTextField(
        value = value, onValueChange = onChange, textStyle = style, singleLine = singleLine,
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

/** One moment on the timeline: a dot, a thread to the next, the time, the words and any photo. */
@Composable
private fun Moment(e: Entry, photos: List<Attachment>, repo: Repository, onClick: (() -> Unit)?, accent: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 28.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(20.dp).fillMaxHeight()) {
            Spacer(Modifier.height(5.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
            Box(Modifier.padding(top = 4.dp).width(2.dp).weight(1f).background(accent.copy(alpha = 0.22f)))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(bottom = 18.dp)) {
            Text(Dates.time(e.happenedAt), style = MarkText)
            if (e.text.isNotBlank()) FormattedText(e.text, style = MaterialTheme.typography.bodyLarge)
            if (e.details.isNotBlank()) FormattedText(e.details, style = QuietText)
            photos.forEach { p ->
                Gap(6)
                AsyncImage(
                    model = repo.fileFor(p), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.small),
                )
            }
        }
    }
}
