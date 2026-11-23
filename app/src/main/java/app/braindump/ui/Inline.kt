package app.braindump.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle

/**
 * Light inline formatting kept as plain text, so it survives export and copy-paste:
 *  **bold**   _italic_   ==highlight==   "quotes" (shown softly in italic)
 */
private val inline = Regex("\\*\\*(.+?)\\*\\*|==(.+?)==|(?<![\\w_])_(?!\\s)(.+?)(?<!\\s)_(?![\\w_])|\"([^\"\\n]+)\"|\u201c([^\u201d\\n]+)\u201d")

/** A soft sand-coloured marker, quieter than the coral accent. */
val HighlightColor = Color(0xFFE8C766).copy(alpha = 0.38f)

private val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)
private val italicStyle = SpanStyle(fontStyle = FontStyle.Italic)
private val markStyle = SpanStyle(background = HighlightColor)
private fun quoteStyle(ink: Color) = SpanStyle(fontStyle = FontStyle.Italic, color = ink)

/** For reading: markers disappear, the words take on the style. */
fun styled(text: String, quoteInk: Color): AnnotatedString {
    if (!inline.containsMatchIn(text)) return AnnotatedString(text)
    return buildAnnotatedString {
        var at = 0
        inline.findAll(text).forEach { m ->
            append(text.substring(at, m.range.first))
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> withStyle(boldStyle) { append(g[1]) }
                g[2].isNotEmpty() -> withStyle(markStyle) { append(g[2]) }
                g[3].isNotEmpty() -> withStyle(italicStyle) { append(g[3]) }
                else -> withStyle(quoteStyle(quoteInk)) { append(m.value) }
            }
            at = m.range.last + 1
        }
        append(text.substring(at))
    }
}

/** For writing: markers stay (faint) so the cursor behaves; the words already look styled. */
class InlineStyleTransformation(private val faint: Color, private val quoteInk: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (!inline.containsMatchIn(raw)) return TransformedText(text, OffsetMapping.Identity)
        val out = buildAnnotatedString {
            append(raw)
            val dim = SpanStyle(color = faint)
            inline.findAll(raw).forEach { m ->
                val g = m.groupValues
                val r = m.range
                when {
                    g[1].isNotEmpty() -> { addStyle(boldStyle, r.first + 2, r.last - 1); addStyle(dim, r.first, r.first + 2); addStyle(dim, r.last - 1, r.last + 1) }
                    g[2].isNotEmpty() -> { addStyle(markStyle, r.first + 2, r.last - 1); addStyle(dim, r.first, r.first + 2); addStyle(dim, r.last - 1, r.last + 1) }
                    g[3].isNotEmpty() -> { addStyle(italicStyle, r.first + 1, r.last); addStyle(dim, r.first, r.first + 1); addStyle(dim, r.last, r.last + 1) }
                    else -> addStyle(quoteStyle(quoteInk), r.first, r.last + 1)
                }
            }
        }
        return TransformedText(out, OffsetMapping.Identity)
    }
}

/** Wraps the selection in a marker, or unwraps it if it's already wrapped. Empty selection inserts a pair with the cursor inside. */
fun toggleWrap(v: androidx.compose.ui.text.input.TextFieldValue, mark: String): androidx.compose.ui.text.input.TextFieldValue {
    val s = v.selection.min; val e = v.selection.max
    val t = v.text
    val n = mark.length
    if (s >= n && e + n <= t.length && t.substring(s - n, s) == mark && t.substring(e, e + n) == mark) {
        val out = t.substring(0, s - n) + t.substring(s, e) + t.substring(e + n)
        return v.copy(text = out, selection = androidx.compose.ui.text.TextRange(s - n, e - n))
    }
    val out = t.substring(0, s) + mark + t.substring(s, e) + mark + t.substring(e)
    return v.copy(text = out, selection = androidx.compose.ui.text.TextRange(s + n, e + n))
}

/** Text with photo markers and style markers removed, for previews and search results. */
fun plainText(s: String): String = s.lines().filterNot { photoLine.matches(it) }.joinToString("\n")
    .replace("**", "").replace("==", "").replace(Regex("(?<![\\w_])_(?!\\s)(.+?)(?<!\\s)_(?![\\w_])"), "$1")

val photoLine = Regex("^\\[\\[photo:([A-Za-z0-9-]+)]]$")

@Composable
fun rememberInlineTransformation(faint: Color, quoteInk: Color) = remember(faint, quoteInk) { InlineStyleTransformation(faint, quoteInk) }
