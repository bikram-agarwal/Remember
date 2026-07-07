package dev.bikram.remember.ui.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.EntryPointAccessors
import dev.bikram.remember.data.AppMediaStorage
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteAction
import dev.bikram.remember.data.NoteAttachmentEntity
import dev.bikram.remember.data.NoteOptions
import dev.bikram.remember.data.NoteReminder
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.RecurrenceRule
import dev.bikram.remember.data.RememberReservedTags
import dev.bikram.remember.data.getActiveReminders
import dev.bikram.remember.di.SettingsDependenciesEntryPoint
import dev.bikram.remember.ui.common.HeroFraming
import dev.bikram.remember.ui.nav.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.bikram.remember.data.Visibility as NoteVisibility

/**
 * Shared owner of every persisted field, setter, and note-lifecycle action common to both the
 * Edit Note and Edit List screens. The two screens differ only in their body payload (free-text
 * body vs. a checklist) and the create/update/diff calls that depend on it, so those are left as
 * abstract hooks ([persistNewDraftForAttachment], [saveIfNeeded]) while everything else lives here.
 *
 * Each field is exposed as its own [StateFlow] so leaf composables can collect just the slice they
 * render and skip recompositions when unrelated fields change.
 *
 * Save/load is serialized through a single [EditorPersistenceSession] so ON_STOP, dispose, and
 * explicit calls cannot race against each other (or against the async note load in the subclass
 * `init`).
 */
