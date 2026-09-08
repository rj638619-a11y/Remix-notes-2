package com.example.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.GeminiSearchMode
import com.example.data.model.NoteEntity
import com.example.data.model.NoteSummary
import com.example.ui.security.AppLockScreen
import com.example.ui.security.LockScreenMode
import com.example.ui.components.GlassDock
import com.example.ui.components.GlassHeader
import com.example.ui.components.GlassToast
import com.example.ui.components.NoteGridItem
import com.example.ui.components.NoteRowItem
import com.example.ui.components.RunningPill
import com.example.ui.components.SwipeableNoteRow
import com.example.ui.editor.NoteEditorScreen
import com.example.ui.editor.PdfRendererView
import com.example.ui.sheets.ClockSheet
import com.example.ui.sheets.CreateNoteSheet
import com.example.ui.sheets.GeminiSearchSheet
import com.example.ui.sheets.NoteActionsSheet
import com.example.ui.sheets.SettingsSheet
import com.example.ui.sheets.SortSheet
import com.example.ui.sheets.SyncSheet
import com.example.ui.theme.GlassTheme
import com.example.ui.util.AmbientBackground
import com.example.ui.viewmodel.NotesViewModel
import com.example.ui.viewmodel.ToastEvent
import com.example.ui.viewmodel.WidgetNavAction
import com.example.util.VibrationHelper
import kotlinx.coroutines.delay
import java.io.File

sealed interface ActiveSheet {
    object None : ActiveSheet
    object Create : ActiveSheet
    data class NoteOptions(val note: NoteEntity) : ActiveSheet
    data class SetCategory(val note: NoteEntity) : ActiveSheet
    data class Clock(val kind: String = "timer") : ActiveSheet
    object Settings : ActiveSheet
    object Sync : ActiveSheet
    object Sort : ActiveSheet
    data class GeminiSearch(val query: String = "", val mode: GeminiSearchMode = GeminiSearchMode.ASK_NOTES) : ActiveSheet
}

