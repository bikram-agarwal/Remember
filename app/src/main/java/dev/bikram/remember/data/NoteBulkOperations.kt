package dev.bikram.remember.data

import androidx.room.withTransaction
import dev.bikram.remember.reminders.ReminderScheduler

/**
 * Selection-mode actions that apply to many notes at once.
 *
 * Each method wraps the DAO writes for every id in a single Room transaction
 * so observers (observeActive / observeArchived / observeTrashed) emit exactly
 * ONCE per bulk action. Without this, sequential per-id writes caused the
 * LazyColumn to remove items one-by-one and the user saw a cascade of fade-out
 * animations even on bulk Archive / Trash. Scheduler cancellations and refresh
 * side-effects run once after the transaction commits.
 */
internal class NoteBulkOperations(
    private val noteDao: NoteDao,
    private val scheduler: ReminderScheduler?,
    private val clock: () -> Long,
    private val database: RememberDatabase?,
    private val reminderCoordinator: NoteReminderCoordinator,
    private val mediaMaintenance: NoteMediaMaintenance,
) {
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
        mediaMaintenance.cleanupUnreferencedMedia(deletedNotes.flatMap { deletedNote -> deletedNote.mediaUris() })
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
            ids.forEach { id -> reminderCoordinator.markCompleted(id)?.let { snapshots[id] = it } }
        }
        return snapshots
    }

    suspend fun markIncomplete(
        ids: Collection<Long>,
        snapshots: Map<Long, NoteCompletionSnapshot>,
    ) {
        if (ids.isEmpty()) return
        runInTransaction {
            ids.forEach { id -> reminderCoordinator.markIncomplete(id, snapshots[id]) }
        }
    }
}
