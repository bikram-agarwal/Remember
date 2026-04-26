package dev.bikram.remember.googletasks

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Persistence for the Google Tasks import flow:
 *  - Last picked account email so the UI can offer a one-tap re-auth on subsequent visits.
 *  - googleTaskId -> Remember note id map, so re-running an import does not duplicate an
 *    already-imported task. The user can still choose to overwrite from the import sheet.
 *
 * The map is intentionally a small JSON blob in DataStore rather than a Room table; the data
 * is never queried in bulk and never participates in joins. We trim entries when the
 * referenced Remember note has been deleted (best-effort sweep at next import time).
 */
private val Context.googleTasksImportDataStore by preferencesDataStore(name = "google_tasks_import_prefs")

class GoogleTasksImportPrefs(private val context: Context) {

    private object Keys {
        val LAST_ACCOUNT_EMAIL = stringPreferencesKey("last_account_email")
        val IMPORTED_MAP_JSON = stringPreferencesKey("imported_map_json")
    }

    private val json: Json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ImportedMap(val map: Map<String, Long>)

    val lastAccountEmail: Flow<String?> = context.googleTasksImportDataStore.data.map { prefs ->
        prefs[Keys.LAST_ACCOUNT_EMAIL]?.takeIf { it.isNotBlank() }
    }

    suspend fun setLastAccountEmail(email: String?) {
        context.googleTasksImportDataStore.edit { prefs ->
            if (email.isNullOrBlank()) prefs.remove(Keys.LAST_ACCOUNT_EMAIL)
            else prefs[Keys.LAST_ACCOUNT_EMAIL] = email
        }
    }

    /** Returns the full map of googleTaskId -> rememberNoteId. Empty when nothing is imported. */
    suspend fun importedMap(): Map<String, Long> {
        val raw = context.googleTasksImportDataStore.data.first()[Keys.IMPORTED_MAP_JSON].orEmpty()
        if (raw.isBlank()) return emptyMap()
        return runCatching { json.decodeFromString(ImportedMap.serializer(), raw).map }
            .getOrDefault(emptyMap())
    }

    /**
     * Replace the entries for [pairs] in the persisted map. Existing entries for other Google
     * task ids are preserved.
     */
    suspend fun recordImported(pairs: Map<String, Long>) {
        if (pairs.isEmpty()) return
        context.googleTasksImportDataStore.edit { prefs ->
            val current = prefs[Keys.IMPORTED_MAP_JSON].orEmpty()
            val existing = if (current.isBlank()) emptyMap() else {
                runCatching { json.decodeFromString(ImportedMap.serializer(), current).map }
                    .getOrDefault(emptyMap())
            }
            val merged = existing.toMutableMap().apply { putAll(pairs) }
            prefs[Keys.IMPORTED_MAP_JSON] = json.encodeToString(
                ImportedMap.serializer(),
                ImportedMap(merged),
            )
        }
    }

    /** Drop entries whose values are not in [stillExistingNoteIds]. Used as a best-effort sweep. */
    suspend fun pruneMissing(stillExistingNoteIds: Set<Long>) {
        val current = importedMap()
        if (current.isEmpty()) return
        val pruned = current.filterValues { it in stillExistingNoteIds }
        if (pruned.size == current.size) return
        context.googleTasksImportDataStore.edit { prefs ->
            prefs[Keys.IMPORTED_MAP_JSON] = json.encodeToString(
                ImportedMap.serializer(),
                ImportedMap(pruned),
            )
        }
    }

    /** Forget the picked account and clear the imported-id map. Used on user "switch account". */
    suspend fun reset() {
        context.googleTasksImportDataStore.edit { prefs ->
            prefs.remove(Keys.LAST_ACCOUNT_EMAIL)
            prefs.remove(Keys.IMPORTED_MAP_JSON)
        }
    }
}
