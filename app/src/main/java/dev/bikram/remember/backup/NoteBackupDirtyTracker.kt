package dev.bikram.remember.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.noteBackupStateStore by preferencesDataStore(name = "note_backup_state")

/**
 * Tracks whether note data has changed since the last successful backup into the user-chosen
 * export tree folder (auto-export on leaving the app, or scheduled export).
 */
class NoteBackupDirtyTracker(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.noteBackupStateStore)

    private val exportMutex = Mutex()

    suspend fun markNotesChangedSinceLastTreeExport() {
        dataStore.edit { state ->
            state[CHANGE_VERSION] = (state[CHANGE_VERSION] ?: 0L) + 1L
        }
    }

    /**
     * A failed or cancelled export leaves its version pending across process restarts.
     * Edits arriving during an export receive a newer version and need another backup.
     * The mutex coalesces concurrent scheduled and app-exit exports.
     */
    suspend fun <Value> exportPendingChanges(export: suspend () -> Result<Value>): Result<Value>? =
        exportMutex.withLock {
            val state = dataStore.data.first()
            val version = state[CHANGE_VERSION] ?: 0L
            if (state[EXPORTED_VERSION] == version) return@withLock null
            val result = export()
            if (result.isSuccess) {
                dataStore.edit { current -> current[EXPORTED_VERSION] = version }
            }
            result
        }

    private companion object {
        val CHANGE_VERSION = longPreferencesKey("change_version")
        val EXPORTED_VERSION = longPreferencesKey("exported_version")
    }
}
