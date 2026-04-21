package dev.bikram.remember.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

class Converters {
    @TypeConverter fun fromKind(kind: NoteKind): String = kind.name
    @TypeConverter fun toKind(value: String): NoteKind = NoteKind.valueOf(value)

    @TypeConverter fun fromImportance(v: Importance): String = v.name
    @TypeConverter fun toImportance(value: String): Importance = Importance.valueOf(value)

    @TypeConverter fun fromVisibility(v: Visibility): String = v.name
    @TypeConverter fun toVisibility(value: String): Visibility = Visibility.valueOf(value)

    @TypeConverter
    fun fromActions(v: List<NoteAction>): String {
        val arr = JSONArray()
        v.forEach { a ->
            arr.put(
                JSONObject()
                    .put("t", a.type.name)
                    .put("ti", a.title)
                    .put("d", a.details)
                    .put("e", a.extra ?: JSONObject.NULL)
            )
        }
        return arr.toString()
    }

    @TypeConverter
    fun toActions(value: String): List<NoteAction> {
        if (value.isBlank()) return emptyList()
        val arr = JSONArray(value)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            NoteAction(
                type = ActionType.valueOf(o.getString("t")),
                title = o.getString("ti"),
                details = o.getString("d"),
                extra = if (o.isNull("e")) null else o.getString("e"),
            )
        }
    }

    @TypeConverter
    fun fromStringList(v: List<String>): String {
        val arr = JSONArray()
        v.forEach { arr.put(it) }
        return arr.toString()
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        val arr = JSONArray(value)
        return List(arr.length()) { arr.getString(it) }
    }

    @TypeConverter
    fun fromRecurrence(v: RecurrenceRule?): String? = RecurrenceRule.toJson(v)

    @TypeConverter
    fun toRecurrence(value: String?): RecurrenceRule? = RecurrenceRule.fromJson(value)
}

@Database(
    entities = [
        NoteEntity::class,
        ChecklistItemEntity::class,
        NoteAttachmentEntity::class,
        NoteFtsEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RememberDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun checklistItemDao(): ChecklistItemDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        /** Adds nullable hero framing JSON without recreating the notes table. */
        private val migration1To2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notes ADD COLUMN pictureHeroFraming TEXT",
                )
            }
        }

        /**
         * Adds hierarchy + weighted ordering columns to checklist_items and drops the legacy
         * integer `position` column. Uses a copy-and-swap because older bundled SQLite builds
         * do not support `ALTER TABLE ... DROP COLUMN` reliably. `sortOrder` is backfilled from
         * the old `position` so that existing lists keep their visual order after upgrade.
         */
        private val migration2To3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS checklist_items_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        noteId INTEGER NOT NULL,
                        text TEXT NOT NULL,
                        checked INTEGER NOT NULL,
                        sortOrder REAL NOT NULL,
                        parentId INTEGER,
                        depth INTEGER NOT NULL,
                        FOREIGN KEY(noteId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO checklist_items_new (id, noteId, text, checked, sortOrder, parentId, depth)
                    SELECT id, noteId, text, checked, CAST(position AS REAL), NULL, 0
                    FROM checklist_items
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE checklist_items")
                db.execSQL("ALTER TABLE checklist_items_new RENAME TO checklist_items")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_checklist_items_noteId ON checklist_items(noteId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_checklist_items_parentId ON checklist_items(parentId)",
                )
            }
        }

        /**
         * Adds the `archived` and `trashedAt` columns to notes, then creates the FTS4 virtual
         * shadow table plus the INSERT/UPDATE/DELETE triggers that keep `notes_fts` in sync.
         * `trashedAt` is backfilled from `updatedAt` for rows already in the trash so the
         * 30-day auto-sweep has a defensible reference time for pre-upgrade notes.
         */
        private val migration3To4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN trashedAt INTEGER")
                // Backfill so existing trashed rows age out deterministically rather than
                // sitting in the trash forever after an upgrade.
                db.execSQL("UPDATE notes SET trashedAt = updatedAt WHERE trashed = 1")

                // FTS4 virtual table shadowing `notes`. Room will (re)create it when
                // Database#version matches, but we create it explicitly here too so the
                // upgrade path works when Room's own init runs after migrations.
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING FTS4(
                        title TEXT NOT NULL,
                        body TEXT NOT NULL,
                        tags TEXT NOT NULL,
                        tokenize=unicode61 `remove_diacritics=2`,
                        content=`notes`
                    )
                    """.trimIndent(),
                )
                // Backfill the FTS index from existing notes.
                db.execSQL(
                    "INSERT INTO notes_fts(docid, title, body, tags) " +
                        "SELECT id, title, body, tags FROM notes",
                )
                // Sync triggers: insert, update, delete. FTS4 content-table mode (`content=notes`)
                // means we must populate the shadow table whenever `notes` changes.
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS notes_fts_bu BEFORE UPDATE ON notes BEGIN
                        DELETE FROM notes_fts WHERE docid = old.id;
                    END
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS notes_fts_bd BEFORE DELETE ON notes BEGIN
                        DELETE FROM notes_fts WHERE docid = old.id;
                    END
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS notes_fts_au AFTER UPDATE ON notes BEGIN
                        INSERT INTO notes_fts(docid, title, body, tags)
                        VALUES (new.id, new.title, new.body, new.tags);
                    END
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS notes_fts_ai AFTER INSERT ON notes BEGIN
                        INSERT INTO notes_fts(docid, title, body, tags)
                        VALUES (new.id, new.title, new.body, new.tags);
                    END
                    """.trimIndent(),
                )
            }
        }

        /**
         * Adds `completedAt` to notes for the task model: non-null means the note is in
         * the Done bucket. Existing rows back-fill to NULL (active), which is the right
         * default - we do not want pre-existing notes with past reminders to suddenly
         * appear as "Done" after upgrade.
         */
        private val migration4To5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN completedAt INTEGER")
            }
        }

        fun build(context: Context): RememberDatabase =
            Room.databaseBuilder(context, RememberDatabase::class.java, "remember.db")
                .addMigrations(migration1To2, migration2To3, migration3To4, migration4To5)
                .fallbackToDestructiveMigration(false)
                .build()
    }
}
