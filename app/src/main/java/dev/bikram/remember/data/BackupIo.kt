package dev.bikram.remember.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dev.bikram.remember.backup.RememberBackupWork
import dev.bikram.remember.backup.SettingsBackup
import dev.bikram.remember.diagnostics.DiagnosticLog
import dev.bikram.remember.domain.backupFileTimestamp
import dev.bikram.remember.update.UpdateCheckWorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal data class ZipExtractionLimits(
    val maximumEntryCount: Int = 10_000,
    val maximumEntryBytes: Long = 256L * 1024L * 1024L,
    val maximumTotalBytes: Long = 1024L * 1024L * 1024L,
)

private class ByteLimitedInputStream(
    private val source: InputStream,
    private val maximumBytes: Long,
) : InputStream() {
    private var consumedBytes = 0L

    override fun read(): Int {
        val value = source.read()
        if (value >= 0) recordBytesRead(1)
        return value
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        val bytesRead = source.read(buffer, offset, length)
        if (bytesRead > 0) recordBytesRead(bytesRead)
        return bytesRead
    }

    override fun close() {
        source.close()
    }

    private fun recordBytesRead(bytesRead: Int) {
        consumedBytes += bytesRead
        if (consumedBytes > maximumBytes) {
            throw IOException("Backup JSON exceeds the input-size limit")
        }
    }
}

@Suppress("ktlint:standard:function-expression-body")
internal fun readUtf8TextWithinLimit(
    inputStream: InputStream,
    maximumBytes: Long,
): String? {
    return runCatching {
        ByteLimitedInputStream(inputStream, maximumBytes)
            .bufferedReader(Charsets.UTF_8)
            .use { reader -> reader.readText() }
    }.getOrNull()
}

@Suppress("ktlint:standard:function-expression-body")
internal fun extractZipEntriesWithinLimits(
    inputStream: InputStream,
    extractRoot: File,
    limits: ZipExtractionLimits = ZipExtractionLimits(),
): Boolean {
    return runCatching {
        var extractedEntryCount = 0
        var extractedTotalBytes = 0L
        val copyBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
        ZipInputStream(BufferedInputStream(inputStream)).use { zipInput ->
            var zipEntry = zipInput.nextEntry
            while (zipEntry != null) {
                extractedEntryCount++
                if (extractedEntryCount > limits.maximumEntryCount) {
                    return@runCatching false
                }
                if (zipEntry.size > limits.maximumEntryBytes) {
                    return@runCatching false
                }
                if (!zipEntry.isDirectory) {
                    val outputFile =
                        canonicalFileInsideBaseDirectoryOrNull(
                            candidate = File(extractRoot, zipEntry.name),
                            baseDirectory = extractRoot,
                        ) ?: return@runCatching false
                    val outputDirectory = outputFile.parentFile
                    if (outputDirectory != null && !outputDirectory.exists() && !outputDirectory.mkdirs()) {
                        return@runCatching false
                    }
                    var extractedEntryBytes = 0L
                    FileOutputStream(outputFile).use { fileOutput ->
                        var bytesRead = zipInput.read(copyBuffer)
                        while (bytesRead >= 0) {
                            if (bytesRead > 0) {
                                extractedEntryBytes += bytesRead
                                extractedTotalBytes += bytesRead
                                if (extractedEntryBytes > limits.maximumEntryBytes) {
                                    return@runCatching false
                                }
                                if (extractedTotalBytes > limits.maximumTotalBytes) {
                                    return@runCatching false
                                }
                                fileOutput.write(copyBuffer, 0, bytesRead)
                            }
                            bytesRead = zipInput.read(copyBuffer)
                        }
                    }
                }
                zipInput.closeEntry()
                zipEntry = zipInput.nextEntry
            }
        }
        true
    }.getOrDefault(false)
}

