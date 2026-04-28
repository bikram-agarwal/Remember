package dev.bikram.remember.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class MediaCopyResult(
    val uriString: String,
    val copiedToAppStorage: Boolean,
)

@Singleton
class AppMediaStorage
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val fileProviderAuthority: String
            get() = "${context.packageName}.fileprovider"

        fun isAppStoredMediaUri(uriString: String?): Boolean = appStoredFileForUri(context, uriString) != null

        suspend fun copyAttachmentToPrivateStorage(
            noteId: Long,
            sourceUri: Uri,
            displayName: String,
            mimeType: String?,
        ): MediaCopyResult =
            withContext(Dispatchers.IO) {
                val destinationDirectory = File(context.filesDir, "$NOTE_ATTACHMENTS_DIR/$noteId").apply { mkdirs() }
                val destinationFile =
                    File(
                        destinationDirectory,
                        "${System.currentTimeMillis()}_${safeFileName(displayName, mimeType)}",
                    )
                val copyResult =
                    runCatching {
                        context.contentResolver.openInputStream(sourceUri)?.use { input ->
                            destinationFile.outputStream().use { output -> input.copyTo(output) }
                        } ?: throw IOException("openInputStream failed")
                    }
                if (copyResult.isSuccess) {
                    MediaCopyResult(
                        uriString = fileProviderUri(destinationFile),
                        copiedToAppStorage = true,
                    )
                } else {
                    runCatching { destinationFile.delete() }
                    MediaCopyResult(
                        uriString = sourceUri.toString(),
                        copiedToAppStorage = false,
                    )
                }
            }

        suspend fun copyHeroForDuplicate(uriString: String?): String? =
            copyAppStoredMediaToDirectory(
                uriString = uriString,
                destinationDirectory = File(context.filesDir, NOTE_HEROES_DIR),
                fallbackName = "cover.jpg",
            )

        suspend fun copyAttachmentForDuplicate(
            noteId: Long,
            uriString: String,
            displayName: String,
            mimeType: String?,
        ): String =
            copyAppStoredMediaToDirectory(
                uriString = uriString,
                destinationDirectory = File(context.filesDir, "$NOTE_ATTACHMENTS_DIR/$noteId"),
                fallbackName = safeFileName(displayName, mimeType),
            ) ?: uriString

        suspend fun deleteAppStoredMedia(uriString: String) {
            withContext(Dispatchers.IO) {
                val storedFile = appStoredFileForUri(context, uriString) ?: return@withContext
                runCatching { storedFile.delete() }
                runCatching {
                    val parentDirectory = storedFile.parentFile ?: return@runCatching
                    if (parentDirectory.isDirectory && parentDirectory.list().isNullOrEmpty()) {
                        parentDirectory.delete()
                    }
                }
            }
        }

        private suspend fun copyAppStoredMediaToDirectory(
            uriString: String?,
            destinationDirectory: File,
            fallbackName: String,
        ): String? =
            withContext(Dispatchers.IO) {
                if (uriString.isNullOrBlank()) return@withContext uriString
                val sourceFile = appStoredFileForUri(context, uriString) ?: return@withContext uriString
                if (!sourceFile.isFile) return@withContext uriString

                destinationDirectory.mkdirs()
                val destinationFile =
                    File(
                        destinationDirectory,
                        "${System.currentTimeMillis()}_${safeFileName(fallbackName, null)}",
                    )
                runCatching {
                    sourceFile.copyTo(destinationFile, overwrite = true)
                    fileProviderUri(destinationFile)
                }.getOrElse {
                    runCatching { destinationFile.delete() }
                    uriString
                }
            }

        private fun fileProviderUri(file: File): String =
            FileProvider
                .getUriForFile(
                    context,
                    fileProviderAuthority,
                    file,
                ).toString()

        companion object {
            private const val NOTE_HEROES_DIR = "note_heroes"
            private const val NOTE_ATTACHMENTS_DIR = "note_attachments"
            private const val REMEMBER_BACKUP_DIR = "remember_backup"

            fun isAppStoredMediaUri(
                context: Context,
                uriString: String?,
            ): Boolean = appStoredFileForUri(context, uriString) != null

            fun appStoredFileForUri(
                context: Context,
                uriString: String?,
            ): File? {
                if (uriString.isNullOrBlank()) return null
                val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
                if (uri.authority != "${context.packageName}.fileprovider") return null
                val pathSegments = uri.pathSegments
                if (pathSegments.isEmpty()) return null
                val rootDirectory =
                    when (pathSegments.first()) {
                        NOTE_HEROES_DIR -> NOTE_HEROES_DIR
                        NOTE_ATTACHMENTS_DIR -> NOTE_ATTACHMENTS_DIR
                        REMEMBER_BACKUP_DIR -> REMEMBER_BACKUP_DIR
                        else -> return null
                    }
                val relativePathSegments = pathSegments.drop(1)
                if (relativePathSegments.isEmpty()) return null
                val candidate =
                    relativePathSegments.fold(File(context.filesDir, rootDirectory)) { currentDirectory, pathSegment ->
                        File(currentDirectory, pathSegment)
                    }
                val baseDirectory = context.filesDir.canonicalFile
                val canonicalCandidate = candidate.canonicalFile
                return if (canonicalCandidate.path.startsWith(baseDirectory.path)) {
                    canonicalCandidate
                } else {
                    null
                }
            }

            private fun safeFileName(
                displayName: String,
                mimeType: String?,
            ): String {
                val rawName = displayName.ifBlank { "attachment${extensionFromMimeType(mimeType)}" }
                val sanitized = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
                return sanitized.ifBlank { "attachment${extensionFromMimeType(mimeType)}" }
            }

            private fun extensionFromMimeType(mimeType: String?): String =
                when (mimeType) {
                    "image/jpeg" -> ".jpg"
                    "image/png" -> ".png"
                    "image/webp" -> ".webp"
                    "application/pdf" -> ".pdf"
                    "text/plain" -> ".txt"
                    else -> ".bin"
                }
        }
    }
