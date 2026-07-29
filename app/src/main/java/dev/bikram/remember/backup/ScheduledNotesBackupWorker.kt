package dev.bikram.remember.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.bikram.remember.data.BackupIo
import dev.bikram.remember.data.BackupPrefs
import dev.bikram.remember.diagnostics.DiagnosticLog

@HiltWorker
class ScheduledNotesBackupWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val backupPrefs: BackupPrefs,
        private val backupIo: BackupIo,
        private val noteBackupDirtyTracker: NoteBackupDirtyTracker,
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            val prefs = backupPrefs.snapshot()
            val backupDestinations =
                listOf(
                    prefs.exportFolderUri,
                    prefs.cloudExportFolderUri,
                ).filter { it.isNotBlank() }
            if (!prefs.scheduledExportEnabled || backupDestinations.isEmpty()) {
                return Result.success()
            }
            if (!noteBackupDirtyTracker.consumePendingChangeSinceLastTreeExport()) {
                return Result.success()
            }
            val exportOutcome = backupIo.exportToTreeFolders(backupDestinations)
            if (exportOutcome.isFailure) {
                noteBackupDirtyTracker.markNotesChangedSinceLastTreeExport()
            }
            return exportOutcome.fold(
                onSuccess = { Result.success() },
                onFailure = { error ->
                    val attemptNumber = runAttemptCount + 1
                    val willRetry = attemptNumber < MAX_RUN_ATTEMPTS_PER_PERIOD
                    DiagnosticLog.record(
                        applicationContext,
                        "Scheduled backup export failed: destinations=${backupDestinations.size}, " +
                            "attempt=$attemptNumber, willRetry=$willRetry",
                        error,
                    )
                    if (willRetry) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                },
            )
        }

        companion object {
            private const val MAX_RUN_ATTEMPTS_PER_PERIOD = 3
        }
    }
