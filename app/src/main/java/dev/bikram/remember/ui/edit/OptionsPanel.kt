package dev.bikram.remember.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.data.AppMediaStorage
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteAction
import dev.bikram.remember.data.NoteAttachmentEntity
import dev.bikram.remember.data.RecurrenceRule
import dev.bikram.remember.data.RecurrenceUnit
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.TagChipFilled
import dev.bikram.remember.ui.feedback.tapSoundClickable
import java.text.DateFormat
import java.util.Date

@Composable
fun OptionsPanel(
    reminderAt: Long?,
    recurrence: RecurrenceRule?,
    importance: Importance,
    pictureUri: String?,
    iconKey: String?,
    isChecklist: Boolean,
    actions: List<NoteAction>,
    tags: List<String>,
    attachments: List<NoteAttachmentEntity>,
    onOpenReminder: () -> Unit,
    onSetImportance: (Importance) -> Unit,
    onOpenPicture: () -> Unit,
    onOpenIcon: () -> Unit,
    onOpenActions: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenAttachments: () -> Unit,
    modifier: Modifier = Modifier,
    // When true (archived / trashed), the Importance row's internal bottom-sheet trigger is
    // suppressed. Every OTHER row already no-ops through its caller-provided `onOpen*` lambda
    // when read-only, but Importance owns its own sheet so the gate has to live here.
    readOnly: Boolean = false,
) {
    var importanceOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val storedAttachmentCount =
        attachments.count { attachment ->
            AppMediaStorage.isAppStoredMediaUri(context, attachment.uri)
        }
    val linkedAttachmentCount = attachments.size - storedAttachmentCount
    val attachmentSummary =
        if (attachments.isEmpty()) {
            attachmentsSummary(attachments.size)
        } else {
            attachmentStorageSummary(
                fileSummary = attachmentsSummary(attachments.size),
                storedCount = storedAttachmentCount,
                linkedCount = linkedAttachmentCount,
            )
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            OptionRow(
                symbolName = "emoji_symbols",
                title = stringResource(R.string.options_icon),
                summary =
                    iconLabelFor(iconKey)
                        ?: stringResource(
                            if (isChecklist) {
                                R.string.options_icon_default_checklist
                            } else {
                                R.string.options_icon_default_note
                            },
                        ),
                onClick = onOpenIcon,
            )
            OptionRow(
                symbolName = "label",
                title = stringResource(R.string.options_tags),
                summary = if (tags.isEmpty()) "None" else "",
                onClick = onOpenTags,
                summaryContent =
                    if (tags.isEmpty()) {
                        null
                    } else {
                        { TagPillsRow(tags = tags) }
                    },
            )
            OptionRow(
                symbolName = "alarm",
                title = stringResource(R.string.options_reminder),
                summary = reminderSummary(reminderAt, recurrence),
                onClick = onOpenReminder,
            )
            OptionRow(
                symbolName = actions.firstOrNull()?.type?.materialSymbolName() ?: "bolt",
                title = stringResource(R.string.options_actions),
                summary = actionsSummary(actions),
                onClick = onOpenActions,
            )
            OptionRow(
                symbolName = "priority_high",
                title = stringResource(R.string.options_importance),
                summary = importance.label(),
                // On archived / trashed shelves we keep the row visible (users should still
                // see "this note was High importance") but make it inert. Passing null to
                // OptionRow drops the clickable modifier entirely so there's no ripple.
                onClick = if (readOnly) null else ({ importanceOpen = true }),
            )
            OptionRow(
                symbolName = "add_a_photo",
                title = stringResource(R.string.options_picture),
                summary =
                    if (pictureUri == null) {
                        stringResource(R.string.options_picture_none)
                    } else {
                        stringResource(R.string.options_picture_attached)
                    },
                onClick = onOpenPicture,
                summaryContent =
                    if (pictureUri == null) {
                        null
                    } else {
                        {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.options_picture_attached),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                MediaStorageChip(
                                    storedInApp = AppMediaStorage.isAppStoredMediaUri(context, pictureUri),
                                )
                            }
                        }
                    },
            )
            OptionRow(
                symbolName = "attach_file",
                title = stringResource(R.string.options_attachments),
                summary = attachmentSummary,
                onClick = onOpenAttachments,
            )
        }
    }

    if (importanceOpen) {
        ChoiceSheet(
            title = stringResource(R.string.options_importance),
            options = Importance.entries.map { it to it.label() },
            selected = importance,
            onPick = {
                onSetImportance(it)
                importanceOpen = false
            },
            onDismiss = { importanceOpen = false },
        )
    }
}

