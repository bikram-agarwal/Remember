package dev.bikram.remember.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.backup.RememberBackupWork
import dev.bikram.remember.data.BackupIo
import dev.bikram.remember.data.BackupPreferencesState
import dev.bikram.remember.data.BackupPrefs
import dev.bikram.remember.ui.common.ResponsiveActionLayout
import dev.bikram.remember.ui.common.responsiveActionLayout
import dev.bikram.remember.ui.components.RememberActionLabel
import dev.bikram.remember.ui.components.RememberOutlinedButton
import dev.bikram.remember.ui.components.settings.GroupPosition
import dev.bikram.remember.ui.components.settings.GroupedListColumn
import dev.bikram.remember.ui.components.settings.GroupedListItem
import dev.bikram.remember.ui.feedback.tapSoundClickable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backup section: local and cloud folder pickers (long-click to clear), three
 * include/auto/schedule toggles that disable until at least one folder is selected,
 * an "import" / "export now" pair, and a "restore (replace)" pill.
 *
 * Pulled out of [SettingsRoute] in audit 3.1. The four launcher actions (local /
 * cloud folder pick, import-merge, import-replace) are exposed as plain callbacks so
 * this composable does not need to know about the underlying ActivityResult plumbing.
 */
@Composable
internal fun BackupSection(
    backupState: BackupPreferencesState,
    backupPrefs: BackupPrefs,
    backupIo: BackupIo,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    onPickLocalFolder: () -> Unit,
    onPickCloudFolder: () -> Unit,
    onLaunchImportMerge: () -> Unit,
    onLaunchImportReplace: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    val internalStorageDisplayName = stringResource(R.string.filesystem_folder_picker_internal_storage)
    val chooseLocalFolderLabel = stringResource(R.string.settings_choose_local_backup_folder)
    val chooseCloudFolderLabel = stringResource(R.string.settings_choose_cloud_backup_file)
    val resolvedLocalFolderLabel by produceState(
        initialValue =
            backupState.exportFolderUri
                .takeIf { it.isNotBlank() }
                ?.let { internalStorageDisplayName } ?: chooseLocalFolderLabel,
        backupState.exportFolderUri,
        internalStorageDisplayName,
        chooseLocalFolderLabel,
    ) {
        val uriString = backupState.exportFolderUri
        value =
            if (uriString.isBlank()) {
                chooseLocalFolderLabel
            } else {
                withContext(Dispatchers.IO) {
                    exportFolderDisplayLabel(context, uriString, internalStorageDisplayName)
                }
            }
    }
    val resolvedCloudFolderLabel by produceState(
        initialValue =
            backupState.cloudExportFolderUri
                .takeIf { it.isNotBlank() }
                ?.let { internalStorageDisplayName } ?: chooseCloudFolderLabel,
        backupState.cloudExportFolderUri,
        internalStorageDisplayName,
        chooseCloudFolderLabel,
    ) {
        val uriString = backupState.cloudExportFolderUri
        value =
            if (uriString.isBlank()) {
                chooseCloudFolderLabel
            } else {
                withContext(Dispatchers.IO) {
                    exportFolderDisplayLabel(context, uriString, internalStorageDisplayName)
                }
            }
    }
    val localFolderLabel = resolvedLocalFolderLabel
    val cloudFolderLabel = resolvedCloudFolderLabel
    val exportFolderReady =
        backupState.exportFolderUri.isNotBlank() ||
            backupState.cloudExportFolderUri.isNotBlank()
    val includeMediaSwitchEnabled = exportFolderReady || backupState.includeMediaInBackup
    val autoExportSwitchEnabled = exportFolderReady || backupState.autoExportOnChange
    val scheduledExportSwitchEnabled = exportFolderReady || backupState.scheduledExportEnabled

    GroupedListColumn {
        GroupedListItem(position = GroupPosition.FIRST) {
            BackupFolderPickerItem(
                title = localFolderLabel,
                subtitle = stringResource(R.string.settings_local_backup_folder_hint),
                accessibilityLabel = stringResource(R.string.settings_choose_local_backup_folder),
                onClick = onPickLocalFolder,
                onLongClick = {
                    if (backupState.exportFolderUri.isNotBlank()) {
                        scope.launch {
                            backupPrefs.setExportFolderUri("")
                            RememberBackupWork.updateSchedule(context, backupPrefs.snapshot())
                            snackbarHostState.showSnackbar(
                                message = resources.getString(R.string.settings_local_backup_folder_cleared),
                                duration = SnackbarDuration.Short,
                            )
                        }
                    }
                },
            )
        }
        GroupedListItem(position = GroupPosition.MIDDLE) {
            BackupFolderPickerItem(
                title = cloudFolderLabel,
                subtitle = stringResource(R.string.settings_cloud_backup_folder_hint),
                accessibilityLabel = stringResource(R.string.settings_choose_cloud_backup_file),
                onClick = onPickCloudFolder,
                onLongClick = {
                    if (backupState.cloudExportFolderUri.isNotBlank()) {
                        scope.launch {
                            backupPrefs.setCloudExportFolderUri("")
                            RememberBackupWork.updateSchedule(context, backupPrefs.snapshot())
                            snackbarHostState.showSnackbar(
                                message = resources.getString(R.string.settings_cloud_backup_file_cleared),
                                duration = SnackbarDuration.Short,
                            )
                        }
                    }
                },
            )
        }
        GroupedListItem(position = GroupPosition.MIDDLE) {
            BackupFolderSettingsToggleItem(
                title = stringResource(R.string.settings_include_media_in_backup),
                subtitle = stringResource(R.string.settings_include_media_in_backup_hint),
                infoTooltipText = stringResource(R.string.settings_include_media_in_backup_tooltip),
                infoContentDescription = stringResource(R.string.settings_include_media_info_cd),
                checked = backupState.includeMediaInBackup,
                switchEnabled = includeMediaSwitchEnabled,
                onDisabledInteraction = {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = resources.getString(R.string.settings_export_select_folder_first),
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
                onCheckedChange = { enabled ->
                    scope.launch {
                        backupPrefs.setIncludeMediaInBackup(enabled)
                    }
                },
            )
        }
        GroupedListItem(position = GroupPosition.MIDDLE) {
            BackupFolderSettingsToggleItem(
                title = stringResource(R.string.settings_compress_images),
                subtitle = stringResource(R.string.settings_compress_images_hint),
                infoTooltipText = stringResource(R.string.settings_compress_images_tooltip),
                infoContentDescription = stringResource(R.string.settings_compress_images_info_cd),
                checked = backupState.compressImages,
                switchEnabled = true,
                onDisabledInteraction = null,
                onCheckedChange = { enabled ->
                    scope.launch {
                        backupPrefs.setCompressImages(enabled)
                    }
                },
            )
        }
        GroupedListItem(position = GroupPosition.MIDDLE) {
            BackupFolderSettingsToggleItem(
                title = stringResource(R.string.settings_auto_export_on_change),
                subtitle = stringResource(R.string.settings_auto_export_on_change_hint),
                checked = backupState.autoExportOnChange,
                switchEnabled = autoExportSwitchEnabled,
                onDisabledInteraction = {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = resources.getString(R.string.settings_export_select_folder_first),
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
                onCheckedChange = { enabled ->
                    scope.launch {
                        backupPrefs.setAutoExportOnChange(enabled)
                        RememberBackupWork.updateSchedule(context, backupPrefs.snapshot())
                    }
                },
            )
        }
        GroupedListItem(position = GroupPosition.MIDDLE) {
            BackupFolderSettingsToggleItem(
                title = stringResource(R.string.settings_scheduled_export),
                subtitle = stringResource(R.string.settings_scheduled_export_hint),
                checked = backupState.scheduledExportEnabled,
                switchEnabled = scheduledExportSwitchEnabled,
                onDisabledInteraction = {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = resources.getString(R.string.settings_export_select_folder_first),
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
                onCheckedChange = { enabled ->
                    scope.launch {
                        backupPrefs.setScheduledExportEnabled(enabled)
                        RememberBackupWork.updateSchedule(context, backupPrefs.snapshot())
                    }
                },
            )
        }
        GroupedListItem(position = GroupPosition.LAST) {
            val backupHelpCd = stringResource(R.string.settings_backup_help_icon_cd)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val stacked =
                        responsiveActionLayout(
                            availableWidth = maxWidth,
                            effectiveFontScale = LocalDensity.current.fontScale,
                            itemCount = 2,
                        ) == ResponsiveActionLayout.STACKED
                    if (stacked) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RememberOutlinedButton(
                                onClick = onLaunchImportMerge,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                RememberActionLabel(stringResource(R.string.settings_import_rules))
                            }
                            RememberOutlinedButton(
                                onClick = {
                                    if (exportFolderReady) {
                                        scope.launch {
                                            val backupDestinations =
                                                listOf(
                                                    backupState.exportFolderUri,
                                                    backupState.cloudExportFolderUri,
                                                ).filter { it.isNotBlank() }
                                            val exportOutcome = backupIo.exportToTreeFolders(backupDestinations)
                                            val message =
                                                exportOutcome.fold(
                                                    onSuccess = { fileNames ->
                                                        resources.getQuantityString(
                                                            R.plurals.toast_exported_to_destinations,
                                                            fileNames.size,
                                                            fileNames.size,
                                                        )
                                                    },
                                                    onFailure = {
                                                        resources.getString(R.string.toast_export_failed)
                                                    },
                                                )
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = resources.getString(R.string.settings_export_select_folder_first),
                                                duration = SnackbarDuration.Short,
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                RememberActionLabel(stringResource(R.string.settings_export_now))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RememberOutlinedButton(
                                onClick = onLaunchImportMerge,
                                modifier = Modifier.weight(1f),
                            ) {
                                RememberActionLabel(stringResource(R.string.settings_import_rules))
                            }
                            RememberOutlinedButton(
                                onClick = {
                                    if (exportFolderReady) {
                                        scope.launch {
                                            val backupDestinations =
                                                listOf(
                                                    backupState.exportFolderUri,
                                                    backupState.cloudExportFolderUri,
                                                ).filter { it.isNotBlank() }
                                            val exportOutcome = backupIo.exportToTreeFolders(backupDestinations)
                                            val message =
                                                exportOutcome.fold(
                                                    onSuccess = { fileNames ->
                                                        resources.getQuantityString(
                                                            R.plurals.toast_exported_to_destinations,
                                                            fileNames.size,
                                                            fileNames.size,
                                                        )
                                                    },
                                                    onFailure = {
                                                        resources.getString(R.string.toast_export_failed)
                                                    },
                                                )
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = resources.getString(R.string.settings_export_select_folder_first),
                                                duration = SnackbarDuration.Short,
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                RememberActionLabel(stringResource(R.string.settings_export_now))
                            }
                        }
                    }
                }
                val restoreShape = ButtonDefaults.outlinedShape
                val restoreOutline = MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                val restoreLabelColor = MaterialTheme.colorScheme.error
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            // heightIn (not a fixed height) lets the button grow to fit the label
                            // on large-font devices instead of vertically clipping it.
                            .heightIn(min = 40.dp)
                            .clip(restoreShape)
                            .border(BorderStroke(1.dp, restoreOutline), restoreShape),
                ) {
                    // The label is the height driver; the click layer matches the final size.
                    RememberActionLabel(
                        text = stringResource(R.string.settings_restore_backup),
                        style = MaterialTheme.typography.labelLarge.copy(color = restoreLabelColor),
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                // Keep the centered label clear of the trailing 40.dp info icon.
                                .padding(horizontal = 40.dp, vertical = 6.dp),
                    )
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .tapSoundClickable(onClick = onLaunchImportReplace),
                    )
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        SettingsInfoDropdown(
                            title = stringResource(R.string.settings_backup_help_title),
                            tipText = stringResource(R.string.settings_backup_help_body),
                            contentDescription = backupHelpCd,
                            iconTint = restoreLabelColor.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}
