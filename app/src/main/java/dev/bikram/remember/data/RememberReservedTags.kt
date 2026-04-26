package dev.bikram.remember.data

/**
 * Tags managed by the app that should not appear in user-facing tag editors.
 * [FAVORITE] is kept in sync with [NoteEntity.favorite] so favorites behave like a tag in storage.
 */
object RememberReservedTags {
    const val FAVORITE: String = "__remember_favorite__"

    fun userVisibleTags(tags: List<String>): List<String> =
        tags.filterNot { it == FAVORITE }
}
