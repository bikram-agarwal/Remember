package dev.bikram.remember.share

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.remember.R
import dev.bikram.remember.data.AppMediaStorage
import dev.bikram.remember.data.DefaultNotePrefs
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.MediaCopyResult
import dev.bikram.remember.data.NoteOptions
import dev.bikram.remember.data.NoteReminder
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.Visibility
import dev.bikram.remember.data.limitedToReminderSlots
import dev.bikram.remember.data.normalizeTagName
import dev.bikram.remember.data.tagNamesUsingStoredSpellings
import dev.bikram.remember.di.ApplicationScope
import dev.bikram.remember.ui.edit.isStrictChecklistMarkdown
import dev.bikram.remember.ui.edit.markdownToPersistableChecklistItems
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

internal enum class SingleImagePlacement {
    ATTACH_TO_NOTE,
    USE_AS_BANNER,
}

internal sealed interface ShareCaptureUiEvent {
    data class ImageStageFailed(
        val abortCapture: Boolean,
    ) : ShareCaptureUiEvent

    data class SavedForEditor(
        val noteId: Long,
    ) : ShareCaptureUiEvent
}

@HiltViewModel
class ShareCaptureViewModel internal constructor(
    private val noteRepository: NoteRepository,
    private val defaultNotePrefs: DefaultNotePrefs,
    private val applicationScope: CoroutineScope,
    private val savedStateHandle: SavedStateHandle,
    private val cacheDir: File,
    private val untitledNoteTitle: String,
    private val untitledListTitle: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val stageImagesAction: suspend (String, List<Uri>) -> ShareImageStageResult,
    private val promoteBannerAction: suspend (File, String?) -> String?,
    private val promoteAttachmentAction: suspend (Long, File, String, String?) -> MediaCopyResult?,
) : ViewModel() {
    @Inject
    constructor(
        noteRepository: NoteRepository,
        appMediaStorage: AppMediaStorage?,
        defaultNotePrefs: DefaultNotePrefs,
        @ApplicationScope applicationScope: CoroutineScope,
        @ApplicationContext appContext: Context,
        savedStateHandle: SavedStateHandle,
    ) : this(
        noteRepository = noteRepository,
        defaultNotePrefs = defaultNotePrefs,
        applicationScope = applicationScope,
        savedStateHandle = savedStateHandle,
        cacheDir = appContext.cacheDir,
        untitledNoteTitle = appContext.getString(R.string.edit_note_title_new),
        untitledListTitle = appContext.getString(R.string.edit_list_title_new),
        stageImagesAction = { sessionId, uris ->
            ShareMediaStaging.stageImages(appContext, sessionId, uris)
        },
        promoteBannerAction = { file, mimeType ->
            appMediaStorage?.promoteStagedFileToHero(file, mimeType)
        },
        promoteAttachmentAction = { noteId, file, displayName, mimeType ->
            appMediaStorage?.promoteStagedFileToAttachment(noteId, file, displayName, mimeType)
        },
    )

    internal val sessionId: String =
        savedStateHandle.get<String>(KEY_SESSION_ID)
            ?: UUID.randomUUID().toString().also { generated ->
                savedStateHandle[KEY_SESSION_ID] = generated
            }

    private val _title = MutableStateFlow(savedStateHandle.get<String>(KEY_TITLE).orEmpty())
    val title: StateFlow<String> = _title.asStateFlow()

    private val _body = MutableStateFlow(savedStateHandle.get<String>(KEY_BODY).orEmpty())
    val body: StateFlow<String> = _body.asStateFlow()

    private val mutableStagedImages = MutableStateFlow<List<StagedShareImage>>(emptyList())
    internal val stagedImages: StateFlow<List<StagedShareImage>> = mutableStagedImages.asStateFlow()

    private val mutableSingleImageMode =
        MutableStateFlow(
            savedStateHandle
                .get<String>(KEY_SINGLE_IMAGE_MODE)
                ?.let { raw -> runCatching { SingleImagePlacement.valueOf(raw) }.getOrNull() }
                ?: SingleImagePlacement.ATTACH_TO_NOTE,
        )
    internal val singleImageMode: StateFlow<SingleImagePlacement> = mutableSingleImageMode.asStateFlow()

    private val _reminders = MutableStateFlow<List<NoteReminder>>(emptyList())
    val reminders: StateFlow<List<NoteReminder>> = _reminders.asStateFlow()

    private val _tags = MutableStateFlow<List<String>>(savedStateHandle.get<ArrayList<String>>(KEY_TAGS)?.toList().orEmpty())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    private val _importance =
        MutableStateFlow(
            savedStateHandle
                .get<String>(KEY_IMPORTANCE)
                ?.let { raw -> runCatching { Importance.valueOf(raw) }.getOrNull() }
                ?: Importance.DEFAULT,
        )
    val importance: StateFlow<Importance> = _importance.asStateFlow()

    private val _isInitializing = MutableStateFlow(false)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /** Id of the note created by a quick Save; non-null puts the sheet into its read-only "Saved" state. */
    private val _savedNoteId =
        MutableStateFlow(savedStateHandle.get<Long>(KEY_SAVED_NOTE_ID))
    val savedNoteId: StateFlow<Long?> = _savedNoteId.asStateFlow()

    /** The persisted note, loaded after a quick Save so the sheet can render it as the app would. */
    private val _savedNote = MutableStateFlow<NoteWithItems?>(null)
    val savedNote: StateFlow<NoteWithItems?> = _savedNote.asStateFlow()

    init {
        _savedNoteId.value?.let { id ->
            viewModelScope.launch { _savedNote.value = withContext(ioDispatcher) { noteRepository.get(id) } }
        }
    }

    val activeTagSuggestions: StateFlow<List<String>> =
        noteRepository
            .observeActiveTagSuggestions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val eventChannel = Channel<ShareCaptureUiEvent>(Channel.BUFFERED)
    internal val events: Flow<ShareCaptureUiEvent> = eventChannel.receiveAsFlow()

    private var defaultVisibility: Visibility = Visibility.DEFAULT
    private var importanceEditedByUser: Boolean = savedStateHandle.contains(KEY_IMPORTANCE)
    private var titleEditedByUser: Boolean = false
    private var bodyEditedByUser: Boolean = false
    private val commitStarted = AtomicBoolean(false)

    internal fun initializeIfNeeded(route: ShareRoute.QuickCapture) {
        viewModelScope.launch(ioDispatcher) {
            ShareMediaStaging.deleteOrphanSessionsOnce(cacheDir, keepSessionId = sessionId)
        }

        if (savedStateHandle.get<Boolean>(KEY_INITIALIZED) == true) {
            viewModelScope.launch {
                val defaults = runCatching { defaultNotePrefs.snapshot() }.getOrNull()
                if (defaults != null) {
                    defaultVisibility = defaults.defaultVisibility
                }
                val restoredImages =
                    withContext(ioDispatcher) {
                        ShareMediaStaging.restoreSessionImages(cacheDir, sessionId)
                    }
                mutableStagedImages.value = restoredImages
            }
            return
        }

        savedStateHandle[KEY_INITIALIZED] = true
        setTitleInternal(route.initialTitle)
        _isInitializing.value = true

        viewModelScope.launch {
            try {
                val defaults = runCatching { defaultNotePrefs.snapshot() }.getOrNull()
                if (defaults != null) {
                    defaultVisibility = defaults.defaultVisibility
                    if (!importanceEditedByUser) {
                        _importance.value = defaults.defaultImportance
                    }
                }

                val parsedBody =
                    withContext(defaultDispatcher) {
                        ShareIntentParser.resolveRichOrPlainBody(route.rawHtml, route.plainText)
                    }
                if (!bodyEditedByUser && parsedBody.isNotEmpty()) {
                    setBodyInternal(parsedBody)
                }

                if (route.imageUris.isNotEmpty()) {
                    val stageResult = stageImagesAction(sessionId, route.imageUris)
                    mutableStagedImages.value = stageResult.stagedImages
                    val onlyFile = stageResult.stagedImages.singleOrNull()?.takeIf { staged -> !staged.isImage }
                    if (onlyFile != null && _title.value.isBlank() && !titleEditedByUser) {
                        setTitleInternal(onlyFile.displayName.substringBeforeLast('.'))
                    }
                    if (stageResult.failedCount > 0) {
                        val nothingLeft =
                            stageResult.stagedImages.isEmpty() &&
                                _title.value.isBlank() &&
                                _body.value.isBlank()
                        eventChannel.trySend(ShareCaptureUiEvent.ImageStageFailed(abortCapture = nothingLeft))
                    }
                }
            } finally {
                _isInitializing.value = false
            }
        }
    }

    internal fun setStagedImagesForTest(images: List<StagedShareImage>) {
        mutableStagedImages.value = images
    }

    fun setTitle(value: String) {
        titleEditedByUser = true
        setTitleInternal(value)
    }

    private fun setTitleInternal(value: String) {
        _title.value = value
        savedStateHandle[KEY_TITLE] = value
    }

    fun setBody(value: String) {
        bodyEditedByUser = true
        setBodyInternal(value)
    }

    private fun setBodyInternal(value: String) {
        _body.value = value
        savedStateHandle[KEY_BODY] = value
    }

    internal fun setSingleImageMode(mode: SingleImagePlacement) {
        mutableSingleImageMode.value = mode
        savedStateHandle[KEY_SINGLE_IMAGE_MODE] = mode.name
    }

    internal fun removeStagedImage(imageId: String) {
        val current = mutableStagedImages.value
        val target = current.firstOrNull { image -> image.id == imageId } ?: return
        val remaining = current.filterNot { image -> image.id == imageId }
        mutableStagedImages.value = remaining
        if (remaining.size != 1) {
            setSingleImageMode(SingleImagePlacement.ATTACH_TO_NOTE)
        }
        viewModelScope.launch(ioDispatcher) {
            ShareMediaStaging.removeStagedImage(target)
        }
    }

    fun setReminders(updatedReminders: List<NoteReminder>) {
        _reminders.value = updatedReminders.limitedToReminderSlots()
    }

    fun clearReminders() {
        _reminders.value = emptyList()
    }

    fun setImportance(value: Importance) {
        importanceEditedByUser = true
        _importance.value = value
        savedStateHandle[KEY_IMPORTANCE] = value.name
    }

    fun saveTagsWithColors(
        requestedTags: List<String>,
        newColors: Map<String, String>,
    ) {
        val cleaned = tagNamesUsingStoredSpellings(requestedTags, activeTagSuggestions.value)
        _tags.value = cleaned
        savedStateHandle[KEY_TAGS] = ArrayList(cleaned)
        if (newColors.isNotEmpty()) {
            applicationScope.launch {
                newColors.forEach { (name, hex) ->
                    noteRepository.tagRepository?.setTagColor(name, hex)
                }
            }
        }
    }

    fun editExistingTag(
        oldName: String,
        newName: String,
        colorHex: String?,
        resetColor: Boolean,
    ) {
        viewModelScope.launch {
            val result =
                noteRepository.tagRepository?.editTag(
                    oldName = oldName,
                    newName = newName,
                    colorHex = colorHex,
                    resetColor = resetColor,
                ) ?: return@launch
            val updated =
                _tags.value
                    .map { tagName ->
                        if (tagName.equals(result.oldName, ignoreCase = true)) result.newName else tagName
                    }.distinctBy(::normalizeTagName)
            _tags.value = updated
            savedStateHandle[KEY_TAGS] = ArrayList(updated)
        }
    }

    fun saveQuickClose(onPersisted: (() -> Unit)? = null): Boolean = commitCapture(openInEditor = false, onQuickSaved = onPersisted)

    fun saveAndOpenInEditor() {
        commitCapture(openInEditor = true, onQuickSaved = null)
    }

    private fun commitCapture(
        openInEditor: Boolean,
        onQuickSaved: (() -> Unit)?,
    ): Boolean {
        if (_isInitializing.value) return false
        val snapshotTitle = _title.value.trim()
        val snapshotBody = _body.value
        val snapshotImages = mutableStagedImages.value
        if (snapshotTitle.isBlank() && snapshotBody.isBlank() && snapshotImages.isEmpty()) return false
        if (!commitStarted.compareAndSet(false, true)) return false

        _isSaving.value = true
        val snapshotMode = mutableSingleImageMode.value
        val snapshotReminders = _reminders.value
        val snapshotTags = _tags.value
        val snapshotImportance = _importance.value
        val snapshotVisibility = defaultVisibility

        applicationScope.launch {
            val noteId =
                withContext(ioDispatcher) {
                    persistSnapshot(
                        snapshotTitle = snapshotTitle,
                        snapshotBody = snapshotBody,
                        snapshotImages = snapshotImages,
                        snapshotMode = snapshotMode,
                        snapshotReminders = snapshotReminders,
                        snapshotTags = snapshotTags,
                        snapshotImportance = snapshotImportance,
                        snapshotVisibility = snapshotVisibility,
                    )
                }
            if (openInEditor) {
                eventChannel.trySend(ShareCaptureUiEvent.SavedForEditor(noteId))
            } else {
                savedStateHandle[KEY_SAVED_NOTE_ID] = noteId
                _savedNote.value = withContext(ioDispatcher) { noteRepository.get(noteId) }
                _savedNoteId.value = noteId
                _isSaving.value = false
                onQuickSaved?.invoke()
            }
        }
        return true
    }

    private suspend fun persistSnapshot(
        snapshotTitle: String,
        snapshotBody: String,
        snapshotImages: List<StagedShareImage>,
        snapshotMode: SingleImagePlacement,
        snapshotReminders: List<NoteReminder>,
        snapshotTags: List<String>,
        snapshotImportance: Importance,
        snapshotVisibility: Visibility,
    ): Long {
        try {
            val bannerCandidate =
                snapshotImages
                    .singleOrNull()
                    ?.takeIf { snapshotMode == SingleImagePlacement.USE_AS_BANNER }
            val heroUri =
                bannerCandidate?.let { candidate ->
                    promoteBannerAction(candidate.file, candidate.mimeType)
                }
            val soonestReminder = snapshotReminders.minByOrNull { reminder -> reminder.reminderAt }
            val options =
                NoteOptions(
                    reminderAt = soonestReminder?.reminderAt,
                    importance = snapshotImportance,
                    visibility = snapshotVisibility,
                    pictureUri = heroUri,
                    tags = snapshotTags,
                    recurrence = soonestReminder?.recurrence?.sanitized(),
                    reminders = snapshotReminders,
                )
            val createAsChecklist = isStrictChecklistMarkdown(snapshotBody)
            val fallbackTitle = if (createAsChecklist) untitledListTitle else untitledNoteTitle
            val finalTitle = snapshotTitle.ifBlank { fallbackTitle }
            val createdId =
                if (createAsChecklist) {
                    noteRepository.createListWithItems(
                        title = finalTitle,
                        colorIndex = 0,
                        items = markdownToPersistableChecklistItems(snapshotBody),
                        options = options,
                    )
                } else {
                    noteRepository.createNote(
                        title = finalTitle,
                        body = snapshotBody,
                        colorIndex = 0,
                        options = options,
                    )
                }

            if (bannerCandidate == null && snapshotImages.isNotEmpty()) {
                snapshotImages.forEach { image ->
                    val promoted =
                        promoteAttachmentAction(
                            createdId,
                            image.file,
                            image.displayName,
                            image.mimeType,
                        )
                    val attachmentUri = promoted?.uriString ?: image.file.toURI().toString()
                    val attachmentName = promoted?.displayName ?: image.displayName
                    val attachmentMime = promoted?.mimeType ?: image.mimeType
                    noteRepository.addAttachment(
                        noteId = createdId,
                        uri = attachmentUri,
                        displayName = attachmentName,
                        mimeType = attachmentMime,
                    )
                }
            }
            return createdId
        } finally {
            ShareMediaStaging.deleteSession(cacheDir, sessionId)
        }
    }

    fun discardCapture() {
        if (commitStarted.get()) return
        applicationScope.launch(ioDispatcher) {
            ShareMediaStaging.deleteSession(cacheDir, sessionId)
        }
    }

    companion object {
        private const val KEY_SESSION_ID = "share_session_id"
        private const val KEY_INITIALIZED = "share_initialized"
        private const val KEY_TITLE = "share_title"
        private const val KEY_BODY = "share_body"
        private const val KEY_SINGLE_IMAGE_MODE = "share_single_image_mode"
        private const val KEY_TAGS = "share_tags"
        private const val KEY_IMPORTANCE = "share_importance"
        private const val KEY_SAVED_NOTE_ID = "share_saved_note_id"
    }
}
