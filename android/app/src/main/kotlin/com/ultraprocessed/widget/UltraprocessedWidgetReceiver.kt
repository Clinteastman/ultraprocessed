package com.ultraprocessed.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.runBlocking

class UltraprocessedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UltraprocessedWidget()
}

/**
 * Re-renders every active widget instance. Call this after a sync or
 * after a meal log so the widget stays current without waiting for the
 * 30-min minimum updatePeriodMillis.
 */
object UltraprocessedWidgetUpdater {
    fun update(context: Context) {
        runBlocking {
            val manager = GlanceAppWidgetManager(context)
            val widget = UltraprocessedWidget()
            manager.getGlanceIds(UltraprocessedWidget::class.java).forEach { id ->
                widget.update(context, id)
            }
        }
    }
}
