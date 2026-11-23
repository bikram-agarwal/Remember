package dev.bikram.remember.ui.edit

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteOptions
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.PersistableChecklistItem
import dev.bikram.remember.domain.checklist.EditableItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal const val MAX_NOTE_MARKDOWN_BYTES = 512L * 1024L
internal const val MAX_NOTE_MARKDOWN_KB = 512
private const val MAX_EXPORT_FILE_NAME_BASE_LENGTH = 80
private const val MARKDOWN_EXTENSION = ".md"
private const val MARKDOWN_LONG_EXTENSION = ".markdown"
private const val PLAIN_TEXT_EXTENSION = ".txt"

internal sealed interface NoteMarkdownImportResult {
    data class Success(
        val markdown: String,
        val suggestedTitle: String? = null,
    ) : NoteMarkdownImportResult

    data object TooLarge : NoteMarkdownImportResult

    data object Empty : NoteMarkdownImportResult

    data object Failed : NoteMarkdownImportResult
}

internal fun readMarkdownFromInputStream(
    inputStream: InputStream,
    maximumBytes: Long = MAX_NOTE_MARKDOWN_BYTES,
): NoteMarkdownImportResult =
    runCatching {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = ByteArrayOutputStream()
        var totalBytes = 0L
        while (true) {
            val bytesRead = inputStream.read(buffer)
            if (bytesRead <= 0) break
            totalBytes += bytesRead
            if (totalBytes > maximumBytes) {
                return@runCatching NoteMarkdownImportResult.TooLarge
            }
            output.write(buffer, 0, bytesRead)
        }
        val normalized =
            output
                .toString(Charsets.UTF_8.name())
                .replace("\r\n", "\n")
                .replace('\r', '\n')
        if (normalized.isBlank()) {
            NoteMarkdownImportResult.Empty
        } else {
            NoteMarkdownImportResult.Success(normalized)
        }
    }.getOrElse {
        NoteMarkdownImportResult.Failed
    }

internal suspend fun importMarkdownFromUri(
    context: Context,
    uri: Uri,
    maximumBytes: Long = MAX_NOTE_MARKDOWN_BYTES,
): NoteMarkdownImportResult =
    withContext(Dispatchers.IO) {
        runCatching {
            val result =
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    readMarkdownFromInputStream(stream, maximumBytes)
                } ?: NoteMarkdownImportResult.Failed
            if (result is NoteMarkdownImportResult.Success) {
                val title = resolveMarkdownImportTitle(context, uri)
                result.copy(suggestedTitle = title)
            } else {
                result
            }
        }.getOrElse {
            NoteMarkdownImportResult.Failed
        }
    }

internal fun markdownTitleFromFileName(fileName: String): String? {
    val baseName = fileName.substringAfterLast('/').trim()
    val withoutExtension =
        when {
            baseName.endsWith(MARKDOWN_LONG_EXTENSION, ignoreCase = true) ->
                baseName.dropLast(MARKDOWN_LONG_EXTENSION.length)
            baseName.endsWith(MARKDOWN_EXTENSION, ignoreCase = true) ->
                baseName.dropLast(MARKDOWN_EXTENSION.length)
            baseName.endsWith(PLAIN_TEXT_EXTENSION, ignoreCase = true) ->
                baseName.dropLast(PLAIN_TEXT_EXTENSION.length)
            else -> baseName
        }
    return withoutExtension
        .replace('\r', ' ')
        .replace('\n', ' ')
        .trim()
        .takeIf { it.isNotBlank() }
}

internal fun resolveMarkdownDisplayName(
    context: Context,
    uri: Uri,
): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else {
                null
            }
        }
    }.getOrNull()

internal fun isMarkdownFileUri(
    context: Context,
    uri: Uri,
): Boolean {
    val displayName = resolveMarkdownDisplayName(context, uri) ?: uri.lastPathSegment.orEmpty()
    val baseName = displayName.substringAfterLast('/')
    if (
        baseName.endsWith(MARKDOWN_EXTENSION, ignoreCase = true) ||
        baseName.endsWith(MARKDOWN_LONG_EXTENSION, ignoreCase = true)
    ) {
        return true
    }
    val mimeType = context.contentResolver.getType(uri)
    return mimeType.equals("text/markdown", ignoreCase = true) ||
        mimeType.equals("text/x-markdown", ignoreCase = true)
}