class BackupIo(
    private val context: Context,
    private val repository: NoteRepository,
    private val themePrefs: ThemePrefs,
    private val viewOptionsPrefs: ViewOptionsPrefs,
    private val lockPrefs: LockPrefs,
    private val interactionPrefs: InteractionPrefs,
    private val backupPrefs: BackupPrefs,
    private val quickCapturePrefs: QuickCapturePrefs,
    private val reminderPrefs: ReminderPrefs,
    private val updatePrefs: UpdatePrefs,
    private val updateCheckWorkScheduler: UpdateCheckWorkScheduler,
) {
    private val fileProviderAuthority: String
        get() = "${context.packageName}.fileprovider"

    data class RestoreMediaSummary(
        val includeMediaRequested: Boolean?,
        val mediaReferenceCount: Int,
        val mediaEmbeddedCount: Int,
        val mediaLinkedCount: Int,
        val mediaFailedCount: Int,
        val readable: Boolean = true,
    ) {
        val hasMissingMedia: Boolean
            get() = readable && mediaReferenceCount > mediaEmbeddedCount
    }

    data class RestoreResult(
        val noteCount: Int,
        val settingsOutcome: BackupSettingsRestoreOutcome,
    )

    private data class MediaExportStats(
        var mediaReferenceCount: Int = 0,
        var mediaEmbeddedCount: Int = 0,
        var mediaLinkedCount: Int = 0,
        var mediaFailedCount: Int = 0,
    )

    fun suggestedBackupFileName(): String = "remember_backup_${backupFileTimestamp()}.zip"

    private suspend fun buildSettingsJson(): JSONObject = SettingsBackup.exportJson(themePrefs, viewOptionsPrefs, lockPrefs, interactionPrefs, backupPrefs, quickCapturePrefs, reminderPrefs, updatePrefs)

    private suspend fun importSettingsFromJson(settingsJson: JSONObject?): BackupSettingsRestoreOutcome {
        val restoreOutcome =
            SettingsBackup.importJson(settingsJson, themePrefs, viewOptionsPrefs, lockPrefs, interactionPrefs, backupPrefs, quickCapturePrefs, reminderPrefs, updatePrefs)
        runCatching {
            RememberBackupWork.updateSchedule(context, backupPrefs.snapshot())
        }.onFailure { error ->
            DiagnosticLog.record(context, "Restored backup could not reconcile scheduled exports", error)
        }
        runCatching {
            updateCheckWorkScheduler.syncFromPreferences()
        }.onFailure { error ->
            DiagnosticLog.record(context, "Restored backup could not reconcile update checks", error)
        }
        return restoreOutcome
    }

    private data class NotesSnapshot(
        val root: JSONObject,
        val noteCount: Int,
    )

    private suspend fun snapshotNotes(): NotesSnapshot {
        val all =
            notesForBackup(
                activeNotes = repository.observeActive().first(),
                archivedNotes = repository.observeArchived().first(),
                trashedNotes = repository.observeTrashed().first(),
            )
        val tagColors =
            repository.tagRepository
                ?.observeTagColorMap()
                ?.first()
                ?: emptyMap()
        val root =
            JSONObject().apply {
                put("version", SCHEMA_VERSION)
                put("exportedAt", System.currentTimeMillis())
                put(
                    "tagColors",
                    JSONObject().apply {
                        tagColors.forEach { (tagName, colorHex) -> put(tagName, colorHex) }
                    },
                )
                put(
                    "notes",
                    JSONArray().apply {
                        all.forEach { put(encode(it)) }
                    },
                )
            }
        return NotesSnapshot(root, all.size)
    }

    private fun encode(n: NoteWithItems): JSONObject {
        val note = n.note
        return JSONObject().apply {
            put("id", note.id)
            put("kind", note.kind.name)
            put("title", note.title)
            put("body", note.body)
            put("colorIndex", note.colorIndex)
            put("starred", note.starred)
            put("trashed", note.trashed)
            put("archived", note.archived)
            note.trashedAt?.let { put("trashedAt", it) }
            note.completedAt?.let { put("completedAt", it) }
            note.pinnedAt?.let { put("pinnedAt", it) }
            put("createdAt", note.createdAt)
            put("updatedAt", note.updatedAt)
            note.reminderAt?.let { put("reminderAt", it) }
            put("importance", note.importance.name)
            put("visibility", note.visibility.name)
            note.pictureUri?.let { put("pictureUri", it) }
            note.pictureHeroFraming?.let { put("pictureHeroFraming", it) }
            put("locked", note.locked)
            note.iconKey?.let { put("iconKey", it) }
            put(
                "actions",
                JSONArray().apply {
                    note.actions.forEach { a ->
                        put(
                            JSONObject().apply {
                                put("type", a.type.name)
                                put("title", a.title)
                                put("details", a.details)
                                a.extra?.let { put("extra", it) }
                                a.iconData?.let { put("iconData", it) }
                            },
                        )
                    }
                },
            )
            put("tags", JSONArray().apply { note.tags.forEach { put(it) } })
            RecurrenceRule.toJson(note.recurrence)?.let { put("recurrence", it) }
            put(
                "reminders",
                JSONArray().apply {
                    note.reminders.limitedToReminderSlots().forEach { reminder ->
                        put(encodeReminderForBackup(reminder))
                    }
                },
            )
            put(
                "items",
                JSONArray().apply {
                    n.items.forEach { it2 ->
                        put(
                            JSONObject().apply {
                                put("id", it2.id)
                                put("text", it2.text)
                                put("details", it2.details)
                                put("checked", it2.checked)
                                put("sortOrder", it2.sortOrder)
                                it2.parentId?.let { parent -> put("parentId", parent) }
                                put("depth", it2.depth)
                            },
                        )
                    }
                },
            )
            put(
                "attachments",
                JSONArray().apply {
                    n.attachments.forEach { a ->
                        put(
                            JSONObject().apply {
                                put("uri", a.uri)
                                put("displayName", a.displayName)
                                a.mimeType?.let { put("mimeType", it) }
                            },
                        )
                    }
                },
            )
        }
    }

    private suspend fun writeZipArchive(
        outputStream: OutputStream,
        notesJson: JSONObject,
        includeMedia: Boolean,
    ) {
        ZipOutputStream(outputStream).use { zip ->
            val mediaStats =
                if (includeMedia) {
                    embedMediaInNotesJson(zip, notesJson)
                } else {
                    countLinkedMediaInNotesJson(notesJson)
                }
            if (!includeMedia) {
                mediaStats.mediaLinkedCount = mediaStats.mediaReferenceCount
            }
            writeZipTextEntry(
                zip = zip,
                entryName = ENTRY_MANIFEST,
                text =
                    buildBackupManifestJson(
                        includeMediaRequested = includeMedia,
                        mediaStats = mediaStats,
                    ).toString(2),
            )
            writeZipTextEntry(zip, ENTRY_NOTES, notesJson.toString(2))
            writeZipTextEntry(zip, ENTRY_SETTINGS, buildSettingsJson().toString(2))
        }
    }

    private fun writeZipTextEntry(
        zip: ZipOutputStream,
        entryName: String,
        text: String,
    ) {
        zip.putNextEntry(ZipEntry(entryName))
        val writer = OutputStreamWriter(zip, Charsets.UTF_8)
        writer.write(text)
        writer.flush()
        zip.closeEntry()
    }

    private fun embedMediaInNotesJson(
        zip: ZipOutputStream,
        notesRoot: JSONObject,
    ): MediaExportStats {
        val mediaStats = MediaExportStats()
        val notesArray = notesRoot.optJSONArray("notes") ?: return mediaStats
        for (noteIndex in 0 until notesArray.length()) {
            val noteJson = notesArray.getJSONObject(noteIndex)
            val noteId = noteJson.optLong("id", 0L)
            if (noteId <= 0L) continue
            val pictureUri = noteJson.optStringOrNull("pictureUri")
            if (!pictureUri.isNullOrBlank()) {
                mediaStats.mediaReferenceCount++
                val entryPath = "media/$noteId/picture${guessExtension(pictureUri, null)}"
                if (copyUriIntoZip(zip, entryPath, pictureUri.toUri())) {
                    noteJson.put("pictureUri", "$REL_PREFIX$entryPath")
                    mediaStats.mediaEmbeddedCount++
                } else {
                    mediaStats.mediaLinkedCount++
                    mediaStats.mediaFailedCount++
                }
            }
            val attachmentsArray = noteJson.optJSONArray("attachments") ?: continue
            for (attachmentIndex in 0 until attachmentsArray.length()) {
                val attachmentJson = attachmentsArray.getJSONObject(attachmentIndex)
                val attachmentUriString = attachmentJson.optString("uri", "")
                if (attachmentUriString.isBlank()) continue
                mediaStats.mediaReferenceCount++
                val extension = guessExtension(attachmentUriString, attachmentJson.optStringOrNull("mimeType"))
                val entryPath = "media/$noteId/att_$attachmentIndex$extension"
                if (copyUriIntoZip(zip, entryPath, attachmentUriString.toUri())) {
                    attachmentJson.put("uri", "$REL_PREFIX$entryPath")
                    mediaStats.mediaEmbeddedCount++
                } else {
                    mediaStats.mediaLinkedCount++
                    mediaStats.mediaFailedCount++
                }
            }
        }
        return mediaStats
    }

    private fun countLinkedMediaInNotesJson(notesRoot: JSONObject): MediaExportStats {
        val mediaStats = MediaExportStats()
        val notesArray = notesRoot.optJSONArray("notes") ?: return mediaStats
        for (noteIndex in 0 until notesArray.length()) {
            val note = notesArray.getJSONObject(noteIndex)
            if (!note.optStringOrNull("pictureUri").isNullOrBlank()) {
                mediaStats.mediaReferenceCount++
            }
            val attachments = note.optJSONArray("attachments") ?: continue
            for (attachmentIndex in 0 until attachments.length()) {
                val attachment = attachments.getJSONObject(attachmentIndex)
                if (attachment.optString("uri", "").isNotBlank()) {
                    mediaStats.mediaReferenceCount++
                }
            }
        }
        return mediaStats
    }

    private fun buildBackupManifestJson(
        includeMediaRequested: Boolean,
        mediaStats: MediaExportStats,
    ): JSONObject =
        JSONObject().apply {
            put("version", 1)
            put("includeMediaRequested", includeMediaRequested)
            put("mediaReferenceCount", mediaStats.mediaReferenceCount)
            put("mediaEmbeddedCount", mediaStats.mediaEmbeddedCount)
            put("mediaLinkedCount", mediaStats.mediaLinkedCount)
            put("mediaFailedCount", mediaStats.mediaFailedCount)
        }

    private fun guessExtension(
        uriString: String,
        mimeType: String?,
    ): String {
        val filePart = uriString.substringAfterLast('/', uriString).substringBeforeLast('?', uriString)
        val dotIndex = filePart.lastIndexOf('.')
        if (dotIndex >= 0 && dotIndex < filePart.length - 1) {
            return filePart.substring(dotIndex)
        }
        val extension = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return if (!extension.isNullOrBlank()) ".$extension" else ".bin"
    }

    private fun copyUriIntoZip(
        zip: ZipOutputStream,
        entryName: String,
        source: Uri,
    ): Boolean =
        runCatching {
            val inputStream = context.contentResolver.openInputStream(source) ?: return@runCatching false
            inputStream.use { rawInput ->
                zip.putNextEntry(ZipEntry(entryName))
                BufferedInputStream(rawInput).copyTo(zip)
                zip.closeEntry()
            }
            true
        }.getOrDefault(false)

    /** @return note count on success, or -1 if the output stream could not be opened. */
    suspend fun exportTo(uri: Uri): Int =
        withContext(Dispatchers.IO) {
            runCatching {
                val includeMedia = backupPrefs.snapshot().includeMediaInBackup
                val snapshot = snapshotNotes()
                openDocumentOutputStream(uri).use { outputStream ->
                    writeZipArchive(outputStream, snapshot.root, includeMedia)
                }
                snapshot.noteCount
            }.onFailure { throwable ->
                DiagnosticLog.record(context, "Manual backup export failed", throwable)
            }.getOrDefault(-1)
        }

    suspend fun exportToTreeFolder(treeUriString: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!treeUriString.startsWith("content://")) error("Invalid export folder")
                val exportedFileNames = exportToTreeFolders(listOf(treeUriString)).getOrThrow()
                exportedFileNames.first()
            }.onFailure { throwable ->
                DiagnosticLog.record(context, "Scheduled backup export failed", throwable)
            }
        }

    suspend fun exportToTreeFolders(treeUriStrings: List<String>): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val destinations = treeUriStrings.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                if (destinations.isEmpty()) error("No export folder")
                val includeMedia = backupPrefs.snapshot().includeMediaInBackup
                val snapshot = snapshotNotes()
                val fileName = "remember_backup_${backupFileTimestamp()}.zip"
                val temporaryArchive = File.createTempFile("remember_backup_", ".zip", context.cacheDir)
                try {
                    FileOutputStream(temporaryArchive).use { outputStream ->
                        writeZipArchive(outputStream, snapshot.root, includeMedia)
                    }
                    destinations.forEach { destinationUriString ->
                        if (!destinationUriString.startsWith("content://")) error("Invalid export folder")
                        val destinationUri = destinationUriString.toUri()
                        if (DocumentsContract.isTreeUri(destinationUri)) {
                            writeTreeDocument(destinationUri, fileName, "application/zip", temporaryArchive)
                        } else {
                            writeDocumentFile(destinationUri, temporaryArchive, mode = "wt")
                        }
                    }
                } finally {
                    temporaryArchive.delete()
                }
                List(destinations.size) { fileName }
            }
        }

    private fun writeTreeDocument(
        treeUri: Uri,
        fileName: String,
        mimeType: String,
        sourceFile: File,
    ) {
        val resolver = context.contentResolver
        val documentTreeUri =
            DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
        val temporaryName = "$fileName.${UUID.randomUUID()}.partial"
        val temporaryUri =
            DocumentsContract.createDocument(
                resolver,
                documentTreeUri,
                mimeType,
                temporaryName,
            ) ?: error("Failed to create temporary backup document")
        var fallbackDestinationUri: Uri? = null
        try {
            writeDocumentFile(temporaryUri, sourceFile)
            val publishedUri =
                runCatching {
                    DocumentsContract.renameDocument(resolver, temporaryUri, fileName)
                }.getOrNull()
            if (publishedUri == null) {
                val createdDestinationUri =
                    DocumentsContract.createDocument(
                        resolver,
                        documentTreeUri,
                        mimeType,
                        fileName,
                    ) ?: error("Failed to create backup document")
                fallbackDestinationUri = createdDestinationUri
                writeDocumentFile(createdDestinationUri, sourceFile)
                runCatching { resolver.delete(temporaryUri, null, null) }
            }
        } catch (error: Exception) {
            runCatching { resolver.delete(temporaryUri, null, null) }
            fallbackDestinationUri?.let { destinationUri ->
                runCatching { resolver.delete(destinationUri, null, null) }
            }
            throw error
        }
    }

    private fun writeDocumentFile(
        documentUri: Uri,
        sourceFile: File,
        mode: String? = null,
    ) {
        openDocumentOutputStream(documentUri, mode).use { outputStream ->
            sourceFile.inputStream().buffered().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
            outputStream.flush()
        }
    }

    @Suppress("ktlint:standard:function-expression-body")
    private fun openDocumentOutputStream(
        documentUri: Uri,
        mode: String? = null,
    ): OutputStream {
        return if (mode == null) {
            context.contentResolver.openOutputStream(documentUri)
        } else {
            context.contentResolver.openOutputStream(documentUri, mode)
        } ?: error("Failed to open output stream for backup document")
    }

    suspend fun restoreFullReplace(uri: Uri): RestoreResult =
        withContext(Dispatchers.IO) {
            val payload =
                readRestorePayload(uri)
                    ?: return@withContext RestoreResult(
                        noteCount = 0,
                        settingsOutcome = BackupSettingsRestoreOutcome(),
                    )
            try {
                val count =
                    repository.restoreNotesFullReplace {
                        importFromJsonText(
                            text = payload.notesText,
                            extractDir = payload.extractRoot,
                            preserveNoteIds = true,
                            suppressReminderSchedule = true,
                        ).noteCount
                    }
                val settingsOutcome = importSettingsFromJson(payload.settingsJson)
                RestoreResult(noteCount = count, settingsOutcome = settingsOutcome)
            } finally {
                payload.extractRoot?.deleteRecursively()
            }
        }

    suspend fun importFrom(
        uri: Uri,
        preserveIdsForNotes: Boolean = false,
    ): Int =
        withContext(Dispatchers.IO) {
            runCatching {
                if (isZipUri(uri)) {
                    importFromZip(uri, preserveIdsForNotes)
                } else {
                    val text =
                        context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
                            ?: return@runCatching 0
                    importNotesAtomically(
                        text = text,
                        extractDir = null,
                        preserveNoteIds = preserveIdsForNotes,
                    ).noteCount
                }
            }.onFailure { throwable ->
                DiagnosticLog.record(context, "Backup import failed", throwable)
            }.getOrDefault(0)
        }

    suspend fun inspectRestoreMedia(uri: Uri): RestoreMediaSummary =
        withContext(Dispatchers.IO) {
            val payload =
                readRestorePayload(uri)
                    ?: return@withContext RestoreMediaSummary(
                        includeMediaRequested = null,
                        mediaReferenceCount = 0,
                        mediaEmbeddedCount = 0,
                        mediaLinkedCount = 0,
                        mediaFailedCount = 0,
                        readable = false,
                    )
            try {
                restoreMediaSummaryFromPayload(payload)
            } finally {
                payload.extractRoot?.deleteRecursively()
            }
        }

    private data class RestorePayload(
        val notesText: String,
        val settingsJson: JSONObject?,
        val manifestJson: JSONObject?,
        val extractRoot: File?,
    )

    private data class NoteImportResult(
        val noteCount: Int,
        val noteIds: List<Long>,
    )

    private fun restoreMediaSummaryFromPayload(payload: RestorePayload): RestoreMediaSummary {
        val inferredStats =
            runCatching {
                val notesRoot = JSONObject(payload.notesText)
                inferRestoreMediaStats(notesRoot)
            }.getOrDefault(MediaExportStats())
        val manifest = payload.manifestJson
        return if (manifest != null) {
            RestoreMediaSummary(
                includeMediaRequested =
                    if (manifest.has("includeMediaRequested") && !manifest.isNull("includeMediaRequested")) {
                        manifest.getBoolean("includeMediaRequested")
                    } else {
                        null
                    },
                mediaReferenceCount = manifest.optInt("mediaReferenceCount", inferredStats.mediaReferenceCount),
                mediaEmbeddedCount = manifest.optInt("mediaEmbeddedCount", inferredStats.mediaEmbeddedCount),
                mediaLinkedCount = manifest.optInt("mediaLinkedCount", inferredStats.mediaLinkedCount),
                mediaFailedCount = manifest.optInt("mediaFailedCount", inferredStats.mediaFailedCount),
            )
        } else {
            RestoreMediaSummary(
                includeMediaRequested = null,
                mediaReferenceCount = inferredStats.mediaReferenceCount,
                mediaEmbeddedCount = inferredStats.mediaEmbeddedCount,
                mediaLinkedCount = inferredStats.mediaLinkedCount,
                mediaFailedCount = inferredStats.mediaFailedCount,
            )
        }
    }

    private fun inferRestoreMediaStats(notesRoot: JSONObject): MediaExportStats {
        val mediaStats = MediaExportStats()
        val notesArray = notesRoot.optJSONArray("notes") ?: return mediaStats
        for (noteIndex in 0 until notesArray.length()) {
            val noteJson = notesArray.getJSONObject(noteIndex)
            noteJson.optStringOrNull("pictureUri")?.takeIf { uriString -> uriString.isNotBlank() }?.let { uriString ->
                mediaStats.mediaReferenceCount++
                if (uriString.startsWith(REL_PREFIX)) {
                    mediaStats.mediaEmbeddedCount++
                } else {
                    mediaStats.mediaLinkedCount++
                }
            }
            val attachmentsArray = noteJson.optJSONArray("attachments") ?: continue
            for (attachmentIndex in 0 until attachmentsArray.length()) {
                val attachmentUriString = attachmentsArray.getJSONObject(attachmentIndex).optString("uri", "")
                if (attachmentUriString.isBlank()) continue
                mediaStats.mediaReferenceCount++
                if (attachmentUriString.startsWith(REL_PREFIX)) {
                    mediaStats.mediaEmbeddedCount++
                } else {
                    mediaStats.mediaLinkedCount++
                }
            }
        }
        return mediaStats
    }

    /**
     * Reads and validates backup contents without mutating notes. Used so a corrupt or
     * unreadable archive never triggers a destructive delete first.
     */
    private fun readRestorePayload(uri: Uri): RestorePayload? {
        if (isZipUri(uri)) {
            val extractRoot = File(context.cacheDir, "remember_zip_restore_${UUID.randomUUID()}")
            if (extractRoot.exists()) extractRoot.deleteRecursively()
            extractRoot.mkdirs()
            if (!materializeZipEntries(uri, extractRoot)) {
                extractRoot.deleteRecursively()
                return null
            }
            val notesFile = File(extractRoot, ENTRY_NOTES)
            if (!notesFile.isFile) {
                extractRoot.deleteRecursively()
                return null
            }
            val notesText =
                notesFile.inputStream().use { inputStream ->
                    readUtf8TextWithinLimit(inputStream, MAX_NOTES_JSON_BYTES)
                } ?: run {
                    extractRoot.deleteRecursively()
                    return null
                }
            val root =
                runCatching { JSONObject(notesText) }.getOrNull() ?: run {
                    extractRoot.deleteRecursively()
                    return null
                }
            if (!root.has("notes")) {
                extractRoot.deleteRecursively()
                return null
            }
            val settingsFile = File(extractRoot, ENTRY_SETTINGS)
            val settingsJson =
                if (settingsFile.isFile) {
                    settingsFile
                        .inputStream()
                        .use { inputStream ->
                            readUtf8TextWithinLimit(inputStream, MAX_METADATA_JSON_BYTES)
                        }?.let { settingsText ->
                            runCatching { JSONObject(settingsText) }.getOrNull()
                        }
                } else {
                    null
                }
            val manifestFile = File(extractRoot, ENTRY_MANIFEST)
            val manifestJson =
                if (manifestFile.isFile) {
                    manifestFile
                        .inputStream()
                        .use { inputStream ->
                            readUtf8TextWithinLimit(inputStream, MAX_METADATA_JSON_BYTES)
                        }?.let { manifestText ->
                            runCatching { JSONObject(manifestText) }.getOrNull()
                        }
                } else {
                    null
                }
            return RestorePayload(notesText, settingsJson, manifestJson, extractRoot)
        }
        val text =
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                readUtf8TextWithinLimit(inputStream, MAX_NOTES_JSON_BYTES)
            }
                ?: return null
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (!root.has("notes")) return null
        return RestorePayload(text, null, null, null)
    }

    @Suppress("ktlint:standard:function-expression-body")
    private fun materializeZipEntries(
        uri: Uri,
        extractRoot: File,
    ): Boolean {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                extractZipEntriesWithinLimits(inputStream, extractRoot)
            } ?: false
        }.getOrDefault(false)
    }

    private suspend fun importFromZip(
        uri: Uri,
        preserveNoteIds: Boolean,
    ): Int {
        val extractRoot = File(context.cacheDir, "remember_zip_import_${UUID.randomUUID()}")
        if (extractRoot.exists()) extractRoot.deleteRecursively()
        extractRoot.mkdirs()
        return try {
            if (!materializeZipEntries(uri, extractRoot)) return 0
            val notesFile = File(extractRoot, ENTRY_NOTES)
            val settingsFile = File(extractRoot, ENTRY_SETTINGS)
            val notesText =
                if (notesFile.isFile) {
                    notesFile.inputStream().use { inputStream ->
                        readUtf8TextWithinLimit(inputStream, MAX_NOTES_JSON_BYTES)
                    } ?: return 0
                } else {
                    return 0
                }
            val settingsText =
                if (settingsFile.isFile) {
                    settingsFile.inputStream().use { inputStream ->
                        readUtf8TextWithinLimit(inputStream, MAX_METADATA_JSON_BYTES)
                    }
                } else {
                    null
                }
            val settingsJson = settingsText?.let { runCatching { JSONObject(it) }.getOrNull() }
            val importResult =
                importNotesAtomically(
                    text = notesText,
                    extractDir = extractRoot,
                    preserveNoteIds = preserveNoteIds,
                )
            importSettingsFromJson(settingsJson)
            importResult.noteCount
        } finally {
            extractRoot.deleteRecursively()
        }
    }

    private suspend fun importNotesAtomically(
        text: String,
        extractDir: File?,
        preserveNoteIds: Boolean,
    ): NoteImportResult {
        val importedNoteIds = mutableListOf<Long>()
        val importResult =
            try {
                repository.runImportTransaction {
                    importFromJsonText(
                        text = text,
                        extractDir = extractDir,
                        preserveNoteIds = preserveNoteIds,
                        suppressReminderSchedule = true,
                        importedNoteIds = importedNoteIds,
                    )
                }
            } catch (error: Exception) {
                if (!preserveNoteIds) {
                    importedNoteIds.forEach { noteId ->
                        File(context.filesDir, "remember_backup/$noteId").deleteRecursively()
                    }
                }
                throw error
            }
        runCatching {
            repository.reconcileImportedNotes(importResult.noteIds)
        }.onFailure { error ->
            DiagnosticLog.record(context, "Imported notes could not reconcile reminders", error)
        }
        return importResult
    }

    private suspend fun importFromJsonText(
        text: String,
        extractDir: File?,
        preserveNoteIds: Boolean,
        suppressReminderSchedule: Boolean = false,
        importedNoteIds: MutableList<Long>? = null,
    ): NoteImportResult {
        val root = JSONObject(text)
        val arr = root.optJSONArray("notes") ?: return NoteImportResult(0, emptyList())
        var added = 0
        val insertedNoteIds = mutableListOf<Long>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val noteFromJson = decodeNoteEntity(o)
            val items = decodeChecklistItems(o)
            val rawAttachments = decodeAttachmentMetas(o)
            val rawPicture = o.optStringOrNull("pictureUri")
            val picturePathForRelCopy = rawPicture?.takeIf { stored -> stored.startsWith(REL_PREFIX) }
            val nonRelAttachments = rawAttachments.filter { !it.uri.startsWith(REL_PREFIX) }
            val noteForInsert =
                when {
                    preserveNoteIds && noteFromJson.id > 0 ->
                        noteFromJson.copy(pictureUri = if (picturePathForRelCopy != null) null else rawPicture)
                    else ->
                        noteFromJson.copy(
                            id = 0L,
                            pictureUri =
                                if (picturePathForRelCopy != null) {
                                    null
                                } else {
                                    rawPicture?.takeUnless { picture -> picture.startsWith(REL_PREFIX) }
                                },
                        )
                }
            val insertedNoteId =
                repository.importNoteWithChildren(
                    note = noteForInsert,
                    items = items,
                    attachments = nonRelAttachments,
                    suppressReminderSchedule = suppressReminderSchedule,
                )
            insertedNoteIds += insertedNoteId
            importedNoteIds?.add(insertedNoteId)
            if (extractDir != null) {
                if (picturePathForRelCopy != null) {
                    val relativePicture = picturePathForRelCopy.removePrefix(REL_PREFIX)
                    copyRelToAppFiles(relativePicture, extractDir, insertedNoteId, "picture")?.let { uriString ->
                        repository.updatePictureUri(insertedNoteId, uriString)
                    }
                }
                rawAttachments.filter { it.uri.startsWith(REL_PREFIX) }.forEach { attachmentRow ->
                    val relativeAttachment = attachmentRow.uri.removePrefix(REL_PREFIX)
                    copyRelToAppFiles(
                        relativeAttachment,
                        extractDir,
                        insertedNoteId,
                        attachmentRow.displayName,
                    )?.let { uriString ->
                        repository.addAttachment(
                            insertedNoteId,
                            uriString,
                            attachmentRow.displayName,
                            attachmentRow.mimeType,
                        )
                    }
                }
            }
            added++
        }
        importTagColors(root.optJSONObject("tagColors"))
        return NoteImportResult(
            noteCount = added,
            noteIds = insertedNoteIds,
        )
    }

    private suspend fun importTagColors(tagColorsJson: JSONObject?) {
        if (tagColorsJson == null) return
        val tagRepository = repository.tagRepository ?: return
        tagColorsJson.keys().forEach { tagName ->
            val colorHex = tagColorsJson.optString(tagName, "")
            tagRepository.setTagColor(tagName, colorHex)
        }
    }

    private fun decodeNoteEntity(o: JSONObject): NoteEntity {
        val kind = NoteKind.valueOf(o.optString("kind", "NOTE"))
        return NoteEntity(
            id = o.optLong("id", 0L).takeIf { it > 0 } ?: 0L,
            kind = kind,
            title = o.optString("title", ""),
            body = o.optString("body", ""),
            colorIndex = o.optInt("colorIndex", 0),
            starred = o.optBoolean("starred", false),
            trashed = o.optBoolean("trashed", false),
            archived = o.optBoolean("archived", false),
            trashedAt = o.optLongOrNull("trashedAt"),
            completedAt = o.optLongOrNull("completedAt"),
            pinnedAt = o.optLongOrNull("pinnedAt"),
            createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
            reminderAt = o.optLongOrNull("reminderAt"),
            importance =
                runCatching { Importance.valueOf(o.optString("importance", Importance.DEFAULT.name)) }
                    .getOrDefault(Importance.DEFAULT),
            visibility =
                runCatching { Visibility.valueOf(o.optString("visibility", Visibility.DEFAULT.name)) }
                    .getOrDefault(Visibility.DEFAULT),
            pictureUri = o.optStringOrNull("pictureUri"),
            pictureHeroFraming = o.optStringOrNull("pictureHeroFraming"),
            locked = o.optBoolean("locked", false),
            iconKey = o.optStringOrNull("iconKey"),
            actions = o.optJSONArray("actions")?.let { decodeActions(it) } ?: emptyList(),
            tags = o.optJSONArray("tags")?.let { decodeStringArray(it) } ?: emptyList(),
            recurrence = o.optStringOrNull("recurrence")?.let { RecurrenceRule.fromJson(it) },
            reminders = o.optJSONArray("reminders")?.let { decodeReminders(it) } ?: emptyList(),
        )
    }

    private fun decodeReminders(a: JSONArray): List<NoteReminder> {
        val out = ArrayList<NoteReminder>(a.length())
        for (i in 0 until a.length()) {
            val reminderJson = a.optJSONObject(i) ?: continue
            decodeReminderFromBackup(reminderJson)?.let { reminder ->
                out.add(reminder)
            }
        }
        return out.limitedToReminderSlots()
    }

    private fun decodeChecklistItems(o: JSONObject): List<ChecklistItemEntity> {
        val arr = o.optJSONArray("items") ?: return emptyList()
        return List(arr.length()) { idx ->
            val jo = arr.getJSONObject(idx)
            // Legacy archives only have `position`; new archives use `sortOrder` (Double) plus
            // optional `id`, `parentId`, `depth`. Fall back gracefully so old backups keep working.
            val legacyPosition = jo.optInt("position", idx)
            val sortOrder =
                if (jo.has("sortOrder")) {
                    jo.optDouble("sortOrder", legacyPosition.toDouble())
                } else {
                    legacyPosition.toDouble()
                }
            val parentId: Long? =
                if (jo.has("parentId") && !jo.isNull("parentId")) {
                    jo.optLong("parentId", 0L).takeIf { it != 0L }
                } else {
                    null
                }
            ChecklistItemEntity(
                id = jo.optLong("id", 0L),
                noteId = 0L,
                text = jo.optString("text", ""),
                details = jo.optString("details", ""),
                checked = jo.optBoolean("checked", false),
                sortOrder = sortOrder,
                parentId = parentId,
                depth = jo.optInt("depth", if (parentId != null) 1 else 0).coerceIn(0, 1),
            )
        }.sortedBy { it.sortOrder }
    }

    private fun decodeAttachmentMetas(o: JSONObject): List<NoteAttachmentEntity> {
        val arr = o.optJSONArray("attachments") ?: return emptyList()
        return List(arr.length()) { idx ->
            val a = arr.getJSONObject(idx)
            NoteAttachmentEntity(
                id = 0L,
                noteId = 0L,
                uri = a.optString("uri", ""),
                displayName = a.optString("displayName", ""),
                mimeType = a.optStringOrNull("mimeType"),
            )
        }
    }

    private fun copyRelToAppFiles(
        relative: String,
        extractDir: File,
        destNoteId: Long,
        label: String,
    ): String? {
        val source =
            canonicalFileInsideBaseDirectoryOrNull(
                candidate = File(extractDir, relative),
                baseDirectory = extractDir,
            ) ?: return null
        if (!source.isFile) return null
        val destDir = File(context.filesDir, "remember_backup/$destNoteId").apply { mkdirs() }
        val safeName = label.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
        val dest = File(destDir, "${System.currentTimeMillis()}_$safeName")
        runCatching {
            source.copyTo(dest, overwrite = true)
            return FileProvider.getUriForFile(context, fileProviderAuthority, dest).toString()
        }
        return null
    }

    private fun isZipUri(uri: Uri): Boolean =
        context.contentResolver.openInputStream(uri)?.use { input ->
            val header = ByteArray(4)
            val read = input.read(header, 0, 4)
            read == 4 && header[0] == 0x50.toByte() && header[1] == 0x4b.toByte()
        } ?: false

    private fun decodeActions(a: JSONArray): List<NoteAction> {
        val out = ArrayList<NoteAction>(a.length())
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val type = runCatching { ActionType.valueOf(o.optString("type", "")) }.getOrNull() ?: continue
            out.add(
                NoteAction(
                    type = type,
                    title = o.optString("title", ""),
                    details = o.optString("details", ""),
                    extra = o.optStringOrNull("extra"),
                    iconData = o.optStringOrNull("iconData"),
                ),
            )
        }
        return out
    }

    private fun decodeStringArray(a: JSONArray): List<String> = List(a.length()) { a.optString(it, "") }.filter { it.isNotBlank() }

    private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null

    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

    companion object {
        const val SCHEMA_VERSION = 5
        const val LEGACY_SCHEMA_VERSION = 1
        const val ENTRY_MANIFEST = "backup_manifest.json"
        const val ENTRY_NOTES = "notes.json"
        const val ENTRY_SETTINGS = "settings.json"
        const val REL_PREFIX = "REL:"
        private const val MAX_NOTES_JSON_BYTES = 32L * 1024L * 1024L
        private const val MAX_METADATA_JSON_BYTES = 1024L * 1024L
    }
}

