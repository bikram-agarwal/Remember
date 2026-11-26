package dev.bikram.remember.di

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hand-off of freshly imported note ids to the Notes home screen. Whoever finishes a multi-note
 * import (share sheet, drag-and-drop, the Import screen) parks the ids here; `HomeViewModel`
 * consumes them exactly once - selecting those notes and scrolling to them - and clears the slot,
 * so a rotation or a later visit to Notes never re-applies the selection.
 */
@Singleton
class PendingNoteSelection
    @Inject
    constructor() {
        val ids = MutableStateFlow<Set<Long>?>(null)

        /** Parks [noteIds] only for a genuine multi-note import; a single note opens the editor instead. */
        fun offer(noteIds: Collection<Long>) {
            if (noteIds.size >= 2) ids.value = noteIds.toSet()
        }
    }
