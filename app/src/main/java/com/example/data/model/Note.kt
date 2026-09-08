package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["pinned"]),
        Index(value = ["updatedAt"]),
        Index(value = ["hash"]),
        Index(value = ["source"]),
        Index(value = ["category"]),
        Index(value = ["isDeleted"])
    ]
)
data class NoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString().take(12),
    val title: String = "",
    val type: String = "text", // "text" or "html"
    val content: String = "",
    val pinned: Boolean = false,
    val source: String? = null,
    val category: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val hash: String = "",
    val isLocked: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
) {
    val displayTitle: String
        get() {
            if (title.isNotBlank()) return title
            val preview = content.take(1000)
            val cleanContent = preview
                .replace(Regex("<[^>]*>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            return if (cleanContent.isNotBlank()) cleanContent.take(60) else "Untitled"
        }

    val snippet: String
        get() {
            var s = if (type == "html") {
                content.take(2000)
                    .replace(Regex("(?is)<style.*?>.*?</style>"), "")
                    .replace(Regex("<[^>]*>"), " ")
            } else {
                content.take(500)
            }
            s = s.replace(Regex("\\s+"), " ").trim()
            return if (s.length > 160) s.take(160) + "…" else s
        }

    val wordCount: Int
        get() {
            val trimmed = content.trim()
            if (trimmed.isEmpty()) return 0
            return trimmed.split(Regex("\\s+")).size
        }

    val readingTimeMin: Int
        get() = maxOf(1, (wordCount + 199) / 200)
}

data class NoteSummary(
    val id: String,
    val title: String,
    val type: String,
    val pinned: Boolean,
    val source: String?,
    val category: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isLocked: Boolean,
    val snippetPreview: String,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
) {
    val displayTitle: String
        get() {
            if (title.isNotBlank()) return title
            val cleanContent = snippetPreview
                .replace(Regex("<[^>]*>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            return if (cleanContent.isNotBlank()) cleanContent.take(60) else "Untitled"
        }
}
