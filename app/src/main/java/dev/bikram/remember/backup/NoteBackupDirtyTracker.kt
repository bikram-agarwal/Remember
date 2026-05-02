package dev.bikram.remember.backup

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tracks whether note data has changed since the last successful backup into the user-chosen
 * export tree folder (auto-export on leaving the app, or scheduled export).
 */
class NoteBackupDirtyTracker {
    private val hasPendingChanges = AtomicBoolean(false)

    fun markNotesChangedSinceLastTreeExport() {
        hasPendingChanges.set(true)
    }

    fun consumePendingChangeSinceLastTreeExport(): Boolean = hasPendingChanges.getAndSet(false)
}
