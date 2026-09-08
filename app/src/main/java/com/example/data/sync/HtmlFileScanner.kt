package com.example.data.sync

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream

data class DiscoveredHtmlFile(
    val uriString: String,
    val displayName: String,
    val size: Long,
    val lastModified: Long,
    val canonicalPath: String? = null,
    val initialContent: String? = null
)

object HtmlFileScanner {

    private const val MAX_SCAN_DEPTH = 6
    private const val MAX_HTML_FILE_SIZE = 5 * 1024 * 1024L // 5MB limit
    private const val MAX_DISCOVERED_LIMIT = 250 // Prevent storage scan OOM

    /**
     * Discovers all HTML and HTM files across device storage using MediaStore,
     * standard public external directories, and custom SAF folder tree.
     * Uses unified canonical path and fingerprint tracking to guarantee ZERO duplicate entries.
     */
    fun scanDeviceForHtmlFiles(
        context: Context,
        customTreeUriString: String? = null
    ): List<DiscoveredHtmlFile> {
        val discovered = mutableListOf<DiscoveredHtmlFile>()
        val seenFingerprints = HashSet<String>()
        val seenCanonicalPaths = HashSet<String>()

        try {
            // 1. Scan user-selected SAF tree URI first if configured
            if (!customTreeUriString.isNullOrBlank()) {
                scanSafTree(context, customTreeUriString, discovered, seenFingerprints)
            }

            // 2. Scan standard external & internal storage directories
            scanExternalDirectories(context, discovered, seenFingerprints, seenCanonicalPaths)

            // 3. Scan via MediaStore (Files table)
            scanMediaStore(context, discovered, seenFingerprints, seenCanonicalPaths)
        } catch (_: Throwable) {
            // Master fallback safety wrapper
        }

        return discovered
    }

