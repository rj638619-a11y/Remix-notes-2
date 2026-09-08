package com.example.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.data.model.NoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object PdfHelper {

    fun getPdfsDir(context: Context): File {
        val dir = File(context.filesDir, "pdfs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun getPdfFileForNote(context: Context, note: NoteEntity): File = withContext(Dispatchers.IO) {
        val pdfsDir = getPdfsDir(context)

        // If content points to existing PDF file
        if (note.content.startsWith("/") || note.content.startsWith("file://")) {
            val path = note.content.removePrefix("file://")
            val existing = File(path)
            if (existing.exists() && existing.length() > 0) {
                return@withContext existing
            }
        }

        // Check if cached PDF file exists for note ID
        val cachedFile = File(pdfsDir, "${note.id}.pdf")
        if (cachedFile.exists() && cachedFile.length() > 0 && cachedFile.lastModified() >= note.updatedAt) {
            return@withContext cachedFile
        }

        // If content or source is a content:// URI, copy it to cache
        val uriStr = if (note.content.startsWith("content://")) note.content else if (note.source?.startsWith("content://") == true) note.source else null
        if (uriStr != null) {
            try {
                val uri = Uri.parse(uriStr)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(cachedFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (cachedFile.exists() && cachedFile.length() > 0) {
                    return@withContext cachedFile
                }
            } catch (_: Throwable) {}
        }

        // Generate a new PDF file for this note
        return@withContext generatePdfFile(context, note, cachedFile)
    }

    suspend fun saveImportedPdfUri(context: Context, uri: Uri, originalName: String): NoteEntity = withContext(Dispatchers.IO) {
        val pdfsDir = getPdfsDir(context)
        val noteId = UUID.randomUUID().toString().take(12)
        val destFile = File(pdfsDir, "$noteId.pdf")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }

        val cleanTitle = originalName.substringBeforeLast(".").replace(Regex("[_-]+"), " ").ifBlank { "PDF Document" }
        val now = System.currentTimeMillis()

        NoteEntity(
            id = noteId,
            title = cleanTitle,
            type = "pdf",
            content = destFile.absolutePath,
            pinned = false,
            source = uri.toString(),
            createdAt = now,
            updatedAt = now,
            hash = HashUtil.sha256("pdf:${cleanTitle.lowercase().trim()}:${destFile.length()}")
        )
    }

    private fun generatePdfFile(context: Context, note: NoteEntity, outputFile: File): File {
        val document = PdfDocument()
        val pageWidth = 595 // A4 standard width in points
        val pageHeight = 842 // A4 standard height in points
        val margin = 40f
        val contentWidth = pageWidth - (margin * 2)

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 13f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val lineSpacing = 20f
        val cleanContent = if (note.type == "html") {
            note.content
                .replace(Regex("(?is)<style.*?>.*?</style>"), "")
                .replace(Regex("<br\\s*/?>"), "\n")
                .replace(Regex("</p>"), "\n\n")
                .replace(Regex("</li>"), "\n")
                .replace(Regex("<[^>]*>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim()
        } else {
            note.content
        }

        val lines = mutableListOf<String>()
        val rawLines = cleanContent.split("\n")
        for (raw in rawLines) {
            if (raw.isBlank()) {
                lines.add("")
                continue
            }
            var current = ""
            val words = raw.split(" ")
            for (word in words) {
                val test = if (current.isEmpty()) word else "$current $word"
                if (bodyPaint.measureText(test) <= contentWidth) {
                    current = test
                } else {
                    if (current.isNotEmpty()) lines.add(current)
                    current = word
                }
            }
            if (current.isNotEmpty()) lines.add(current)
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        var y = margin + 30f

        // Draw Document Header
        val displayTitle = note.displayTitle
        canvas.drawText(displayTitle, margin, y, titlePaint)
        y += 35f

        val headerSubPaint = Paint().apply {
            color = Color.GRAY
            textSize = 10f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val dateStr = java.text.SimpleDateFormat("MMM dd, yyyy · HH:mm", java.util.Locale.getDefault()).format(java.util.Date(note.updatedAt))
        canvas.drawText("PDF Reader Document · $dateStr", margin, y, headerSubPaint)
        y += 25f

        // Draw Divider
        val dividerPaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
        y += 25f

        // Draw Body Lines
        for (line in lines) {
            if (y + lineSpacing > pageHeight - margin) {
                // Finish current page
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = margin + 30f
            }

            if (line.isNotEmpty()) {
                canvas.drawText(line, margin, y, bodyPaint)
            }
            y += lineSpacing
        }

        document.finishPage(page)

        FileOutputStream(outputFile).use { out ->
            document.writeTo(out)
        }
        document.close()

        return outputFile
    }
}
