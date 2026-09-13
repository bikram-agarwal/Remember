package dev.bikram.remember.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteBackupDirtyTrackerTest {
    @Test
    fun pending_changes_and_successful_exports_survive_tracker_recreation() =
        runTest {
            val store = MemoryBackupStateStore()
            val firstProcess = NoteBackupDirtyTracker(store)
            firstProcess.markNotesChangedSinceLastTreeExport()

            val restartedProcess = NoteBackupDirtyTracker(store)
            assertNotNull(restartedProcess.exportPendingChanges { Result.success(Unit) })
            assertNull(NoteBackupDirtyTracker(store).exportPendingChanges<Unit> { error("Already backed up") })
        }

    @Test
    fun first_scheduled_run_backs_up_existing_notes_without_an_in_memory_change_event() =
        runTest {
            assertNotNull(NoteBackupDirtyTracker(MemoryBackupStateStore()).exportPendingChanges { Result.success(Unit) })
        }

    @Test
    fun failed_exports_remain_pending() =
        runTest {
            val store = MemoryBackupStateStore()
            val tracker = NoteBackupDirtyTracker(store)
            val failed = tracker.exportPendingChanges { Result.failure<Unit>(IllegalStateException("Folder unavailable")) }
            assertTrue(failed?.isFailure == true)

            assertNotNull(NoteBackupDirtyTracker(store).exportPendingChanges { Result.success(Unit) })
        }

    @Test
    fun cancelled_exports_remain_pending() =
        runTest {
            val store = MemoryBackupStateStore()
            val tracker = NoteBackupDirtyTracker(store)
            try {
                tracker.exportPendingChanges<Unit> { throw CancellationException("App foregrounded") }
            } catch (_: CancellationException) {
                // The next process must still see the unfinished export.
            }

            assertNotNull(NoteBackupDirtyTracker(store).exportPendingChanges { Result.success(Unit) })
        }

    @Test
    fun edits_during_an_export_require_another_backup() =
        runTest {
            val tracker = NoteBackupDirtyTracker(MemoryBackupStateStore())
            tracker.exportPendingChanges {
                tracker.markNotesChangedSinceLastTreeExport()
                Result.success(Unit)
            }

            assertNotNull(tracker.exportPendingChanges { Result.success(Unit) })
            assertNull(tracker.exportPendingChanges<Unit> { error("Already backed up") })
        }

    @Test
    fun overlapping_exit_and_scheduled_exports_write_only_one_backup() =
        runTest {
            val tracker = NoteBackupDirtyTracker(MemoryBackupStateStore())
            var exportCount = 0
            val exitExport =
                async {
                    tracker.exportPendingChanges {
                        exportCount++
                        yield()
                        Result.success(Unit)
                    }
                }
            val scheduledExport =
                async {
                    tracker.exportPendingChanges {
                        exportCount++
                        Result.success(Unit)
                    }
                }

            exitExport.await()
            scheduledExport.await()
            assertEquals(1, exportCount)
        }
}

private class MemoryBackupStateStore : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(data.value)
        data.value = updated
        return updated
    }
}
