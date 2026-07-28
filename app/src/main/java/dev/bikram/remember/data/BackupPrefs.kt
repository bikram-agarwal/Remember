package dev.bikram.remember.data

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
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

data class BackupSettingsRestoreOutcome(
    val foldersNeedingReselection: Int = 0,
    val automationsDisabled: Boolean = false,
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

    private fun canRetainRestoredFolder(folderUriString: String): Boolean {
        if (folderUriString.isBlank()) return true
        // Unlike FilePipe, Remember has no All Files Access mode, so every usable backup destination is a SAF URI.
        if (!folderUriString.startsWith("content://")) return false
        val folderUri = folderUriString.toUri()
        val resolver = context.contentResolver
        val alreadyPersisted =
            resolver.persistedUriPermissions.any { permission ->
                permission.uri == folderUri && permission.isReadPermission && permission.isWritePermission
            }
        if (alreadyPersisted) return true
        val permissionFlags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (runCatching { resolver.takePersistableUriPermission(folderUri, permissionFlags) }.isFailure) {
            return false
        }
        return resolver.persistedUriPermissions.any { permission ->
            permission.uri == folderUri && permission.isReadPermission && permission.isWritePermission
        }
    }

    suspend fun importFromBackup(json: JSONObject?): BackupSettingsRestoreOutcome {
        if (json == null || json.length() == 0) return BackupSettingsRestoreOutcome()
        val existingPreferences = snapshot()
        val requestedExportFolder =
            if (json.has(Keys.EXPORT_FOLDER_URI.name) && !json.isNull(Keys.EXPORT_FOLDER_URI.name)) {
                json.getString(Keys.EXPORT_FOLDER_URI.name)
            } else {
                existingPreferences.exportFolderUri
            }
        val requestedCloudExportFolder =
            if (json.has(Keys.CLOUD_EXPORT_FOLDER_URI.name) && !json.isNull(Keys.CLOUD_EXPORT_FOLDER_URI.name)) {
                json.getString(Keys.CLOUD_EXPORT_FOLDER_URI.name)
            } else {
                existingPreferences.cloudExportFolderUri
            }
        var foldersNeedingReselection = 0
        val restoredExportFolder =
            requestedExportFolder.takeIf(::canRetainRestoredFolder).orEmpty().also {
                if (requestedExportFolder.isNotBlank() && it.isBlank()) foldersNeedingReselection += 1
            }
        val restoredCloudExportFolder =
            requestedCloudExportFolder.takeIf(::canRetainRestoredFolder).orEmpty().also {
                if (requestedCloudExportFolder.isNotBlank() && it.isBlank()) foldersNeedingReselection += 1
            }
        val hasBackupDestination = restoredExportFolder.isNotBlank() || restoredCloudExportFolder.isNotBlank()
        var automationsDisabled = false
        context.backupDataStore.edit { mutable ->
            mutable[Keys.EXPORT_FOLDER_URI] = restoredExportFolder
            mutable[Keys.CLOUD_EXPORT_FOLDER_URI] = restoredCloudExportFolder
            if (json.has(Keys.AUTO_EXPORT_ON_CHANGE.name) && !json.isNull(Keys.AUTO_EXPORT_ON_CHANGE.name)) {
                val requested = json.getBoolean(Keys.AUTO_EXPORT_ON_CHANGE.name)
                mutable[Keys.AUTO_EXPORT_ON_CHANGE] = requested && hasBackupDestination
                automationsDisabled = automationsDisabled || (requested && !hasBackupDestination)
            }
            if (json.has(Keys.SCHEDULED_EXPORT.name) && !json.isNull(Keys.SCHEDULED_EXPORT.name)) {
                val requested = json.getBoolean(Keys.SCHEDULED_EXPORT.name)
                mutable[Keys.SCHEDULED_EXPORT] = requested && hasBackupDestination
                automationsDisabled = automationsDisabled || (requested && !hasBackupDestination)
            }
            if (json.has(Keys.INCLUDE_MEDIA.name) && !json.isNull(Keys.INCLUDE_MEDIA.name)) {
                mutable[Keys.INCLUDE_MEDIA] = json.getBoolean(Keys.INCLUDE_MEDIA.name)
            }
            if (json.has(Keys.COMPRESS_IMAGES.name) && !json.isNull(Keys.COMPRESS_IMAGES.name)) {
                mutable[Keys.COMPRESS_IMAGES] = json.getBoolean(Keys.COMPRESS_IMAGES.name)
            }
        }
        return BackupSettingsRestoreOutcome(
            foldersNeedingReselection = foldersNeedingReselection,
            automationsDisabled = automationsDisabled,
        )
    }
}
