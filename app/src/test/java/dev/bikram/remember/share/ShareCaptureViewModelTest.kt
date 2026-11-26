package dev.bikram.remember.share

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.SavedStateHandle
import dev.bikram.remember.data.AttachmentDao
import dev.bikram.remember.data.ChecklistItemDao
import dev.bikram.remember.data.ChecklistItemEntity
import dev.bikram.remember.data.DefaultNotePrefs
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.MediaCopyResult
import dev.bikram.remember.data.NoteAttachmentEntity
import dev.bikram.remember.data.NoteDao
import dev.bikram.remember.data.NoteEntity
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteReminder
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.NoteTagCacheRow
import dev.bikram.remember.data.NoteTagLink
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.Visibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class ShareCaptureViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var appScope: CoroutineScope
    private lateinit var tempCacheDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        appScope = CoroutineScope(SupervisorJob() + testDispatcher)
        tempCacheDir = Files.createTempDirectory("remember_share_test_").toFile()
    }

    @After
    fun tearDown() {
        appScope.cancel()
        Dispatchers.resetMain()
        tempCacheDir.deleteRecursively()
    }

    @Test
    fun quickCapture_convertsHtmlAppliesDefaultsMetadataAndPersistsNote() =
        runTest(testDispatcher) {
            val store = FakeShareRepositoryStore()
            val viewModel =
                createViewModel(
                    store = store,
                    defaultVisibility = Visibility.PRIVATE,
                    defaultImportance = Importance.HIGH,
                )

            viewModel.initializeIfNeeded(
                ShareRoute.QuickCapture(
                    initialTitle = "Shared Article",
                    rawHtml = "<p>Hello <b>world</b> with <a href=\"https://example.com\">site</a></p>",
                    plainText = "Hello world with site",
                    imageUris = emptyList(),
                ),
            )
            advanceUntilIdle()

            assertEquals("Shared Article", viewModel.title.value)
            assertEquals("Hello **world** with [site](https://example.com)", viewModel.body.value)
            assertEquals(Importance.HIGH, viewModel.importance.value)

            viewModel.saveTagsWithColors(listOf("reading"), emptyMap())
            viewModel.setReminders(listOf(NoteReminder(reminderAt = 50_000L)))
            viewModel.saveQuickClose()
            advanceUntilIdle()

            val saved = store.notes.values.single()
            assertEquals(NoteKind.NOTE, saved.kind)
            assertEquals("Shared Article", saved.title)
            assertEquals("Hello **world** with [site](https://example.com)", saved.body)
            assertEquals(Importance.HIGH, saved.importance)
            assertEquals(Visibility.PRIVATE, saved.visibility)
            assertEquals(listOf("reading"), saved.tags)
            assertEquals(50_000L, saved.reminderAt)
        }

    @Test
    fun singleImage_defaultAttachModeVsBannerMode_persistsExpectedTargetAndCleansStaging() =
        runTest(testDispatcher) {
            // 1. Default mode: ATTACH_TO_NOTE
            val attachStore = FakeShareRepositoryStore()
            val attachVm = createViewModel(store = attachStore)
            attachVm.initializeIfNeeded(
                ShareRoute.QuickCapture(
                    initialTitle = "Receipt",
                    rawHtml = null,
                    plainText = "Lunch receipt",
                    imageUris = emptyList(),
                ),
            )
            advanceUntilIdle()

            val stagedAttachSessionDir = File(File(tempCacheDir, "share_staging"), attachVm.sessionId).apply { mkdirs() }
            val stagedAttachFile = File(stagedAttachSessionDir, "receipt.jpg").apply { writeText("img-bytes") }
            attachVm.setStagedImagesForTest(
                listOf(
                    StagedShareImage(
                        id = "receipt.jpg",
                        file = stagedAttachFile,
                        displayName = "receipt.jpg",
                        mimeType = "image/jpeg",
                    ),
                ),
            )

            attachVm.saveAndOpenInEditor()
            val editorEvent = attachVm.events.first()
            advanceUntilIdle()

            assertTrue(editorEvent is ShareCaptureUiEvent.SavedForEditor)
            val attachNoteId = (editorEvent as ShareCaptureUiEvent.SavedForEditor).noteId
            val attachSaved = attachStore.notes.getValue(attachNoteId)
            assertNull(attachSaved.pictureUri)
            val savedAttachments = attachStore.attachmentsByNote[attachNoteId].orEmpty()
            assertEquals(1, savedAttachments.size)
            assertEquals("content://dev.bikram.remember.fileprovider/note_attachments/$attachNoteId/receipt.jpg", savedAttachments.single().uri)
            assertFalse(stagedAttachSessionDir.exists())

            // 2. Toggled mode: USE_AS_BANNER
            val bannerStore = FakeShareRepositoryStore()
            val bannerVm = createViewModel(store = bannerStore)
            bannerVm.initializeIfNeeded(
                ShareRoute.QuickCapture(
                    initialTitle = "Poster",
                    rawHtml = null,
                    plainText = "",
                    imageUris = emptyList(),
                ),
            )
            advanceUntilIdle()

            val stagedBannerSessionDir = File(File(tempCacheDir, "share_staging"), bannerVm.sessionId).apply { mkdirs() }
            val stagedBannerFile = File(stagedBannerSessionDir, "poster.png").apply { writeText("png-bytes") }
            bannerVm.setStagedImagesForTest(
                listOf(
                    StagedShareImage(
                        id = "poster.png",
                        file = stagedBannerFile,
                        displayName = "poster.png",
                        mimeType = "image/png",
                    ),
                ),
            )
            bannerVm.setSingleImageMode(SingleImagePlacement.USE_AS_BANNER)
            bannerVm.saveQuickClose()
            advanceUntilIdle()

            val bannerSaved = bannerStore.notes.values.single()
            assertEquals("content://dev.bikram.remember.fileprovider/note_heroes/poster.png", bannerSaved.pictureUri)
            assertTrue(bannerStore.attachmentsByNote[bannerSaved.id].orEmpty().isEmpty())
            assertFalse(stagedBannerSessionDir.exists())
        }

    @Test
    fun strictChecklistBody_createsChecklistWithUntitledFallbackAndDiscardCleansStaging() =
        runTest(testDispatcher) {
            val store = FakeShareRepositoryStore()
            val viewModel = createViewModel(store = store)
            viewModel.initializeIfNeeded(
                ShareRoute.QuickCapture(
                    initialTitle = "",
                    rawHtml = null,
                    plainText = "- [ ] Milk\n- [x] Eggs",
                    imageUris = emptyList(),
                ),
            )
            advanceUntilIdle()

            viewModel.saveQuickClose()
            advanceUntilIdle()

            val savedList = store.notes.values.single()
            assertEquals(NoteKind.LIST, savedList.kind)
            assertEquals("Untitled list", savedList.title)
            val items = store.itemsByNote[savedList.id].orEmpty()
            assertEquals(2, items.size)
            assertEquals("Milk", items[0].text)
            assertFalse(items[0].checked)
            assertEquals("Eggs", items[1].text)
            assertTrue(items[1].checked)

            // Verify discardCapture cleans up staged session directory
            val discardVm = createViewModel(store = store)
            val discardSessionDir = File(File(tempCacheDir, "share_staging"), discardVm.sessionId).apply { mkdirs() }
            File(discardSessionDir, "temp.jpg").writeText("temp")
            assertTrue(discardSessionDir.exists())

            discardVm.discardCapture()
            advanceUntilIdle()
            assertFalse(discardSessionDir.exists())
        }

    private fun createViewModel(
        store: FakeShareRepositoryStore,
        defaultVisibility: Visibility = Visibility.DEFAULT,
        defaultImportance: Importance = Importance.DEFAULT,
    ): ShareCaptureViewModel {
        val prefsDataStore =
            object : DataStore<Preferences> {
                override val data: Flow<Preferences> =
                    flowOf(
                        preferencesOf(
                            stringPreferencesKey("default_visibility") to defaultVisibility.name,
                            stringPreferencesKey("default_importance") to defaultImportance.name,
                        ),
                    )

                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                    error("Read-only test DataStore")
                }
            }

        return ShareCaptureViewModel(
            noteRepository = store.repository(testDispatcher),
            defaultNotePrefs = DefaultNotePrefs(prefsDataStore),
            applicationScope = appScope,
            savedStateHandle = SavedStateHandle(),
            cacheDir = tempCacheDir,
            untitledNoteTitle = "Untitled note",
            untitledListTitle = "Untitled list",
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher,
            stageImagesAction = { _, _ -> ShareImageStageResult(emptyList(), 0) },
            promoteBannerAction = { file, _ ->
                file.delete()
                "content://dev.bikram.remember.fileprovider/note_heroes/${file.name}"
            },
            promoteAttachmentAction = { noteId, file, displayName, mimeType ->
                file.delete()
                MediaCopyResult(
                    uriString = "content://dev.bikram.remember.fileprovider/note_attachments/$noteId/$displayName",
                    copiedToAppStorage = true,
                    mimeType = mimeType,
                    displayName = displayName,
                )
            },
        )
    }
}

