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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.unit.sp
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
    val officialLanguage: String,
    val translationHiUrl: String,
    val translationHiLabel: String,
    val notes: String
)

private data class Exam(
    val id: String,
    val nameHi: String,
    val nameEn: String,
    val category: String,
    val keywords: List<String>,
    val status: String,
    val coverageStatus: String,
    val coverageNote: String,
    val documents: List<SyllabusDocument>
)

// One primary category per exam. The compact UI shows four quick tabs;
// other non-empty categories appear under More, not as empty buttons.
private data class ExamCategory(val id: String, val title: String)
private val legacyExamCategories = listOf(
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

// Older cached catalogs without exam_categories continue to use these defaults.
// New categories are delivered through the online JSON without an APK rebuild.
private fun readExamCategories(text: String): List<ExamCategory> {
    val array = JSONObject(text).optJSONArray("exam_categories") ?: return legacyExamCategories
    require(array.length() in 1..60) { "Invalid exam category count" }
    val seen = mutableSetOf<String>()
    val categories = buildList {
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val id = item.getString("id")
            val title = item.getString("title").trim()
            require(Regex("[a-z0-9-]{2,40}").matches(id) &&
                id !in setOf("all", "more") && seen.add(id)) { "Invalid or duplicate exam category" }
            require(title.isNotBlank() && title.length <= 70) { "Invalid exam category title" }
            add(ExamCategory(id, title))
        }
    }
    require(categories.any { it.id == "other" }) { "Other category required" }
    return categories
}

// Supports older cached catalogs until legacy category labels are migrated.
private fun normalizedCategory(exam: Exam, categories: List<ExamCategory>): String = when (exam.category) {
    "college" -> "teaching"
    "technical" -> "engineering"
    else -> exam.category.takeIf { id -> categories.any { it.id == id } } ?: "other"
}

private fun categoryTitle(id: String, categories: List<ExamCategory>): String =
    categories.firstOrNull { it.id == id }?.title ?: "Other Departments"

private data class ExploreItem(val id: String, val title: String, val status: String, val url: String)

private fun readExplore(text: String): List<ExploreItem> {
    val items = JSONObject(text).optJSONArray("explore_categories") ?: JSONArray()
    require(items.length() <= 40) { "Too many Explore categories" }
    val ids = mutableSetOf<String>()
    return buildList {
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val id = item.getString("id")
            val title = item.getString("title")
            val status = item.getString("status")
            val url = item.optString("url")
            require(Regex("[a-z0-9-]{2,40}").matches(id) && ids.add(id)) { "Invalid Explore ID" }
            require(title.isNotBlank() && title.length <= 70) { "Invalid Explore title" }
            require(status in listOf("coming_soon", "available")) { "Invalid Explore status" }
            require(status != "available" || (url.startsWith("https://") && Uri.parse(url).host != null)) { "Explore URL required" }
            require(url.isBlank() || (url.startsWith("https://") && Uri.parse(url).host != null)) { "Invalid Explore URL" }
            add(ExploreItem(id, title, status, url))
        }
    }
}

private data class DownloadRecord(val requestId: Long, val documentId: String, val title: String)

private fun pdfKey(doc: SyllabusDocument, hindi: Boolean) =
    if (hindi) "${doc.id}::hi" else doc.id

private fun pdfUrl(doc: SyllabusDocument, hindi: Boolean) =
    if (hindi) doc.translationHiUrl else doc.officialUrl

private fun allowedTranslationUrl(url: String): Boolean {
    val uri = Uri.parse(url)
    return uri.scheme == "https" && uri.authority == "github.com" &&
        Regex("^/yogiansh98-spec/rpsc-syllabus-hub/releases/download/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+\\.pdf$")
            .matches(uri.path.orEmpty()) && uri.query == null && uri.fragment == null
}

