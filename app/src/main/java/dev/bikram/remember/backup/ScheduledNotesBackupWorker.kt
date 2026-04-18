package dev.bikram.remember.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.bikram.remember.RememberApp

class ScheduledNotesBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as RememberApp
        val prefs = app.container.backupPrefs.snapshot()
        if (!prefs.scheduledExportEnabled || prefs.exportFolderUri.isBlank()) {
            return Result.success()
        }
        val exportOutcome = app.container.backupIo.exportToTreeFolder(prefs.exportFolderUri)
        if (exportOutcome.isSuccess) {
            app.container.noteBackupDirtyTracker.clearAfterSuccessfulTreeExport()
        }
        return exportOutcome.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }
}