private class FakeShareRepositoryStore {
    var nextNoteId = 1L
    var nextItemId = 1L
    var nextAttachmentId = 1L
    val notes = linkedMapOf<Long, NoteEntity>()
    val itemsByNote = linkedMapOf<Long, MutableList<ChecklistItemEntity>>()
    val attachmentsByNote = linkedMapOf<Long, MutableList<NoteAttachmentEntity>>()

    fun repository(dispatcher: kotlinx.coroutines.CoroutineDispatcher): NoteRepository =
        NoteRepository(
            noteDao = FakeShareNoteDao(this),
            itemDao = FakeShareItemDao(this),
            attachmentDao = FakeShareAttachmentDao(this),
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )

    fun snapshot(id: Long): NoteWithItems? {
        val note = notes[id] ?: return null
        return NoteWithItems(
            note = note,
            items = itemsByNote[id]?.toList().orEmpty(),
            attachments = attachmentsByNote[id]?.toList().orEmpty(),
        )
    }
}

private class FakeShareNoteDao(
    private val store: FakeShareRepositoryStore,
) : NoteDao {
    override fun observeActive(): Flow<List<NoteWithItems>> = flowOf(emptyList())

    override fun observeTrashed(): Flow<List<NoteWithItems>> = flowOf(emptyList())

    override fun observeArchived(): Flow<List<NoteWithItems>> = flowOf(emptyList())

    override fun observe(id: Long): Flow<NoteWithItems?> = flowOf(store.snapshot(id))

    override fun searchNotes(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())

    override fun searchArchived(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())

    override fun searchTrashed(ftsQuery: String): Flow<List<NoteWithItems>> = flowOf(emptyList())

    override suspend fun get(id: Long): NoteWithItems? = store.snapshot(id)

    override suspend fun allNotes(): List<NoteWithItems> = store.notes.keys.mapNotNull(store::snapshot)

    override suspend fun activeRemindersUntil(untilMillis: Long): List<NoteWithItems> = emptyList()

    override suspend fun activeStarred(): List<NoteWithItems> = emptyList()

    override suspend fun insert(note: NoteEntity): Long {
        val id = if (note.id > 0L) note.id else store.nextNoteId++
        store.notes[id] = note.copy(id = id)
        return id
    }

    override suspend fun update(note: NoteEntity) {
        store.notes[note.id] = note
    }

    override suspend fun setStarred(
        id: Long,
        starred: Boolean,
        updatedAt: Long,
    ) = Unit

    override suspend fun updateTagCache(
        id: Long,
        tags: List<String>,
    ) {
        store.notes[id]?.let { existing ->
            store.notes[id] = existing.copy(tags = tags)
        }
    }

    override suspend fun setTrashed(
        id: Long,
        trashed: Boolean,
        updatedAt: Long,
    ) = Unit

    override suspend fun setArchived(
        id: Long,
        archived: Boolean,
        updatedAt: Long,
    ) = Unit

    override suspend fun deleteById(id: Long) {
        store.notes.remove(id)
    }

    override suspend fun allNoteIds(): List<Long> = store.notes.keys.toList()

    override suspend fun noteTagCaches(): List<NoteTagCacheRow> = emptyList()

    override suspend fun noteTagLinks(): List<NoteTagLink> = emptyList()

    override suspend fun countPictureUri(uri: String): Int = 0

    override suspend fun trashedNoteIds(): List<Long> = emptyList()

    override suspend fun archivedNoteIds(): List<Long> = emptyList()

    override suspend fun trashedNoteIdsOlderThan(cutoff: Long): List<Long> = emptyList()

    override suspend fun deleteAllNotes() = Unit

    override suspend fun emptyTrash() = Unit

    override suspend fun deleteTrashedOlderThan(cutoff: Long) = Unit
}

