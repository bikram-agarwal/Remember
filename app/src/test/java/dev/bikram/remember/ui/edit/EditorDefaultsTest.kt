package dev.bikram.remember.ui.edit

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.SavedStateHandle
import dev.bikram.remember.data.DefaultNotePrefs
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteOptions
import dev.bikram.remember.data.Visibility
import dev.bikram.remember.ui.nav.Routes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorDefaultsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun newDraftAndQueuedSaveWaitForDefaults() =
        runTest {
            for (isList in listOf(false, true)) {
                val store = FakeRepositoryStore()
                val dataStore = DelayedDefaultsDataStore()
                val editor = createEditor(isList, store, dataStore)
                assertFalse(editor.loaded.value)
                editor.setTitle("New draft")
                val save = async { editor.saveIfNeeded("Untitled") }
                runCurrent()
                assertFalse(save.isCompleted)
                assertTrue(store.notes.isEmpty())

                dataStore.release(Visibility.SECRET, Importance.HIGH)
                runCurrent()
                save.await()

                assertTrue(editor.loaded.value)
                val saved = store.notes.values.single()
                assertEquals(Visibility.SECRET, saved.visibility)
                assertEquals(Importance.HIGH, saved.importance)
                assertNull(saved.reminderAt)
                assertTrue(saved.reminders.isEmpty())
            }
        }

    @Test
    fun lateDefaultsPreserveExplicitChoicesIncludingNormal() =
        runTest {
            for (isList in listOf(false, true)) {
                for (chooseNormal in listOf(false, true)) {
                    val dataStore = DelayedDefaultsDataStore()
                    val editor = createEditor(isList, FakeRepositoryStore(), dataStore)
                    runCurrent()
                    val visibility = if (chooseNormal) Visibility.DEFAULT else Visibility.SECRET
                    val importance = if (chooseNormal) Importance.DEFAULT else Importance.HIGH
                    editor.setVisibility(visibility)
                    editor.setImportance(importance)

                    dataStore.release(Visibility.PRIVATE, Importance.LOW)
                    runCurrent()

                    assertEquals(visibility, editor.visibility.value)
                    assertEquals(importance, editor.importance.value)
                    assertTrue(editor.loaded.value)
                }
            }
        }

    @Test
    fun defaultsAloneDoNotPersistAnEmptyDraft() =
        runTest {
            for (isList in listOf(false, true)) {
                val store = FakeRepositoryStore()
                val dataStore = DelayedDefaultsDataStore()
                val editor = createEditor(isList, store, dataStore)
                dataStore.release(Visibility.SECRET, Importance.HIGH)
                runCurrent()

                assertFalse(editor.hasUnsavedChanges.value)
                assertNull(editor.saveIfNeeded("Untitled"))
                assertTrue(store.notes.isEmpty())
            }
        }

    @Test
    fun existingNotesKeepTheirOptionsWithoutWaitingForDefaults() =
        runTest {
            for (isList in listOf(false, true)) {
                val store = FakeRepositoryStore()
                val options = NoteOptions(visibility = Visibility.PRIVATE, importance = Importance.LOW)
                val noteId =
                    if (isList) {
                        store.repository().createList("Existing", 0, emptyList(), options)
                    } else {
                        store.repository().createNote("Existing", "Body", 0, options)
                    }
                val editor = createEditor(isList, store, DelayedDefaultsDataStore(), noteId)
                runCurrent()

                assertTrue(editor.loaded.value)
                assertEquals(Visibility.PRIVATE, editor.visibility.value)
                assertEquals(Importance.LOW, editor.importance.value)
            }
        }

    private fun createEditor(
        isList: Boolean,
        store: FakeRepositoryStore,
        dataStore: DelayedDefaultsDataStore,
        noteId: Long? = null,
    ): BaseEditorViewModel {
        val savedState = SavedStateHandle(mapOf(Routes.ARG_ID to noteId))
        val prefs = DefaultNotePrefs(dataStore)
        return if (isList) {
            EditListViewModel(store.repository(), savedStateHandle = savedState, defaultNotePrefs = prefs)
        } else {
            EditNoteViewModel(store.repository(), savedStateHandle = savedState, defaultNotePrefs = prefs)
        }
    }
}

private class DelayedDefaultsDataStore : DataStore<Preferences> {
    private val initial = CompletableDeferred<Preferences>()
    override val data: Flow<Preferences> = flow { emit(initial.await()) }

    fun release(
        visibility: Visibility,
        importance: Importance,
    ) {
        initial.complete(
            preferencesOf(
                stringPreferencesKey("default_visibility") to visibility.name,
                stringPreferencesKey("default_importance") to importance.name,
            ),
        )
    }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        error("Editor initialization must only read defaults")
    }
}
