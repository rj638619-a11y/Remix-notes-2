package com.example.data.sync

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File

data class DiscoveredPdfFile(
    val uriString: String,
    val displayName: String,
    val size: Long,
    val lastModified: Long,
    val canonicalPath: String? = null
)

object PdfFileScanner {

    private const val MAX_SCAN_DEPTH = 6
    private const val MAX_PDF_FILE_SIZE = 100 * 1024 * 1024L // 100MB limit
    private const val MAX_DISCOVERED_LIMIT = 300 // Prevent storage scan OOM on low-end devices

    /**
     * Discovers all PDF files across device storage using MediaStore,
     * standard public external directories, and custom SAF folder trees.
     * Uses unified canonical path and fingerprint tracking to guarantee ZERO duplicate entries.
     */
    fun scanDeviceForPdfFiles(
        context: Context,
        customTreeUriString: String? = null
    ): List<DiscoveredPdfFile> {
        val discovered = mutableListOf<DiscoveredPdfFile>()
        val seenFingerprints = HashSet<String>()
        val seenCanonicalPaths = HashSet<String>()

        try {
            // 1. Scan user-selected SAF tree URI if configured
            if (!customTreeUriString.isNullOrBlank()) {
                scanSafTree(context, customTreeUriString, discovered, seenFingerprints)
            }

            // 2. Scan standard external & internal storage directories
            scanExternalDirectories(context, discovered, seenFingerprints, seenCanonicalPaths)

            // 3. Scan via MediaStore (Files table)
            scanMediaStore(context, discovered, seenFingerprints, seenCanonicalPaths)
        } catch (_: Throwable) {
            // Fallback safety
        }

        return discovered
    }

