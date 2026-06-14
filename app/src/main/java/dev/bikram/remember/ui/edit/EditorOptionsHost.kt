package dev.bikram.remember.ui.edit

import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import dev.bikram.remember.R
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteAction
import dev.bikram.remember.data.NoteAttachmentEntity
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteReminder
import dev.bikram.remember.ui.common.FullScreenHeroImageOverlay
import dev.bikram.remember.ui.common.HeroFraming
import dev.bikram.remember.ui.components.RememberConfirmDialog
import java.io.File
import dev.bikram.remember.data.Visibility as NoteVisibility

/**
 * Shared options panel wiring for every content editor. Notes and checklists can
 * supply different body surfaces, but every option row and option sheet should
 * enter through this component so behavior cannot drift between the two editors.
 */
@Composable
fun EditorOptionsPanel(
    reminders: List<NoteReminder>,
    importance: Importance,
    visibility: NoteVisibility,
    pictureUri: String?,
    actions: List<NoteAction>,
    tags: List<String>,
    attachments: List<NoteAttachmentEntity>,
    notificationsAllowed: Boolean,
    readOnly: Boolean,
    starred: Boolean,
    createdAt: Long?,
    updatedAt: Long?,
    onOpenReminder: () -> Unit,
    onImportanceChange: (Importance) -> Unit,
    onVisibilityChange: (NoteVisibility) -> Unit,
    onOpenPicture: () -> Unit,
    onBrowsePictureWithApp: () -> Unit,
    onOpenActions: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenAttachmentsSheet: () -> Unit,
    onPickAttachment: () -> Unit,
) {
    OptionsPanel(
        reminders = reminders,
        importance = importance,
        visibility = visibility,
        pictureUri = pictureUri,
        actions = actions,
        tags = tags,
        attachments = attachments,
        onOpenReminder = if (readOnly) ({}) else onOpenReminder,
        reminderPermissionMissing = reminders.isNotEmpty() && !notificationsAllowed,
        onSetImportance = if (readOnly) ({ _ -> }) else onImportanceChange,
        onSetVisibility = if (readOnly) ({ _ -> }) else onVisibilityChange,
        onOpenPicture = if (readOnly) ({}) else onOpenPicture,
        onBrowsePictureWithApp = if (readOnly) ({}) else onBrowsePictureWithApp,
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
        createdAt = createdAt,
        updatedAt = updatedAt,
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
    heroImagePicker: HeroImagePickerController,
    pendingHeroSession: Pair<String, File?>?,
    pictureViewer: Pair<String, Long>?,
    currentPictureHeroFraming: String?,
    readOnly: Boolean,
    activeTagSuggestions: List<String>,
    attachments: List<NoteAttachmentEntity>,
    currentReminders: List<NoteReminder>,
    currentIconKey: String?,
    currentActions: List<NoteAction>,
    currentTags: List<String>,
    heroImageContentDescription: String,
    onReminderChange: (List<NoteReminder>) -> Unit,
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
            initialReminders = currentReminders,
            onConfirm = { reminders ->
                onReminderChange(reminders)
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
    if (deleteForeverConfirmOpen) {
        RememberConfirmDialog(
            title = stringResource(R.string.edit_delete_forever_dialog_title),
            text = stringResource(R.string.edit_delete_forever_dialog_body),
            confirmLabel = stringResource(R.string.edit_delete_forever_dialog_confirm),
            onConfirm = {
                onDismissDeleteForever()
                onDeleteForever()
            },
            onDismiss = onDismissDeleteForever,
            destructive = true,
        )
    }

    val pendingHeroUri = pendingHeroSession?.first
    val activeViewerUri = pendingHeroUri ?: pictureViewer?.first
    FullScreenHeroImageOverlay(
        visible = activeViewerUri != null,
        imageUri = activeViewerUri,
        imageCacheRevision = if (pendingHeroUri != null) 0L else pictureViewer?.second ?: 0L,
        imageContentDescription = heroImageContentDescription,
        sharedElementKey = activeViewerUri?.let { uri -> "hero-image-$uri" },
        initialFraming =
            if (pendingHeroUri == null) {
                remember(currentPictureHeroFraming) {
                    HeroFraming.fromJsonString(currentPictureHeroFraming)
                }
            } else {
                null
            },
        startInReframeMode = pendingHeroUri != null,
        dismissOnCancelReframe = pendingHeroUri != null,
        dismissAfterCommit = pendingHeroUri == null,
        onDismiss = {
            pendingHeroSession?.second?.delete()
            if (pendingHeroUri != null) {
                onDismissPendingHero()
            } else {
                onDismissPictureViewer()
            }
        },
        onReplace =
            if (readOnly) {
                null
            } else {
                {
                    pendingHeroSession?.second?.delete()
                    if (pendingHeroUri != null) {
                        onDismissPendingHero()
                    } else {
                        onDismissPictureViewer()
                    }
                    heroImagePicker.pickWithPhotoPicker()
                }
            },
        onReplaceLongClick =
            if (readOnly) {
                null
            } else {
                {
                    pendingHeroSession?.second?.delete()
                    if (pendingHeroUri != null) {
                        onDismissPendingHero()
                    } else {
                        onDismissPictureViewer()
                    }
                    heroImagePicker.browseWithApp()
                }
            },
        onCommitFraming = { framing ->
            activeViewerUri?.let { uri ->
                onHeroCommitted(uri, framing)
            }
            if (pendingHeroUri != null) {
                onDismissPendingHero()
            }
        },
        onDelete =
            if (readOnly) {
                null
            } else {
                {
                    pendingHeroSession?.second?.delete()
                    if (pendingHeroUri != null) {
                        onDismissPendingHero()
                    } else {
                        onPictureChange(null)
                        onDismissPictureViewer()
                    }
                }
            },
        onEdit =
            if (readOnly) {
                null
            } else {
                {}
            },
    )
}
