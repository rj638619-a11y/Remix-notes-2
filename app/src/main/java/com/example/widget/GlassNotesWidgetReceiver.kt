package com.example.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll

class GlassNotesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GlassNotesWidget()

    companion object {
        suspend fun updateAllWidgets(context: Context) {
            try {
                GlassNotesWidget().updateAll(context)
            } catch (_: Exception) {
                // Ignore if no widgets active
            }
        }
    }
}
