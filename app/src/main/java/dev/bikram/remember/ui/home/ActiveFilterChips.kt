package dev.bikram.remember.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.data.FilterType
import dev.bikram.remember.data.GroupBy
import dev.bikram.remember.data.NotesFilter
import dev.bikram.remember.data.SortDir
import dev.bikram.remember.data.SortKey
import dev.bikram.remember.data.ViewOptions
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberCheckbox
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.components.RememberInputChip
import dev.bikram.remember.ui.components.tagColor
import kotlinx.collections.immutable.toPersistentSet

private val ActiveChipHeight = 32.dp

@Composable
internal fun ActiveFilterChips(
    filter: NotesFilter,
    onChange: (NotesFilter) -> Unit,
    modifier: Modifier = Modifier,
    viewOptions: ViewOptions = ViewOptions(),
    onViewOptionsChange: ((ViewOptions) -> Unit)? = null,
    availableTags: List<String> = emptyList(),
    scrollState: ScrollState = rememberScrollState(),
    expandedDropdown: ActiveFilterDropdown? = null,
    onExpandedDropdownChange: (ActiveFilterDropdown?) -> Unit = {},
) {
    val defaultFilter = NotesFilter()
    val defaultViewOptions = ViewOptions()
    val canReset = filter != defaultFilter || viewOptions != defaultViewOptions
    Row(
        modifier =
            modifier
                .horizontalScroll(scrollState)
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterDropdownChip(
            label = stringResource(R.string.filter_dropdown_type, typeLabel(filter.type)),
            selected = filter.type != FilterType.ALL,
            expanded = expandedDropdown == ActiveFilterDropdown.TYPE,
            onExpandedChange = { expanded ->
                onExpandedDropdownChange(if (expanded) ActiveFilterDropdown.TYPE else null)
            },
        ) {
            FilterType.entries.forEach { type ->
                RadioMenuItem(
                    label = typeLabel(type),
                    selected = filter.type == type,
                    onClick = {
                        onChange(filter.copy(type = type))
                    },
                )
            }
        }
        FilterDropdownChip(
            label = stringResource(R.string.filter_dropdown_group, groupLabel(viewOptions.groupBy)),
            selected = viewOptions.groupBy != defaultViewOptions.groupBy,
            expanded = expandedDropdown == ActiveFilterDropdown.GROUP,
            onExpandedChange = { expanded ->
                onExpandedDropdownChange(if (expanded) ActiveFilterDropdown.GROUP else null)
            },
        ) {
            groupOptions().forEach { (groupBy, label) ->
                RadioMenuItem(
                    label = label,
                    selected = viewOptions.groupBy == groupBy,
                    onClick = {
                        onViewOptionsChange?.invoke(viewOptions.copy(groupBy = groupBy))
                    },
                )
            }
        }
        FilterDropdownChip(
            label = stringResource(R.string.filter_dropdown_sort, sortLabel(viewOptions.sortKey, viewOptions.sortDir)),
            selected = viewOptions.sortKey != defaultViewOptions.sortKey || viewOptions.sortDir != defaultViewOptions.sortDir,
            expanded = expandedDropdown == ActiveFilterDropdown.SORT,
            onExpandedChange = { expanded ->
                onExpandedDropdownChange(if (expanded) ActiveFilterDropdown.SORT else null)
            },
        ) {
            sortOptions().forEach { sortOption ->
                RadioMenuItem(
                    label = sortOption.label,
                    selected = viewOptions.sortKey == sortOption.sortKey && viewOptions.sortDir == sortOption.sortDir,
                    onClick = {
                        onViewOptionsChange?.invoke(
                            viewOptions.copy(sortKey = sortOption.sortKey, sortDir = sortOption.sortDir),
                        )
                    },
                )
            }
        }
        val tagLabel =
            when (filter.tags.size) {
                0 -> stringResource(R.string.filter_dropdown_tags)
                1 -> stringResource(R.string.filter_dropdown_tags_one, filter.tags.first())
                else -> stringResource(R.string.filter_dropdown_tags_count, filter.tags.size)
            }
        FilterDropdownChip(
            label = tagLabel,
            selected = filter.tags.isNotEmpty(),
            enabled = availableTags.isNotEmpty(),
            expanded = expandedDropdown == ActiveFilterDropdown.TAGS,
            onExpandedChange = { expanded ->
                onExpandedDropdownChange(if (expanded) ActiveFilterDropdown.TAGS else null)
            },
            avatar =
                filter.tags.singleOrNull()?.let { selectedTag ->
                    {
                        TagColorAvatar(tag = selectedTag)
                    }
                },
        ) {
            availableTags.forEach { tag ->
                val checked = filter.tags.any { selectedTag -> selectedTag.equals(tag, ignoreCase = true) }
                CheckableMenuItem(
                    label = tag,
                    checked = checked,
                    onClick = {
                        val nextTags = filter.tags.toMutableSet()
                        if (checked) {
                            nextTags.removeIf { selectedTag -> selectedTag.equals(tag, ignoreCase = true) }
                        } else {
                            nextTags.add(tag)
                        }
                        onChange(filter.copy(tags = nextTags.toPersistentSet()))
                    },
                )
            }
        }
        val otherFilterCount =
            listOf(
                filter.hasReminder,
                filter.hasPicture,
                filter.hasAttachment,
                filter.favorite,
            ).count { active -> active == true }
        FilterDropdownChip(
            label =
                if (otherFilterCount == 0) {
                    stringResource(R.string.filter_dropdown_others)
                } else {
                    stringResource(R.string.filter_dropdown_others_count, otherFilterCount)
                },
            selected = otherFilterCount > 0,
            expanded = expandedDropdown == ActiveFilterDropdown.OTHERS,
            onExpandedChange = { expanded ->
                onExpandedDropdownChange(if (expanded) ActiveFilterDropdown.OTHERS else null)
            },
        ) {
            CheckableMenuItem(
                label = stringResource(R.string.filter_has_reminder),
                checked = filter.hasReminder == true,
                onClick = { onChange(filter.copy(hasReminder = if (filter.hasReminder == true) null else true)) },
            )
            CheckableMenuItem(
                label = stringResource(R.string.filter_has_picture),
                checked = filter.hasPicture == true,
                onClick = { onChange(filter.copy(hasPicture = if (filter.hasPicture == true) null else true)) },
            )
            CheckableMenuItem(
                label = stringResource(R.string.filter_has_attachment),
                checked = filter.hasAttachment == true,
                onClick = { onChange(filter.copy(hasAttachment = if (filter.hasAttachment == true) null else true)) },
            )
            CheckableMenuItem(
                label = stringResource(R.string.filter_favorites),
                checked = filter.favorite == true,
                onClick = { onChange(filter.copy(favorite = if (filter.favorite == true) null else true)) },
            )
        }
        RememberInputChip(
            selected = false,
            onClick = {
                onChange(defaultFilter)
                onViewOptionsChange?.invoke(defaultViewOptions)
            },
            enabled = canReset,
            label = { Text(stringResource(R.string.action_reset)) },
            modifier = Modifier.height(ActiveChipHeight),
            leadingIcon = {
                RememberMaterialRoundedSymbol(
                    name = "undo",
                    size = 16.dp,
                    weight = FontWeight.Medium,
                )
            },
        )
    }
}

