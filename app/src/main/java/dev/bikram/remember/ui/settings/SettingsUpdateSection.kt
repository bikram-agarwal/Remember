package dev.bikram.remember.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bikram.remember.BuildConfig
import dev.bikram.remember.R
import dev.bikram.remember.data.UpdateCheckSchedule
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.components.RememberOutlinedButton
import dev.bikram.remember.ui.components.RememberSwitch
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.update.RememberUpdateInfo

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun UpdateCheckBottomSheetContent(
    isCheckingUpdate: Boolean,
    updateInfo: RememberUpdateInfo?,
    updateCheckFinishedWithoutResult: Boolean,
    downloadProgress: Float?,
    onCheckAgain: () -> Unit,
    onDownloadClick: (RememberUpdateInfo) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_updates_section),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        when {
            isCheckingUpdate -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.settings_checking_for_updates),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            updateInfo != null -> {
                Text(
                    text = stringResource(R.string.settings_update_available, updateInfo.versionName),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Text(
                        text = updateInfo.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (downloadProgress != null) {
                    UpdateDownloadProgress(downloadProgress = downloadProgress)
                } else {
                    RememberOutlinedButton(
                        onClick = { onDownloadClick(updateInfo) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_download_install, updateInfo.versionName))
                    }
                }
            }
            updateCheckFinishedWithoutResult -> {
                Text(
                    text = stringResource(R.string.settings_up_to_date),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_update_current_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RememberOutlinedButton(
                    onClick = onCheckAgain,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_check_for_updates))
                }
            }
            else -> {
                Text(
                    text = stringResource(R.string.settings_update_current_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RememberOutlinedButton(
                    onClick = onCheckAgain,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_check_for_updates))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateDownloadProgress(downloadProgress: Float) {
    when {
        downloadProgress == -1f -> {
            Text(
                text = stringResource(R.string.settings_installing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        downloadProgress < 0f -> {
            Text(
                text = stringResource(R.string.settings_downloading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        else -> {
            Text(
                text = stringResource(R.string.settings_downloading_percent, downloadProgress.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearWavyProgressIndicator(
                progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun UpdateCheckScheduleDropdown(
    selected: UpdateCheckSchedule,
    onSelect: (UpdateCheckSchedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_update_check_frequency),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        RememberOutlinedButton(onClick = { expanded = true }) {
            Text(updateScheduleSummaryBeforeColon(selected))
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                UpdateCheckSchedule.entries.forEach { option ->
                    RememberDropdownMenuItem(
                        text = { Text(updateScheduleLabel(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun UpdateSettingsToggleItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .tapSoundClickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        RememberSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent =
                if (checked) {
                    {
                        RememberMaterialRoundedSymbol(
                            name = "check",
                            size = SwitchDefaults.IconSize,
                            weight = FontWeight.Bold,
                        )
                    }
                } else {
                    null
                },
        )
    }
}

private fun summaryLabelBeforeColon(fullScheduleLabel: String): String {
    val colonIndex = fullScheduleLabel.indexOf(':')
    return if (colonIndex >= 0) {
        fullScheduleLabel.substring(0, colonIndex).trim()
    } else {
        fullScheduleLabel
    }
}

@Composable
private fun updateScheduleSummaryBeforeColon(schedule: UpdateCheckSchedule): String = summaryLabelBeforeColon(updateScheduleLabel(schedule))

@Composable
private fun updateScheduleLabel(schedule: UpdateCheckSchedule): String =
    when (schedule) {
        UpdateCheckSchedule.AT_APP_START -> stringResource(R.string.settings_update_schedule_app_start)
        UpdateCheckSchedule.DAILY_AT_21 -> stringResource(R.string.settings_update_schedule_daily_21)
        UpdateCheckSchedule.WEEKLY_MONDAY_AT_21 -> stringResource(R.string.settings_update_schedule_monday_21)
        UpdateCheckSchedule.NEVER -> stringResource(R.string.settings_update_schedule_never)
    }
