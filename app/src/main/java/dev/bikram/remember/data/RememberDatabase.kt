package dev.bikram.remember.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    @TypeConverter fun fromKind(kind: NoteKind): String = kind.name

    @TypeConverter fun toKind(value: String): NoteKind = NoteKind.valueOf(value)

    @TypeConverter fun fromImportance(v: Importance): String = v.name

    @TypeConverter fun toImportance(value: String): Importance = Importance.valueOf(value)

    @TypeConverter fun fromVisibility(v: Visibility): String = v.name

    @TypeConverter fun toVisibility(value: String): Visibility = Visibility.valueOf(value)

    @TypeConverter
    fun fromActions(v: List<NoteAction>): String = json.encodeToString(v)

    @TypeConverter
    fun toActions(value: String): List<NoteAction> {
        if (value.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<NoteAction>>(value) }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun fromStringList(v: List<String>): String = json.encodeToString(v)

    @TypeConverter
    fun toStringList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
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
        TagEntity::class,
        NoteTagCrossRef::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RememberDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun checklistItemDao(): ChecklistItemDao

    abstract fun attachmentDao(): AttachmentDao

    abstract fun tagDao(): TagDao

    companion object {
        fun build(context: Context): RememberDatabase =
            Room
                .databaseBuilder(context, RememberDatabase::class.java, "remember.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(false)
                .build()

        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE notes ADD COLUMN checklistText TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE notes ADD COLUMN attachmentText TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE notes ADD COLUMN actionsText TEXT NOT NULL DEFAULT ''")
                    db.execSQL(
                        """
                        UPDATE notes
                        SET checklistText = COALESCE(
                            (
                                SELECT GROUP_CONCAT(text, ' ')
                                FROM checklist_items
                                WHERE checklist_items.noteId = notes.id
                            ),
                            ''
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        UPDATE notes
                        SET attachmentText = COALESCE(
                            (
                                SELECT GROUP_CONCAT(displayName, ' ')
                                FROM attachments
                                WHERE attachments.noteId = notes.id
                            ),
                            ''
                        )
                        """.trimIndent(),
                    )
                    db.query(
                        """
                        SELECT rowid, actions
                        FROM notes
                        WHERE actions IS NOT NULL AND actions != '' AND actions != '[]'
                        """.trimIndent(),
                    ).use { cursor ->
                        val rowIdColumn = cursor.getColumnIndex("rowid")
                        val actionsColumn = cursor.getColumnIndex("actions")
                        while (cursor.moveToNext()) {
                            val rowId = cursor.getLong(rowIdColumn)
                            val actionsJson = cursor.getString(actionsColumn) ?: continue
                            val actionsText = actionsSearchTextFromJson(actionsJson)
                            db.execSQL(
                                "UPDATE notes SET actionsText = ? WHERE rowid = ?",
                                arrayOf<Any?>(actionsText, rowId),
                            )
                        }
                    }
                    dropNotesFtsSyncTriggers(db)
                    db.execSQL("DROP TABLE IF EXISTS notes_fts")
                    createNotesFtsTable(db)
                    createNotesFtsSyncTriggers(db)
                    db.execSQL(
                        """
                        INSERT INTO notes_fts(docid, title, body, tags, checklistText, attachmentText, actionsText)
                        SELECT rowid, title, body, tags, checklistText, attachmentText, actionsText FROM notes
                        """.trimIndent(),
                    )
                }
            }

        private fun dropNotesFtsSyncTriggers(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_notes_fts_BEFORE_UPDATE")
            db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_notes_fts_BEFORE_DELETE")
            db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_notes_fts_AFTER_UPDATE")
            db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_notes_fts_AFTER_INSERT")
        }

        private fun createNotesFtsTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS `notes_fts` USING FTS4(
                    `title` TEXT NOT NULL,
                    `body` TEXT NOT NULL,
                    `tags` TEXT NOT NULL,
                    `checklistText` TEXT NOT NULL,
                    `attachmentText` TEXT NOT NULL,
                    `actionsText` TEXT NOT NULL,
                    tokenize=unicode61 `remove_diacritics=2`,
                    content=`notes`
                )
                """.trimIndent(),
            )
        }

        private fun createNotesFtsSyncTriggers(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_BEFORE_UPDATE
                BEFORE UPDATE ON notes
                BEGIN
                    DELETE FROM notes_fts WHERE docid=OLD.rowid;
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_BEFORE_DELETE
                BEFORE DELETE ON notes
                BEGIN
                    DELETE FROM notes_fts WHERE docid=OLD.rowid;
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_AFTER_UPDATE
                AFTER UPDATE ON notes
                BEGIN
                    INSERT INTO notes_fts(docid, title, body, tags, checklistText, attachmentText, actionsText)
                    VALUES (NEW.rowid, NEW.title, NEW.body, NEW.tags, NEW.checklistText, NEW.attachmentText, NEW.actionsText);
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_AFTER_INSERT
                AFTER INSERT ON notes
                BEGIN
                    INSERT INTO notes_fts(docid, title, body, tags, checklistText, attachmentText, actionsText)
                    VALUES (NEW.rowid, NEW.title, NEW.body, NEW.tags, NEW.checklistText, NEW.attachmentText, NEW.actionsText);
                END
                """.trimIndent(),
            )
        }
    }
}
