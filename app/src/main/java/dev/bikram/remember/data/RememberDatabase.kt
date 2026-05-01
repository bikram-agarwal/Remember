package dev.bikram.remember.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
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
    version = 1,
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
                .fallbackToDestructiveMigration(false)
                .build()
    }
}
