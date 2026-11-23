package app.braindump.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Packs your writing into one .zip: readable Markdown (one file per day, one per thread),
 * the photos, and a full braindump.json so it can be imported again later.
 */
object Exporter {
    private val longDate = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")
    private val shortDate = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val time = DateTimeFormatter.ofPattern("h:mm a")
    private fun local(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

    fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(50).ifEmpty { "untitled" }

    suspend fun export(context: Context, journal: Boolean, threads: Boolean): File = withContext(Dispatchers.IO) {
        val repo = Repository.get(context)
        val dir = File(context.cacheDir, "exports").apply { deleteRecursively(); mkdirs() }
        val what = when { journal && threads -> "all"; journal -> "journal"; else -> "threads" }
        val out = File(dir, "BrainDump-$what-${LocalDate.now()}.zip")
        val json = JSONObject().put("app", "BrainDump").put("version", 1).put("exportedAt", System.currentTimeMillis())

        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            fun put(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
            fun putFile(name: String, f: File) { if (f.exists()) { zip.putNextEntry(ZipEntry(name)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry() } }

            if (journal) {
                val days = repo.daysBetween("0000-01-01", "9999-12-31").first()
                val entries = repo.entriesBetween("0000-01-01", "9999-12-31").first()
                val photos = repo.allAttachments()
                val entryIds = entries.associateBy { it.id }
                val dates = (days.filter { it.notes.isNotBlank() || it.title.isNotBlank() }.map { it.date } + entries.map { it.date } +
                    photos.filter { it.ownerType == "day" }.map { it.ownerId }).toSortedSet()
                dates.forEach { date ->
                    val day = days.firstOrNull { it.date == date }
                    val md = StringBuilder()
                    md.append("# ").append(day?.title?.ifBlank { null } ?: LocalDate.parse(date).format(longDate)).append("\n\n")
                    md.append("_").append(LocalDate.parse(date).format(longDate)).append("_\n\n")
                    val dayPhotos = photos.filter { it.ownerType == "day" && it.ownerId == date }
                    val placed = mutableSetOf<String>()
                    if (!day?.notes.isNullOrBlank()) {
                        // Photos placed inside the writing stay where you put them.
                        val body = day!!.notes.trim().lines().joinToString("\n") { line ->
                            val id = Regex("^\\[\\[photo:([A-Za-z0-9-]+)]]$").matchEntire(line.trim())?.groupValues?.get(1)
                            val a = id?.let { i -> dayPhotos.firstOrNull { it.id == i } }
                            if (a != null) { placed += a.id; "\n![](../photos/${File(a.filePath).name})\n" } else if (id != null) "" else line
                        }
                        md.append(body.replace("==", "")).append("\n\n")
                    }
                    dayPhotos.filter { it.id !in placed }.forEach { md.append("![](../photos/${File(it.filePath).name})\n\n") }
                    val dayEntries = entries.filter { it.date == date }.sortedBy { it.happenedAt }
                    if (dayEntries.isNotEmpty()) {
                        md.append("## Moments\n\n")
                        dayEntries.forEach { e ->
                            md.append("- **").append(local(e.happenedAt).format(time)).append("** ").append(e.text.trim()).append("\n")
                            if (e.details.isNotBlank()) e.details.trim().lines().forEach { md.append("  ").append(it).append("\n") }
                            photos.filter { it.ownerType == "entry" && it.ownerId == e.id }.forEach { md.append("  ![](../photos/${File(it.filePath).name})\n") }
                        }
                    }
                    put("journal/$date.md", md.toString())
                }
                val mine = photos.filter { (it.ownerType == "day") || (it.ownerType == "entry" && it.ownerId in entryIds) }
                mine.forEach { putFile("photos/${File(it.filePath).name}", repo.fileFor(it)) }
                json.put("days", JSONArray(days.map { JSONObject().put("date", it.date).put("title", it.title).put("notes", it.notes) }))
                json.put("entries", JSONArray(entries.map {
                    JSONObject().put("id", it.id).put("date", it.date).put("happenedAt", it.happenedAt).put("text", it.text).put("details", it.details).put("kind", it.kind)
                }))
                json.put("photos", JSONArray(mine.map {
                    JSONObject().put("id", it.id).put("ownerType", it.ownerType).put("ownerId", it.ownerId).put("file", "photos/${File(it.filePath).name}").put("mime", it.mime)
                }))
            }
            if (threads) {
                val all = repo.threads().first()
                val notes = repo.allThreadNotes().first()
                val used = mutableSetOf<String>()
                all.forEach { t ->
                    val mine = notes.filter { it.threadId == t.id }.sortedBy { it.at }
                    val md = StringBuilder("# ${t.title}\n\n_${if (t.major) "Big" else "Small"} decision · since ${local(t.createdAt).format(shortDate)}_\n\n")
                    mine.firstOrNull()?.let { if (it.text.isNotBlank()) md.append(it.text.trim()).append("\n\n") }
                    if (t.expectation.isNotBlank()) md.append("**Expected**\n\n").append(t.expectation.trim()).append("\n\n")
                    if (mine.size > 1) {
                        md.append("## Since then\n\n")
                        mine.drop(1).forEach { n -> md.append("### ").append(local(n.at).format(shortDate)).append("\n\n").append(n.text.trim()).append("\n\n") }
                    }
                    var name = slug(t.title); var i = 2
                    while (!used.add(name)) name = slug(t.title) + "-" + i++
                    put("threads/$name.md", md.toString())
                }
                json.put("threads", JSONArray(all.map {
                    JSONObject().put("id", it.id).put("title", it.title).put("expectation", it.expectation).put("major", it.major)
                        .put("revisitDays", it.revisitDays).put("createdAt", it.createdAt)
                }))
                json.put("threadNotes", JSONArray(notes.map { JSONObject().put("id", it.id).put("threadId", it.threadId).put("at", it.at).put("text", it.text) }))
            }
            put("braindump.json", json.toString(2))
        }
        out
    }
}