abstract class BaseEditorViewModel(
    protected val repository: NoteRepository,
    protected val appMediaStorage: AppMediaStorage?,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    protected val noteId: Long? =
        savedStateHandle
            .get<Long>(Routes.ARG_ID)
            ?.takeIf { it > 0L }

    /** Only the Edit Note screen passes a prefill; lists leave this blank. */
    protected val prefillBody: String = savedStateHandle[Routes.ARG_PREFILL] ?: ""

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _starred = MutableStateFlow(false)
    val starred: StateFlow<Boolean> = _starred.asStateFlow()

    /**
     * Snapshot of the underlying note's [dev.bikram.remember.data.NoteEntity.completedAt] as a
     * boolean. Driven by the live DB observer started in the subclass `init`, so external
     * completion via swipe / notification action / repository.markCompleted is reflected here too.
     */
    private val _completed = MutableStateFlow(false)
    val completed: StateFlow<Boolean> = _completed.asStateFlow()

    private val _reminderAt = MutableStateFlow<Long?>(null)
    val reminderAt: StateFlow<Long?> = _reminderAt.asStateFlow()

    private val _recurrence = MutableStateFlow<RecurrenceRule?>(null)
    val recurrence: StateFlow<RecurrenceRule?> = _recurrence.asStateFlow()

    private val _reminders = MutableStateFlow<List<NoteReminder>>(emptyList())
    val reminders: StateFlow<List<NoteReminder>> = _reminders.asStateFlow()

    private val _importance = MutableStateFlow(Importance.DEFAULT)
    val importance: StateFlow<Importance> = _importance.asStateFlow()

    private val _visibility = MutableStateFlow(NoteVisibility.DEFAULT)
    val visibility: StateFlow<NoteVisibility> = _visibility.asStateFlow()

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val _pictureUri = MutableStateFlow<String?>(null)
    val pictureUri: StateFlow<String?> = _pictureUri.asStateFlow()

    /** Bumped whenever the hero bytes change, including in-place edits that keep the same URI string. */
    private val _pictureRevision = MutableStateFlow(0L)
    val pictureRevision: StateFlow<Long> = _pictureRevision.asStateFlow()

    private val _pictureHeroFraming = MutableStateFlow<String?>(null)
    val pictureHeroFraming: StateFlow<String?> = _pictureHeroFraming.asStateFlow()

    private val _iconKey = MutableStateFlow<String?>(null)
    val iconKey: StateFlow<String?> = _iconKey.asStateFlow()

    private val _actions = MutableStateFlow<List<NoteAction>>(emptyList())
    val actions: StateFlow<List<NoteAction>> = _actions.asStateFlow()

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()
    val activeTagSuggestions: StateFlow<List<String>> =
        repository
            .observeActiveTagSuggestions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _attachments = MutableStateFlow<List<NoteAttachmentEntity>>(emptyList())
    val attachments: StateFlow<List<NoteAttachmentEntity>> = _attachments.asStateFlow()

    private val _createdAt = MutableStateFlow<Long?>(null)
    val createdAt: StateFlow<Long?> = _createdAt.asStateFlow()

    private val _updatedAt = MutableStateFlow<Long?>(null)
    val updatedAt: StateFlow<Long?> = _updatedAt.asStateFlow()

    /**
     * Mirrors the underlying note's archived / trashed shelf. Used by the edit screen to flip into
     * read-only mode and swap the bottom-bar action set. New notes/lists always start active.
     */
    private val _archived = MutableStateFlow(false)
    val archived: StateFlow<Boolean> = _archived.asStateFlow()

    private val _trashed = MutableStateFlow(false)
    val trashed: StateFlow<Boolean> = _trashed.asStateFlow()

    protected fun updateTimestamps(
        createdAt: Long?,
        updatedAt: Long?,
    ) {
        _createdAt.value = createdAt
        _updatedAt.value = updatedAt
    }

    /**
     * True once this session is backed by a database row ([loadedId] non-null). New drafts start
     * false so the action bar omits archive/trash until the first save (or attachment create)
     * assigns an id, without tying that UI to the navigation argument (which stays null).
     */
    private val _hasPersistedRow = MutableStateFlow(noteId != null)
    val hasPersistedRow: StateFlow<Boolean> = _hasPersistedRow.asStateFlow()

    private val _currentNoteId = MutableStateFlow(noteId)
    val currentNoteId: StateFlow<Long?> = _currentNoteId.asStateFlow()

    protected val persistence =
        EditorPersistenceSession(initialDirty = noteId == null && prefillBody.isNotBlank())
    val hasUnsavedChanges: StateFlow<Boolean> = persistence.hasUnsavedChanges

    protected var loadedId: Long? = noteId
    protected var originalNote: dev.bikram.remember.data.NoteEntity? = null

    protected fun markDirty() {
        persistence.markDirty()
    }

    protected fun syncHasPersistedRow() {
        _hasPersistedRow.value = loadedId != null
        _currentNoteId.value = loadedId
    }

    /**
     * Populates every field this base owns from a freshly loaded row. Subclasses call this from
     * their `init` load and then set their own body/items payload.
     */
    protected fun applyLoadedCommon(existing: NoteWithItems) {
        val n = existing.note
        originalNote = n
        _title.value = n.title
        _starred.value = n.starred || n.tags.contains(RememberReservedTags.STARRED)
        val activeRems = n.getActiveReminders()
        _reminders.value = activeRems
        val soonest = activeRems.minByOrNull { it.reminderAt }
        _reminderAt.value = soonest?.reminderAt
        _recurrence.value = soonest?.recurrence?.sanitized()
        _importance.value = n.importance
        _visibility.value = n.visibility
        _locked.value = n.locked
        _pictureUri.value = n.pictureUri
        _pictureHeroFraming.value = n.pictureHeroFraming
        _iconKey.value = n.iconKey
        _actions.value = n.actions
        _tags.value = n.tags.filterNot { it == RememberReservedTags.STARRED }
        _attachments.value = existing.attachments
        _archived.value = n.archived
        _trashed.value = n.trashed
        _completed.value = n.completedAt != null
        _createdAt.value = n.createdAt
        _updatedAt.value = n.updatedAt
    }

    /**
     * Live-mirror only the fields that can be written from OUTSIDE this VM while the editor is open:
     * reminderAt + recurrence (snooze action, recurrence advance after a fire, mark-as-done from
     * notification), plus trashed/completed/timestamps. The other fields are owned by this VM's
     * user-input flow and would clobber unsaved drafts if mirrored. Room's Flow only emits on actual
     * row changes, so this is a single small distinct subscription - negligible battery impact.
     */
    protected fun startExternalFieldMirror(id: Long) {
        viewModelScope.launch {
            repository.observe(id).collect { row ->
                val n = row?.note ?: return@collect
                val activeRems = n.getActiveReminders()
                if (_reminders.value != activeRems) _reminders.value = activeRems
                if (_reminderAt.value != n.reminderAt) _reminderAt.value = n.reminderAt
                val sanitized = n.recurrence?.sanitized()
                if (_recurrence.value != sanitized) _recurrence.value = sanitized
                if (_trashed.value != n.trashed) _trashed.value = n.trashed
                val isCompleted = n.completedAt != null
                if (_completed.value != isCompleted) _completed.value = isCompleted
                if (_createdAt.value != n.createdAt) _createdAt.value = n.createdAt
                if (_updatedAt.value != n.updatedAt) _updatedAt.value = n.updatedAt
            }
        }
    }

    fun setTitle(value: String) {
        if (_title.value == value) return
        _title.value = value
        markDirty()
    }

    fun toggleStar() {
        _starred.value = !_starred.value
        markDirty()
    }

    fun setReminder(
        at: Long?,
        rule: RecurrenceRule?,
    ) {
        val normalized = rule?.sanitized()
        if (_reminderAt.value == at && _recurrence.value == normalized) return
        _reminderAt.value = at
        _recurrence.value = normalized
        if (at != null) {
            _reminders.value = listOf(NoteReminder(at, normalized))
        } else {
            _reminders.value = emptyList()
        }
        markDirty()
    }

    fun setReminders(remindersList: List<NoteReminder>) {
        if (_reminders.value == remindersList) return
        _reminders.value = remindersList
        val soonest = remindersList.minByOrNull { it.reminderAt }
        _reminderAt.value = soonest?.reminderAt
        _recurrence.value = soonest?.recurrence?.sanitized()
        markDirty()
    }

    fun setImportance(value: Importance) {
        if (_importance.value == value) return
        _importance.value = value
        markDirty()
    }

    fun setVisibility(value: NoteVisibility) {
        if (_visibility.value == value) return
        _visibility.value = value
        markDirty()
        refreshActiveNotificationVisibility(value)
    }

    private fun refreshActiveNotificationVisibility(value: NoteVisibility) {
        val id = loadedId ?: return
        viewModelScope.launch {
            repository.refreshNotificationVisibilityPreview(id, value)
        }
    }

    fun toggleLock() {
        _locked.value = !_locked.value
        markDirty()
    }

    fun setPictureUri(value: String?) {
        if (_pictureUri.value != value) {
            _pictureUri.value = value
        }
        if (value == null) {
            _pictureHeroFraming.value = null
        }
        _pictureRevision.value = _pictureRevision.value + 1L
        markDirty()
    }

    fun setHeroWithFraming(
        pictureUri: String,
        framing: HeroFraming,
    ) {
        _pictureUri.value = pictureUri
        _pictureHeroFraming.value = framing.toJsonString()
        _pictureRevision.value = _pictureRevision.value + 1L
        markDirty()
    }

    fun setIconKey(value: String?) {
        if (_iconKey.value == value) return
        _iconKey.value = value
        markDirty()
    }

    fun setActions(value: List<NoteAction>) {
        if (_actions.value == value) return
        _actions.value = value
        markDirty()
    }

    fun setTags(value: List<String>) {
        val cleaned = value.filterNot { it == RememberReservedTags.STARRED }
        if (_tags.value == cleaned) return
        _tags.value = cleaned
        markDirty()
    }

    fun addTag(
        value: String,
        colorHex: String,
    ) {
        val cleaned = value.trim()
        if (cleaned.isBlank() || cleaned == RememberReservedTags.STARRED) return
        if (_tags.value.any { tag -> tag.equals(cleaned, ignoreCase = true) }) return
        _tags.value = _tags.value + cleaned
        markDirty()
        viewModelScope.launch {
            repository.tagRepository?.setTagColor(cleaned, colorHex)
        }
    }

    fun saveTagsWithColors(
        tags: List<String>,
        newColors: Map<String, String>,
    ) {
        setTags(tags)
        if (newColors.isNotEmpty()) {
            viewModelScope.launch {
                newColors.forEach { (name, hex) ->
                    repository.tagRepository?.setTagColor(name, hex)
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
                repository.tagRepository?.editTag(
                    oldName = oldName,
                    newName = newName,
                    colorHex = colorHex,
                    resetColor = resetColor,
                ) ?: return@launch
            val updatedTags =
                _tags.value
                    .map { tagName ->
                        if (tagName.equals(result.oldName, ignoreCase = true)) result.newName else tagName
                    }.distinctBy { tagName -> tagName.lowercase() }
            if (_tags.value != updatedTags) {
                _tags.value = updatedTags
                markDirty()
            }
        }
    }

    fun addAttachment(
        uri: Uri,
        name: String,
        mime: String?,
    ) {
        viewModelScope.launch {
            persistence.withLock {
                val id = loadedId ?: persistNewDraftForAttachment()
                val copyResult =
                    appMediaStorage?.copyAttachmentToPrivateStorage(
                        noteId = id,
                        sourceUri = uri,
                        displayName = name,
                        mimeType = mime,
                    )
                val attachmentUri = copyResult?.uriString ?: uri.toString()
                val attachmentDisplayName = copyResult?.displayName ?: name
                val attachmentMimeType = copyResult?.mimeType ?: mime
                repository.addAttachment(id, attachmentUri, attachmentDisplayName, attachmentMimeType)
                _attachments.value = repository.get(id)?.attachments ?: emptyList()
                markDirty()
            }
        }
    }

    fun removeAttachment(attachmentId: Long) {
        viewModelScope.launch {
            persistence.withLock {
                repository.removeAttachment(attachmentId)
                val id = loadedId
                _attachments.value =
                    if (id != null) {
                        repository.get(id)?.attachments ?: emptyList()
                    } else {
                        _attachments.value.filterNot { it.id == attachmentId }
                    }
                markDirty()
            }
        }
    }

    protected fun tagsForPersistence(): List<String> {
        val base = _tags.value.filterNot { it == RememberReservedTags.STARRED }
        return if (_starred.value) {
            (base + RememberReservedTags.STARRED).distinct()
        } else {
            base
        }
    }

    protected fun currentOptions() =
        NoteOptions(
            reminderAt = _reminderAt.value,
            importance = _importance.value,
            visibility = _visibility.value,
            pictureUri = _pictureUri.value,
            pictureHeroFraming = _pictureHeroFraming.value,
            locked = _locked.value,
            iconKey = _iconKey.value,
            actions = _actions.value,
            tags = tagsForPersistence(),
            recurrence = _recurrence.value,
            reminders = _reminders.value,
        )

    /**
     * Creates the backing row for a still-unsaved draft when an attachment is added before the
     * first explicit save. Note vs. list differ in the create call (and the note variant restores
     * the starred flag), so each subclass supplies its own. Implementations must set [loadedId]
     * and call [syncHasPersistedRow].
     */
    protected abstract suspend fun persistNewDraftForAttachment(): Long

    /**
     * Persist the in-memory state. Safe to call from ON_STOP, onDispose, or any other lifecycle
     * hook; concurrent calls serialize through [persistence] so we never insert the same draft
     * twice. Returns an undo action that reverts this save, or null when nothing was written.
     */
    abstract suspend fun saveIfNeeded(untitledName: String): (suspend () -> Unit)?

    protected fun syncTimestamps() {
        val id = loadedId ?: return
        viewModelScope.launch {
            val cur = repository.get(id)?.note
            if (cur != null) {
                _createdAt.value = cur.createdAt
                _updatedAt.value = cur.updatedAt
            }
        }
    }

    suspend fun trashCurrent() {
        persistence.withLock {
            val id = loadedId ?: return@withLock
            repository.moveToTrash(id)
            persistence.clearDirty()
            _trashed.value = true
            _archived.value = false
        }
        syncTimestamps()
    }

    /**
     * Flip the note's done state from inside the editor's bottom bar. Routes through
     * [NoteRepository.markCompleted] / [NoteRepository.markIncomplete] so recurrence is honored -
     * completing a recurring note rolls it forward instead of moving it to Done. The live observer
     * started in `init` picks up the new value and updates [completed] automatically.
     */
    suspend fun toggleCompleted() {
        val id = loadedId ?: return
        if (_completed.value) {
            repository.markIncomplete(id)
        } else {
            repository.markCompleted(id)
        }
        syncTimestamps()
    }

    /** Flip the note to the archive shelf after saving any in-flight edits. */
    suspend fun archiveCurrent(untitledName: String) {
        // Persist any in-flight edits first so the archive snapshot matches what the user sees.
        saveIfNeeded(untitledName)
        persistence.withLock {
            val id = loadedId ?: return@withLock
            repository.archiveNote(id)
            _archived.value = true
            _trashed.value = false
            persistence.clearDirty()
        }
        syncTimestamps()
    }

    suspend fun unarchiveCurrent() {
        persistence.withLock {
            val id = loadedId ?: return@withLock
            repository.unarchiveNote(id)
            _archived.value = false
            _trashed.value = false
            persistence.clearDirty()
        }
        syncTimestamps()
    }

    suspend fun restoreFromTrashCurrent() {
        persistence.withLock {
            val id = loadedId ?: return@withLock
            repository.restoreFromTrash(id)
            _trashed.value = false
            _archived.value = false
            persistence.clearDirty()
        }
        syncTimestamps()
    }

    /** Permanent delete of the current note. Called from the trashed-state action bar after confirm. */
    suspend fun deleteForeverCurrent() {
        persistence.withLock {
            val id = loadedId ?: return@withLock
            repository.deleteForever(id)
            persistence.clearDirty()
        }
    }

    suspend fun fireNotification(
        context: android.content.Context,
        untitledName: String,
    ) {
        saveIfNeeded(untitledName)
        val id = loadedId ?: return
        val noteWithItems = repository.get(id) ?: return
        val reminderPrefs =
            EntryPointAccessors
                .fromApplication(
                    context.applicationContext,
                    SettingsDependenciesEntryPoint::class.java,
                ).reminderPrefs()
        val keepUntilDone = reminderPrefs.snapshot().keepReminderNotificationsUntilDone
        dev.bikram.remember.reminders.ReminderReceiver.showNotification(
            context = context,
            note = noteWithItems.note,
            items = noteWithItems.items,
            keepUntilDone = keepUntilDone,
        )
        android.widget.Toast
            .makeText(
                context,
                context.getString(dev.bikram.remember.R.string.notification_created),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
    }
}
