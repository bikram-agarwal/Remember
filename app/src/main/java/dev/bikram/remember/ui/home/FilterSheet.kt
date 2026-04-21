package dev.bikram.remember.ui.home
import androidx.compose.material3.TextButton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import dev.bikram.remember.ui.components.RememberSegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bikram.remember.data.FilterType
import dev.bikram.remember.data.NotesFilter
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.RememberSwitch
import androidx.compose.ui.res.stringResource
import dev.bikram.remember.R

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    filter: NotesFilter,
    availableTags: List<String>,
    onChange: (NotesFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(
        title = stringResource(R.string.filter_title),
        onDismiss = onDismiss,
        actions = {
            RememberTextButton(onClick = { onChange(filter.copy(
                type = FilterType.ALL,
                tags = emptySet(),
                hasReminder = null,
                hasPicture = null,
                hasAttachment = null,
                pinned = null,
            )) }) { Text(stringResource(R.string.common_clear)) }
            RememberTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
        },
    ) {
        Section("Type")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val entries = FilterType.entries
            entries.forEachIndexed { idx, t ->
                RememberSegmentedButton(
                    selected = filter.type == t,
                    onClick = { onChange(filter.copy(type = t)) },
                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = entries.size),
                    label = { Text(typeLabel(t)) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        if (availableTags.isNotEmpty()) {
            Section("Tags")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                availableTags.forEach { tag ->
                    val selected = filter.tags.any { it.equals(tag, ignoreCase = true) }
                    dev.bikram.remember.ui.components.TagChipFilled(
                        tag = tag,
                        faded = !selected,
                        onClick = {
                            val next = filter.tags.toMutableSet()
                            if (selected) next.removeIf { it.equals(tag, ignoreCase = true) }
                            else next.add(tag)
                            onChange(filter.copy(tags = next))
                        },
                        modifier = Modifier.padding(end = 4.dp, bottom = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Section("State")
        ToggleRow("Has reminder", filter.hasReminder == true) { on ->
            onChange(filter.copy(hasReminder = if (on) true else null))
        }
        ToggleRow("Has picture", filter.hasPicture == true) { on ->
            onChange(filter.copy(hasPicture = if (on) true else null))
        }
        ToggleRow("Has attachment", filter.hasAttachment == true) { on ->
            onChange(filter.copy(hasAttachment = if (on) true else null))
        }
        ToggleRow("Favorites", filter.pinned == true) { on ->
            onChange(filter.copy(pinned = if (on) true else null))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        RememberSwitch(checked = checked, onCheckedChange = onChange)
    }
}

private fun typeLabel(t: FilterType): String = when (t) {
    FilterType.ALL -> "All"
    FilterType.NOTE -> "Notes"
    FilterType.LIST -> "Lists"
}
