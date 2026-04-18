package dev.bikram.remember.data

import dev.bikram.remember.reminders.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class NoteOptions(
    val reminderAt: Long? = null,
    val importance: Importance = Importance.DEFAULT,
    val visibility: Visibility = Visibility.PRIVATE,
    val pictureUri: String? = null,
    val locked: Boolean = false,
    val iconKey: String? = null,
    val actions: List<NoteAction> = emptyList(),
    val tags: List<String> = emptyList(),
    val recurrence: RecurrenceRule? = null,
)

class NoteRepository(
    private val noteDao: NoteDao,
    private val itemDao: ChecklistItemDao,
    private val attachmentDao: AttachmentDao,
    private val scheduler: ReminderScheduler? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun observeActive(): Flow<List<NoteWithItems>> = noteDao.observeActive()

    /** Distinct tag names from non-trashed notes; for suggestion UIs (sheet-scoped collection preferred). */
    fun observeActiveTagSuggestions(): Flow<List<String>> =
        observeActive().map { notes ->
            notes.flatMap { it.note.tags }.distinct().sorted()
        }

    fun observeTrashed(): Flow<List<NoteWithItems>> = noteDao.observeTrashed()
    fun observe(id: Long): Flow<NoteWithItems?> = noteDao.observe(id)
    suspend fun get(id: Long): NoteWithItems? = noteDao.get(id)

    suspend fun createNote(
        title: String,
        body: String,
        colorIndex: Int,
        options: NoteOptions = NoteOptions(),
    ): Long {
        val now = clock()
        return noteDao.insert(
            NoteEntity(
                kind = NoteKind.NOTE,
                title = title,
                body = body,
                colorIndex = colorIndex,
                pinned = false,
                trashed = false,
                createdAt = now,
                updatedAt = now,
                reminderAt = options.reminderAt,
                importance = options.importance,
                visibility = options.visibility,
                pictureUri = options.pictureUri,
                locked = options.locked,
                iconKey = options.iconKey,
                actions = options.actions,
                tags = options.tags,
                recurrence = options.recurrence,
            )
        ).also { id -> options.reminderAt?.let { scheduler?.schedule(id, it) } }
    }

    suspend fun createList(
        title: String,
        colorIndex: Int,
        items: List<String>,
        options: NoteOptions = NoteOptions(),
    ): Long {
        val now = clock()
        val id = noteDao.insert(
            NoteEntity(
                kind = NoteKind.LIST,
                title = title,
                body = "",
                colorIndex = colorIndex,
                pinned = false,
                trashed = false,
                createdAt = now,
                updatedAt = now,
                reminderAt = options.reminderAt,
                importance = options.importance,
                visibility = options.visibility,
                pictureUri = options.pictureUri,
                locked = options.locked,
                iconKey = options.iconKey,
                actions = options.actions,
                tags = options.tags,
                recurrence = options.recurrence,
            )
        )
        if (items.isNotEmpty()) {
            itemDao.insertAll(
                items.mapIndexedNotNull { index, text ->
                    val trimmed = text.trim()
                    if (trimmed.isEmpty()) null
                    else ChecklistItemEntity(noteId = id, text = trimmed, checked = false, position = index)
                }
            )
        }
        options.reminderAt?.let { scheduler?.schedule(id, it) }
        return id
    }

    suspend fun updateNote(
        id: Long,
        title: String,
        body: String,
        colorIndex: Int,
        options: NoteOptions,
    ) {
        val existing = noteDao.get(id)?.note ?: return
        noteDao.update(
            existing.copy(
                title = title,
                body = body,
                colorIndex = colorIndex,
                updatedAt = clock(),
                reminderAt = options.reminderAt,
                importance = options.importance,
                visibility = options.visibility,
                pictureUri = options.pictureUri,
                locked = options.locked,
                iconKey = options.iconKey,
                actions = options.actions,
                tags = options.tags,
                recurrence = options.recurrence,
            )
        )
        rescheduleReminder(id, options.reminderAt)
    }

    suspend fun updateList(
        id: Long,
        title: String,
        colorIndex: Int,
        items: List<ChecklistItemEntity>,
        options: NoteOptions,
    ) {
        val existing = noteDao.get(id)?.note ?: return
        noteDao.update(
            existing.copy(
                title = title,
                colorIndex = colorIndex,
                updatedAt = clock(),
                reminderAt = options.reminderAt,
                importance = options.importance,
                visibility = options.visibility,
                pictureUri = options.pictureUri,
                locked = options.locked,
                iconKey = options.iconKey,
                actions = options.actions,
                tags = options.tags,
                recurrence = options.recurrence,
            )
        )
        itemDao.deleteForNote(id)
        if (items.isNotEmpty()) {
            itemDao.insertAll(
                items.mapIndexed { index, item ->
                    item.copy(id = 0, noteId = id, position = index)
                }
            )
        }
        rescheduleReminder(id, options.reminderAt)
    }

    suspend fun setPinned(id: Long, pinned: Boolean) {
        noteDao.setPinned(id, pinned, clock())
    }

    suspend fun moveToTrash(id: Long) {
        noteDao.setTrashed(id, true, clock())
        scheduler?.cancel(id)
    }

    suspend fun restoreFromTrash(id: Long) {
        noteDao.setTrashed(id, false, clock())
        val n = noteDao.get(id)?.note
        n?.reminderAt?.let { scheduler?.schedule(id, it) }
    }

    suspend fun deleteForever(id: Long) {
        scheduler?.cancel(id)
        noteDao.deleteById(id)
    }

    suspend fun emptyTrash() = noteDao.emptyTrash()

    /**
     * Removes every note (active and trashed), checklist rows, and attachments (Room cascades).
     * Cancels scheduled reminders for each note id first.
     */
    suspend fun deleteAllNotes() {
        noteDao.allNoteIds().forEach { noteId -> scheduler?.cancel(noteId) }
        noteDao.deleteAllNotes()
    }

    /**
     * Copies the note or list into a new row. Reminders are not copied on the duplicate.
     */
    suspend fun duplicateNote(id: Long): Long? {
        val existing = get(id) ?: return null
        val note = existing.note
        val optionsWithoutReminder = NoteOptions(
            reminderAt = null,
            importance = note.importance,
            visibility = note.visibility,
            pictureUri = note.pictureUri,
            locked = note.locked,
            iconKey = note.iconKey,
            actions = note.actions,
            tags = note.tags,
            recurrence = null,
        )
        val newId = when (note.kind) {
            NoteKind.NOTE -> createNote(
                title = note.title,
                body = note.body,
                colorIndex = note.colorIndex,
                options = optionsWithoutReminder,
            )
            NoteKind.LIST -> {
                val texts = existing.items.map { it.text }
                createList(
                    title = note.title,
                    colorIndex = note.colorIndex,
                    items = texts,
                    options = optionsWithoutReminder,
                )
            }
        }
        existing.attachments.forEach { attachment ->
            addAttachment(
                noteId = newId,
                uri = attachment.uri,
                displayName = attachment.displayName,
                mimeType = attachment.mimeType,
            )
        }
        return newId
    }

    suspend fun addAttachment(noteId: Long, uri: String, displayName: String, mimeType: String?): Long =
        attachmentDao.insert(
            NoteAttachmentEntity(
                noteId = noteId,
                uri = uri,
                displayName = displayName,
                mimeType = mimeType,
            )
        )

    /**
     * Inserts a note or list with checklist rows and attachments.
     * When [note].id is non-zero, Room uses that id (used after [deleteAllNotes] for full restore).
     */
    suspend fun importNoteWithChildren(
        note: NoteEntity,
        items: List<ChecklistItemEntity>,
        attachments: List<NoteAttachmentEntity>,
    ): Long {
        val noteId = noteDao.insert(note)
        if (items.isNotEmpty()) {
            itemDao.insertAll(
                items
                    .sortedBy { it.position }
                    .map { item ->
                        item.copy(id = 0, noteId = noteId)
                    },
            )
        }
        attachments.forEach { attachment ->
            attachmentDao.insert(
                attachment.copy(id = 0, noteId = noteId),
            )
        }
        note.reminderAt?.let { scheduler?.schedule(noteId, it) }
        return noteId
    }

    suspend fun updatePictureUri(noteId: Long, pictureUri: String?) {
        val existing = noteDao.get(noteId)?.note ?: return
        noteDao.update(
            existing.copy(
                pictureUri = pictureUri,
                updatedAt = clock(),
            ),
        )
    }

    suspend fun removeAttachment(id: Long) {
        attachmentDao.deleteById(id)
    }

    private fun rescheduleReminder(id: Long, at: Long?) {
        scheduler?.cancel(id)
        if (at != null) scheduler?.schedule(id, at)
    }

    /**
     * Called from ReminderReceiver after a reminder fires. If the note has a recurrence rule,
     * computes the next occurrence, writes it back to the note, and schedules the next alarm.
     * Clears the reminder when the rule has exhausted its end condition.
     */
    suspend fun advanceReminderOnFire(id: Long) {
        val note = noteDao.get(id)?.note ?: return
        val rule = note.recurrence ?: return
        val current = note.reminderAt ?: return
        val consumedRule = rule.afterFire()
        val stoppedByCount = consumedRule.endKind == RecurrenceEndKind.AFTER_COUNT &&
            (consumedRule.endCount ?: 0) <= 0
        val nextTime = if (stoppedByCount) null else consumedRule.nextAfter(current)
        val nextRule = if (stoppedByCount || nextTime == null) null else consumedRule
        noteDao.update(
            note.copy(
                reminderAt = nextTime,
                recurrence = nextRule,
                updatedAt = clock(),
            ),
        )
        if (nextTime != null) scheduler?.schedule(id, nextTime)
    }

    suspend fun toggleItemChecked(item: ChecklistItemEntity) {
        itemDao.update(item.copy(checked = !item.checked))
    }

    /**
     * Used when the user taps "Mark as done" on a reminder notification action.
     * Stops future alarms for this note by clearing reminder and recurrence.
     */
    suspend fun clearReminderFromNotificationAction(noteId: Long): Boolean {
        val existing = noteDao.get(noteId)?.note ?: return false
        noteDao.update(
            existing.copy(
                reminderAt = null,
                recurrence = null,
                updatedAt = clock(),
            ),
        )
        scheduler?.cancel(noteId)
        return true
    }
}
