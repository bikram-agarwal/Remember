package dev.bikram.remember.backup

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.bikram.remember.data.BackupIo
import dev.bikram.remember.data.BackupPrefs
import dev.bikram.remember.data.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * When "auto-export on app exit" is enabled, writes at most one ZIP per time the app goes to
 * the background, and only if notes or lists changed since the last successful export to the
 * export folder (including a successful scheduled backup).
 */
class BackupExportCoordinator(
    private val backupPrefs: BackupPrefs,
    private val backupIo: BackupIo,
    private val noteRepository: NoteRepository,
    private val applicationScope: CoroutineScope,
    private val noteBackupDirtyTracker: NoteBackupDirtyTracker,
) {
    private var exitExportJob: Job? = null

    fun start() {
        applicationScope.launch {
            combine(
                noteRepository.observeActive(),
                noteRepository.observeTrashed(),
            ) { activeNotes, trashedNotes ->
                activeNotes to trashedNotes
            }.distinctUntilChanged()
                .drop(1)
                .collect {
                    noteBackupDirtyTracker.markNotesChangedSinceLastTreeExport()
                }
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    exitExportJob?.cancel()
                    exitExportJob = null
                }

                override fun onStop(owner: LifecycleOwner) {
                    exitExportJob?.cancel()
                    exitExportJob =
                        applicationScope.launch {
                            delay(EXIT_DEBOUNCE_MS)
                            val prefs = backupPrefs.snapshot()
                            val backupDestinations =
                                listOf(
                                    prefs.exportFolderUri,
                                    prefs.cloudExportFolderUri,
                                ).filter { it.isNotBlank() }
                            if (!prefs.autoExportOnChange || backupDestinations.isEmpty()) return@launch
                            if (!noteBackupDirtyTracker.hasPendingChangeSinceLastTreeExport()) return@launch
                            val exportOutcome = backupIo.exportToTreeFolders(backupDestinations)
                            if (exportOutcome.isSuccess) {
                                noteBackupDirtyTracker.clearAfterSuccessfulTreeExport()
                            }
                        }
                }
            },
        )
    }

    private companion object {
        const val EXIT_DEBOUNCE_MS = 600L
    }
}
