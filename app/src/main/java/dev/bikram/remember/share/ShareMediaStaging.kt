package dev.bikram.remember.share

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import dev.bikram.remember.ui.edit.resolveDisplayName
import dev.bikram.remember.ui.edit.resolveMimeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Cap individual shared file copies at 100 MB so corrupt streams cannot exhaust cache space. */
private const val MAX_STAGED_IMAGE_BYTES = 100L * 1024L * 1024L

internal data class StagedShareImage(
    val id: String,
    val file: File,
    val displayName: String,
    val mimeType: String?,
) {
    /** Shared files that are not images are staged and attached the same way, just shown as file tiles. */
    val isImage: Boolean get() = mimeType?.startsWith("image/", ignoreCase = true) == true
}

internal data class ShareImageStageResult(
    val stagedImages: List<StagedShareImage>,
    val failedCount: Int,
)

/**
 * Copies incoming image stream URIs into `cacheDir/share_staging/<sessionId>/` while the
 * calling app's `FLAG_GRANT_READ_URI_PERMISSION` is still active on [ShareReceiverActivity].
 */
internal object ShareMediaStaging {
    private const val STAGING_ROOT_DIR = "share_staging"
    private val orphansDeleted = AtomicBoolean(false)

    suspend fun stageImages(
        context: Context,
        sessionId: String,
        uris: List<Uri>,
    ): ShareImageStageResult =
        withContext(Dispatchers.IO) {
            val sessionDirectory = sessionDirectory(context.cacheDir, sessionId).apply { mkdirs() }
            val staged = mutableListOf<StagedShareImage>()
            var failedCount = 0

            uris.forEachIndexed { index, uri ->
                val rawDisplayName = runCatching { resolveDisplayName(context, uri) }.getOrDefault("file_${index + 1}")
                val rawMimeType = runCatching { resolveMimeType(context, uri) }.getOrNull()
                val effectiveMimeType =
                    rawMimeType?.takeIf { it.isNotBlank() && !it.equals("application/octet-stream", ignoreCase = true) }
                        ?: inferMimeType(rawDisplayName)
                val safeName = sanitizeStagedFileName(rawDisplayName, effectiveMimeType, index)
                val targetFile = File(sessionDirectory, "${index}_${UUID.randomUUID()}_$safeName")

                val copied =
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            targetFile.outputStream().use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                var totalBytes = 0L
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    totalBytes += read
                                    if (totalBytes > MAX_STAGED_IMAGE_BYTES) {
                                        throw IOException("Shared image exceeds staging size limit")
                                    }
                                    output.write(buffer, 0, read)
                                }
                                totalBytes > 0L
                            }
                        } ?: false
                    }.getOrDefault(false)

                if (copied) {
                    staged +=
                        StagedShareImage(
                            id = targetFile.name,
                            file = targetFile,
                            displayName = rawDisplayName.ifBlank { safeName },
                            mimeType = effectiveMimeType,
                        )
                } else {
                    runCatching { targetFile.delete() }
                    failedCount++
                }
            }

            ShareImageStageResult(
                stagedImages = staged,
                failedCount = failedCount,
            )
        }

    fun restoreSessionImages(
        cacheDir: File,
        sessionId: String,
    ): List<StagedShareImage> {
        val dir = sessionDirectory(cacheDir, sessionId)
        if (!dir.isDirectory) return emptyList()
        return dir
            .listFiles { file -> file.isFile && file.length() > 0L }
            ?.sortedBy(File::getName)
            ?.map { file ->
                val cleanName = file.name.substringAfter('_', file.name).substringAfter('_', file.name)
                StagedShareImage(
                    id = file.name,
                    file = file,
                    displayName = cleanName,
                    mimeType = inferMimeType(cleanName),
                )
            }.orEmpty()
    }

    fun removeStagedImage(image: StagedShareImage) {
        runCatching { image.file.delete() }
    }

    fun deleteSession(
        cacheDir: File,
        sessionId: String,
    ) {
        val dir = sessionDirectory(cacheDir, sessionId)
        runCatching {
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        }
    }

    fun deleteOrphanSessionsOnce(
        cacheDir: File,
        keepSessionId: String?,
    ) {
        if (!orphansDeleted.compareAndSet(false, true)) return
        val root = File(cacheDir, STAGING_ROOT_DIR)
        if (!root.isDirectory) return
        root.listFiles()?.forEach { child ->
            if (child.name != keepSessionId) {
                runCatching { child.deleteRecursively() }
            }
        }
    }

    private fun sessionDirectory(
        cacheDir: File,
        sessionId: String,
    ): File = File(File(cacheDir, STAGING_ROOT_DIR), sessionId)

    private fun sanitizeStagedFileName(
        displayName: String,
        mimeType: String?,
        index: Int,
    ): String {
        val fallbackExtension = extensionForMimeType(mimeType)
        val raw = displayName.ifBlank { "shared_file_${index + 1}$fallbackExtension" }
        val sanitized = raw.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(64)
        return if ('.' in sanitized) sanitized else "$sanitized$fallbackExtension"
    }

    private fun inferMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "avif" -> "image/avif"
            else ->
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                    ?: "application/octet-stream"
        }
    }

    private fun extensionForMimeType(mimeType: String?): String {
        val lower = mimeType?.lowercase() ?: return ""
        return when (lower) {
            "image/heic" -> ".heic"
            "image/heif" -> ".heif"
            "image/avif" -> ".avif"
            else ->
                MimeTypeMap
                    .getSingleton()
                    .getExtensionFromMimeType(lower)
                    ?.let { ".$it" }
                    .orEmpty()
        }
    }
}
