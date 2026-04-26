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
        val cleanedTags = cleanUserVisibleTagNames(
            listOf(
                " Work ",
                "work",
                "",
                RememberReservedTags.FAVORITE,
                "Personal",
                " personal ",
            ),
        )

        assertEquals(listOf("Work", "Personal"), cleanedTags)
    }
}
