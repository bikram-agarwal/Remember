package dev.bikram.remember.data

/** Actions assignable to horizontal swipe on a note card in the main list. */
enum class NoteSwipeAction(
    /** Google Material Symbols ligature for [dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol]. */
    val materialSymbolName: String,
) {
    EDIT("edit"),
    TRASH("delete"),
    DUPLICATE("content_copy"),
    TOGGLE_STAR("star"),

    /** Pin/unpin to the top-pinned "Pinned" section on Home. Placement only - see [NoteEntity.pinnedAt]. */
    TOGGLE_PIN("push_pin"),
    ARCHIVE("archive"),

    /** Mark task done. Recurrence-aware via [NoteRepository.markCompleted]. */
    MARK_DONE("check_circle"),
    ;

    /**
     * True for actions whose effect depends on the note's current state, so the handler must read
     * that state fresh instead of flipping a snapshot captured when the card composed.
     */
    val isToggle: Boolean
        get() = this == TOGGLE_STAR || this == TOGGLE_PIN || this == MARK_DONE
}
