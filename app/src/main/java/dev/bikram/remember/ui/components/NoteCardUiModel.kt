package dev.bikram.remember.ui.components

import androidx.compose.runtime.Immutable
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.RememberReservedTags
import dev.bikram.remember.data.getActiveReminders
import dev.bikram.remember.data.pinned
import dev.bikram.remember.ui.edit.NoteIcon
import dev.bikram.remember.ui.edit.resolveNoteIcon
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList

@Immutable
data class NoteCardUiModel(
    val id: Long,
    val kind: NoteKind,
    val title: String,
    val body: String,
    val starred: Boolean,
    /**
     * Pinned to the top of Home. Separate from [starred] on purpose: starred is a favorite
     * (filter chip + its own widget), pinned is placement only. The two cues must stay
     * visually distinct - starred owns the card border + watermark, pinned gets its own
     * leading glyph in the metadata row.
     */
    val pinned: Boolean,
    val completed: Boolean,
    val icon: NoteIcon,
    val pictureUri: String?,
    val pictureHeroFraming: String?,
    val pictureCacheRevision: Long,
    val reminderAt: Long?,
    val remindersCount: Int,
    val recurring: Boolean,
    val hasAttachment: Boolean,
    val visibleTags: PersistentList<String>,
    val checklistPreviewItems: PersistentList<NoteCardChecklistItemUiModel>,
    val checklistHiddenItemCount: Int,
)

@Immutable
data class NoteCardChecklistItemUiModel(
    val text: String,
    val details: String,
    val checked: Boolean,
    val depth: Int,
)

fun NoteWithItems.toNoteCardUiModel(
    checklistPreviewLimit: Int = 2,
): NoteCardUiModel {
    val orderedChecklistItems =
        items.filter { !it.checked }.sortedBy { it.sortOrder } +
            items.filter { it.checked }.sortedBy { it.sortOrder }
    return NoteCardUiModel(
        id = note.id,
        kind = note.kind,
        title = note.title,
        body = note.body,
        starred = note.starred || note.tags.contains(RememberReservedTags.STARRED),
        pinned = note.pinned,
        completed = note.completedAt != null,
        icon = resolveNoteIcon(note.iconKey, note.kind),
        pictureUri = note.pictureUri,
        pictureHeroFraming = note.pictureHeroFraming,
        pictureCacheRevision = note.updatedAt,
        reminderAt = note.reminderAt,
        remindersCount = note.getActiveReminders().size,
        recurring = note.recurrence != null,
        hasAttachment = attachments.isNotEmpty(),
        visibleTags = RememberReservedTags.userVisibleTags(note.tags).toPersistentList(),
        checklistPreviewItems =
            orderedChecklistItems
                .take(checklistPreviewLimit)
                .map { item ->
                    NoteCardChecklistItemUiModel(
                        text = item.text,
                        details = item.details,
                        checked = item.checked,
                        depth = item.depth,
                    )
                }.toPersistentList(),
        checklistHiddenItemCount = (orderedChecklistItems.size - checklistPreviewLimit).coerceAtLeast(0),
    )
}
