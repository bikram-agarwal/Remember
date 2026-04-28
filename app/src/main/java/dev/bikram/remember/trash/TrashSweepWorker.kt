package dev.bikram.remember.trash

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.bikram.remember.data.NoteRepository

/**
 * Periodically deletes notes that have been in the trash for longer than
 * [NoteRepository.TRASH_RETENTION_MILLIS]. Scheduled by [RememberTrashSweepWork]
 * from app startup and runs no more than once per day.
 */
@HiltWorker
class TrashSweepWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val noteRepository: NoteRepository,
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            val cutoff = System.currentTimeMillis() - NoteRepository.TRASH_RETENTION_MILLIS
            return runCatching {
                noteRepository.autoEmptyTrashOlderThan(cutoff)
            }.fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() },
            )
        }
    }
