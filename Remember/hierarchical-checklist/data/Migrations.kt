package com.example.checklist.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from the flat "v1" schema (id, text, isChecked) to the hierarchical
 * "v2" schema that adds sortOrder, parentId, and depth.
 *
 * Existing rows are seeded with `sortOrder = id * 1.0` so their historical
 * insertion order is preserved without an explicit reorder by the user.
 *
 * If your existing table name or columns differ, adjust the ALTER statements;
 * the column additions themselves are idempotent-safe only on fresh upgrades,
 * so don't run this twice.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE checklist_item ADD COLUMN sortOrder REAL NOT NULL DEFAULT 0.0"
        )
        db.execSQL(
            "ALTER TABLE checklist_item ADD COLUMN parentId INTEGER DEFAULT NULL"
        )
        db.execSQL(
            "ALTER TABLE checklist_item ADD COLUMN depth INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "UPDATE checklist_item SET sortOrder = CAST(id AS REAL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_checklist_item_parentId " +
                "ON checklist_item(parentId)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_checklist_item_sortOrder " +
                "ON checklist_item(sortOrder)"
        )
    }
}