@Composable
private fun OptionRow(
    symbolName: String,
    title: String,
    summary: String,
    onClick: (() -> Unit)? = null,
    summaryContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.tapSoundClickable(onClick = onClick) else it }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberMaterialRoundedSymbol(
            name = symbolName,
            size = 22.dp,
            tint = MaterialTheme.colorScheme.primary,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (summaryContent != null) {
                Spacer(Modifier.size(6.dp))
                summaryContent()
            } else {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun MediaStorageChip(
    storedInApp: Boolean,
    modifier: Modifier = Modifier,
) {
    val label =
        stringResource(
            if (storedInApp) {
                R.string.media_storage_stored_in_app
            } else {
                R.string.media_storage_linked_original
            },
        )
    val statusContentDescription = stringResource(R.string.media_storage_status_cd)
    val linkedOriginalHint = stringResource(R.string.media_storage_linked_original_hint)
    val chipContentDescription =
        if (storedInApp) {
            "$statusContentDescription: $label"
        } else {
            "$statusContentDescription: $label. $linkedOriginalHint"
        }
    Surface(
        modifier =
            modifier.semantics {
                this.contentDescription = chipContentDescription
            },
        shape = MaterialTheme.shapes.extraExtraLarge,
        color =
            if (storedInApp) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        contentColor =
            if (storedInApp) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPillsRow(tags: List<String>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { tag ->
            TagChipFilled(tag = tag, compact = true)
        }
    }
}

@Composable
private fun <T> ChoiceSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(
        title = title,
        onDismiss = onDismiss,
        actions = {
            RememberTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
        },
    ) {
        options.forEach { (value, label) ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .tapSoundClickable { onPick(value) }
                        .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = value == selected,
                    onClick = { onPick(value) },
                )
                Spacer(Modifier.size(8.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun Importance.label(): String =
    when (this) {
        Importance.LOW -> "Low"
        Importance.DEFAULT -> "Default"
        Importance.HIGH -> "High"
    }

private fun reminderSummary(
    reminderAt: Long?,
    recurrence: RecurrenceRule?,
): String {
    if (reminderAt == null) return "None"
    val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    val datePart = fmt.format(Date(reminderAt))
    val rule = recurrence?.sanitized() ?: return datePart
    val recurrenceLabel = compactRecurrenceLabel(rule)
    return if (recurrenceLabel.isEmpty()) datePart else "$datePart  |  $recurrenceLabel"
}

/**
 * Short, human-readable recurrence label for the reminder pill summary line. Mirrors
 * the long-form summary the picker emits but keeps it to one or two words ("Daily",
 * "Every 3 weeks", "Monthly") so the pill stays compact.
 */
private fun compactRecurrenceLabel(rule: RecurrenceRule): String {
    val interval = rule.interval.coerceAtLeast(1)
    return if (interval == 1) {
        when (rule.unit) {
            RecurrenceUnit.DAY -> "Daily"
            RecurrenceUnit.WEEK -> "Weekly"
            RecurrenceUnit.MONTH -> "Monthly"
            RecurrenceUnit.YEAR -> "Yearly"
        }
    } else {
        val unitLower =
            when (rule.unit) {
                RecurrenceUnit.DAY -> "days"
                RecurrenceUnit.WEEK -> "weeks"
                RecurrenceUnit.MONTH -> "months"
                RecurrenceUnit.YEAR -> "years"
            }
        "Every $interval $unitLower"
    }
}

private fun actionsSummary(actions: List<NoteAction>): String =
    when (actions.size) {
        0 -> "None"
        1 -> actions[0].title.ifBlank { "1 action" }
        else -> "${actions.size} actions"
    }

@Composable
private fun attachmentStorageSummary(
    fileSummary: String,
    storedCount: Int,
    linkedCount: Int,
): String {
    val storedSummary =
        pluralStringResource(
            R.plurals.media_storage_stored_count,
            storedCount,
            storedCount,
        )
    val linkedSummary =
        pluralStringResource(
            R.plurals.media_storage_linked_count,
            linkedCount,
            linkedCount,
        )
    return stringResource(
        R.string.options_attachments_storage_summary,
        fileSummary,
        storedSummary,
        linkedSummary,
    )
}

private fun attachmentsSummary(count: Int): String =
    when (count) {
        0 -> "None"
        1 -> "1 file"
        else -> "$count files"
    }
