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
    entities = [NoteEntity::class, ChecklistItemEntity::class, NoteAttachmentEntity::class],
    version = 2,
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

        fun build(context: Context): RememberDatabase =
            Room.databaseBuilder(context, RememberDatabase::class.java, "remember.db")
                .addMigrations(migration1To2)
                .fallbackToDestructiveMigration(false)
                .build()
    }
}
