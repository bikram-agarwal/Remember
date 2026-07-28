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
import kotlinx.coroutines.flow.drop
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
        scheduleWidgetRefreshes()
        observeQuickCapturePref()
        observeReminderPrefs()
        observeReminderSummarySource()
        backupExportCoordinator.start()
        applicationScope.launch {
            RememberBackupWork.updateSchedule(this@RememberApp, backupPrefs.snapshot())
            noteRepository.refreshReminderSummaryNotification()
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
        if (!UpdateCheckWorkScheduler.supportsSilentChecks()) return
        val updateInfo =
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                rememberUpdateChecker.checkGithubReleaseForUpdate(
                    repositoryName = BuildConfig.GITHUB_REPO,
                    currentVersionName = BuildConfig.VERSION_NAME,
                )
            } ?: return
        rememberUpdateState.showUpdate(updateInfo)
        updateAvailableNotifier.notifyIfNewUpdateAvailable(updateInfo, prefs)
    }

    private fun observeReminderPrefs() {
        reminderPrefs.state
            .distinctUntilChanged()
            .onEach {
                noteRepository.refreshActiveReminderNotifications()
                noteRepository.refreshReminderSummaryNotification()
            }.launchIn(applicationScope)
    }

    private fun observeReminderSummarySource() {
        noteRepository
            .observeActive()
            .drop(1)
            .onEach {
                noteRepository.refreshReminderSummaryNotification()
            }.launchIn(applicationScope)
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

    private fun scheduleWidgetRefreshes() {
        noteRepository
            .observeActive()
            .drop(1) // skip initial emission
            .onEach {
                notesWidgetUpdater.refreshAll()
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
        val highChannel =
            NotificationChannel(
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
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
        notificationManager.createNotificationChannel(highChannel)

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
}