internal fun isMarkdownOrPlainTextUri(
    context: Context,
    uri: Uri,
): Boolean {
    if (isMarkdownFileUri(context, uri)) return true
    val mimeType = context.contentResolver.getType(uri)
    return mimeType.equals("text/plain", ignoreCase = true)
}

internal fun isJsonFileUri(
    context: Context,
    uri: Uri,
): Boolean {
    val fileName = (resolveMarkdownDisplayName(context, uri) ?: uri.lastPathSegment.orEmpty()).substringAfterLast('/')
    if (fileName.endsWith(".json", ignoreCase = true)) return true
    val mimeType = context.contentResolver.getType(uri)
    return mimeType.equals("application/json", ignoreCase = true) ||
        mimeType.equals("text/json", ignoreCase = true)
}

internal fun isZipFileUri(
    context: Context,
    uri: Uri,
): Boolean {
    val fileName = (resolveMarkdownDisplayName(context, uri) ?: uri.lastPathSegment.orEmpty()).substringAfterLast('/')
    if (fileName.endsWith(".zip", ignoreCase = true)) return true
    val mimeType = context.contentResolver.getType(uri)
    return mimeType.equals("application/zip", ignoreCase = true) ||
        mimeType.equals("application/x-zip-compressed", ignoreCase = true)
}

internal suspend fun createImportedMarkdownNote(
    noteRepository: NoteRepository,
    title: String,
    markdown: String,
): Long =
    if (isStrictChecklistMarkdown(markdown)) {
        noteRepository.createListWithItems(
            title = title,
            colorIndex = 0,
            items = markdownToPersistableChecklistItems(markdown),
            options = NoteOptions(),
        )
    } else {
        noteRepository.createNote(
            title = title,
            body = markdown,
            colorIndex = 0,
            options = NoteOptions(),
        )
    }

private fun resolveMarkdownImportTitle(
    context: Context,
    uri: Uri,
): String? {
    val displayName = resolveMarkdownDisplayName(context, uri)
    val rawName = displayName?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: return null
    return markdownTitleFromFileName(rawName)
}

internal fun buildMarkdownExportFileName(
    title: String,
    fallbackTitle: String,
): String {
    val rawTitle = title.trim().ifBlank { fallbackTitle.trim() }.ifBlank { "note" }
    val withoutExtension =
        if (rawTitle.endsWith(MARKDOWN_EXTENSION, ignoreCase = true)) {
            rawTitle.dropLast(MARKDOWN_EXTENSION.length)
        } else {
            rawTitle
        }
    val sanitized =
        withoutExtension
            .replace(Regex("[/\\\\:*?\"<>|\\r\\n\\t]"), "_")
            .trim()
            .take(MAX_EXPORT_FILE_NAME_BASE_LENGTH)
            .ifBlank { "note" }
    return "$sanitized$MARKDOWN_EXTENSION"
}

internal suspend fun exportMarkdownToUri(
    context: Context,
    uri: Uri,
    markdown: String,
): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(markdown.toByteArray(Charsets.UTF_8))
                output.flush()
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

private val CHECKLIST_LINE_REGEX = Regex("""^(\s*)(?:[-*+]|\d+[.)])\s+\[([ xX])]\s*(.*)$""")
private val BULLET_OR_NUMBERED_LINE_REGEX = Regex("""^(\s*)(?:[-*+]|\d+[.)])\s+(.*)$""")
private val RICH_BLOCK_DISQUALIFIER_REGEX =
    Regex("""^\s*(?:#{1,6}\s+|```|~~~|>\s?|(?:-{3,}|\*{3,}|_{3,})\s*$|\|.*\|)""")

internal fun isStrictChecklistMarkdown(markdown: String): Boolean =
    analyzeChecklistConvertibility(markdown = markdown, allowPlainLists = false)

internal fun canConvertMarkdownNoteToChecklist(markdown: String): Boolean =
    analyzeChecklistConvertibility(markdown = markdown, allowPlainLists = true)

