package dev.bikram.remember

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import dev.bikram.remember.backup.RememberBackupWork
import dev.bikram.remember.di.AppContainer
import dev.bikram.remember.reminders.ReminderScheduler
import dev.bikram.remember.widget.NotesWidget
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class RememberApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ensureReminderChannel()
        scheduleWidgetRefreshes()
        container.backupExportCoordinator.start()
        container.applicationScope.launch {
            RememberBackupWork.updateSchedule(this@RememberApp, container.backupPrefs.snapshot())
        }
    }

    private fun scheduleWidgetRefreshes() {
        container.noteRepository.observeActive()
            .drop(1) // skip initial emission
            .onEach {
                runCatching {
                    val mgr = GlanceAppWidgetManager(this@RememberApp)
                    val ids = mgr.getGlanceIds(NotesWidget::class.java)
                    val widget = NotesWidget()
                    ids.forEach { widget.update(this@RememberApp, it) }
                }
            }
            .launchIn(container.applicationScope)
    }

    private fun ensureReminderChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            ReminderScheduler.CHANNEL_ID,
            "Reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Note reminders" }
        nm.createNotificationChannel(channel)
    }

}
