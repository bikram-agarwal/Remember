package dev.bikram.remember.widget

import dev.bikram.remember.data.NoteEntity
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.SortDir
import dev.bikram.remember.data.SortKey
import dev.bikram.remember.data.ViewOptions
import dev.bikram.remember.data.Visibility
import org.junit.Assert.assertEquals
import org.junit.Test

class NotesWidgetTest {
    @Test
    fun widget_plain_text_strips_inline_markdown() {
        assertEquals("30 May IST", widgetPlainText("**30 May IST**"))
        assertEquals("May 2026 games were:...", widgetPlainText("**May 2026** games were:..."))
        assertEquals("Read docs", widgetPlainText("[Read docs](https://example.com)"))
        assertEquals("code", widgetPlainText("`code`"))
    }

    @Test
    fun widget_plain_text_uses_first_non_blank_rendered_line() {
        assertEquals("Heading", widgetPlainText("\n## Heading\nBody"))
        assertEquals("Buy milk", widgetPlainText("- [ ] Buy milk"))
        assertEquals("Quoted", widgetPlainText("> Quoted"))
        assertEquals("Body", widgetPlainText("---\nBody"))
    }

    @Test
    fun quick_capture_copy_stays_full_when_header_fits() {
        val needsCondensedCopy =
            quickCaptureNeedsCondensedCopy(
                widgetWidthDp = 180f,
                density = 1f,
                fontScale = 1f,
                title = "Remember",
                trailingText = "Nothing due",
            ) { text, _ ->
                when (text) {
                    "Remember" -> 80f
                    "Nothing due" -> 50f
                    else -> 0f
                }
            }

        assertEquals(false, needsCondensedCopy)
    }

    @Test
    fun quick_capture_copy_condenses_only_when_header_would_wrap() {
        val needsCondensedCopy =
            quickCaptureNeedsCondensedCopy(
                widgetWidthDp = 180f,
                density = 1f,
                fontScale = 1f,
                title = "Remember",
                trailingText = "Nothing due",
            ) { text, _ ->
                when (text) {
                    "Remember" -> 90f
                    "Nothing due" -> 64f
                    else -> 0f
                }
            }

        assertEquals(true, needsCondensedCopy)
    }

    @Test
    fun selected_notes_widget_items_all_filters_active_uncompleted_notes() {
        val notes =
            listOf(
                createTestNote(id = 1L, title = "Active Note"),
                createTestNote(id = 2L, title = "Completed Note", completedAt = 500L),
                createTestNote(id = 3L, title = "Secret Note", visibility = Visibility.SECRET),
                createTestNote(id = 4L, title = "Another Active Note"),
            )

        val result =
            selectedNotesWidgetItems(
                notes = notes,
                config = SelectedNotesWidgetConfig(filterType = SelectedNotesFilterType.ALL),
                viewOptions = ViewOptions(),
            )

        assertEquals(listOf(1L, 4L), result.map { it.note.id })
    }

    @Test
    fun selected_notes_widget_items_starred_filters_only_starred_notes() {
        val notes =
            listOf(
                createTestNote(id = 1L, title = "Regular"),
                createTestNote(id = 2L, title = "Starred", starred = true),
                createTestNote(id = 3L, title = "Starred Tag", tags = listOf(dev.bikram.remember.data.RememberReservedTags.STARRED)),
                createTestNote(id = 4L, title = "Starred Completed", starred = true, completedAt = 500L),
            )

        val result =
            selectedNotesWidgetItems(
                notes = notes,
                config = SelectedNotesWidgetConfig(filterType = SelectedNotesFilterType.STARRED),
                viewOptions = ViewOptions(),
            )

        assertEquals(listOf(2L, 3L), result.map { it.note.id })
    }

    @Test
    fun selected_notes_widget_items_pinned_filters_only_pinned_notes() {
        val notes =
            listOf(
                createTestNote(id = 1L, title = "Regular"),
                createTestNote(id = 2L, title = "Pinned", pinned = true),
                createTestNote(id = 3L, title = "Pinned Completed", pinned = true, completedAt = 500L),
            )

        val result =
            selectedNotesWidgetItems(
                notes = notes,
                config = SelectedNotesWidgetConfig(filterType = SelectedNotesFilterType.PINNED),
                viewOptions = ViewOptions(),
            )

        assertEquals(listOf(2L), result.map { it.note.id })
    }

    @Test
    fun selected_notes_widget_items_tag_filters_matching_user_tags() {
        val notes =
            listOf(
                createTestNote(id = 1L, title = "Work item", tags = listOf("Work")),
                createTestNote(id = 2L, title = "Personal item", tags = listOf("Personal")),
                createTestNote(id = 3L, title = "Another work item", tags = listOf("work", "project")),
                createTestNote(id = 4L, title = "Completed work", tags = listOf("work"), completedAt = 500L),
            )

        val result =
            selectedNotesWidgetItems(
                notes = notes,
                config = SelectedNotesWidgetConfig(filterType = SelectedNotesFilterType.TAG, tag = "work"),
                viewOptions = ViewOptions(),
            )

        assertEquals(listOf(1L, 3L), result.map { it.note.id })
    }

    @Test
    fun selected_notes_widget_items_respects_alphabetical_sort() {
        val notes =
            listOf(
                createTestNote(id = 1L, title = "Zebra"),
                createTestNote(id = 2L, title = "Apple"),
                createTestNote(id = 3L, title = "Banana"),
            )

        val ascending =
            selectedNotesWidgetItems(
                notes = notes,
                config = SelectedNotesWidgetConfig(filterType = SelectedNotesFilterType.ALL),
                viewOptions = ViewOptions(sortKey = SortKey.ALPHABETICAL, sortDir = SortDir.ASC),
            )

        assertEquals(listOf(2L, 3L, 1L), ascending.map { it.note.id })
    }

    private fun createTestNote(
        id: Long,
        title: String = "Note $id",
        starred: Boolean = false,
        pinned: Boolean = false,
        completedAt: Long? = null,
        visibility: Visibility = Visibility.DEFAULT,
        tags: List<String> = emptyList(),
    ): NoteWithItems =
        NoteWithItems(
            note =
                NoteEntity(
                    id = id,
                    kind = NoteKind.NOTE,
                    title = title,
                    body = "",
                    colorIndex = 0,
                    starred = starred,
                    trashed = false,
                    createdAt = id * 1000L,
                    updatedAt = id * 1000L,
                    visibility = visibility,
                    tags = tags,
                    completedAt = completedAt,
                    pinnedAt = if (pinned) 1000L else null,
                ),
            items = emptyList(),
        )
}
