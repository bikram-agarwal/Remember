package com.example.checklist.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChecklistItem::class],
    version = 2,
    exportSchema = true,
)
abstract class ChecklistDatabase : RoomDatabase() {

    abstract fun checklistDao(): ChecklistDao

    companion object {
        @Volatile private var cached: ChecklistDatabase? = null

        fun get(context: Context): ChecklistDatabase {
            val existing = cached
            if (existing != null) return existing
            return synchronized(this) {
                val inner = cached
                if (inner != null) {
                    inner
                } else {
                    val built = Room.databaseBuilder(
                        context.applicationContext,
                        ChecklistDatabase::class.java,
                        "checklist.db",
                    )
                        .addMigrations(MIGRATION_1_2)
                        .build()
                    cached = built
                    built
                }
            }
        }
    }
}
