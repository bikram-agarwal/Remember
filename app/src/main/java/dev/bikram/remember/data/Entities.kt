package dev.bikram.remember.data

import androidx.annotation.StringRes
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.bikram.remember.R
import kotlinx.serialization.Serializable

@Serializable
enum class NoteKind { NOTE, LIST }

@Serializable
enum class Importance { LOW, DEFAULT, HIGH }

@StringRes
fun Importance.labelRes(): Int =
    when (this) {
        Importance.LOW -> R.string.importance_low
        Importance.DEFAULT -> R.string.importance_default
        Importance.HIGH -> R.string.importance_high
    }

@Serializable
enum class Visibility { SECRET, PRIVATE, PUBLIC }

@Serializable
enum class ActionType {
    CALL_NUMBER,
    SEND_MESSAGE,
    SEND_EMAIL,
    GET_DIRECTIONS,
    OPEN_LINK,
    OPEN_APP,
    OPEN_SHORTCUT,
    COPY_TO_CLIPBOARD,
    SHARE_CONTENT,
    MARK_AS_DONE,
    SNOOZE,
}

@StringRes
fun ActionType.labelRes(): Int =
    when (this) {
        ActionType.CALL_NUMBER -> R.string.action_type_call_number
        ActionType.SEND_MESSAGE -> R.string.action_type_send_message
        ActionType.SEND_EMAIL -> R.string.action_type_send_email
        ActionType.GET_DIRECTIONS -> R.string.action_type_get_directions
        ActionType.OPEN_LINK -> R.string.action_type_open_link
        ActionType.OPEN_APP -> R.string.action_type_open_app
        ActionType.OPEN_SHORTCUT -> R.string.action_type_open_shortcut
        ActionType.COPY_TO_CLIPBOARD -> R.string.action_type_copy_to_clipboard
        ActionType.SHARE_CONTENT -> R.string.action_type_share_content
        ActionType.MARK_AS_DONE -> R.string.action_type_mark_as_done
        ActionType.SNOOZE -> R.string.action_type_snooze
    }

@StringRes
fun ActionType.dataLabelRes(): Int =
    when (this) {
        ActionType.CALL_NUMBER,
        ActionType.SEND_MESSAGE,
        -> R.string.action_field_phone_number
        ActionType.SEND_EMAIL -> R.string.action_field_email_address
        ActionType.GET_DIRECTIONS -> R.string.action_field_address
        ActionType.OPEN_LINK -> R.string.action_field_url
        ActionType.OPEN_APP -> R.string.action_field_package
        ActionType.OPEN_SHORTCUT -> R.string.action_field_shortcut
        ActionType.COPY_TO_CLIPBOARD -> R.string.action_field_text_copy
        ActionType.SHARE_CONTENT -> R.string.action_field_text_share
        ActionType.MARK_AS_DONE -> R.string.action_field_mark_done_blank
        ActionType.SNOOZE -> R.string.action_field_snooze_blank
    }

@Serializable
data class NoteAction(
    val type: ActionType,
    val title: String,
    val details: String,
    val extra: String? = null,
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: NoteKind,
    val title: String,
    val body: String,
    val colorIndex: Int,
    @ColumnInfo(name = "pinned") val favorite: Boolean,
    val trashed: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val reminderAt: Long? = null,
    val importance: Importance = Importance.DEFAULT,
    val visibility: Visibility = Visibility.PRIVATE,
    val pictureUri: String? = null,
    /** JSON from [dev.bikram.remember.ui.common.HeroFraming.toJsonString]; null = legacy center crop. */
    val pictureHeroFraming: String? = null,
    val locked: Boolean = false,
    val iconKey: String? = null,
    val actions: List<NoteAction> = emptyList(),
    val tags: List<String> = emptyList(),
    val recurrence: RecurrenceRule? = null,
    /**
     * Distinct from [trashed]: archived notes are hidden from Home but remain searchable,
     * never auto-deleted, and have no favorite state. Mutually exclusive with [trashed]:
     * trashing an archived note clears its archive state, and vice versa.
     */
    val archived: Boolean = false,
    /** Timestamp (epoch ms) when the row transitioned to [trashed] = true. Used for the 30-day auto-sweep. */
    val trashedAt: Long? = null,
    /**
     * Wall-clock instant at which this note was marked done. Non-null = the note is in the
     * "Done" bucket on Home; cards render struck-through and the bottom-pinned section
     * collects them.
     *
     * Recurrence semantics: completing a recurring occurrence is NOT a transition to "done".
     * The repository call that handles "mark this fire done" rolls [reminderAt] forward via
     * [RecurrenceRule.nextAfter] and leaves [completedAt] null - the note stays active and
     * reappears in the Today/Upcoming bucket for the next occurrence. A note only enters
     * Done when it has no future occurrence (non-recurring, OR the rule has exhausted its
     * end condition).
     */
    val completedAt: Long? = null,
)

/**
 * Content-less FTS4 shadow table over [NoteEntity]. Only the text-bearing columns are
 * indexed; everything else stays in `notes`. Triggers in the migration keep it in sync
 * so application code doesn't have to maintain it manually.
 *
 * `tokenize = "unicode61 remove_diacritics 2"` gives diacritic-insensitive matching and
 * sensible word breaking across the user's mixed-language content.
 */
@Entity(tableName = "notes_fts")
@Fts4(
    contentEntity = NoteEntity::class,
    tokenizer = "unicode61",
    tokenizerArgs = ["remove_diacritics=2"],
)
data class NoteFtsEntity(
    val title: String,
    val body: String,
    val tags: String,
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["normalizedName"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val colorHex: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId"), Index("tagId")],
)
data class NoteTagCrossRef(
    val noteId: Long,
    val tagId: Long,
    val sortOrder: Int,
)

@Entity(
    tableName = "checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId"), Index("parentId")],
)
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val text: String,
    val checked: Boolean,
    /**
     * Weighted sort position. Items are ordered by ascending [sortOrder] within each of the
     * two logical sublists (active / completed). Using a Double lets us insert between two
     * existing rows without rewriting every neighbour's position -- new values are chosen
     * as the midpoint between siblings, or as max(sortOrder) + 1.0 when appended.
     */
    val sortOrder: Double,
    /**
     * Parent row id for one level of nesting. `null` means the row is a top-level parent.
     * Children always share the same [noteId] as their parent.
     */
    val parentId: Long? = null,
    /** 0 for top-level rows, 1 for children. Kept in sync with [parentId] presence. */
    val depth: Int = 0,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId")],
)
data class NoteAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String? = null,
)
