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

class GoogleTasksImportPrefs(
    private val context: Context,
) {
    private object Keys {
        val LAST_ACCOUNT_EMAIL = stringPreferencesKey("last_account_email")
        val IMPORTED_MAP_JSON = stringPreferencesKey("imported_map_json")
        val IMPORTED_MAPS_BY_SOURCE_JSON = stringPreferencesKey("imported_maps_by_source_json")
    }

    private val json: Json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ImportedMap(
        val map: Map<String, Long>,
    )

    @Serializable
    private data class ImportedMapsBySource(
        val maps: Map<String, Map<String, Long>>,
    )

    val lastAccountEmail: Flow<String?> =
        context.googleTasksImportDataStore.data.map { prefs ->
            prefs[Keys.LAST_ACCOUNT_EMAIL]?.takeIf { it.isNotBlank() }
        }

    suspend fun setLastAccountEmail(email: String?) {
        context.googleTasksImportDataStore.edit { prefs ->
            if (email.isNullOrBlank()) {
                prefs.remove(Keys.LAST_ACCOUNT_EMAIL)
            } else {
                prefs[Keys.LAST_ACCOUNT_EMAIL] = email
            }
        }
    }

    /** Returns the full map of googleTaskId -> rememberNoteId. Empty when nothing is imported. */
    suspend fun importedMap(sourceKey: String): Map<String, Long> {
        val mapsBySource = importedMapsBySource()
        if (sourceKey in mapsBySource) {
            return mapsBySource[sourceKey].orEmpty()
        }
        val raw =
            context.googleTasksImportDataStore.data
                .first()[Keys.IMPORTED_MAP_JSON]
                .orEmpty()
        if (raw.isBlank()) return emptyMap()
        return runCatching { json.decodeFromString(ImportedMap.serializer(), raw).map }
            .getOrDefault(emptyMap())
    }

    /**
     * Replace the entries for [pairs] in the persisted map. Existing entries for other Google
     * task ids are preserved.
     */
    suspend fun recordImported(
        sourceKey: String,
        pairs: Map<String, Long>,
    ) {
        if (pairs.isEmpty()) return
        context.googleTasksImportDataStore.edit { prefs ->
            val existingMaps = decodeMapsBySource(prefs[Keys.IMPORTED_MAPS_BY_SOURCE_JSON].orEmpty())
            val existing = existingMaps[sourceKey].orEmpty()
            val merged = existing.toMutableMap().apply { putAll(pairs) }
            val nextMaps = existingMaps.toMutableMap().apply { put(sourceKey, merged) }
            prefs[Keys.IMPORTED_MAPS_BY_SOURCE_JSON] =
                json.encodeToString(
                    ImportedMapsBySource.serializer(),
                    ImportedMapsBySource(nextMaps),
                )
            prefs.remove(Keys.IMPORTED_MAP_JSON)
        }
    }

    /** Drop entries whose values are not in [stillExistingNoteIds]. Used as a best-effort sweep. */
    suspend fun pruneMissing(
        sourceKey: String,
        stillExistingNoteIds: Set<Long>,
    ) {
        val current = importedMap(sourceKey)
        if (current.isEmpty()) return
        val pruned = current.filterValues { it in stillExistingNoteIds }
        if (pruned.size == current.size) return
        context.googleTasksImportDataStore.edit { prefs ->
            val existingMaps = decodeMapsBySource(prefs[Keys.IMPORTED_MAPS_BY_SOURCE_JSON].orEmpty())
            val nextMaps = existingMaps.toMutableMap().apply { put(sourceKey, pruned) }
            prefs[Keys.IMPORTED_MAPS_BY_SOURCE_JSON] =
                json.encodeToString(
                    ImportedMapsBySource.serializer(),
                    ImportedMapsBySource(nextMaps),
                )
            prefs.remove(Keys.IMPORTED_MAP_JSON)
        }
    }

    /** Forget the visible source only. Used when user disconnects or clears a Takeout import. */
    suspend fun resetSource(sourceKey: String?) {
        context.googleTasksImportDataStore.edit { prefs ->
            prefs.remove(Keys.LAST_ACCOUNT_EMAIL)
            prefs.remove(Keys.IMPORTED_MAP_JSON)
            val key = sourceKey?.takeIf { it.isNotBlank() } ?: return@edit
            val existingMaps = decodeMapsBySource(prefs[Keys.IMPORTED_MAPS_BY_SOURCE_JSON].orEmpty())
            if (key in existingMaps) {
                val nextMaps = existingMaps.toMutableMap().apply { remove(key) }
                prefs[Keys.IMPORTED_MAPS_BY_SOURCE_JSON] =
                    json.encodeToString(
                        ImportedMapsBySource.serializer(),
                        ImportedMapsBySource(nextMaps),
                    )
            }
        }
    }

    private suspend fun importedMapsBySource(): Map<String, Map<String, Long>> =
        decodeMapsBySource(
            context.googleTasksImportDataStore.data
                .first()[Keys.IMPORTED_MAPS_BY_SOURCE_JSON]
                .orEmpty(),
        )

    private fun decodeMapsBySource(raw: String): Map<String, Map<String, Long>> =
        if (raw.isBlank()) {
            emptyMap()
        } else {
            runCatching { json.decodeFromString(ImportedMapsBySource.serializer(), raw).maps }
                .getOrDefault(emptyMap())
        }
}
