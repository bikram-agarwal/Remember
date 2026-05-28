package dev.bikram.remember.data

import androidx.room.withTransaction
import dev.bikram.remember.di.DefaultDispatcher
import dev.bikram.remember.di.IoDispatcher
import dev.bikram.remember.reminders.ReminderScheduler
import dev.bikram.remember.widget.NotesWidgetUpdater
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class NoteOptions(
    val reminderAt: Long? = null,
    val importance: Importance = Importance.DEFAULT,
    val visibility: Visibility = Visibility.DEFAULT,
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

/**
 * Pre-completion snapshot used by Undo to fully reverse a mark-done — including for
 * recurring notes whose [reminderAt]/[recurrence] would otherwise be advanced or
 * consumed by [NoteRepository.markCompleted]. Stored in the snackbar's pending-action
 * record so a subsequent tap on Undo restores the row's exact prior state.
 */
data class NoteCompletionSnapshot(
    val reminderAt: Long?,
    val recurrence: RecurrenceRule?,
)

@Suppress("LargeClass")
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
    /**
     * Long-lived scope used for fire-and-forget post-write bookkeeping (summary
     * notification rebuild, widget refresh). Decoupling these from the calling
     * coroutine lets the UI's StateFlow recompose immediately after the DB write
     * commits, instead of waiting on ~250ms of widget debounce + ~30-60ms of
     * notification builder work that previously blocked Main. Default is provided
     * so unit tests that construct the repository directly don't need to wire
     * Hilt's @ApplicationScope -- production binding always overrides it.
     */
    private val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
) {
    /**
     * Fires the heavy post-write bookkeeping (summary notification rebuild + widget
     * refresh) on [applicationScope] so the calling coroutine can return immediately
     * and the UI's StateFlow can pick up the DB change without queueing behind these
     * launches. Lighter-weight side effects -- alarm cancel/schedule via
     * [ReminderScheduler], and individual DB writes -- stay synchronous on the
     * caller's coroutine because they're cheap and need ordering guarantees with
     * paired operations (e.g. cancel-then-schedule for undo).
     *
     * @param includeSummary set false for write paths that don't change reminder
     *     state (starred toggle, attachment add/remove, picture URI, list item
     *     check toggle) -- the summary notification only reflects pending reminders
     *     so re-querying for those changes is wasted work.
     */
    private fun postWriteBookkeeping(includeSummary: Boolean = true) {
        applicationScope.launch {
            if (includeSummary) refreshReminderSummaryNotification()
            notesWidgetUpdater?.refreshAll()
        }
    }

    fun observeActive(): Flow<List<NoteWithItems>> = noteDao.observeActive().flowOn(ioDispatcher)

    /** Distinct tag names from non-trashed notes; for suggestion UIs (sheet-scoped collection preferred). */
    fun observeActiveTagSuggestions(): Flow<List<String>> =
        tagRepository?.observeActiveTagSuggestions()?.flowOn(ioDispatcher)
            ?: observeActive()
                .map { notes ->
                    notes
                        .flatMap { it.note.tags }
                        .filterNot { RememberReservedTags.isSuggestionReserved(it) }
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
            noteDao.searchNotes(fts).flowOn(ioDispatcher)
        }.flowOn(defaultDispatcher)
    }

    /** Same FTS prefix match as [searchNotes], but scoped to archived notes only. */
    fun searchArchivedNotes(query: String): Flow<List<NoteWithItems>> {
        val fts = toFtsPrefixQuery(query)
        return if (fts.isEmpty()) {
            flowOf(emptyList())
        } else {
            noteDao.searchArchived(fts).flowOn(ioDispatcher)
        }.flowOn(defaultDispatcher)
    }

    /** Same FTS prefix match as [searchNotes], but scoped to trashed notes only. */
    fun searchTrashedNotes(query: String): Flow<List<NoteWithItems>> {
        val fts = toFtsPrefixQuery(query)
        return if (fts.isEmpty()) {
            flowOf(emptyList())
        } else {
            noteDao.searchTrashed(fts).flowOn(ioDispatcher)
        }.flowOn(defaultDispatcher)
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
                    starred = false,
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
                    actionsText = actionsSearchText(options.actions),
                    tags = options.tags,
                    recurrence = options.recurrence?.sanitized(),
                ),
            )
        tagRepository?.replaceTagsForNote(noteId, options.tags)
        options.reminderAt?.let { scheduler?.schedule(noteId, it, options.importance) }
        postWriteBookkeeping()
        return noteId
    }

    suspend fun createList(
        title: String,
        colorIndex: Int,
        items: List<String>,
        options: NoteOptions = NoteOptions(),
    ): Long {
        val now = clock()
        val validItems = items.map { it.trim() }.filter { it.isNotEmpty() }
        val id =
            noteDao.insert(
                NoteEntity(
                    kind = NoteKind.LIST,
                    title = title,
                    body = "",
                    checklistText = checklistSearchText(validItems),
                    colorIndex = colorIndex,
                    starred = false,
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
                    actionsText = actionsSearchText(options.actions),
                    tags = options.tags,
                    recurrence = options.recurrence?.sanitized(),
                ),
            )
        tagRepository?.replaceTagsForNote(id, options.tags)
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
        options.reminderAt?.let { scheduler?.schedule(id, it, options.importance) }
        postWriteBookkeeping()
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
        val oldPictureUri = existing.pictureUri
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
                actionsText = actionsSearchText(options.actions),
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
        rescheduleReminder(id, options.reminderAt, options.importance)
        refreshNotificationIfActive(id)
        if (oldPictureUri != null && oldPictureUri != options.pictureUri) {
            cleanupUnreferencedMedia(listOf(oldPictureUri))
        }
        postWriteBookkeeping()
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
                        checklistText =
                            checklistSearchText(
                                items
                                    .sortedBy { item -> item.sortOrder }
                                    .map { item -> item.text },
                            ),
                        updatedAt = clock(),
                        reminderAt = options.reminderAt,
                        importance = options.importance,
                        visibility = options.visibility,
                        pictureUri = options.pictureUri,
                        pictureHeroFraming = options.pictureHeroFraming,
                        locked = options.locked,
                        iconKey = options.iconKey,
                        actions = options.actions,
                        actionsText = actionsSearchText(options.actions),
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
                updateListChecklistItems(noteId = id, items = items)
                true
            }
        }
        val existingNote = noteDao.get(id)?.note
        val oldPictureUri = existingNote?.pictureUri

        val didUpdate =
            if (database != null) {
                database.withTransaction { applyUpdates() }
            } else {
                applyUpdates()
            }
        if (didUpdate) tagRepository?.replaceTagsForNote(id, options.tags)
        if (didUpdate) rescheduleReminder(id, options.reminderAt, options.importance)
        if (didUpdate) refreshNotificationIfActive(id)
        if (didUpdate) {
            if (oldPictureUri != null && oldPictureUri != options.pictureUri) {
                cleanupUnreferencedMedia(listOf(oldPictureUri))
            }
            postWriteBookkeeping()
        }
    }

    suspend fun setStarred(
        id: Long,
        starred: Boolean,
    ) {
        val row = noteDao.get(id) ?: return
        val baseTags = row.note.tags.filterNot { it == RememberReservedTags.STARRED }
        val newTags = if (starred) (baseTags + RememberReservedTags.STARRED).distinct() else baseTags
        if (row.note.starred == starred && row.note.tags == newTags) return
        noteDao.update(row.note.copy(starred = starred, tags = newTags, updatedAt = clock()))
        postWriteBookkeeping(includeSummary = false)
    }

    suspend fun moveToTrash(id: Long) {
        noteDao.setTrashed(id, true, clock())
        scheduler?.cancel(id)
        scheduler?.cancelNotification(id)
        postWriteBookkeeping()
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
        postWriteBookkeeping()
    }

    suspend fun restoreFromTrash(id: Long) {
        noteDao.setTrashed(id, false, clock())
        val n = noteDao.get(id)?.note
        n?.reminderAt?.let { at -> scheduler?.schedule(id, at, n.importance) }
        postWriteBookkeeping()
    }

    /**
     * Move to the archive shelf. Mirrors [moveToTrash] for reminder-cancel behaviour:
     * archived notes are considered "put away" so their alarms stop firing until unarchived.
     */
    suspend fun archiveNote(id: Long) {
        noteDao.setArchived(id, true, clock())
        scheduler?.cancel(id)
        scheduler?.cancelNotification(id)
        postWriteBookkeeping()
    }

    suspend fun unarchiveNote(id: Long) {
        noteDao.setArchived(id, false, clock())
        val n = noteDao.get(id)?.note
        n?.reminderAt?.let { at -> scheduler?.schedule(id, at, n.importance) }
        postWriteBookkeeping()
    }

    suspend fun deleteForever(id: Long) {
        val deletedNote = noteDao.get(id)
        scheduler?.cancel(id)
        scheduler?.cancelNotification(id)
        noteDao.deleteById(id)
        cleanupUnreferencedMedia(deletedNote?.mediaUris().orEmpty())
        postWriteBookkeeping()
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
        postWriteBookkeeping()
    }

    // ---------------------------------------------------------------------------
    // Bulk operations
    //
    // Each method wraps the DAO writes for every id in a single Room transaction
    // so observers (observeActive / observeArchived / observeTrashed) emit exactly
    // ONCE per bulk action. Without this, sequential per-id writes caused the
    // LazyColumn to remove items one-by-one and the user saw a cascade of fade-out
    // animations even on bulk Archive / Trash. Scheduler cancellations and refresh
    // side-effects run once after the transaction commits.
    // ---------------------------------------------------------------------------

    private suspend fun runInTransaction(block: suspend () -> Unit) {
        if (database != null) database.withTransaction { block() } else block()
    }

    suspend fun archiveNotes(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val now = clock()
        runInTransaction {
            ids.forEach { id -> noteDao.setArchived(id, true, now) }
        }
        ids.forEach { id ->
            scheduler?.cancel(id)
            scheduler?.cancelNotification(id)
        }
        postWriteBookkeeping()
    }

    suspend fun unarchiveNotes(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val now = clock()
        runInTransaction {
            ids.forEach { id -> noteDao.setArchived(id, false, now) }
        }
        // Re-arm reminders after the rows are flipped back to active. Only schedule
        // for notes that still have a reminderAt set; restored rows without one stay
        // alarm-free.
        ids.forEach { id ->
            val note = noteDao.get(id)?.note ?: return@forEach
            note.reminderAt?.let { at -> scheduler?.schedule(id, at, note.importance) }
        }
        postWriteBookkeeping()
    }

    suspend fun moveToTrash(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val now = clock()
        runInTransaction {
            ids.forEach { id -> noteDao.setTrashed(id, true, now) }
        }
        ids.forEach { id ->
            scheduler?.cancel(id)
            scheduler?.cancelNotification(id)
        }
        postWriteBookkeeping()
    }

    suspend fun restoreFromTrash(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val now = clock()
        runInTransaction {
            ids.forEach { id -> noteDao.setTrashed(id, false, now) }
        }
        ids.forEach { id ->
            val note = noteDao.get(id)?.note ?: return@forEach
            note.reminderAt?.let { at -> scheduler?.schedule(id, at, note.importance) }
        }
        postWriteBookkeeping()
    }

    /**
     * Bulk permanent-delete. Captures media URIs before the rows are gone so they can
     * be cleaned up; cancels any pending alarms first because once the row is deleted
     * the scheduler has no id left to look up.
     */
    suspend fun deleteForever(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val deletedNotes = ids.mapNotNull { id -> noteDao.get(id) }
        ids.forEach { id ->
            scheduler?.cancel(id)
            scheduler?.cancelNotification(id)
        }
        runInTransaction {
            ids.forEach { id -> noteDao.deleteById(id) }
        }
        cleanupUnreferencedMedia(deletedNotes.flatMap { deletedNote -> deletedNote.mediaUris() })
        postWriteBookkeeping()
    }

    /**
     * Bulk mark-completed. Returns a per-id snapshot of each row's pre-completion
     * state so the snackbar Undo can fully restore recurring rules without an extra
     * read. Returned map has an entry for every id whose row existed at call time;
     * missing-row ids are silently skipped (consistent with the single-id overload).
     */
    suspend fun markCompleted(ids: Collection<Long>): Map<Long, NoteCompletionSnapshot> {
        if (ids.isEmpty()) return emptyMap()
        val snapshots = mutableMapOf<Long, NoteCompletionSnapshot>()
        runInTransaction {
            ids.forEach { id -> markCompleted(id)?.let { snapshots[id] = it } }
        }
        return snapshots
    }

    suspend fun markIncomplete(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        runInTransaction {
            ids.forEach { id -> markIncomplete(id) }
        }
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
        postWriteBookkeeping()
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
        postWriteBookkeeping()
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
        postWriteBookkeeping()
        return count
    }

    private suspend fun resyncRemindersAfterMassReplace(oldIds: Set<Long>) {
        val schedulerNonNull = scheduler ?: return
        val newIds = noteDao.allNoteIds().toSet()
        (oldIds + newIds).forEach { noteId -> schedulerNonNull.cancel(noteId) }
        newIds.forEach { noteId ->
            val note = noteDao.get(noteId)?.note ?: return@forEach
            if (!note.trashed && note.reminderAt != null) {
                schedulerNonNull.schedule(noteId, note.reminderAt, note.importance)
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
        refreshAttachmentSearchText(noteId)
        postWriteBookkeeping(includeSummary = false)
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
        val noteId =
            noteDao.insert(
                note.copy(
                    checklistText =
                        checklistSearchText(
                            items
                                .sortedBy { item -> item.sortOrder }
                                .map { item -> item.text },
                        ),
                    attachmentText = attachmentSearchText(attachments),
                    actionsText = actionsSearchText(note.actions),
                ),
            )
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
            note.reminderAt?.let { scheduler?.schedule(noteId, it, note.importance) }
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
        val (parents, children) = items.partition { it.parentLocalKey == null }
        val keyToRealId = mutableMapOf<Long, Long>()

        // 1. Insert parents (depth 0)
        parents.forEach { draft ->
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
            if (draft.localKey != 0L) keyToRealId[draft.localKey] = newId
        }

        // 2. Insert children (depth 1)
        children.forEach { draft ->
            val realParentId = draft.parentLocalKey?.let { keyToRealId[it] }
            val resolvedDepth = if (realParentId != null) draft.depth.coerceIn(0, 1) else 0
            val newId =
                itemDao.insert(
                    ChecklistItemEntity(
                        id = 0,
                        noteId = noteId,
                        text = draft.text,
                        checked = draft.checked,
                        sortOrder = draft.sortOrder,
                        parentId = realParentId,
                        depth = resolvedDepth,
                    ),
                )
            if (draft.localKey != 0L) keyToRealId[draft.localKey] = newId
        }
    }

    private suspend fun updateListChecklistItems(
        noteId: Long,
        items: List<PersistableChecklistItem>,
    ) {
        val existingItems = itemDao.itemsFor(noteId)
        val existingById = existingItems.associateBy { it.id }

        // 1. Partition incoming items into parents and children
        val (parents, children) = items.partition { it.parentLocalKey == null }
        val keyToRealId = mutableMapOf<Long, Long>()

        // 2. Process parents (depth 0)
        parents.forEach { draft ->
            val existing = existingById[draft.localKey]
            if (existing != null) {
                itemDao.update(
                    ChecklistItemEntity(
                        id = existing.id,
                        noteId = noteId,
                        text = draft.text,
                        checked = draft.checked,
                        sortOrder = draft.sortOrder,
                        parentId = null,
                        depth = 0,
                    ),
                )
                keyToRealId[draft.localKey] = existing.id
            } else {
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
                keyToRealId[draft.localKey] = newId
            }
        }

        // 3. Process children (depth 1)
        children.forEach { draft ->
            val realParentId = draft.parentLocalKey?.let { keyToRealId[it] }
            val resolvedDepth = if (realParentId != null) draft.depth.coerceIn(0, 1) else 0

            val existing = existingById[draft.localKey]
            if (existing != null) {
                itemDao.update(
                    ChecklistItemEntity(
                        id = existing.id,
                        noteId = noteId,
                        text = draft.text,
                        checked = draft.checked,
                        sortOrder = draft.sortOrder,
                        parentId = realParentId,
                        depth = resolvedDepth,
                    ),
                )
                keyToRealId[draft.localKey] = existing.id
            } else {
                val newId =
                    itemDao.insert(
                        ChecklistItemEntity(
                            id = 0,
                            noteId = noteId,
                            text = draft.text,
                            checked = draft.checked,
                            sortOrder = draft.sortOrder,
                            parentId = realParentId,
                            depth = resolvedDepth,
                        ),
                    )
                keyToRealId[draft.localKey] = newId
            }
        }

        // 4. Delete items that were in the database but are no longer in our saved set
        val savedRealIds = keyToRealId.values.toSet()
        existingItems.forEach { existing ->
            if (existing.id !in savedRealIds) {
                itemDao.deleteById(existing.id)
            }
        }
    }

    suspend fun updatePictureUri(
        noteId: Long,
        pictureUri: String?,
    ) {
        val existing = noteDao.get(noteId)?.note ?: return
        val oldPictureUri = existing.pictureUri
        noteDao.update(
            existing.copy(
                pictureUri = pictureUri,
                pictureHeroFraming = if (pictureUri == null) null else existing.pictureHeroFraming,
                updatedAt = clock(),
            ),
        )
        if (oldPictureUri != null && oldPictureUri != pictureUri) {
            cleanupUnreferencedMedia(listOf(oldPictureUri))
        }
        postWriteBookkeeping(includeSummary = false)
    }

    suspend fun removeAttachment(id: Long) {
        val removedAttachment = attachmentDao.getById(id)
        attachmentDao.deleteById(id)
        removedAttachment?.noteId?.let { noteId -> refreshAttachmentSearchText(noteId) }
        cleanupUnreferencedMedia(listOfNotNull(removedAttachment?.uri))
        postWriteBookkeeping(includeSummary = false)
    }

    private suspend fun refreshAttachmentSearchText(noteId: Long) {
        val existing = noteDao.get(noteId)?.note ?: return
        val attachments = attachmentDao.attachmentsFor(noteId)
        noteDao.update(
            existing.copy(
                attachmentText = attachmentSearchText(attachments),
                updatedAt = clock(),
            ),
        )
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
        importance: Importance,
    ) {
        scheduler?.cancel(id)
        if (at != null) scheduler?.schedule(id, at, importance)
    }

    private suspend fun refreshNotificationIfActive(id: Long) {
        val row = noteDao.get(id) ?: return
        scheduler?.refreshNotificationIfActive(row.note, row.items)
    }

    suspend fun refreshNotificationVisibilityPreview(
        id: Long,
        visibility: Visibility,
    ) {
        val row = noteDao.get(id) ?: return
        scheduler?.refreshNotificationIfActive(row.note.copy(visibility = visibility), row.items)
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

    suspend fun starredWidgetItems(): List<NoteWithItems> = noteDao.activeStarred()

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
        if (nextTime != null) scheduler?.schedule(id, nextTime, note.importance)
    }

    suspend fun toggleItemChecked(item: ChecklistItemEntity) {
        itemDao.update(item.copy(checked = !item.checked))
        postWriteBookkeeping(includeSummary = false)
    }

    /**
     * Used when the user taps "Mark as done" on a reminder notification action.
     * Recurring notes advance to their next occurrence; one-shot notes enter Done.
     */
    suspend fun clearReminderFromNotificationAction(noteId: Long): Boolean = markCompleted(noteId) != null

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
     * Returns the pre-completion [NoteCompletionSnapshot] when the row was written
     * (or null when the note id no longer exists). The snackbar Undo path uses this
     * snapshot to restore the original reminderAt + recurrence -- including for
     * recurring rows whose rule was advanced or consumed in place. Captured from the
     * single [noteDao.get] this method already does, so undo support is free of any
     * extra DB read.
     */
    suspend fun markCompleted(noteId: Long): NoteCompletionSnapshot? {
        val existing = noteDao.get(noteId)?.note ?: return null
        val snapshot =
            NoteCompletionSnapshot(
                reminderAt = existing.reminderAt,
                recurrence = existing.recurrence,
            )
        val sanitized = existing.recurrence?.sanitized()
        if (sanitized != null) {
            // Advance only after explicit completion. The notification fire path leaves
            // the old reminder time in place so the task remains Overdue until handled.
            advanceRecurringReminderAfterCompletion(noteId)
            val after = noteDao.get(noteId)?.note ?: return snapshot
            if (after.reminderAt == null && after.recurrence == null) {
                noteDao.update(after.copy(completedAt = clock(), updatedAt = clock()))
            }
            scheduler?.cancelNotification(noteId)
            postWriteBookkeeping()
            return snapshot
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
        postWriteBookkeeping()
        return snapshot
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
            if (at > clock()) scheduler?.schedule(noteId, at, existing.importance)
        }
        postWriteBookkeeping()
        return true
    }

    /**
     * Reverse a previous [markCompleted] for the given note ids using the snapshots
     * captured before that call. For each id, clears [NoteEntity.completedAt],
     * restores the original [NoteEntity.reminderAt] and [NoteEntity.recurrence],
     * cancels any alarm scheduled by the meanwhile-advanced state, and re-arms
     * the original alarm if its time is still in the future. All DB writes share
     * one Room transaction so the home list reflows once. Bookkeeping (summary,
     * widgets) fires once at the end.
     *
     * Idempotent on missing rows; non-recurring snapshots collapse to the same
     * behavior the previous [markIncomplete] provided, so this method is the
     * preferred restore path for the snackbar Undo regardless of recurrence.
     */
    suspend fun restoreCompletionStates(snapshots: Map<Long, NoteCompletionSnapshot>) {
        if (snapshots.isEmpty()) return
        val applyAll: suspend () -> Unit = {
            snapshots.forEach { (id, snapshot) ->
                val existing = noteDao.get(id)?.note ?: return@forEach
                noteDao.update(
                    existing.copy(
                        completedAt = null,
                        reminderAt = snapshot.reminderAt,
                        recurrence = snapshot.recurrence?.sanitized(),
                        updatedAt = clock(),
                    ),
                )
                // Drop any alarm that may have been armed for the advanced
                // (now-superseded) reminderAt, then re-arm the original if it's
                // still in the future. Past reminders stay past -- they just
                // fall back into Overdue on next sync.
                scheduler?.cancel(id)
                snapshot.reminderAt?.let { at ->
                    if (at > clock()) scheduler?.schedule(id, at, existing.importance)
                }
            }
        }
        if (database != null) database.withTransaction { applyAll() } else applyAll()
        postWriteBookkeeping()
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
