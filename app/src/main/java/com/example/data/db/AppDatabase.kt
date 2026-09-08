package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.NoteEntity
import com.example.util.HashUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import androidx.room.migration.Migration

@Database(entities = [NoteEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE notes ADD COLUMN category TEXT DEFAULT NULL")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_category` ON notes (`category`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE notes ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE notes ADD COLUMN deletedAt INTEGER DEFAULT NULL")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_isDeleted` ON notes (`isDeleted`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "glass_notes_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialNotes(database.noteDao())
                    }
                }
            }
        }

        suspend fun populateInitialNotes(dao: NoteDao) {
            val now = System.currentTimeMillis()

            val textNoteContent = """# Welcome to Glass Notes

A quiet home for your notes, with a liquid-glass finish.

## Getting started
- Tap the amber + button to create a note.
- Choose **Create with Text** or **Create with HTML Code**.
- Switch to **Read** mode to see this note formatted — headings, bullets and checkboxes all render.
- Long-press any note for the full menu: full screen, Timer, Stopwatch, copy, pin, duplicate, export, share, delete.
- Tap the expand icon for **true full screen** — content fills every edge, the title and bars disappear, the cursor auto-hides.
- Tap Sync in the dock to pull in .txt, .md and .html files from a device folder every 5 minutes. Duplicates are never created.

## Productivity boost
- A formatting toolbar appears in text **Edit** mode — H1 / H2 / H3, bold, italic, code, bullet list, checkbox, quote, divider, date stamp.
- Inside full screen, tap once to reveal a floating Edit/Read toggle and exit button.
- A reading progress bar appears at the very top in full screen.

## Keyboard shortcuts
- **Esc** — exit full screen, then editor, then sheet
- **F11** or **Ctrl/Cmd + Enter** — toggle full screen
- **Ctrl/Cmd + S** — force-save
- **Ctrl/Cmd + B / I** — bold / italic in text edit mode

## Try a checklist
[x] Open this note
[ ] Write your first note
[ ] Try full screen reading

> Swipe from the left edge inside a note to go back.

Everything is stored on this device only. Export a backup from Settings any time."""

            val htmlNoteContent = """<div style="font-family:Georgia,serif;max-width:560px;margin:0 auto;color:#1d1b16">
  <h1 style="font-size:28px;letter-spacing:-.5px;margin-bottom:4px">HTML notes, rendered</h1>
  <p style="color:#6f6a5c;margin-top:0">This note is raw HTML. Open it in <b>Code</b> mode to see the markup, or stay in <b>Preview</b> to read it like a page.</p>
  <div style="background:#fdf3d7;border:1px solid #f0d98c;border-radius:14px;padding:14px 16px;margin:18px 0">
    <b>Tip</b> — inline styles travel with the note, so your formatting survives export and backup.
  </div>
  <h3 style="margin-bottom:6px">What works in preview</h3>
  <ul style="line-height:1.8">
    <li>Headings, lists, tables and images</li>
    <li>Optional JavaScript — toggle "JS on" in the preview toolbar</li>
    <li>True full-screen reading: expand icon, or the note's three-dot menu</li>
  </ul>
  <blockquote style="margin:18px 0;padding:2px 0 2px 14px;border-left:3px solid #f2b90c;font-style:italic;color:#6f6a5c">Simple enough for a grocery list, strong enough for a snippet library.</blockquote>
</div>"""

            val textNote = NoteEntity(
                id = "welcome-note-1",
                title = "Welcome to Glass Notes",
                type = "text",
                content = textNoteContent,
                pinned = true,
                createdAt = now - 600_000,
                updatedAt = now - 600_000,
                hash = HashUtil.noteHash("Welcome to Glass Notes", textNoteContent)
            )

            val htmlNote = NoteEntity(
                id = "html-demo-note-2",
                title = "HTML preview demo",
                type = "html",
                content = htmlNoteContent,
                pinned = false,
                createdAt = now - 1_200_000,
                updatedAt = now - 1_200_000,
                hash = HashUtil.noteHash("HTML preview demo", htmlNoteContent)
            )

            dao.insertNotes(listOf(textNote, htmlNote))
        }
    }
}