    private fun scanMediaStore(
        context: Context,
        discovered: MutableList<DiscoveredHtmlFile>,
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

            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.html' OR " +
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.htm' OR " +
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.xhtml' OR " +
                    "${MediaStore.Files.FileColumns.MIME_TYPE} = 'text/html'"

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
                                DiscoveredHtmlFile(
                                    uriString = itemUri,
                                    displayName = cleanName,
                                    size = size,
                                    lastModified = dateMs,
                                    canonicalPath = dataPath
                                )
                            )
                        } catch (_: Throwable) {
                            // Row level safety
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // MediaStore query fallback gracefully
        }
    }

    private fun scanExternalDirectories(
        context: Context,
        discovered: MutableList<DiscoveredHtmlFile>,
        seenFingerprints: HashSet<String>,
        seenCanonicalPaths: HashSet<String>
    ) {
        try {
            val rootDirs = mutableListOf<File>()

            // 1. Primary Public Directories
            try {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.let {
                    if (it.exists() && it.canRead()) rootDirs.add(it)
                }
            } catch (_: Throwable) {}

            try {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let {
                    if (it.exists() && it.canRead()) rootDirs.add(it)
                }
            } catch (_: Throwable) {}

            // 2. Common Android storage paths
            val commonPaths = listOf(
                "/storage/emulated/0/Documents",
                "/storage/emulated/0/Download",
                "/storage/emulated/0/Downloads",
                "/storage/emulated/0/Notes",
                "/storage/emulated/0/HTML"
            )
            for (p in commonPaths) {
                try {
                    val f = File(p)
                    if (f.exists() && f.isDirectory && f.canRead()) {
                        rootDirs.add(f)
                    }
                } catch (_: Throwable) {}
            }

            // 3. App-specific External Files & Cache Dirs
            try {
                context.getExternalFilesDirs(null).filterNotNull().forEach { dir ->
                    if (dir.exists() && dir.canRead()) rootDirs.add(dir)
                }
            } catch (_: Throwable) {}

            try {
                context.filesDir?.let { if (it.exists() && it.canRead()) rootDirs.add(it) }
            } catch (_: Throwable) {}

            val visitedDirPaths = HashSet<String>()

            // Scan each root safely
            for (dir in rootDirs) {
                if (discovered.size >= MAX_DISCOVERED_LIMIT) break
                scanDirectoryRecursively(dir, 0, discovered, seenFingerprints, seenCanonicalPaths, visitedDirPaths)
            }
        } catch (_: Throwable) {
            // Fallback gracefully
        }
    }

    private fun scanDirectoryRecursively(
        dir: File,
        depth: Int,
        discovered: MutableList<DiscoveredHtmlFile>,
        seenFingerprints: HashSet<String>,
        seenCanonicalPaths: HashSet<String>,
        visitedDirPaths: HashSet<String>
    ) {
        if (depth > MAX_SCAN_DEPTH || discovered.size >= MAX_DISCOVERED_LIMIT) return

        val canonicalDir = try {
            dir.canonicalPath.lowercase()
        } catch (_: Throwable) {
            dir.absolutePath.lowercase()
        }

        if (!visitedDirPaths.add(canonicalDir)) {
            // Prevent symlink cycles and re-entering visited directories
            return
        }

        val files = try {
            dir.listFiles()
        } catch (_: Throwable) {
            null
        } ?: return

        for (file in files) {
            if (discovered.size >= MAX_DISCOVERED_LIMIT) break
            val name = file.name ?: continue

            // Skip system, temporary, cache, and hidden directories
            if (name.startsWith(".") ||
                name.equals("Android", ignoreCase = true) ||
                name.equals("node_modules", ignoreCase = true) ||
                name.equals(".cache", ignoreCase = true) ||
                name.equals("cache", ignoreCase = true) ||
                name.equals("LOST.DIR", ignoreCase = true)
            ) {
                continue
            }

            try {
                if (file.isDirectory) {
                    scanDirectoryRecursively(file, depth + 1, discovered, seenFingerprints, seenCanonicalPaths, visitedDirPaths)
                } else if (file.isFile) {
                    val isHtml = name.endsWith(".html", ignoreCase = true) ||
                            name.endsWith(".htm", ignoreCase = true) ||
                            name.endsWith(".xhtml", ignoreCase = true)

                    if (isHtml) {
                        val canonical = try { file.canonicalPath.lowercase() } catch (_: Throwable) { file.absolutePath.lowercase() }
                        val fileSize = file.length()
                        val fingerprint = "${name.trim().lowercase()}::$fileSize"

                        if (seenCanonicalPaths.add(canonical) && seenFingerprints.add(fingerprint)) {
                            val pathKey = file.absolutePath
                            discovered.add(
                                DiscoveredHtmlFile(
                                    uriString = pathKey,
                                    displayName = name,
                                    size = fileSize,
                                    lastModified = file.lastModified(),
                                    canonicalPath = canonical
                                )
                            )
                        }
                    }
                }
            } catch (_: Throwable) {
                // Individual file safety
            }
        }
    }

    private fun scanSafTree(
        context: Context,
        treeUriString: String,
        discovered: MutableList<DiscoveredHtmlFile>,
        seenFingerprints: HashSet<String>
    ) {
        try {
            val treeUri = Uri.parse(treeUriString)
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return
            scanDocumentFileRecursively(rootDoc, 0, discovered, seenFingerprints)
        } catch (_: Throwable) {
            // SAF tree scan fallback gracefully
        }
    }

    private fun scanDocumentFileRecursively(
        doc: DocumentFile,
        depth: Int,
        discovered: MutableList<DiscoveredHtmlFile>,
        seenFingerprints: HashSet<String>
    ) {
        if (depth > MAX_SCAN_DEPTH || discovered.size >= MAX_DISCOVERED_LIMIT || !doc.canRead()) return

        val children = try {
            doc.listFiles()
        } catch (_: Throwable) {
            emptyArray()
        }

        for (child in children) {
            if (discovered.size >= MAX_DISCOVERED_LIMIT) break
            val name = child.name ?: continue
            if (name.startsWith(".")) continue

            try {
                if (child.isDirectory) {
                    scanDocumentFileRecursively(child, depth + 1, discovered, seenFingerprints)
                } else if (child.isFile) {
                    val isHtml = name.endsWith(".html", ignoreCase = true) ||
                            name.endsWith(".htm", ignoreCase = true) ||
                            name.endsWith(".xhtml", ignoreCase = true) ||
                            child.type == "text/html"

                    if (isHtml) {
                        val size = child.length()
                        val fingerprint = "${name.trim().lowercase()}::$size"
                        if (seenFingerprints.add(fingerprint)) {
                            val uriStr = child.uri.toString()
                            discovered.add(
                                DiscoveredHtmlFile(
                                    uriString = uriStr,
                                    displayName = name,
                                    size = size,
                                    lastModified = child.lastModified()
                                )
                            )
                        }
                    }
                }
            } catch (_: Throwable) {
                // Individual document file safety
            }
        }
    }

    /**
     * Efficiently and safely reads file content from a URI or direct file path.
     */
    fun readFileContent(context: Context, source: String, maxChars: Int = MAX_HTML_FILE_SIZE.toInt()): String? {
        return try {
            val inputStream: InputStream? = when {
                source.startsWith("content://") -> {
                    try {
                        context.contentResolver.openInputStream(Uri.parse(source))
                    } catch (_: Throwable) {
                        null
                    }
                }
                source.startsWith("file://") -> {
                    try {
                        context.contentResolver.openInputStream(Uri.parse(source))
                    } catch (_: Throwable) {
                        File(Uri.parse(source).path ?: "").inputStream()
                    }
                }
                else -> {
                    try {
                        File(source).inputStream()
                    } catch (_: Throwable) {
                        null
                    }
                }
            }

            inputStream?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val buffer = CharArray(8192)
                    val sb = StringBuilder()
                    var totalChars = 0
                    var read: Int
                    while (reader.read(buffer).also { read = it } != -1) {
                        sb.append(buffer, 0, read)
                        totalChars += read
                        if (totalChars >= maxChars) {
                            break
                        }
                    }
                    sb.toString()
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Extracts an intelligent title from HTML source (or filename).
     */
    fun extractTitleFromHtml(htmlContent: String, defaultName: String): String {
        try {
            if (htmlContent.isNotBlank()) {
                val titleMatch = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE).find(htmlContent)
                if (titleMatch != null) {
                    val t = titleMatch.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                    if (t.isNotBlank()) return t.take(60)
                }
                val h1Match = Regex("<h1[^>]*>(.*?)</h1>", RegexOption.IGNORE_CASE).find(htmlContent)
                if (h1Match != null) {
                    val t = h1Match.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                    if (t.isNotBlank()) return t.take(60)
                }
            }
        } catch (_: Throwable) {}
        return defaultName.substringBeforeLast(".").replace(Regex("[_-]+"), " ").trim()
    }
}