private fun readCatalog(text: String, categories: List<ExamCategory>): List<Exam> {
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
            require(exam.getString("category") in categories.map { it.id } ||
                (exam.getString("category") == "college" && categories.any { it.id == "teaching" }) ||
                (exam.getString("category") == "technical" && categories.any { it.id == "engineering" })) {
                "Exam references an unknown category"
            }
            val status = exam.getString("status")
            require(status == "planned" || status == "published") { "Invalid exam status" }
            val coverageStatus = exam.optString("coverage_status", if (status == "published") "unknown" else "not_applicable")
            require(coverageStatus in listOf("unknown", "partial_verified", "complete_verified", "not_applicable")) { "Invalid coverage status" }
            require(status != "published" || coverageStatus != "not_applicable") { "Published exam needs coverage status" }
            val coverageNote = exam.optString("coverage_note")
            require(coverageNote.length <= 350) { "Coverage note too long" }
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
                    val hiUrl = doc.optString("translation_hi_url")
                    require(hiUrl.isBlank() || allowedTranslationUrl(hiUrl)) { "Bad independent Hindi PDF URL" }
                    val translationHiLabel = doc.optString("translation_hi_label", "Hindi PDF • Translated by Author").trim()
                    require(translationHiLabel.length in 1..90 && !translationHiLabel.contains("\\n")) { "Invalid Hindi label" }
                    val officialLanguage = doc.optString("official_language", "original")
                    require(officialLanguage in listOf("original", "en", "hi", "mixed")) { "Bad original language" }
                    add(SyllabusDocument(
                        documentId, doc.optString("stage"), doc.optString("paper"),
                        doc.optString("subject"), doc.getString("edition_label"), pdfUrl,
                        officialLanguage, hiUrl, translationHiLabel, doc.optString("notes")
                    ))
                }
            }
            require(status != "published" || documents.isNotEmpty()) { "Published exam has no verified PDF" }
            require(status != "planned" || documents.isEmpty()) { "Unpublished exam has a PDF" }
            add(Exam(id, exam.getString("name_hi"), exam.getString("name_en"),
                exam.getString("category"), words, status, coverageStatus, coverageNote, documents))
        }
    }
    return parsed
}

private data class CatalogSnapshot(
    val version: Int,
    val exams: List<Exam>,
    val categories: List<ExamCategory>,
    val explore: List<ExploreItem>
)

private fun readSnapshot(text: String): CatalogSnapshot {
    val categories = readExamCategories(text)
    val version = JSONObject(text).getInt("catalog_version")
    require(version >= 1) { "Invalid catalog version" }
    return CatalogSnapshot(version, readCatalog(text, categories), categories, readExplore(text))
}

