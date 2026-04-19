package dev.bikram.remember.data

/** Actions assignable to horizontal swipe on a note card in the main list. */
enum class NoteSwipeAction(
    /** Google Material Symbols ligature for [dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol]. */
    val materialSymbolName: String,
) {
    OPEN("edit"),
    TRASH("delete"),
    DUPLICATE("content_copy"),
    TOGGLE_PIN("favorite"),
}
