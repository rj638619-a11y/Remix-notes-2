package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.data.db.NoteDao
import com.example.data.model.AppSettings
import com.example.data.model.NoteEntity
import com.example.util.HashUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class FolderSyncResult(
    val scanned: Int = 0,
    val newCount: Int = 0,
    val updatedCount: Int = 0,
    val dupeCount: Int = 0,
    val error: String? = null
)

class NoteRepository(
    private val noteDao: NoteDao,
    private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("glass_notes_prefs", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow = _settingsFlow.asStateFlow()

    val allNotes: Flow<List<NoteEntity>> = noteDao.getAllNotes()
    val allNoteSummaries: Flow<List<com.example.data.model.NoteSummary>> = noteDao.getAllNoteSummaries()
    val trashNotes: Flow<List<NoteEntity>> = noteDao.getTrashNotes()
    val trashNoteSummaries: Flow<List<com.example.data.model.NoteSummary>> = noteDao.getTrashNoteSummaries()
    val trashCount: Flow<Int> = noteDao.getTrashCount()

    fun getNote(id: String): Flow<NoteEntity?> = noteDao.getNoteById(id)

    suspend fun getNoteDirect(id: String): NoteEntity? = withContext(Dispatchers.IO) {
        noteDao.getNoteByIdDirect(id)
    }

    private fun loadSettings(): AppSettings {
        val isLowRam = try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            if (activityManager != null) {
                val memoryInfo = android.app.ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                val totalRamGb = memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
                totalRamGb <= 3.2 || activityManager.isLowRamDevice
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }

        return AppSettings(
            theme = prefs.getString("pref_theme", "auto") ?: "auto",
            reduceTransparency = prefs.getBoolean("pref_reduce", isLowRam),
            readingFontSize = prefs.getInt("pref_font_size", 17),
            autoSync = prefs.getBoolean("pref_auto_sync", true),
            syncFolderUri = prefs.getString("pref_sync_folder_uri", null),
            syncFolderName = prefs.getString("pref_sync_folder_name", null),
            lastSyncTime = prefs.getLong("pref_last_sync_time", 0L),
            sortOrder = prefs.getString("pref_sort_order", "edited") ?: "edited",
            viewMode = prefs.getString("pref_view_mode", "list") ?: "list",
            biometricLockEnabled = prefs.getBoolean("pref_biometric_lock", false),
            faceEnrolled = prefs.getBoolean("pref_face_enrolled", false),
            faceSignature = prefs.getString("pref_face_signature", "") ?: "",
            pinCode = prefs.getString("pref_pin_code", "") ?: "",
            appLockEnabled = prefs.getBoolean("pref_app_lock_enabled", false),
            readerMode = prefs.getString("pref_reader_mode", "html") ?: "html",
            pdfPageMode = prefs.getString("pref_pdf_page_mode", "continuous") ?: "continuous",
            pdfColorFilter = prefs.getString("pref_pdf_color_filter", "default") ?: "default",
            pdfRenderQuality = prefs.getString("pref_pdf_render_quality", "sharp") ?: "sharp",
            geminiApiKey = prefs.getString("pref_gemini_api_key", "") ?: "",
            hapticsEnabled = prefs.getBoolean("pref_haptics_enabled", true)
        )
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = withContext(Dispatchers.IO) {
        val current = _settingsFlow.value
        val updated = transform(current)
        prefs.edit()
            .putString("pref_theme", updated.theme)
            .putBoolean("pref_reduce", updated.reduceTransparency)
            .putInt("pref_font_size", updated.readingFontSize)
            .putBoolean("pref_auto_sync", updated.autoSync)
            .putString("pref_sync_folder_uri", updated.syncFolderUri)
            .putString("pref_sync_folder_name", updated.syncFolderName)
            .putLong("pref_last_sync_time", updated.lastSyncTime)
            .putString("pref_sort_order", updated.sortOrder)
            .putString("pref_view_mode", updated.viewMode)
            .putBoolean("pref_biometric_lock", updated.biometricLockEnabled)
            .putBoolean("pref_face_enrolled", updated.faceEnrolled)
            .putString("pref_face_signature", updated.faceSignature)
            .putString("pref_pin_code", updated.pinCode)
            .putBoolean("pref_app_lock_enabled", updated.appLockEnabled)
            .putString("pref_reader_mode", updated.readerMode)
            .putString("pref_pdf_page_mode", updated.pdfPageMode)
            .putString("pref_pdf_color_filter", updated.pdfColorFilter)
            .putString("pref_pdf_render_quality", updated.pdfRenderQuality)
            .putString("pref_gemini_api_key", updated.geminiApiKey)
            .putBoolean("pref_haptics_enabled", updated.hapticsEnabled)
            .apply()
        _settingsFlow.value = updated
    }

    suspend fun insertNote(note: NoteEntity) = withContext(Dispatchers.IO) {
        val hash = if (note.type == "pdf") {
            if (note.hash.isNotBlank()) note.hash else HashUtil.sha256("pdf:${note.title.lowercase().trim()}:${note.content.length}")
        } else {
            HashUtil.noteHash(note.title, note.content)
        }
        noteDao.insertNote(note.copy(hash = hash))
        com.example.widget.GlassNotesWidgetReceiver.updateAllWidgets(context)
    }

    suspend fun updateNote(note: NoteEntity) = withContext(Dispatchers.IO) {
        val hash = if (note.type == "pdf") {
            if (note.hash.isNotBlank()) note.hash else HashUtil.sha256("pdf:${note.title.lowercase().trim()}:${note.content.length}")
        } else {
            HashUtil.noteHash(note.title, note.content)
        }
        noteDao.updateNote(note.copy(hash = hash, updatedAt = System.currentTimeMillis()))
        com.example.widget.GlassNotesWidgetReceiver.updateAllWidgets(context)
    }

    suspend fun moveToTrash(id: String) = withContext(Dispatchers.IO) {
        noteDao.moveToTrash(id, System.currentTimeMillis())
        com.example.widget.GlassNotesWidgetReceiver.updateAllWidgets(context)
    }

    suspend fun restoreFromTrash(id: String) = withContext(Dispatchers.IO) {
        noteDao.restoreFromTrash(id)
        com.example.widget.GlassNotesWidgetReceiver.updateAllWidgets(context)
    }

    suspend fun restoreAllFromTrash() = withContext(Dispatchers.IO) {
        noteDao.restoreAllFromTrash()
        com.example.widget.GlassNotesWidgetReceiver.updateAllWidgets(context)
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        noteDao.emptyTrash()
        com.example.widget.GlassNotesWidgetReceiver.updateAllWidgets(context)
    }

    suspend fun deletePermanently(id: String) = withContext(Dispatchers.IO) {
        noteDao.deleteNotePermanently(id)
        com.example.widget.GlassNotesWidgetReceiver.updateAllWidgets(context)
    }

    suspend fun deleteNote(id: String) = withContext(Dispatchers.IO) {
        // Soft delete moves to trash
        noteDao.moveToTrash(id, System.currentTimeMillis())
        com.example.widget.GlassNotesWidgetReceiver.updateAllWidgets(context)
    }

    suspend fun deleteAllNotes() = withContext(Dispatchers.IO) {
        noteDao.deleteAllNotes()
        com.example.widget.GlassNotesWidgetReceiver.updateAllWidgets(context)
    }

    suspend fun togglePin(id: String) = withContext(Dispatchers.IO) {
        val note = noteDao.getNoteByIdDirect(id) ?: return@withContext
        val updated = note.copy(pinned = !note.pinned)
        noteDao.updateNote(updated)
        com.example.widget.GlassNotesWidgetReceiver.updateAllWidgets(context)
    }

    suspend fun duplicateNote(id: String): NoteEntity? = withContext(Dispatchers.IO) {
        val original = noteDao.getNoteByIdDirect(id) ?: return@withContext null
        val newTitle = if (original.title.isNotBlank()) "${original.title} copy" else "Untitled copy"
        val now = System.currentTimeMillis()
        val duplicate = NoteEntity(
            id = UUID.randomUUID().toString().take(12),
            title = newTitle,
            type = original.type,
            content = original.content,
            pinned = false,
            source = null,
            createdAt = now,
            updatedAt = now,
            hash = HashUtil.noteHash(newTitle, original.content)
        )
        noteDao.insertNote(duplicate)
        com.example.widget.GlassNotesWidgetReceiver.updateAllWidgets(context)
        duplicate
    }

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val notes = noteDao.getAllNotesDirect()
        val root = JSONObject()
        root.put("v", 1)
        root.put("exported", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date()))
        val array = JSONArray()
        for (n in notes) {
            val obj = JSONObject()
            obj.put("id", n.id)
            obj.put("title", n.title)
            obj.put("type", n.type)
            obj.put("content", n.content)
            obj.put("pinned", n.pinned)
            obj.put("source", n.source ?: JSONObject.NULL)
            obj.put("createdAt", n.createdAt)
            obj.put("updatedAt", n.updatedAt)
            obj.put("hash", n.hash)
            array.put(obj)
        }
        root.put("notes", array)
        root.toString(2)
    }

    data class ImportResult(val newCount: Int, val updatedCount: Int, val dupeCount: Int, val errorCount: Int)

    suspend fun importBackupJson(jsonString: String): ImportResult = withContext(Dispatchers.IO) {
        var neu = 0
        var dupes = 0
        var errs = 0
        try {
            val root = JSONObject(jsonString)
            val array = root.optJSONArray("notes") ?: return@withContext ImportResult(0, 0, 0, 1)
            val existing = noteDao.getAllNotesDirect()
            val existingHashes = existing.map { it.hash }.filter { it.isNotBlank() }.toSet()
            val existingTitlesAndContent = existing.map { "${it.title.trim().lowercase()}::${it.content.trim()}" }.toSet()

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val content = obj.optString("content", "")
                val title = obj.optString("title", "")
                val type = when (obj.optString("type")) {
                    "html" -> "html"
                    "pdf" -> "pdf"
                    else -> "text"
                }
                val hash = if (type == "pdf") {
                    HashUtil.sha256("pdf:${title.lowercase().trim()}:${content.length}")
                } else {
                    HashUtil.noteHash(title, content)
                }

                if (existingHashes.contains(hash) || existingTitlesAndContent.contains("${title.trim().lowercase()}::${content.trim()}")) {
                    dupes++
                    continue
                }

                val now = System.currentTimeMillis()
                val note = NoteEntity(
                    id = obj.optString("id", UUID.randomUUID().toString().take(12)),
                    title = title,
                    type = type,
                    content = content,
                    pinned = obj.optBoolean("pinned", false),
                    source = if (obj.has("source") && !obj.isNull("source")) obj.getString("source") else null,
                    createdAt = obj.optLong("createdAt", now),
                    updatedAt = obj.optLong("updatedAt", now),
                    hash = hash
                )
                noteDao.insertNote(note)
                neu++
            }
        } catch (_: Exception) {
            errs++
        }
        removeDuplicateNotes()
        ImportResult(newCount = neu, updatedCount = 0, dupeCount = dupes, errorCount = errs)
    }

    suspend fun importTextFile(fileName: String, content: String): String = withContext(Dispatchers.IO) {
        val isHtml = fileName.endsWith(".html", ignoreCase = true) || fileName.endsWith(".htm", ignoreCase = true)
        val title = fileName.substringBeforeLast(".").replace(Regex("[_-]+"), " ").trim()
        val hash = HashUtil.noteHash(title, content)

        val existing = noteDao.getAllNotesDirect()
        val exactDupe = existing.find { it.hash == hash || (it.title.equals(title, ignoreCase = true) && it.content == content) }
        if (exactDupe != null) {
            return@withContext "dupe"
        }

        val existingBySource = existing.find {
            it.source == fileName || it.source?.substringAfterLast('/')?.equals(fileName, ignoreCase = true) == true
        }
        if (existingBySource != null) {
            val updated = existingBySource.copy(
                content = content,
                title = title,
                type = if (isHtml) "html" else "text",
                hash = hash,
                updatedAt = System.currentTimeMillis()
            )
            noteDao.updateNote(updated)
            return@withContext "updated"
        }

        val now = System.currentTimeMillis()
        val newNote = NoteEntity(
            id = UUID.randomUUID().toString().take(12),
            title = title,
            type = if (isHtml) "html" else "text",
            content = content,
            pinned = false,
            source = fileName,
            createdAt = now,
            updatedAt = now,
            hash = hash
        )
        noteDao.insertNote(newNote)
        return@withContext "new"
    }

    suspend fun insertOrUpdate(note: NoteEntity) = withContext(Dispatchers.IO) {
        val hash = if (note.hash.isNotEmpty()) note.hash else {
            if (note.type == "pdf") HashUtil.sha256("pdf:${note.title.lowercase().trim()}:${note.content.length}")
            else HashUtil.noteHash(note.title, note.content)
        }
        val existing = noteDao.getNoteByIdDirect(note.id)
        if (existing != null) {
            noteDao.updateNote(note.copy(hash = hash, updatedAt = System.currentTimeMillis()))
        } else {
            noteDao.insertNote(note.copy(hash = hash))
        }
        com.example.widget.GlassNotesWidgetReceiver.updateAllWidgets(context)
    }

    suspend fun ensureNoteContentLoaded(note: NoteEntity): NoteEntity = withContext(Dispatchers.IO) {
        if (note.type == "html" && note.content.isBlank() && !note.source.isNullOrBlank()) {
            val content = com.example.data.sync.HtmlFileScanner.readFileContent(context, note.source)
            if (!content.isNullOrBlank()) {
                noteDao.updateNoteContent(note.id, content, note.updatedAt)
                return@withContext note.copy(content = content)
            }
        }
        note
    }

    suspend fun syncAllDeviceHtmlFiles(): FolderSyncResult = withContext(Dispatchers.IO) {
        try {
            // First clean up any pre-existing duplicates
            removeDuplicateNotes()

            val currentSettings = _settingsFlow.value
            val customTree = currentSettings.syncFolderUri
            val discovered = com.example.data.sync.HtmlFileScanner.scanDeviceForHtmlFiles(context, customTree)

            val existingNotes = noteDao.getAllNotesDirect()
            val existingBySource = existingNotes.filter { !it.source.isNullOrBlank() }.associateBy { it.source!! }
            val existingByTitle = existingNotes.filter { it.type == "html" }.associateBy { it.title.lowercase().trim() }

            var neu = 0
            var upd = 0
            var dupe = 0

            val notesToInsert = mutableListOf<NoteEntity>()
            val processedFileKeys = HashSet<String>()

            for (file in discovered) {
                val cleanFileName = file.displayName.trim()
                val fileKey = "${cleanFileName.lowercase()}::${file.size}"
                if (!processedFileKeys.add(fileKey)) {
                    // Already processed this physical file in this scan batch
                    continue
                }

                val canonical = file.canonicalPath?.lowercase()
                val existing = existingBySource[file.uriString]
                    ?: (if (canonical != null) existingNotes.find { it.source?.lowercase() == canonical } else null)
                    ?: existingNotes.find { it.type == "html" && (it.source?.endsWith(cleanFileName, ignoreCase = true) == true) }

                if (existing != null) {
                    val readContent = if (file.size in 1..256 * 1024L) {
                        com.example.data.sync.HtmlFileScanner.readFileContent(context, file.uriString, 256 * 1024) ?: existing.content
                    } else {
                        existing.content
                    }
                    val derivedTitle = com.example.data.sync.HtmlFileScanner.extractTitleFromHtml(readContent, file.displayName)
                    val computedHash = HashUtil.noteHash(derivedTitle, readContent)

                    if (existing.hash == computedHash && existing.content.isNotEmpty()) {
                        dupe++
                    } else {
                        val updated = existing.copy(
                            title = if (derivedTitle.isNotBlank()) derivedTitle else existing.title,
                            updatedAt = file.lastModified,
                            hash = computedHash,
                            content = readContent,
                            source = file.uriString
                        )
                        noteDao.updateNote(updated)
                        upd++
                    }
                } else {
                    val initialContent = if (file.size in 1..256 * 1024L) {
                        com.example.data.sync.HtmlFileScanner.readFileContent(context, file.uriString, 256 * 1024) ?: ""
                    } else {
                        ""
                    }
                    val derivedTitle = com.example.data.sync.HtmlFileScanner.extractTitleFromHtml(initialContent, file.displayName)
                    val cleanTitle = derivedTitle.ifBlank { cleanFileName.substringBeforeLast(".") }.ifBlank { "HTML Document" }
                    val computedHash = HashUtil.noteHash(cleanTitle, initialContent)

                    // Check if title or content matches existing note
                    val matchingExisting = existingByTitle[cleanTitle.lowercase().trim()]
                        ?: existingNotes.find { it.type == "html" && it.hash == computedHash }

                    if (matchingExisting != null) {
                        // Link source and update rather than duplicate
                        val updated = matchingExisting.copy(
                            source = file.uriString,
                            content = if (matchingExisting.content.isBlank()) initialContent else matchingExisting.content,
                            hash = computedHash,
                            updatedAt = maxOf(matchingExisting.updatedAt, file.lastModified)
                        )
                        noteDao.updateNote(updated)
                        dupe++
                    } else {
                        val newNote = NoteEntity(
                            id = UUID.randomUUID().toString().take(12),
                            title = cleanTitle,
                            type = "html",
                            content = initialContent,
                            pinned = false,
                            source = file.uriString,
                            createdAt = file.lastModified,
                            updatedAt = file.lastModified,
                            hash = computedHash
                        )
                        notesToInsert.add(newNote)
                        neu++
                    }
                }

                if (notesToInsert.size >= 10) {
                    noteDao.insertNotes(notesToInsert.toList())
                    notesToInsert.clear()
                }
            }

            if (notesToInsert.isNotEmpty()) {
                noteDao.insertNotes(notesToInsert)
            }

            // Clean up any potential duplicates
            removeDuplicateNotes()

            updateSettings { it.copy(lastSyncTime = System.currentTimeMillis()) }
            com.example.widget.GlassNotesWidgetReceiver.updateAllWidgets(context)

            FolderSyncResult(
                scanned = discovered.size,
                newCount = neu,
                updatedCount = upd,
                dupeCount = dupe
            )
        } catch (t: Throwable) {
            FolderSyncResult(error = t.localizedMessage ?: "Device sync failed")
        }
    }

    suspend fun syncAllDevicePdfFiles(): FolderSyncResult = withContext(Dispatchers.IO) {
        try {
            // First clean up any pre-existing duplicates
            removeDuplicateNotes()

            val currentSettings = _settingsFlow.value
            val customTree = currentSettings.syncFolderUri
            val discovered = com.example.data.sync.PdfFileScanner.scanDeviceForPdfFiles(context, customTree)

            val existingNotes = noteDao.getAllNotesDirect()
            val existingBySource = existingNotes.filter { !it.source.isNullOrBlank() }.associateBy { it.source!! }
            val existingByTitle = existingNotes.filter { it.type == "pdf" }.associateBy { it.title.lowercase().trim() }

            var neu = 0
            var upd = 0
            var dupe = 0

            val notesToInsert = mutableListOf<NoteEntity>()
            val processedFileKeys = HashSet<String>()

            for (file in discovered) {
                val cleanFileName = file.displayName.trim()
                val fileKey = "${cleanFileName.lowercase()}::${file.size}"
                if (!processedFileKeys.add(fileKey)) {
                    // Already processed this physical file in this scan batch
                    continue
                }

                val cleanTitle = cleanFileName.substringBeforeLast(".").replace(Regex("[_-]+"), " ").trim().ifBlank { "PDF Document" }
                val pdfHash = HashUtil.sha256("pdf:${cleanTitle.lowercase().trim()}:${file.size}")
                val canonical = file.canonicalPath?.lowercase()

                val existing = existingBySource[file.uriString]
                    ?: (if (canonical != null) existingNotes.find { it.source?.lowercase() == canonical } else null)
                    ?: existingByTitle[cleanTitle.lowercase().trim()]
                    ?: existingNotes.find { it.type == "pdf" && (it.hash == pdfHash || it.source?.endsWith(cleanFileName, ignoreCase = true) == true) }

                if (existing != null) {
                    if (existing.hash == pdfHash) {
                        dupe++
                    } else {
                        val updated = existing.copy(
                            title = cleanTitle,
                            updatedAt = file.lastModified,
                            hash = pdfHash,
                            content = file.uriString,
                            source = file.uriString
                        )
                        noteDao.updateNote(updated)
                        upd++
                    }
                } else {
                    val newNote = NoteEntity(
                        id = UUID.randomUUID().toString().take(12),
                        title = cleanTitle,
                        type = "pdf",
                        content = file.uriString,
                        pinned = false,
                        source = file.uriString,
                        createdAt = file.lastModified,
                        updatedAt = file.lastModified,
                        hash = pdfHash
                    )
                    notesToInsert.add(newNote)
                    neu++
                }

                if (notesToInsert.size >= 10) {
                    noteDao.insertNotes(notesToInsert.toList())
                    notesToInsert.clear()
                }
            }

            if (notesToInsert.isNotEmpty()) {
                noteDao.insertNotes(notesToInsert)
            }

            // Clean up any potential duplicates
            removeDuplicateNotes()

            updateSettings { it.copy(lastSyncTime = System.currentTimeMillis()) }
            com.example.widget.GlassNotesWidgetReceiver.updateAllWidgets(context)

            FolderSyncResult(
                scanned = discovered.size,
                newCount = neu,
                updatedCount = upd,
                dupeCount = dupe
            )
        } catch (t: Throwable) {
            FolderSyncResult(error = t.localizedMessage ?: "PDF Device sync failed")
        }
    }

    suspend fun syncFromDeviceFolder(folderUriString: String): FolderSyncResult = withContext(Dispatchers.IO) {
        try {
            val treeUri = Uri.parse(folderUriString)
            val documentFile = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext FolderSyncResult(error = "Folder not accessible")

            if (!documentFile.canRead()) {
                return@withContext FolderSyncResult(error = "Permission denied or folder inaccessible")
            }

            // Clean existing duplicates first
            removeDuplicateNotes()

            var scanned = 0
            var neu = 0
            var upd = 0
            var dupe = 0

            val files = documentFile.listFiles()
            for (file in files) {
                val name = file.name ?: ""
                val isSupported = file.isFile && (
                    name.endsWith(".html", ignoreCase = true) ||
                    name.endsWith(".htm", ignoreCase = true) ||
                    name.endsWith(".txt", ignoreCase = true) ||
                    name.endsWith(".md", ignoreCase = true)
                )

                if (isSupported) {
                    scanned++
                    try {
                        val content = context.contentResolver.openInputStream(file.uri)?.use { stream ->
                            stream.bufferedReader().readText()
                        }
                        if (content != null) {
                            when (importTextFile(name, content)) {
                                "new" -> neu++
                                "updated" -> upd++
                                else -> dupe++
                            }
                        }
                    } catch (_: Exception) {
                        // Skip read failure
                    }
                }
            }

            removeDuplicateNotes()

            updateSettings { it.copy(lastSyncTime = System.currentTimeMillis()) }
            com.example.widget.GlassNotesWidgetReceiver.updateAllWidgets(context)
            FolderSyncResult(scanned = scanned, newCount = neu, updatedCount = upd, dupeCount = dupe)
        } catch (e: Exception) {
            FolderSyncResult(error = e.localizedMessage ?: "Sync error")
        }
    }

    suspend fun findDuplicateNote(title: String, content: String): NoteEntity? = withContext(Dispatchers.IO) {
        val targetHash = HashUtil.noteHash(title.trim(), content.trim())
        val existing = noteDao.getNoteByHash(targetHash)
        if (existing != null) return@withContext existing
        val all = noteDao.getAllNotesDirect()
        all.find { it.title.trim().equals(title.trim(), ignoreCase = true) && it.content.trim() == content.trim() }
    }

    suspend fun removeDuplicateNotes(): Int = withContext(Dispatchers.IO) {
        val allNotes = noteDao.getAllNotesDirect()
        if (allNotes.size <= 1) return@withContext 0

        val toDeleteIds = mutableSetOf<String>()

        // 1. Group by exact non-blank hash
        val byHash = allNotes.groupBy { it.hash }.filter { it.key.isNotBlank() && it.value.size > 1 }
        for ((_, group) in byHash) {
            val sorted = group.sortedWith(
                compareByDescending<NoteEntity> { it.pinned }
                    .thenByDescending { it.content.isNotBlank() }
                    .thenByDescending { it.updatedAt }
            )
            toDeleteIds.addAll(sorted.drop(1).map { it.id })
        }

        // Remaining notes after hash pass
        val remainingAfterHash = allNotes.filter { !toDeleteIds.contains(it.id) }

        // 2. Text / HTML notes with identical title and identical content
        val byTextContent = remainingAfterHash.filter { it.type != "pdf" }.groupBy {
            "${it.type}::${it.title.trim().lowercase()}::${it.content.trim()}"
        }.filter { it.value.size > 1 }

        for ((_, group) in byTextContent) {
            val sorted = group.sortedWith(
                compareByDescending<NoteEntity> { it.pinned }
                    .thenByDescending { it.updatedAt }
            )
            toDeleteIds.addAll(sorted.drop(1).map { it.id })
        }

        // 3. PDF notes with identical title or pointing to identical source/file
        val remainingAfterText = remainingAfterHash.filter { !toDeleteIds.contains(it.id) }
        val byPdfIdentity = remainingAfterText.filter { it.type == "pdf" }.groupBy { note ->
            val srcFile = note.source?.substringAfterLast('/')?.lowercase()?.trim() ?: ""
            val titleClean = note.title.lowercase().trim()
            if (srcFile.isNotEmpty()) "pdf_src::$srcFile" else "pdf_title::$titleClean"
        }.filter { it.value.size > 1 }

        for ((_, group) in byPdfIdentity) {
            val sorted = group.sortedWith(
                compareByDescending<NoteEntity> { it.pinned }
                    .thenByDescending { it.content.startsWith("/") || it.content.startsWith("file://") || it.content.startsWith("content://") }
                    .thenByDescending { it.updatedAt }
            )
            toDeleteIds.addAll(sorted.drop(1).map { it.id })
        }

        if (toDeleteIds.isNotEmpty()) {
            noteDao.deleteNotesByIds(toDeleteIds.toList())
            com.example.widget.GlassNotesWidgetReceiver.updateAllWidgets(context)
        }
        toDeleteIds.size
    }

    // --- AI Query History Persistence (Separated by HTML vs PDF Mode) ---

    fun getAiHistory(modeType: String): List<com.example.data.model.AiHistoryItem> {
        val key = if (modeType == "pdf") "pref_ai_history_pdf" else "pref_ai_history_html"
        val rawJson = prefs.getString(key, "[]") ?: "[]"
        val list = mutableListOf<com.example.data.model.AiHistoryItem>()
        try {
            val arr = JSONArray(rawJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    com.example.data.model.AiHistoryItem(
                        id = obj.optString("id", UUID.randomUUID().toString().take(8)),
                        query = obj.optString("query", ""),
                        modeType = obj.optString("modeType", modeType),
                        aiSearchMode = obj.optString("aiSearchMode", "ASK_NOTES"),
                        responseTitle = obj.optString("responseTitle", ""),
                        responseContent = obj.optString("responseContent", ""),
                        suggestedType = obj.optString("suggestedType", "text"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list.sortedByDescending { it.timestamp }
    }

    suspend fun saveAiHistoryItem(item: com.example.data.model.AiHistoryItem) = withContext(Dispatchers.IO) {
        val key = if (item.modeType == "pdf") "pref_ai_history_pdf" else "pref_ai_history_html"
        val existing = getAiHistory(item.modeType).toMutableList()
        // Remove identical prior queries if any to avoid clutter
        existing.removeAll { it.query.equals(item.query, ignoreCase = true) || it.id == item.id }
        existing.add(0, item)
        val capped = existing.take(40) // Keep latest 40 items per mode

        val arr = JSONArray()
        for (h in capped) {
            val obj = JSONObject().apply {
                put("id", h.id)
                put("query", h.query)
                put("modeType", h.modeType)
                put("aiSearchMode", h.aiSearchMode)
                put("responseTitle", h.responseTitle)
                put("responseContent", h.responseContent)
                put("suggestedType", h.suggestedType)
                put("timestamp", h.timestamp)
            }
            arr.put(obj)
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    suspend fun deleteAiHistoryItem(id: String, modeType: String) = withContext(Dispatchers.IO) {
        val key = if (modeType == "pdf") "pref_ai_history_pdf" else "pref_ai_history_html"
        val existing = getAiHistory(modeType).filter { it.id != id }
        val arr = JSONArray()
        for (h in existing) {
            val obj = JSONObject().apply {
                put("id", h.id)
                put("query", h.query)
                put("modeType", h.modeType)
                put("aiSearchMode", h.aiSearchMode)
                put("responseTitle", h.responseTitle)
                put("responseContent", h.responseContent)
                put("suggestedType", h.suggestedType)
                put("timestamp", h.timestamp)
            }
            arr.put(obj)
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    suspend fun clearAiHistory(modeType: String) = withContext(Dispatchers.IO) {
        val key = if (modeType == "pdf") "pref_ai_history_pdf" else "pref_ai_history_html"
        prefs.edit().remove(key).apply()
    }
}
