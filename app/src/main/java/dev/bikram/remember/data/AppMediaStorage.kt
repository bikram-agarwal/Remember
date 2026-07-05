package dev.bikram.remember.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class MediaCopyResult(
    val uriString: String,
    val copiedToAppStorage: Boolean,
    val mimeType: String?,
    val displayName: String,
)

@Singleton
class AppMediaStorage
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val backupPrefs: BackupPrefs,
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
                var destinationFile =
                    File(
                        destinationDirectory,
                        "${System.currentTimeMillis()}_${safeFileName(displayName, mimeType)}",
                    )
                val compress = backupPrefs.state.first().compressImages
                val isImage = mimeType?.startsWith("image/") == true
                var finalMimeType = mimeType
                var finalDisplayName = displayName
                val copyResult =
                    runCatching {
                        val compressed =
                            if (compress && isImage) {
                                compressImage(context, sourceUri, destinationFile)
                            } else {
                                false
                            }
                        if (compressed) {
                            val jpegFile = File(destinationFile.parentFile, jpegFileName(destinationFile.name))
                            if (destinationFile.renameTo(jpegFile)) {
                                destinationFile = jpegFile
                            }
                            finalMimeType = "image/jpeg"
                            finalDisplayName = jpegFileName(displayName)
                        } else {
                            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                                destinationFile.outputStream().use { output -> input.copyTo(output) }
                            } ?: throw IOException("openInputStream failed")
                        }
                    }
                if (copyResult.isSuccess) {
                    MediaCopyResult(
                        uriString = fileProviderUri(destinationFile),
                        copiedToAppStorage = true,
                        mimeType = finalMimeType,
                        displayName = finalDisplayName,
                    )
                } else {
                    runCatching { destinationFile.delete() }
                    MediaCopyResult(
                        uriString = sourceUri.toString(),
                        copiedToAppStorage = false,
                        mimeType = mimeType,
                        displayName = displayName,
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

            suspend fun compressImage(
                context: Context,
                sourceUri: Uri,
                destinationFile: File,
            ): Boolean =
                withContext(Dispatchers.IO) {
                    if (isAnimatedImage(context, sourceUri)) {
                        return@withContext false
                    }
                    val compressed =
                        runCatching {
                            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                                // 1. Read dimensions
                                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeStream(input, null, options)

                                if (options.outWidth <= 0 || options.outHeight <= 0) {
                                    return@runCatching false
                                }

                                // 2. Read EXIF orientation
                                var orientation = ExifInterface.ORIENTATION_NORMAL
                                context.contentResolver.openInputStream(sourceUri)?.use { exifInput ->
                                    val exif = ExifInterface(exifInput)
                                    orientation =
                                        exif.getAttributeInt(
                                            ExifInterface.TAG_ORIENTATION,
                                            ExifInterface.ORIENTATION_NORMAL,
                                        )
                                }

                                // 3. Calculate sample size
                                val maxDimension = 2048
                                var inSampleSize = 1
                                if (options.outWidth > maxDimension || options.outHeight > maxDimension) {
                                    val halfWidth = options.outWidth / 2
                                    val halfHeight = options.outHeight / 2
                                    while (halfWidth / inSampleSize >= maxDimension && halfHeight / inSampleSize >= maxDimension) {
                                        inSampleSize *= 2
                                    }
                                }

                                // 4. Decode bitmap with inSampleSize
                                context.contentResolver.openInputStream(sourceUri)?.use { bitmapInput ->
                                    val decodeOptions =
                                        BitmapFactory.Options().apply {
                                            this.inSampleSize = inSampleSize
                                        }
                                    val bitmap =
                                        BitmapFactory.decodeStream(bitmapInput, null, decodeOptions)
                                            ?: return@runCatching false

                                    try {
                                        // 5. Rotate bitmap if needed
                                        val matrix = Matrix()
                                        when (orientation) {
                                            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                                            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                                            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                                        }
                                        val rotatedBitmap =
                                            if (matrix.isIdentity) {
                                                bitmap
                                            } else {
                                                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                            }

                                        try {
                                            // 6. Compress and save to destination
                                            destinationFile.outputStream().use { output ->
                                                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
                                            }
                                            true
                                        } finally {
                                            if (rotatedBitmap != bitmap) {
                                                rotatedBitmap.recycle()
                                            }
                                        }
                                    } finally {
                                        bitmap.recycle()
                                    }
                                } ?: false
                            } ?: false
                        }.getOrDefault(false)
                    if (!compressed) {
                        return@withContext false
                    }
                    val sourceSize = sourceSizeBytes(context, sourceUri)
                    if (sourceSize != null && sourceSize > 0 && destinationFile.length() >= sourceSize) {
                        runCatching { destinationFile.delete() }
                        return@withContext false
                    }
                    true
                }

            private class HeaderOnlyAbort : RuntimeException()

            private fun isAnimatedImage(
                context: Context,
                sourceUri: Uri,
            ): Boolean {
                var animated = false
                runCatching {
                    val source = ImageDecoder.createSource(context.contentResolver, sourceUri)
                    ImageDecoder.decodeDrawable(source) { _, info, _ ->
                        animated = info.isAnimated
                        throw HeaderOnlyAbort()
                    }
                }
                return animated
            }

            private fun sourceSizeBytes(
                context: Context,
                sourceUri: Uri,
            ): Long? =
                runCatching {
                    context.contentResolver
                        .query(sourceUri, arrayOf(OpenableColumns.SIZE), null, null, null)
                        ?.use { cursor ->
                            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
                        }
                }.getOrNull()

            private fun jpegFileName(originalName: String): String {
                val base = originalName.ifBlank { "attachment" }
                val dotIndex = base.lastIndexOf('.')
                val stem = if (dotIndex > 0) base.substring(0, dotIndex) else base
                return "$stem.jpg"
            }

            fun isAppStoredMediaUri(
                context: Context,
                uriString: String?,
            ): Boolean = appStoredFileForUri(context, uriString) != null

            fun appStoredFileForUri(
                context: Context,
                uriString: String?,
            ): File? {
                if (uriString.isNullOrBlank()) return null
                val uri = runCatching { uriString.toUri() }.getOrNull() ?: return null
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
                return canonicalFileInsideBaseDirectoryOrNull(
                    candidate = candidate,
                    baseDirectory = context.filesDir,
                )
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
