package dev.bikram.remember.share

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import dev.bikram.remember.R
import dev.bikram.remember.data.AppMediaStorage
import dev.bikram.remember.data.BackupIo
import dev.bikram.remember.data.DefaultNotePreferencesState
import dev.bikram.remember.data.DefaultNotePrefs
import dev.bikram.remember.data.NoteOptions
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.googletasks.ManualTaskImportParser
import dev.bikram.remember.ui.components.RememberBackupZipActionDialog
import dev.bikram.remember.ui.components.showBackupRestoreOutcome
import dev.bikram.remember.ui.edit.MAX_NOTE_MARKDOWN_KB
import dev.bikram.remember.ui.edit.MarkdownZipTooLargeException
import dev.bikram.remember.ui.edit.NoteMarkdownImportResult
import dev.bikram.remember.ui.edit.createImportedMarkdownNote
import dev.bikram.remember.ui.edit.importMarkdownFromUri
import dev.bikram.remember.ui.edit.isJsonFileUri
import dev.bikram.remember.ui.edit.isMarkdownOrPlainTextUri
import dev.bikram.remember.ui.edit.isZipFileUri
import dev.bikram.remember.ui.edit.readMarkdownEntriesFromZip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal sealed interface FileImportOutcome {
    data class ImportedSingleNote(
        val noteId: Long,
    ) : FileImportOutcome

    data class ImportedBatch(
        val importedCount: Int,
        /** Ids of the Markdown/text notes created; excludes notes restored from a Remember backup ZIP. */
        val noteIds: List<Long> = emptyList(),
    ) : FileImportOutcome

    data class StagedBackupZip(
        val file: File,
    ) : FileImportOutcome

    data class ValidTaskImportJson(
        val jsonText: String,
    ) : FileImportOutcome

    data object HandledWithErrorToast : FileImportOutcome

    /**
     * Nothing here can be imported as notes (an unsupported type, an invalid task export, a ZIP with no
     * Markdown). The caller offers to attach the files to a new note instead of rejecting them.
     */
    data class NeedsAttachments(
        val uris: List<Uri>,
        /**
         * True when the files are a type Remember imports (Markdown, text, ZIP, JSON) but their content
         * isn't importable, so asking first avoids surprising someone who expected an import. False for
         * types Remember never imports; those are attached straight away.
         */
        val confirmFirst: Boolean,
    ) : FileImportOutcome
}

/**
 * Shared file import pipeline used by both [dev.bikram.remember.MainActivity] drag-and-drop and
 * [ShareReceiverActivity] (`ACTION_SEND`, `ACTION_SEND_MULTIPLE`, `ACTION_VIEW`).
 */
internal object FileImportCoordinator {
    suspend fun importUris(
        context: Context,
        uris: List<Uri>,
        noteRepository: NoteRepository,
        backupIo: BackupIo,
        defaultNotePrefs: DefaultNotePrefs? = null,
    ): FileImportOutcome {
        val appContext = context.applicationContext
        if (uris.isEmpty()) {
            showToast(appContext, appContext.getString(R.string.drag_drop_error_unsupported_type))
            return FileImportOutcome.HandledWithErrorToast
        }
        if (uris.any { uri -> !isImportableDocument(appContext, uri) }) {
            return FileImportOutcome.NeedsAttachments(uris, confirmFirst = false)
        }
        val firstUri = uris.first()
        if (uris.size == 1 && isJsonFileUri(appContext, firstUri)) {
            return handleSingleJson(appContext, firstUri)
        }
        if (uris.size == 1 && isZipFileUri(appContext, firstUri) && backupIo.isBackupZip(firstUri)) {
            val stagedFile = stageBackupFile(appContext, firstUri)
            return if (stagedFile != null) {
                FileImportOutcome.StagedBackupZip(stagedFile)
            } else {
                showToast(appContext, appContext.getString(R.string.backup_zip_import_failed))
                FileImportOutcome.HandledWithErrorToast
            }
        }
        val defaultOptions = resolveDefaultNoteOptions(defaultNotePrefs)
        if (uris.size == 1 && !isZipFileUri(appContext, firstUri)) {
            return handleSingleMarkdownOrText(appContext, firstUri, noteRepository, defaultOptions)
        }
        return handleMultiUris(appContext, uris, noteRepository, backupIo, defaultOptions)
    }

