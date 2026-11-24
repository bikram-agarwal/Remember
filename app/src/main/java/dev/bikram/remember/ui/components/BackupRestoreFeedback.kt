package dev.bikram.remember.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.SnackbarHostState
import dev.bikram.remember.R
import dev.bikram.remember.data.BackupIo

/**
 * What a full restore reports afterwards, wherever it was started (Settings, a dropped backup, or
 * the Import screen): how many notes came back, then which backup folders need picking again and
 * whether automatic backups were turned off because no backup folder came back with the restore.
 */
internal suspend fun showBackupRestoreOutcome(
    context: Context,
    snackbarHostState: SnackbarHostState,
    result: BackupIo.RestoreResult,
) {
    val resources = context.resources
    Toast
        .makeText(
            context,
            resources.getQuantityString(R.plurals.toast_imported_notes, result.noteCount, result.noteCount),
            Toast.LENGTH_SHORT,
        ).show()
    val foldersNeedingReselection = result.settingsOutcome.foldersNeedingReselection
    if (foldersNeedingReselection > 0) {
        snackbarHostState.showSnackbar(
            resources.getQuantityString(
                R.plurals.settings_restore_folders_need_reselection,
                foldersNeedingReselection,
                foldersNeedingReselection,
            ),
        )
    }
    if (result.settingsOutcome.automationsDisabled) {
        snackbarHostState.showSnackbar(resources.getString(R.string.settings_restore_automation_disabled_no_folder))
    }
}
