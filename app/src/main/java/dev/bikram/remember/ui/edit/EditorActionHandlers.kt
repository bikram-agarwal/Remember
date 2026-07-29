package dev.bikram.remember.ui.edit

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.bikram.remember.R
import dev.bikram.remember.notifications.canPostNotifications
import dev.bikram.remember.ui.theme.LocalSnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class EditorActionHandlers(
    val saveAndShowToast: () -> Unit,
    val saveAndBack: () -> Unit,
    val saveAndNavigateUp: () -> Unit,
    val archiveAndBack: () -> Unit,
    val notifyOrRequestPermission: () -> Unit,
    val unarchive: () -> Unit,
    val trashAndBack: () -> Unit,
    val restore: () -> Unit,
    val deleteForeverAndBack: () -> Unit,
)

internal fun flushThenSaveAndNavigate(
    flushPendingEdits: () -> Unit,
    launchSave: () -> Unit,
    navigate: () -> Unit,
) {
    flushPendingEdits()
    launchSave()
    navigate()
}

@Composable
internal fun rememberEditorActionHandlers(
    appScope: CoroutineScope,
    archived: Boolean,
    trashed: Boolean,
    untitledName: String,
    onBack: () -> Unit,
    onNavigateUp: () -> Unit,
    onNotificationPermissionRequired: () -> Unit,
    saveIfNeeded: suspend (String) -> (suspend () -> Unit)?,
    archiveCurrent: suspend (String) -> Unit,
    trashCurrent: suspend () -> Unit,
    unarchiveCurrent: suspend () -> Unit,
    restoreFromTrashCurrent: suspend () -> Unit,
    deleteForeverCurrent: suspend () -> Unit,
    fireNotification: suspend (Context, String) -> Unit,
    flushPendingEdits: () -> Unit = {},
): EditorActionHandlers {
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val changesSavedMsg = stringResource(R.string.changes_saved)
    val undoMsg = stringResource(R.string.common_undo)
    val msgArchived = pluralStringResource(R.plurals.bulk_action_archived, 1, 1)
    val msgTrashed = pluralStringResource(R.plurals.bulk_action_trashed, 1, 1)
    val msgUnarchived = pluralStringResource(R.plurals.bulk_action_unarchived, 1, 1)
    val msgRestored = pluralStringResource(R.plurals.bulk_action_restored, 1, 1)

    suspend fun showUndoableSave() {
        val undoAction = saveIfNeeded(untitledName)
        if (undoAction != null) {
            val result =
                snackbarHostState.showSnackbar(
                    message = changesSavedMsg,
                    actionLabel = undoMsg,
                    withDismissAction = true,
                    duration = SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) {
                undoAction()
            }
        }
    }

    return EditorActionHandlers(
        saveAndShowToast = {
            flushPendingEdits()
            appScope.launch {
                if (saveIfNeeded(untitledName) != null) {
                    android.widget.Toast
                        .makeText(
                            context,
                            changesSavedMsg,
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        },
        saveAndBack = {
            flushThenSaveAndNavigate(
                flushPendingEdits = flushPendingEdits,
                launchSave = { appScope.launch { showUndoableSave() } },
                navigate = onBack,
            )
        },
        saveAndNavigateUp = {
            flushThenSaveAndNavigate(
                flushPendingEdits = flushPendingEdits,
                launchSave = { appScope.launch { showUndoableSave() } },
                navigate = onNavigateUp,
            )
        },
        archiveAndBack = {
            flushPendingEdits()
            val archiveStartedFromTrash = trashed
            appScope.launch {
                archiveCurrent(untitledName)
                val result =
                    snackbarHostState.showSnackbar(
                        message = msgArchived,
                        actionLabel = undoMsg,
                        withDismissAction = true,
                        duration = SnackbarDuration.Short,
                    )
                if (result == SnackbarResult.ActionPerformed) {
                    if (archiveStartedFromTrash) {
                        trashCurrent()
                    } else {
                        unarchiveCurrent()
                    }
                }
            }
            onBack()
        },
        notifyOrRequestPermission = {
            flushPendingEdits()
            if (canPostNotifications(context)) {
                appScope.launch { fireNotification(context, untitledName) }
            } else {
                onNotificationPermissionRequired()
            }
        },
        unarchive = {
            appScope.launch {
                unarchiveCurrent()
                val result =
                    snackbarHostState.showSnackbar(
                        message = msgUnarchived,
                        actionLabel = undoMsg,
                        withDismissAction = true,
                        duration = SnackbarDuration.Short,
                    )
                if (result == SnackbarResult.ActionPerformed) {
                    archiveCurrent(untitledName)
                }
            }
        },
        trashAndBack = {
            val trashStartedFromArchive = archived
            appScope.launch {
                trashCurrent()
                val result =
                    snackbarHostState.showSnackbar(
                        message = msgTrashed,
                        actionLabel = undoMsg,
                        withDismissAction = true,
                        duration = SnackbarDuration.Short,
                    )
                if (result == SnackbarResult.ActionPerformed) {
                    if (trashStartedFromArchive) {
                        archiveCurrent(untitledName)
                    } else {
                        restoreFromTrashCurrent()
                    }
                }
            }
            onBack()
        },
        restore = {
            appScope.launch {
                restoreFromTrashCurrent()
                val result =
                    snackbarHostState.showSnackbar(
                        message = msgRestored,
                        actionLabel = undoMsg,
                        withDismissAction = true,
                        duration = SnackbarDuration.Short,
                    )
                if (result == SnackbarResult.ActionPerformed) {
                    trashCurrent()
                }
            }
        },
        deleteForeverAndBack = {
            appScope.launch { deleteForeverCurrent() }
            onBack()
        },
    )
}
