package dev.bikram.remember.data

/**
 * Tags managed by the app that should not appear in user-facing tag editors.
 * [STARRED] is kept in sync with [NoteEntity.starred] so starred notes behave like a tag in storage.
 * [MOCK] marks notes created by developer options. Visible on the note itself so the user can remove
 * it (promoting the note to real), but filtered from tag suggestions and bulk-tag sheets.
 */
object RememberReservedTags {
    const val STARRED: String = "__remember_starred__"
    const val MOCK: String = "Mock note"

    fun userVisibleTags(tags: List<String>): List<String> = tags.filterNot { it == STARRED }

    fun isSuggestionReserved(tag: String): Boolean =
        tag.equals(STARRED, ignoreCase = true) || tag.equals(MOCK, ignoreCase = true)
}
