package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.data.db.AppDatabase
import com.example.data.model.NoteEntity
import com.example.util.DateFormatter

class GlassNotesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getDatabase(context)
        val pinnedNotes: List<NoteEntity> = try {
            db.noteDao().getPinnedNotesDirect()
        } catch (_: Exception) {
            emptyList()
        }

        provideContent {
            WidgetContent(context = context, notes = pinnedNotes)
        }
    }

    @Composable
    private fun WidgetContent(context: Context, notes: List<NoteEntity>) {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val createNoteIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_CREATE)
        }

        // Frosted dark glass aesthetic for home screen widget
        val widgetBg = Color(0xEB16181D)
        val cardBg = Color(0x33FFFFFF)
        val textPrimary = Color(0xFFF0F3F8)
        val textSecondary = Color(0xFFB0B8C4)
        val textTertiary = Color(0xFF7A8494)
        val amberAccent = Color(0xFFF2B90C)
        val amberText = Color(0xFF1E1500)
        val dividerColor = Color(0x1FFFFFFF)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(widgetBg))
                .cornerRadius(22.dp)
                .padding(12.dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .clickable(actionStartActivity(openAppIntent)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Amber Glow Pin Icon placeholder
                        Box(
                            modifier = GlanceModifier
                                .size(24.dp)
                                .background(ColorProvider(Color(0x33F2B90C)))
                                .cornerRadius(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "📌",
                                style = TextStyle(fontSize = 12.sp)
                            )
                        }

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        Text(
                            text = "Pinned Notes",
                            style = TextStyle(
                                color = ColorProvider(textPrimary),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        if (notes.isNotEmpty()) {
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            Box(
                                modifier = GlanceModifier
                                    .background(ColorProvider(Color(0x2EFFFFFF)))
                                    .cornerRadius(10.dp)
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = notes.size.toString(),
                                    style = TextStyle(
                                        color = ColorProvider(textSecondary),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }

                    // Quick Create Note Button
                    Box(
                        modifier = GlanceModifier
                            .size(28.dp)
                            .background(ColorProvider(amberAccent))
                            .cornerRadius(14.dp)
                            .clickable(actionStartActivity(createNoteIntent)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            style = TextStyle(
                                color = ColorProvider(amberText),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                // Divider below header
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ColorProvider(dividerColor))
                ) {}

                Spacer(modifier = GlanceModifier.height(8.dp))

                // Notes List or Empty State
                if (notes.isEmpty()) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .clickable(actionStartActivity(openAppIntent)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No pinned notes",
                                style = TextStyle(
                                    color = ColorProvider(textSecondary),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Text(
                                text = "Pin notes in Glass Notes to view here",
                                style = TextStyle(
                                    color = ColorProvider(textTertiary),
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = GlanceModifier.fillMaxSize()
                    ) {
                        items(notes) { note ->
                            val openNoteIntent = Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, note.id)
                            }

                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .background(ColorProvider(cardBg))
                                    .cornerRadius(12.dp)
                                    .clickable(actionStartActivity(openNoteIntent))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Column(modifier = GlanceModifier.fillMaxWidth()) {
                                    Row(
                                        modifier = GlanceModifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (note.title.isNotBlank()) note.title else "Untitled Note",
                                            style = TextStyle(
                                                color = ColorProvider(textPrimary),
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            maxLines = 1
                                        )

                                        if (note.type == "html") {
                                            Spacer(modifier = GlanceModifier.width(6.dp))
                                            Box(
                                                modifier = GlanceModifier
                                                    .background(ColorProvider(Color(0x33F2B90C)))
                                                    .cornerRadius(4.dp)
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "HTML",
                                                    style = TextStyle(
                                                        color = ColorProvider(amberAccent),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    if (note.snippet.isNotBlank()) {
                                        Spacer(modifier = GlanceModifier.height(2.dp))
                                        Text(
                                            text = note.snippet,
                                            style = TextStyle(
                                                color = ColorProvider(textSecondary),
                                                fontSize = 11.5.sp
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
