package dev.bikram.remember.ui.home

import dev.bikram.remember.data.GroupBy
import dev.bikram.remember.data.NoteEntity
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.NotesFilter
import dev.bikram.remember.data.RememberReservedTags
import dev.bikram.remember.data.SortDir
import dev.bikram.remember.data.SortKey
import dev.bikram.remember.data.ViewOptions
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeStateFlowTest {
    @Test
    fun selection_reuses_content_without_reading_the_note_lists_again() =
        runTest {
            var noteReads = 0
            val notes =
                ObservedNotes(
                    List(256) { index ->
                        note(
                            id = index.toLong() + 1,
                            pinned = index == 0,
                            starred = index == 0,
                            tags = listOf("Work"),
                        )
                    },
                ) { noteReads++ }
            val fixture = HomeFlowFixture(notes)
            fixture.filter.value = NotesFilter(text = "search")
            fixture.archived.value = listOf(note(id = 300L))
            fixture.trashed.value = listOf(note(id = 301L))
            val state = fixture.state(this)
            runCurrent()
            val prepared = state.value
            assertFalse(prepared.loading)
            assertTrue(noteReads > 0)
            noteReads = 0

            fixture.selected.value = setOf(1L)
            runCurrent()
            assertEquals(setOf(1L), state.value.selectedIds)
            assertTrue(state.value.inSelectionMode)
            assertFalse(state.value.canPinSelected)
            assertFalse(state.value.canStarSelected)

            fixture.selected.value = setOf(1L, 2L, 999L)
            runCurrent()
            assertEquals(setOf(1L, 2L), state.value.selectedIds)
            assertTrue(state.value.canPinSelected)
            assertTrue(state.value.canStarSelected)
            assertSame(prepared.items, state.value.items)
            assertSame(prepared.availableTags, state.value.availableTags)
            assertSame(prepared.archivedMatches, state.value.archivedMatches)
            assertSame(prepared.trashedMatches, state.value.trashedMatches)

            fixture.selected.value = emptySet()
            runCurrent()
            assertFalse(state.value.inSelectionMode)
            assertFalse(state.value.canPinSelected)
            assertFalse(state.value.canStarSelected)
            assertSame(prepared.items, state.value.items)
            assertEquals(0, noteReads)
        }

    @Test
    fun tags_and_active_count_update_independently_of_search_and_arrangement() =
        runTest {
            val fixture =
                HomeFlowFixture(
                    listOf(note(id = 1L, tags = listOf("Work", "Home", "Work", RememberReservedTags.STARRED))),
                )
            val state = fixture.state(this)
            runCurrent()
            val originalTags = state.value.availableTags
            assertEquals(listOf("Home", "Work"), originalTags)

            fixture.filter.value = NotesFilter(text = "search", tags = persistentSetOf("Work"))
            fixture.options.value = ViewOptions(groupBy = GroupBy.TAG, sortKey = SortKey.ALPHABETICAL)
            runCurrent()
            assertSame(originalTags, state.value.availableTags)
            val searchedItems = state.value.items

            fixture.allActive.value = fixture.allActive.value + note(id = 2L, tags = listOf("Travel"))
            runCurrent()
            assertEquals(2, state.value.totalActive)
            assertEquals(listOf("Home", "Travel", "Work"), state.value.availableTags)
            assertSame(searchedItems, state.value.items)
        }

    @Test
    fun content_changes_refresh_bulk_actions_and_prune_hidden_or_removed_notes() =
        runTest {
            val fixture = HomeFlowFixture(listOf(note(id = 1L), note(id = 2L)))
            fixture.selected.value = setOf(1L, 2L)
            val state = fixture.state(this)
            runCurrent()
            assertTrue(state.value.canPinSelected)
            assertTrue(state.value.canStarSelected)

            fixture.notes.value = listOf(note(id = 1L, pinned = true, starred = true))
            runCurrent()
            assertEquals(setOf(1L), state.value.selectedIds)
            assertFalse(state.value.canPinSelected)
            assertFalse(state.value.canStarSelected)

            fixture.filter.value = NotesFilter(pinned = false)
            runCurrent()
            assertTrue(state.value.items.isEmpty())
            assertTrue(state.value.selectedIds.isEmpty())
            assertFalse(state.value.inSelectionMode)
            assertFalse(state.value.canPinSelected)
            assertFalse(state.value.canStarSelected)
        }

    @Test
    fun sorting_and_duplicate_tag_rows_preserve_selection_and_bulk_action_flags() =
        runTest {
            val fixture =
                HomeFlowFixture(
                    listOf(
                        note(id = 1L, title = "Alpha", starred = true, tags = listOf("Home", "Work")),
                        note(id = 2L, title = "Beta", tags = listOf("Work")),
                    ),
                )
            fixture.options.value = ViewOptions(groupBy = GroupBy.NONE, sortKey = SortKey.ALPHABETICAL)
            fixture.selected.value = setOf(1L)
            val state = fixture.state(this)
            runCurrent()
            assertEquals(
                listOf(1L, 2L),
                state.value.items
                    .filterIsInstance<HomeListItem.NoteRow>()
                    .map { it.card.id },
            )

            fixture.options.value = fixture.options.value.copy(sortDir = SortDir.DESC)
            runCurrent()
            assertEquals(
                listOf(2L, 1L),
                state.value.items
                    .filterIsInstance<HomeListItem.NoteRow>()
                    .map { it.card.id },
            )

            fixture.options.value = fixture.options.value.copy(groupBy = GroupBy.TAG)
            runCurrent()
            assertEquals(
                2,
                state.value.items
                    .filterIsInstance<HomeListItem.NoteRow>()
                    .count { it.card.id == 1L },
            )
            assertEquals(setOf(1L), state.value.selectedIds)
            assertTrue(state.value.canPinSelected)
            assertFalse(state.value.canStarSelected)
        }

    @Test
    fun search_facets_apply_to_all_sources_without_repeating_text_matching() =
        runTest {
            val fixture =
                HomeFlowFixture(
                    listOf(note(id = 1L, tags = listOf("Work")), note(id = 2L, tags = listOf("Home"))),
                )
            fixture.archived.value = listOf(note(id = 3L, tags = listOf("Work")), note(id = 4L))
            fixture.trashed.value = listOf(note(id = 5L, tags = listOf("Work")), note(id = 6L))
            // FTS can match terms across fields; the literal query need not occur in the title.
            fixture.filter.value = NotesFilter(text = "separate terms", tags = persistentSetOf("Work"))
            fixture.selected.value = setOf(1L, 3L, 5L)
            val state = fixture.state(this)
            runCurrent()
            assertEquals(
                listOf(1L),
                state.value.items
                    .filterIsInstance<HomeListItem.NoteRow>()
                    .map { it.card.id },
            )
            assertEquals(listOf(3L), state.value.archivedMatches.map { it.note.id })
            assertEquals(listOf(5L), state.value.trashedMatches.map { it.note.id })
            assertEquals(setOf(1L), state.value.selectedIds)

            fixture.filter.value = fixture.filter.value.copy(text = "")
            runCurrent()
            assertTrue(state.value.archivedMatches.isEmpty())
            assertTrue(state.value.trashedMatches.isEmpty())
        }

    @Test
    fun available_tags_follow_the_supplied_catalog() =
        runTest {
            val notes =
                listOf(
                    note(id = 1L, tags = listOf("errands")),
                    note(id = 2L, tags = listOf("Errands", "subscriptions")),
                )
            val state =
                homeStateFlow(
                    filter = flowOf(NotesFilter()),
                    notesSource = flowOf(notes),
                    allActiveNotes = flowOf(notes),
                    viewOptions = flowOf(ViewOptions()),
                    selectedIds = flowOf(emptySet()),
                    archivedSearchSource = flowOf(emptyList()),
                    trashedSearchSource = flowOf(emptyList()),
                    availableTagNames = flowOf(listOf("Errands", "Subscriptions")),
                ).first()

            assertEquals(listOf("Errands", "Subscriptions"), state.availableTags)
        }

    @Test
    fun list_preparation_uses_a_background_dispatcher_by_default() =
        runTest {
            val collectorThread = Thread.currentThread()
            val notes =
                ObservedNotes(listOf(note(id = 1L, tags = listOf("Work")))) {
                    assertNotSame(collectorThread, Thread.currentThread())
                }
            val state =
                homeStateFlow(
                    filter = flowOf(NotesFilter()),
                    notesSource = flowOf(notes),
                    allActiveNotes = flowOf(notes),
                    viewOptions = flowOf(ViewOptions()),
                    selectedIds = flowOf(setOf(1L)),
                    archivedSearchSource = flowOf(emptyList()),
                    trashedSearchSource = flowOf(emptyList()),
                    availableTagNames = flowOf(listOf("Work")),
                ).first()

            assertFalse(state.loading)
            assertEquals(setOf(1L), state.selectedIds)
            assertEquals(listOf("Work"), state.availableTags)
        }

    private class HomeFlowFixture(
        initialNotes: List<NoteWithItems>,
    ) {
        val filter = MutableStateFlow(NotesFilter())
        val notes = MutableStateFlow(initialNotes)
        val allActive = MutableStateFlow(initialNotes)
        val options = MutableStateFlow(ViewOptions())
        val selected = MutableStateFlow(emptySet<Long>())
        val archived = MutableStateFlow(emptyList<NoteWithItems>())
        val trashed = MutableStateFlow(emptyList<NoteWithItems>())
        val availableTagNames =
            allActive.map { activeNotes ->
                activeNotes
                    .asSequence()
                    .flatMap { noteWithItems -> RememberReservedTags.userVisibleTags(noteWithItems.note.tags) }
                    .distinct()
                    .sorted()
                    .toList()
            }

        @Suppress("ktlint:standard:function-expression-body")
        fun state(scope: TestScope): StateFlow<HomeState> {
            return homeStateFlow(
                filter = filter,
                notesSource = notes,
                allActiveNotes = allActive,
                viewOptions = options,
                selectedIds = selected,
                archivedSearchSource = archived,
                trashedSearchSource = trashed,
                availableTagNames = availableTagNames,
                computationDispatcher = StandardTestDispatcher(scope.testScheduler),
            ).stateIn(scope.backgroundScope, SharingStarted.Eagerly, HomeState())
        }
    }

    private class ObservedNotes(
        private val notes: List<NoteWithItems>,
        private val onRead: () -> Unit,
    ) : AbstractList<NoteWithItems>() {
        override val size: Int get() = notes.size

        override fun get(index: Int): NoteWithItems {
            onRead()
            return notes[index]
        }
    }

    @Suppress("ktlint:standard:function-expression-body")
    private fun note(
        id: Long,
        title: String = "Note $id",
        pinned: Boolean = false,
        starred: Boolean = false,
        tags: List<String> = emptyList(),
    ): NoteWithItems {
        return NoteWithItems(
            note =
                NoteEntity(
                    id = id,
                    kind = NoteKind.NOTE,
                    title = title,
                    body = "",
                    colorIndex = 0,
                    starred = starred,
                    trashed = false,
                    createdAt = 0L,
                    updatedAt = 0L,
                    pinnedAt = if (pinned) 1_000L else null,
                    tags = tags,
                ),
            items = emptyList(),
        )
    }
}
