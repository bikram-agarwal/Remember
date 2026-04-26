package dev.bikram.remember.ui.edit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteAction
import dev.bikram.remember.data.NoteAttachmentEntity
import dev.bikram.remember.data.NoteOptions
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.RecurrenceRule
import dev.bikram.remember.data.RememberReservedTags
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.ui.common.HeroFraming
import dev.bikram.remember.data.Visibility as NoteVisibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/**
 * Owns every persisted field for the Edit Note screen and serializes save/load through
 * a single [persistMutex] so that ON_STOP, dispose, and explicit calls cannot race against
 * each other (or against the async note load in [init]).
 *
 * Each field is exposed as its own [StateFlow] so leaf composables can collect just the
 * slice they render and skip recompositions when unrelated fields change. The screen-level
 * composable should not collect everything in one place.
 */
class EditNoteViewModel(
    private val repository: NoteRepository,
    private val themePrefs: ThemePrefs,
    private val noteId: Long?,
    prefillBody: String = "",
) : ViewModel() {

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _body = MutableStateFlow(if (noteId == null) prefillBody else "")
    val body: StateFlow<String> = _body.asStateFlow()

    private val _favorite = MutableStateFlow(false)
    val favorite: StateFlow<Boolean> = _favorite.asStateFlow()

    /**
     * Snapshot of the underlying note's [dev.bikram.remember.data.NoteEntity.completedAt]
     * as a boolean. Driven by the live DB observer in [init], so external completion
     * via swipe / notification action / repository.markCompleted is reflected here too.
     */
    private val _completed = MutableStateFlow(false)
    val completed: StateFlow<Boolean> = _completed.asStateFlow()

    private val _reminderAt = MutableStateFlow<Long?>(null)
    val reminderAt: StateFlow<Long?> = _reminderAt.asStateFlow()

    private val _recurrence = MutableStateFlow<RecurrenceRule?>(null)
    val recurrence: StateFlow<RecurrenceRule?> = _recurrence.asStateFlow()

    private val _importance = MutableStateFlow(Importance.DEFAULT)
    val importance: StateFlow<Importance> = _importance.asStateFlow()

    private val _visibility = MutableStateFlow(NoteVisibility.PRIVATE)
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

    private val _attachments = MutableStateFlow<List<NoteAttachmentEntity>>(emptyList())
    val attachments: StateFlow<List<NoteAttachmentEntity>> = _attachments.asStateFlow()

    /**
     * Mirrors the underlying note's archived / trashed shelf. Used by the edit screen to flip
     * into read-only mode and swap the bottom-bar action set. New notes always start on the
     * active shelf.
     */
    private val _archived = MutableStateFlow(false)
    val archived: StateFlow<Boolean> = _archived.asStateFlow()

    private val _trashed = MutableStateFlow(false)
    val trashed: StateFlow<Boolean> = _trashed.asStateFlow()

    /**
     * True once this session is backed by a database row ([loadedId] non-null). New drafts start
     * false so the action bar omits archive/trash until the first save (or attachment create)
     * assigns an id, without tying that UI to the navigation argument (which stays null).
     */
    private val _hasPersistedRow = MutableStateFlow(noteId != null)
    val hasPersistedRow: StateFlow<Boolean> = _hasPersistedRow.asStateFlow()

    /** True after the initial DB load has populated the state flows (or immediately for a new note). */
    private val _loaded = MutableStateFlow(noteId == null)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private var loadedId: Long? = noteId
    private var dirty: Boolean = noteId == null && prefillBody.isNotBlank()
    private val persistMutex = Mutex()
    /** Bumped on every user-visible mutation so a save that suspends in the repository cannot clear [dirty] if edits landed mid-flight. */
    private val mutationEpoch = AtomicInteger(0)

    private fun markDirty() {
        mutationEpoch.incrementAndGet()
        dirty = true
    }

    private var originalNote: dev.bikram.remember.data.NoteEntity? = null

    private fun syncHasPersistedRow() {
        _hasPersistedRow.value = loadedId != null
    }

    init {
        if (noteId != null) {
            check(persistMutex.tryLock()) { "persistMutex must be unlocked at construction" }
            viewModelScope.launch {
                try {
                    val existing = repository.get(noteId)
                    if (existing != null) {
                        val n = existing.note
                        originalNote = n
                        _title.value = n.title
                        _body.value = n.body
                        _favorite.value = n.favorite || n.tags.contains(RememberReservedTags.FAVORITE)
                        _reminderAt.value = n.reminderAt
                        _recurrence.value = n.recurrence?.sanitized()
                        _importance.value = n.importance
                        _visibility.value = n.visibility
                        _locked.value = n.locked
                        _pictureUri.value = n.pictureUri
                        _pictureHeroFraming.value = n.pictureHeroFraming
                        _iconKey.value = n.iconKey
                        _actions.value = n.actions
                        _tags.value = n.tags.filterNot { it == RememberReservedTags.FAVORITE }
                        _attachments.value = existing.attachments
                        _archived.value = n.archived
                        _trashed.value = n.trashed
                        _completed.value = n.completedAt != null
                    }
                } finally {
                    // Leave loading when the load finishes: missing row, success, or thrown from get().
                    _loaded.value = true
                    persistMutex.unlock()
                }
            }
            // Live-mirror only the fields that can be written from OUTSIDE this VM
            // while the editor is open: reminderAt + recurrence (snooze action,
            // recurrence advance after a fire, mark-as-done from notification). The
            // other fields are owned by this VM's user-input flow and would clobber
            // unsaved drafts if we mirrored them too. Room's Flow only emits on
            // actual row changes, so this is a single small distinct subscription -
            // negligible battery impact, no polling.
            viewModelScope.launch {
                repository.observe(noteId).collect { row ->
                    val n = row?.note ?: return@collect
                    if (_reminderAt.value != n.reminderAt) _reminderAt.value = n.reminderAt
                    val sanitized = n.recurrence?.sanitized()
                    if (_recurrence.value != sanitized) _recurrence.value = sanitized
                    if (_trashed.value != n.trashed) _trashed.value = n.trashed
                    val isCompleted = n.completedAt != null
                    if (_completed.value != isCompleted) _completed.value = isCompleted
                }
            }
        }
    }

    fun setTitle(value: String) {
        if (_title.value == value) return
        _title.value = value
        markDirty()
    }

    fun setBody(value: String) {
        if (_body.value == value) return
        _body.value = value
        markDirty()
    }

    fun toggleFavorite() {
        _favorite.value = !_favorite.value
        markDirty()
    }

    fun setReminder(at: Long?, rule: RecurrenceRule?) {
        val normalized = rule?.sanitized()
        if (_reminderAt.value == at && _recurrence.value == normalized) return
        _reminderAt.value = at
        _recurrence.value = normalized
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

    fun setHeroWithFraming(pictureUri: String, framing: HeroFraming) {
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
        val cleaned = value.filterNot { it == RememberReservedTags.FAVORITE }
        if (_tags.value == cleaned) return
        _tags.value = cleaned
        markDirty()
    }

    fun saveTagsWithColors(tags: List<String>, newColors: Map<String, String>) {
        setTags(tags)
        if (newColors.isNotEmpty()) {
            viewModelScope.launch {
                newColors.forEach { (name, hex) ->
                    repository.tagRepository?.setTagColor(name, hex) ?: themePrefs.setTagColor(name, hex)
                }
            }
        }
    }

    fun editExistingTag(oldName: String, newName: String, colorHex: String?, resetColor: Boolean) {
        viewModelScope.launch {
            val result = repository.tagRepository?.editTag(
                oldName = oldName,
                newName = newName,
                colorHex = colorHex,
                resetColor = resetColor,
            ) ?: return@launch
            val updatedTags = _tags.value.map { tagName ->
                if (tagName.equals(result.oldName, ignoreCase = true)) result.newName else tagName
            }.distinctBy { tagName -> tagName.lowercase() }
            if (_tags.value != updatedTags) {
                _tags.value = updatedTags
                markDirty()
            }
        }
    }

    fun addAttachment(uri: Uri, name: String, mime: String?) {
        viewModelScope.launch {
            persistMutex.withLock {
                val id = loadedId ?: run {
                    val newId = repository.createNote(
                        title = _title.value,
                        body = _body.value,
                        colorIndex = 0,
                        options = currentOptions(),
                    )
                    loadedId = newId
                    syncHasPersistedRow()
                    if (_favorite.value) repository.setFavorite(newId, true)
                    newId
                }
                repository.addAttachment(id, uri.toString(), name, mime)
                _attachments.value = repository.get(id)?.attachments ?: emptyList()
                markDirty()
            }
        }
    }

    fun removeAttachment(attachmentId: Long) {
        viewModelScope.launch {
            persistMutex.withLock {
                repository.removeAttachment(attachmentId)
                val id = loadedId
                _attachments.value = if (id != null) {
                    repository.get(id)?.attachments ?: emptyList()
                } else {
                    _attachments.value.filterNot { it.id == attachmentId }
                }
                markDirty()
            }
        }
    }

    private fun tagsForPersistence(): List<String> {
        val base = _tags.value.filterNot { it == RememberReservedTags.FAVORITE }
        return if (_favorite.value) (base + RememberReservedTags.FAVORITE).distinct()
        else base
    }

    private fun currentOptions() = NoteOptions(
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
    )

    private fun hasNetChanges(): Boolean {
        val id = loadedId
        val t = _title.value
        val b = _body.value
        val opts = currentOptions()
        val favorite = _favorite.value
        if (id == null) {
            return t.isNotBlank() || b.isNotBlank() || _attachments.value.isNotEmpty() || opts.pictureUri != null || opts.tags.isNotEmpty() || opts.reminderAt != null || opts.actions.isNotEmpty() || opts.iconKey != null
        } else {
            val old = originalNote ?: return true
            if (t != old.title) return true
            if (b != old.body) return true
            if (favorite != old.favorite) return true
            if (opts.reminderAt != old.reminderAt) return true
            if (opts.importance != old.importance) return true
            if (opts.visibility != old.visibility) return true
            if (opts.pictureUri != old.pictureUri) return true
            if (opts.pictureHeroFraming != old.pictureHeroFraming) return true
            if (opts.locked != old.locked) return true
            if (opts.iconKey != old.iconKey) return true
            if (opts.actions != old.actions) return true
            if (opts.tags != old.tags) return true
            if (opts.recurrence != old.recurrence) return true
            return false
        }
    }

    /**
     * Persist the in-memory state. Safe to call from ON_STOP, onDispose, or any other
     * lifecycle hook; concurrent calls serialize through [persistMutex] so we never
     * insert the same draft note twice.
     */
    suspend fun saveIfNeeded(untitledName: String): (suspend () -> Unit)? {
        return persistMutex.withLock {
            if (!hasNetChanges()) {
                dirty = false
                return@withLock null
            }
            val titleValue = _title.value
            val bodyValue = _body.value
            val id = loadedId
            val finalTitle = titleValue.ifBlank { untitledName }
            if (id == null) {
                if (!dirty) return@withLock null
                val epochAtWrite = mutationEpoch.get()
                val newId = repository.createNote(finalTitle, bodyValue, 0, currentOptions())
                loadedId = newId
                syncHasPersistedRow()
                if (titleValue.isBlank()) _title.value = finalTitle
                if (_favorite.value) repository.setFavorite(newId, true)
                if (mutationEpoch.get() == epochAtWrite) dirty = false
                
                originalNote = repository.get(newId)?.note
                
                return@withLock {
                    repository.moveToTrash(newId)
                }
            } else {
                if (!dirty) return@withLock null
                val epochAtWrite = mutationEpoch.get()
                repository.updateNote(id, finalTitle, bodyValue, 0, currentOptions())
                if (titleValue.isBlank()) _title.value = finalTitle
                val cur = repository.get(id)?.note
                if (cur != null && cur.favorite != _favorite.value) {
                    repository.setFavorite(id, _favorite.value)
                }
                if (mutationEpoch.get() == epochAtWrite) dirty = false
                
                val old = originalNote
                originalNote = repository.get(id)?.note
                
                if (old != null) {
                    return@withLock {
                        repository.updateNote(
                            id = id,
                            title = old.title,
                            body = old.body,
                            colorIndex = old.colorIndex,
                            options = NoteOptions(
                                reminderAt = old.reminderAt,
                                importance = old.importance,
                                visibility = old.visibility,
                                pictureUri = old.pictureUri,
                                pictureHeroFraming = old.pictureHeroFraming,
                                locked = old.locked,
                                iconKey = old.iconKey,
                                actions = old.actions,
                                tags = old.tags,
                                recurrence = old.recurrence
                            )
                        )
                        repository.setFavorite(id, old.favorite)
                    }
                } else {
                    return@withLock null
                }
            }
        }
    }

    suspend fun trashCurrent() {
        persistMutex.withLock {
            val id = loadedId ?: return@withLock
            repository.moveToTrash(id)
            dirty = false
            _trashed.value = true
            _archived.value = false
        }
    }

    /**
     * Flip the note's done state from inside the editor's bottom bar. Routes through
     * [NoteRepository.markCompleted] / [NoteRepository.markIncomplete] so recurrence
     * is honored - completing a recurring note rolls it forward instead of moving it
     * to Done. The live observer in [init] picks up the new value and updates
     * [completed] automatically; we don't have to mutate it here.
     */
    suspend fun toggleCompleted() {
        val id = loadedId ?: return
        if (_completed.value) {
            repository.markIncomplete(id)
        } else {
            repository.markCompleted(id)
        }
    }

    /**
     * Flip the note to the archive shelf. Unlike [trashCurrent] this is not a destructive
     * operation, so the edit screen stays open and drops into read-only mode via the
     * [archived] state flow.
     */
    suspend fun archiveCurrent(untitledName: String) {
        // Persist any in-flight edits first so the archive snapshot matches what the user sees.
        saveIfNeeded(untitledName)
        persistMutex.withLock {
            val id = loadedId ?: return@withLock
            repository.archiveNote(id)
            _archived.value = true
            _trashed.value = false
            dirty = false
        }
    }

    suspend fun unarchiveCurrent() {
        persistMutex.withLock {
            val id = loadedId ?: return@withLock
            repository.unarchiveNote(id)
            _archived.value = false
            dirty = false
        }
    }

    suspend fun restoreFromTrashCurrent() {
        persistMutex.withLock {
            val id = loadedId ?: return@withLock
            repository.restoreFromTrash(id)
            _trashed.value = false
            dirty = false
        }
    }

    suspend fun fireNotification(context: android.content.Context, untitledName: String) {
        saveIfNeeded(untitledName)
        val id = loadedId ?: return
        val note = repository.get(id)?.note ?: return
        dev.bikram.remember.reminders.ReminderReceiver.showNotification(context, note)
        android.widget.Toast.makeText(
            context,
            context.getString(dev.bikram.remember.R.string.notification_created),
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }

    suspend fun deleteForeverCurrent() {
        persistMutex.withLock {
            val id = loadedId ?: return@withLock
            repository.deleteForever(id)
            dirty = false
        }
    }

    companion object {
        fun factory(
            repository: NoteRepository,
            themePrefs: ThemePrefs,
            noteId: Long?,
            prefillBody: String = "",
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EditNoteViewModel(repository, themePrefs, noteId, prefillBody) as T
        }
    }
}
