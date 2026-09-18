package dev.bikram.remember.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.data.DefaultNotePreferencesState
import dev.bikram.remember.data.DefaultNotePrefs
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.RecurrenceRule
import dev.bikram.remember.data.RecurrenceUnit
import dev.bikram.remember.data.labelRes
import dev.bikram.remember.domain.formatTimeOfDay
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.components.RememberOutlinedButton
import dev.bikram.remember.ui.components.settings.GroupPosition
import dev.bikram.remember.ui.components.settings.GroupedListColumn
import dev.bikram.remember.ui.components.settings.GroupedListItem
import dev.bikram.remember.ui.edit.ReminderTimePickerDialog
import dev.bikram.remember.ui.feedback.appClickable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import dev.bikram.remember.data.Visibility as NoteVisibility

/**
 * Defaults for newly created notes and lists.
 *
 * Remember-only domain section. FilePipe has no analogous "new item defaults" surface; do not
 * force-match a Defaults section into FilePipe settings.
 */
@Composable
internal fun DefaultsSection(
    defaultsState: DefaultNotePreferencesState,
    defaultNotePrefs: DefaultNotePrefs,
    scope: CoroutineScope,
) {
    var timePickerOpen by remember { mutableStateOf(false) }
    var visibilityExpanded by remember { mutableStateOf(false) }
    var importanceExpanded by remember { mutableStateOf(false) }

    GroupedListColumn {
        GroupedListItem(position = GroupPosition.FIRST) {
            DefaultsDropdownRow(
                materialSymbolName = "visibility",
                title = stringResource(R.string.settings_defaults_visibility_title),
                subtitle = stringResource(R.string.settings_defaults_visibility_desc),
                selectedLabel = stringResource(defaultsState.defaultVisibility.labelRes()),
                expanded = visibilityExpanded,
                onExpandedChange = { expanded -> visibilityExpanded = expanded },
            ) {
                NoteVisibility.entries.forEach { option ->
                    RememberDropdownMenuItem(
                        text = { Text(stringResource(option.labelRes())) },
                        onClick = {
                            scope.launch { defaultNotePrefs.setDefaultVisibility(option) }
                            visibilityExpanded = false
                        },
                    )
                }
            }
        }
        GroupedListItem(position = GroupPosition.MIDDLE) {
            DefaultsDropdownRow(
                materialSymbolName = "priority_high",
                title = stringResource(R.string.settings_defaults_importance_title),
                subtitle = stringResource(R.string.settings_defaults_importance_desc),
                selectedLabel = stringResource(defaultsState.defaultImportance.labelRes()),
                expanded = importanceExpanded,
                onExpandedChange = { expanded -> importanceExpanded = expanded },
            ) {
                Importance.entries.forEach { option ->
                    RememberDropdownMenuItem(
                        text = { Text(stringResource(option.labelRes())) },
                        onClick = {
                            scope.launch { defaultNotePrefs.setDefaultImportance(option) }
                            importanceExpanded = false
                        },
                    )
                }
            }
        }
        GroupedListItem(position = GroupPosition.MIDDLE) {
            DefaultsReminderTimeRow(
                minutesOfDay = defaultsState.defaultReminderMinutesOfDay,
                onOpenPicker = { timePickerOpen = true },
            )
        }
        GroupedListItem(position = GroupPosition.LAST) {
            DefaultsRecurrenceRow(
                recurrence = defaultsState.defaultRecurrence,
                onSelect = { rule ->
                    scope.launch { defaultNotePrefs.setDefaultRecurrence(rule) }
                },
            )
        }
    }

    if (timePickerOpen) {
        val initialMinutes = defaultsState.defaultReminderMinutesOfDay
        ReminderTimePickerDialog(
            initialHour = DefaultNotePrefs.hourFromMinutesOfDay(initialMinutes),
            initialMinute = DefaultNotePrefs.minuteFromMinutesOfDay(initialMinutes),
            onDismiss = { timePickerOpen = false },
            onConfirm = { hour, minute ->
                timePickerOpen = false
                scope.launch {
                    defaultNotePrefs.setDefaultReminderMinutesOfDay(
                        DefaultNotePrefs.minutesOfDay(hour, minute),
                    )
                }
            },
        )
    }
}

@Composable
private fun DefaultsDropdownRow(
    materialSymbolName: String,
    title: String,
    subtitle: String,
    selectedLabel: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .appClickable { onExpandedChange(true) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberMaterialRoundedSymbol(
            name = materialSymbolName,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        RememberOutlinedButton(onClick = { onExpandedChange(true) }) {
            Text(selectedLabel)
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun DefaultsReminderTimeRow(
    minutesOfDay: Int,
    onOpenPicker: () -> Unit,
) {
    val context = LocalContext.current
    val summary =
        formatTimeOfDay(
            context,
            DefaultNotePrefs.hourFromMinutesOfDay(minutesOfDay),
            DefaultNotePrefs.minuteFromMinutesOfDay(minutesOfDay),
        )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .appClickable(onClick = onOpenPicker)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberMaterialRoundedSymbol(
            name = "alarm",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(R.string.settings_defaults_reminder_time_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.settings_defaults_reminder_time_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        RememberOutlinedButton(onClick = onOpenPicker) {
            Text(summary)
        }
    }
}

@Composable
private fun DefaultsRecurrenceRow(
    recurrence: RecurrenceRule?,
    onSelect: (RecurrenceRule) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedPreset = DefaultRecurrencePreset.fromRule(recurrence)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberMaterialRoundedSymbol(
            name = "event_repeat",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(R.string.settings_defaults_recurrence_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.settings_defaults_recurrence_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        RememberOutlinedButton(onClick = { expanded = true }) {
            Text(stringResource(selectedPreset.labelRes))
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                DefaultRecurrencePreset.entries.forEach { preset ->
                    RememberDropdownMenuItem(
                        text = { Text(stringResource(preset.labelRes)) },
                        onClick = {
                            onSelect(preset.toRule())
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * There is deliberately no "None" option: the default never switches repeat on, it only decides
 * which unit the repeat controls open on, so "no unit" would have nothing to mean.
 */
private enum class DefaultRecurrencePreset(
    val labelRes: Int,
) {
    DAILY(R.string.settings_defaults_recurrence_daily),
    WEEKLY(R.string.settings_defaults_recurrence_weekly),
    MONTHLY(R.string.settings_defaults_recurrence_monthly),
    YEARLY(R.string.settings_defaults_recurrence_yearly),
    ;

    fun toRule(): RecurrenceRule =
        when (this) {
            DAILY -> RecurrenceRule(unit = RecurrenceUnit.DAY, interval = 1)
            WEEKLY -> RecurrenceRule(unit = RecurrenceUnit.WEEK, interval = 1)
            MONTHLY -> RecurrenceRule(unit = RecurrenceUnit.MONTH, interval = 1)
            YEARLY -> RecurrenceRule(unit = RecurrenceUnit.YEAR, interval = 1)
        }

    companion object {
        fun fromRule(rule: RecurrenceRule?): DefaultRecurrencePreset =
            when (rule?.sanitized()?.unit) {
                RecurrenceUnit.WEEK -> WEEKLY
                RecurrenceUnit.MONTH -> MONTHLY
                RecurrenceUnit.YEAR -> YEARLY
                else -> DAILY
            }
    }
}
