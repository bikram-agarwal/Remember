package dev.bikram.remember.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import dev.bikram.remember.backup.SettingsBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupIo(
    private val context: Context,
    private val repository: NoteRepository,
    private val themePrefs: ThemePrefs,
    private val lockPrefs: LockPrefs,
    private val interactionPrefs: InteractionPrefs,
    private val backupPrefs: BackupPrefs,
) {

    private val fileProviderAuthority: String
        get() = "${context.packageName}.fileprovider"

    private fun backupStamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

    fun suggestedBackupFileName(): String = "remember_backup_${backupStamp()}.zip"

    private suspend fun buildSettingsJson(): JSONObject =
        SettingsBackup.exportJson(themePrefs, lockPrefs, interactionPrefs, backupPrefs)

    private suspend fun importSettingsFromJson(settingsJson: JSONObject?) {
        SettingsBackup.importJson(settingsJson, themePrefs, lockPrefs, interactionPrefs, backupPrefs)
    }

    private suspend fun buildNotesRootObject(): JSONObject {
        val active = repository.observeActive().first()
        val trashed = repository.observeTrashed().first()
        val all = active + trashed
        return JSONObject().apply {
            put("version", SCHEMA_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("notes", JSONArray().apply {
                all.forEach { put(encode(it)) }
            })
        }
    }

    private fun encode(n: NoteWithItems): JSONObject {
        val note = n.note
        return JSONObject().apply {
            put("id", note.id)
            put("kind", note.kind.name)
            put("title", note.title)
            put("body", note.body)
            put("colorIndex", note.colorIndex)
            put("pinned", note.pinned)
            put("trashed", note.trashed)
            put("createdAt", note.createdAt)
            put("updatedAt", note.updatedAt)
            note.reminderAt?.let { put("reminderAt", it) }
            put("importance", note.importance.name)
            put("visibility", note.visibility.name)
            note.pictureUri?.let { put("pictureUri", it) }
            put("locked", note.locked)
            note.iconKey?.let { put("iconKey", it) }
            put("actions", JSONArray().apply {
                note.actions.forEach { a ->
                    put(JSONObject().apply {
                        put("type", a.type.name)
                        put("title", a.title)
                        put("details", a.details)
                        a.extra?.let { put("extra", it) }
                    })
                }
            })
            put("tags", JSONArray().apply { note.tags.forEach { put(it) } })
            RecurrenceRule.toJson(note.recurrence)?.let { put("recurrence", it) }
            put("items", JSONArray().apply {
                n.items.forEach { it2 ->
                    put(JSONObject().apply {
                        put("text", it2.text)
                        put("checked", it2.checked)
                        put("position", it2.position)
                    })
                }
            })
            put("attachments", JSONArray().apply {
                n.attachments.forEach { a ->
                    put(JSONObject().apply {
                        put("uri", a.uri)
                        put("displayName", a.displayName)
                        a.mimeType?.let { put("mimeType", it) }
                    })
                }
            })
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
            if (includeMedia) {
                embedMediaInNotesJson(zip, notesClone)
            }
            zip.putNextEntry(ZipEntry(ENTRY_NOTES))
            zip.write(notesClone.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(ENTRY_SETTINGS))
            zip.write(settingsBytes)
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun embedMediaInNotesJson(zip: ZipOutputStream, notesRoot: JSONObject) {
        val arr = notesRoot.optJSONArray("notes") ?: return
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val noteId = o.optLong("id", 0L)
            if (noteId <= 0L) continue
            val pictureUri = o.optStringOrNull("pictureUri")
            if (!pictureUri.isNullOrBlank()) {
                val entryPath = "media/$noteId/picture${guessExtension(pictureUri, null)}"
                if (copyUriIntoZip(zip, entryPath, Uri.parse(pictureUri))) {
                    o.put("pictureUri", "$REL_PREFIX$entryPath")
                }
            }
            val atts = o.optJSONArray("attachments") ?: continue
            for (j in 0 until atts.length()) {
                val att = atts.getJSONObject(j)
                val uriStr = att.optString("uri", "")
                if (uriStr.isBlank()) continue
                val ext = guessExtension(uriStr, att.optStringOrNull("mimeType"))
                val entryPath = "media/$noteId/att_$j$ext"
                if (copyUriIntoZip(zip, entryPath, Uri.parse(uriStr))) {
                    att.put("uri", "$REL_PREFIX$entryPath")
                }
            }
        }
    }

    private fun guessExtension(uriString: String, mimeType: String?): String {
        val filePart = uriString.substringAfterLast('/', uriString).substringBeforeLast('?', uriString)
        val dot = filePart.lastIndexOf('.')
        if (dot >= 0 && dot < filePart.length - 1) {
            return filePart.substring(dot)
        }
        val ext = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return if (!ext.isNullOrBlank()) ".$ext" else ".bin"
    }

    private fun copyUriIntoZip(zip: ZipOutputStream, entryName: String, source: Uri): Boolean =
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
    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val includeMedia = backupPrefs.snapshot().includeMediaInBackup
        val notesRoot = buildNotesRootObject()
        val all = repository.observeActive().first() + repository.observeTrashed().first()
        val bytes = writeZipArchive(notesRoot, includeMedia)
        val outputStream = context.contentResolver.openOutputStream(uri) ?: return@withContext -1
        outputStream.use { stream -> stream.write(bytes) }
        all.size
    }

    suspend fun exportToTreeFolder(treeUriString: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!treeUriString.startsWith("content://")) error("Invalid export folder")
            val includeMedia = backupPrefs.snapshot().includeMediaInBackup
            val treeUri = Uri.parse(treeUriString)
            val notesRoot = buildNotesRootObject()
            val bytes = writeZipArchive(notesRoot, includeMedia)
            val stamp = backupStamp()
            val fileName = "remember_backup_$stamp.zip"
            val docTreeUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            val docUri = DocumentsContract.createDocument(
                context.contentResolver,
                docTreeUri,
                "application/zip",
                fileName,
            ) ?: error("Failed to create document in export folder")
            context.contentResolver.openOutputStream(docUri)?.use { stream ->
                stream.write(bytes)
            } ?: error("Failed to open output stream for export")
            fileName
        }
    }

    suspend fun restoreFullReplace(uri: Uri): Int = withContext(Dispatchers.IO) {
        repository.deleteAllNotes()
        importFrom(uri, preserveIdsForNotes = true)
    }

    suspend fun importFrom(uri: Uri, preserveIdsForNotes: Boolean = false): Int = withContext(Dispatchers.IO) {
        if (isZipUri(uri)) {
            importFromZip(uri, preserveIdsForNotes)
        } else {
            val text = context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
                ?: return@withContext 0
            importFromJsonText(text, extractDir = null, preserveIdsForNotes)
        }
    }

    private suspend fun importFromZip(uri: Uri, preserveNoteIds: Boolean): Int {
        val extractRoot = File(context.cacheDir, "remember_zip_import_${UUID.randomUUID()}")
        if (extractRoot.exists()) extractRoot.deleteRecursively()
        extractRoot.mkdirs()
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (entry.isDirectory) {
                            entry = zipIn.nextEntry
                            continue
                        }
                        val outFile = File(extractRoot, name).canonicalFile
                        val extractBase = extractRoot.canonicalFile
                        if (!outFile.path.startsWith(extractBase.path)) {
                            entry = zipIn.nextEntry
                            continue
                        }
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos -> zipIn.copyTo(fos) }
                        entry = zipIn.nextEntry
                    }
                }
            }
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
            val noteForInsert = when {
                preserveNoteIds && noteFromJson.id > 0 ->
                    noteFromJson.copy(pictureUri = if (picturePathForRelCopy != null) null else rawPicture)
                else ->
                    noteFromJson.copy(
                        id = 0L,
                        pictureUri = if (picturePathForRelCopy != null) {
                            null
                        } else {
                            rawPicture?.takeUnless { picture -> picture.startsWith(REL_PREFIX) }
                        },
                    )
            }
            val insertedNoteId = repository.importNoteWithChildren(
                note = noteForInsert,
                items = items,
                attachments = nonRelAttachments,
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
        return added
    }

    private fun decodeNoteEntity(o: JSONObject): NoteEntity {
        val kind = NoteKind.valueOf(o.optString("kind", "NOTE"))
        return NoteEntity(
            id = o.optLong("id", 0L).takeIf { it > 0 } ?: 0L,
            kind = kind,
            title = o.optString("title", ""),
            body = o.optString("body", ""),
            colorIndex = o.optInt("colorIndex", 0),
            pinned = o.optBoolean("pinned", false),
            trashed = o.optBoolean("trashed", false),
            createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
            reminderAt = o.optLongOrNull("reminderAt"),
            importance = runCatching { Importance.valueOf(o.optString("importance", Importance.DEFAULT.name)) }
                .getOrDefault(Importance.DEFAULT),
            visibility = runCatching { Visibility.valueOf(o.optString("visibility", Visibility.PRIVATE.name)) }
                .getOrDefault(Visibility.PRIVATE),
            pictureUri = o.optStringOrNull("pictureUri"),
            locked = o.optBoolean("locked", false),
            iconKey = o.optStringOrNull("iconKey"),
            actions = o.optJSONArray("actions")?.let { decodeActions(it) } ?: emptyList(),
            tags = o.optJSONArray("tags")?.let { decodeStringArray(it) } ?: emptyList(),
            recurrence = o.optStringOrNull("recurrence")?.let { RecurrenceRule.fromJson(it) },
        )
    }

    private fun decodeChecklistItems(o: JSONObject): List<ChecklistItemEntity> {
        val arr = o.optJSONArray("items") ?: return emptyList()
        return List(arr.length()) { idx ->
            val jo = arr.getJSONObject(idx)
            ChecklistItemEntity(
                id = 0L,
                noteId = 0L,
                text = jo.optString("text", ""),
                checked = jo.optBoolean("checked", false),
                position = jo.optInt("position", idx),
            )
        }.sortedBy { it.position }
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
        val source = File(extractDir, relative).canonicalFile
        val base = extractDir.canonicalFile
        if (!source.path.startsWith(base.path)) return null
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

    private fun decodeActions(a: JSONArray): List<NoteAction> = List(a.length()) { i ->
        val o = a.getJSONObject(i)
        NoteAction(
            type = ActionType.valueOf(o.getString("type")),
            title = o.optString("title", ""),
            details = o.optString("details", ""),
            extra = o.optStringOrNull("extra"),
        )
    }

    private fun decodeStringArray(a: JSONArray): List<String> =
        List(a.length()) { a.optString(it, "") }.filter { it.isNotBlank() }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) getLong(key) else null

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    companion object {
        const val SCHEMA_VERSION = 2
        const val LEGACY_SCHEMA_VERSION = 1
        const val ENTRY_NOTES = "notes.json"
        const val ENTRY_SETTINGS = "settings.json"
        const val REL_PREFIX = "REL:"
    }
}
