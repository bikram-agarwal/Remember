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

    private val _pinned = MutableStateFlow(false)
    val pinned: StateFlow<Boolean> = _pinned.asStateFlow()

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

    init {
        if (noteId != null) {
            // Lock the persist mutex synchronously *before* launching the load. Otherwise a
            // saveIfNeeded() / addAttachment() / removeAttachment() that wins the dispatcher
            // race could observe the default StateFlow values (empty title/body, etc.) and
            // overwrite the real note in the database while the load is still in flight.
            check(persistMutex.tryLock()) { "persistMutex must be unlocked at construction" }
            viewModelScope.launch {
                try {
                    val existing = repository.get(noteId)
                    if (existing != null) {
                        val n = existing.note
                        _title.value = n.title
                        _body.value = n.body
                        _pinned.value = n.pinned || n.tags.contains(RememberReservedTags.FAVORITE)
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
                    }
                } finally {
                    // Leave loading when the load finishes: missing row, success, or thrown from get().
                    _loaded.value = true
                    persistMutex.unlock()
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

    fun togglePin() {
        _pinned.value = !_pinned.value
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
                newColors.forEach { (name, hex) -> themePrefs.setTagColor(name, hex) }
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
                    if (_pinned.value) repository.setPinned(newId, true)
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
        return if (_pinned.value) (base + RememberReservedTags.FAVORITE).distinct()
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

    /**
     * Persist the in-memory state. Safe to call from ON_STOP, onDispose, or any other
     * lifecycle hook; concurrent calls serialize through [persistMutex] so we never
     * insert the same draft note twice.
     */
    suspend fun saveIfNeeded(): Boolean {
        return persistMutex.withLock {
            val titleValue = _title.value
            val bodyValue = _body.value
            val id = loadedId
            val empty = titleValue.isBlank() && bodyValue.isBlank()
            if (id == null) {
                if (empty) return@withLock false
                val epochAtWrite = mutationEpoch.get()
                val newId = repository.createNote(titleValue, bodyValue, 0, currentOptions())
                loadedId = newId
                if (_pinned.value) repository.setPinned(newId, true)
                if (mutationEpoch.get() == epochAtWrite) dirty = false
                true
            } else {
                if (!dirty) return@withLock false
                val epochAtWrite = mutationEpoch.get()
                repository.updateNote(id, titleValue, bodyValue, 0, currentOptions())
                val cur = repository.get(id)?.note
                if (cur != null && cur.pinned != _pinned.value) {
                    repository.setPinned(id, _pinned.value)
                }
                if (mutationEpoch.get() == epochAtWrite) dirty = false
                true
            }
        }
    }

    suspend fun trashCurrent() {
        persistMutex.withLock {
            val id = loadedId ?: return@withLock
            repository.moveToTrash(id)
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
