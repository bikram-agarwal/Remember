package dev.bikram.remember.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

data class BackupPreferencesState(
    val exportFolderUri: String = "",
    val cloudExportFolderUri: String = "",
    val autoExportOnChange: Boolean = false,
    val scheduledExportEnabled: Boolean = false,
    val includeMediaInBackup: Boolean = false,
    val compressImages: Boolean = true,
)

private val Context.backupDataStore by preferencesDataStore(name = "backup_prefs")

class BackupPrefs(
    private val context: Context,
) {
    private object Keys {
        val EXPORT_FOLDER_URI = stringPreferencesKey("export_folder_uri")
        val CLOUD_EXPORT_FOLDER_URI = stringPreferencesKey("cloud_export_folder_uri")
        val AUTO_EXPORT_ON_CHANGE = booleanPreferencesKey("auto_export_on_change")
        val SCHEDULED_EXPORT = booleanPreferencesKey("scheduled_export_enabled")
        val INCLUDE_MEDIA = booleanPreferencesKey("include_media_in_backup")
        val COMPRESS_IMAGES = booleanPreferencesKey("compress_images")
    }

    val state: Flow<BackupPreferencesState> =
        context.backupDataStore.data.map { prefs ->
            BackupPreferencesState(
                exportFolderUri = prefs[Keys.EXPORT_FOLDER_URI].orEmpty(),
                cloudExportFolderUri = prefs[Keys.CLOUD_EXPORT_FOLDER_URI].orEmpty(),
                autoExportOnChange = prefs[Keys.AUTO_EXPORT_ON_CHANGE] ?: false,
                scheduledExportEnabled = prefs[Keys.SCHEDULED_EXPORT] ?: false,
                includeMediaInBackup = prefs[Keys.INCLUDE_MEDIA] ?: false,
                compressImages = prefs[Keys.COMPRESS_IMAGES] ?: true,
            )
        }

    suspend fun snapshot(): BackupPreferencesState = state.first()

    suspend fun setExportFolderUri(uriString: String) {
        context.backupDataStore.edit { it[Keys.EXPORT_FOLDER_URI] = uriString }
    }

    suspend fun setCloudExportFolderUri(uriString: String) {
        context.backupDataStore.edit { it[Keys.CLOUD_EXPORT_FOLDER_URI] = uriString }
    }

    suspend fun setAutoExportOnChange(enabled: Boolean) {
        context.backupDataStore.edit { it[Keys.AUTO_EXPORT_ON_CHANGE] = enabled }
    }

    suspend fun setScheduledExportEnabled(enabled: Boolean) {
        context.backupDataStore.edit { it[Keys.SCHEDULED_EXPORT] = enabled }
    }

    suspend fun setIncludeMediaInBackup(enabled: Boolean) {
        context.backupDataStore.edit { it[Keys.INCLUDE_MEDIA] = enabled }
    }

    suspend fun setCompressImages(enabled: Boolean) {
        context.backupDataStore.edit { it[Keys.COMPRESS_IMAGES] = enabled }
    }

    suspend fun exportForBackup(): JSONObject {
        val prefs = context.backupDataStore.data.first()
        return JSONObject().apply {
            put(Keys.EXPORT_FOLDER_URI.name, prefs[Keys.EXPORT_FOLDER_URI].orEmpty())
            put(Keys.CLOUD_EXPORT_FOLDER_URI.name, prefs[Keys.CLOUD_EXPORT_FOLDER_URI].orEmpty())
            put(Keys.AUTO_EXPORT_ON_CHANGE.name, prefs[Keys.AUTO_EXPORT_ON_CHANGE] ?: false)
            put(Keys.SCHEDULED_EXPORT.name, prefs[Keys.SCHEDULED_EXPORT] ?: false)
            put(Keys.INCLUDE_MEDIA.name, prefs[Keys.INCLUDE_MEDIA] ?: false)
            put(Keys.COMPRESS_IMAGES.name, prefs[Keys.COMPRESS_IMAGES] ?: true)
        }
    }

    suspend fun reset() {
        context.backupDataStore.edit { it.clear() }
    }

    suspend fun importFromBackup(json: JSONObject?) {
        if (json == null || json.length() == 0) return
        context.backupDataStore.edit { mutable ->
            if (json.has(Keys.EXPORT_FOLDER_URI.name) && !json.isNull(Keys.EXPORT_FOLDER_URI.name)) {
                mutable[Keys.EXPORT_FOLDER_URI] = json.getString(Keys.EXPORT_FOLDER_URI.name)
            }
            if (json.has(Keys.CLOUD_EXPORT_FOLDER_URI.name) && !json.isNull(Keys.CLOUD_EXPORT_FOLDER_URI.name)) {
                mutable[Keys.CLOUD_EXPORT_FOLDER_URI] = json.getString(Keys.CLOUD_EXPORT_FOLDER_URI.name)
            }
            if (json.has(Keys.AUTO_EXPORT_ON_CHANGE.name) && !json.isNull(Keys.AUTO_EXPORT_ON_CHANGE.name)) {
                mutable[Keys.AUTO_EXPORT_ON_CHANGE] = json.getBoolean(Keys.AUTO_EXPORT_ON_CHANGE.name)
            }
            if (json.has(Keys.SCHEDULED_EXPORT.name) && !json.isNull(Keys.SCHEDULED_EXPORT.name)) {
                mutable[Keys.SCHEDULED_EXPORT] = json.getBoolean(Keys.SCHEDULED_EXPORT.name)
            }
            if (json.has(Keys.INCLUDE_MEDIA.name) && !json.isNull(Keys.INCLUDE_MEDIA.name)) {
                mutable[Keys.INCLUDE_MEDIA] = json.getBoolean(Keys.INCLUDE_MEDIA.name)
            }
            if (json.has(Keys.COMPRESS_IMAGES.name) && !json.isNull(Keys.COMPRESS_IMAGES.name)) {
                mutable[Keys.COMPRESS_IMAGES] = json.getBoolean(Keys.COMPRESS_IMAGES.name)
            }
        }
    }
}
