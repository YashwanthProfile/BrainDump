@file:OptIn(ExperimentalMaterial3Api::class)

package app.braindump.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.braindump.data.Dates
import app.braindump.data.Entry

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.displaySmall)
                if (subtitle != null) Text(subtitle, style = QuietText)
            }
            if (action != null) action()
        }
    }
}

/** The app's basic card: soft, rounded, no outline. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    val m = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = m, color = color, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(18.dp), content = content)
        }
    } else {
        Surface(modifier = m, color = color, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(18.dp), content = content)
        }
    }
}

@Composable
fun IconBadge(icon: ImageVector, hue: Color, size: Int = 40) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(hue.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = hue, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp),
    )
}

/** One moment on a vertical timeline: a dot, a connecting line, the time and the words. */
@Composable
fun TimelineItem(entry: Entry, isLast: Boolean, hue: Color = Hues.Journal, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(20.dp).fillMaxHeight()) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(hue))
            if (!isLast) {
                Box(Modifier.padding(top = 4.dp).width(2.dp).weight(1f).background(hue.copy(alpha = 0.25f)))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(bottom = 18.dp)) {
            Text(
                Dates.time(entry.happenedAt) + if (entry.kind == "missed") "  ·  added later" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(entry.text, style = MaterialTheme.typography.bodyLarge)
            if (entry.details.isNotBlank()) FormattedText(entry.details, style = QuietText)
        }
    }
}

/** Add or edit an entry: what happened, optional details, and when. */
@Composable
fun EntryDialog(
    title: String,
    initialText: String,
    initialDetails: String,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    allowEmpty: Boolean = false,
    onSave: (text: String, details: String, minuteOfDay: Int) -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    var detailsV by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(initialDetails)) }
    val details = detailsV.text
    var minute by remember { mutableIntStateOf(initialMinute) }
    var pickTime by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text("What happened") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(detailsV, { detailsV = continueLists(detailsV, it) }, label = { Text("Details (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { pickTime = true }) { Text("At ${Dates.formatMinute(minute)}") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text, details, minute) }, enabled = allowEmpty || text.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
    if (pickTime) {
        TimeDialog(minute, onDismiss = { pickTime = false }) { minute = it; pickTime = false }
    }
}

@Composable
fun TimeDialog(initialMinute: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinute / 60 % 24, initialMinute = initialMinute % 60)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") }
                }
            }
        }
    }
}

@Composable
fun EmptyNote(text: String) {
    Text(text, style = QuietText, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
}

@Composable
fun Gap(h: Int = 8) = Spacer(Modifier.height(h.dp))

/** A big number with a small caption under it. */
@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = QuietText, maxLines = 1)
    }
}

fun label(s: String) = s.replaceFirstChar { it.uppercase() }