private fun analyzeChecklistConvertibility(
    markdown: String,
    allowPlainLists: Boolean,
): Boolean {
    if (markdown.isBlank()) return false
    var itemCount = 0
    var hasTopLevelItem = false
    var currentDepth = 0

    val normalized = markdown.replace("\r\n", "\n").replace('\r', '\n')
    for (rawLine in normalized.lines()) {
        if (rawLine.isBlank()) continue
        if (RICH_BLOCK_DISQUALIFIER_REGEX.containsMatchIn(rawLine)) return false

        val checklistMatch = CHECKLIST_LINE_REGEX.matchEntire(rawLine)
        val plainListMatch =
            if (allowPlainLists && checklistMatch == null) {
                BULLET_OR_NUMBERED_LINE_REGEX.matchEntire(rawLine)
            } else {
                null
            }

        if (checklistMatch != null || plainListMatch != null) {
            val leadingWhitespace =
                checklistMatch?.groupValues?.get(1) ?: plainListMatch!!.groupValues[1]
            val itemText =
                (checklistMatch?.groupValues?.get(3) ?: plainListMatch!!.groupValues[2]).trim()
            if (itemText.isEmpty()) return false

            val indentSpaces =
                leadingWhitespace.sumOf { ch -> if (ch == '\t') 2 else 1 }
            if (indentSpaces >= 4 && currentDepth == 1) {
                return false
            }
            val depth = if (indentSpaces >= 2 && hasTopLevelItem) 1 else 0
            if (depth == 0) {
                hasTopLevelItem = true
            }
            currentDepth = depth
            itemCount++
        } else if (
            (rawLine.startsWith("  ") || rawLine.startsWith("\t")) &&
            itemCount > 0 &&
            BULLET_OR_NUMBERED_LINE_REGEX.matchEntire(rawLine) == null
        ) {
            // Indented detail continuation line under the preceding item.
        } else {
            return false
        }
    }
    return itemCount > 0
}

internal fun markdownToPersistableChecklistItems(markdown: String): List<PersistableChecklistItem> =
    markdownToChecklistItems(markdown).map { item ->
        PersistableChecklistItem(
            localKey = item.localId,
            text = item.text,
            details = item.details,
            checked = item.checked,
            sortOrder = item.sortOrder,
            parentLocalKey = item.parentLocalId,
            depth = item.depth.coerceIn(0, 1),
        )
    }

internal fun checklistToMarkdown(items: List<EditableItem>): String {
    val lines = mutableListOf<String>()
    items.forEach { item ->
        if (item.text.isBlank() && item.details.isBlank()) return@forEach
        val indent = "  ".repeat(item.depth.coerceIn(0, 1))
        val marker = if (item.checked) "- [x] " else "- [ ] "
        lines += "$indent$marker${item.text.trim()}"
        if (item.details.isNotBlank()) {
            val detailIndent = "  ".repeat(item.depth.coerceIn(0, 1) + 1)
            item.details.lines().forEach { detailLine ->
                if (detailLine.isNotBlank()) {
                    lines += "$detailIndent${detailLine.trim()}"
                }
            }
        }
    }
    return lines.joinToString("\n")
}

internal fun markdownToChecklistItems(markdown: String): List<EditableItem> {
    val result = mutableListOf<EditableItem>()
    var nextLocalId = -1L
    var currentTopLevelId: Long? = null

    markdown.lines().forEach { rawLine ->
        if (rawLine.isBlank()) return@forEach
        val checklistMatch = CHECKLIST_LINE_REGEX.matchEntire(rawLine)
        val listMatch = if (checklistMatch == null) BULLET_OR_NUMBERED_LINE_REGEX.matchEntire(rawLine) else null

        if (checklistMatch != null || listMatch != null) {
            val leadingWhitespace =
                checklistMatch?.groupValues?.get(1) ?: listMatch!!.groupValues[1]
            val checked =
                checklistMatch?.groupValues?.get(2)?.equals("x", ignoreCase = true) == true
            val itemText =
                (checklistMatch?.groupValues?.get(3) ?: listMatch!!.groupValues[2]).trim()
            if (itemText.isEmpty()) return@forEach

            val isIndented = leadingWhitespace.contains('\t') || leadingWhitespace.length >= 2
            val depth = if (isIndented && currentTopLevelId != null) 1 else 0
            val parentId = if (depth == 1) currentTopLevelId else null
            val localId = nextLocalId--
            if (depth == 0) {
                currentTopLevelId = localId
            }
            result +=
                EditableItem(
                    localId = localId,
                    text = itemText,
                    details = "",
                    checked = checked,
                    sortOrder = (result.size + 1).toDouble(),
                    parentLocalId = parentId,
                    depth = depth,
                )
        } else if (
            (rawLine.startsWith("  ") || rawLine.startsWith("\t")) &&
            result.isNotEmpty()
        ) {
            val previous = result.removeAt(result.lastIndex)
            val detailLine = rawLine.trim()
            val updatedDetails =
                if (previous.details.isBlank()) detailLine else "${previous.details}\n$detailLine"
            result += previous.copy(details = updatedDetails)
        } else {
            val trimmed = rawLine.trim()
            val localId = nextLocalId--
            currentTopLevelId = localId
            result +=
                EditableItem(
                    localId = localId,
                    text = trimmed,
                    details = "",
                    checked = false,
                    sortOrder = (result.size + 1).toDouble(),
                    parentLocalId = null,
                    depth = 0,
                )
        }
    }
    return result
}

