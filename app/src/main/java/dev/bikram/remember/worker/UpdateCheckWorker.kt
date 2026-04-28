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
        private val rememberUpdateState: RememberUpdateState,
        private val updateAvailableNotifier: UpdateAvailableNotifier,
        private val updateCheckWorkScheduler: UpdateCheckWorkScheduler,
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            val prefs = updatePrefs.snapshot()
            if (prefs.updateCheckSchedule != UpdateCheckSchedule.DAILY_AT_21 &&
                prefs.updateCheckSchedule != UpdateCheckSchedule.WEEKLY_MONDAY_AT_21
            ) {
                updateCheckWorkScheduler.syncFromPreferences()
                return Result.success()
            }
            if (UpdateCheckWorkScheduler.supportsSilentChecks()) {
                val updateInfo =
                    rememberUpdateChecker.checkGithubReleaseForUpdate(
                        repositoryName = BuildConfig.GITHUB_REPO,
                        currentVersionName = BuildConfig.VERSION_NAME,
                    )
                if (updateInfo != null) {
                    rememberUpdateState.showUpdate(updateInfo)
                    updateAvailableNotifier.notifyIfNewUpdateAvailable(updateInfo, prefs)
                }
            }
            updateCheckWorkScheduler.syncFromPreferences()
            return Result.success()
        }

        companion object {
            const val UNIQUE_WORK_NAME = "remember_update_check_work"
        }
    }
