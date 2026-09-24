package dev.bikram.remember.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.bikram.remember.BuildConfig
import dev.bikram.remember.data.UpdateCheckSchedule
import dev.bikram.remember.data.UpdatePrefs
import dev.bikram.remember.diagnostics.DiagnosticLog
import dev.bikram.remember.update.PlayStoreUpdateChecker
import dev.bikram.remember.update.RememberUpdateChecker
import dev.bikram.remember.update.RememberUpdateState
import dev.bikram.remember.update.UpdateAvailableNotifier
import dev.bikram.remember.update.UpdateCheckWorkScheduler

@HiltWorker
class UpdateCheckWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val updatePrefs: UpdatePrefs,
        private val rememberUpdateChecker: RememberUpdateChecker,
        private val playStoreUpdateChecker: PlayStoreUpdateChecker,
        private val rememberUpdateState: RememberUpdateState,
        private val updateAvailableNotifier: UpdateAvailableNotifier,
        private val updateCheckWorkScheduler: UpdateCheckWorkScheduler,
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            if (!BuildConfig.SHOW_UPDATES) {
                updateCheckWorkScheduler.syncFromPreferences()
                return Result.success()
            }
            val prefs = updatePrefs.snapshot()
            if (prefs.updateCheckSchedule != UpdateCheckSchedule.DAILY_AT_21 &&
                prefs.updateCheckSchedule != UpdateCheckSchedule.WEEKLY_MONDAY_AT_21
            ) {
                updateCheckWorkScheduler.syncFromPreferences()
                return Result.success()
            }
            runCatching {
                val updateInfo =
                    if (BuildConfig.USE_PLAY_IN_APP_UPDATES) {
                        playStoreUpdateChecker.checkForUpdate()
                    } else {
                        rememberUpdateChecker.checkForUpdate()
                    }
                if (updateInfo != null) {
                    rememberUpdateState.showUpdate(updateInfo)
                    updateAvailableNotifier.notifyIfNewUpdateAvailable(updateInfo, prefs)
                }
            }.onFailure { error ->
                DiagnosticLog.record(applicationContext, "Scheduled update check failed: attempt=$runAttemptCount", error)
                if (runAttemptCount < MAX_IMMEDIATE_RETRIES) {
                    return Result.retry()
                }
                updateCheckWorkScheduler.syncFromPreferences()
                return Result.success()
            }
            updateCheckWorkScheduler.syncFromPreferences()
            return Result.success()
        }

        companion object {
            const val UNIQUE_WORK_NAME = "remember_update_check_work"
            private const val MAX_IMMEDIATE_RETRIES = 3
        }
    }
