package dev.bikram.remember.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteDaoInstrumentedTest {
    private lateinit var database: RememberDatabase

    @Before
    fun create_database() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database =
            Room
                .inMemoryDatabaseBuilder(context, RememberDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun close_database() {
        database.close()
    }

    @Test
    fun checklist_items_are_ordered_by_weighted_sort_order() =
        runBlocking {
            val noteId = database.noteDao().insert(note(title = "Weekend list", kind = NoteKind.LIST))

            database.checklistItemDao().insert(
                ChecklistItemEntity(
                    noteId = noteId,
                    text = "Second",
                    checked = false,
                    sortOrder = 20.0,
                ),
            )
            database.checklistItemDao().insert(
                ChecklistItemEntity(
                    noteId = noteId,
                    text = "First",
                    checked = false,
                    sortOrder = 10.0,
                ),
            )

            val orderedItems = database.checklistItemDao().itemsFor(noteId)

            assertEquals(listOf("First", "Second"), orderedItems.map { item -> item.text })
        }

    @Test
    fun child_parent_relationship_survives_round_trip() =
        runBlocking {
            val noteId = database.noteDao().insert(note(title = "Project list", kind = NoteKind.LIST))
            val parentId =
                database.checklistItemDao().insert(
                    ChecklistItemEntity(
                        noteId = noteId,
                        text = "Parent",
                        checked = false,
                        sortOrder = 10.0,
                    ),
                )
            val childId =
                database.checklistItemDao().insert(
                    ChecklistItemEntity(
                        noteId = noteId,
                        text = "Child",
                        checked = false,
                        sortOrder = 20.0,
                        parentId = parentId,
                        depth = 1,
                    ),
                )

            val child =
                database
                    .checklistItemDao()
                    .itemsFor(noteId)
                    .first { item -> item.id == childId }

            assertEquals(parentId, child.parentId)
            assertEquals(1, child.depth)
        }

    @Test
    fun failed_multi_note_import_rolls_back_every_insert() =
        runBlocking {
            val originalNoteId = database.noteDao().insert(note(title = "Existing"))
            val repository =
                NoteRepository(
                    noteDao = database.noteDao(),
                    itemDao = database.checklistItemDao(),
                    attachmentDao = database.attachmentDao(),
                    database = database,
                )

            val importResult =
                runCatching {
                    repository.runImportTransaction {
                        database.noteDao().insert(note(title = "Imported one"))
                        database.noteDao().insert(note(title = "Imported two"))
                        error("Simulated import failure")
                    }
                }

            assertTrue(importResult.isFailure)
            assertEquals(listOf(originalNoteId), database.noteDao().allNoteIds())
        }

    @Test
    fun fts_search_returns_matching_active_notes() =
        runBlocking {
            val matchingNoteId = database.noteDao().insert(note(title = "Alpha project"))
            database.noteDao().insert(note(title = "Beta errands"))

            val searchResults = database.noteDao().searchNotes("alpha*").first()

            assertEquals(listOf(matchingNoteId), searchResults.map { noteWithItems -> noteWithItems.note.id })
        }

    @Test
    fun fts_search_matches_checklist_item_text() =
        runBlocking {
            val repository =
                NoteRepository(
                    noteDao = database.noteDao(),
                    itemDao = database.checklistItemDao(),
                    attachmentDao = database.attachmentDao(),
                    clock = { 1L },
                    database = database,
                    ioDispatcher = Dispatchers.Unconfined,
                    defaultDispatcher = Dispatchers.Unconfined,
                )
            val matchingNoteId =
                repository.createList(
                    title = "Groceries",
                    colorIndex = 0,
                    items = listOf("Almond milk"),
                )

            val searchResults = database.noteDao().searchNotes("almond*").first()

            assertEquals(listOf(matchingNoteId), searchResults.map { noteWithItems -> noteWithItems.note.id })
        }

    @Test
    fun fts_search_matches_attachment_display_name() =
        runBlocking {
            val repository =
                NoteRepository(
                    noteDao = database.noteDao(),
                    itemDao = database.checklistItemDao(),
                    attachmentDao = database.attachmentDao(),
                    clock = { 1L },
                    database = database,
                    ioDispatcher = Dispatchers.Unconfined,
                    defaultDispatcher = Dispatchers.Unconfined,
                )
            val noteId =
                repository.createNote(
                    title = "Receipts",
                    body = "",
                    colorIndex = 0,
                )
            repository.addAttachment(
                noteId = noteId,
                uri = "content://remember/attachments/receipt.pdf",
                displayName = "Hotel invoice.pdf",
                mimeType = "application/pdf",
            )

            val searchResults = database.noteDao().searchNotes("invoice*").first()

            assertEquals(listOf(noteId), searchResults.map { noteWithItems -> noteWithItems.note.id })
        }

    @Test
    fun fts_search_matches_action_title_and_details() =
        runBlocking {
            val repository =
                NoteRepository(
                    noteDao = database.noteDao(),
                    itemDao = database.checklistItemDao(),
                    attachmentDao = database.attachmentDao(),
                    clock = { 1L },
                    database = database,
                    ioDispatcher = Dispatchers.Unconfined,
                    defaultDispatcher = Dispatchers.Unconfined,
                )
            val noteId =
                repository.createNote(
                    title = "Contacts",
                    body = "",
                    colorIndex = 0,
                    options =
                        NoteOptions(
                            actions =
                                listOf(
                                    NoteAction(
                                        type = ActionType.CALL_NUMBER,
                                        title = "Call plumber",
                                        details = "555-0100",
                                    ),
                                ),
                        ),
                )

            val titleMatches = database.noteDao().searchNotes("plumber*").first()
            val detailsMatches = database.noteDao().searchNotes("555*").first()

            assertEquals(listOf(noteId), titleMatches.map { noteWithItems -> noteWithItems.note.id })
            assertEquals(listOf(noteId), detailsMatches.map { noteWithItems -> noteWithItems.note.id })
        }

    @Test
    fun tags_for_note_are_returned_in_assignment_order() =
        runBlocking {
            val noteId = database.noteDao().insert(note(title = "Tagged note"))
            val secondTagId =
                database.tagDao().insertTag(
                    TagEntity(
                        name = "Later",
                        normalizedName = "later",
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                )
            val firstTagId =
                database.tagDao().insertTag(
                    TagEntity(
                        name = "Soon",
                        normalizedName = "soon",
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                )

            assertTrue(secondTagId > 0L)
            assertTrue(firstTagId > 0L)
            database.tagDao().insertAssignments(
                listOf(
                    NoteTagCrossRef(noteId = noteId, tagId = secondTagId, sortOrder = 2),
                    NoteTagCrossRef(noteId = noteId, tagId = firstTagId, sortOrder = 1),
                ),
            )

            val orderedTags = database.tagDao().tagsForNote(noteId)

            assertEquals(listOf("Soon", "Later"), orderedTags.map { tag -> tag.name })
        }

    @Test
    fun observe_active_tags_excludes_archived_and_trashed_notes() =
        runBlocking {
            val activeNoteId = database.noteDao().insert(note(title = "Active note"))
            val archivedNoteId = database.noteDao().insert(note(title = "Archived note", archived = true))
            val trashedNoteId = database.noteDao().insert(note(title = "Trashed note", trashed = true))
            val activeTagId = insertTag("Active")
            val archivedTagId = insertTag("Archived")
            val trashedTagId = insertTag("Trashed")

            database.tagDao().insertAssignments(
                listOf(
                    NoteTagCrossRef(noteId = activeNoteId, tagId = activeTagId, sortOrder = 1),
                    NoteTagCrossRef(noteId = archivedNoteId, tagId = archivedTagId, sortOrder = 1),
                    NoteTagCrossRef(noteId = trashedNoteId, tagId = trashedTagId, sortOrder = 1),
                ),
            )

            val activeTags = database.tagDao().observeActiveTags().first()

            assertEquals(listOf("Active"), activeTags.map { tag -> tag.name })
        }

    private suspend fun insertTag(name: String): Long {
        val normalizedName = name.lowercase()
        val tagId =
            database.tagDao().insertTag(
                TagEntity(
                    name = name,
                    normalizedName = normalizedName,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            )
        assertTrue(tagId > 0L)
        return tagId
    }

    private fun note(
        title: String,
        kind: NoteKind = NoteKind.NOTE,
        archived: Boolean = false,
        trashed: Boolean = false,
    ): NoteEntity =
        NoteEntity(
            kind = kind,
            title = title,
            body = "",
            colorIndex = 0,
            starred = false,
            trashed = trashed,
            createdAt = 1L,
            updatedAt = 1L,
            archived = archived,
            trashedAt = if (trashed) 1L else null,
        )
}
