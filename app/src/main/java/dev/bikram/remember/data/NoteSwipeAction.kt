package dev.bikram.remember.data

/** Actions assignable to horizontal swipe on a note card in the main list. */
enum class NoteSwipeAction(
    /** Google Material Symbols ligature for [dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol]. */
    val materialSymbolName: String,
) {
    EDIT("edit"),
    TRASH("delete"),
    DUPLICATE("content_copy"),
    TOGGLE_FAVORITE("favorite"),
    ARCHIVE("archive"),

    /** Mark task done. Recurrence-aware via [NoteRepository.markCompleted]. */
    MARK_DONE("check_circle"),
}