    private fun isImportableDocument(
        appContext: Context,
        uri: Uri,
    ): Boolean = isJsonFileUri(appContext, uri) || isZipFileUri(appContext, uri) || isMarkdownOrPlainTextUri(appContext, uri)

    /** Creates one note holding [staged] files as attachments (title = the file name when there is just one). */
    suspend fun createNoteWithAttachments(
        noteRepository: NoteRepository,
        appMediaStorage: AppMediaStorage,
        defaultNotePrefs: DefaultNotePrefs?,
        staged: List<StagedShareImage>,
        untitledTitle: String,
    ): Long {
        val title =
            staged
                .singleOrNull()
                ?.displayName
                ?.substringBeforeLast('.')
                ?.ifBlank { null }
                ?: untitledTitle
        val noteId =
            noteRepository.createNote(
                title = title,
                body = "",
                colorIndex = 0,
                options = resolveDefaultNoteOptions(defaultNotePrefs),
            )
        staged.forEach { file ->
            val promoted = appMediaStorage.promoteStagedFileToAttachment(noteId, file.file, file.displayName, file.mimeType)
            noteRepository.addAttachment(
                noteId = noteId,
                uri = promoted.uriString,
                displayName = promoted.displayName,
                mimeType = promoted.mimeType,
            )
        }
        return noteId
    }

    private suspend fun resolveDefaultNoteOptions(defaultNotePrefs: DefaultNotePrefs?): NoteOptions {
        val defaults =
            runCatching { defaultNotePrefs?.snapshot() }.getOrNull()
                ?: DefaultNotePreferencesState()
        return NoteOptions(
            importance = defaults.defaultImportance,
            visibility = defaults.defaultVisibility,
        )
    }

    private suspend fun handleSingleJson(
        appContext: Context,
        uri: Uri,
    ): FileImportOutcome {
        val validJson =
            withContext(Dispatchers.IO) {
                runCatching {
                    val text =
                        appContext.contentResolver
                            .openInputStream(uri)
                            ?.use { stream -> stream.reader(Charsets.UTF_8).readText() }
                            ?: return@runCatching null
                    ManualTaskImportParser().parse(text)
                    text
                }.getOrNull()
            }
        return if (validJson != null) {
            FileImportOutcome.ValidTaskImportJson(validJson)
        } else {
            FileImportOutcome.NeedsAttachments(listOf(uri), confirmFirst = true)
        }
    }

    private suspend fun stageBackupFile(
        appContext: Context,
        uri: Uri,
    ): File? =
        withContext(Dispatchers.IO) {
            var temporaryFile: File? = null
            runCatching {
                val stagedFile = StagedBackupFiles.createFile(appContext.cacheDir)
                temporaryFile = stagedFile
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    stagedFile.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Could not open backup archive")
                stagedFile
            }.onFailure {
                temporaryFile?.delete()
            }.getOrNull()
        }

    private suspend fun handleSingleMarkdownOrText(
        appContext: Context,
        uri: Uri,
        noteRepository: NoteRepository,
        defaultOptions: NoteOptions,
    ): FileImportOutcome {
        if (!isMarkdownOrPlainTextUri(appContext, uri)) {
            showToast(appContext, appContext.getString(R.string.drag_drop_error_unsupported_type))
            return FileImportOutcome.HandledWithErrorToast
        }
        return when (val result = importMarkdownFromUri(appContext, uri)) {
            is NoteMarkdownImportResult.Success -> {
                val noteTitle = result.suggestedTitle ?: appContext.getString(R.string.edit_note_title_new)
                val newId =
                    createImportedMarkdownNote(
                        noteRepository = noteRepository,
                        title = noteTitle,
                        markdown = result.markdown,
                        options = defaultOptions,
                    )
                showToast(appContext, appContext.getString(R.string.toast_import_md_success))
                FileImportOutcome.ImportedSingleNote(newId)
            }
            NoteMarkdownImportResult.TooLarge -> {
                showToast(
                    appContext,
                    appContext.getString(R.string.toast_import_md_too_large, MAX_NOTE_MARKDOWN_KB),
                )
                FileImportOutcome.HandledWithErrorToast
            }
            NoteMarkdownImportResult.Empty -> {
                showToast(appContext, appContext.getString(R.string.toast_import_md_empty))
                FileImportOutcome.HandledWithErrorToast
            }
            NoteMarkdownImportResult.Failed -> {
                showToast(appContext, appContext.getString(R.string.toast_import_md_failed))
                FileImportOutcome.HandledWithErrorToast
            }
        }
    }

