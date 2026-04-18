package dev.bikram.remember.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bikram.remember.data.FilterType
import dev.bikram.remember.data.NotesFilter
import dev.bikram.remember.ui.components.TagChipFilled

@Composable
fun ActiveFilterChips(
    filter: NotesFilter,
    onChange: (NotesFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!filter.facetActive) return
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (filter.type != FilterType.ALL) {
            DismissibleChip(
                label = if (filter.type == FilterType.NOTE) "Notes" else "Lists",
                onDismiss = { onChange(filter.copy(type = FilterType.ALL)) },
            )
        }
        filter.tags.forEach { tag ->
            TagChipFilled(
                tag = tag,
                onClick = { onChange(filter.copy(tags = filter.tags - tag)) },
                onRemove = { onChange(filter.copy(tags = filter.tags - tag)) },
            )
        }
        if (filter.hasReminder == true) {
            DismissibleChip("Has reminder") { onChange(filter.copy(hasReminder = null)) }
        }
        if (filter.hasPicture == true) {
            DismissibleChip("Has picture") { onChange(filter.copy(hasPicture = null)) }
        }
        if (filter.hasAttachment == true) {
            DismissibleChip("Has attachment") { onChange(filter.copy(hasAttachment = null)) }
        }
        if (filter.pinned == true) {
            DismissibleChip("Pinned") { onChange(filter.copy(pinned = null)) }
        }
    }
}

@Composable
private fun DismissibleChip(label: String, onDismiss: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onDismiss,
        label = { Text(label) },
        trailingIcon = {
            Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
        },
    )
}
