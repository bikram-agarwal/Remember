package dev.bikram.remember.trash

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules the periodic [TrashSweepWorker] so notes that have lived in the trash for longer
 * than the 30-day retention window are auto-deleted even if the app is never opened.
 *
 * The schedule is idempotent -- calling [ensureScheduled] repeatedly (e.g. on every app start)
 * is safe and will not duplicate work.
 */
object RememberTrashSweepWork {
    private const val UNIQUE_NAME = "remember_trash_sweep"

    fun ensureScheduled(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val request =
            PeriodicWorkRequestBuilder<TrashSweepWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiresBatteryNotLow(true)
                        .build(),
                ).build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            // UPDATE (matching the scheduled-backup worker) preserves the existing daily schedule
            // while still applying any future change to the period/constraints; KEEP would ignore
            // such changes. Both policies remain idempotent, so the doc note above still holds.
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
