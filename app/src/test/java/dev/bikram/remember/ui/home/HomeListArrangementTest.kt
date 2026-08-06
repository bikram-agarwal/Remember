package dev.bikram.remember.ui.home

import dev.bikram.remember.data.GroupBy
import dev.bikram.remember.data.NoteEntity
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.NotesFilter
import dev.bikram.remember.data.SortDir
import dev.bikram.remember.data.SortKey
import dev.bikram.remember.data.ViewOptions
import dev.bikram.remember.data.matches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the section-precedence contract in [arrangeItems], with an emphasis on the pinning
 * rules: pin beats every grouping mode, Done beats pin, and a pinned note is never duplicated
 * into the group it would otherwise land in.
 */
class HomeListArrangementTest {
    @Test
    fun pinned_section_leads_the_list_and_carries_only_pinned_rows() {
        val items =
            arrangeItems(
                notes = listOf(note(id = 1L), note(id = 2L, pinned = true)),
                opts = ViewOptions(),
            )

        assertEquals(PINNED_SECTION_KEY, (items.first() as HomeListItem.Header).stableKey)
        assertEquals(1, (items.first() as HomeListItem.Header).count)
        assertEquals(listOf(2L), noteIdsIn(items, PINNED_SECTION_KEY))
    }

    @Test
    fun pinned_note_appears_exactly_once_across_the_whole_list() {
        val items =
            arrangeItems(
                notes =
                    listOf(
                        note(id = 1L, pinned = true, tags = listOf("work", "personal")),
                        note(id = 2L, tags = listOf("work")),
                    ),
                opts = ViewOptions(groupBy = GroupBy.TAG),
            )

        // GroupBy.TAG is the one mode that legitimately repeats a note across sections, so it
        // is the strongest check that extraction (rather than duplication) happened.
        assertEquals(1, noteRows(items).count { it.card.id == 1L })
        assertEquals(listOf(1L), noteIdsIn(items, PINNED_SECTION_KEY))
    }

    @Test
    fun pinned_outranks_overdue() {
        val items =
            arrangeItems(
                notes = listOf(note(id = 1L, reminderAt = PAST), note(id = 2L, pinned = true, reminderAt = PAST)),
                opts = ViewOptions(),
            )

        assertEquals(listOf(2L), noteIdsIn(items, PINNED_SECTION_KEY))
        assertEquals(listOf(1L), noteIdsIn(items, "OVERDUE"))
        assertTrue(headerKeys(items).indexOf(PINNED_SECTION_KEY) < headerKeys(items).indexOf("OVERDUE"))
    }

    @Test
    fun done_outranks_pinned_so_a_completed_pin_drops_to_the_bottom_section() {
        val items =
            arrangeItems(
                notes = listOf(note(id = 1L), note(id = 2L, pinned = true, completedAt = 500L)),
                opts = ViewOptions(),
            )

        assertFalse(headerKeys(items).contains(PINNED_SECTION_KEY))
        assertEquals(listOf(2L), noteIdsIn(items, "DONE"))
        assertEquals(headerKeys(items).size - 1, headerKeys(items).indexOf("DONE"))
    }

    @Test
    fun un_completing_a_pinned_note_returns_it_to_the_pinned_section() {
        // The pin flag survives completion untouched, so the only difference between these two
        // arrangements is completedAt - this is the round trip a user sees when they undo Done.
        val completed = arrangeItems(listOf(note(id = 1L, pinned = true, completedAt = 500L)), ViewOptions())
        val reopened = arrangeItems(listOf(note(id = 1L, pinned = true)), ViewOptions())

        assertEquals(listOf(1L), noteIdsIn(completed, "DONE"))
        assertEquals(listOf(1L), noteIdsIn(reopened, PINNED_SECTION_KEY))
    }

    @Test
    fun pinned_section_survives_every_grouping_mode() {
        GroupBy.entries.forEach { groupBy ->
            val items =
                arrangeItems(
                    notes = listOf(note(id = 1L, pinned = true, tags = listOf("work")), note(id = 2L)),
                    opts = ViewOptions(groupBy = groupBy),
                )

            assertEquals(
                "groupBy=$groupBy should still lead with the pinned section",
                PINNED_SECTION_KEY,
                (items.first() as HomeListItem.Header).stableKey,
            )
            assertEquals("groupBy=$groupBy", listOf(1L), noteIdsIn(items, PINNED_SECTION_KEY))
        }
    }

    @Test
    fun pinned_section_stays_first_for_every_sort_key_and_direction() {
        SortKey.entries.forEach { sortKey ->
            SortDir.entries.forEach { sortDir ->
                val items =
                    arrangeItems(
                        notes = listOf(note(id = 1L), note(id = 2L, pinned = true)),
                        opts = ViewOptions(sortKey = sortKey, sortDir = sortDir),
                    )

                assertEquals(
                    "sortKey=$sortKey sortDir=$sortDir should still lead with the pinned section",
                    PINNED_SECTION_KEY,
                    (items.first() as HomeListItem.Header).stableKey,
                )
            }
        }
    }

