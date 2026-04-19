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

    @Transaction
    @Query("SELECT * FROM notes WHERE trashed = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeActive(): Flow<List<NoteWithItems>>

    @Transaction
    @Query("SELECT * FROM notes WHERE trashed = 1 ORDER BY updatedAt DESC")
    fun observeTrashed(): Flow<List<NoteWithItems>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun observe(id: Long): Flow<NoteWithItems?>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): NoteWithItems?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("UPDATE notes SET pinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET trashed = :trashed, pinned = CASE WHEN :trashed THEN 0 ELSE pinned END, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setTrashed(id: Long, trashed: Boolean, updatedAt: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT id FROM notes")
    suspend fun allNoteIds(): List<Long>

    @Query("SELECT id FROM notes WHERE trashed = 1")
    suspend fun trashedNoteIds(): List<Long>

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()

    @Query("DELETE FROM notes WHERE trashed = 1")
    suspend fun emptyTrash()
}

@Dao
interface ChecklistItemDao {

    @Query("SELECT * FROM checklist_items WHERE noteId = :noteId ORDER BY position ASC")
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

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM attachments WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)
}
