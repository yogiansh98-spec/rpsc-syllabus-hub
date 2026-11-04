package com.syllabushub.rpsc

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread

private const val PREFS = "syllabus_hub_preferences"
private const val KEY_BOOKMARKS = "bookmark_ids"
private const val KEY_DOWNLOADS = "tracked_downloads"

private data class SyllabusDocument(
    val id: String,
    val stage: String,
    val paper: String,
    val subject: String,
    val edition: String,
    val officialUrl: String,
    val notes: String
)

private data class Exam(
    val id: String,
    val nameHi: String,
    val nameEn: String,
    val category: String,
    val keywords: List<String>,
    val status: String,
    val documents: List<SyllabusDocument>
)

// One primary category per exam. The compact UI shows four quick tabs;
// other non-empty categories appear under More, not as empty buttons.
private data class ExamCategory(val id: String, val title: String)
private val examCategories = listOf(
    ExamCategory("teaching", "Teaching & Education"),
    ExamCategory("administrative", "RAS & Administration"),
    ExamCategory("police", "Police & Security"),
    ExamCategory("engineering", "Engineering & Technical"),
    ExamCategory("agriculture", "Agriculture & Veterinary"),
    ExamCategory("medical", "Medical & AYUSH"),
    ExamCategory("legal", "Law & Legal"),
    ExamCategory("finance", "Finance & Statistics"),
    ExamCategory("science", "IT & Science"),
    ExamCategory("other", "Other Departments")
)

// Supports older cached catalogs until their category labels are migrated.
private fun normalizedCategory(exam: Exam): String = when (exam.category) {
    "college" -> "teaching"
    "technical" -> "engineering"
    else -> exam.category.takeIf { id -> examCategories.any { it.id == id } } ?: "other"
}

private fun categoryTitle(id: String): String =
    examCategories.firstOrNull { it.id == id }?.title ?: "Other Departments"

private data class DownloadRecord(val requestId: Long, val documentId: String, val title: String)

private fun readCatalog(text: String): List<Exam> {
    val root = JSONObject(text)
    require(root.getInt("schema_version") == 2) { "Unsupported catalog" }
    val exams = root.getJSONArray("exams")
    require(exams.length() in 0..5000) { "Oversized catalog" }
    val seenExams = mutableSetOf<String>()
    val seenDocuments = mutableSetOf<String>()
    val parsed = buildList {
        for (i in 0 until exams.length()) {
            val exam = exams.getJSONObject(i)
            val id = exam.getString("id")
            require(seenExams.add(id)) { "Duplicate exam" }
            val words = mutableListOf<String>()
            for (field in listOf("aliases_hi", "aliases_en", "post", "department", "subjects")) {
                val array = exam.optJSONArray(field) ?: JSONArray()
                for (j in 0 until array.length()) words.add(array.getString(j))
            }
            val status = exam.getString("status")
            require(status == "planned" || status == "published") { "Invalid exam status" }
            val pdfs = exam.getJSONArray("documents")
            val documents = buildList {
                for (j in 0 until pdfs.length()) {
                    val doc = pdfs.getJSONObject(j)
                    val documentId = doc.getString("id")
                    require(seenDocuments.add(documentId)) { "Duplicate document" }
                    require(doc.getString("edition_status") == "current_verified") { "Unverified syllabus" }
                    require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(doc.getString("verified_at"))) { "Invalid verification date" }
                    val pdfUrl = doc.getString("official_url")
                    val uri = Uri.parse(pdfUrl)
                    require(uri.scheme == "https" && uri.host == "rpsc.rajasthan.gov.in" &&
                        uri.path.orEmpty().startsWith("/Static/Syllabus/", ignoreCase = true) &&
                        uri.path.orEmpty().endsWith(".pdf", ignoreCase = true)) { "Non-official PDF URL" }
                    add(SyllabusDocument(
                        documentId, doc.optString("stage"), doc.optString("paper"),
                        doc.optString("subject"), doc.getString("edition_label"), pdfUrl,
                        doc.optString("notes")
                    ))
                }
            }
            require(status != "published" || documents.isNotEmpty()) { "Published exam has no verified PDF" }
            require(status != "planned" || documents.isEmpty()) { "Unpublished exam has a PDF" }
            add(Exam(id, exam.getString("name_hi"), exam.getString("name_en"),
                exam.getString("category"), words, status, documents))
        }
    }
    return parsed
}

