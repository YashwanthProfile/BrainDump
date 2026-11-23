@file:OptIn(ExperimentalMaterial3Api::class)

package app.braindump.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.braindump.data.Repository
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val resultDate = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

private data class Hit(val date: String, val title: String, val snippet: String)

/** Search every title, write-up and moment. One quiet line of filters: what to search, and the order. */
@Composable
fun SearchScreen(repo: Repository, onBack: () -> Unit, onOpenDay: (String) -> Unit, initialQuery: String = "", onOpenThread: (String) -> Unit = {}, threadsOnly: Boolean = false) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var scope by rememberSaveable { mutableStateOf(if (threadsOnly) "threads" else "all") } // all, writing, moments | threads
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    val q = query.trim()
    val days by remember(q) { if (q.length < 2) flowOf(emptyList()) else repo.searchDays(q) }.collectAsStateWithLifecycle(emptyList())
    val entries by remember(q) { if (q.length < 2) flowOf(emptyList()) else repo.searchEntries(q) }.collectAsStateWithLifecycle(emptyList())
    val threads by remember(q) { if (q.length < 2) flowOf(emptyList()) else repo.searchThreads(q) }.collectAsStateWithLifecycle(emptyList())
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    // One result per day: the best matching line from that day.
    val hits = remember(days, entries, scope, newestFirst) {
        val byDate = linkedMapOf<String, Hit>()
        if (scope == "threads") return@remember emptyList<Hit>()
        if (scope != "moments") days.forEach { d ->
            val inTitle = d.title.contains(q, ignoreCase = true)
            byDate[d.date] = Hit(d.date, d.title, plainText(d.notes).let { if (inTitle) it else snippet(it, q) })
        }
        if (scope != "writing") entries.forEach { e ->
            val line = if (e.text.contains(q, true)) e.text else e.details
            byDate.getOrPut(e.date) { Hit(e.date, days.firstOrNull { it.date == e.date }?.title.orEmpty(), line) }
        }
        byDate.values.sortedBy { it.date }.let { if (newestFirst) it.reversed() else it }
    }
    val accent = Hues.Journal

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            BasicTextField(
                query, { query = it }, singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) Text(if (threadsOnly) "Search threads" else "Search your journal", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") }
        }
        Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            (if (threadsOnly) emptyList() else listOf("all" to "All", "writing" to "Writing", "moments" to "Moments")).forEach { (k, l) ->
                FilterChip(selected = scope == k, onClick = { scope = k }, label = { Text(l) })
            }
            TextButton(onClick = { newestFirst = !newestFirst }) { Text(if (newestFirst) "Newest" else "Oldest") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            if (q.length >= 2 && (if (threadsOnly) threads.isEmpty() else hits.isEmpty())) item { EmptyNote("Nothing found for “$q”.") }
            if (threadsOnly) items(threads, key = { "t" + it.id }) { t ->
                Column(Modifier.fillMaxWidth().clickable { onOpenThread(t.id) }.padding(horizontal = 24.dp, vertical = 14.dp)) {
                    Text("THREAD", style = MarkText.copy(color = accent))
                    Text(t.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            items(hits, key = { it.date }) { h ->
                Column(Modifier.fillMaxWidth().clickable { onOpenDay(h.date) }.padding(horizontal = 24.dp, vertical = 14.dp)) {
                    Text(LocalDate.parse(h.date).format(resultDate).uppercase(), style = MarkText)
                    if (h.title.isNotBlank()) Text(h.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (h.snippet.isNotBlank()) Text(highlight(h.snippet, q, accent), style = QuietText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A short window of [text] around the first match of [q]. */
private fun snippet(text: String, q: String): String {
    val i = text.indexOf(q, ignoreCase = true)
    if (i < 0) return text
    val start = (i - 40).coerceAtLeast(0)
    return (if (start > 0) "…" else "") + text.substring(start).replace('\n', ' ')
}

private fun highlight(text: String, q: String, color: androidx.compose.ui.graphics.Color) = buildAnnotatedString {
    var from = 0
    while (true) {
        val i = text.indexOf(q, from, ignoreCase = true)
        if (i < 0 || q.isEmpty()) { append(text.substring(from)); break }
        append(text.substring(from, i))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(text.substring(i, i + q.length)) }
        from = i + q.length
    }
}
