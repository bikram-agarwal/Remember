package dev.bikram.remember

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.bikram.remember.backup.BackupExportCoordinator
import dev.bikram.remember.backup.RememberBackupWork
import dev.bikram.remember.data.BackupPrefs
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.QuickCapturePrefs
import dev.bikram.remember.data.ReminderPrefs
import dev.bikram.remember.data.UpdateCheckSchedule
import dev.bikram.remember.data.UpdatePrefs
import dev.bikram.remember.di.AppStartupWarmup
import dev.bikram.remember.di.ApplicationScope
import dev.bikram.remember.diagnostics.DiagnosticLog
import dev.bikram.remember.quickcapture.QuickCaptureNotifier
import dev.bikram.remember.reminders.ReminderScheduler
import dev.bikram.remember.reminders.observeNoteRefreshes
import dev.bikram.remember.trash.RememberTrashSweepWork
import dev.bikram.remember.ui.lock.AppLockSession
import dev.bikram.remember.update.PlayInAppUpdateProgressController
import dev.bikram.remember.update.PlayStoreUpdateChecker
import dev.bikram.remember.update.RememberUpdateChecker
import dev.bikram.remember.update.RememberUpdateState
import dev.bikram.remember.update.UpdateAvailableNotifier
import dev.bikram.remember.update.UpdateCheckWorkScheduler
import dev.bikram.remember.widget.NotesWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltAndroidApp
class RememberApp :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var backupExportCoordinator: BackupExportCoordinator

    @Inject lateinit var backupPrefs: BackupPrefs

    @Inject lateinit var noteRepository: NoteRepository

    @Inject lateinit var quickCapturePrefs: QuickCapturePrefs

    @Inject lateinit var reminderPrefs: ReminderPrefs

    @Inject lateinit var updatePrefs: UpdatePrefs

    @Inject lateinit var rememberUpdateChecker: RememberUpdateChecker

    @Inject lateinit var playStoreUpdateChecker: PlayStoreUpdateChecker

    @Inject lateinit var playInAppUpdateProgressController: PlayInAppUpdateProgressController

    @Inject lateinit var rememberUpdateState: RememberUpdateState

    @Inject lateinit var updateAvailableNotifier: UpdateAvailableNotifier

    @Inject lateinit var updateCheckWorkScheduler: UpdateCheckWorkScheduler

    @Inject lateinit var appStartupWarmup: AppStartupWarmup

    @Inject lateinit var notesWidgetUpdater: NotesWidgetUpdater

    @Inject lateinit var appLockSession: AppLockSession

    @ApplicationScope @Inject
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()
        DiagnosticLog.installCrashHandler(this)
        DiagnosticLog.record(this, "RememberApp.onCreate started")
        appLockSession.start()
        appStartupWarmup.start()
        ensureReminderChannel()
        updateAvailableNotifier.ensureNotificationChannel()
        QuickCaptureNotifier.ensureChannel(this)
        applicationScope.observeNoteRefreshes(
            notesSource = noteRepository.observeActive(),
            reminderPreferences = reminderPrefs.state,
            refreshWidgets = notesWidgetUpdater::refreshAll,
            refreshSummary = noteRepository::refreshReminderSummaryNotification,
            refreshActiveNotifications = noteRepository::refreshActiveReminderNotifications,
            refreshCriticalRepeat = noteRepository::refreshCriticalReminderRepeat,
        )
        observeQuickCapturePref()
        backupExportCoordinator.start()
        applicationScope.launch {
            RememberBackupWork.updateSchedule(this@RememberApp, backupPrefs.snapshot())
            updateCheckWorkScheduler.syncFromPreferences()
            runCatching {
                runStartupUpdateCheck()
            }.onFailure { throwable ->
                DiagnosticLog.record(this@RememberApp, "Startup update check failed", throwable)
            }
        }
        RememberTrashSweepWork.ensureScheduled(this)
    }

    private suspend fun runStartupUpdateCheck() {
        if (!BuildConfig.CHECK_UPDATES) return
        val prefs = updatePrefs.snapshot()
        if (prefs.updateCheckSchedule != UpdateCheckSchedule.AT_APP_START) return
        if (BuildConfig.USE_PLAY_IN_APP_UPDATES) {
            val updateInfo =
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    playStoreUpdateChecker.checkForUpdate()
                } ?: return
            rememberUpdateState.showUpdate(updateInfo)
            playInAppUpdateProgressController.ensureInstallStateListenerRegistered()
            return
        }
        val updateInfo =
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                rememberUpdateChecker.checkForUpdate()
            } ?: return
        rememberUpdateState.showUpdate(updateInfo)
        updateAvailableNotifier.notifyIfNewUpdateAvailable(updateInfo, prefs)
    }

    private fun observeQuickCapturePref() {
        quickCapturePrefs.state
            .map { it.enabled }
            .distinctUntilChanged()
            .onEach { enabled ->
                if (enabled) {
                    QuickCaptureNotifier.show(this@RememberApp)
                } else {
                    QuickCaptureNotifier.hide(this@RememberApp)
                }
            }.launchIn(applicationScope)
    }

    private fun ensureReminderChannel() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Drop pre-versioned channels. Channel settings are immutable after first
        // registration, so the only way to fix a low-importance "reminder_high" left
        // over from an older build is to delete and re-register under a new id.
        ReminderScheduler.LEGACY_CHANNEL_IDS.forEach(notificationManager::deleteNotificationChannel)

        val lowChannel =
            NotificationChannel(
                ReminderScheduler.CHANNEL_ID_LOW,
                getString(R.string.notification_channel_reminders_low),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.notification_channel_reminders_low_desc) }
        notificationManager.createNotificationChannel(lowChannel)

        val defaultChannel =
            NotificationChannel(
                ReminderScheduler.CHANNEL_ID_DEFAULT,
                getString(R.string.notification_channel_reminders_normal),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = getString(R.string.notification_channel_reminders_normal_desc) }
        notificationManager.createNotificationChannel(defaultChannel)

        // For Android to flip on the per-channel "Pop on screen" (heads-up) toggle
        // automatically, the channel must be IMPORTANCE_HIGH AND have a non-null
        // sound. A silent or sound-less HIGH channel does not heads-up. The audio
        // attributes use USAGE_NOTIFICATION_RINGTONE so the system treats this as a
        // user-attention sound that bypasses normal media-volume ducking rules.
        notificationManager.createNotificationChannel(
            alertingReminderChannel(
                id = ReminderScheduler.CHANNEL_ID_HIGH,
                name = getString(R.string.notification_channel_reminders_high),
                description = getString(R.string.notification_channel_reminders_high_desc),
                soundType = RingtoneManager.TYPE_NOTIFICATION,
                soundUsage = AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
            ),
        )
        // Critical is configured like High but plays on the alarm stream: its volume is the
        // alarm volume, which silent and vibrate ringer modes do not mute. Critical alerts loop
        // their sound, so the default is the user's alarm tone, which is made to loop; a short
        // notification tone would loop as a run of separate pings. An alarm-stream channel also
        // makes the system sound picker offer alarm tones. The separate channel lets its sound be
        // customised in system settings without changing High.
        notificationManager.createNotificationChannel(
            alertingReminderChannel(
                id = ReminderScheduler.CHANNEL_ID_CRITICAL,
                name = getString(R.string.notification_channel_reminders_critical),
                description = getString(R.string.notification_channel_reminders_critical_desc),
                soundType = RingtoneManager.TYPE_ALARM,
                soundUsage = AudioAttributes.USAGE_ALARM,
            ),
        )

        val summaryChannel =
            NotificationChannel(
                ReminderScheduler.CHANNEL_ID_SUMMARY,
                getString(R.string.notification_channel_reminder_summary),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_reminder_summary_desc)
                setSound(null, null)
                enableVibration(false)
            }
        notificationManager.createNotificationChannel(summaryChannel)
    }

    private fun alertingReminderChannel(
        id: String,
        name: String,
        description: String,
        soundType: Int,
        soundUsage: Int,
    ): NotificationChannel =
        NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            this.description = description
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 200, 250)
            enableLights(true)
            setBypassDnd(true)
            setSound(
                RingtoneManager.getDefaultUri(soundType),
                AudioAttributes
                    .Builder()
                    .setUsage(soundUsage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
}