@Suppress("ktlint:standard:function-expression-body")
internal fun notesForBackup(
    activeNotes: List<NoteWithItems>,
    archivedNotes: List<NoteWithItems>,
    trashedNotes: List<NoteWithItems>,
): List<NoteWithItems> {
    return buildList(activeNotes.size + archivedNotes.size + trashedNotes.size) {
        addAll(activeNotes)
        addAll(archivedNotes)
        addAll(trashedNotes)
    }
}

@Suppress("ktlint:standard:function-expression-body")
internal fun encodeReminderForBackup(reminder: NoteReminder): JSONObject {
    return JSONObject().apply {
        put("reminderAt", reminder.reminderAt)
        RecurrenceRule.toJson(reminder.recurrence)?.let { recurrenceJson ->
            put("recurrence", recurrenceJson)
        }
        reminder.originalReminderAt?.let { originalReminderAt ->
            put("originalReminderAt", originalReminderAt)
        }
    }
}

internal fun decodeReminderFromBackup(reminderJson: JSONObject): NoteReminder? {
    val reminderAt = reminderJson.optLong("reminderAt", 0L)
    if (reminderAt <= 0L) return null
    val recurrence =
        if (reminderJson.has("recurrence") && !reminderJson.isNull("recurrence")) {
            RecurrenceRule.fromJson(reminderJson.getString("recurrence"))
        } else {
            null
        }
    val originalReminderAt =
        if (reminderJson.has("originalReminderAt") && !reminderJson.isNull("originalReminderAt")) {
            reminderJson.getLong("originalReminderAt")
        } else {
            null
        }
    return NoteReminder(
        reminderAt = reminderAt,
        recurrence = recurrence,
        originalReminderAt = originalReminderAt,
    )
}
