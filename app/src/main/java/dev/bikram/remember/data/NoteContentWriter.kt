package dev.bikram.remember.data

import androidx.room.withTransaction
import dev.bikram.remember.reminders.ReminderScheduler

/**
 * The create / update half of [NoteRepository]: turns a screen's title, body, colour, checklist
 * rows and [NoteOptions] into stored rows, then hands the follow-up work to the checklist,
 * reminder and media collaborators. Split out so the four near-identical entity-building paths
 * (note, simple list, hierarchical list, and their update counterparts) sit together.
 */
internal class NoteContentWriter(
    private val noteDao: NoteDao,
    private val itemDao: ChecklistItemDao,
    private val checklistWriter: NoteChecklistWriter,
    private val mediaMaintenance: NoteMediaMaintenance,
    private val reminderCoordinator: NoteReminderCoordinator,
    private val tagRepository: TagRepository?,
    private val scheduler: ReminderScheduler?,
    private val clock: () -> Long,
    private val database: RememberDatabase?,
) {
    suspend fun createNote(
        title: String,
        body: String,
        colorIndex: Int,
        options: NoteOptions,
    ): Long {
        val now = clock()
        val resolvedReminders = reminderCoordinator.resolveUpdatedReminders(null, options)
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
        return noteId
    }

    suspend fun createList(
        title: String,
        colorIndex: Int,
        items: List<String>,
        options: NoteOptions,
    ): Long {
        val now = clock()
        val validItems = items.map { it.trim() }.filter { it.isNotEmpty() }
        val resolvedReminders = reminderCoordinator.resolveUpdatedReminders(null, options)
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
        return id
    }

    suspend fun createListWithItems(
        title: String,
        colorIndex: Int,
        items: List<PersistableChecklistItem>,
        options: NoteOptions,
    ): Long {
        val now = clock()
        val validItems = items.filter { item -> item.text.isNotBlank() || item.details.isNotBlank() }
        val resolvedReminders = reminderCoordinator.resolveUpdatedReminders(null, options)
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
        checklistWriter.insertHierarchy(noteId = id, items = validItems)
        if (noteEntity.reminderAt != null) {
            val createdNoteWithItems = noteDao.get(id)
            if (createdNoteWithItems != null) {
                scheduler?.scheduleOrShow(createdNoteWithItems.note, createdNoteWithItems.items)
            }
        }
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
        val resolvedReminders = reminderCoordinator.resolveUpdatedReminders(existing, options)
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
        reminderCoordinator.rescheduleReminder(id, noteEntity.reminderAt)
        reminderCoordinator.refreshNotificationIfActive(id)
        if (oldPictureUri != null && oldPictureUri != options.pictureUri) {
            mediaMaintenance.cleanupUnreferencedMedia(listOf(oldPictureUri))
        }
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
                val resolvedReminders = reminderCoordinator.resolveUpdatedReminders(existing, options)
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
                checklistWriter.syncHierarchy(noteId = id, items = items)
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
            reminderCoordinator.rescheduleReminder(id, noteEntity?.reminderAt)
        }
        if (didUpdate) reminderCoordinator.refreshNotificationIfActive(id)
        if (didUpdate) {
            if (oldPictureUri != null && oldPictureUri != options.pictureUri) {
                mediaMaintenance.cleanupUnreferencedMedia(listOf(oldPictureUri))
            }
        }
    }
}
