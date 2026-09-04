package dev.bikram.remember.widget

import android.content.Context
import androidx.annotation.Keep
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

@Keep
class SelectedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SelectedWidget()

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        super.onDeleted(context, appWidgetIds)
        SelectedNotesWidgetConfigStore.deleteConfigs(context, appWidgetIds)
    }
}

typealias SelectedNotesWidgetReceiver = SelectedWidgetReceiver
