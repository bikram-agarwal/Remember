package dev.bikram.remember.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.bikram.remember.data.BackupPreferencesState
import java.util.concurrent.TimeUnit

object RememberBackupWork {
    private const val UNIQUE_NAME = "remember_scheduled_notes_backup"

    fun updateSchedule(context: Context, prefs: BackupPreferencesState) {
        val workManager = WorkManager.getInstance(context)
        if (prefs.scheduledExportEnabled && prefs.exportFolderUri.isNotBlank()) {
            val request = PeriodicWorkRequestBuilder<ScheduledNotesBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        } else {
            workManager.cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
