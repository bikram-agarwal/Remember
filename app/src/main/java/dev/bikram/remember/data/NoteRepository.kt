package dev.bikram.remember.data

import androidx.room.withTransaction
import dev.bikram.remember.di.DefaultDispatcher
import dev.bikram.remember.di.IoDispatcher
import dev.bikram.remember.reminders.ReminderScheduler
import dev.bikram.remember.widget.NotesWidgetUpdater
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
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
    val tagRepository: TagRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val database: RememberDatabase? = null,
    private val notesWidgetUpdater: NotesWidgetUpdater? = null,
    private val appMediaStorage: AppMediaStorage? = null,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    fun observeActive(): Flow<List<NoteWithItems>> = noteDao.observeActive().flowOn(ioDispatcher)

    /** Distinct tag names from non-trashed notes; for suggestion UIs (sheet-scoped collection preferred). */
    fun observeActiveTagSuggestions(): Flow<List<String>> =
        tagRepository?.observeActiveTagSuggestions()?.flowOn(ioDispatcher)
            ?: observeActive()
                .map { notes ->
                    notes
                        .flatMap { it.note.tags }
                        .filterNot { it == RememberReservedTags.FAVORITE }
                        .distinct()
                        .sorted()
                }.flowOn(defaultDispatcher)

    fun observeTrashed(): Flow<List<NoteWithItems>> = noteDao.observeTrashed().flowOn(ioDispatcher)

    fun observeArchived(): Flow<List<NoteWithItems>> = noteDao.observeArchived().flowOn(ioDispatcher)

    fun observe(id: Long): Flow<NoteWithItems?> = noteDao.observe(id).flowOn(ioDispatcher)

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
            flowOf(emptyList())
        } else {
            combine(
                noteDao.searchNotes(fts).flowOn(ioDispatcher),
                noteDao.observeActive().flowOn(ioDispatcher),
            ) { ftsMatches, activeNotes ->
                mergeSearchMatches(ftsMatches, activeNotes, query)
            }
        }.flowOn(defaultDispatcher)
    }

    /** Same FTS prefix match as [searchNotes], but scoped to archived notes only. */
    fun searchArchivedNotes(query: String): Flow<List<NoteWithItems>> {
        val fts = toFtsPrefixQuery(query)
        return if (fts.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                noteDao.searchArchived(fts).flowOn(ioDispatcher),
                noteDao.observeArchived().flowOn(ioDispatcher),
            ) { ftsMatches, archivedNotes ->
                mergeSearchMatches(ftsMatches, archivedNotes, query)
            }
        }.flowOn(defaultDispatcher)
    }

    /** Same FTS prefix match as [searchNotes], but scoped to trashed notes only. */
    fun searchTrashedNotes(query: String): Flow<List<NoteWithItems>> {
        val fts = toFtsPrefixQuery(query)
        return if (fts.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                noteDao.searchTrashed(fts).flowOn(ioDispatcher),
                noteDao.observeTrashed().flowOn(ioDispatcher),
            ) { ftsMatches, trashedNotes ->
                mergeSearchMatches(ftsMatches, trashedNotes, query)
            }
        }.flowOn(defaultDispatcher)
    }

    private fun mergeSearchMatches(
        ftsMatches: List<NoteWithItems>,
        scopedNotes: List<NoteWithItems>,
        query: String,
    ): List<NoteWithItems> {
        val mergedById = LinkedHashMap<Long, NoteWithItems>()
        ftsMatches.forEach { noteWithItems ->
            mergedById[noteWithItems.note.id] = noteWithItems
        }

        val textFilter = NotesFilter(text = query)
        scopedNotes
            .filter { noteWithItems -> textFilter.matches(noteWithItems) }
            .forEach { noteWithItems ->
                mergedById.putIfAbsent(noteWithItems.note.id, noteWithItems)
            }

        return mergedById.values.toList()
    }

    suspend fun createNote(
        title: String,
        body: String,
        colorIndex: Int,
        options: NoteOptions = NoteOptions(),
    ): Long {
        val now = clock()
        val noteId =
            noteDao.insert(
                NoteEntity(
                    kind = NoteKind.NOTE,
                    title = title,
                    body = body,
                    colorIndex = colorIndex,
                    favorite = false,
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
                ),
            )
        tagRepository?.replaceTagsForNote(noteId, options.tags)
        options.reminderAt?.let { scheduler?.schedule(noteId, it) }
        refreshReminderSummaryNotification()
        notesWidgetUpdater?.refreshAll()
        return noteId
    }

    suspend fun createList(
        title: String,
        colorIndex: Int,
        items: List<String>,
        options: NoteOptions = NoteOptions(),
    ): Long {
        val now = clock()
        val id =
            noteDao.insert(
                NoteEntity(
                    kind = NoteKind.LIST,
                    title = title,
                    body = "",
                    colorIndex = colorIndex,
                    favorite = false,
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
                ),
            )
        tagRepository?.replaceTagsForNote(id, options.tags)
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
                },
            )
        }
        options.reminderAt?.let { scheduler?.schedule(id, it) }
        refreshReminderSummaryNotification()
        notesWidgetUpdater?.refreshAll()
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
                completedAt =
                    if (existing.completedAt != null && options.reminderAt != null) {
                        null
                    } else {
                        existing.completedAt
                    },
            ),
        )
        tagRepository?.replaceTagsForNote(id, options.tags)
        rescheduleReminder(id, options.reminderAt)
        refreshReminderSummaryNotification()
        notesWidgetUpdater?.refreshAll()
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
                        completedAt =
                            if (existing.completedAt != null && options.reminderAt != null) {
                                null
                            } else {
                                existing.completedAt
                            },
                    ),
                )
                itemDao.deleteForNote(id)
                persistHierarchy(noteId = id, items = items)
                true
            }
        }
        val didUpdate =
            if (database != null) {
                database.withTransaction { applyUpdates() }
            } else {
                applyUpdates()
            }
        if (didUpdate) tagRepository?.replaceTagsForNote(id, options.tags)
        if (didUpdate) rescheduleReminder(id, options.reminderAt)
        if (didUpdate) refreshNotificationIfActive(id)
        if (didUpdate) refreshReminderSummaryNotification()
        if (didUpdate) notesWidgetUpdater?.refreshAll()
    }

    suspend fun setFavorite(
        id: Long,
        favorite: Boolean,
    ) {
        val row = noteDao.get(id) ?: return
        val baseTags = row.note.tags.filterNot { it == RememberReservedTags.FAVORITE }
        val newTags = if (favorite) (baseTags + RememberReservedTags.FAVORITE).distinct() else baseTags
        val now = clock()
        if (row.note.favorite == favorite && row.note.tags == newTags) return
        noteDao.update(row.note.copy(favorite = favorite, tags = newTags, updatedAt = now))
        notesWidgetUpdater?.refreshAll()
    }

    suspend fun moveToTrash(id: Long) {
        noteDao.setTrashed(id, true, clock())
        scheduler?.cancel(id)
        scheduler?.cancelNotification(id)
        refreshReminderSummaryNotification()
        notesWidgetUpdater?.refreshAll()
    }

    suspend fun moveAllArchivedToTrash() {
        val archivedIds = noteDao.archivedNoteIds()
        if (archivedIds.isEmpty()) return
        val now = clock()
        archivedIds.forEach { archivedId ->
            noteDao.setTrashed(archivedId, true, now)
            scheduler?.cancel(archivedId)
            scheduler?.cancelNotification(archivedId)
        }
        refreshReminderSummaryNotification()
        notesWidgetUpdater?.refreshAll()
    }

    suspend fun restoreFromTrash(id: Long) {
        noteDao.setTrashed(id, false, clock())
        val n = noteDao.get(id)?.note
        n?.reminderAt?.let { scheduler?.schedule(id, it) }
        refreshReminderSummaryNotification()
        notesWidgetUpdater?.refreshAll()
    }

    /**
     * Move to the archive shelf. Mirrors [moveToTrash] for reminder-cancel behaviour:
     * archived notes are considered "put away" so their alarms stop firing until unarchived.
     */
    suspend fun archiveNote(id: Long) {
        noteDao.setArchived(id, true, clock())
        scheduler?.cancel(id)
        scheduler?.cancelNotification(id)
        refreshReminderSummaryNotification()
        notesWidgetUpdater?.refreshAll()
    }

    suspend fun unarchiveNote(id: Long) {
        noteDao.setArchived(id, false, clock())
        val n = noteDao.get(id)?.note
        n?.reminderAt?.let { scheduler?.schedule(id, it) }
        refreshReminderSummaryNotification()
        notesWidgetUpdater?.refreshAll()
    }

    suspend fun deleteForever(id: Long) {
        val deletedNote = noteDao.get(id)
        scheduler?.cancel(id)
        scheduler?.cancelNotification(id)
        noteDao.deleteById(id)
        cleanupUnreferencedMedia(deletedNote?.mediaUris().orEmpty())
        refreshReminderSummaryNotification()
        notesWidgetUpdater?.refreshAll()
    }

    suspend fun emptyTrash() {
        val deletedNotes = noteDao.trashedNoteIds().mapNotNull { trashedId -> noteDao.get(trashedId) }
        deletedNotes.forEach { deletedNote ->
            val trashedId = deletedNote.note.id
            scheduler?.cancel(trashedId)
            scheduler?.cancelNotification(trashedId)
        }
        noteDao.emptyTrash()
        cleanupUnreferencedMedia(deletedNotes.flatMap { deletedNote -> deletedNote.mediaUris() })
        refreshReminderSummaryNotification()
        notesWidgetUpdater?.refreshAll()
    }

    /**
     * Deletes trashed notes whose [NoteEntity.trashedAt] is older than [cutoffMillis].
     * Called by the daily WorkManager sweep to implement the 30-day retention policy.
     * Returns the number of ids that were purged (including those already missing a
     * pending reminder so we don't try to cancel an unscheduled alarm).
     */
    suspend fun autoEmptyTrashOlderThan(cutoffMillis: Long): Int {
        val deletedNotes = noteDao.trashedNoteIdsOlderThan(cutoffMillis).mapNotNull { noteId -> noteDao.get(noteId) }
        deletedNotes.forEach { deletedNote ->
            val noteId = deletedNote.note.id
            scheduler?.cancel(noteId)
            scheduler?.cancelNotification(noteId)
        }
        noteDao.deleteTrashedOlderThan(cutoffMillis)
        cleanupUnreferencedMedia(deletedNotes.flatMap { deletedNote -> deletedNote.mediaUris() })
        refreshReminderSummaryNotification()
        notesWidgetUpdater?.refreshAll()
        return deletedNotes.size
    }

    /**
     * Removes every note (active and trashed), checklist rows, and attachments (Room cascades).
     * Cancels scheduled reminders for each note id first.
     */
    suspend fun deleteAllNotes() {
        val deletedNotes = noteDao.allNoteIds().mapNotNull { noteId -> noteDao.get(noteId) }
        deletedNotes.forEach { deletedNote ->
            val noteId = deletedNote.note.id
            scheduler?.cancel(noteId)
            scheduler?.cancelNotification(noteId)
        }
        noteDao.deleteAllNotes()
        cleanupUnreferencedMedia(deletedNotes.flatMap { deletedNote -> deletedNote.mediaUris() })
        refreshReminderSummaryNotification()
        notesWidgetUpdater?.refreshAll()
    }

    /**
     * Runs [importBlock] only after the notes table has been cleared, inside a single Room
     * transaction when [database] is non-null so a failed import rolls back the delete and
     * leaves existing notes intact. Reminder [PendingIntent]s are reconciled after a successful
     * commit only (never cancel the old schedule before we know the replace succeeded).
     */
    suspend fun restoreNotesFullReplace(importBlock: suspend () -> Int): Int {
        val oldIds = noteDao.allNoteIds().toSet()
        val replacedNotes = oldIds.mapNotNull { noteId -> noteDao.get(noteId) }
        val count =
            if (database != null) {
                database.withTransaction {
                    noteDao.deleteAllNotes()
                    importBlock()
                }
            } else {
                oldIds.forEach { noteId ->
                    scheduler?.cancel(noteId)
                    scheduler?.cancelNotification(noteId)
                }
                noteDao.deleteAllNotes()
                importBlock()
            }
        cleanupUnreferencedMedia(replacedNotes.flatMap { replacedNote -> replacedNote.mediaUris() })
        resyncRemindersAfterMassReplace(oldIds)
        refreshReminderSummaryNotification()
        notesWidgetUpdater?.refreshAll()
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
        val duplicatedHeroUri = appMediaStorage?.copyHeroForDuplicate(note.pictureUri) ?: note.pictureUri
        val optionsWithoutReminder =
            NoteOptions(
                reminderAt = null,
                importance = note.importance,
                visibility = note.visibility,
                pictureUri = duplicatedHeroUri,
                pictureHeroFraming = note.pictureHeroFraming,
                locked = note.locked,
                iconKey = note.iconKey,
                actions = note.actions,
                tags = note.tags,
                recurrence = null,
            )
        val newId =
            when (note.kind) {
                NoteKind.NOTE ->
                    createNote(
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
            val duplicatedAttachmentUri =
                appMediaStorage?.copyAttachmentForDuplicate(
                    noteId = newId,
                    uriString = attachment.uri,
                    displayName = attachment.displayName,
                    mimeType = attachment.mimeType,
                ) ?: attachment.uri
            addAttachment(
                noteId = newId,
                uri = duplicatedAttachmentUri,
                displayName = attachment.displayName,
                mimeType = attachment.mimeType,
            )
        }
        return newId
    }

    suspend fun addAttachment(
        noteId: Long,
        uri: String,
        displayName: String,
        mimeType: String?,
    ): Long {
        val attachmentId =
            attachmentDao.insert(
                NoteAttachmentEntity(
                    noteId = noteId,
                    uri = uri,
                    displayName = displayName,
                    mimeType = mimeType,
                ),
            )
        notesWidgetUpdater?.refreshAll()
        return attachmentId
    }

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
        tagRepository?.replaceTagsForNote(noteId, note.tags)
        if (items.isNotEmpty()) {
            // Backup rows carry stable pre-export ids so parentId pointers can be remapped
            // after Room assigns fresh autogenerated ids. Fall back to preserving input order
            // via sortOrder when the import lacks explicit ids (legacy archives).
            val sorted = items.sortedBy { it.sortOrder }
            persistHierarchy(
                noteId = noteId,
                items =
                    sorted.map { item ->
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
            val newId =
                itemDao.insert(
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

    suspend fun updatePictureUri(
        noteId: Long,
        pictureUri: String?,
    ) {
        val existing = noteDao.get(noteId)?.note ?: return
        noteDao.update(
            existing.copy(
                pictureUri = pictureUri,
                pictureHeroFraming = if (pictureUri == null) null else existing.pictureHeroFraming,
                updatedAt = clock(),
            ),
        )
        notesWidgetUpdater?.refreshAll()
    }

    suspend fun removeAttachment(id: Long) {
        val removedAttachment = attachmentDao.getById(id)
        attachmentDao.deleteById(id)
        cleanupUnreferencedMedia(listOfNotNull(removedAttachment?.uri))
        notesWidgetUpdater?.refreshAll()
    }

    private suspend fun cleanupUnreferencedMedia(mediaUris: List<String>) {
        val storage = appMediaStorage ?: return
        mediaUris
            .distinct()
            .filter { uri -> storage.isAppStoredMediaUri(uri) }
            .forEach { uri ->
                val remainingReferences = noteDao.countPictureUri(uri) + attachmentDao.countByUri(uri)
                if (remainingReferences == 0) {
                    storage.deleteAppStoredMedia(uri)
                }
            }
    }

    private fun NoteWithItems.mediaUris(): List<String> =
        buildList {
            note.pictureUri?.takeIf { uri -> uri.isNotBlank() }?.let { uri -> add(uri) }
            attachments.mapNotNullTo(this) { attachment -> attachment.uri.takeIf { uri -> uri.isNotBlank() } }
        }

    private fun rescheduleReminder(
        id: Long,
        at: Long?,
    ) {
        scheduler?.cancel(id)
        if (at != null) scheduler?.schedule(id, at)
    }

    private suspend fun refreshNotificationIfActive(id: Long) {
        val row = noteDao.get(id) ?: return
        scheduler?.refreshNotificationIfActive(row.note, row.items)
    }

    suspend fun refreshActiveReminderNotifications() {
        val schedulerNonNull = scheduler ?: return
        val reminders = noteDao.activeRemindersUntil(Long.MAX_VALUE)
        reminders.forEach { noteWithItems ->
            schedulerNonNull.refreshNotificationIfActive(noteWithItems.note, noteWithItems.items)
        }
    }

    suspend fun refreshReminderSummaryNotification() {
        val schedulerNonNull = scheduler ?: return
        val now = clock()
        val reminders = reminderSummaryItems(now)
        schedulerNonNull.refreshSummaryNotification(reminders, now)
    }

    suspend fun reminderSummaryItems(now: Long = clock()): List<NoteWithItems> = noteDao.activeRemindersUntil(now + REMINDER_SUMMARY_WINDOW_MILLIS)

    /**
     * Consume the currently due recurring occurrence after the user marks it done. Merely
     * firing the notification must not call this: until completion, the card should keep
     * its past [NoteEntity.reminderAt] and remain in Overdue.
     */
    private suspend fun advanceRecurringReminderAfterCompletion(id: Long) {
        val note = noteDao.get(id)?.note ?: return
        val rule = note.recurrence?.sanitized() ?: return
        val current = note.reminderAt ?: return
        val consumedRule = rule.afterFire()
        val stoppedByCount =
            consumedRule.endKind == RecurrenceEndKind.AFTER_COUNT &&
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
        notesWidgetUpdater?.refreshAll()
    }

    /**
     * Used when the user taps "Mark as done" on a reminder notification action.
     * Recurring notes advance to their next occurrence; one-shot notes enter Done.
     */
    suspend fun clearReminderFromNotificationAction(noteId: Long): Boolean = markCompleted(noteId)

    /**
     * Mark a note done. Behavior depends on whether the note has a live recurrence rule:
     *
     * - **Recurring** (rule still has occurrences): roll [reminderAt] forward via
     *   [advanceRecurringReminderAfterCompletion] and leave [completedAt] null. The note stays active and
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
            // Advance only after explicit completion. The notification fire path leaves
            // the old reminder time in place so the task remains Overdue until handled.
            advanceRecurringReminderAfterCompletion(noteId)
            val after = noteDao.get(noteId)?.note ?: return true
            if (after.reminderAt == null && after.recurrence == null) {
                noteDao.update(after.copy(completedAt = clock(), updatedAt = clock()))
            }
            scheduler?.cancelNotification(noteId)
            refreshReminderSummaryNotification()
            notesWidgetUpdater?.refreshAll()
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
        scheduler?.cancelNotification(noteId)
        refreshReminderSummaryNotification()
        notesWidgetUpdater?.refreshAll()
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
        refreshReminderSummaryNotification()
        notesWidgetUpdater?.refreshAll()
        return true
    }

    companion object {
        /** 30 days in milliseconds -- the retention window for trashed notes. */
        const val TRASH_RETENTION_MILLIS: Long = 30L * 24L * 60L * 60L * 1000L
        private const val REMINDER_SUMMARY_WINDOW_MILLIS = 7L * 24L * 60L * 60L * 1_000L
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
    val sanitised =
        raw
            .replace(Regex("[\"'*:()\\-^]"), " ")
            .trim()
    if (sanitised.isEmpty()) return ""
    return sanitised
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .joinToString(separator = " ") { "$it*" }
}
