package dev.bikram.remember.ui.home
import androidx.compose.material3.TextButton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.bikram.remember.data.GroupBy
import dev.bikram.remember.data.SortDir
import dev.bikram.remember.data.SortKey
import dev.bikram.remember.data.ViewOptions
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import androidx.compose.ui.text.font.FontWeight
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewOptionsSheet(
    viewOptions: ViewOptions,
    onChange: (ViewOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(
        title = "View options",
        subtitle = "Change how your notes are sorted and grouped",
        onDismiss = onDismiss,
        actions = {
            RememberTextButton(onClick = { onChange(ViewOptions()) }) { Text("Reset") }
            RememberTextButton(onClick = onDismiss) { Text("Done") }
        },
    ) {
        SectionLabel("Sort by")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                val keys = listOf(
                    SortKey.LAST_MODIFIED to "Modified",
                    SortKey.CREATED to "Created",
                    SortKey.REMINDER to "Reminder",
                )
                keys.forEachIndexed { idx, (key, label) ->
                    SegmentedButton(
                        selected = viewOptions.sortKey == key,
                        onClick = { onChange(viewOptions.copy(sortKey = key)) },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = keys.size),
                        label = { Text(label) },
                    )
                }
            }
            RememberFilledTonalIconButton(
                onClick = {
                    val next = if (viewOptions.sortDir == SortDir.DESC) SortDir.ASC else SortDir.DESC
                    onChange(viewOptions.copy(sortDir = next))
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                RememberMaterialRoundedSymbol(
                    name = if (viewOptions.sortDir == SortDir.DESC) "arrow_downward" else "arrow_upward",
                    weight = FontWeight.Medium,
                    modifier = Modifier.semantics {
                        contentDescription =
                            if (viewOptions.sortDir == SortDir.DESC) "Descending" else "Ascending"
                    },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("Group by")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val groups = listOf(
                GroupBy.NONE to "None",
                GroupBy.TAG to "Tags",
                GroupBy.TYPE to "Type",
            )
            groups.forEachIndexed { idx, (g, label) ->
                SegmentedButton(
                    selected = viewOptions.groupBy == g,
                    onClick = { onChange(viewOptions.copy(groupBy = g)) },
                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = groups.size),
                    label = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
