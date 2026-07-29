package dev.bikram.remember.ui.settings

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dev.bikram.remember.R
import dev.bikram.remember.update.RememberUpdateInfo
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

internal const val REMEMBER_UPDATE_APK_CACHE_NAME = "remember_update.apk"
private const val MAX_UPDATE_APK_DISPLAY_NAME_LENGTH = 120
private const val INVALID_UPDATE_APK_FILENAME_CHARACTERS = "<>:\"/\\|?*"
private const val APK_EXTENSION = ".apk"

internal fun sanitizeUpdateApkDisplayName(
    displayName: String,
    fallbackName: String,
): String {
    val cleanedName =
        buildString(displayName.length) {
            displayName.forEach { character ->
                if (character.isISOControl() || character in INVALID_UPDATE_APK_FILENAME_CHARACTERS) {
                    append('_')
                } else {
                    append(character)
                }
            }
        }.trim(' ', '.')
    val nameWithoutExtension =
        if (cleanedName.endsWith(".apk", ignoreCase = true)) {
            cleanedName.dropLast(4)
        } else {
            cleanedName
        }
    val boundedName =
        nameWithoutExtension
            .trim(' ', '.')
            .take(MAX_UPDATE_APK_DISPLAY_NAME_LENGTH - APK_EXTENSION.length)
            .trimEnd(' ', '.')
    return if (boundedName.isBlank()) fallbackName else boundedName + APK_EXTENSION
}

internal suspend fun downloadUpdateApk(
    context: Context,
    updateInfo: RememberUpdateInfo,
    onProgress: suspend (Float) -> Unit,
): File {
    val connection = URL(updateInfo.downloadUrl).openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = true
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000
    return try {
        connection.connect()
        if (connection.responseCode !in 200..299) {
            error("Download returned HTTP ${connection.responseCode}")
        }
        val contentLength = connection.contentLength
        val updateFile = File(context.cacheDir, REMEMBER_UPDATE_APK_CACHE_NAME)
        connection.inputStream.use { inputStream ->
            updateFile.outputStream().use { outputStream ->
                val buffer = ByteArray(8192)
                var totalBytesRead = 0L
                var bytesRead: Int
                if (contentLength > 0) {
                    while (inputStream.read(buffer).also { readCount -> bytesRead = readCount } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        val percent = (100f * totalBytesRead / contentLength).coerceIn(0f, 100f)
                        onProgress(percent)
                    }
                } else {
                    onProgress(-2f)
                    while (inputStream.read(buffer).also { readCount -> bytesRead = readCount } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }
        }
        updateFile
    } finally {
        connection.disconnect()
    }
}

internal fun copyUpdateApkToMediaStoreDownloads(
    context: Context,
    cacheApkFile: File,
    displayName: String,
): Result<Unit> =
    runCatching {
        val safeName = sanitizeUpdateApkDisplayName(displayName, REMEMBER_UPDATE_APK_CACHE_NAME)
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val itemUri =
            resolver.insert(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values,
            ) ?: error("MediaStore insert returned null")
        try {
            resolver.openOutputStream(itemUri, "w")?.use { output ->
                FileInputStream(cacheApkFile).use { input ->
                    input.copyTo(output)
                }
            } ?: error("openOutputStream returned null")
            val publish =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
            resolver.update(itemUri, publish, null, null)
        } catch (throwable: Throwable) {
            runCatching { resolver.delete(itemUri, null, null) }
            throw throwable
        }
    }

internal fun exportFolderDisplayLabel(
    context: Context,
    uriString: String,
    internalStorageFallback: String,
): String {
    if (uriString.isBlank()) return ""
    val uri = uriString.toUri()
    if (!DocumentsContract.isTreeUri(uri)) {
        providerDisplayName(context, uri.authority)?.let { return it }
    }
    val providerName =
        providerDisplayName(context, uri.authority)
            ?.takeUnless { displayName -> displayName == uri.authority }
    val relativeTreePath =
        runCatching {
            val treeId = DocumentsContract.getTreeDocumentId(uri)
            val decodedTreeId = Uri.decode(treeId)
            decodedTreeId.substringAfter(':', decodedTreeId)
        }.getOrNull()
    val documentName = DocumentFile.fromTreeUri(context, uri)?.name
    val folderLabel =
        if (providerName != null) {
            documentName?.takeIf { it.isNotBlank() }
                ?: relativeTreePath?.takeIf { it.isNotBlank() }
        } else {
            relativeTreePath?.takeIf { it.isNotBlank() }
                ?: documentName?.takeIf { it.isNotBlank() }
        } ?: internalStorageFallback
    return if (providerName != null && !folderLabel.equals(providerName, ignoreCase = true)) {
        context.getString(R.string.cloud_provider_folder_path, providerName, folderLabel)
    } else {
        folderLabel
    }
}

private fun providerDisplayName(
    context: Context,
    authority: String?,
): String? {
    val providerAuthority = authority?.takeIf { it.isNotBlank() } ?: return null
    val normalizedAuthority = providerAuthority.lowercase()
    return when {
        normalizedAuthority.contains("google.android.apps.docs") ->
            context.getString(R.string.cloud_provider_google_drive)
        normalizedAuthority.contains("skydrive") || normalizedAuthority.contains("onedrive") ->
            context.getString(R.string.cloud_provider_onedrive)
        normalizedAuthority.contains("dropbox") ->
            context.getString(R.string.cloud_provider_dropbox)
        normalizedAuthority.contains("box.android") ->
            context.getString(R.string.cloud_provider_box)
        else ->
            providerAuthority
    }
}
