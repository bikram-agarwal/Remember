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
        observeReminderPrefs()
        observeReminderSummarySource()
        container.backupExportCoordinator.start()
        container.applicationScope.launch {
            RememberBackupWork.updateSchedule(this@RememberApp, container.backupPrefs.snapshot())
            container.noteRepository.refreshReminderSummaryNotification()
        }
        RememberTrashSweepWork.ensureScheduled(this)
    }

    private fun observeReminderPrefs() {
        container.reminderPrefs.state
            .distinctUntilChanged()
            .onEach {
                container.noteRepository.refreshActiveReminderNotifications()
                container.noteRepository.refreshReminderSummaryNotification()
            }
            .launchIn(container.applicationScope)
    }

    private fun observeReminderSummarySource() {
        container.noteRepository.observeActive()
            .drop(1)
            .onEach {
                container.noteRepository.refreshReminderSummaryNotification()
            }
            .launchIn(container.applicationScope)
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
            getString(R.string.notification_channel_reminders_low),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.notification_channel_reminders_low_desc) }
        nm.createNotificationChannel(lowChannel)

        val defaultChannel = NotificationChannel(
            ReminderScheduler.CHANNEL_ID_DEFAULT,
            getString(R.string.notification_channel_reminders_normal),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = getString(R.string.notification_channel_reminders_normal_desc) }
        nm.createNotificationChannel(defaultChannel)

        // For Android to flip on the per-channel "Pop on screen" (heads-up) toggle
        // automatically, the channel must be IMPORTANCE_HIGH AND have a non-null
        // sound. A silent or sound-less HIGH channel does not heads-up. The audio
        // attributes use USAGE_NOTIFICATION_RINGTONE so the system treats this as a
        // user-attention sound that bypasses normal media-volume ducking rules.
        val highChannel = NotificationChannel(
            ReminderScheduler.CHANNEL_ID_HIGH,
            getString(R.string.notification_channel_reminders_high),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.notification_channel_reminders_high_desc)
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

        val summaryChannel = NotificationChannel(
            ReminderScheduler.CHANNEL_ID_SUMMARY,
            getString(R.string.notification_channel_reminder_summary),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_reminder_summary_desc)
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(summaryChannel)
    }

}