private class CatalogStore(private val context: Context) {
    private val cached = File(context.filesDir, "catalog_cache.json")
    fun load(): List<Exam> {
        val bundled = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
        val cachedText = runCatching { cached.readText() }.getOrNull()
        if (cachedText != null) {
            runCatching { readCatalog(cachedText) }.getOrNull()?.let { return it }
        }
        return readCatalog(bundled)
    }
    fun refresh(onSuccess: (List<Exam>) -> Unit) {
        val url = BuildConfig.CATALOG_URL
        if (url.isBlank() || !url.startsWith("https://")) return
        thread(name = "syllabus-catalog-refresh") {
            val fresh = runCatching {
                val connection = URL(url).openConnection() as HttpsURLConnection
                try {
                    connection.connectTimeout = 10000
                    connection.readTimeout = 12000
                    connection.instanceFollowRedirects = false
                    require(connection.responseCode == 200) { "Bad catalog status" }
                    val bytes = connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (output.size() <= 2_000_000) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    require(bytes.size <= 2_000_000) { "Catalog too big" }
                    val json = bytes.toString(Charsets.UTF_8)
                    val exams = readCatalog(json) // validate BEFORE replacing last-good cache
                    val tmp = File(context.filesDir, "catalog_cache.tmp")
                    tmp.writeText(json)
                    if (!tmp.renameTo(cached)) { tmp.copyTo(cached, overwrite = true); tmp.delete() }
                    exams
                } finally { connection.disconnect() }
            }.getOrNull()
            if (fresh != null) (context as? ComponentActivity)?.runOnUiThread { onSuccess(fresh) }
        }
    }
}

private fun getBookmarks(context: Context): Set<String> =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getStringSet(KEY_BOOKMARKS, emptySet())?.toSet().orEmpty()

private fun putBookmarks(context: Context, ids: Set<String>) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putStringSet(KEY_BOOKMARKS, ids.toSet()).apply()
}

private fun getDownloads(context: Context): List<DownloadRecord> = runCatching {
    val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_DOWNLOADS, "[]") ?: "[]"
    val array = JSONArray(text)
    buildList {
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            add(DownloadRecord(item.getLong("id"), item.getString("document_id"), item.getString("title")))
        }
    }
}.getOrDefault(emptyList())

private fun putDownloads(context: Context, records: List<DownloadRecord>) {
    val json = JSONArray()
    records.forEach {
        json.put(JSONObject().put("id", it.requestId).put("document_id", it.documentId).put("title", it.title))
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString(KEY_DOWNLOADS, json.toString()).apply()
}

private fun openOfficialPdf(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "PDF खोलने के लिए Browser उपलब्ध नहीं है", Toast.LENGTH_LONG).show() }
}

private fun startDownload(context: Context, doc: SyllabusDocument): DownloadRecord? = runCatching {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val request = DownloadManager.Request(Uri.parse(doc.officialUrl))
        .setTitle("RPSC • ${doc.subject.ifBlank { doc.paper }}")
        .setDescription("Official syllabus PDF")
        .setMimeType("application/pdf")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "${doc.id}.pdf")
    val id = manager.enqueue(request)
    DownloadRecord(id, doc.id, "${doc.subject.ifBlank { doc.paper }} — ${doc.edition}")
}.onFailure {
    Toast.makeText(context, "डाउनलोड शुरू नहीं हुआ: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
}.getOrNull()

private data class LocalDownload(val label: String, val complete: Boolean, val uri: Uri?)
private fun queryDownload(context: Context, record: DownloadRecord): LocalDownload {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    return runCatching {
        manager.query(DownloadManager.Query().setFilterById(record.requestId)).use { cursor ->
            if (!cursor.moveToFirst()) return@use LocalDownload("File missing", false, null)
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val uri = manager.getUriForDownloadedFile(record.requestId)
                    LocalDownload(if (uri == null) "File missing" else "Downloaded", uri != null, uri)
                }
                DownloadManager.STATUS_FAILED -> LocalDownload("Failed • retry", false, null)
                DownloadManager.STATUS_RUNNING -> LocalDownload("Downloading…", false, null)
                DownloadManager.STATUS_PAUSED -> LocalDownload("Paused", false, null)
                else -> LocalDownload("Pending", false, null)
            }
        }
    }.getOrDefault(LocalDownload("Unavailable", false, null))
}