internal fun buildBulkExportZipFileName(): String = "remember_notes_export.zip"

internal fun noteWithItemsToMarkdown(noteWithItems: NoteWithItems): String {
    val note = noteWithItems.note
    if (note.kind != NoteKind.LIST) return note.body
    val editableItems =
        noteWithItems.items
            .sortedBy { it.sortOrder }
            .map { item ->
                EditableItem(
                    localId = item.id,
                    text = item.text,
                    details = item.details,
                    checked = item.checked,
                    sortOrder = item.sortOrder,
                    parentLocalId = item.parentId,
                    depth = item.depth,
                )
            }
    return checklistToMarkdown(editableItems)
}

internal fun writeNotesMarkdownZip(
    outputStream: OutputStream,
    notes: List<NoteWithItems>,
    fallbackUntitledNote: String,
    fallbackUntitledList: String,
) {
    val usedEntryNames = HashSet<String>()
    ZipOutputStream(outputStream).use { zip ->
        notes.forEach { noteWithItems ->
            val note = noteWithItems.note
            val fallbackTitle =
                if (note.kind == NoteKind.LIST) fallbackUntitledList else fallbackUntitledNote
            val baseFileName = buildMarkdownExportFileName(note.title, fallbackTitle)
            val entryName = uniqueZipEntryName(baseFileName, usedEntryNames)
            val markdownBytes = noteWithItemsToMarkdown(noteWithItems).toByteArray(Charsets.UTF_8)
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(markdownBytes)
            zip.closeEntry()
        }
    }
}

private fun uniqueZipEntryName(
    baseFileName: String,
    usedEntryNames: MutableSet<String>,
): String {
    if (usedEntryNames.add(baseFileName.lowercase())) {
        return baseFileName
    }
    val stem = baseFileName.removeSuffix(MARKDOWN_EXTENSION)
    var suffixIndex = 2
    while (true) {
        val candidate = "$stem ($suffixIndex)$MARKDOWN_EXTENSION"
        if (usedEntryNames.add(candidate.lowercase())) {
            return candidate
        }
        suffixIndex++
    }
}

internal suspend fun exportNotesAsMarkdownZip(
    context: Context,
    uri: Uri,
    notes: List<NoteWithItems>,
    fallbackUntitledNote: String,
    fallbackUntitledList: String,
): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                writeNotesMarkdownZip(
                    outputStream = output,
                    notes = notes,
                    fallbackUntitledNote = fallbackUntitledNote,
                    fallbackUntitledList = fallbackUntitledList,
                )
                output.flush()
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

internal data class MarkdownZipEntry(
    val suggestedTitle: String?,
    val markdown: String,
)

internal fun isValidMarkdownZipEntryName(entryName: String): Boolean {
    val normalized = entryName.replace('\\', '/').trim('/')
    if (normalized.isEmpty()) return false
    val segments = normalized.split('/')
    if (segments.any { segment -> segment.isEmpty() || segment.equals("__MACOSX", ignoreCase = true) || segment.startsWith('.') }) {
        return false
    }
    val fileName = segments.last()
    return fileName.endsWith(MARKDOWN_EXTENSION, ignoreCase = true) ||
        fileName.endsWith(MARKDOWN_LONG_EXTENSION, ignoreCase = true)
}

internal fun readMarkdownEntriesFromZip(
    inputStream: InputStream,
    maximumEntryBytes: Long = MAX_NOTE_MARKDOWN_BYTES,
): List<MarkdownZipEntry> {
    val entries = mutableListOf<MarkdownZipEntry>()
    ZipInputStream(inputStream).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            try {
                if (!entry.isDirectory && isValidMarkdownZipEntryName(entry.name)) {
                    val result = readMarkdownFromInputStream(zip, maximumEntryBytes)
                    if (result is NoteMarkdownImportResult.Success) {
                        val normalizedName = entry.name.replace('\\', '/')
                        entries +=
                            MarkdownZipEntry(
                                suggestedTitle = markdownTitleFromFileName(normalizedName),
                                markdown = result.markdown,
                            )
                    }
                }
            } finally {
                zip.closeEntry()
            }
        }
    }
    return entries
}
