package dev.bikram.remember.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupDataSafetyTest {
    @Test
    fun backup_snapshot_includes_active_archived_and_trashed_shelves() {
        val activeNote = noteWithItems(id = 1L, title = "Active")
        val archivedNote = noteWithItems(id = 2L, title = "Archived", archived = true)
        val trashedNote = noteWithItems(id = 3L, title = "Trashed", trashed = true)

        val backupNotes =
            notesForBackup(
                activeNotes = listOf(activeNote),
                archivedNotes = listOf(archivedNote),
                trashedNotes = listOf(trashedNote),
            )

        assertEquals(listOf(1L, 2L, 3L), backupNotes.map { note -> note.note.id })
    }

    private fun noteWithItems(
        id: Long,
        title: String,
        archived: Boolean = false,
        trashed: Boolean = false,
    ): NoteWithItems =
        NoteWithItems(
            note =
                NoteEntity(
                    id = id,
                    kind = NoteKind.NOTE,
                    title = title,
                    body = "",
                    colorIndex = 0,
                    starred = false,
                    trashed = trashed,
                    archived = archived,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            items = emptyList(),
        )
}
