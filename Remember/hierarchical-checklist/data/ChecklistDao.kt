package com.example.checklist.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {

    /** Emits the full list every time any row changes. Sorted in Kotlin so the
     *  UI layer can decide between flat, grouped, or section-split presentations. */
    @Query("SELECT * FROM checklist_item ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<ChecklistItem>>

    @Query("SELECT COALESCE(MAX(sortOrder), 0.0) FROM checklist_item")
    suspend fun maxSortOrder(): Double

    @Query("SELECT * FROM checklist_item WHERE id = :id LIMIT 1")
    suspend fun findById(id: Int): ChecklistItem?

    @Query("SELECT * FROM checklist_item WHERE parentId = :parentId ORDER BY sortOrder ASC")
    suspend fun childrenOf(parentId: Int): List<ChecklistItem>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ChecklistItem): Long

    @Update
    suspend fun update(item: ChecklistItem)

    @Update
    suspend fun updateAll(items: List<ChecklistItem>)

    @Delete
    suspend fun delete(item: ChecklistItem)

    /** Sets isChecked on the row with [id] AND every row whose parentId equals [id].
     *  Used when a parent is checked - cascades to children in one transaction. */
    @Query("UPDATE checklist_item SET isChecked = :checked WHERE id = :id OR parentId = :id")
    suspend fun setCheckedForFamily(id: Int, checked: Boolean)

    @Transaction
    suspend fun checkFamily(parentId: Int, checked: Boolean) {
        setCheckedForFamily(parentId, checked)
    }
}
