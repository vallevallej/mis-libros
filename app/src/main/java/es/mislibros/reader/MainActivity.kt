package es.mislibros.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import java.io.File
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.jsoup.Jsoup
import java.util.zip.ZipInputStream
import java.io.ByteArrayInputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 4102)
        }
        setContent {
            MisLibrosScreen(
                onSpeak = { text, rate ->
                    lifecycleScope.launch {
                        // Avoid Android Binder's small transaction limit for full novels.
                        val path = withContext(Dispatchers.IO) {
                            File(cacheDir, "current_reading.txt").apply { writeText(text, Charsets.UTF_8) }.absolutePath
                        }
                        val intent = Intent(this@MainActivity, ReadingService::class.java).apply {
                            action = ReadingService.ACTION_PLAY
                            putExtra(ReadingService.EXTRA_FILE_PATH, path)
                            putExtra(ReadingService.EXTRA_RATE, rate)
                        }
                        androidx.core.content.ContextCompat.startForegroundService(this@MainActivity, intent)
                    }
                },
                onStop = {
                    startService(Intent(this, ReadingService::class.java).setAction(ReadingService.ACTION_STOP))
                },
                onVoiceSettings = ::openTtsSettings,
                onPause = {
                    startService(Intent(this, ReadingService::class.java).setAction(ReadingService.ACTION_PAUSE))
                },
                onResume = {
                    startService(Intent(this, ReadingService::class.java).setAction(ReadingService.ACTION_RESUME))
                }
            )
        }
    }

    private fun openTtsSettings() {
        val intent = Intent("com.android.settings.TTS_SETTINGS")
        startActivity(if (intent.resolveActivity(packageManager) != null) intent else Intent(Settings.ACTION_SETTINGS))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MisLibrosScreen(
    onSpeak: (String, Float) -> Unit,
    onStop: () -> Unit,
    onVoiceSettings: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("Todavía no has abierto ningún libro") }
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Importa un libro para empezar") }
    var speed by remember { mutableFloatStateOf(1f) }
    var loading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            loading = true
            status = "Leyendo documento…"
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            kotlinx.coroutines.MainScope().launch {
                val result = withContext(Dispatchers.IO) { readDocument(context, uri) }
                result.onSuccess { (name, content) -> title = name; text = content; status = "Libro preparado · ${content.length} caracteres" }
                    .onFailure { status = "No se pudo abrir el documento: ${it.message}" }
                loading = false
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Mis libros", fontWeight = FontWeight.Bold) }, actions = {
        TextButton(onClick = onVoiceSettings) { Text("Voces") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Tu biblioteca", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Abre una novela y escúchala con la voz instalada en tu móvil.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { picker.launch(arrayOf("application/pdf", "application/epub+zip", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth(), enabled = !loading) {
                Text(if (loading) "Cargando…" else "＋  Abrir libro")
            }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (text.isNotBlank()) {
                        Text("Velocidad: ${"%.1f".format(speed)}x")
                        Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.7f..1.4f)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onSpeak(text, speed) }, modifier = Modifier.weight(1f)) { Text("▶ Escuchar") }
                                OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) { Text("Pausar") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onResume, modifier = Modifier.weight(1f)) { Text("Continuar") }
                                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Detener") }
                            }
                        }
                        Text("Vista previa", fontWeight = FontWeight.SemiBold)
                        Text(text.take(1200), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("Formatos de esta versión: PDF con texto, EPUB y TXT.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text("La voz utilizada depende del motor de Texto a voz seleccionado en los ajustes de Android. Puedes cambiarlo desde «Voces».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private suspend fun readDocument(context: android.content.Context, uri: Uri): Result<Pair<String, String>> = runCatching {
    val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else "Mi libro"
    } ?: "Mi libro"
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("No se pudo leer el archivo")
    val lower = name.lowercase()
    val content = when {
        lower.endsWith(".pdf") -> PDDocument.load(bytes).use { PDFTextStripper().getText(it) }
        lower.endsWith(".epub") || bytes.take(2).toByteArray().decodeToString() == "PK" -> extractEpubText(bytes)
        else -> bytes.toString(Charsets.UTF_8)
    }
    require(content.isNotBlank()) { "El documento no contiene texto seleccionable. Los PDF escaneados necesitan OCR." }
    name to content
}

private fun extractEpubText(bytes: ByteArray): String {
    // Keep the EPUB entries in a map so the package document can define the
    // actual reading order through its manifest and spine.
    val entries = linkedMapOf<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
            zip.closeEntry()
        }
    }

    fun normalizeArchivePath(path: String): String {
        val parts = mutableListOf<String>()
        path.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += part
            }
        }
        return parts.joinToString("/")
    }

    val containerBytes = entries["META-INF/container.xml"]
    val opfPath = containerBytes?.let { container ->
        org.jsoup.Jsoup.parse(container.toString(Charsets.UTF_8), "", org.jsoup.parser.Parser.xmlParser())
            .getElementsByTag("rootfile").firstOrNull()?.attr("full-path")
    }.orEmpty()

    val orderedPaths = if (opfPath.isNotBlank()) {
        val opfBytes = entries[opfPath]
        if (opfBytes != null) {
            val opf = org.jsoup.Jsoup.parse(opfBytes.toString(Charsets.UTF_8), "", org.jsoup.parser.Parser.xmlParser())
            val manifest = opf.getElementsByTag("item").associate { item ->
                item.attr("id") to item.attr("href")
            }
            val opfDirectory = opfPath.substringBeforeLast('/', "")
            opf.getElementsByTag("itemref").mapNotNull { itemRef ->
                val href = manifest[itemRef.attr("idref")].orEmpty()
                if (href.isBlank()) return@mapNotNull null
                val decodedHref = runCatching {
                    java.net.URLDecoder.decode(href.substringBefore('#').substringBefore('?'), "UTF-8")
                }.getOrDefault(href.substringBefore('#').substringBefore('?'))
                normalizeArchivePath(if (opfDirectory.isBlank()) decodedHref else "$opfDirectory/$decodedHref")
            }.filter { entries.containsKey(it) }
        } else emptyList()
    } else emptyList()

    // Some malformed EPUBs omit a usable package document. Retain a
    // deterministic fallback rather than failing to open those books.
    val htmlPaths = if (orderedPaths.isNotEmpty()) orderedPaths else entries.keys
        .filter { it.endsWith(".xhtml", true) || it.endsWith(".html", true) || it.endsWith(".htm", true) }
        .sorted()

    return htmlPaths.joinToString("\n\n") { path ->
        Jsoup.parse(entries.getValue(path).toString(Charsets.UTF_8)).text()
    }
}
