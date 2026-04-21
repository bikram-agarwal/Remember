package dev.bikram.remember.data

import androidx.room.withTransaction
import dev.bikram.remember.reminders.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class NoteOptions(
    val reminderAt: Long? = null,
    val importance: Importance = Importance.DEFAULT,
    val visibility: Visibility = Visibility.PRIVATE,
    val pictureUri: String? = null,
    val pictureHeroFraming: String? = null,
    val locked: Boolean = false,
    val iconKey: String? = null,
    val actions: List<NoteAction> = emptyList(),
    val tags: List<String> = emptyList(),
    val recurrence: RecurrenceRule? = null,
)

/**
 * Save-side representation of a checklist row. Callers use [localKey] to express parent/child
 * linkage between rows that may or may not already exist in the database: persisted rows carry
 * their Room id as [localKey]; drafts assign a unique negative value. The repository remaps these
 * keys to the real auto-generated ids after insert so [parentLocalKey] pointers survive.
 */
data class PersistableChecklistItem(
    val localKey: Long,
    val text: String,
    val checked: Boolean,
    val sortOrder: Double,
    val parentLocalKey: Long? = null,
    val depth: Int = 0,
)

class NoteRepository(
    private val noteDao: NoteDao,
    private val itemDao: ChecklistItemDao,
    private val attachmentDao: AttachmentDao,
    private val scheduler: ReminderScheduler? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val database: RememberDatabase? = null,
) {

    fun observeActive(): Flow<List<NoteWithItems>> = noteDao.observeActive()

    /** Distinct tag names from non-trashed notes; for suggestion UIs (sheet-scoped collection preferred). */
    fun observeActiveTagSuggestions(): Flow<List<String>> =
        observeActive().map { notes ->
            notes
                .flatMap { it.note.tags }
                .filterNot { it == RememberReservedTags.FAVORITE }
                .distinct()
                .sorted()
        }

    fun observeTrashed(): Flow<List<NoteWithItems>> = noteDao.observeTrashed()
    fun observeArchived(): Flow<List<NoteWithItems>> = noteDao.observeArchived()
    fun observe(id: Long): Flow<NoteWithItems?> = noteDao.observe(id)
    suspend fun get(id: Long): NoteWithItems? = noteDao.get(id)

    /**
     * FTS4 search across active notes. Empty / whitespace-only [query] returns an empty flow
     * (callers should fall back to [observeActive] instead). The query is tokenised on
     * whitespace, each term is escaped and turned into a prefix match (`term*`), so typing
     * "buy mil" will match "buy milk" as soon as the user has typed the third letter.
     */
    fun searchNotes(query: String): Flow<List<NoteWithItems>> {
        val fts = toFtsPrefixQuery(query)
        return if (fts.isEmpty()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            noteDao.searchNotes(fts)
        }
    }

    /** Same FTS prefix match as [searchNotes], but scoped to archived notes only. */
    fun searchArchivedNotes(query: String): Flow<List<NoteWithItems>> {
        val fts = toFtsPrefixQuery(query)
        return if (fts.isEmpty()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            noteDao.searchArchived(fts)
        }
    }

    /** Same FTS prefix match as [searchNotes], but scoped to trashed notes only. */
    fun searchTrashedNotes(query: String): Flow<List<NoteWithItems>> {
        val fts = toFtsPrefixQuery(query)
        return if (fts.isEmpty()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            noteDao.searchTrashed(fts)
        }
    }

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
                pictureHeroFraming = options.pictureHeroFraming,
                locked = options.locked,
                iconKey = options.iconKey,
                actions = options.actions,
                tags = options.tags,
                recurrence = options.recurrence?.sanitized(),
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
                pictureHeroFraming = options.pictureHeroFraming,
                locked = options.locked,
                iconKey = options.iconKey,
                actions = options.actions,
                tags = options.tags,
                recurrence = options.recurrence?.sanitized(),
            )
        )
        val validItems = items.map { it.trim() }.filter { it.isNotEmpty() }
        if (validItems.isNotEmpty()) {
            itemDao.insertAll(
                validItems.mapIndexed { index, text ->
                    ChecklistItemEntity(
                        noteId = id,
                        text = text,
                        checked = false,
                        sortOrder = (index + 1).toDouble(),
                        parentId = null,
                        depth = 0,
                    )
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
                pictureHeroFraming = options.pictureHeroFraming,
                locked = options.locked,
                iconKey = options.iconKey,
                actions = options.actions,
                tags = options.tags,
                recurrence = options.recurrence?.sanitized(),
            )
        )
        rescheduleReminder(id, options.reminderAt)
    }

    suspend fun updateList(
        id: Long,
        title: String,
        colorIndex: Int,
        items: List<PersistableChecklistItem>,
        options: NoteOptions,
    ) {
        val applyUpdates: suspend () -> Boolean = {
            val existing = noteDao.get(id)?.note
            if (existing == null) {
                false
            } else {
                noteDao.update(
                    existing.copy(
                        title = title,
                        colorIndex = colorIndex,
                        updatedAt = clock(),
                        reminderAt = options.reminderAt,
                        importance = options.importance,
                        visibility = options.visibility,
                        pictureUri = options.pictureUri,
                        pictureHeroFraming = options.pictureHeroFraming,
                        locked = options.locked,
                        iconKey = options.iconKey,
                        actions = options.actions,
                        tags = options.tags,
                        recurrence = options.recurrence?.sanitized(),
                    )
                )
                itemDao.deleteForNote(id)
                persistHierarchy(noteId = id, items = items)
                true
            }
        }
        val didUpdate = if (database != null) {
            database.withTransaction { applyUpdates() }
        } else {
            applyUpdates()
        }
        if (didUpdate) rescheduleReminder(id, options.reminderAt)
    }

    suspend fun setPinned(id: Long, pinned: Boolean) {
        val row = noteDao.get(id) ?: return
        val baseTags = row.note.tags.filterNot { it == RememberReservedTags.FAVORITE }
        val newTags = if (pinned) (baseTags + RememberReservedTags.FAVORITE).distinct() else baseTags
        val now = clock()
        if (row.note.pinned == pinned && row.note.tags == newTags) return
        noteDao.update(row.note.copy(pinned = pinned, tags = newTags, updatedAt = now))
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

    /**
     * Move to the archive shelf. Mirrors [moveToTrash] for reminder-cancel behaviour:
     * archived notes are considered "put away" so their alarms stop firing until unarchived.
     */
    suspend fun archiveNote(id: Long) {
        noteDao.setArchived(id, true, clock())
        scheduler?.cancel(id)
    }

    suspend fun unarchiveNote(id: Long) {
        noteDao.setArchived(id, false, clock())
        val n = noteDao.get(id)?.note
        n?.reminderAt?.let { scheduler?.schedule(id, it) }
    }

    suspend fun deleteForever(id: Long) {
        scheduler?.cancel(id)
        noteDao.deleteById(id)
    }

    suspend fun emptyTrash() {
        noteDao.trashedNoteIds().forEach { trashedId -> scheduler?.cancel(trashedId) }
        noteDao.emptyTrash()
    }

    /**
     * Deletes trashed notes whose [NoteEntity.trashedAt] is older than [cutoffMillis].
     * Called by the daily WorkManager sweep to implement the 30-day retention policy.
     * Returns the number of ids that were purged (including those already missing a
     * pending reminder so we don't try to cancel an unscheduled alarm).
     */
    suspend fun autoEmptyTrashOlderThan(cutoffMillis: Long): Int {
        val ids = noteDao.trashedNoteIdsOlderThan(cutoffMillis)
        ids.forEach { scheduler?.cancel(it) }
        noteDao.deleteTrashedOlderThan(cutoffMillis)
        return ids.size
    }

    /**
     * Removes every note (active and trashed), checklist rows, and attachments (Room cascades).
     * Cancels scheduled reminders for each note id first.
     */
    suspend fun deleteAllNotes() {
        noteDao.allNoteIds().forEach { noteId -> scheduler?.cancel(noteId) }
        noteDao.deleteAllNotes()
    }

    /**
     * Runs [importBlock] only after the notes table has been cleared, inside a single Room
     * transaction when [database] is non-null so a failed import rolls back the delete and
     * leaves existing notes intact. Reminder [PendingIntent]s are reconciled after a successful
     * commit only (never cancel the old schedule before we know the replace succeeded).
     */
    suspend fun restoreNotesFullReplace(importBlock: suspend () -> Int): Int {
        val oldIds = noteDao.allNoteIds().toSet()
        val count = if (database != null) {
            database.withTransaction {
                noteDao.deleteAllNotes()
                importBlock()
            }
        } else {
            deleteAllNotes()
            importBlock()
        }
        resyncRemindersAfterMassReplace(oldIds)
        return count
    }

    private suspend fun resyncRemindersAfterMassReplace(oldIds: Set<Long>) {
        val schedulerNonNull = scheduler ?: return
        val newIds = noteDao.allNoteIds().toSet()
        (oldIds + newIds).forEach { noteId -> schedulerNonNull.cancel(noteId) }
        newIds.forEach { noteId ->
            val note = noteDao.get(noteId)?.note ?: return@forEach
            if (!note.trashed && note.reminderAt != null) {
                schedulerNonNull.schedule(noteId, note.reminderAt)
            }
        }
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
            pictureHeroFraming = note.pictureHeroFraming,
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
        suppressReminderSchedule: Boolean = false,
    ): Long {
        val noteId = noteDao.insert(note)
        if (items.isNotEmpty()) {
            // Backup rows carry stable pre-export ids so parentId pointers can be remapped
            // after Room assigns fresh autogenerated ids. Fall back to preserving input order
            // via sortOrder when the import lacks explicit ids (legacy archives).
            val sorted = items.sortedBy { it.sortOrder }
            persistHierarchy(
                noteId = noteId,
                items = sorted.map { item ->
                    PersistableChecklistItem(
                        localKey = item.id,
                        text = item.text,
                        checked = item.checked,
                        sortOrder = item.sortOrder,
                        parentLocalKey = item.parentId,
                        depth = item.depth,
                    )
                },
            )
        }
        attachments.forEach { attachment ->
            attachmentDao.insert(
                attachment.copy(id = 0, noteId = noteId),
            )
        }
        if (!suppressReminderSchedule && !note.trashed) {
            note.reminderAt?.let { scheduler?.schedule(noteId, it) }
        }
        return noteId
    }

    /**
     * Writes a flat list of rows that already carry weighted [PersistableChecklistItem.sortOrder]
     * and [PersistableChecklistItem.parentLocalKey] relations. Works in two passes:
     *
     *  1. Insert every row with `parentId = null` so the table is always in a valid state, even
     *     if the caller ordered children before their parents.
     *  2. Re-update children with the freshly minted parent id resolved via [PersistableChecklistItem.localKey].
     *
     * Dangling pointers (children whose parent is missing from [items]) are left as top-level rows.
     */
    private suspend fun persistHierarchy(
        noteId: Long,
        items: List<PersistableChecklistItem>,
    ) {
        if (items.isEmpty()) return
        val keyToRealId = mutableMapOf<Long, Long>()
        // First pass: insert every row flat.
        items.forEach { draft ->
            val newId = itemDao.insert(
                ChecklistItemEntity(
                    id = 0,
                    noteId = noteId,
                    text = draft.text,
                    checked = draft.checked,
                    sortOrder = draft.sortOrder,
                    parentId = null,
                    depth = 0,
                ),
            )
            // localKey 0 means "no stable key"; skip so it does not collide with other drafts.
            if (draft.localKey != 0L) keyToRealId[draft.localKey] = newId
        }
        // Second pass: patch parentId / depth on children.
        items.forEach { draft ->
            val parentKey = draft.parentLocalKey ?: return@forEach
            val realParentId = keyToRealId[parentKey] ?: return@forEach
            val realId = keyToRealId[draft.localKey] ?: return@forEach
            itemDao.update(
                ChecklistItemEntity(
                    id = realId,
                    noteId = noteId,
                    text = draft.text,
                    checked = draft.checked,
                    sortOrder = draft.sortOrder,
                    parentId = realParentId,
                    depth = draft.depth.coerceIn(0, 1),
                ),
            )
        }
    }

    suspend fun updatePictureUri(noteId: Long, pictureUri: String?) {
        val existing = noteDao.get(noteId)?.note ?: return
        noteDao.update(
            existing.copy(
                pictureUri = pictureUri,
                pictureHeroFraming = if (pictureUri == null) null else existing.pictureHeroFraming,
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
        val rule = note.recurrence?.sanitized() ?: return
        val current = note.reminderAt ?: return
        val consumedRule = rule.afterFire()
        val stoppedByCount = consumedRule.endKind == RecurrenceEndKind.AFTER_COUNT &&
            consumedRule.endCount != null &&
            consumedRule.endCount <= 0
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
        return markCompleted(noteId)
    }

    /**
     * Mark a note done. Behavior depends on whether the note has a live recurrence rule:
     *
     * - **Recurring** (rule still has occurrences): roll [reminderAt] forward via
     *   [advanceReminderOnFire] and leave [completedAt] null. The note stays active and
     *   reappears in Today / Upcoming for the next occurrence. This is what the user means
     *   by "I completed this fire of the reminder, but the task itself isn't done."
     * - **Recurring but exhausted** (rule's end condition is consumed): the next-fire
     *   computation returns null, the note has no future, so set [completedAt] = now and
     *   route the note into the Done bucket.
     * - **Non-recurring**: set [completedAt] = now and cancel any pending alarm.
     *
     * Returns true when a row was written.
     */
    suspend fun markCompleted(noteId: Long): Boolean {
        val existing = noteDao.get(noteId)?.note ?: return false
        val sanitized = existing.recurrence?.sanitized()
        if (sanitized != null) {
            // Delegate to advanceReminderOnFire which already knows how to roll forward
            // and clear the rule when exhausted. After it runs, re-read; if reminderAt is
            // still null AND recurrence is null, the rule was exhausted and we should
            // stamp completedAt so the note routes into Done.
            advanceReminderOnFire(noteId)
            val after = noteDao.get(noteId)?.note ?: return true
            if (after.reminderAt == null && after.recurrence == null) {
                noteDao.update(after.copy(completedAt = clock(), updatedAt = clock()))
            }
            return true
        }
        // Non-recurring: a single completion stamp is the whole transition.
        noteDao.update(
            existing.copy(
                completedAt = clock(),
                updatedAt = clock(),
            ),
        )
        scheduler?.cancel(noteId)
        return true
    }

    /**
     * Restore a note from Done back to active. Clears [completedAt]; leaves [reminderAt]
     * alone so the original reminder time (if any) returns. Used by undo on swipe-done.
     */
    suspend fun markIncomplete(noteId: Long): Boolean {
        val existing = noteDao.get(noteId)?.note ?: return false
        if (existing.completedAt == null) return false
        noteDao.update(
            existing.copy(
                completedAt = null,
                updatedAt = clock(),
            ),
        )
        // Re-arm the alarm only if the saved reminder is still in the future. A past
        // reminder time stays past (the note will land in Overdue next sync) - we
        // intentionally don't bump it forward since the user was undoing, not snoozing.
        existing.reminderAt?.let { at ->
            if (at > clock()) scheduler?.schedule(noteId, at)
        }
        return true
    }

    companion object {
        /** 30 days in milliseconds -- the retention window for trashed notes. */
        const val TRASH_RETENTION_MILLIS: Long = 30L * 24L * 60L * 60L * 1000L
    }
}

/**
 * Tokenise [raw] into an FTS4-safe query string that performs prefix matching on every term.
 * Strips characters FTS treats as syntax (quotes, parens, columns, operators) to avoid
 * accidentally activating boolean operators when the user is just typing a search. Returns
 * an empty string when there's nothing matchable left (caller substitutes an empty result).
 */
internal fun toFtsPrefixQuery(raw: String): String {
    if (raw.isBlank()) return ""
    val sanitised = raw
        .replace(Regex("[\"'*:()\\-^]"), " ")
        .trim()
    if (sanitised.isEmpty()) return ""
    return sanitised
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .joinToString(separator = " ") { "$it*" }
}