private class CatalogStore(private val context: Context) {
    private val cached = File(context.filesDir, "catalog_cache.json")
    fun load(): CatalogSnapshot {
        val bundled = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
        val bundledSnapshot = readSnapshot(bundled)
        val cachedSnapshot = runCatching { readSnapshot(cached.readText()) }.getOrNull()
        return if (cachedSnapshot != null && cachedSnapshot.version >= bundledSnapshot.version)
            cachedSnapshot else bundledSnapshot
    }
    fun refresh(onSuccess: (CatalogSnapshot) -> Unit) {
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
                    val snapshot = readSnapshot(json) // validate all sections before saving
                    if (snapshot.version <= load().version) return@runCatching null
                    val tmp = File(context.filesDir, "catalog_cache.tmp")
                    tmp.writeText(json)
                    if (!tmp.renameTo(cached)) { tmp.copyTo(cached, overwrite = true); tmp.delete() }
                    snapshot
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

private fun startDownload(context: Context, doc: SyllabusDocument, hindi: Boolean = false): DownloadRecord? = runCatching {
    val url = pdfUrl(doc, hindi)
    require(url.isNotBlank()) { "PDF URL is missing" }
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle("${if (hindi) "Hindi Translation" else "RPSC Original"} • ${doc.subject.ifBlank { doc.paper }}")
        .setDescription(if (hindi) "Independent Hindi translation; not official RPSC Hindi publication" else "Official RPSC syllabus PDF")
        .setMimeType("application/pdf")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "${doc.id}${if (hindi) "-hi" else "-original"}.pdf")
    val id = manager.enqueue(request)
    DownloadRecord(id, pdfKey(doc, hindi),
        "${doc.subject.ifBlank { doc.paper }} — ${if (hindi) "Hindi translation" else "RPSC Original"} — ${doc.edition}")
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
    private lateinit var catalogStore: CatalogStore
    private var onCatalogUpdated: ((CatalogSnapshot) -> Unit)? = null

    override fun onResume() {
        super.onResume()
        // Refresh whenever the user returns to the app, not only on process restart.
        if (::catalogStore.isInitialized) {
            catalogStore.refresh { latest -> onCatalogUpdated?.invoke(latest) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        catalogStore = CatalogStore(this)
        setContent {
            var snapshot by remember {
                mutableStateOf(runCatching { catalogStore.load() }
                    .getOrElse { CatalogSnapshot(0, emptyList(), legacyExamCategories, emptyList()) })
            }
            val exams = snapshot.exams
            val exploreItems = snapshot.explore
            val examCategories = snapshot.categories
            var bookmarks by remember { mutableStateOf(getBookmarks(this)) }
            var downloads by remember { mutableStateOf(getDownloads(this)) }
            var screen by rememberSaveable { mutableStateOf("home") }
            var selectedExam by rememberSaveable { mutableStateOf("") }
            var search by rememberSaveable { mutableStateOf("") }
            var category by rememberSaveable { mutableStateOf("all") }
            var downloadRefresh by remember { mutableIntStateOf(0) }

            DisposableEffect(Unit) {
                onCatalogUpdated = { latest -> snapshot = latest }
                onDispose { onCatalogUpdated = null }
            }
            BackHandler(enabled = screen == "detail" || screen == "about" || (screen == "home" && category !in listOf("all", "teaching", "administrative"))) {
                when {
                    screen == "detail" || screen == "about" -> screen = "home"
                    category == "more" -> category = "all"
                    else -> category = "more"
                }
            }

            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF21645A),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFDDEEE9),
                onPrimaryContainer = Color(0xFF173B35),
                secondary = Color(0xFF566B66),
                onSecondary = Color.White,
                background = Color(0xFFF8FAF9),
                onBackground = Color(0xFF202A28),
                surface = Color.White,
                onSurface = Color(0xFF202A28),
                surfaceVariant = Color(0xFFF0F5F2),
                onSurfaceVariant = Color(0xFF596D67),
                outline = Color(0xFFC2D1CB),
                outlineVariant = Color(0xFFE4EBE8)
            )) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                            title = {
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text("Syllabus Hub", style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold)
                                    Text("RPSC Exam Syllabus",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            actions = {
                                IconButton(onClick = { screen = "about" }) {
                                    Icon(Icons.Filled.Info, contentDescription = "About this independent app")
                                }
                            }
                        )
                    },
                    bottomBar = {
                        CompactBottomBar(
                            selected = if (screen == "detail" || screen == "about") "home" else screen,
                            onSelect = { tab ->
                                screen = tab
                                if (tab == "downloads") downloadRefresh++
                            }
                        )
                    }
                ) { padding ->
                    Column(Modifier.padding(padding).fillMaxSize()) {
                        when (screen) {
                            "home" -> HomeScreen(
                                exams = exams, categories = examCategories,
                                query = search, onQuery = { search = it; if (it.isNotBlank()) category = "all" },
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
                                        onOpen = { doc, hindi -> openOfficialPdf(this@MainActivity, pdfUrl(doc, hindi)) },
                                        onDownload = { doc, hindi ->
                                            val key = pdfKey(doc, hindi)
                                            val existing = downloads.firstOrNull { it.documentId == key }
                                            val current = existing?.let { queryDownload(this@MainActivity, it) }
                                            if (current?.complete == true && current.uri != null) {
                                                openDownloadedPdf(this@MainActivity, current.uri)
                                            } else if (current?.label in listOf("Downloading…", "Paused", "Pending")) {
                                                Toast.makeText(this@MainActivity, "डाउनलोड पहले से जारी है", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val made = startDownload(this@MainActivity, doc, hindi)
                                                if (made != null) {
                                                    existing?.let { (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).remove(it.requestId) }
                                                    downloads = downloads.filterNot { it.documentId == key } + made
                                                    putDownloads(this@MainActivity, downloads)
                                                    Toast.makeText(this@MainActivity, "डाउनलोड शुरू हुआ", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                            "about" -> AboutScreen(onOpenOfficial = {
                                openOfficialPdf(this@MainActivity, "https://rpsc.rajasthan.gov.in/")
                            })
                            "explore" -> ExploreScreen(exploreItems, onOpen = { openOfficialPdf(this@MainActivity, it) })
                            "bookmarks" -> SavedScreen(exams.filter { it.id in bookmarks }) {
                                selectedExam = it; screen = "detail"
                            }
                            "downloads" -> key(downloadRefresh) {
                                DownloadsScreen(downloads,
                                    onRefresh = { downloadRefresh++ },
                                    onOpen = { openDownloadedPdf(this@MainActivity, it) },
                                    onRetry = { record ->
                                        val hindi = record.documentId.endsWith("::hi")
                                        val docId = record.documentId.removeSuffix("::hi")
                                        val doc = exams.flatMap { it.documents }.firstOrNull { it.id == docId }
                                        if (doc == null || (hindi && doc.translationHiUrl.isBlank())) {
                                            Toast.makeText(this@MainActivity, "PDF अभी catalog में उपलब्ध नहीं है", Toast.LENGTH_LONG).show()
                                        } else {
                                            val made = startDownload(this@MainActivity, doc, hindi)
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
private fun CompactBottomBar(selected: String, onSelect: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
                .height(60.dp).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                Triple("home", "Home", Icons.Filled.Home),
                Triple("bookmarks", "Saved", Icons.Filled.BookmarkBorder),
                Triple("downloads", "Downloads", Icons.Filled.FileDownload),
                Triple("explore", "Explore", Icons.Filled.Explore)
            ).forEach { tab ->
                val active = selected == tab.first
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .clickable { onSelect(tab.first) }
                        .padding(top = 6.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        tab.third, contentDescription = tab.second,
                        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        tab.second, style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    exams: List<Exam>, categories: List<ExamCategory>,
    query: String, onQuery: (String) -> Unit,
    category: String, onCategory: (String) -> Unit,
    onSelect: (String) -> Unit, bookmarks: Set<String>
) {
    val quickTabs = listOf("all" to "All", "teaching" to "Teaching", "administrative" to "RAS", "more" to "More")
    val selectedTab = if (category in listOf("all", "teaching", "administrative")) category else "more"
    val q = query.trim().lowercase()
    val visible = remember(exams, categories, query, category) {
        exams.filter { exam ->
            val categoryMatch = category == "all" || category == "more" ||
                normalizedCategory(exam, categories) == category
            val terms = listOf(exam.nameHi, exam.nameEn, categoryTitle(normalizedCategory(exam, categories), categories)) + exam.keywords
            categoryMatch && (q.isBlank() || terms.any { it.lowercase().contains(q) })
        }
    }
    val grouped = remember(visible, categories) { visible.groupBy { normalizedCategory(it, categories) } }
    val moreCategories = remember(exams, categories) {
        categories.filter { cat ->
            cat.id !in listOf("teaching", "administrative") &&
                exams.any { normalizedCategory(it, categories) == cat.id }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text("Search exams, posts, subjects",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            BasicTextField(
                                value = query, onValueChange = onQuery,
                                singleLine = true, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQuery("") }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    quickTabs.forEach { (id, label) ->
                        val active = selectedTab == id
                        Surface(
                            onClick = { onCategory(id) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface,
                            border = if (active) null else androidx.compose.foundation.BorderStroke(
                                1.dp, MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(label, style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (category == "more") {
                item {
                    Text("Categories", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 3.dp))
                    Text("Choose a category to view its exams",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 10.dp))
                }
                if (moreCategories.isEmpty()) {
                    item { CompactEmptyState("No additional categories yet", "New categories appear as exams are added.") }
                } else {
                    items(moreCategories, key = { "cat_${it.id}" }) { cat ->
                        val count = exams.count { normalizedCategory(it, categories) == cat.id }
                        Surface(onClick = { onCategory(cat.id) },
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null,
                                    modifier = Modifier.size(19.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text(cat.title, modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium)
                                Text("$count", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(7.dp))
                                Icon(Icons.Filled.ChevronRight, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            } else {
                if (category !in listOf("all", "teaching", "administrative")) {
                    item {
                        TextButton(onClick = { onCategory("more") },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = null,
                                modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("All categories", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (visible.isEmpty()) {
                    item {
                        CompactEmptyState("No matching exams", "Try a different exam name, subject or spelling.")
                    }
                }
                categories.forEach { cat ->
                    val section = grouped[cat.id].orEmpty()
                    if (section.isNotEmpty()) {
                        item(key = "heading_${cat.id}") {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(top = 14.dp, bottom = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cat.title, modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold)
                                Text("${section.size} exams",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(section, key = { it.id }) { exam ->
                            ExamListRow(
                                exam = exam, bookmarked = exam.id in bookmarks,
                                onClick = { onSelect(exam.id) }
                            )
                        }
                    }
                }

            }
        }
    }
}

@Composable
private fun ExploreScreen(items: List<ExploreItem>, onOpen: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Explore", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("More exam resources",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (items.isEmpty()) {
            Text("More categories coming soon")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items.chunked(2)) { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        pair.forEach { item ->
                            UpcomingFeatureCard(
                                item.title,
                                when (item.id) {
                                    "previous-year-papers" -> Icons.Filled.Description
                                    "exam-study-plan" -> Icons.Filled.DateRange
                                    else -> Icons.Filled.FolderOpen
                                },
                                Modifier.weight(1f),
                                status = if (item.status == "available") "Open" else "Coming Soon",
                                onClick = if (item.status == "available") ({ onOpen(item.url) }) else null
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingFeatureCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    status: String = "Coming Soon",
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().height(124.dp).padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(23.dp)
            )
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompactEmptyState(title: String, description: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(Icons.Filled.SearchOff, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExamListRow(exam: Exam, bookmarked: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(35.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MenuBook, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(exam.nameHi, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(exam.nameEn, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (bookmarked) {
                Icon(Icons.Filled.Bookmark, contentDescription = "Saved exam",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = "Open exam",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AboutScreen(onOpenOfficial: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("About Syllabus Hub", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text("Independent educational reference app",
            style = MaterialTheme.typography.titleMedium)
        Text("यह स्वतंत्र शैक्षणिक ऐप है। इसका RPSC या राजस्थान सरकार से कोई आधिकारिक संबंध नहीं है।",
            style = MaterialTheme.typography.bodyMedium)
        Text("Syllabus केवल आधिकारिक RPSC PDF से सत्यापित होने के बाद जोड़ा जाएगा।",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onOpenOfficial) {
            Icon(Icons.Filled.OpenInNew, contentDescription = null,
                modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open official RPSC website")
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
    onBookmark: () -> Unit, onOpen: (SyllabusDocument, Boolean) -> Unit,
    onDownload: (SyllabusDocument, Boolean) -> Unit
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
            if (exam.status == "published" && exam.coverageStatus != "complete_verified") {
                Text(
                    if (exam.coverageStatus == "partial_verified") "Partial syllabus coverage • ${exam.coverageNote}"
                    else "Verified PDFs are available; complete syllabus coverage is not confirmed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(6.dp))
            }
            OutlinedTextField(
                value = subjectQuery, onValueChange = { subjectQuery = it },
                placeholder = { Text("इस परीक्षा में Subject / Paper खोजें") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            if (ordered.isNotEmpty()) {
                Text("Syllabus PDFs (${ordered.size})", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }
        }
        if (ordered.isEmpty()) {
            item {
                ElevatedCard {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(if (exam.status == "planned") "Syllabus अभी उपलब्ध नहीं है" else "इस विषय का Syllabus नहीं मिला",
                            fontWeight = FontWeight.SemiBold)
                        Text(if (exam.status == "planned") "इस परीक्षा के Paper और Subject के Syllabus जल्द यहाँ मिलेंगे।" else "दूसरा Paper या Subject खोजकर देखें।",
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
                        Text(when (doc.officialLanguage) {
                            "en" -> "RPSC Original • English"
                            "hi" -> "RPSC Original • हिंदी"
                            else -> "RPSC Original"
                        }, style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onOpen(doc, false) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp)); Text("Open", maxLines = 1)
                            }
                            OutlinedButton(onClick = { onDownload(doc, false) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp)); Text("Download", maxLines = 1)
                            }
                        }
                        Text("Source: rpsc.rajasthan.gov.in", style = MaterialTheme.typography.labelSmall)
                        if (doc.translationHiUrl.isNotBlank()) {
                            HorizontalDivider()
                            Text(doc.translationHiLabel,
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onOpen(doc, true) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp)); Text("हिंदी खोलें", maxLines = 1)
                                }
                                OutlinedButton(onClick = { onDownload(doc, true) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp)); Text("डाउनलोड", maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
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

