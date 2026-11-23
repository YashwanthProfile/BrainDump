package app.braindump.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.braindump.data.Attachment
import app.braindump.data.Repository
import coil.compose.AsyncImage

/**
 * The day's write-up is plain text where a line like [[photo:ID]] places a photo.
 * While editing it becomes blocks: text, photo, text, photo, text… so you can write above and below each photo.
 */
sealed interface Block {
    data class Words(val value: TextFieldValue) : Block
    data class Photo(val id: String) : Block
}

fun photoIds(notes: String): List<String> = notes.lines().mapNotNull { photoLine.matchEntire(it.trim())?.groupValues?.get(1) }

fun toBlocks(notes: String): List<Block> {
    val out = mutableListOf<Block>()
    val buf = mutableListOf<String>()
    fun flush() { val t = buf.joinToString("\n"); out += Block.Words(TextFieldValue(t, TextRange(t.length))); buf.clear() }
    notes.lines().forEach { l ->
        val m = photoLine.matchEntire(l.trim())
        if (m != null) { flush(); out += Block.Photo(m.groupValues[1]) } else buf += l
    }
    flush()
    return out
}

fun fromBlocks(blocks: List<Block>): String = blocks.mapNotNull {
    when (it) {
        is Block.Photo -> "[[photo:${it.id}]]"
        is Block.Words -> it.value.text.takeIf { t -> t.isNotEmpty() }
    }
}.joinToString("\n")

class WriteUpState(initial: String) {
    val blocks = mutableStateListOf<Block>().apply { addAll(toBlocks(initial)) }
    var focused by mutableStateOf(-1)

    fun text() = fromBlocks(blocks)

    private fun target(): Int = focused.takeIf { it in blocks.indices && blocks[it] is Block.Words }
        ?: blocks.indexOfLast { it is Block.Words }

    /** Puts the photo where the cursor is, splitting the text around it. */
    fun insertPhoto(id: String) {
        val i = target()
        val w = blocks[i] as Block.Words
        val t = w.value.text
        val at = w.value.selection.min.coerceIn(0, t.length)
        val before = t.substring(0, at).removeSuffix("\n")
        val after = t.substring(at).removePrefix("\n")
        blocks[i] = Block.Words(TextFieldValue(before, TextRange(before.length)))
        blocks.add(i + 1, Block.Photo(id))
        blocks.add(i + 2, Block.Words(TextFieldValue(after, TextRange(0))))
        focused = i + 2
    }

    /** Takes a photo out and joins the text that was around it. */
    fun removePhoto(id: String) {
        val i = blocks.indexOfFirst { it is Block.Photo && it.id == id }
        if (i < 0) return
        val a = (blocks.getOrNull(i - 1) as? Block.Words)?.value?.text
        val b = (blocks.getOrNull(i + 1) as? Block.Words)?.value?.text
        if (a != null && b != null) {
            val joined = listOf(a, b).filter { it.isNotEmpty() }.joinToString("\n")
            blocks.removeAt(i + 1); blocks.removeAt(i)
            blocks[i - 1] = Block.Words(TextFieldValue(joined, TextRange(a.length)))
            focused = i - 1
        } else blocks.removeAt(i)
    }

    fun format(mark: String) {
        val i = target()
        val w = blocks[i] as Block.Words
        blocks[i] = Block.Words(toggleWrap(w.value, mark))
        focused = i
    }
}

@Composable
fun WriteUpEditor(state: WriteUpState, photos: List<Attachment>, repo: Repository, style: TextStyle, onChange: () -> Unit, onPhotoTap: (Attachment) -> Unit) {
    Column {
        state.blocks.forEachIndexed { i, b ->
            when (b) {
                is Block.Words -> RichField(
                    b.value,
                    { state.blocks[i] = Block.Words(it); onChange() },
                    if (state.blocks.size == 1) "Write about the day…" else "",
                    style,
                    modifier = Modifier.onFocusChanged { if (it.isFocused) state.focused = i },
                )
                is Block.Photo -> photos.firstOrNull { it.id == b.id }?.let { p ->
                    AsyncImage(
                        model = repo.fileFor(p), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth().heightIn(max = 340.dp)
                            .clip(MaterialTheme.shapes.small).clickable { onPhotoTap(p) },
                    )
                }
            }
        }
    }
}

@Composable
fun WriteUpReader(notes: String, photos: List<Attachment>, repo: Repository, style: TextStyle) {
    Column {
        toBlocks(notes).forEach { b ->
            when (b) {
                is Block.Words -> if (b.value.text.isNotBlank()) FormattedText(b.value.text.trim('\n'), style = style)
                is Block.Photo -> photos.firstOrNull { it.id == b.id }?.let { p ->
                    AsyncImage(
                        model = repo.fileFor(p), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth().heightIn(max = 340.dp).clip(MaterialTheme.shapes.small),
                    )
                }
            }
        }
    }
}

/** A small, quiet bar while writing: bold, italic, highlight, photo. */
@Composable
fun FormatBar(onBold: () -> Unit, onItalic: () -> Unit, onHighlight: () -> Unit, onPhoto: () -> Unit, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    @Composable fun Btn(onClick: () -> Unit, content: @Composable () -> Unit) =
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
    Row(
        modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Btn(onBold) { Text("B", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ink) }
        Btn(onItalic) { Text("I", fontStyle = FontStyle.Italic, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 18.sp, color = ink) }
        Btn(onHighlight) {
            Text("A", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = ink,
                modifier = Modifier.background(HighlightColor, MaterialTheme.shapes.extraSmall).padding(horizontal = 5.dp))
        }
        Btn(onPhoto) { Icon(Icons.Rounded.Image, "Add a photo here", tint = Hues.Journal, modifier = Modifier.size(20.dp)) }
    }
}
