package dev.bikram.remember.ui.common

import android.content.Context
import androidx.annotation.PluralsRes
import dev.bikram.remember.R
import dev.bikram.remember.data.NoteCompletionSnapshot

/**
 * Records the most recent bulk action so the user-facing snackbar can offer Undo and so
 * the receiving screen can pick the right summary string. The variant tells the
 * ViewModel which inverse repository call to make if Undo is tapped.
 *
 * Each variant records the operation that just happened. The row state-changing
 * actions themselves are absolute; these variants choose the inverse for Undo:
 *   - [Archived] (active -> archive)            -> unarchive these ids
 *   - [Trashed] (active -> trash)               -> restoreFromTrash
 *   - [MarkedDone]                              -> restoreCompletionStates (or markIncomplete fallback)
 *   - [Restored] (trash -> active)              -> moveToTrash
 *   - [Unarchived] (archive -> active)          -> archiveNotes
 *   - [ArchivedFromTrash] (trash -> archive)    -> moveToTrash
 *   - [MovedArchiveToTrash] (archive -> trash)  -> archiveNotes
 *
 * Permanent delete is intentionally NOT undoable here; rows are gone after delete and
 * the screen handles that path with a confirmation dialog instead of an undo snackbar.
 */
sealed interface BulkUndoableAction {
    val ids: Set<Long>
    val count: Int get() = ids.size

    data class Archived(
        override val ids: Set<Long>,
    ) : BulkUndoableAction

    data class Trashed(
        override val ids: Set<Long>,
    ) : BulkUndoableAction

    /**
     * @param snapshots pre-completion state captured before [NoteRepository.markCompleted]
     *     ran. Carrying these lets undo restore the exact prior reminderAt + recurrence
     *     rule even for recurring notes whose rule was advanced or consumed by the
     *     mark-done. Defaulted to empty for callers that don't capture (notification
     *     action path, importer); without a snapshot, [markIncomplete] can only restore
     *     the completion flag and whatever reminder state remains on the row.
     */
    data class MarkedDone(
        override val ids: Set<Long>,
        val snapshots: Map<Long, NoteCompletionSnapshot> = emptyMap(),
    ) : BulkUndoableAction

    data class Restored(
        override val ids: Set<Long>,
    ) : BulkUndoableAction

    data class Unarchived(
        override val ids: Set<Long>,
    ) : BulkUndoableAction

    data class ArchivedFromTrash(
        override val ids: Set<Long>,
    ) : BulkUndoableAction

    data class MovedArchiveToTrash(
        override val ids: Set<Long>,
    ) : BulkUndoableAction
}

/**
 * Resolves the snackbar message for [action], formatted with the affected count.
 * Centralised here so HomeRoute and HistoryRoute don't drift apart on wording or
 * pluralisation handling. The string resources use "%1$d" so the count placement is
 * locale-friendly.
 */
fun bulkActionSnackbarMessage(
    context: Context,
    action: BulkUndoableAction,
): String {
    @PluralsRes
    val resId =
        when (action) {
            is BulkUndoableAction.Archived -> R.plurals.bulk_action_archived
            is BulkUndoableAction.Trashed -> R.plurals.bulk_action_trashed
            is BulkUndoableAction.MarkedDone -> R.plurals.bulk_action_marked_done
            is BulkUndoableAction.Restored -> R.plurals.bulk_action_restored
            is BulkUndoableAction.Unarchived -> R.plurals.bulk_action_unarchived
            is BulkUndoableAction.ArchivedFromTrash -> R.plurals.bulk_action_archived_from_trash
            is BulkUndoableAction.MovedArchiveToTrash -> R.plurals.bulk_action_moved_to_trash_from_archive
        }
    return context.resources.getQuantityString(resId, action.count, action.count)
}
