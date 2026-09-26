package dev.bikram.remember.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TagRepositoryTest {
    @Test
    fun `normalizes tag names with locale-safe lowercase`() {
        assertEquals("work", normalizeTagName(" Work "))
        assertEquals("office", normalizeTagName("OFFICE"))
    }

    @Test
    fun `cleans tag list for normalized assignments`() {
        val cleanedTags =
            cleanUserVisibleTagNames(
                listOf(
                    " Work ",
                    "work",
                    "",
                    RememberReservedTags.STARRED,
                    "Personal",
                    " personal ",
                ),
            )

        assertEquals(listOf("Work", "Personal"), cleanedTags)
    }

    @Test
    fun `keeps the stored spelling when a tag already exists`() {
        assertEquals(
            listOf("Errands", "Travel"),
            tagNamesUsingStoredSpellings(
                requestedNames = listOf(" errands ", "errands", "Travel"),
                storedNames = listOf("Errands"),
            ),
        )
    }

    @Test
    fun `keeps a new tag spelling when nothing is stored`() {
        assertEquals(
            listOf("Errands"),
            tagNamesUsingStoredSpellings(
                requestedNames = listOf("Errands"),
                storedNames = emptyList(),
            ),
        )
    }
}
