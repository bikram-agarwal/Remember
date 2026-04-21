package dev.bikram.remember.trash

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.bikram.remember.RememberApp
import dev.bikram.remember.data.NoteRepository

/**
 * Periodically deletes notes that have been in the trash for longer than
 * [NoteRepository.TRASH_RETENTION_MILLIS]. Scheduled by [RememberTrashSweepWork]
 * from [RememberApp.onCreate] and runs no more than once per day.
 */
class TrashSweepWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as RememberApp
        val cutoff = System.currentTimeMillis() - NoteRepository.TRASH_RETENTION_MILLIS
        return runCatching {
            app.container.noteRepository.autoEmptyTrashOlderThan(cutoff)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }
}
