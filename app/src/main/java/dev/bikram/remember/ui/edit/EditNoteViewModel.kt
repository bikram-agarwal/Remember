package dev.bikram.remember.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.bikram.remember.data.AppMediaStorage
import dev.bikram.remember.data.NoteOptions
import dev.bikram.remember.data.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the free-text body for the Edit Note screen on top of every persisted field shared with
 * lists in [BaseEditorViewModel]. Only the body payload and the create/update/diff calls that
 * depend on it live here; setters, tag handling, and the note-lifecycle actions are inherited.
 */
@HiltViewModel
class EditNoteViewModel
    @Inject
    constructor(
        repository: NoteRepository,
        appMediaStorage: AppMediaStorage? = null,
        savedStateHandle: SavedStateHandle,
    ) : BaseEditorViewModel(repository, appMediaStorage, savedStateHandle) {
        private val _body = MutableStateFlow(if (noteId == null) prefillBody else "")
        val body: StateFlow<String> = _body.asStateFlow()

        /** True after the initial DB load has populated the state flows (or immediately for a new note). */
        private val _loaded = MutableStateFlow(noteId == null)
        val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

        init {
            if (noteId != null) {
                check(persistence.tryLock()) { "persistence lock must be unlocked at construction" }
                viewModelScope.launch {
                    try {
                        val existing = repository.get(noteId)
                        if (existing != null) {
                            applyLoadedCommon(existing)
                            _body.value = existing.note.body
                        }
                    } finally {
                        // Leave loading when the load finishes: missing row, success, or thrown from get().
                        _loaded.value = true
                        persistence.unlock()
                    }
                }
                startExternalFieldMirror(noteId)
            }
        }

        fun setBody(value: String) {
            if (_body.value == value) return
            _body.value = value
            markDirty()
        }

        override suspend fun persistNewDraftForAttachment(): Long {
            val newId =
                repository.createNote(
                    title = _title.value,
                    body = _body.value,
                    colorIndex = 0,
                    options = currentOptions(),
                )
            loadedId = newId
            syncHasPersistedRow()
            if (_starred.value) repository.setStarred(newId, true)
            return newId
        }

        private fun hasNetChanges(): Boolean {
            val id = loadedId
            val t = _title.value
            val b = _body.value
            val opts = currentOptions()
            val starred = _starred.value
            if (id == null) {
                return t.isNotBlank() || b.isNotBlank() || attachments.value.isNotEmpty() || opts.pictureUri != null || opts.tags.isNotEmpty() || opts.reminderAt != null || opts.actions.isNotEmpty() || opts.iconKey != null
            } else {
                val old = originalNote ?: return true
                if (t != old.title) return true
                if (b != old.body) return true
                if (starred != old.starred) return true
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

        override suspend fun saveIfNeeded(untitledName: String): (suspend () -> Unit)? {
            return persistence.withLock {
                if (!hasNetChanges()) {
                    persistence.clearDirty()
                    return@withLock null
                }
                val titleValue = _title.value
                val bodyValue = _body.value
                val id = loadedId
                val finalTitle = titleValue.ifBlank { untitledName }
                if (id == null) {
                    if (!persistence.isDirty) return@withLock null
                    val epochAtWrite = persistence.currentEpoch()
                    val newId = repository.createNote(finalTitle, bodyValue, 0, currentOptions())
                    loadedId = newId
                    syncHasPersistedRow()
                    if (titleValue.isBlank()) _title.value = finalTitle
                    if (_starred.value) repository.setStarred(newId, true)
                    persistence.clearDirtyIfUnchanged(epochAtWrite)

                    val savedNote = repository.get(newId)?.note
                    originalNote = savedNote
                    if (savedNote != null) {
                        _createdAt.value = savedNote.createdAt
                        _updatedAt.value = savedNote.updatedAt
                    }

                    return@withLock {
                        repository.moveToTrash(newId)
                    }
                } else {
                    if (!persistence.isDirty) return@withLock null
                    val epochAtWrite = persistence.currentEpoch()
                    repository.updateNote(id, finalTitle, bodyValue, 0, currentOptions())
                    if (titleValue.isBlank()) _title.value = finalTitle
                    val cur = repository.get(id)?.note
                    if (cur != null && cur.starred != _starred.value) {
                        repository.setStarred(id, _starred.value)
                    }
                    persistence.clearDirtyIfUnchanged(epochAtWrite)

                    val old = originalNote
                    val savedNote = repository.get(id)?.note
                    originalNote = savedNote
                    if (savedNote != null) {
                        _createdAt.value = savedNote.createdAt
                        _updatedAt.value = savedNote.updatedAt
                    }

                    if (old != null) {
                        return@withLock {
                            repository.updateNote(
                                id = id,
                                title = old.title,
                                body = old.body,
                                colorIndex = old.colorIndex,
                                options =
                                    NoteOptions(
                                        reminderAt = old.reminderAt,
                                        importance = old.importance,
                                        visibility = old.visibility,
                                        pictureUri = old.pictureUri,
                                        pictureHeroFraming = old.pictureHeroFraming,
                                        locked = old.locked,
                                        iconKey = old.iconKey,
                                        actions = old.actions,
                                        tags = old.tags,
                                        recurrence = old.recurrence,
                                    ),
                            )
                            repository.setStarred(id, old.starred)
                        }
                    } else {
                        return@withLock null
                    }
                }
            }
        }
    }