private class FakeShareItemDao(
    private val store: FakeShareRepositoryStore,
) : ChecklistItemDao {
    override suspend fun itemsFor(noteId: Long): List<ChecklistItemEntity> = store.itemsByNote[noteId]?.toList().orEmpty()

    override suspend fun insert(item: ChecklistItemEntity): Long {
        val id = if (item.id > 0L) item.id else store.nextItemId++
        val persisted = item.copy(id = id)
        store.itemsByNote.getOrPut(item.noteId) { mutableListOf() }.add(persisted)
        return id
    }

    override suspend fun insertAll(items: List<ChecklistItemEntity>) {
        items.forEach { item -> insert(item) }
    }

    override suspend fun update(item: ChecklistItemEntity) = Unit

    override suspend fun deleteById(id: Long) = Unit

    override suspend fun deleteForNote(noteId: Long) {
        store.itemsByNote[noteId] = mutableListOf()
    }
}

private class FakeShareAttachmentDao(
    private val store: FakeShareRepositoryStore,
) : AttachmentDao {
    override suspend fun attachmentsFor(noteId: Long): List<NoteAttachmentEntity> = store.attachmentsByNote[noteId]?.toList().orEmpty()

    override suspend fun insert(attachment: NoteAttachmentEntity): Long {
        val id = if (attachment.id > 0L) attachment.id else store.nextAttachmentId++
        val persisted = attachment.copy(id = id)
        store.attachmentsByNote.getOrPut(attachment.noteId) { mutableListOf() }.add(persisted)
        return id
    }

    override suspend fun insertAll(attachments: List<NoteAttachmentEntity>) {
        attachments.forEach { attachment -> insert(attachment) }
    }

    override suspend fun getById(id: Long): NoteAttachmentEntity? =
        store.attachmentsByNote.values
            .flatten()
            .firstOrNull { attachment -> attachment.id == id }

    override suspend fun countByUri(uri: String): Int =
        store.attachmentsByNote.values
            .flatten()
            .count { attachment -> attachment.uri == uri }

    override suspend fun deleteById(id: Long) {
        store.attachmentsByNote.values.forEach { list ->
            list.removeAll { attachment -> attachment.id == id }
        }
    }

    override suspend fun deleteForNote(noteId: Long) {
        store.attachmentsByNote.remove(noteId)
    }
}
