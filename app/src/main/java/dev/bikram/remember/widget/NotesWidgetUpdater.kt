package dev.bikram.remember.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotesWidgetUpdater
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        suspend fun refreshAll() {
            runCatching {
                val glanceManager = GlanceAppWidgetManager(context)
                val widget = NotesWidget()
                glanceManager.getGlanceIds(NotesWidget::class.java).forEach { glanceId ->
                    widget.update(context, glanceId)
                }
            }.onFailure { throwable ->
                Log.w(TAG, "Failed to refresh notes widget", throwable)
            }
        }

        private companion object {
            private const val TAG = "NotesWidgetUpdater"
        }
    }
