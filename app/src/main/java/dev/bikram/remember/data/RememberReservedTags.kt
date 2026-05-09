package dev.bikram.remember.data

/**
 * Tags managed by the app that should not appear in user-facing tag editors.
 * [STARRED] is kept in sync with [NoteEntity.starred] so starred notes behave like a tag in storage.
 */
object RememberReservedTags {
    const val STARRED: String = "__remember_starred__"

    fun userVisibleTags(tags: List<String>): List<String> = tags.filterNot { it == STARRED }
}
