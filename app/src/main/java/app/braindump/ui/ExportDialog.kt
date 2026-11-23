package app.braindump.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.braindump.data.Exporter
import kotlinx.coroutines.launch
import java.io.File

/** Turns a saved document's address into something a person can read, e.g. "Internal storage › Download › file.zip". */
fun readableLocation(uri: Uri): String {
    val path = Uri.decode(uri.lastPathSegment ?: return uri.toString())
    return when {
        path.startsWith("primary:") -> "Internal storage › " + path.removePrefix("primary:").replace("/", " › ")
        path.startsWith("raw:") -> path.removePrefix("raw:")
        ':' in path -> "SD card › " + path.substringAfter(':').replace("/", " › ")
        else -> path
    }
}

@Composable
fun ExportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf("both") }
    var busy by remember { mutableStateOf(false) }
    var savedTo by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var made by remember { mutableStateOf<File?>(null) }

    suspend fun build(): File? = try {
        busy = true; error = null
        Exporter.export(context, journal = choice != "threads", threads = choice != "journal").also { made = it }
    } catch (e: Exception) {
        error = "Couldn't make the export: ${e.message}"; null
    } finally { busy = false }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val f = made
        if (uri != null && f != null) scope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
                }
                savedTo = readableLocation(uri)
            } catch (e: Exception) { error = "Couldn't save: ${e.message}" }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(3.dp)) {
                    listOf("journal" to "Journal", "threads" to "Threads", "both" to "Both").forEach { (k, l) ->
                        val on = choice == k
                        Box(
                            Modifier.weight(1f).clip(CircleShape).background(if (on) Hues.Journal else Color.Transparent)
                                .clickable { choice = k; savedTo = null }.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(l, style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Gap(14)
                Text("One .zip with readable text files, your photos, and a full backup file.", style = QuietText)
                if (busy) { Gap(12); LinearProgressIndicator(Modifier.fillMaxWidth(), color = Hues.Journal) }
                savedTo?.let { Gap(12); Text("Saved to", style = MarkText); Gap(2); Text(it, style = MaterialTheme.typography.bodyMedium) }
                error?.let { Gap(12); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            Row {
                TextButton(enabled = !busy, onClick = {
                    scope.launch { build()?.let { save.launch(it.name) } }
                }) { Text("Save to phone") }
                TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        val f = build() ?: return@launch
                        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
                        val send = Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(send, "Share export"))
                    }
                }) { Text("Share", color = Hues.Journal) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
