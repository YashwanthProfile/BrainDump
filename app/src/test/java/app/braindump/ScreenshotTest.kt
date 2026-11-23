package app.braindump

import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.braindump.data.Dates
import app.braindump.data.Repository
import app.braindump.data.ThemeMode
import app.braindump.ui.*
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Renders the main screens with sample data so the design can be reviewed without a phone.
 * Run with: ./gradlew recordRoborazziDebug   (images land in app/build/screenshots)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun settle() {
        repeat(12) {
            Thread.sleep(80)
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
        }
    }

    private fun seed(repo: Repository) = runBlocking {
        val today = LocalDate.now()
        fun at(d: LocalDate, h: Int, m: Int = 0) = Dates.at(d.toString(), h * 60 + m)
        val lines = listOf(
            "Slow start, tea by the window while the street woke up.",
            "Finished the thing I'd been putting off all week. Felt light.",
            "Lunch with an old friend, talked for two hours.",
            "Long walk by the river, the light was golden.",
            "Read thirty pages before bed.",
        )
        // Past days, so the calendars have something to show.
        listOf(1, 2, 4, 5, 8, 9, 11, 15, 16, 19, 22, 26, 30, 33, 37, 41).forEach { back ->
            val d = today.minusDays(back.toLong())
            repo.addEntry(lines[back % lines.size], at(d, 9 + back % 8))
            repo.addEntry(lines[(back + 2) % lines.size], at(d, 18))
        }
        repo.addEntry(lines[0], at(today, 7, 40))
        repo.addEntry(lines[1], at(today, 11, 15))
        repo.addEntry(lines[2], at(today, 13, 30))
        val y = today.minusDays(1)
        repo.saveDayNotes(y.toString(), "A **quiet, good day**. I noticed I'm ==calmer== when I start without the phone. Want to keep that going this week, and maybe sleep before midnight for once. Someone once told me \"slow mornings make good days\" and I'm starting to believe it.\n\nThis week:\n1. No phone before breakfast\n2. Walk after lunch\n3. Bed by 11:30")
        repo.addEntry("Woke up at 7 without an alarm, first time in weeks.", at(y, 7, 5))
        repo.addEntry("Morning run, 4 km along the river. Legs heavy but happy.", at(y, 7, 50))
        repo.addEntry("Two hours of deep work, phone in the other room.", at(y, 10, 30))
        repo.addEntry("Cooked dinner at home and called family.", at(y, 20, 15))

        val big = app.braindump.data.LifeThread(title = "Taking the job abroad", expectation = "- Settled in by spring\n- A few real friends there\n- Still glad I went", major = true, createdAt = at(today.minusDays(120), 21))
        repo.saveThread(big)
        listOf(
            120 to "I want to see how I do somewhere new, without the safety net. Staying would be easier, but I'd always wonder.",
            60 to "Harder than I thought. The work is fine, the evenings are lonely.",
            35 to "Lowest point so far. Comparing myself to friends back home.",
            10 to "Joined a running group. It finally feels like my city too. Glad I came.",
        ).forEach { (back, text) -> repo.saveThreadNote(app.braindump.data.ThreadNote(threadId = big.id, at = at(today.minusDays(back.toLong()), 21), text = text)) }
        repo.startThread("Selling the car", "Cycling to work instead. Worth it if I still like it after the rainy season.", "")
        repo.saveDayTitle(y.toString(), "The calm morning")
        repo.saveDayTitle(today.minusDays(2).toString(), "River walk and a long talk")
        repo.saveDayTitle(today.minusDays(5).toString(), "Finally done")
        repo.saveDayTitle(today.minusDays(9).toString(), "Rainy Sunday, slow reading")
    }

    @Test
    fun screens() {
        val repo = Repository.get(ApplicationProvider.getApplicationContext())
        seed(repo)
        val yesterday = LocalDate.now().minusDays(1).toString()
        val threadId = runBlocking { repo.threads().first().first { it.title.startsWith("Taking") }.id }
        val emptyId = runBlocking { repo.startThread("Moving to a new city", "", "") }
        var mode by mutableStateOf(ThemeMode.LIGHT)
        var screen by mutableStateOf("journal")
        compose.setContent {
            BrainDumpTheme(mode) {
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    when (screen) {
                        "journal" -> JournalScreen(repo, onOpenMonth = {})
                        "month" -> MonthScreen(repo, LocalDate.now().toString().take(7), {}, {})
                        "day" -> DayScreen(repo, yesterday, {})
                        "threads" -> ThreadsScreen(repo, {}, {}, {})
                        "threads-select" -> ThreadsScreen(repo, {}, {}, {}, startSelecting = true)
                        "new-thread" -> androidx.compose.foundation.layout.Box(androidx.compose.foundation.layout.PaddingValues(top = 40.dp).let { androidx.compose.ui.Modifier.padding(it) }) { app.braindump.ui.NewThreadForm { _, _, _, _, _ -> } }
                        "thread" -> ThreadScreen(repo, threadId, {})
                        "thread-edit" -> ThreadScreen(repo, threadId, {}, startEditing = true)
                        "thread-empty" -> ThreadScreen(repo, emptyId, {})
                        "thread-empty-edit" -> ThreadScreen(repo, emptyId, {}, startEditing = true)
                        "search" -> SearchScreen(repo, {}, {}, initialQuery = "lake")
                        "day-edit" -> DayScreen(repo, yesterday, {}, startEditing = true)
                    }
                }
            }
        }
        val shots = listOf("journal", "month", "day", "day-edit", "search", "threads", "threads-select", "new-thread", "thread", "thread-edit", "thread-empty", "thread-empty-edit")
        for (m in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            for (s in shots) {
                mode = m; screen = s
                settle()
                compose.onRoot().captureRoboImage("build/screenshots/${m.name.lowercase()}-$s.png")
            }
        }
    }
}
