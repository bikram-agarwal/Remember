package dev.bikram.remember.data

import androidx.annotation.StringRes
import androidx.room.Entity
import dev.bikram.remember.R
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class NoteKind { NOTE, LIST }

enum class Importance { LOW, DEFAULT, HIGH }

@StringRes
fun Importance.labelRes(): Int = when (this) {
    Importance.LOW -> R.string.importance_low
    Importance.DEFAULT -> R.string.importance_default
    Importance.HIGH -> R.string.importance_high
}

enum class Visibility { SECRET, PRIVATE, PUBLIC }

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
}

@StringRes
fun ActionType.labelRes(): Int = when (this) {
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
}

@StringRes
fun ActionType.dataLabelRes(): Int = when (this) {
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
}

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
    val pinned: Boolean,
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
)

@Entity(
    tableName = "checklist_items",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("noteId")],
)
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val text: String,
    val checked: Boolean,
    val position: Int,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("noteId")],
)
data class NoteAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String? = null,
)
