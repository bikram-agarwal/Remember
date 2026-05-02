package dev.bikram.remember.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class NoteWithItems(
    @androidx.room.Embedded val note: NoteEntity,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "noteId")
    val items: List<ChecklistItemEntity>,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "noteId")
    val attachments: List<NoteAttachmentEntity> = emptyList(),
)

@Dao
interface NoteDao {
    /**
     * "Active" now means neither trashed nor archived -- archive is a hidden-but-kept state
     * that behaves like a separate shelf from Home.
     */
    @Transaction
    @Query("SELECT * FROM notes WHERE trashed = 0 AND archived = 0 ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<NoteWithItems>>

    @Transaction
    @Query("SELECT * FROM notes WHERE trashed = 1 ORDER BY updatedAt DESC")
    fun observeTrashed(): Flow<List<NoteWithItems>>

    @Transaction
    @Query("SELECT * FROM notes WHERE archived = 1 AND trashed = 0 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<NoteWithItems>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun observe(id: Long): Flow<NoteWithItems?>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): NoteWithItems?

    @Transaction
    @Query(
        """
        SELECT * FROM notes
        WHERE trashed = 0
          AND archived = 0
          AND completedAt IS NULL
          AND reminderAt IS NOT NULL
          AND reminderAt <= :untilMillis
        ORDER BY reminderAt ASC
        """,
    )
    suspend fun activeRemindersUntil(untilMillis: Long): List<NoteWithItems>

    /**
     * Full-text search over active notes (no trashed, no archived). Uses an inner join against
     * the FTS4 virtual table `notes_fts` via the `MATCH` operator. The query is passed straight
     * through to FTS -- callers are responsible for sanitising (see [NoteRepository.searchNotes]).
     */
    @Transaction
    @Query(
        """
        SELECT notes.* FROM notes
        JOIN notes_fts ON notes.id = notes_fts.docid
        WHERE notes_fts MATCH :ftsQuery
          AND notes.trashed = 0
          AND notes.archived = 0
        ORDER BY notes.updatedAt DESC
        """,
    )
    fun searchNotes(ftsQuery: String): Flow<List<NoteWithItems>>

    /**
     * FTS over archived (non-trashed) notes. Drives the collapsible "Archive (N)" section
     * inside the main-tab search - these notes are still the user's but not in the active
     * list, so they are surfaced only when the user expands the section.
     */
    @Transaction
    @Query(
        """
        SELECT notes.* FROM notes
        JOIN notes_fts ON notes.id = notes_fts.docid
        WHERE notes_fts MATCH :ftsQuery
          AND notes.archived = 1
          AND notes.trashed = 0
        ORDER BY notes.updatedAt DESC
        """,
    )
    fun searchArchived(ftsQuery: String): Flow<List<NoteWithItems>>

    /**
     * FTS over trashed notes. Powers the collapsible "Trash (N)" section inside main-tab
     * search so users can rediscover a recently-deleted note without leaving the tab.
     */
    @Transaction
    @Query(
        """
        SELECT notes.* FROM notes
        JOIN notes_fts ON notes.id = notes_fts.docid
        WHERE notes_fts MATCH :ftsQuery
          AND notes.trashed = 1
        ORDER BY notes.trashedAt DESC
        """,
    )
    fun searchTrashed(ftsQuery: String): Flow<List<NoteWithItems>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("UPDATE notes SET favorite = :favorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(
        id: Long,
        favorite: Boolean,
        updatedAt: Long,
    )

    @Query("UPDATE notes SET tags = :tags WHERE id = :id")
    suspend fun updateTagCache(
        id: Long,
        tags: List<String>,
    )

    @Query(
        "UPDATE notes " +
            "SET trashed = :trashed, " +
            "archived = 0, " +
            "trashedAt = CASE WHEN :trashed THEN :updatedAt ELSE NULL END, " +
            "updatedAt = :updatedAt " +
            "WHERE id = :id",
    )
    suspend fun setTrashed(
        id: Long,
        trashed: Boolean,
        updatedAt: Long,
    )

    /**
     * Archive is just another shelf: it cannot coexist with trash, but user intent
     * like Favorite must survive archive and undo.
     */
    @Query(
        "UPDATE notes " +
            "SET archived = :archived, " +
            "trashed = 0, " +
            "trashedAt = NULL, " +
            "updatedAt = :updatedAt " +
            "WHERE id = :id",
    )
    suspend fun setArchived(
        id: Long,
        archived: Boolean,
        updatedAt: Long,
    )

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT id FROM notes")
    suspend fun allNoteIds(): List<Long>

    @Query("SELECT COUNT(*) FROM notes WHERE pictureUri = :uri")
    suspend fun countPictureUri(uri: String): Int

    @Query("SELECT id FROM notes WHERE trashed = 1")
    suspend fun trashedNoteIds(): List<Long>

    @Query("SELECT id FROM notes WHERE archived = 1 AND trashed = 0")
    suspend fun archivedNoteIds(): List<Long>

    @Query("SELECT id FROM notes WHERE trashed = 1 AND trashedAt IS NOT NULL AND trashedAt < :cutoff")
    suspend fun trashedNoteIdsOlderThan(cutoff: Long): List<Long>

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()

    @Query("DELETE FROM notes WHERE trashed = 1")
    suspend fun emptyTrash()

    @Query("DELETE FROM notes WHERE trashed = 1 AND trashedAt IS NOT NULL AND trashedAt < :cutoff")
    suspend fun deleteTrashedOlderThan(cutoff: Long)
}

@Dao
interface ChecklistItemDao {
    @Query("SELECT * FROM checklist_items WHERE noteId = :noteId ORDER BY sortOrder ASC")
    suspend fun itemsFor(noteId: Long): List<ChecklistItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ChecklistItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChecklistItemEntity>)

    @Update
    suspend fun update(item: ChecklistItemEntity)

    @Query("DELETE FROM checklist_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM checklist_items WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY id ASC")
    suspend fun attachmentsFor(noteId: Long): List<NoteAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: NoteAttachmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(attachments: List<NoteAttachmentEntity>)

    @Query("SELECT * FROM attachments WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NoteAttachmentEntity?

    @Query("SELECT COUNT(*) FROM attachments WHERE uri = :uri")
    suspend fun countByUri(uri: String): Int

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM attachments WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY normalizedName ASC")
    fun observeAllTags(): Flow<List<TagEntity>>

    @Query(
        """
        SELECT DISTINCT tags.* FROM tags
        INNER JOIN note_tags ON tags.id = note_tags.tagId
        INNER JOIN notes ON notes.id = note_tags.noteId
        WHERE notes.trashed = 0 AND notes.archived = 0
        ORDER BY tags.normalizedName ASC
        """,
    )
    fun observeActiveTags(): Flow<List<TagEntity>>

    @Query(
        """
        SELECT tags.* FROM tags
        INNER JOIN note_tags ON tags.id = note_tags.tagId
        WHERE note_tags.noteId = :noteId
        ORDER BY note_tags.sortOrder ASC, tags.normalizedName ASC
        """,
    )
    suspend fun tagsForNote(noteId: Long): List<TagEntity>

    @Query("SELECT * FROM tags WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): TagEntity?

    @Query("SELECT noteId FROM note_tags WHERE tagId = :tagId")
    suspend fun noteIdsForTag(tagId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Update
    suspend fun updateTag(tag: TagEntity)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun deleteAssignmentsForNote(noteId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignments(assignments: List<NoteTagCrossRef>)
}