@Composable
fun HomeScreen(
    viewModel: NotesViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = GlassTheme.colors

    val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
    val groupedNotes by viewModel.groupedNotes.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val timerState by viewModel.timerState.collectAsStateWithLifecycle()
    val stopwatchState by viewModel.stopwatchState.collectAsStateWithLifecycle()
    val runningPillInfo by viewModel.runningPill.collectAsStateWithLifecycle()
    val geminiState by viewModel.geminiState.collectAsStateWithLifecycle()
    val aiHistory by viewModel.aiHistory.collectAsStateWithLifecycle()
    val trashCount by viewModel.trashCount.collectAsStateWithLifecycle()

    var activeSheet by remember { mutableStateOf<ActiveSheet>(ActiveSheet.None) }
    var editingNoteId by remember { mutableStateOf<String?>(null) }
    var openInZenMode by remember { mutableStateOf(false) }
    var currentToastEvent by remember { mutableStateOf<ToastEvent?>(null) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var clickedTrashNote by remember { mutableStateOf<com.example.data.model.NoteSummary?>(null) }
    var noteToUnlock by remember { mutableStateOf<NoteSummary?>(null) }

    val handleOpenNote: (NoteSummary, Boolean) -> Unit = { note, isZen ->
        if (selectedFilter == "trash") {
            clickedTrashNote = note
        } else if (note.isLocked && !viewModel.isNoteUnlocked(note.id)) {
            noteToUnlock = note
        } else {
            openInZenMode = isZen
            editingNoteId = note.id
        }
    }

    // Collect toasts
    LaunchedEffect(Unit) {
        viewModel.toastFlow.collect { event ->
            currentToastEvent = event
            delay(if (event is ToastEvent.WithAction) 5200 else 2600)
            if (currentToastEvent == event) {
                currentToastEvent = null
            }
        }
    }

    // Collect widget navigation actions (e.g. from home screen widget)
    LaunchedEffect(Unit) {
        viewModel.widgetNavAction.collect { action ->
            when (action) {
                is WidgetNavAction.OpenNote -> {
                    openInZenMode = false
                    editingNoteId = action.id
                    activeSheet = ActiveSheet.None
                }
                is WidgetNavAction.CreateNote -> {
                    activeSheet = ActiveSheet.Create
                }
            }
            viewModel.consumeWidgetNavAction()
        }
    }

    // JSON Backup Restore Launcher
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val text = stream.bufferedReader().readText()
                    viewModel.restoreBackupJson(text)
                }
            } catch (_: Exception) {
                viewModel.showToast("Failed to read backup file")
            }
        }
    }

    var pendingSyncAction by remember { mutableStateOf<String?>(null) }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted || android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            when (pendingSyncAction) {
                "html" -> viewModel.syncAllDeviceHtmlFiles(silent = false)
                "pdf" -> viewModel.syncAllDevicePdfFiles(silent = false)
            }
        } else {
            viewModel.showToast("Storage permission is required for full device scan")
        }
        pendingSyncAction = null
    }

    fun triggerFullDeviceSync(type: String) {
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.S_V2) {
            val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasPerm) {
                pendingSyncAction = type
                storagePermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                return
            }
        }
        if (type == "html") viewModel.syncAllDeviceHtmlFiles(silent = false)
        else viewModel.syncAllDevicePdfFiles(silent = false)
    }

    // Select Folder Launcher for Device Folder Auto-Sync (.html, .htm, .md, .txt)
    val selectFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                val folderName = docFile?.name ?: uri.lastPathSegment ?: "Device Folder"
                viewModel.setSyncFolder(uri.toString(), folderName)
            } catch (_: Exception) {
                viewModel.showToast("Could not access folder permission")
            }
        }
    }

    // Import Files Launcher (.txt, .md, .html)
    val importFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val fileDataList = mutableListOf<Pair<String, String>>()
            for (uri in uris) {
                try {
                    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "imported_note.txt"
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val content = stream.bufferedReader().readText()
                        fileDataList.add(Pair(name, content))
                    }
                } catch (_: Exception) {
                    // Skip failed file
                }
            }
            if (fileDataList.isNotEmpty()) {
                viewModel.importFiles(fileDataList)
            }
        }
    }

    // Import PDF Launcher
    val importPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                var name = "document.pdf"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
                viewModel.importPdf(uri, name)
            } catch (e: Exception) {
                viewModel.showToast("Could not resolve PDF file name")
            }
        }
    }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    val isScrolled by remember(settings.viewMode) {
        derivedStateOf {
            if (settings.viewMode == "grid") {
                gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 44
            } else {
                listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 44
            }
        }
    }

    val activeEditingNote = remember(editingNoteId, allNotes) {
        allNotes.find { it.id == editingNoteId }
    }

    LaunchedEffect(editingNoteId) {
        if (editingNoteId != null) {
            val n = allNotes.find { it.id == editingNoteId }
            if (n != null) {
                viewModel.ensureNoteContentLoaded(n)
            }
        }
    }

    val totalNotesCount = allNotes.size
    val approxStorageKb = remember(allNotes) {
        val bytes = allNotes.sumOf { it.title.length + it.content.length }
        maxOf(1, bytes / 1024)
    }

    // Helpers for Note Actions
    fun handleCopyText(note: NoteEntity) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(note.displayTitle, if (note.type == "html") note.content else "${note.displayTitle}\n\n${note.content}")
        clipboard.setPrimaryClip(clip)
        viewModel.showToast("Copied to clipboard")
    }

    fun handleShareNote(note: NoteEntity) {
        val shareText = if (note.type == "html") "${note.displayTitle}\n\n${note.snippet}" else "${note.displayTitle}\n\n${note.content}"
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share Note"))
    }

    fun handleExportFile(note: NoteEntity) {
        try {
            val extension = if (note.type == "html") ".html" else ".txt"
            val sanitizedTitle = note.displayTitle.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val fileName = "$sanitizedTitle$extension"
            val file = File(context.cacheDir, fileName)
            file.writeText(note.content)

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                type = if (note.type == "html") "text/html" else "text/plain"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Export Note"))
            viewModel.showToast("Exported")
        } catch (_: Exception) {
            // Fallback copy to clipboard
            handleCopyText(note)
            viewModel.showToast("Export fallback: Copied to clipboard")
        }
    }

    fun handleExportBackupJson() {
        viewModel.exportBackupJson { json ->
            try {
                val file = File(context.cacheDir, "glass-notes-backup.json")
                file.writeText(json)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, uri)
                    type = "application/json"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Export Backup JSON"))
            } catch (_: Exception) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Glass Notes Backup", json))
                viewModel.showToast("Backup copied to clipboard")
            }
        }
    }

    val isHomePushed = activeEditingNote != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        // Ambient background glowing gradients
        AmbientBackground(isDark = colors.isDark, isReduced = colors.isReduced)

        val homeScale by animateFloatAsState(
            targetValue = if (isHomePushed) 0.94f else 1f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
            label = "homeScale"
        )
        val homeAlpha by animateFloatAsState(
            targetValue = if (isHomePushed) 0.7f else 1f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
            label = "homeAlpha"
        )

        // Home View with push scaling animation when editor opens
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = homeScale
                    scaleY = homeScale
                    alpha = homeAlpha
                    if (isHomePushed) {
                        shape = RoundedCornerShape(26.dp)
                        clip = true
                    }
                }
        ) {
            // Notes Content List or Grid
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val topBarPadding = if (isLandscape) statusBarHeight + 115.dp else statusBarHeight + 212.dp
            val bottomBarPadding = if (isLandscape) navBarHeight + 68.dp else navBarHeight + 96.dp

            if (groupedNotes.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = if (isLandscape) statusBarHeight + 120.dp else statusBarHeight + 220.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(colors.field),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (selectedFilter == "trash") Icons.Default.DeleteOutline else Icons.Default.Description,
                            contentDescription = null,
                            tint = if (selectedFilter == "trash") Color(0xFFEF4444) else colors.textTertiary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (selectedFilter == "trash") "Trash is empty" else if (searchQuery.isNotEmpty() || selectedFilter != "all") "No matching notes" else "No Notes yet",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary
                    )
                    Text(
                        text = if (selectedFilter == "trash") "Notes and documents moved to trash will appear here." else if (searchQuery.isNotEmpty() || selectedFilter != "all") "Try a different search or filter" else "Tap the amber button to create one",
                        fontSize = 14.5.sp,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(top = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else if (settings.viewMode == "grid") {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    state = gridState,
                    contentPadding = PaddingValues(
                        top = topBarPadding,
                        bottom = bottomBarPadding,
                        start = 14.dp,
                        end = 14.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (selectedFilter == "trash" && trashCount > 0) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            TrashFolderBanner(
                                trashCount = trashCount,
                                onRestoreAll = { viewModel.restoreAllTrash() },
                                onEmptyTrash = { showEmptyTrashDialog = true }
                            )
                        }
                    }

                    for (group in groupedNotes) {
                        if (group.header != null) {
                            item(span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                                SectionHeader(title = group.header)
                            }
                        }
                        items(group.notes, key = { it.id }, contentType = { it.type }) { note ->
                            NoteGridItem(
                                note = note,
                                searchQuery = searchQuery,
                                onClick = {
                                    VibrationHelper.vibrate(context, 6)
                                    handleOpenNote(note, false)
                                },
                                onLongClick = {
                                    VibrationHelper.vibrate(context, 14)
                                    if (selectedFilter == "trash") {
                                        clickedTrashNote = note
                                    } else {
                                        val entity = allNotes.find { it.id == note.id }
                                        if (entity != null) activeSheet = ActiveSheet.NoteOptions(entity)
                                    }
                                },
                                onMoreClick = {
                                    VibrationHelper.vibrate(context, 8)
                                    if (selectedFilter == "trash") {
                                        clickedTrashNote = note
                                    } else {
                                        val entity = allNotes.find { it.id == note.id }
                                        if (entity != null) activeSheet = ActiveSheet.NoteOptions(entity)
                                    }
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    // Count line footer
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        val footerText = if (selectedFilter == "trash") {
                            "$trashCount item${if (trashCount == 1) "" else "s"} in Trash"
                        } else {
                            "$totalNotesCount note${if (totalNotesCount == 1) "" else "s"}${if (searchQuery.isNotEmpty()) " matching “$searchQuery”" else ""}"
                        }
                        Text(
                            text = footerText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        top = topBarPadding,
                        bottom = bottomBarPadding,
                        start = 16.dp,
                        end = 16.dp
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (selectedFilter == "trash" && trashCount > 0) {
                        item {
                            TrashFolderBanner(
                                trashCount = trashCount,
                                onRestoreAll = { viewModel.restoreAllTrash() },
                                onEmptyTrash = { showEmptyTrashDialog = true },
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }
                    }

                    for (group in groupedNotes) {
                        if (group.header != null) {
                            item(contentType = "header") {
                                SectionHeader(title = group.header)
                            }
                        }
                        items(group.notes, key = { it.id }, contentType = { it.type }) { note ->
                            SwipeableNoteRow(
                                note = note,
                                searchQuery = searchQuery,
                                isTrashMode = (selectedFilter == "trash"),
                                onClick = {
                                    VibrationHelper.vibrate(context, 6)
                                    handleOpenNote(note, false)
                                },
                                onLongClick = {
                                    VibrationHelper.vibrate(context, 14)
                                    if (selectedFilter == "trash") {
                                        clickedTrashNote = note
                                    } else {
                                        val entity = allNotes.find { it.id == note.id }
                                        if (entity != null) activeSheet = ActiveSheet.NoteOptions(entity)
                                    }
                                },
                                onMoreClick = {
                                    VibrationHelper.vibrate(context, 8)
                                    if (selectedFilter == "trash") {
                                        clickedTrashNote = note
                                    } else {
                                        val entity = allNotes.find { it.id == note.id }
                                        if (entity != null) activeSheet = ActiveSheet.NoteOptions(entity)
                                    }
                                },
                                onTogglePin = {
                                    viewModel.togglePin(note.id)
                                },
                                onDelete = {
                                    if (selectedFilter == "trash") {
                                        viewModel.deletePermanently(note.id)
                                    } else {
                                        viewModel.deleteNote(note.id)
                                    }
                                },
                                onRestore = {
                                    viewModel.restoreNote(note.id)
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    // Count line footer
                    item(contentType = "footer") {
                        val footerText = if (selectedFilter == "trash") {
                            "$trashCount item${if (trashCount == 1) "" else "s"} in Trash"
                        } else {
                            "$totalNotesCount note${if (totalNotesCount == 1) "" else "s"}${if (searchQuery.isNotEmpty()) " matching “$searchQuery”" else ""}"
                        }
                        Text(
                            text = footerText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp)
                        )
                    }
                }
            }

            // Top Header
            GlassHeader(
                isScrolled = isScrolled,
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                selectedFilter = selectedFilter,
                categories = categories,
                trashCount = trashCount,
                onFilterChange = {
                    VibrationHelper.vibrate(context, 6)
                    viewModel.setFilter(it)
                },
                viewMode = settings.viewMode,
                onToggleView = {
                    VibrationHelper.vibrate(context, 6)
                    viewModel.toggleViewMode()
                },
                onToggleTheme = {
                    VibrationHelper.vibrate(context, 6)
                    viewModel.cycleTheme()
                },
                onOpenSort = {
                    VibrationHelper.vibrate(context, 6)
                    activeSheet = ActiveSheet.Sort
                },
                onOpenGemini = { query, mode ->
                    VibrationHelper.vibrate(context, 8)
                    activeSheet = ActiveSheet.GeminiSearch(query, mode)
                },
                readerMode = settings.readerMode,
                onReaderModeChange = {
                    VibrationHelper.vibrate(context, 10)
                    viewModel.setReaderMode(it)
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Bottom Dock
            GlassDock(
                isSyncActive = settings.autoSync,
                onSyncClick = {
                    VibrationHelper.vibrate(context, 8)
                    activeSheet = ActiveSheet.Sync
                },
                onCreateClick = {
                    VibrationHelper.vibrate(context, 8)
                    activeSheet = ActiveSheet.Create
                },
                onSettingsClick = {
                    VibrationHelper.vibrate(context, 8)
                    activeSheet = ActiveSheet.Settings
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
            )
        }

        // Running Timer/Stopwatch Pill at Top
        RunningPill(
            info = runningPillInfo,
            onClick = { activeSheet = ActiveSheet.Clock(runningPillInfo.kind.ifEmpty { "timer" }) },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Glass Toast Message
        GlassToast(
            toastEvent = currentToastEvent,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Slide-in Note Editor Screen
        AnimatedVisibility(
            visible = activeEditingNote != null,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(animationSpec = tween(280)) + scaleIn(
                initialScale = 0.94f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
            ) + fadeOut(animationSpec = tween(220)) + scaleOut(
                targetScale = 0.94f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
            )
        ) {
            if (activeEditingNote != null) {
                if (activeEditingNote.type == "pdf") {
                    PdfRendererView(
                        note = activeEditingNote,
                        initialZenMode = openInZenMode,
                        onBack = { editingNoteId = null },
                        onSaveTitle = { newTitle ->
                            viewModel.saveNote(activeEditingNote.id, newTitle, activeEditingNote.content)
                        },
                        onSharePdf = { file ->
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    type = "application/pdf"
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share PDF Document"))
                            } catch (_: Exception) {
                                viewModel.showToast("Could not share PDF")
                            }
                        },
                        onOpenMore = { activeSheet = ActiveSheet.NoteOptions(activeEditingNote) },
                        onAskGemini = { query, mode ->
                            activeSheet = ActiveSheet.GeminiSearch(query, mode)
                        }
                    )
                } else {
                    NoteEditorScreen(
                        note = activeEditingNote,
                        fontSize = settings.readingFontSize,
                        initialZenMode = openInZenMode,
                        onBack = { editingNoteId = null },
                        onSave = { t, c -> viewModel.saveNote(activeEditingNote.id, t, c) },
                        onOpenMore = { activeSheet = ActiveSheet.NoteOptions(activeEditingNote) },
                        onShare = { handleShareNote(activeEditingNote) },
                        onAskGemini = { query, mode ->
                            activeSheet = ActiveSheet.GeminiSearch(query, mode)
                        }
                    )
                }
            }
        }

        // Bottom Sheets Host
        when (val sheet = activeSheet) {
            is ActiveSheet.Create -> {
                CreateNoteSheet(
                    onDismiss = { activeSheet = ActiveSheet.None },
                    onCreateText = {
                        viewModel.createNote("text") { newId ->
                            openInZenMode = false
                            editingNoteId = newId
                        }
                    },
                    onCreateHtml = {
                        viewModel.createNote("html") { newId ->
                            openInZenMode = false
                            editingNoteId = newId
                        }
                    },
                    onCreatePdf = {
                        importPdfLauncher.launch("application/pdf")
                    }
                )
            }

            is ActiveSheet.NoteOptions -> {
                NoteActionsSheet(
                    note = sheet.note,
                    onDismiss = { activeSheet = ActiveSheet.None },
                    onOpen = {
                        if (sheet.note.isLocked && !viewModel.isNoteUnlocked(sheet.note.id)) {
                            noteToUnlock = NoteSummary(
                                id = sheet.note.id,
                                title = sheet.note.title,
                                type = sheet.note.type,
                                pinned = sheet.note.pinned,
                                source = sheet.note.source,
                                category = sheet.note.category,
                                createdAt = sheet.note.createdAt,
                                updatedAt = sheet.note.updatedAt,
                                isLocked = sheet.note.isLocked,
                                snippetPreview = sheet.note.snippet,
                                isDeleted = sheet.note.isDeleted,
                                deletedAt = sheet.note.deletedAt
                            )
                        } else {
                            openInZenMode = false
                            editingNoteId = sheet.note.id
                        }
                    },
                    onZenReading = {
                        if (sheet.note.isLocked && !viewModel.isNoteUnlocked(sheet.note.id)) {
                            noteToUnlock = NoteSummary(
                                id = sheet.note.id,
                                title = sheet.note.title,
                                type = sheet.note.type,
                                pinned = sheet.note.pinned,
                                source = sheet.note.source,
                                category = sheet.note.category,
                                createdAt = sheet.note.createdAt,
                                updatedAt = sheet.note.updatedAt,
                                isLocked = sheet.note.isLocked,
                                snippetPreview = sheet.note.snippet,
                                isDeleted = sheet.note.isDeleted,
                                deletedAt = sheet.note.deletedAt
                            )
                        } else {
                            openInZenMode = true
                            editingNoteId = sheet.note.id
                        }
                    },
                    onOpenTimer = { activeSheet = ActiveSheet.Clock("timer") },
                    onOpenStopwatch = { activeSheet = ActiveSheet.Clock("sw") },
                    onSetCategory = { activeSheet = ActiveSheet.SetCategory(sheet.note) },
                    onTogglePin = { viewModel.togglePin(sheet.note.id) },
                    onToggleLock = {
                        if (!sheet.note.isLocked && settings.pinCode.isBlank()) {
                            viewModel.showToast("Please set up a PIN in Settings first")
                        } else {
                            viewModel.toggleNoteLock(sheet.note.id)
                        }
                    },
                    onCopyText = { handleCopyText(sheet.note) },
                    onDuplicate = { viewModel.duplicateNote(sheet.note.id) },
                    onExportFile = { handleExportFile(sheet.note) },
                    onShare = { handleShareNote(sheet.note) },
                    onDelete = {
                        if (editingNoteId == sheet.note.id) {
                            editingNoteId = null
                        }
                        if (sheet.note.isDeleted) {
                            viewModel.deletePermanently(sheet.note.id)
                        } else {
                            viewModel.deleteNote(sheet.note.id)
                        }
                    },
                    onRestore = {
                        viewModel.restoreNote(sheet.note.id)
                    }
                )
            }

            is ActiveSheet.SetCategory -> {
                com.example.ui.sheets.SetCategorySheet(
                    note = sheet.note,
                    existingCategories = categories,
                    onDismiss = { activeSheet = ActiveSheet.None },
                    onSaveCategory = { cat ->
                        viewModel.setNoteCategory(sheet.note.id, cat)
                        activeSheet = ActiveSheet.None
                    }
                )
            }

            is ActiveSheet.Clock -> {
                ClockSheet(
                    initialKind = sheet.kind,
                    timerState = timerState,
                    stopwatchState = stopwatchState,
                    onDismiss = { activeSheet = ActiveSheet.None },
                    onSetTimerMinutes = { viewModel.timerSetMinutes(it) },
                    onStartTimer = { viewModel.timerStart(it) },
                    onPauseTimer = { viewModel.timerPause() },
                    onResumeTimer = { viewModel.timerResume() },
                    onResetTimer = { viewModel.timerReset() },
                    onStartSw = { viewModel.swStart() },
                    onPauseSw = { viewModel.swPause() },
                    onResumeSw = { viewModel.swResume() },
                    onLapSw = { viewModel.swLap() },
                    onResetSw = { viewModel.swReset() }
                )
            }

            is ActiveSheet.Settings -> {
                SettingsSheet(
                    settings = settings,
                    totalNotesCount = totalNotesCount,
                    approxStorageKb = approxStorageKb,
                    onDismiss = { activeSheet = ActiveSheet.None },
                    onSetTheme = { viewModel.setTheme(it) },
                    onSetReduceTransparency = { viewModel.setReduceTransparency(it) },
                    onSetReadingFontSize = { viewModel.setReadingFontSize(it) },
                    onSetHapticsEnabled = { viewModel.setHapticsEnabled(it) },
                    onSetPdfPageMode = { viewModel.setPdfPageMode(it) },
                    onSetPdfColorFilter = { viewModel.setPdfColorFilter(it) },
                    onSetPdfRenderQuality = { viewModel.setPdfRenderQuality(it) },
                    onExportBackup = { handleExportBackupJson() },
                    onRestoreBackup = { restoreBackupLauncher.launch(arrayOf("application/json", "text/*")) },
                    onRemoveDuplicates = { viewModel.removeDuplicateNotes() },
                    onWipeAllNotes = { viewModel.deleteAllNotes() },
                    onSetGeminiApiKey = { viewModel.setGeminiApiKey(it) },
                    onSetPinCode = { viewModel.setPinCode(it) },
                    onSetBiometricLock = { viewModel.setBiometricLock(it) },
                    onEnrollFace = { viewModel.enrollFace(it) },
                    onDeleteFaceProfile = { viewModel.deleteFaceProfile() },
                    onSetAppLock = { viewModel.setAppLock(it) },
                    onLockAppNow = { viewModel.lockApp() }
                )
            }

            is ActiveSheet.Sync -> {
                SyncSheet(
                    isAutoSync = settings.autoSync,
                    readerMode = settings.readerMode,
                    syncFolderName = settings.syncFolderName,
                    lastSyncTime = settings.lastSyncTime,
                    onDismiss = { activeSheet = ActiveSheet.None },
                    onSelectFolder = { selectFolderLauncher.launch(null) },
                    onClearFolder = { viewModel.setSyncFolder(null, null) },
                    onSyncNow = { viewModel.syncCurrentMode(silent = false) },
                    onSyncFullDevice = { triggerFullDeviceSync("html") },
                    onSyncPdfDevice = { triggerFullDeviceSync("pdf") },
                    onImportFiles = { importFilesLauncher.launch(arrayOf("text/*", "text/html", "text/plain")) },
                    onImportPdf = { importPdfLauncher.launch("application/pdf") },
                    onToggleAutoSync = { viewModel.setAutoSync(it) }
                )
            }

            is ActiveSheet.Sort -> {
                SortSheet(
                    currentSort = settings.sortOrder,
                    onDismiss = { activeSheet = ActiveSheet.None },
                    onSelectSort = { viewModel.setSortOrder(it) }
                )
            }

            is ActiveSheet.GeminiSearch -> {
                GeminiSearchSheet(
                    initialQuery = sheet.query,
                    initialMode = sheet.mode,
                    geminiState = geminiState,
                    allNotes = allNotes,
                    aiHistory = aiHistory,
                    currentReaderMode = settings.readerMode,
                    geminiApiKey = settings.geminiApiKey,
                    onSetGeminiApiKey = { viewModel.setGeminiApiKey(it) },
                    onDismiss = {
                        activeSheet = ActiveSheet.None
                        viewModel.clearGeminiState()
                    },
                    onQuery = { q, m -> viewModel.askGemini(q, m) },
                    onSaveAsNote = { res, type ->
                        viewModel.saveGeminiResultAsNote(res, type) { newId ->
                            activeSheet = ActiveSheet.None
                            editingNoteId = newId
                        }
                    },
                    onOpenCitedNote = { noteId ->
                        activeSheet = ActiveSheet.None
                        editingNoteId = noteId
                    },
                    onDeleteHistoryItem = { historyId ->
                        viewModel.deleteAiHistoryItem(historyId, settings.readerMode)
                    },
                    onClearHistory = {
                        viewModel.clearAiHistory(settings.readerMode)
                    }
                )
            }

            ActiveSheet.None -> {}
        }

        // Empty Trash Confirmation Dialog
        if (showEmptyTrashDialog) {
            AlertDialog(
                onDismissRequest = { showEmptyTrashDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = Color(0xFFEF4444)
                    )
                },
                title = {
                    Text(text = "Empty Trash?", fontWeight = FontWeight.Bold)
                },
                text = {
                    Text("All $trashCount item${if (trashCount == 1) "" else "s"} in Trash will be permanently deleted. This action cannot be undone.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showEmptyTrashDialog = false
                            viewModel.emptyTrash()
                        }
                    ) {
                        Text("Empty All", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyTrashDialog = false }) {
                        Text("Cancel", color = colors.textSecondary)
                    }
                }
            )
        }

        // Clicked Trash Note Dialog (Restore or Delete Forever)
        val trashNote = clickedTrashNote
        if (trashNote != null) {
            AlertDialog(
                onDismissRequest = { clickedTrashNote = null },
                icon = {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = colors.accent
                    )
                },
                title = {
                    Text(text = trashNote.displayTitle, fontWeight = FontWeight.Bold, maxLines = 1)
                },
                text = {
                    Text("This note is currently in your Trash folder. Would you like to restore it to your active library or delete it permanently?")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val id = trashNote.id
                            clickedTrashNote = null
                            viewModel.restoreNote(id)
                        }
                    ) {
                        Text("Restore Note", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                val id = trashNote.id
                                clickedTrashNote = null
                                viewModel.deletePermanently(id)
                            }
                        ) {
                            Text("Delete Forever", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(onClick = { clickedTrashNote = null }) {
                            Text("Cancel", color = colors.textSecondary)
                        }
                    }
                }
            )
        }

        // Note Unlock Dialog (Face Unlock / PIN)
        val lockedNote = noteToUnlock
        if (lockedNote != null) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { noteToUnlock = null },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                AppLockScreen(
                    mode = LockScreenMode.UnlockNote(noteTitle = lockedNote.displayTitle),
                    correctPin = settings.pinCode,
                    biometricEnabled = settings.biometricLockEnabled,
                    faceSignature = settings.faceSignature,
                    faceEnrolled = settings.faceEnrolled,
                    onSuccess = {
                        viewModel.unlockNote(lockedNote.id)
                        openInZenMode = false
                        editingNoteId = lockedNote.id
                        noteToUnlock = null
                    },
                    onDismiss = { noteToUnlock = null }
                )
            }
        }
    }
}

@Composable
private fun TrashFolderBanner(
    trashCount: Int,
    onRestoreAll: () -> Unit,
    onEmptyTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = GlassTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Trash Folder",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text
                    )
                    Text(
                        text = if (trashCount == 1) "1 item in Trash" else "$trashCount items in Trash",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (trashCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.15f))
                            .clickable(onClick = onRestoreAll)
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Restore All",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                            .clickable(onClick = onEmptyTrash)
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "Empty",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val colors = GlassTheme.colors
    Text(
        text = title.uppercase(),
        fontSize = 12.5.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.8.sp,
        color = colors.textTertiary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 4.dp, start = 2.dp)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.hairline)
            .padding(bottom = 2.dp)
    )
}
