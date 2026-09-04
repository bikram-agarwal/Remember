package dev.bikram.remember.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.remember.diagnostics.DiagnosticLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotesWidgetUpdater
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val refreshMutex = Mutex()

        @Volatile
        private var refreshPending = false

        suspend fun refreshAll() {
            refreshPending = true
            if (!refreshMutex.tryLock()) return
            try {
                do {
                    refreshPending = false
                    delay(REFRESH_DEBOUNCE_MILLIS)
                    performRefresh()
                } while (refreshPending)
            } finally {
                refreshMutex.unlock()
            }
        }

        private suspend fun performRefresh() {
            runCatching {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val notesIds = widgetIds(appWidgetManager, NotesWidgetReceiver::class.java)
                val quickIds = widgetIds(appWidgetManager, QuickCaptureWidgetReceiver::class.java)
                val selectedIds = widgetIds(appWidgetManager, SelectedWidgetReceiver::class.java)
                NotesWidget().updateAll(context)
                QuickCaptureWidget().updateAll(context)
                SelectedWidget().updateAll(context)
                requestProviderUpdate(NotesWidgetReceiver::class.java, notesIds)
                requestProviderUpdate(QuickCaptureWidgetReceiver::class.java, quickIds)
                requestProviderUpdate(SelectedWidgetReceiver::class.java, selectedIds)
            }.onFailure { throwable ->
                Log.w(TAG, "Failed to refresh notes widget", throwable)
                DiagnosticLog.record(context, "Failed to refresh notes widgets", throwable)
            }
        }

        private fun widgetIds(
            appWidgetManager: AppWidgetManager,
            receiverClass: Class<*>,
        ): IntArray =
            appWidgetManager.getAppWidgetIds(
                ComponentName(context, receiverClass),
            )

        private fun requestProviderUpdate(
            receiverClass: Class<*>,
            appWidgetIds: IntArray,
        ) {
            if (appWidgetIds.isEmpty()) return
            context.sendBroadcast(
                Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    component = ComponentName(context, receiverClass)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                },
            )
        }

        private companion object {
            private const val TAG = "NotesWidgetUpdater"
            private const val REFRESH_DEBOUNCE_MILLIS = 250L
        }
    }
