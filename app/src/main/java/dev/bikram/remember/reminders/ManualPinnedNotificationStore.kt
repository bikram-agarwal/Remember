package dev.bikram.remember.reminders

import android.content.Context

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
