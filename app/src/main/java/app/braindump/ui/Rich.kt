package app.braindump.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Lists in plain text. A line that starts with a bullet (- * • ◦ ▪ → – ✓ ★ …), a number (1. or 1)),
 * a letter (a)) or a checkbox ([ ] / [x]) is a list item. Two leading spaces nest it one level.
 */
private val listLine = Regex("^( *)([-*•◦▪▸►→–—✓✔★☆♦·]|\\d{1,3}[.)]|[a-z]\\)|\\[[ xX]\\])[ \\t]+(.*)$")

private data class Item(val level: Int, val marker: String, val body: String)

private fun parse(line: String): Item? = listLine.matchEntire(line)?.let {
    Item(it.groupValues[1].length / 2, it.groupValues[2], it.groupValues[3])
}

private fun shown(i: Item): String = when (i.marker) {
    "-", "*", "•" -> listOf("•", "◦", "▪")[i.level.coerceAtMost(2)]
    "[ ]" -> "☐"
    "[x]", "[X]" -> "☑"
    else -> i.marker
}

/** Reads text with neat, hanging-indent lists. Plain lines stay plain paragraphs. */
@Composable
fun FormattedText(text: String, style: TextStyle, modifier: Modifier = Modifier, marker: Color = Hues.Journal) {
    val lines = text.trimEnd().split('\n')
    val ink = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    if (lines.none { parse(it) != null }) { Text(styled(text.trimEnd(), ink), style = style, modifier = modifier); return }
    Column(modifier) {
        val para = StringBuilder()
        fun flushInto(out: MutableList<Pair<String?, Item?>>) { if (para.isNotEmpty()) { out += para.toString().trimEnd('\n') to null; para.clear() } }
        val blocks = mutableListOf<Pair<String?, Item?>>()
        lines.forEach { l ->
            val i = parse(l)
            if (i == null) para.append(l).append('\n') else { flushInto(blocks); blocks += null to i }
        }
        flushInto(blocks)
        blocks.forEachIndexed { idx, (p, i) ->
            if (p != null) {
                if (idx > 0) Spacer(Modifier.height(6.dp))
                Text(styled(p, ink), style = style)
                if (idx < blocks.lastIndex) Spacer(Modifier.height(6.dp))
            } else if (i != null) {
                Row(Modifier.padding(start = (i.level * 20).dp, top = 2.dp, bottom = 2.dp)) {
                    Text(shown(i), style = style.copy(color = marker), modifier = Modifier.widthIn(min = 22.dp).padding(end = 8.dp), maxLines = 1, overflow = TextOverflow.Clip)
                    val done = i.marker == "[x]" || i.marker == "[X]"
                    Text(styled(i.body, ink), style = if (done) style.copy(color = style.color.copy(alpha = 0.5f)) else style, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun nextMarker(m: String): String {
    m.dropLast(1).toIntOrNull()?.let { return "${it + 1}${m.last()}" }
    if (m.length == 2 && m[0] in 'a'..'y' && m[1] == ')') return "${m[0] + 1})"
    if (m.startsWith("[")) return "[ ]"
    return m
}

/** Keeps lists going while typing: Enter continues a list, Enter on an empty item ends it, "- " becomes "• ". */
fun continueLists(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    val c = new.selection.start
    if (!new.selection.collapsed || new.text.length != old.text.length + 1 || c == 0) return new
    val typed = new.text[c - 1]
    val lineStart = new.text.lastIndexOf('\n', c - 2) + 1
    if (typed == ' ') {
        val head = new.text.substring(lineStart, c - 1)
        if (head.isNotEmpty() && head.trimStart(' ') in setOf("-", "*")) {
            val t = new.text.substring(0, c - 2) + "•" + new.text.substring(c - 1)
            return TextFieldValue(t, TextRange(c))
        }
        return new
    }
    if (typed != '\n') return new
    val prev = new.text.substring(lineStart, c - 1)
    val i = listLine.matchEntire(prev) ?: return new
    val indent = i.groupValues[1]
    if (i.groupValues[3].isBlank()) {
        // Empty item: end the list.
        val t = new.text.substring(0, lineStart) + new.text.substring(c)
        return TextFieldValue(t, TextRange(lineStart))
    }
    val add = indent + nextMarker(i.groupValues[2]) + " "
    val t = new.text.substring(0, c) + add + new.text.substring(c)
    return TextFieldValue(t, TextRange(c + add.length))
}

/** A borderless writing field that understands lists. */
@Composable
fun ListField(
    value: String, onChange: (String) -> Unit, placeholder: String, style: TextStyle,
    modifier: Modifier = Modifier, minLines: Int = 1, singleLine: Boolean = false,
) {
    var tfv by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val current = if (tfv.text == value) tfv else TextFieldValue(value, TextRange(value.length))
    RichField(current, { tfv = it; onChange(it.text) }, placeholder, style, modifier, minLines, singleLine)
}

/** The same writing field with the value (and cursor) held by the caller, so a toolbar can act on the selection. */
@Composable
fun RichField(
    value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, placeholder: String, style: TextStyle,
    modifier: Modifier = Modifier, minLines: Int = 1, singleLine: Boolean = false,
) {
    val current = value
    val raw = current.text
    val color = if (style.color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else style.color
    val transform = rememberInlineTransformation(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f))
    BasicTextField(
        value = current,
        visualTransformation = transform,
        onValueChange = { n -> onValueChange(if (singleLine) n else continueLists(current, n)) },
        textStyle = style.copy(color = color), singleLine = singleLine, minLines = minLines,
        cursorBrush = SolidColor(Hues.Journal),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box {
                if (raw.isEmpty()) Text(placeholder, style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)))
                inner()
            }
        },
    )
}
