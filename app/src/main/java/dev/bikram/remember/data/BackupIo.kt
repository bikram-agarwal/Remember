package dev.bikram.remember.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dev.bikram.remember.backup.SettingsBackup
import dev.bikram.remember.diagnostics.DiagnosticLog
import dev.bikram.remember.domain.backupFileTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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

    private data class MediaExportStats(
        var mediaReferenceCount: Int = 0,
        var mediaEmbeddedCount: Int = 0,
        var mediaLinkedCount: Int = 0,
        var mediaFailedCount: Int = 0,
    )

    fun suggestedBackupFileName(): String = "remember_backup_${backupFileTimestamp()}.zip"

    private suspend fun buildSettingsJson(): JSONObject = SettingsBackup.exportJson(themePrefs, viewOptionsPrefs, lockPrefs, interactionPrefs, backupPrefs, quickCapturePrefs, reminderPrefs, updatePrefs)

    private suspend fun importSettingsFromJson(settingsJson: JSONObject?) {
        SettingsBackup.importJson(settingsJson, themePrefs, viewOptionsPrefs, lockPrefs, interactionPrefs, backupPrefs, quickCapturePrefs, reminderPrefs, updatePrefs)
    }

    private data class NotesSnapshot(
        val root: JSONObject,
        val noteCount: Int,
    )

    private suspend fun snapshotNotes(): NotesSnapshot {
        val all = repository.observeActive().first() + repository.observeTrashed().first()
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
                    note.reminders.forEach { r ->
                        put(
                            JSONObject().apply {
                                put("reminderAt", r.reminderAt)
                                RecurrenceRule.toJson(r.recurrence)?.let { put("recurrence", it) }
                            },
                        )
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
        notesJson: JSONObject,
        includeMedia: Boolean,
    ): ByteArray {
        val notesClone = JSONObject(notesJson.toString())
        val settingsBytes = buildSettingsJson().toString(2).toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val mediaStats =
                if (includeMedia) {
                    embedMediaInNotesJson(zip, notesClone)
                } else {
                    countLinkedMediaInNotesJson(notesClone)
                }
            if (!includeMedia) {
                mediaStats.mediaLinkedCount = mediaStats.mediaReferenceCount
            }
            val manifestBytes =
                buildBackupManifestJson(
                    includeMediaRequested = includeMedia,
                    mediaStats = mediaStats,
                ).toString(2).toByteArray(Charsets.UTF_8)
            zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
            zip.write(manifestBytes)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(ENTRY_NOTES))
            zip.write(notesClone.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(ENTRY_SETTINGS))
            zip.write(settingsBytes)
            zip.closeEntry()
        }
        return out.toByteArray()
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
                val bytes = writeZipArchive(snapshot.root, includeMedia)
                val outputStream =
                    context.contentResolver.openOutputStream(uri)
                        ?: error("openOutputStream returned null")
                outputStream.use { stream -> stream.write(bytes) }
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
                val bytes = writeZipArchive(snapshot.root, includeMedia)
                val fileName = "remember_backup_${backupFileTimestamp()}.zip"
                destinations.forEach { destinationUriString ->
                    if (!destinationUriString.startsWith("content://")) error("Invalid export folder")
                    val destinationUri = destinationUriString.toUri()
                    if (DocumentsContract.isTreeUri(destinationUri)) {
                        val docTreeUri =
                            DocumentsContract.buildDocumentUriUsingTree(
                                destinationUri,
                                DocumentsContract.getTreeDocumentId(destinationUri),
                            )
                        val docUri =
                            DocumentsContract.createDocument(
                                context.contentResolver,
                                docTreeUri,
                                "application/zip",
                                fileName,
                            ) ?: error("Failed to create document in export folder")
                        context.contentResolver.openOutputStream(docUri)?.use { stream ->
                            stream.write(bytes)
                        } ?: error("Failed to open output stream for export")
                    } else {
                        context.contentResolver.openOutputStream(destinationUri, "wt")?.use { stream ->
                            stream.write(bytes)
                        } ?: error("Failed to open output stream for export")
                    }
                }
                List(destinations.size) { fileName }
            }
        }

    suspend fun restoreFullReplace(uri: Uri): Int =
        withContext(Dispatchers.IO) {
            val payload = readRestorePayload(uri) ?: return@withContext 0
            try {
                val count =
                    repository.restoreNotesFullReplace {
                        importFromJsonText(
                            text = payload.notesText,
                            extractDir = payload.extractRoot,
                            preserveNoteIds = true,
                            suppressReminderSchedule = true,
                        )
                    }
                importSettingsFromJson(payload.settingsJson)
                count
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
                    importFromJsonText(text, extractDir = null, preserveIdsForNotes)
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
                runCatching { notesFile.readText() }.getOrNull() ?: run {
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
                    runCatching { JSONObject(settingsFile.readText(Charsets.UTF_8)) }.getOrNull()
                } else {
                    null
                }
            val manifestFile = File(extractRoot, ENTRY_MANIFEST)
            val manifestJson =
                if (manifestFile.isFile) {
                    runCatching { JSONObject(manifestFile.readText(Charsets.UTF_8)) }.getOrNull()
                } else {
                    null
                }
            return RestorePayload(notesText, settingsJson, manifestJson, extractRoot)
        }
        val text =
            context.contentResolver.openInputStream(uri)?.use { input -> input.reader().readText() }
                ?: return null
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (!root.has("notes")) return null
        return RestorePayload(text, null, null, null)
    }

    private fun materializeZipEntries(
        uri: Uri,
        extractRoot: File,
    ): Boolean =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val outFile =
                                canonicalFileInsideBaseDirectoryOrNull(
                                    candidate = File(extractRoot, entry.name),
                                    baseDirectory = extractRoot,
                                )
                            if (outFile != null) {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { fileOutput -> zipIn.copyTo(fileOutput) }
                            }
                        }
                        entry = zipIn.nextEntry
                    }
                }
                true
            } ?: false
        }.getOrDefault(false)

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
            val notesText = if (notesFile.isFile) notesFile.readText() else return 0
            val settingsText = if (settingsFile.isFile) settingsFile.readText(Charsets.UTF_8) else null
            val settingsJson = settingsText?.let { runCatching { JSONObject(it) }.getOrNull() }
            importSettingsFromJson(settingsJson)
            importFromJsonText(notesText, extractRoot, preserveNoteIds)
        } finally {
            extractRoot.deleteRecursively()
        }
    }

    private suspend fun importFromJsonText(
        text: String,
        extractDir: File?,
        preserveNoteIds: Boolean,
        suppressReminderSchedule: Boolean = false,
    ): Int {
        val root = JSONObject(text)
        val arr = root.optJSONArray("notes") ?: return 0
        var added = 0
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
        return added
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
            val o = a.optJSONObject(i) ?: continue
            val reminderAt = o.optLong("reminderAt", 0L)
            if (reminderAt <= 0L) continue
            val recurrence = o.optStringOrNull("recurrence")?.let { RecurrenceRule.fromJson(it) }
            out.add(NoteReminder(reminderAt, recurrence))
        }
        return out
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
        const val SCHEMA_VERSION = 4
        const val LEGACY_SCHEMA_VERSION = 1
        const val ENTRY_MANIFEST = "backup_manifest.json"
        const val ENTRY_NOTES = "notes.json"
        const val ENTRY_SETTINGS = "settings.json"
        const val REL_PREFIX = "REL:"
    }
}
