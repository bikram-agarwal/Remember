package dev.bikram.remember.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.data.NoteSwipeAction
import dev.bikram.remember.data.SwipeGestureMode
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.components.RememberOutlinedButton
import dev.bikram.remember.ui.theme.semanticSwipeBackground
import dev.bikram.remember.ui.theme.semanticSwipeIconTint

@Composable
internal fun SwipeGestureModeDropdown(
    current: SwipeGestureMode,
    onSelect: (SwipeGestureMode) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    RememberOutlinedButton(onClick = { expanded = true }) {
        Text(swipeGestureModeLabel(current))
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            SwipeGestureMode.entries.forEach { mode ->
                RememberDropdownMenuItem(
                    text = { Text(swipeGestureModeLabel(mode)) },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun SwipeRevealSlotsRow(
    title: String,
    actions: List<NoteSwipeAction?>,
    onActionsChange: (List<NoteSwipeAction?>) -> Unit,
) {
    val normalizedActions = List(3) { slotIndex -> actions.getOrNull(slotIndex) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            dev.bikram.remember.ui.components.RememberTextButton(
                onClick = { onActionsChange(listOf(null, null, null)) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(stringResource(R.string.action_reset))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            normalizedActions.forEachIndexed { slotIndex, action ->
                SwipeRevealSlotDropdown(
                    current = action,
                    unavailableActions =
                        normalizedActions
                            .filterIndexed { otherIndex, _ -> otherIndex != slotIndex }
                            .filterNotNull()
                            .toSet(),
                    onSelect = { selected ->
                        val nextActions = normalizedActions.toMutableList()
                        nextActions[slotIndex] = selected
                        onActionsChange(nextActions)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SwipeRevealSlotDropdown(
    current: NoteSwipeAction?,
    unavailableActions: Set<NoteSwipeAction>,
    onSelect: (NoteSwipeAction?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    RememberOutlinedButton(
        onClick = { expanded = true },
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        SwipeActionLabelContent(action = current)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            RememberDropdownMenuItem(
                text = { Text(stringResource(R.string.settings_swipe_none)) },
                leadingIcon = { SwipeActionIcon(action = null) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            SwipeActionDisplayOrder
                .filter { action -> action == current || action !in unavailableActions }
                .forEach { action ->
                    RememberDropdownMenuItem(
                        text = { Text(noteSwipeActionLabel(action)) },
                        leadingIcon = { SwipeActionIcon(action = action) },
                        onClick = {
                            onSelect(action)
                            expanded = false
                        },
                    )
                }
        }
    }
}

@Composable
internal fun NoteSwipeActionDropdown(
    current: NoteSwipeAction,
    excluded: NoteSwipeAction,
    onSelect: (NoteSwipeAction) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    RememberOutlinedButton(onClick = { expanded = true }) {
        SwipeActionLabelContent(action = current)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            SwipeActionDisplayOrder.filter { it != excluded }.forEach { action ->
                RememberDropdownMenuItem(
                    text = { Text(noteSwipeActionLabel(action)) },
                    leadingIcon = { SwipeActionIcon(action = action) },
                    onClick = {
                        onSelect(action)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SwipeActionLabelContent(action: NoteSwipeAction?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SwipeActionIcon(action = action)
        Text(
            text = action?.let { noteSwipeActionLabel(it) } ?: stringResource(R.string.settings_swipe_none),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SwipeActionIcon(action: NoteSwipeAction?) {
    val tint = action?.semanticSwipeIconTint() ?: MaterialTheme.colorScheme.onSurfaceVariant
    val container = action?.semanticSwipeBackground() ?: MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier =
            Modifier
                .size(24.dp)
                .clip(MaterialTheme.shapes.small)
                .background(container),
        contentAlignment = Alignment.Center,
    ) {
        RememberMaterialRoundedSymbol(
            name = action?.materialSymbolName ?: "remove",
            size = 16.dp,
            tint = tint,
            filled = action == NoteSwipeAction.TOGGLE_FAVORITE,
            weight = FontWeight.Medium,
        )
    }
}

private val SwipeActionDisplayOrder: List<NoteSwipeAction> =
    listOf(
        NoteSwipeAction.EDIT,
        NoteSwipeAction.DUPLICATE,
        NoteSwipeAction.TOGGLE_FAVORITE,
        NoteSwipeAction.MARK_DONE,
        NoteSwipeAction.ARCHIVE,
        NoteSwipeAction.TRASH,
    )

@Composable
internal fun noteSwipeActionLabel(action: NoteSwipeAction): String =
    stringResource(
        when (action) {
            NoteSwipeAction.EDIT -> R.string.swipe_action_open
            NoteSwipeAction.TRASH -> R.string.edit_bottom_bar_trash
            NoteSwipeAction.DUPLICATE -> R.string.swipe_action_duplicate
            NoteSwipeAction.TOGGLE_FAVORITE -> R.string.swipe_action_toggle_favorite
            NoteSwipeAction.ARCHIVE -> R.string.edit_bottom_bar_archive
            NoteSwipeAction.MARK_DONE -> R.string.swipe_action_mark_done
        },
    )

@Composable
private fun swipeGestureModeLabel(mode: SwipeGestureMode): String =
    stringResource(
        when (mode) {
            SwipeGestureMode.EXECUTE_ONE -> R.string.settings_swipe_mode_execute_one
            SwipeGestureMode.REVEAL_ACTIONS -> R.string.settings_swipe_mode_reveal_actions
        },
    )

@Composable
internal fun NoteSwipePreviewCard(
    swipeStartToEnd: NoteSwipeAction,
    swipeEndToStart: NoteSwipeAction,
) {
    val leftBackground by animateColorAsState(
        targetValue = swipeStartToEnd.semanticSwipeBackground(),
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "leftBg",
    )
    val rightBackground by animateColorAsState(
        targetValue = swipeEndToStart.semanticSwipeBackground(),
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "rightBg",
    )
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .background(leftBackground)
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RememberMaterialRoundedSymbol(
                    name = swipeStartToEnd.materialSymbolName,
                    size = 20.dp,
                    tint = swipeStartToEnd.semanticSwipeIconTint(),
                    weight = FontWeight.Medium,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    noteSwipeActionLabel(swipeStartToEnd),
                    style = MaterialTheme.typography.labelMedium,
                    color = swipeStartToEnd.semanticSwipeIconTint(),
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .background(rightBackground)
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    noteSwipeActionLabel(swipeEndToStart),
                    style = MaterialTheme.typography.labelMedium,
                    color = swipeEndToStart.semanticSwipeIconTint(),
                )
                Spacer(Modifier.width(6.dp))
                RememberMaterialRoundedSymbol(
                    name = swipeEndToStart.materialSymbolName,
                    size = 20.dp,
                    tint = swipeEndToStart.semanticSwipeIconTint(),
                    weight = FontWeight.Medium,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .fillMaxWidth(0.42f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.settings_swipe_preview_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