    private fun scanMediaStore(
        context: Context,
        discovered: MutableList<DiscoveredPdfFile>,
        seenFingerprints: HashSet<String>,
        seenCanonicalPaths: HashSet<String>
    ) {
        try {
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DATA
            )

            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.pdf' OR " +
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.PDF' OR " +
                    "${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/pdf'"

            val queryUri = MediaStore.Files.getContentUri("external")

            context.contentResolver.query(
                queryUri,
                projection,
                selection,
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                if (idCol != -1 && nameCol != -1) {
                    while (cursor.moveToNext() && discovered.size < MAX_DISCOVERED_LIMIT) {
                        try {
                            val id = cursor.getLong(idCol)
                            val name = cursor.getString(nameCol) ?: continue
                            val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                            val dateSec = if (dateCol != -1) cursor.getLong(dateCol) else 0L
                            val dateMs = if (dateSec > 0) dateSec * 1000L else System.currentTimeMillis()
                            val dataPath = if (dataCol != -1) cursor.getString(dataCol) else null

                            if (size in 1..MAX_PDF_FILE_SIZE) {
                                val cleanName = name.trim()
                                val fingerprint = "${cleanName.lowercase()}::$size"

                                if (dataPath != null) {
                                    val canonical = try { File(dataPath).canonicalPath.lowercase() } catch (_: Throwable) { dataPath.lowercase() }
                                    if (seenCanonicalPaths.contains(canonical)) {
                                        continue
                                    }
                                    seenCanonicalPaths.add(canonical)
                                }

                                if (!seenFingerprints.add(fingerprint)) {
                                    continue
                                }

                                val itemUri = if (dataPath != null && File(dataPath).exists()) {
                                    dataPath
                                } else {
                                    ContentUris.withAppendedId(queryUri, id).toString()
                                }

                                discovered.add(
                                    DiscoveredPdfFile(
                                        uriString = itemUri,
                                        displayName = cleanName,
                                        size = size,
                                        lastModified = dateMs,
                                        canonicalPath = dataPath
                                    )
                                )
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    private fun scanExternalDirectories(
        context: Context,
        discovered: MutableList<DiscoveredPdfFile>,
        seenFingerprints: HashSet<String>,
        seenCanonicalPaths: HashSet<String>
    ) {
        try {
            val rootDirs = mutableListOf<File>()

            // 1. Documents Directory
            try {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.let {
                    if (it.exists() && it.canRead()) rootDirs.add(it)
                }
            } catch (_: Throwable) {}

            // 2. Downloads Directory
            try {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let {
                    if (it.exists() && it.canRead()) rootDirs.add(it)
                }
            } catch (_: Throwable) {}

            // 3. App-internal PDF directory
            val internalPdfDir = File(context.filesDir, "pdfs")
            if (internalPdfDir.exists() && internalPdfDir.canRead()) {
                rootDirs.add(internalPdfDir)
            }

            // 4. Common storage paths
            val commonPaths = listOf(
                "/storage/emulated/0/Documents",
                "/storage/emulated/0/Download",
                "/storage/emulated/0/Downloads",
                "/storage/emulated/0/Books",
                "/storage/emulated/0/PDF"
            )

            for (p in commonPaths) {
                try {
                    val f = File(p)
                    if (f.exists() && f.canRead()) {
                        rootDirs.add(f)
                    }
                } catch (_: Throwable) {}
            }

            val visitedDirPaths = HashSet<String>()

            for (dir in rootDirs) {
                if (discovered.size >= MAX_DISCOVERED_LIMIT) break
                scanDirectoryRecursively(dir, discovered, seenFingerprints, seenCanonicalPaths, visitedDirPaths, depth = 0)
            }
        } catch (_: Throwable) {}
    }

    private fun scanDirectoryRecursively(
        dir: File,
        discovered: MutableList<DiscoveredPdfFile>,
        seenFingerprints: HashSet<String>,
        seenCanonicalPaths: HashSet<String>,
        visitedDirPaths: HashSet<String>,
        depth: Int
    ) {
        if (depth > MAX_SCAN_DEPTH || discovered.size >= MAX_DISCOVERED_LIMIT) return
        val canonicalDir = try { dir.canonicalPath.lowercase() } catch (_: Throwable) { dir.absolutePath.lowercase() }
        if (!visitedDirPaths.add(canonicalDir)) return

        try {
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (discovered.size >= MAX_DISCOVERED_LIMIT) break
                if (file.isDirectory) {
                    val dName = file.name
                    if (!dName.startsWith(".") &&
                        !dName.equals("Android", ignoreCase = true) &&
                        !dName.equals(".cache", ignoreCase = true) &&
                        !dName.equals("cache", ignoreCase = true) &&
                        !dName.equals("node_modules", ignoreCase = true)
                    ) {
                        scanDirectoryRecursively(file, discovered, seenFingerprints, seenCanonicalPaths, visitedDirPaths, depth + 1)
                    }
                } else if (file.isFile) {
                    val name = file.name ?: continue
                    if (name.endsWith(".pdf", ignoreCase = true)) {
                        val size = file.length()
                        if (size in 1..MAX_PDF_FILE_SIZE) {
                            val canonical = try { file.canonicalPath.lowercase() } catch (_: Throwable) { file.absolutePath.lowercase() }
                            val fingerprint = "${name.trim().lowercase()}::$size"

                            if (seenCanonicalPaths.add(canonical) && seenFingerprints.add(fingerprint)) {
                                discovered.add(
                                    DiscoveredPdfFile(
                                        uriString = file.absolutePath,
                                        displayName = name,
                                        size = size,
                                        lastModified = file.lastModified(),
                                        canonicalPath = canonical
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    private fun scanSafTree(
        context: Context,
        treeUriString: String,
        discovered: MutableList<DiscoveredPdfFile>,
        seenFingerprints: HashSet<String>
    ) {
        try {
            val treeUri = Uri.parse(treeUriString)
            val docFile = DocumentFile.fromTreeUri(context, treeUri) ?: return
            if (!docFile.canRead()) return

            scanSafDirectory(docFile, discovered, seenFingerprints, depth = 0)
        } catch (_: Throwable) {}
    }

    private fun scanSafDirectory(
        docDir: DocumentFile,
        discovered: MutableList<DiscoveredPdfFile>,
        seenFingerprints: HashSet<String>,
        depth: Int
    ) {
        if (depth > MAX_SCAN_DEPTH || discovered.size >= MAX_DISCOVERED_LIMIT) return
        try {
            val files = docDir.listFiles()
            for (file in files) {
                if (discovered.size >= MAX_DISCOVERED_LIMIT) break
                if (file.isDirectory) {
                    scanSafDirectory(file, discovered, seenFingerprints, depth + 1)
                } else if (file.isFile) {
                    val name = file.name ?: ""
                    if (name.endsWith(".pdf", ignoreCase = true) || file.type == "application/pdf") {
                        val size = file.length()
                        val fingerprint = "${name.trim().lowercase()}::$size"
                        if (seenFingerprints.add(fingerprint)) {
                            val uriStr = file.uri.toString()
                            discovered.add(
                                DiscoveredPdfFile(
                                    uriString = uriStr,
                                    displayName = name,
                                    size = size,
                                    lastModified = file.lastModified()
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
    }
}
