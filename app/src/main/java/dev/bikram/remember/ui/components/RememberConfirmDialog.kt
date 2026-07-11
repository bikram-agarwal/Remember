package dev.bikram.remember.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.bikram.remember.R

/**
 * Shared confirmation dialog for the app's two-action confirms.
 *
 * Button emphasis follows one rule: the recommended action is a filled [RememberButton] **only when
 * it is safe** ([destructive] = false). Destructive confirms (delete, trash, reset, discard) use a
 * low-emphasis, error-colored text button so the dialog never visually pushes an irreversible
 * action. The dismiss/cancel action is always a neutral (not theme-accented) text button.
 */
@Composable
fun RememberConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    destructive: Boolean = false,
    dismissLabel: String = stringResource(R.string.common_cancel),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = text?.let { body -> { Text(body) } },
        confirmButton = {
            if (destructive) {
                RememberTextButton(onClick = onConfirm) {
                    Text(confirmLabel, color = MaterialTheme.colorScheme.error)
                }
            } else {
                RememberButton(onClick = onConfirm) {
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = {
            RememberTextButton(onClick = onDismiss) {
                // Neutral, not theme-accented: the dismiss action shouldn't compete with the
                // recommended (filled) or destructive (error) action for the eye.
                Text(dismissLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Composable
fun RememberUnsavedChangesDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RememberConfirmDialog(
        title = stringResource(R.string.common_unsaved_changes_title),
        text = stringResource(R.string.common_unsaved_changes_body),
        confirmLabel = stringResource(R.string.common_unsaved_changes_discard),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true,
        dismissLabel = stringResource(R.string.common_unsaved_changes_keep_editing),
        modifier = modifier,
    )
}
