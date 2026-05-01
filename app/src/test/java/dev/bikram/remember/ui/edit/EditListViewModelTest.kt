package dev.bikram.remember.ui.edit

import androidx.lifecycle.SavedStateHandle
import dev.bikram.remember.data.AttachmentDao
import dev.bikram.remember.data.ChecklistItemDao
import dev.bikram.remember.data.ChecklistItemEntity
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteAttachmentEntity
import dev.bikram.remember.data.NoteDao
import dev.bikram.remember.data.NoteEntity
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.Visibility
import dev.bikram.remember.ui.nav.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class EditListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun invalid_reorder_does_not_make_new_draft_saveable() =
        runTest {
            val store = FakeRepositoryStore()
            val viewModel = editListViewModel(store)

            viewModel.reorderWithin(visibleIds = listOf(1L), fromIndex = 0, toIndex = 1)
            val undoAction = viewModel.saveIfNeeded("Untitled")

            assertNull(undoAction)
            assertEquals(0, store.notes.size)
        }

    @Test
    fun draft_save_preserves_parent_local_keys() =
        runTest {
            val store = FakeRepositoryStore()
            val viewModel = editListViewModel(store)

            viewModel.addItem()
            viewModel.updateItemText(localId = -1L, text = "Parent")
            viewModel.addItem()
            viewModel.updateItemText(localId = -2L, text = "Child")
            viewModel.indent(localId = -2L)

            viewModel.saveIfNeeded("Untitled")

            val savedItems = store.itemsByNote.getValue(1L)
            val parent = savedItems.first { item -> item.text == "Parent" }
            val child = savedItems.first { item -> item.text == "Child" }
            assertEquals(parent.id, child.parentId)
            assertEquals(1, child.depth)
        }

    @Test
    fun existing_list_hierarchy_change_is_saved() =
        runTest {
            val store = FakeRepositoryStore()
            store.notes[1L] = noteEntity(id = 1L, title = "Existing list")
            store.itemsByNote[1L] =
                mutableListOf(
                    ChecklistItemEntity(
                        id = 1L,
                        noteId = 1L,
                        text = "Parent",
                        checked = false,
                        sortOrder = 10.0,
                    ),
                    ChecklistItemEntity(
                        id = 2L,
                        noteId = 1L,
                        text = "Child",
                        checked = false,
                        sortOrder = 20.0,
                    ),
                )

            val viewModel = editListViewModel(store, noteId = 1L)
            advanceUntilIdle()
            viewModel.indent(localId = 2L)

            viewModel.saveIfNeeded("Untitled")

            val savedItems = store.itemsByNote.getValue(1L)
            val parent = savedItems.first { item -> item.text == "Parent" }
            val child = savedItems.first { item -> item.text == "Child" }
            assertEquals(parent.id, child.parentId)
            assertEquals(1, child.depth)
            assertEquals(1, store.deleteForNoteCount)
        }

    private fun editListViewModel(
        store: FakeRepositoryStore,
        noteId: Long? = null,
    ): EditListViewModel {
        val savedStateHandle =
            if (noteId == null) {
                SavedStateHandle()
            } else {
                SavedStateHandle(mapOf(Routes.ARG_ID to noteId))
            }
        return EditListViewModel(
            repository = store.repository(),
            savedStateHandle = savedStateHandle,
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class FakeRepositoryStore {
    var nextNoteId = 1L
    var nextItemId = 1L
    var deleteForNoteCount = 0
    val notes = LinkedHashMap<Long, NoteEntity>()
    val itemsByNote = LinkedHashMap<Long, MutableList<ChecklistItemEntity>>()

    fun repository(): NoteRepository =
        NoteRepository(
            noteDao = FakeNoteDao(this),
            itemDao = FakeChecklistItemDao(this),
            attachmentDao = FakeAttachmentDao(),
            clock = { 1_000L },
        )

    fun noteWithItems(noteId: Long): NoteWithItems? {
        val note = notes[noteId] ?: return null
        val items =
            itemsByNote[noteId]
                ?.sortedBy { item -> item.sortOrder }
                ?: emptyList()
        return NoteWithItems(note = note, items = items)
    }
}

private class FakeNoteDao(
    private val store: FakeRepositoryStore,
) : NoteDao {
    override fun observeActive(): Flow<List<NoteWithItems>> = flowOf(store.notes.keys.mapNotNull { noteId -> store.noteWithItems(noteId) })

    override fun observeTrashed(): Flow<List<NoteWithItems>> = flowOf(emptyList())

    override fun observeArchived(): Flow<List<NoteWithItems>> = flowOf(emptyList())

    override fun observe(id: Long): Flow<NoteWithItems?> = flowOf(store.noteWithItems(id))

    override suspend fun get(id: Long): NoteWithItems? = store.noteWithItems(id)

    override suspend fun activeRemindersUntil(untilMillis: Long): List<NoteWithItems> = emptyList()

    override fun searchNotes(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())

    override fun searchArchived(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())

    override fun searchTrashed(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())

    override suspend fun insert(note: NoteEntity): Long {
        val noteId = if (note.id > 0L) note.id else store.nextNoteId++
        store.nextNoteId = maxOf(store.nextNoteId, noteId + 1L)
        store.notes[noteId] = note.copy(id = noteId)
        return noteId
    }

    override suspend fun update(note: NoteEntity) {
        store.notes[note.id] = note
    }

    override suspend fun setFavorite(
        id: Long,
        favorite: Boolean,
        updatedAt: Long,
    ) {
        store.notes[id]?.let { note ->
            store.notes[id] = note.copy(favorite = favorite, updatedAt = updatedAt)
        }
    }

    override suspend fun updateTagCache(
        id: Long,
        tags: List<String>,
    ) {
        store.notes[id]?.let { note ->
            store.notes[id] = note.copy(tags = tags)
        }
    }

    override suspend fun setTrashed(
        id: Long,
        trashed: Boolean,
        updatedAt: Long,
    ) {
        store.notes[id]?.let { note ->
            store.notes[id] = note.copy(trashed = trashed, updatedAt = updatedAt)
        }
    }

    override suspend fun setArchived(
        id: Long,
        archived: Boolean,
        updatedAt: Long,
    ) {
        store.notes[id]?.let { note ->
            store.notes[id] = note.copy(archived = archived, updatedAt = updatedAt)
        }
    }

    override suspend fun deleteById(id: Long) {
        store.notes.remove(id)
        store.itemsByNote.remove(id)
    }

    override suspend fun allNoteIds(): List<Long> = store.notes.keys.toList()

    override suspend fun countPictureUri(uri: String): Int = 0

    override suspend fun trashedNoteIds(): List<Long> = emptyList()

    override suspend fun archivedNoteIds(): List<Long> = emptyList()

    override suspend fun trashedNoteIdsOlderThan(cutoff: Long): List<Long> = emptyList()

    override suspend fun deleteAllNotes() {
        store.notes.clear()
        store.itemsByNote.clear()
    }

    override suspend fun emptyTrash() = Unit

    override suspend fun deleteTrashedOlderThan(cutoff: Long) = Unit
}

private class FakeChecklistItemDao(
    private val store: FakeRepositoryStore,
) : ChecklistItemDao {
    override suspend fun itemsFor(noteId: Long): List<ChecklistItemEntity> =
        store.itemsByNote[noteId]
            ?.sortedBy { item -> item.sortOrder }
            ?: emptyList()

    override suspend fun insert(item: ChecklistItemEntity): Long {
        val itemId = if (item.id > 0L) item.id else store.nextItemId++
        store.nextItemId = maxOf(store.nextItemId, itemId + 1L)
        val savedItem = item.copy(id = itemId)
        store.itemsByNote.getOrPut(savedItem.noteId) { mutableListOf() }.add(savedItem)
        return itemId
    }

    override suspend fun insertAll(items: List<ChecklistItemEntity>) {
        items.forEach { item -> insert(item) }
    }

    override suspend fun update(item: ChecklistItemEntity) {
        val noteItems = store.itemsByNote[item.noteId] ?: return
        val existingIndex = noteItems.indexOfFirst { existingItem -> existingItem.id == item.id }
        if (existingIndex >= 0) {
            noteItems[existingIndex] = item
        }
    }

    override suspend fun deleteById(id: Long) {
        store.itemsByNote.values.forEach { noteItems ->
            noteItems.removeAll { item -> item.id == id }
        }
    }

    override suspend fun deleteForNote(noteId: Long) {
        store.deleteForNoteCount += 1
        store.itemsByNote[noteId] = mutableListOf()
    }
}

private class FakeAttachmentDao : AttachmentDao {
    override suspend fun attachmentsFor(noteId: Long): List<NoteAttachmentEntity> = emptyList()

    override suspend fun insert(attachment: NoteAttachmentEntity): Long = attachment.id

    override suspend fun insertAll(attachments: List<NoteAttachmentEntity>) = Unit

    override suspend fun getById(id: Long): NoteAttachmentEntity? = null

    override suspend fun countByUri(uri: String): Int = 0

    override suspend fun deleteById(id: Long) = Unit

    override suspend fun deleteForNote(noteId: Long) = Unit
}

private fun noteEntity(
    id: Long,
    title: String,
): NoteEntity =
    NoteEntity(
        id = id,
        kind = NoteKind.LIST,
        title = title,
        body = "",
        colorIndex = 0,
        favorite = false,
        trashed = false,
        createdAt = 1L,
        updatedAt = 1L,
        importance = Importance.DEFAULT,
        visibility = Visibility.PRIVATE,
    )
