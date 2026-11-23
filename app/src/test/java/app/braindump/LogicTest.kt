package app.braindump

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.braindump.data.LifeThread
import app.braindump.data.ThreadNote
import app.braindump.ui.continueLists
import app.braindump.ui.dueThreads
import org.junit.Assert.assertEquals
import org.junit.Test

class LogicTest {
    private fun type(before: String, ch: Char): TextFieldValue {
        val old = TextFieldValue(before, TextRange(before.length))
        val new = TextFieldValue(before + ch, TextRange(before.length + 1))
        return continueLists(old, new)
    }

    @Test fun dashBecomesBullet() = assertEquals("•", type("-", ' ').text.trim())
    @Test fun nestedDashKeepsIndent() = assertEquals("  • ", type("  -", ' ').text)
    @Test fun dashMidLineStaysDash() = assertEquals("a - ", type("a -", ' ').text)
    @Test fun bulletContinues() = assertEquals("• one\n• ", type("• one", '\n').text)
    @Test fun numberCounts() = assertEquals("1. one\n2. ", type("1. one", '\n').text)
    @Test fun letterCounts() = assertEquals("a) one\nb) ", type("a) one", '\n').text)
    @Test fun checkboxContinuesUnchecked() = assertEquals("[x] done\n[ ] ", type("[x] done", '\n').text)
    @Test fun emptyItemEndsList() = assertEquals("• one\n", type("• one\n• ", '\n').text)
    @Test fun plainLineUntouched() = assertEquals("hello\n", type("hello", '\n').text)
    @Test fun cursorAfterNewBullet() = assertEquals(TextRange(8), type("• one", '\n').selection)

    private val day = 86_400_000L
    @Test fun dueAfterInterval() {
        val now = System.currentTimeMillis()
        val t = LifeThread(title = "x", expectation = "", revisitDays = 30, createdAt = now - 40 * day)
        assertEquals(1, dueThreads(listOf(t), emptyList()).size)
        val fresh = ThreadNote(threadId = t.id, text = "now", mood = "", at = now - 2 * day)
        assertEquals(0, dueThreads(listOf(t), listOf(fresh)).size)
        assertEquals(0, dueThreads(listOf(t.copy(revisitDays = 0)), emptyList()).size)
    }
}

class WriteUpTest {
    @Test fun roundTrip() {
        val s = "before\n[[photo:abc-1]]\nafter"
        assertEquals(s, app.braindump.ui.fromBlocks(app.braindump.ui.toBlocks(s)))
        assertEquals(listOf("abc-1"), app.braindump.ui.photoIds(s))
    }

    @Test fun photoGoesWhereTheCursorIs() {
        val w = app.braindump.ui.WriteUpState("one two")
        val b = w.blocks[0] as app.braindump.ui.Block.Words
        w.blocks[0] = app.braindump.ui.Block.Words(b.value.copy(selection = TextRange(3)))
        w.focused = 0
        w.insertPhoto("p1")
        assertEquals("one\n[[photo:p1]]\n two", w.text())
        w.removePhoto("p1")
        assertEquals("one\n two", w.text())
    }

    @Test fun photoAtEndThenTextBelow() {
        val w = app.braindump.ui.WriteUpState("hello")
        w.insertPhoto("p1")
        val after = w.blocks[2] as app.braindump.ui.Block.Words
        w.blocks[2] = app.braindump.ui.Block.Words(after.value.copy(text = "below"))
        assertEquals("hello\n[[photo:p1]]\nbelow", w.text())
    }

    @Test fun boldWrapsAndUnwraps() {
        val v = TextFieldValue("make this bold", TextRange(5, 9))
        val on = app.braindump.ui.toggleWrap(v, "**")
        assertEquals("make **this** bold", on.text)
        assertEquals("make this bold", app.braindump.ui.toggleWrap(on, "**").text)
    }

    @Test fun readingHidesMarkers() {
        val s = app.braindump.ui.styled("a **b** _c_ ==d== \"e\"", androidx.compose.ui.graphics.Color.Black)
        assertEquals("a b c d \"e\"", s.text)
    }

    @Test fun snakeCaseIsNotItalic() =
        assertEquals("my_file_name", app.braindump.ui.styled("my_file_name", androidx.compose.ui.graphics.Color.Black).text)

    @Test fun plainTextDropsPhotos() = assertEquals("hi\nthere", app.braindump.ui.plainText("hi\n[[photo:x]]\nthere"))
}
