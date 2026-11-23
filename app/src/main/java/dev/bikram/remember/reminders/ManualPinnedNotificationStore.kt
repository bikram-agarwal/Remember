package dev.bikram.remember.reminders

import android.content.Context
import dev.bikram.remember.data.NoteEntity

/**
 * Tracks note IDs whose notification was manually created as "Pinned", so swipe-dismiss
 * restores them even when the global "Restore notifications" preference is disabled or the
 * note has no scheduled reminder.
 */
internal object ManualPinnedNotificationStore {
    private const val PREFS_NAME = "manual_pinned_notifications"
    private const val KEY_NOTE_IDS = "pinned_note_ids"

    fun isPinned(
        context: Context,
        noteId: Long,
    ): Boolean {
        if (noteId <= 0L) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(KEY_NOTE_IDS, emptySet()).orEmpty()
        return noteId.toString() in ids
    }

    fun pinnedNoteIds(context: Context): Set<Long> =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_NOTE_IDS, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .filter { it > 0L }
            .toSet()

    fun setPinned(
        context: Context,
        noteId: Long,
        pinned: Boolean,
    ) {
        if (noteId <= 0L) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = prefs.getStringSet(KEY_NOTE_IDS, emptySet()).orEmpty().toMutableSet()
        val key = noteId.toString()
        val changed = if (pinned) updated.add(key) else updated.remove(key)
        if (changed) {
            prefs.edit().putStringSet(KEY_NOTE_IDS, updated).apply()
        }
    }
}

/**
 * Whether a pin still applies to [note] when its notification is re-posted after a reboot. A
 * trashed, archived, or completed note has no notification to bring back, so its pin is dropped.
 */
internal fun pinSurvivesReboot(note: NoteEntity): Boolean = !note.trashed && !note.archived && note.completedAt == null