    private suspend fun handleMultiUris(
        appContext: Context,
        uris: List<Uri>,
        noteRepository: NoteRepository,
        backupIo: BackupIo,
        defaultOptions: NoteOptions,
    ): FileImportOutcome {
        var importedCount = 0
        val createdNoteIds = mutableListOf<Long>()
        var sawZip = false
        var sawZipValidContent = false
        var sawUnsupported = false
        var sawFailure = false
        var sawZipTooLarge = false
        val fallbackTitle = appContext.getString(R.string.edit_note_title_new)
        withContext(Dispatchers.IO) {
            uris.forEach { uri ->
                when {
                    isZipFileUri(appContext, uri) -> {
                        sawZip = true
                        if (backupIo.isBackupZip(uri)) {
                            val count = runCatching { backupIo.importFrom(uri, preserveIdsForNotes = false) }.getOrDefault(0)
                            if (count > 0) {
                                sawZipValidContent = true
                                importedCount += count
                            } else {
                                sawFailure = true
                            }
                        } else {
                            runCatching {
                                appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                                    readMarkdownEntriesFromZip(inputStream)
                                } ?: throw IllegalArgumentException("Could not open archive")
                            }.onSuccess { entries ->
                                if (entries.isNotEmpty()) sawZipValidContent = true
                                entries.forEach { entry ->
                                    createdNoteIds +=
                                        createImportedMarkdownNote(
                                            noteRepository = noteRepository,
                                            title = entry.suggestedTitle ?: fallbackTitle,
                                            markdown = entry.markdown,
                                            options = defaultOptions,
                                        )
                                    importedCount++
                                }
                            }.onFailure { error ->
                                if (error is MarkdownZipTooLargeException) sawZipTooLarge = true else sawFailure = true
                            }
                        }
                    }
                    isMarkdownOrPlainTextUri(appContext, uri) -> {
                        when (val result = importMarkdownFromUri(appContext, uri)) {
                            is NoteMarkdownImportResult.Success -> {
                                createdNoteIds +=
                                    createImportedMarkdownNote(
                                        noteRepository = noteRepository,
                                        title = result.suggestedTitle ?: fallbackTitle,
                                        markdown = result.markdown,
                                        options = defaultOptions,
                                    )
                                importedCount++
                            }
                            NoteMarkdownImportResult.Failed -> sawFailure = true
                            NoteMarkdownImportResult.Empty,
                            NoteMarkdownImportResult.TooLarge,
                            -> Unit
                        }
                    }
                    else -> sawUnsupported = true
                }
            }
        }
        if (importedCount == 0 && sawZip && !sawZipValidContent && !sawFailure && !sawZipTooLarge) {
            return FileImportOutcome.NeedsAttachments(uris, confirmFirst = true)
        }
        val feedbackMessage =
            if (importedCount > 0) {
                appContext.resources.getQuantityString(R.plurals.toast_imported_notes, importedCount, importedCount)
            } else {
                when {
                    sawZipTooLarge -> appContext.getString(R.string.markdown_import_zip_too_large)
                    sawUnsupported && !sawFailure -> appContext.getString(R.string.drag_drop_error_unsupported_type)
                    sawFailure -> appContext.getString(R.string.markdown_import_failed)
                    else -> appContext.getString(R.string.markdown_import_none_valid)
                }
            }
        showToast(appContext, feedbackMessage)
        return if (importedCount > 0) {
            FileImportOutcome.ImportedBatch(importedCount, createdNoteIds)
        } else {
            FileImportOutcome.HandledWithErrorToast
        }
    }

    private suspend fun showToast(
        appContext: Context,
        message: String,
    ) {
        withContext(Dispatchers.Main) {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Cache copies of dropped or shared Remember backup ZIPs awaiting the Import/Restore choice.
 * Copies left behind by an earlier process (killed while the dialog was open) are deleted once per
 * process; a copy still referenced by restored UI state is kept.
 */
internal object StagedBackupFiles {
    private const val FILE_PREFIX = "remember_drop_backup_"
    private const val FILE_SUFFIX = ".zip"
    private val orphansDeleted = AtomicBoolean(false)

    fun createFile(cacheDir: File): File = File.createTempFile(FILE_PREFIX, FILE_SUFFIX, cacheDir)

    fun deleteOrphansOnce(
        cacheDir: File,
        keepPath: String?,
    ) {
        if (!orphansDeleted.compareAndSet(false, true)) return
        cacheDir
            .listFiles { file ->
                file.name.startsWith(FILE_PREFIX) &&
                    file.name.endsWith(FILE_SUFFIX) &&
                    file.absolutePath != keepPath
            }?.forEach(File::delete)
    }
}

/**
 * Path of a staged Remember backup waiting for the Import/Restore choice. Saveable, so a window
 * resize or rotation keeps the pending dialog instead of stranding its staged copy in the cache;
 * a restored path whose copy has since been deleted is cleared.
 */
@Composable
internal fun rememberPendingStagedBackupPath(context: Context): MutableState<String?> {
    val pendingPath = rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val keptPath = pendingPath.value
        val keptFileExists =
            withContext(Dispatchers.IO) {
                StagedBackupFiles.deleteOrphansOnce(context.cacheDir, keepPath = keptPath)
                keptPath != null && File(keptPath).isFile
            }
        if (keptPath != null && !keptFileExists && pendingPath.value == keptPath) {
            pendingPath.value = null
        }
    }
    return pendingPath
}

@Composable
internal fun StagedBackupActionDialog(
    backupFile: File,
    backupIo: BackupIo,
    appScope: CoroutineScope,
    snackbarHostState: SnackbarHostState?,
    onClear: () -> Unit,
    /** Called when the user picks Import or Restore (not on dismiss), before [onClear]. */
    onActionChosen: () -> Unit = {},
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val resources = LocalResources.current
    var confirmingRestore by remember(backupFile) { mutableStateOf(false) }
    var mediaSummary by remember(backupFile) { mutableStateOf<BackupIo.RestoreMediaSummary?>(null) }
    RememberBackupZipActionDialog(
        confirmingRestore = confirmingRestore,
        mediaSummary = mediaSummary,
        onImportMerge = {
            onActionChosen()
            onClear()
            appScope.launch {
                val count =
                    try {
                        backupIo.importFrom(Uri.fromFile(backupFile), preserveIdsForNotes = false)
                    } finally {
                        backupFile.delete()
                    }
                val message =
                    if (count > 0) {
                        resources.getQuantityString(R.plurals.toast_imported_notes, count, count)
                    } else {
                        resources.getString(R.string.backup_zip_import_failed)
                    }
                Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
            }
        },
        onRequestRestore = {
            appScope.launch {
                mediaSummary = backupIo.inspectRestoreMedia(Uri.fromFile(backupFile))
                confirmingRestore = true
            }
        },
        onConfirmRestore = {
            onActionChosen()
            onClear()
            appScope.launch {
                val result =
                    try {
                        backupIo.restoreFullReplace(Uri.fromFile(backupFile))
                    } finally {
                        backupFile.delete()
                    }
                if (snackbarHostState != null) {
                    showBackupRestoreOutcome(appContext, snackbarHostState, result)
                } else {
                    showBackupRestoreOutcomeToasts(appContext, result)
                }
            }
        },
        onDismiss = {
            backupFile.delete()
            onClear()
        },
    )
}

private fun showBackupRestoreOutcomeToasts(
    appContext: Context,
    result: BackupIo.RestoreResult,
) {
    val resources = appContext.resources
    Toast
        .makeText(
            appContext,
            resources.getQuantityString(R.plurals.toast_imported_notes, result.noteCount, result.noteCount),
            Toast.LENGTH_SHORT,
        ).show()
    val foldersNeedingReselection = result.settingsOutcome.foldersNeedingReselection
    if (foldersNeedingReselection > 0) {
        Toast
            .makeText(
                appContext,
                resources.getQuantityString(
                    R.plurals.settings_restore_folders_need_reselection,
                    foldersNeedingReselection,
                    foldersNeedingReselection,
                ),
                Toast.LENGTH_LONG,
            ).show()
    }
    if (result.settingsOutcome.automationsDisabled) {
        Toast
            .makeText(
                appContext,
                resources.getString(R.string.settings_restore_automation_disabled_no_folder),
                Toast.LENGTH_LONG,
            ).show()
    }
}
