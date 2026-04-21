package dev.bikram.remember

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.glance.appwidget.GlanceAppWidgetManager
import dev.bikram.remember.backup.RememberBackupWork
import dev.bikram.remember.di.AppContainer
import dev.bikram.remember.quickcapture.QuickCaptureNotifier
import dev.bikram.remember.reminders.ReminderScheduler
import dev.bikram.remember.trash.RememberTrashSweepWork
import dev.bikram.remember.widget.NotesWidget
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class RememberApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ensureReminderChannel()
        QuickCaptureNotifier.ensureChannel(this)
        scheduleWidgetRefreshes()
        observeQuickCapturePref()
        container.backupExportCoordinator.start()
        container.applicationScope.launch {
            RememberBackupWork.updateSchedule(this@RememberApp, container.backupPrefs.snapshot())
        }
        RememberTrashSweepWork.ensureScheduled(this)
    }

    private fun observeQuickCapturePref() {
        container.quickCapturePrefs.state
            .map { it.enabled }
            .distinctUntilChanged()
            .onEach { enabled ->
                if (enabled) {
                    QuickCaptureNotifier.show(this@RememberApp)
                } else {
                    QuickCaptureNotifier.hide(this@RememberApp)
                }
            }
            .launchIn(container.applicationScope)
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

        // Drop pre-versioned channels. Channel settings are immutable after first
        // registration, so the only way to fix a low-importance "reminder_high" left
        // over from an older build is to delete and re-register under a new id.
        ReminderScheduler.LEGACY_CHANNEL_IDS.forEach(nm::deleteNotificationChannel)

        val lowChannel = NotificationChannel(
            ReminderScheduler.CHANNEL_ID_LOW,
            "Reminders (Low)",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Silent note reminders" }
        nm.createNotificationChannel(lowChannel)

        val defaultChannel = NotificationChannel(
            ReminderScheduler.CHANNEL_ID_DEFAULT,
            "Reminders (Normal)",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Note reminders" }
        nm.createNotificationChannel(defaultChannel)

        // For Android to flip on the per-channel "Pop on screen" (heads-up) toggle
        // automatically, the channel must be IMPORTANCE_HIGH AND have a non-null
        // sound. A silent or sound-less HIGH channel does not heads-up. The audio
        // attributes use USAGE_NOTIFICATION_RINGTONE so the system treats this as a
        // user-attention sound that bypasses normal media-volume ducking rules.
        val highChannel = NotificationChannel(
            ReminderScheduler.CHANNEL_ID_HIGH,
            "Reminders (High)",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Important note reminders"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 200, 250)
            enableLights(true)
            setBypassDnd(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        nm.createNotificationChannel(highChannel)
    }

}
