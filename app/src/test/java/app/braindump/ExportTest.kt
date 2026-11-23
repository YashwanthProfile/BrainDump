package app.braindump

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.braindump.data.Exporter
import app.braindump.data.Repository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ExportTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun exportsWhatYouChose() = runBlocking {
        val repo = Repository.get(ctx)
        repo.addEntry("Morning run", coversFrom = null)
        repo.startThread("Taking the job abroad", "- deep work\n- freedom", "settle in by spring", major = true)
        repo.startThread("Taking the job abroad", "duplicate name", "")
        fun names(j: Boolean, t: Boolean) = runBlocking { ZipFile(Exporter.export(ctx, j, t)).use { z -> z.entries().toList().map { it.name } } }

        val both = names(true, true)
        assertTrue(both.contains("braindump.json"))
        assertTrue(both.any { it.startsWith("journal/") })
        assertTrue(both.contains("threads/taking-the-job-abroad.md"))
        assertTrue("duplicate titles get their own file", both.contains("threads/taking-the-job-abroad-2.md"))

        val threadsOnly = names(false, true)
        assertFalse(threadsOnly.any { it.startsWith("journal/") })
        assertTrue(threadsOnly.any { it.startsWith("threads/") })

        val journalOnly = names(true, false)
        assertFalse(journalOnly.any { it.startsWith("threads/") })
        assertTrue(journalOnly.any { it.startsWith("journal/") })
    }
}
