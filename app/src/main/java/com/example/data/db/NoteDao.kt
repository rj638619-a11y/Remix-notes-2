package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.NoteEntity
import com.example.data.model.NoteSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT id, title, type, pinned, source, category, createdAt, updatedAt, isLocked, substr(content, 1, 1000) AS snippetPreview, isDeleted, deletedAt FROM notes WHERE isDeleted = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun getAllNoteSummaries(): Flow<List<NoteSummary>>

    @Query("SELECT * FROM notes WHERE isDeleted = 1 ORDER BY deletedAt DESC, updatedAt DESC")
    fun getTrashNotes(): Flow<List<NoteEntity>>

    @Query("SELECT id, title, type, pinned, source, category, createdAt, updatedAt, isLocked, substr(content, 1, 1000) AS snippetPreview, isDeleted, deletedAt FROM notes WHERE isDeleted = 1 ORDER BY deletedAt DESC, updatedAt DESC")
    fun getTrashNoteSummaries(): Flow<List<NoteSummary>>

    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 1")
    fun getTrashCount(): Flow<Int>

    @Query("SELECT * FROM notes WHERE pinned = 1 AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getPinnedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE pinned = 1 AND isDeleted = 0 ORDER BY updatedAt DESC")
    suspend fun getPinnedNotesDirect(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun getNoteById(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteByIdDirect(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE hash = :hash AND isDeleted = 0 LIMIT 1")
    suspend fun getNoteByHash(hash: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE source = :source AND isDeleted = 0 LIMIT 1")
    suspend fun getNoteBySource(source: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE isDeleted = 0")
    suspend fun getAllNotesDirect(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE source IS NOT NULL AND isDeleted = 0")
    suspend fun getSyncedNotes(): List<NoteEntity>

    @Query("UPDATE notes SET content = :content, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateNoteContent(id: String, content: String, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("UPDATE notes SET isDeleted = 1, deletedAt = :deletedAt, pinned = 0 WHERE id = :id")
    suspend fun moveToTrash(id: String, deletedAt: Long)

    @Query("UPDATE notes SET isDeleted = 0, deletedAt = NULL WHERE id = :id")
    suspend fun restoreFromTrash(id: String)

    @Query("UPDATE notes SET isDeleted = 0, deletedAt = NULL WHERE isDeleted = 1")
    suspend fun restoreAllFromTrash()

    @Query("DELETE FROM notes WHERE isDeleted = 1")
    suspend fun emptyTrash()

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNotePermanently(id: String)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: String)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteNotesByIds(ids: List<String>)

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()
}
