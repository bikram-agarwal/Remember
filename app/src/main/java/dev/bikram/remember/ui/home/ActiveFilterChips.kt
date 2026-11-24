package dev.bikram.remember.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import dev.bikram.remember.ui.components.RememberToggleButton
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
    val canReset =
        filter != defaultFilter ||
            viewOptions.sortKey != defaultViewOptions.sortKey ||
            viewOptions.sortDir != defaultViewOptions.sortDir ||
            viewOptions.groupBy != defaultViewOptions.groupBy
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
            label =
                stringResource(
                    R.string.filter_dropdown_sort,
                    "${sortKeyLabel(viewOptions.sortKey)} ${sortChipArrow(viewOptions.sortKey, viewOptions.sortDir)}",
                ),
            selected = viewOptions.sortKey != defaultViewOptions.sortKey || viewOptions.sortDir != defaultViewOptions.sortDir,
            expanded = expandedDropdown == ActiveFilterDropdown.SORT,
            onExpandedChange = { expanded ->
                onExpandedDropdownChange(if (expanded) ActiveFilterDropdown.SORT else null)
            },
        ) {
            SortDirectionToggleRow(
                sortKey = viewOptions.sortKey,
                sortDir = viewOptions.sortDir,
                onDirectionChange = { sortDir ->
                    onViewOptionsChange?.invoke(viewOptions.copy(sortDir = sortDir))
                },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            sortKeyOptions().forEach { (sortKey, label) ->
                RadioMenuItem(
                    label = label,
                    selected = viewOptions.sortKey == sortKey,
                    onClick = {
                        if (viewOptions.sortKey != sortKey) {
                            onViewOptionsChange?.invoke(
                                viewOptions.copy(
                                    sortKey = sortKey,
                                    sortDir = defaultSortDirFor(sortKey),
                                ),
                            )
                        }
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
                filter.starred,
                filter.pinned,
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
                label = stringResource(R.string.filter_starred),
                checked = filter.starred == true,
                onClick = { onChange(filter.copy(starred = if (filter.starred == true) null else true)) },
            )
            CheckableMenuItem(
                label = stringResource(R.string.filter_pinned),
                checked = filter.pinned == true,
                onClick = { onChange(filter.copy(pinned = if (filter.pinned == true) null else true)) },
            )
        }
        RememberInputChip(
            selected = false,
            onClick = {
                onChange(defaultFilter)
                onViewOptionsChange?.invoke(
                    viewOptions.copy(
                        sortKey = defaultViewOptions.sortKey,
                        sortDir = defaultViewOptions.sortDir,
                        groupBy = defaultViewOptions.groupBy,
                    ),
                )
            },
            enabled = canReset,
            label = { Text(stringResource(R.string.action_reset)) },
            modifier = Modifier.height(ActiveChipHeight),
            leadingIcon = {
                RememberMaterialRoundedSymbol(
                    name = "undo",
                    autoMirror = true,
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SortDirectionToggleRow(
    sortKey: SortKey,
    sortDir: SortDir,
    onDirectionChange: (SortDir) -> Unit,
) {
    val directions =
        when (sortKey) {
            SortKey.REMINDER,
            SortKey.ALPHABETICAL,
            ->
                listOf(
                    SortDir.ASC to sortDirectionLabel(sortKey, SortDir.ASC),
                    SortDir.DESC to sortDirectionLabel(sortKey, SortDir.DESC),
                )
            SortKey.LAST_MODIFIED,
            SortKey.CREATED,
            SortKey.IMPORTANCE,
            ->
                listOf(
                    SortDir.DESC to sortDirectionLabel(sortKey, SortDir.DESC),
                    SortDir.ASC to sortDirectionLabel(sortKey, SortDir.ASC),
                )
        }
    val shapes =
        listOf(
            ButtonGroupDefaults.connectedLeadingButtonShapes(),
            ButtonGroupDefaults.connectedTrailingButtonShapes(),
        )
    val colors =
        ToggleButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    ButtonGroup(
        modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(min = 216.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
    ) {
        directions.forEachIndexed { index, (dir, label) ->
            customItem(
                buttonGroupContent = {
                    RememberToggleButton(
                        checked = sortDir == dir,
                        onCheckedChange = { checked -> if (checked) onDirectionChange(dir) },
                        modifier = Modifier.weight(1f).height(36.dp).semantics { role = Role.RadioButton },
                        shapes = shapes[index],
                        colors = colors,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    ) {
                        if (sortDir == dir) {
                            RememberMaterialRoundedSymbol(
                                name = "check",
                                size = 16.dp,
                                weight = FontWeight.Medium,
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                menuContent = {},
            )
        }
    }
}

@Composable
private fun sortKeyLabel(sortKey: SortKey): String =
    when (sortKey) {
        SortKey.REMINDER -> stringResource(R.string.view_options_sort_reminder)
        SortKey.LAST_MODIFIED -> stringResource(R.string.view_options_sort_modified)
        SortKey.CREATED -> stringResource(R.string.view_options_sort_created)
        SortKey.ALPHABETICAL -> stringResource(R.string.view_options_sort_alphabetical)
        SortKey.IMPORTANCE -> stringResource(R.string.view_options_sort_importance)
    }

@Composable
private fun sortDirectionLabel(
    sortKey: SortKey,
    sortDir: SortDir,
): String =
    when (sortKey) {
        SortKey.REMINDER ->
            stringResource(
                if (sortDir == SortDir.ASC) {
                    R.string.view_options_sort_dir_soonest
                } else {
                    R.string.view_options_sort_dir_farthest
                },
            )
        SortKey.LAST_MODIFIED,
        SortKey.CREATED,
        ->
            stringResource(
                if (sortDir == SortDir.ASC) {
                    R.string.view_options_sort_dir_oldest
                } else {
                    R.string.view_options_sort_dir_newest
                },
            )
        SortKey.ALPHABETICAL ->
            stringResource(
                if (sortDir == SortDir.ASC) {
                    R.string.view_options_sort_dir_az
                } else {
                    R.string.view_options_sort_dir_za
                },
            )
        SortKey.IMPORTANCE ->
            stringResource(
                if (sortDir == SortDir.ASC) {
                    R.string.view_options_sort_dir_lowest
                } else {
                    R.string.view_options_sort_dir_highest
                },
            )
    }

@Composable
private fun sortKeyOptions(): List<Pair<SortKey, String>> =
    listOf(
        SortKey.REMINDER to stringResource(R.string.view_options_sort_reminder),
        SortKey.LAST_MODIFIED to stringResource(R.string.view_options_sort_modified),
        SortKey.CREATED to stringResource(R.string.view_options_sort_created),
        SortKey.ALPHABETICAL to stringResource(R.string.view_options_sort_alphabetical),
        SortKey.IMPORTANCE to stringResource(R.string.view_options_sort_importance),
    )

private fun sortChipArrow(
    sortKey: SortKey,
    sortDir: SortDir,
): String = if (sortDir == defaultSortDirFor(sortKey)) "↓" else "↑"

private fun defaultSortDirFor(sortKey: SortKey): SortDir =
    when (sortKey) {
        SortKey.REMINDER,
        SortKey.ALPHABETICAL,
        -> SortDir.ASC
        SortKey.LAST_MODIFIED,
        SortKey.CREATED,
        SortKey.IMPORTANCE,
        -> SortDir.DESC
    }

@Composable
private fun groupOptions(): List<Pair<GroupBy, String>> =
    listOf(
        GroupBy.NONE to stringResource(R.string.view_options_group_none),
        GroupBy.DATE to stringResource(R.string.view_options_group_date),
        GroupBy.TAG to stringResource(R.string.view_options_group_tags),
        GroupBy.TYPE to stringResource(R.string.view_options_group_type),
        GroupBy.IMPORTANCE to stringResource(R.string.view_options_group_importance),
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
        GroupBy.IMPORTANCE -> stringResource(R.string.view_options_group_importance)
    }
