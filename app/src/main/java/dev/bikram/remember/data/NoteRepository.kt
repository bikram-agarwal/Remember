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
    val reminders: List<NoteReminder> = emptyList(),
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
    val details: String = "",
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
    val reminders: List<NoteReminder> = emptyList(),
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

    fun resolveUpdatedReminders(
        existingNote: NoteEntity?,
        options: NoteOptions,
    ): List<NoteReminder> {
        if (options.reminders.isNotEmpty()) {
            return options.reminders.limitedToReminderSlots()
        }
        val optionPrimaryAt = options.reminderAt ?: return emptyList()
        val existingReminders = existingNote?.reminders?.limitedToReminderSlots() ?: emptyList()
        if (existingReminders.isNotEmpty()) {
            val soonest = existingReminders.minByOrNull { it.reminderAt }
            return existingReminders.map { reminder ->
                if (reminder == soonest) {
                    NoteReminder(optionPrimaryAt, options.recurrence)
                } else {
                    reminder
                }
            }
        }
        return listOf(NoteReminder(optionPrimaryAt, options.recurrence)).limitedToReminderSlots()
    }

    suspend fun createNote(
        title: String,
        body: String,
        colorIndex: Int,
        options: NoteOptions = NoteOptions(),
    ): Long {
        val now = clock()
        val resolvedReminders = resolveUpdatedReminders(null, options)
        val unsyncedNote =
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
                reminders = resolvedReminders,
            )
        val noteEntity = unsyncedNote.withSyncedPrimaryReminder()
        val noteId = noteDao.insert(noteEntity)
        tagRepository?.replaceTagsForNote(noteId, options.tags)
        if (noteEntity.reminderAt != null) {
            val createdNote = noteDao.get(noteId)?.note
            if (createdNote != null) {
                scheduler?.scheduleOrShow(createdNote, emptyList())
            }
        }
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
        val resolvedReminders = resolveUpdatedReminders(null, options)
        val unsyncedNote =
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
                reminders = resolvedReminders,
            )
        val noteEntity = unsyncedNote.withSyncedPrimaryReminder()
        val id = noteDao.insert(noteEntity)
        tagRepository?.replaceTagsForNote(id, options.tags)
        if (validItems.isNotEmpty()) {
            itemDao.insertAll(
                validItems.mapIndexed { index, text ->
                    ChecklistItemEntity(
                        noteId = id,
                        text = text,
                        details = "",
                        checked = false,
                        sortOrder = (index + 1).toDouble(),
                        parentId = null,
                        depth = 0,
                    )
                },
            )
        }
        if (noteEntity.reminderAt != null) {
            val createdNoteWithItems = noteDao.get(id)
            if (createdNoteWithItems != null) {
                scheduler?.scheduleOrShow(createdNoteWithItems.note, createdNoteWithItems.items)
            }
        }
        postWriteBookkeeping()
        return id
    }

    suspend fun createListWithItems(
        title: String,
        colorIndex: Int,
        items: List<PersistableChecklistItem>,
        options: NoteOptions = NoteOptions(),
    ): Long {
        val now = clock()
        val validItems = items.filter { item -> item.text.isNotBlank() || item.details.isNotBlank() }
        val resolvedReminders = resolveUpdatedReminders(null, options)
        val unsyncedNote =
            NoteEntity(
                kind = NoteKind.LIST,
                title = title,
                body = "",
                checklistText =
                    checklistSearchText(
                        validItems
                            .sortedBy { item -> item.sortOrder }
                            .flatMap { item -> listOf(item.text, item.details) },
                    ),
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
                reminders = resolvedReminders,
            )
        val noteEntity = unsyncedNote.withSyncedPrimaryReminder()
        val id = noteDao.insert(noteEntity)
        tagRepository?.replaceTagsForNote(id, options.tags)
        persistHierarchy(noteId = id, items = validItems)
        if (noteEntity.reminderAt != null) {
            val createdNoteWithItems = noteDao.get(id)
            if (createdNoteWithItems != null) {
                scheduler?.scheduleOrShow(createdNoteWithItems.note, createdNoteWithItems.items)
            }
        }
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
        val resolvedReminders = resolveUpdatedReminders(existing, options)
        val unsyncedNote =
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
                reminders = resolvedReminders,
                completedAt =
                    if (existing.completedAt != null && (options.reminderAt != null || resolvedReminders.isNotEmpty())) {
                        null
                    } else {
                        existing.completedAt
                    },
            )
        val noteEntity = unsyncedNote.withSyncedPrimaryReminder()
        noteDao.update(noteEntity)
        tagRepository?.replaceTagsForNote(id, options.tags)
        rescheduleReminder(id, noteEntity.reminderAt)
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
                val resolvedReminders = resolveUpdatedReminders(existing, options)
                val unsyncedNote =
                    existing.copy(
                        title = title,
                        colorIndex = colorIndex,
                        checklistText =
                            checklistSearchText(
                                items
                                    .sortedBy { item -> item.sortOrder }
                                    .flatMap { item -> listOf(item.text, item.details) },
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
                        reminders = resolvedReminders,
                        completedAt =
                            if (existing.completedAt != null && (options.reminderAt != null || resolvedReminders.isNotEmpty())) {
                                null
                            } else {
                                existing.completedAt
                            },
                    )
                val noteEntity = unsyncedNote.withSyncedPrimaryReminder()
                noteDao.update(noteEntity)
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
        if (didUpdate) {
            val noteEntity = noteDao.get(id)?.note
            rescheduleReminder(id, noteEntity?.reminderAt)
        }
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

    /**
     * Pins or unpins [id]. Pinning only affects placement on Home (the top "Pinned" section),
     * so - like [setStarred] - this skips the reminder-summary refresh.
     *
     * Deliberately does not touch [NoteEntity.updatedAt]: pinning is a view concern, and
     * bumping the modified timestamp would silently reshuffle the list under a
     * "sort by last modified" user, which is exactly the confusion pinning is meant to avoid.
     */
    suspend fun setPinned(
        id: Long,
        pinned: Boolean,
    ) {
        val row = noteDao.get(id) ?: return
        if (row.note.pinned == pinned) return
        noteDao.update(row.note.copy(pinnedAt = if (pinned) clock() else null))
        postWriteBookkeeping(includeSummary = false)
    }

    suspend fun moveToTrash(id: Long) {
        noteDao.setTrashed(id, true, clock())
        scheduler?.cancel(id)
        scheduler?.cancelNotification(id)
        postWriteBookkeeping()
    }

    suspend fun snoozeSoonestReminder(
        noteId: Long,
        snoozedUntil: Long,
    ): Boolean {
        val noteWithItems = noteDao.get(noteId) ?: return false
        val note = noteWithItems.note
        if (note.trashed || note.archived || note.completedAt != null) return false
        val activeReminders = note.getActiveReminders()
        val soonestReminder = activeReminders.minByOrNull { reminder -> reminder.reminderAt }
        val updatedReminders =
            if (soonestReminder != null) {
                activeReminders.map { reminder ->
                    if (reminder == soonestReminder) {
                        reminder.copy(
                            reminderAt = snoozedUntil,
                            originalReminderAt = reminder.originalReminderAt ?: reminder.reminderAt,
                        )
                    } else {
                        reminder
                    }
                }
            } else {
                listOf(
                    NoteReminder(
                        reminderAt = snoozedUntil,
                        recurrence = note.recurrence,
                    ),
                )
            }
        val options =
            NoteOptions(
                reminderAt = snoozedUntil,
                importance = note.importance,
                visibility = note.visibility,
                pictureUri = note.pictureUri,
                pictureHeroFraming = note.pictureHeroFraming,
                locked = note.locked,
                iconKey = note.iconKey,
                actions = note.actions,
                tags = note.tags,
                recurrence = note.recurrence,
                reminders = updatedReminders,
            )
        if (note.kind == NoteKind.NOTE) {
            updateNote(note.id, note.title, note.body, note.colorIndex, options)
        } else {
            val persistableItems =
                noteWithItems.items.map { item ->
                    PersistableChecklistItem(
                        localKey = item.id,
                        text = item.text,
                        details = item.details,
                        checked = item.checked,
                        sortOrder = item.sortOrder,
                        parentLocalKey = item.parentId,
                        depth = item.depth,
                    )
                }
            updateList(note.id, note.title, note.colorIndex, persistableItems, options)
        }
        return true
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
        val noteWithItems = noteDao.get(id)
        if (noteWithItems != null) {
            val restoredNote = noteWithItems.note
            if (restoredNote.reminderAt != null) {
                scheduler?.scheduleOrShow(restoredNote, noteWithItems.items)
            }
        }
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
        val noteWithItems = noteDao.get(id)
        if (noteWithItems != null) {
            val unarchivedNote = noteWithItems.note
            if (unarchivedNote.reminderAt != null) {
                scheduler?.scheduleOrShow(unarchivedNote, noteWithItems.items)
            }
        }
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

    /**
     * Bulk pin / unpin. One transaction so the Pinned section reflows in a single emission and
     * the cards animate as one move instead of a cascade. Returns the ids whose state actually
     * changed, which is what the Undo path needs so it does not clear pins the user already had.
     */
    suspend fun setPinned(
        ids: Collection<Long>,
        pinned: Boolean,
    ): Set<Long> {
        if (ids.isEmpty()) return emptySet()
        val now = clock()
        val changed = mutableSetOf<Long>()
        runInTransaction {
            ids.forEach { id ->
                val note = noteDao.get(id)?.note ?: return@forEach
                if (note.pinned == pinned) return@forEach
                noteDao.update(note.copy(pinnedAt = if (pinned) now else null))
                changed += id
            }
        }
        if (changed.isNotEmpty()) postWriteBookkeeping(includeSummary = false)
        return changed
    }

    /**
     * Bulk star / unstar. One transaction so the starred flag + reserved tag stay in lockstep
     * across the selection, and Undo can reverse only the rows that actually changed.
     */
    suspend fun setStarred(
        ids: Collection<Long>,
        starred: Boolean,
    ): Set<Long> {
        if (ids.isEmpty()) return emptySet()
        val now = clock()
        val changed = mutableSetOf<Long>()
        runInTransaction {
            ids.forEach { id ->
                val note = noteDao.get(id)?.note ?: return@forEach
                val baseTags = note.tags.filterNot { it == RememberReservedTags.STARRED }
                val newTags = if (starred) (baseTags + RememberReservedTags.STARRED).distinct() else baseTags
                if (note.starred == starred && note.tags == newTags) return@forEach
                noteDao.update(note.copy(starred = starred, tags = newTags, updatedAt = now))
                changed += id
            }
        }
        if (changed.isNotEmpty()) postWriteBookkeeping(includeSummary = false)
        return changed
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
            val noteWithItems = noteDao.get(id) ?: return@forEach
            val note = noteWithItems.note
            if (note.reminderAt != null) {
                scheduler?.scheduleOrShow(note, noteWithItems.items)
            }
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
            val noteWithItems = noteDao.get(id) ?: return@forEach
            val note = noteWithItems.note
            if (note.reminderAt != null) {
                scheduler?.scheduleOrShow(note, noteWithItems.items)
            }
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

    suspend fun markIncomplete(
        ids: Collection<Long>,
        snapshots: Map<Long, NoteCompletionSnapshot> = emptyMap(),
    ) {
        if (ids.isEmpty()) return
        runInTransaction {
            ids.forEach { id -> markIncomplete(id, snapshots[id]) }
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

    @Suppress("ktlint:standard:function-expression-body")
    internal suspend fun <Result> runImportTransaction(importBlock: suspend () -> Result): Result {
        return if (database != null) {
            database.withTransaction {
                importBlock()
            }
        } else {
            importBlock()
        }
    }

    internal suspend fun reconcileImportedNotes(importedNoteIds: Collection<Long>) {
        val schedulerNonNull = scheduler
        if (schedulerNonNull != null) {
            importedNoteIds.distinct().forEach { noteId ->
                val noteWithItems = noteDao.get(noteId)
                if (noteWithItems == null) {
                    schedulerNonNull.cancel(noteId)
                } else {
                    val note = noteWithItems.note
                    if (note.trashed || note.archived || note.completedAt != null) {
                        schedulerNonNull.cancel(noteId)
                    } else {
                        schedulerNonNull.scheduleOrShow(note, noteWithItems.items)
                    }
                }
            }
        }
        postWriteBookkeeping()
    }

    private suspend fun resyncRemindersAfterMassReplace(oldIds: Set<Long>) {
        val schedulerNonNull = scheduler ?: return
        val newIds = noteDao.allNoteIds().toSet()
        (oldIds + newIds).forEach { noteId -> schedulerNonNull.cancel(noteId) }
        newIds.forEach { noteId ->
            val noteWithItems = noteDao.get(noteId) ?: return@forEach
            val note = noteWithItems.note
            if (!note.trashed && !note.archived && note.completedAt == null && note.reminderAt != null) {
                schedulerNonNull.scheduleOrShow(note, noteWithItems.items)
            }
        }
    }

    /**
     * Copies the note or list into a new row. Reminders are not copied on the duplicate.
     * Neither are the starred and pinned flags (both go through the create path, which
     * defaults them off) - two identical cards side by side at the top of Home is not what
     * "duplicate" should mean.
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
                    val persistableItems =
                        existing.items.map { item ->
                            PersistableChecklistItem(
                                localKey = item.id,
                                text = item.text,
                                details = item.details,
                                checked = item.checked,
                                sortOrder = item.sortOrder,
                                parentLocalKey = item.parentId,
                                depth = item.depth,
                            )
                        }
                    createListWithItems(
                        title = note.title,
                        colorIndex = note.colorIndex,
                        items = persistableItems,
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
        val noteWithSynced = note.withSyncedPrimaryReminder()
        val noteId =
            noteDao.insert(
                noteWithSynced.copy(
                    checklistText =
                        checklistSearchText(
                            items
                                .sortedBy { item -> item.sortOrder }
                                .flatMap { item -> listOf(item.text, item.details) },
                        ),
                    attachmentText = attachmentSearchText(attachments),
                    actionsText = actionsSearchText(noteWithSynced.actions),
                ),
            )
        tagRepository?.replaceTagsForNote(noteId, noteWithSynced.tags)
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
                            details = item.details,
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
        if (!suppressReminderSchedule && !noteWithSynced.trashed) {
            if (noteWithSynced.reminderAt != null) {
                scheduler?.scheduleOrShow(noteWithSynced.copy(id = noteId), items)
            }
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
                        details = draft.details,
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
                        details = draft.details,
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
                        details = draft.details,
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
                            details = draft.details,
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
                        details = draft.details,
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
                            details = draft.details,
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

    private suspend fun rescheduleReminder(
        id: Long,
        at: Long?,
    ) {
        scheduler?.cancel(id)
        if (at != null) {
            val noteWithItems = noteDao.get(id)
            if (noteWithItems != null) {
                scheduler?.scheduleOrShow(noteWithItems.note, noteWithItems.items)
            }
        }
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

    @Suppress("ktlint:standard:function-expression-body")
    suspend fun activeReminderNotes(): List<NoteWithItems> {
        return noteDao.activeRemindersUntil(Long.MAX_VALUE)
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
        if (nextTime != null) {
            val noteWithItems = noteDao.get(id)
            if (noteWithItems != null) {
                scheduler?.scheduleOrShow(noteWithItems.note, noteWithItems.items)
            }
        }
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
        val existingWithItems = noteDao.get(noteId) ?: return null
        val existing = existingWithItems.note
        val activeReminders = existing.getActiveReminders()
        val snapshot =
            NoteCompletionSnapshot(
                reminderAt = existing.reminderAt,
                recurrence = existing.recurrence,
                reminders = existing.reminders,
            )

        if (activeReminders.isEmpty()) {
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

        val soonest = activeReminders.minByOrNull { it.reminderAt } ?: return snapshot
        val updatedReminders =
            activeReminders.mapNotNull { reminder ->
                if (reminder == soonest) {
                    val rule = reminder.recurrence?.sanitized()
                    if (rule != null) {
                        val current = reminder.originalReminderAt ?: reminder.reminderAt
                        val consumedRule = rule.afterFire()
                        val stoppedByCount =
                            consumedRule.endKind == RecurrenceEndKind.AFTER_COUNT &&
                                consumedRule.endCount != null &&
                                consumedRule.endCount <= 0
                        val nextTime = if (stoppedByCount) null else consumedRule.nextAfter(current)
                        val nextRule = if (stoppedByCount || nextTime == null) null else consumedRule
                        if (nextTime != null) {
                            NoteReminder(reminderAt = nextTime, recurrence = nextRule)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } else {
                    reminder
                }
            }

        val nextNote =
            if (updatedReminders.isEmpty()) {
                existing.copy(
                    reminders = emptyList(),
                    reminderAt = null,
                    recurrence = null,
                    completedAt = clock(),
                    updatedAt = clock(),
                )
            } else {
                existing
                    .copy(
                        reminders = updatedReminders,
                        updatedAt = clock(),
                    ).withSyncedPrimaryReminder()
            }

        noteDao.update(nextNote)
        scheduler?.cancelNotification(noteId)
        if (nextNote.completedAt != null) {
            scheduler?.cancel(noteId)
        } else {
            scheduler?.scheduleOrShow(nextNote, existingWithItems.items)
        }
        postWriteBookkeeping()
        return snapshot
    }

    suspend fun markIncomplete(
        noteId: Long,
        snapshot: NoteCompletionSnapshot? = null,
    ): Boolean {
        val existingWithItems = noteDao.get(noteId) ?: return false
        val existing = existingWithItems.note
        if (existing.completedAt == null && snapshot == null) return false
        val restoredNote = restoredIncompleteNote(existing, snapshot)
        noteDao.update(restoredNote)
        scheduler?.scheduleOrShow(restoredNote, existingWithItems.items)
        postWriteBookkeeping()
        return true
    }

    suspend fun restoreCompletionStates(snapshots: Map<Long, NoteCompletionSnapshot>) {
        if (snapshots.isEmpty()) return
        val applyAll: suspend () -> Unit = {
            snapshots.forEach { (id, snapshot) ->
                val existingWithItems = noteDao.get(id) ?: return@forEach
                val existing = existingWithItems.note
                val restoredNote = restoredIncompleteNote(existing, snapshot)
                noteDao.update(restoredNote)
                scheduler?.cancel(id)
                scheduler?.scheduleOrShow(restoredNote, existingWithItems.items)
            }
        }
        if (database != null) database.withTransaction { applyAll() } else applyAll()
        postWriteBookkeeping()
    }

    private fun restoredIncompleteNote(
        existing: NoteEntity,
        snapshot: NoteCompletionSnapshot?,
    ): NoteEntity {
        val restoredNote =
            existing.copy(
                completedAt = null,
                reminderAt = snapshot?.reminderAt ?: existing.reminderAt,
                recurrence = snapshot?.recurrence?.sanitized() ?: existing.recurrence,
                reminders = snapshot?.reminders ?: existing.reminders,
                updatedAt = clock(),
            )
        return restoredNote.withSyncedPrimaryReminder()
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