    @Test
    fun sorting_still_orders_rows_inside_the_pinned_section() {
        val notes =
            listOf(
                note(id = 1L, pinned = true, createdAt = 100L),
                note(id = 2L, pinned = true, createdAt = 300L),
                note(id = 3L, pinned = true, createdAt = 200L),
            )

        val ascending = arrangeItems(notes, ViewOptions(sortKey = SortKey.CREATED, sortDir = SortDir.ASC))
        val descending = arrangeItems(notes, ViewOptions(sortKey = SortKey.CREATED, sortDir = SortDir.DESC))

        assertEquals(listOf(1L, 3L, 2L), noteIdsIn(ascending, PINNED_SECTION_KEY))
        assertEquals(listOf(2L, 3L, 1L), noteIdsIn(descending, PINNED_SECTION_KEY))
    }

    @Test
    fun no_pinned_notes_means_no_pinned_header_at_all() {
        val items = arrangeItems(listOf(note(id = 1L), note(id = 2L)), ViewOptions())

        assertFalse(headerKeys(items).contains(PINNED_SECTION_KEY))
    }

    @Test
    fun each_fixed_section_gets_its_own_badge_glyph() {
        // All three fixed sections used to share the pin glyph, which became ambiguous once one
        // of them was literally "Pinned". They must stay distinct from each other.
        val items =
            arrangeItems(
                notes =
                    listOf(
                        note(id = 1L, pinned = true),
                        note(id = 2L, reminderAt = PAST),
                        note(id = 3L, completedAt = 500L),
                    ),
                opts = ViewOptions(),
            )
        val badges =
            items
                .filterIsInstance<HomeListItem.Header>()
                .associate { it.stableKey to it.bookendBadgeSymbol() }

        assertEquals(PINNED_SECTION_BADGE_SYMBOL, badges[PINNED_SECTION_KEY])
        assertEquals("alarm", badges[OVERDUE_SECTION_KEY])
        assertEquals("check_circle", badges[DONE_SECTION_KEY])
        val distinctBadges = badges.values.filterNotNull().distinct()

        assertEquals(3, distinctBadges.size)
    }

    @Test
    fun ordinary_grouping_sections_get_no_badge() {
        val items =
            arrangeItems(
                notes = listOf(note(id = 1L, tags = listOf("work"))),
                opts = ViewOptions(groupBy = GroupBy.TAG),
            )

        val headers = items.filterIsInstance<HomeListItem.Header>()

        assertTrue(headers.all { it.bookendBadgeSymbol() == null })
    }

    @Test
    fun pinned_filter_facet_narrows_to_pinned_notes_only() {
        // Filtering is the one thing allowed to hide a pinned note, and the facet's inverse use
        // is "show me only the pins" - both directions are checked here.
        val notes = listOf(note(id = 1L, pinned = true), note(id = 2L))

        val onlyPinned = notes.filter { NotesFilter(pinned = true).matches(it) }
        val onlyUnpinned = notes.filter { NotesFilter(pinned = false).matches(it) }
        val unfiltered = notes.filter { NotesFilter().matches(it) }

        assertEquals(listOf(1L), onlyPinned.map { it.note.id })
        assertEquals(listOf(2L), onlyUnpinned.map { it.note.id })
        assertEquals(listOf(1L, 2L), unfiltered.map { it.note.id })
        assertTrue(NotesFilter(pinned = true).facetActive)
        assertFalse(NotesFilter().facetActive)
    }

    @Test
    fun filtering_out_a_pinned_note_removes_the_pinned_section_entirely() {
        val notes = listOf(note(id = 1L, pinned = true), note(id = 2L))
        val filter = NotesFilter(pinned = false)

        val items = arrangeItems(notes.filter { filter.matches(it) }, ViewOptions())

        assertFalse(headerKeys(items).contains(PINNED_SECTION_KEY))
        assertEquals(listOf(2L), noteRows(items).map { it.card.id })
    }

    @Test
    fun pinned_rows_are_collapsible_like_every_other_section() {
        // The Pinned header must be collapsible for the HomeScreen collapse plumbing (which
        // hides NoteRows by groupKey) to work on it at all.
        val items = arrangeItems(listOf(note(id = 1L, pinned = true)), ViewOptions())
        val header = items.first() as HomeListItem.Header

        assertTrue(header.collapsible)
        assertTrue(noteRows(items).all { it.groupKey == PINNED_SECTION_KEY })
    }

    private fun headerKeys(items: List<HomeListItem>): List<String> = items.filterIsInstance<HomeListItem.Header>().map { it.stableKey }

    private fun noteRows(items: List<HomeListItem>): List<HomeListItem.NoteRow> = items.filterIsInstance<HomeListItem.NoteRow>()

    private fun noteIdsIn(
        items: List<HomeListItem>,
        groupKey: String,
    ): List<Long> = noteRows(items).filter { it.groupKey == groupKey }.map { it.card.id }

    @Suppress("ktlint:standard:function-expression-body")
    private fun note(
        id: Long,
        pinned: Boolean = false,
        completedAt: Long? = null,
        reminderAt: Long? = null,
        createdAt: Long = 0L,
        tags: List<String> = emptyList(),
    ): NoteWithItems {
        return NoteWithItems(
            note =
                NoteEntity(
                    id = id,
                    kind = NoteKind.NOTE,
                    title = "Note $id",
                    body = "",
                    colorIndex = 0,
                    starred = false,
                    trashed = false,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                    reminderAt = reminderAt,
                    tags = tags,
                    completedAt = completedAt,
                    pinnedAt = if (pinned) 1_000L else null,
                ),
            items = emptyList(),
        )
    }

    private companion object {
        /** Well before "now", so reminder-bearing notes land in the Overdue bucket. */
        const val PAST = 1_000L
    }
}