internal enum class ActiveFilterDropdown {
    TYPE,
    GROUP,
    SORT,
    TAGS,
    OTHERS,
}

@Composable
private fun FilterDropdownChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    avatar: @Composable (() -> Unit)? = null,
    menuContent: @Composable () -> Unit,
) {
    Box {
        RememberInputChip(
            selected = selected,
            onClick = { onExpandedChange(true) },
            enabled = enabled,
            label = { Text(label) },
            modifier = Modifier.height(ActiveChipHeight),
            avatar = avatar,
            trailingIcon = {
                RememberMaterialRoundedSymbol(
                    name = "expand_more",
                    size = 16.dp,
                    weight = FontWeight.Medium,
                )
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            menuContent()
        }
    }
}

@Composable
private fun TagColorAvatar(tag: String) {
    Box(
        modifier = Modifier.padding(start = 4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(tagColor(tag)),
        )
    }
}

@Composable
private fun CheckableMenuItem(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    RememberDropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = {
            RememberCheckbox(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.size(24.dp),
            )
        },
    )
}

@Composable
private fun RadioMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    RememberDropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = {
            RadioButton(
                selected = selected,
                onClick = null,
                modifier = Modifier.size(24.dp),
            )
        },
    )
}

private data class SortOption(
    val sortKey: SortKey,
    val sortDir: SortDir,
    val label: String,
)

@Composable
private fun sortLabel(
    sortKey: SortKey,
    sortDir: SortDir,
): String =
    when (sortKey) {
        SortKey.REMINDER ->
            if (sortDir == SortDir.ASC) {
                stringResource(R.string.view_options_sort_reminder_soonest)
            } else {
                stringResource(R.string.view_options_sort_reminder_latest)
            }
        SortKey.LAST_MODIFIED ->
            if (sortDir == SortDir.ASC) {
                stringResource(R.string.view_options_sort_modified_oldest)
            } else {
                stringResource(R.string.view_options_sort_modified_newest)
            }
        SortKey.CREATED ->
            if (sortDir == SortDir.ASC) {
                stringResource(R.string.view_options_sort_created_oldest)
            } else {
                stringResource(R.string.view_options_sort_created_newest)
            }
    }

@Composable
private fun sortOptions(): List<SortOption> =
    listOf(
        SortOption(SortKey.REMINDER, SortDir.ASC, stringResource(R.string.view_options_sort_reminder_soonest)),
        SortOption(SortKey.REMINDER, SortDir.DESC, stringResource(R.string.view_options_sort_reminder_latest)),
        SortOption(SortKey.LAST_MODIFIED, SortDir.DESC, stringResource(R.string.view_options_sort_modified_newest)),
        SortOption(SortKey.LAST_MODIFIED, SortDir.ASC, stringResource(R.string.view_options_sort_modified_oldest)),
        SortOption(SortKey.CREATED, SortDir.DESC, stringResource(R.string.view_options_sort_created_newest)),
        SortOption(SortKey.CREATED, SortDir.ASC, stringResource(R.string.view_options_sort_created_oldest)),
    )

@Composable
private fun groupOptions(): List<Pair<GroupBy, String>> =
    listOf(
        GroupBy.NONE to stringResource(R.string.view_options_group_none),
        GroupBy.DATE to stringResource(R.string.view_options_group_date),
        GroupBy.TAG to stringResource(R.string.view_options_group_tags),
        GroupBy.TYPE to stringResource(R.string.view_options_group_type),
    )

@Composable
private fun typeLabel(filterType: FilterType): String =
    when (filterType) {
        FilterType.ALL -> stringResource(R.string.filter_type_all)
        FilterType.NOTE -> stringResource(R.string.filter_type_notes)
        FilterType.LIST -> stringResource(R.string.filter_type_lists)
    }

@Composable
private fun groupLabel(groupBy: GroupBy): String =
    when (groupBy) {
        GroupBy.DATE -> stringResource(R.string.view_options_group_date)
        GroupBy.NONE -> stringResource(R.string.view_options_group_none)
        GroupBy.TAG -> stringResource(R.string.view_options_group_tags)
        GroupBy.TYPE -> stringResource(R.string.view_options_group_type)
    }
