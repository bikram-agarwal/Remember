package dev.bikram.remember.ui.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteAction
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.TagChipFilled
import java.text.DateFormat
import java.util.Date
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.feedback.tapSoundClickable

@Composable
fun OptionsPanel(
    reminderAt: Long?,
    importance: Importance,
    pictureUri: String?,
    iconKey: String?,
    actions: List<NoteAction>,
    tags: List<String>,
    attachmentCount: Int,
    onOpenReminder: () -> Unit,
    onSetImportance: (Importance) -> Unit,
    onOpenPicture: () -> Unit,
    onOpenIcon: () -> Unit,
    onOpenActions: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenAttachments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var importanceOpen by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            OptionRow(
                symbolName = "emoji_symbols",
                title = "Icon",
                summary = iconLabelFor(iconKey) ?: "None",
                onClick = onOpenIcon,
            )
            OptionRow(
                symbolName = "label",
                title = "Tags",
                summary = if (tags.isEmpty()) "None" else "",
                onClick = onOpenTags,
                summaryContent = if (tags.isEmpty()) null else {
                    { TagPillsRow(tags = tags) }
                },
            )
            OptionRow(
                symbolName = "alarm",
                title = "Reminder",
                summary = reminderSummary(reminderAt),
                onClick = onOpenReminder,
            )
            OptionRow(
                symbolName = "bolt",
                title = "Actions",
                summary = actionsSummary(actions),
                onClick = onOpenActions,
            )
            OptionRow(
                symbolName = "priority_high",
                title = "Importance",
                summary = importance.label(),
                onClick = { importanceOpen = true },
            )
            OptionRow(
                symbolName = "add_a_photo",
                title = "Picture",
                summary = if (pictureUri == null) "No picture" else "Attached",
                onClick = onOpenPicture,
            )
            OptionRow(
                symbolName = "attach_file",
                title = "Attachments",
                summary = attachmentsSummary(attachmentCount),
                onClick = onOpenAttachments,
            )
        }
    }

    if (importanceOpen) {
        ChoiceSheet(
            title = "Importance",
            options = Importance.entries.map { it to it.label() },
            selected = importance,
            onPick = { onSetImportance(it); importanceOpen = false },
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
        modifier = Modifier
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
            RememberTextButton(onClick = onDismiss) { Text("Done") }
        },
    ) {
        options.forEach { (value, label) ->
            Row(
                modifier = Modifier
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

private fun Importance.label(): String = when (this) {
    Importance.LOW -> "Low"
    Importance.DEFAULT -> "Default"
    Importance.HIGH -> "High"
}

private fun reminderSummary(reminderAt: Long?): String {
    if (reminderAt == null) return "None"
    val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return fmt.format(Date(reminderAt))
}

private fun actionsSummary(actions: List<NoteAction>): String = when (actions.size) {
    0 -> "None"
    1 -> actions[0].title.ifBlank { "1 action" }
    else -> "${actions.size} actions"
}

private fun attachmentsSummary(count: Int): String = when (count) {
    0 -> "None"
    1 -> "1 file"
    else -> "$count files"
}
