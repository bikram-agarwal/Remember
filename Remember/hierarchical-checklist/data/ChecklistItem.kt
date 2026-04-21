package com.example.checklist.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted checklist row.
 *
 *  - [sortOrder] drives position in both the active and completed lists. Never use
 *    list indices. When items are inserted between two neighbors, assign
 *    `(before.sortOrder + after.sortOrder) / 2.0` so neighbors never need to shift.
 *  - [parentId] + [depth] give one level of nesting. [depth] is 0 for a top-level
 *    item and 1 for a child. No depth > 1 is allowed.
 *  - Toggling [isChecked] moves a row between the two sections; [sortOrder] is
 *    preserved across toggles so items return to their original relative spot.
 */
@Entity(
    tableName = "checklist_item",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistItem::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("parentId"), Index("sortOrder")],
)
data class ChecklistItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "isChecked")
    val isChecked: Boolean = false,

    @ColumnInfo(name = "sortOrder")
    val sortOrder: Double = 0.0,

    @ColumnInfo(name = "parentId")
    val parentId: Int? = null,

    @ColumnInfo(name = "depth")
    val depth: Int = 0,
)
