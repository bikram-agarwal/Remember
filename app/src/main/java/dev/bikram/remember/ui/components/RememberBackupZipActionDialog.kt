package dev.bikram.remember.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.data.BackupIo

/**
 * Two-step dialog for handling a Remember backup `.zip` outside Settings:
 * first letting the user choose between additive Import (merge) and destructive Restore (replace),
 * and confirming before a destructive full restore.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RememberBackupZipActionDialog(
    confirmingRestore: Boolean,
    mediaSummary: BackupIo.RestoreMediaSummary?,
    onImportMerge: () -> Unit,
    onRequestRestore: () -> Unit,
    onConfirmRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!confirmingRestore) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.backup_zip_choice_title)) },
            text = { Text(stringResource(R.string.settings_backup_help_body)) },
            confirmButton = {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RememberTextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.common_cancel),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RememberTextButton(onClick = onRequestRestore) {
                        Text(
                            text = stringResource(R.string.backup_zip_restore_action),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    RememberButton(onClick = onImportMerge) {
                        Text(stringResource(R.string.backup_zip_import_action))
                    }
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.settings_restore_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_restore_confirm_body))
                    if (mediaSummary?.hasMissingMedia == true) {
                        Text(
                            text = stringResource(R.string.settings_restore_media_missing_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                RememberTextButton(onClick = onConfirmRestore) {
                    Text(
                        text = stringResource(R.string.settings_restore_go_ahead),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                RememberTextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}
