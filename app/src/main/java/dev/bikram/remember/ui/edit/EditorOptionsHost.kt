package dev.bikram.remember.ui.edit

import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.bikram.remember.R
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteAction
import dev.bikram.remember.data.NoteAttachmentEntity
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.RecurrenceRule
import dev.bikram.remember.ui.common.FullScreenHeroImageOverlay
import dev.bikram.remember.ui.common.HeroFraming
import dev.bikram.remember.ui.common.HeroFramingEditorDialog
import java.io.File
import dev.bikram.remember.data.Visibility as NoteVisibility

/**
 * Shared options panel wiring for every content editor. Notes and checklists can
 * supply different body surfaces, but every option row and option sheet should
 * enter through this component so behavior cannot drift between the two editors.
 */
@Composable
fun EditorOptionsPanel(
    reminderAt: Long?,
    recurrence: RecurrenceRule?,
    importance: Importance,
    visibility: NoteVisibility,
    pictureUri: String?,
    actions: List<NoteAction>,
    tags: List<String>,
    attachments: List<NoteAttachmentEntity>,
    notificationsAllowed: Boolean,
    readOnly: Boolean,
    starred: Boolean,
    onOpenReminder: () -> Unit,
    onImportanceChange: (Importance) -> Unit,
    onVisibilityChange: (NoteVisibility) -> Unit,
    onOpenPicture: () -> Unit,
    onOpenActions: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenAttachmentsSheet: () -> Unit,
    onPickAttachment: () -> Unit,
) {
    OptionsPanel(
        reminderAt = reminderAt,
        recurrence = recurrence,
        importance = importance,
        visibility = visibility,
        pictureUri = pictureUri,
        actions = actions,
        tags = tags,
        attachments = attachments,
        onOpenReminder = if (readOnly) ({}) else onOpenReminder,
        reminderPermissionMissing = reminderAt != null && !notificationsAllowed,
        onSetImportance = if (readOnly) ({ _ -> }) else onImportanceChange,
        onSetVisibility = if (readOnly) ({ _ -> }) else onVisibilityChange,
        onOpenPicture = if (readOnly) ({}) else onOpenPicture,
        onOpenActions = if (readOnly) ({}) else onOpenActions,
        onOpenTags = if (readOnly) ({}) else onOpenTags,
        onOpenAttachments =
            if (readOnly) {
                {}
            } else {
                {
                    if (attachments.isEmpty()) {
                        onPickAttachment()
                    } else {
                        onOpenAttachmentsSheet()
                    }
                }
            },
        readOnly = readOnly,
        starred = starred,
    )
}

@Composable
fun EditorOptionSheets(
    contentKind: NoteKind,
    reminderPickerOpen: Boolean,
    iconPickerOpen: Boolean,
    actionsPickerOpen: Boolean,
    tagsPickerOpen: Boolean,
    attachmentsPickerOpen: Boolean,
    notificationPermissionSheetOpen: Boolean,
    deleteForeverConfirmOpen: Boolean,
    pendingHeroSession: Pair<String, File?>?,
    pictureViewer: Pair<String, Long>?,
    readOnly: Boolean,
    activeTagSuggestions: List<String>,
    attachments: List<NoteAttachmentEntity>,
    currentReminderAt: Long?,
    currentRecurrence: RecurrenceRule?,
    currentIconKey: String?,
    currentActions: List<NoteAction>,
    currentTags: List<String>,
    heroImageContentDescription: String,
    onReminderChange: (Long?, RecurrenceRule?) -> Unit,
    onIconKeyChange: (String?) -> Unit,
    onActionsChange: (List<NoteAction>) -> Unit,
    onTagsWithColorsChange: (List<String>, Map<String, String>) -> Unit,
    onEditExistingTag: (String, String, String?, Boolean) -> Unit,
    onAddAttachment: (Uri, String, String?) -> Unit,
    onRemoveAttachment: (Long) -> Unit,
    onHeroCommitted: (String, HeroFraming) -> Unit,
    onPictureChange: (String?) -> Unit,
    onDeleteForever: () -> Unit,
    onDismissReminder: () -> Unit,
    onDismissIcon: () -> Unit,
    onDismissActions: () -> Unit,
    onDismissTags: () -> Unit,
    onDismissAttachments: () -> Unit,
    onDismissNotificationPermission: () -> Unit,
    onDismissPendingHero: () -> Unit,
    onDismissDeleteForever: () -> Unit,
    onDismissPictureViewer: () -> Unit,
) {
    if (reminderPickerOpen) {
        ReminderPickerSheet(
            initialMillis = currentReminderAt,
            initialRule = currentRecurrence,
            onConfirm = { at, rule ->
                onReminderChange(at, rule)
                onDismissReminder()
            },
            onDismiss = onDismissReminder,
        )
    }
    if (iconPickerOpen) {
        IconPicker(
            current = currentIconKey,
            onPick = {
                onIconKeyChange(it)
                onDismissIcon()
            },
            onDismiss = onDismissIcon,
            isChecklist = contentKind == NoteKind.LIST,
        )
    }
    if (actionsPickerOpen) {
        ActionPicker(
            current = currentActions,
            onConfirm = {
                onActionsChange(it)
                onDismissActions()
            },
            onDismiss = onDismissActions,
        )
    }
    if (tagsPickerOpen) {
        TagEditorSheet(
            initial = currentTags,
            availableTags = activeTagSuggestions,
            onConfirm = onTagsWithColorsChange,
            onEditExistingTag = onEditExistingTag,
            onDismiss = onDismissTags,
        )
    }
    if (attachmentsPickerOpen) {
        AttachmentsSheet(
            attachments = attachments,
            onDismiss = onDismissAttachments,
            onAdd = onAddAttachment,
            onRemove = onRemoveAttachment,
        )
    }
    if (notificationPermissionSheetOpen) {
        NotificationPermissionRequiredSheet(
            onDismiss = onDismissNotificationPermission,
            titleRes = R.string.notification_permission_required_title,
            bodyRes = R.string.notification_permission_required_body,
        )
    }
    pendingHeroSession?.let { (pickedUri, copiedFile) ->
        HeroFramingEditorDialog(
            imageUri = pickedUri,
            pendingCopiedFile = copiedFile,
            initialFraming = null,
            onDismiss = {
                copiedFile?.delete()
                onDismissPendingHero()
            },
            onConfirm = { framing ->
                onHeroCommitted(pickedUri, framing)
                onDismissPendingHero()
            },
        )
    }
    if (deleteForeverConfirmOpen) {
        AlertDialog(
            onDismissRequest = onDismissDeleteForever,
            title = { Text(stringResource(R.string.edit_delete_forever_dialog_title)) },
            text = { Text(stringResource(R.string.edit_delete_forever_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismissDeleteForever()
                        onDeleteForever()
                    },
                ) {
                    Text(stringResource(R.string.edit_delete_forever_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteForever) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    FullScreenHeroImageOverlay(
        visible = pictureViewer != null,
        imageUri = pictureViewer?.first,
        imageCacheRevision = pictureViewer?.second ?: 0L,
        imageContentDescription = heroImageContentDescription,
        sharedElementKey = pictureViewer?.first?.let { uri -> "hero-image-$uri" },
        onDismiss = onDismissPictureViewer,
        onDelete =
            if (readOnly) {
                null
            } else {
                {
                    onPictureChange(null)
                    onDismissPictureViewer()
                }
            },
    )
}