private fun openDownloadedPdf(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try { context.startActivity(intent) }
    catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "PDF Reader ऐप की जरूरत है", Toast.LENGTH_LONG).show()
    } catch (_: Exception) {
        Toast.makeText(context, "PDF नहीं खुला, डाउनलोड दोबारा जाँचें", Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = CatalogStore(this)
        setContent {
            var exams by remember { mutableStateOf(runCatching { store.load() }.getOrDefault(emptyList())) }
            var bookmarks by remember { mutableStateOf(getBookmarks(this)) }
            var downloads by remember { mutableStateOf(getDownloads(this)) }
            var screen by rememberSaveable { mutableStateOf("home") }
            var selectedExam by rememberSaveable { mutableStateOf("") }
            var search by rememberSaveable { mutableStateOf("") }
            var category by rememberSaveable { mutableStateOf("all") }
            var downloadRefresh by remember { mutableIntStateOf(0) }

            LaunchedEffect(Unit) { store.refresh { newExams -> exams = newExams } }
            BackHandler(enabled = screen == "detail") { screen = "home" }

            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF225C51),
                secondary = Color(0xFF54645F),
                background = Color(0xFFFAFBF9),
                surface = Color.White
            )) {
                Scaffold(
                    topBar = {
                        TopAppBar(title = {
                            Column {
                                Text("Syllabus Hub", fontWeight = FontWeight.SemiBold)
                                Text("Independent syllabus reference", style = MaterialTheme.typography.labelSmall)
                            }
                        })
                    },
                    bottomBar = {
                        NavigationBar {
                            listOf(Triple("home", "Home", Icons.Filled.Home),
                                Triple("bookmarks", "Saved", Icons.Filled.Bookmark),
                                Triple("downloads", "Downloads", Icons.Filled.Download)).forEach { tab ->
                                NavigationBarItem(
                                    selected = screen == tab.first || (screen == "detail" && tab.first == "home"),
                                    onClick = { screen = tab.first; if (tab.first == "downloads") downloadRefresh++ },
                                    label = { Text(tab.second) },
                                    icon = { Icon(tab.third, contentDescription = tab.second) }
                                )
                            }
                        }
                    }
                ) { padding ->
                    Column(Modifier.padding(padding).fillMaxSize()) {
                        when (screen) {
                            "home" -> HomeScreen(
                                exams = exams, query = search, onQuery = { search = it; if (it.isNotBlank()) category = "all" },
                                category = category, onCategory = { category = it },
                                onSelect = { selectedExam = it; screen = "detail" },
                                bookmarks = bookmarks
                            )
                            "detail" -> {
                                val selected = exams.firstOrNull { it.id == selectedExam }
                                if (selected == null) {
                                    Text("Exam not available", modifier = Modifier.padding(20.dp))
                                } else {
                                    ExamScreen(selected, bookmarks.contains(selected.id),
                                        onBack = { screen = "home" },
                                        onBookmark = {
                                            bookmarks = if (selected.id in bookmarks) bookmarks - selected.id else bookmarks + selected.id
                                            putBookmarks(this@MainActivity, bookmarks)
                                        },
                                        onOpen = { openOfficialPdf(this@MainActivity, it.officialUrl) },
                                        onDownload = { doc ->
                                            val existing = downloads.filter { it.documentId == doc.id }
                                            existing.forEach { record ->
                                                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).remove(record.requestId)
                                            }
                                            val made = startDownload(this@MainActivity, doc)
                                            if (made != null) {
                                                downloads = downloads.filterNot { it.documentId == doc.id } + made
                                                putDownloads(this@MainActivity, downloads)
                                                Toast.makeText(this@MainActivity, "डाउनलोड शुरू हुआ", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                            "bookmarks" -> SavedScreen(exams.filter { it.id in bookmarks }) {
                                selectedExam = it; screen = "detail"
                            }
                            "downloads" -> key(downloadRefresh) {
                                DownloadsScreen(downloads,
                                    onRefresh = { downloadRefresh++ },
                                    onOpen = { openDownloadedPdf(this@MainActivity, it) },
                                    onRetry = { record ->
                                        val doc = exams.flatMap { it.documents }.firstOrNull { it.id == record.documentId }
                                        if (doc == null) {
                                            Toast.makeText(this@MainActivity, "PDF अभी catalog में उपलब्ध नहीं है", Toast.LENGTH_LONG).show()
                                        } else {
                                            val made = startDownload(this@MainActivity, doc)
                                            if (made != null) {
                                                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).remove(record.requestId)
                                                downloads = downloads.map { if (it.requestId == record.requestId) made else it }
                                                putDownloads(this@MainActivity, downloads)
                                                downloadRefresh++
                                            }
                                        }
                                    },
                                    onRemove = { record ->
                                        (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).remove(record.requestId)
                                        downloads = downloads.filterNot { it.requestId == record.requestId }
                                        putDownloads(this@MainActivity, downloads)
                                        downloadRefresh++
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    exams: List<Exam>, query: String, onQuery: (String) -> Unit,
    category: String, onCategory: (String) -> Unit,
    onSelect: (String) -> Unit, bookmarks: Set<String>
) {
    val quickTabs = listOf("all" to "All", "teaching" to "Teaching", "administrative" to "RAS", "more" to "More")
    val chosenQuickTab = if (category in listOf("all", "teaching", "administrative")) category else "more"
    val visible = remember(exams, query, category) {
        val q = query.trim().lowercase()
        exams.filter { exam ->
            val categoryOk = category == "all" || category == "more" || normalizedCategory(exam) == category
            val terms = listOf(exam.nameHi, exam.nameEn, categoryTitle(normalizedCategory(exam))) + exam.keywords
            categoryOk && (q.isEmpty() || terms.any { it.lowercase().contains(q) })
        }
    }
    val grouped = remember(visible) { visible.groupBy { normalizedCategory(it) } }
    val moreCategories = remember(exams) {
        examCategories.filter { cat ->
            cat.id !in listOf("teaching", "administrative") && exams.any { normalizedCategory(it) == cat.id }
        }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = onQuery,
            placeholder = { Text("Search exams, posts, subjects...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickTabs.forEach { (id, label) ->
                val selected = chosenQuickTab == id
                if (selected) {
                    Button(
                        onClick = { onCategory(id) }, modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(0.dp)
                    ) { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                } else {
                    OutlinedButton(
                        onClick = { onCategory(id) }, modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(0.dp)
                    ) { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (category == "more") {
                item {
                    Text("More categories", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
                }
                if (moreCategories.isEmpty()) {
                    item { Text("नई परीक्षाएँ जुड़ने पर दूसरी Categories यहाँ दिखेंगी।", style = MaterialTheme.typography.bodySmall) }
                }
                items(moreCategories, key = { "category_${it.id}" }) { cat ->
                    val count = exams.count { normalizedCategory(it) == cat.id }
                    Surface(
                        onClick = { onCategory(cat.id) }, shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(cat.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            } else {
                if (category !in listOf("all", "teaching", "administrative")) {
                    item {
                        TextButton(onClick = { onCategory("more") }, contentPadding = PaddingValues(0.dp)) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Categories", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (visible.isEmpty()) {
                    item { Text("कोई परीक्षा नहीं मिली। दूसरा नाम आज़माएँ।", modifier = Modifier.padding(vertical = 18.dp)) }
                }
                examCategories.forEach { cat ->
                    val section = grouped[cat.id].orEmpty()
                    if (section.isNotEmpty()) {
                        item(key = "heading_${cat.id}") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cat.title, modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("${section.size} exams", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        items(section, key = { it.id }) { exam ->
                            ExamListRow(exam = exam, bookmarked = exam.id in bookmarks, onClick = { onSelect(exam.id) })
                        }
                    }
                }
            }
            item { IndependenceNotice() }
        }
    }
}

@Composable
private fun ExamListRow(exam: Exam, bookmarked: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.MenuBook, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(exam.nameHi, fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(
                    if (exam.status == "planned") "Syllabus जल्द जोड़ा जाएगा" else "${exam.documents.size} verified PDF",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall, maxLines = 1
                )
            }
            if (bookmarked) {
                Icon(Icons.Filled.Bookmark, contentDescription = "Bookmarked",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = "Open exam", modifier = Modifier.size(18.dp))
        }
    }
}

private fun paperStageRank(stage: String): Int = when (stage) {
    "Prelims" -> 0
    "Written" -> 1
    "Mains" -> 2
    else -> 3
}

private fun paperLabelRank(paper: String): Int = when (paper) {
    "Paper I" -> 0
    "Paper II" -> 1
    "Paper III" -> 2
    "Paper IV" -> 3
    else -> 4
}

@Composable
private fun ExamScreen(
    exam: Exam, bookmarked: Boolean, onBack: () -> Unit,
    onBookmark: () -> Unit, onOpen: (SyllabusDocument) -> Unit,
    onDownload: (SyllabusDocument) -> Unit
) {
    var subjectQuery by rememberSaveable(exam.id) { mutableStateOf("") }
    val ordered = remember(exam, subjectQuery) {
        exam.documents.filter { doc ->
            val q = subjectQuery.trim().lowercase()
            q.isEmpty() || listOf(doc.subject, doc.paper, doc.stage, doc.edition)
                .any { it.lowercase().contains(q) }
        }.sortedWith(compareBy<SyllabusDocument>(
            { paperStageRank(it.stage) }, { paperLabelRank(it.paper) }, { it.subject.lowercase() }
        ))
    }
    val sections = remember(ordered) { ordered.groupBy { "${it.stage} • ${it.paper}" } }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp)); Text("All Exams")
            }
            Text(exam.nameHi, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(exam.nameEn, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary)
            TextButton(onClick = onBookmark) {
                Icon(if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = null)
                Spacer(Modifier.width(8.dp)); Text(if (bookmarked) "Saved in Bookmarks" else "Save Exam")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = subjectQuery, onValueChange = { subjectQuery = it },
                placeholder = { Text("इस परीक्षा में Subject / Paper खोजें") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text("${ordered.size} verified official syllabus PDF", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Text("केवल नवीनतम लागू, सत्यापित official syllabus ही प्रकाशित होंगे।",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        if (ordered.isEmpty()) {
            item {
                ElevatedCard {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.HourglassEmpty, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text(if (exam.status == "planned") "इस परीक्षा का syllabus अभी जोड़ा जा रहा है" else "इस खोज के लिए syllabus नहीं मिला",
                            fontWeight = FontWeight.SemiBold)
                        Text("Verified PDF जोड़ने के बाद यही स्क्रीन Paper और Subject के अनुसार अपने आप अपडेट हो जाएगी।",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
        sections.forEach { (sectionTitle, docs) ->
            item(key = "section_$sectionTitle") {
                Text(sectionTitle, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp))
            }
            items(docs, key = { it.id }) { doc ->
                OutlinedCard(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(doc.subject, fontWeight = FontWeight.SemiBold)
                        Text(doc.edition, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onOpen(doc) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp)); Text("Open", maxLines = 1)
                            }
                            OutlinedButton(onClick = { onDownload(doc) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp)); Text("Download", maxLines = 1)
                            }
                        }
                        Text("Source: rpsc.rajasthan.gov.in", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item { IndependenceNotice() }
    }
}

@Composable
private fun SavedScreen(saved: List<Exam>, onSelect: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Bookmarks", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Bookmark saves an exam shortcut, not its PDF.", style = MaterialTheme.typography.bodySmall)
        }
        if (saved.isEmpty()) item { Text("No saved exams yet. Open an exam and tap Save.", modifier = Modifier.padding(14.dp)) }
        items(saved, key = { it.id }) { exam ->
            OutlinedCard(onClick = { onSelect(exam.id) }) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bookmark, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(exam.nameHi, Modifier.weight(1f))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun DownloadsScreen(
    records: List<DownloadRecord>, onRefresh: () -> Unit,
    onOpen: (Uri) -> Unit, onRetry: (DownloadRecord) -> Unit,
    onRemove: (DownloadRecord) -> Unit
) {
    val context = LocalContext.current
    var removing by remember { mutableStateOf<DownloadRecord?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Downloads", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh download status") }
            }
            Text("Only PDFs downloaded from this app appear here.", style = MaterialTheme.typography.bodySmall)
        }
        if (records.isEmpty()) item { Text("No downloaded PDFs yet.", modifier = Modifier.padding(12.dp)) }
        items(records, key = { it.requestId }) { record ->
            val status = queryDownload(context, record)
            OutlinedCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(record.title, fontWeight = FontWeight.SemiBold)
                    Text(status.label, style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(enabled = status.complete, onClick = { status.uri?.let(onOpen) }) { Text("Open offline") }
                        if (status.label.startsWith("Failed")) {
                            OutlinedButton(onClick = { onRetry(record) }) { Text("Retry") }
                        }
                        OutlinedButton(onClick = { removing = record }) { Text("Remove") }
                    }
                }
            }
        }
    }
    if (removing != null) AlertDialog(
        onDismissRequest = { removing = null },
        title = { Text("Remove download?") },
        text = { Text("This removes the tracked download and asks Android to delete its downloaded file.") },
        confirmButton = { TextButton(onClick = { removing?.let(onRemove); removing = null }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } }
    )
}

@Composable
private fun IndependenceNotice() {
    Text("स्वतंत्र शैक्षणिक ऐप। यह RPSC या राजस्थान सरकार का आधिकारिक ऐप नहीं है। किसी भी सूचना के लिए RPSC की मूल PDF देखें।",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(vertical = 14.dp))
}
