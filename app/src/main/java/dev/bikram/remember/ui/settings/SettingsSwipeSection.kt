package dev.bikram.remember.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.bikram.remember.R
import dev.bikram.remember.data.NoteSwipeAction
import dev.bikram.remember.data.SwipeGestureMode
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.components.RememberOutlinedButton
import dev.bikram.remember.ui.theme.semanticSwipeBackground
import dev.bikram.remember.ui.theme.semanticSwipeIconTint

private const val SWIPE_REVEAL_SLOT_COUNT = 3
private const val SWIPE_REVEAL_TOTAL_SLOT_COUNT = SWIPE_REVEAL_SLOT_COUNT * 2

private enum class SwipeDirectionCue {
    LEFT,
    RIGHT,
}

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
internal fun SwipeRevealSlotsEditor(
    startTitle: String,
    endTitle: String,
    startActions: List<NoteSwipeAction?>,
    endActions: List<NoteSwipeAction?>,
    onActionsChange: (startActions: List<NoteSwipeAction?>, endActions: List<NoteSwipeAction?>) -> Unit,
) {
    val slotActions = fullSwipeSlotActions(startActions, endActions)
    val slotBounds = remember { mutableStateMapOf<Int, Rect>() }
    var draggingSlot by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragPointerRoot by remember { mutableStateOf(Offset.Zero) }

    fun finishDrag() {
        val fromSlot = draggingSlot ?: return
        val toSlot = slotBounds.entries.firstOrNull { (_, bounds) -> bounds.contains(dragPointerRoot) }?.key
        if (toSlot != null && toSlot != fromSlot) {
            val nextActions = slotActions.toMutableList()
            val fromAction = nextActions[fromSlot]
            nextActions[fromSlot] = nextActions[toSlot]
            nextActions[toSlot] = fromAction
            onActionsChange(
                nextActions.take(SWIPE_REVEAL_SLOT_COUNT),
                nextActions.drop(SWIPE_REVEAL_SLOT_COUNT),
            )
        }
        draggingSlot = null
        dragOffset = Offset.Zero
        dragPointerRoot = Offset.Zero
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SwipeRevealDirectionSlotsRow(
            title = startTitle,
            direction = SwipeDirectionCue.RIGHT,
            slotStartIndex = 0,
            slotActions = slotActions,
            draggingSlot = draggingSlot,
            dragOffset = dragOffset,
            slotBounds = slotBounds,
            onSlotBoundsChange = { slotIndex, bounds -> slotBounds[slotIndex] = bounds },
            onDragStart = { slotIndex, pointerRoot ->
                draggingSlot = slotIndex
                dragOffset = Offset.Zero
                dragPointerRoot = pointerRoot
            },
            onDrag = { delta ->
                dragOffset += delta
                dragPointerRoot += delta
            },
            onDragEnd = ::finishDrag,
        )
        SwipeRevealDirectionSlotsRow(
            title = endTitle,
            direction = SwipeDirectionCue.LEFT,
            slotStartIndex = SWIPE_REVEAL_SLOT_COUNT,
            slotActions = slotActions,
            draggingSlot = draggingSlot,
            dragOffset = dragOffset,
            slotBounds = slotBounds,
            onSlotBoundsChange = { slotIndex, bounds -> slotBounds[slotIndex] = bounds },
            onDragStart = { slotIndex, pointerRoot ->
                draggingSlot = slotIndex
                dragOffset = Offset.Zero
                dragPointerRoot = pointerRoot
            },
            onDrag = { delta ->
                dragOffset += delta
                dragPointerRoot += delta
            },
            onDragEnd = ::finishDrag,
        )
    }
}

@Composable
internal fun SwipeExecuteOneActionsEditor(
    startTitle: String,
    endTitle: String,
    startAction: NoteSwipeAction,
    endAction: NoteSwipeAction,
    onStartActionChange: (NoteSwipeAction) -> Unit,
    onEndActionChange: (NoteSwipeAction) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SwipeExecuteOneActionRow(
            title = startTitle,
            direction = SwipeDirectionCue.RIGHT,
            action = startAction,
            excluded = endAction,
            onActionChange = onStartActionChange,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)),
        )
        SwipeExecuteOneActionRow(
            title = endTitle,
            direction = SwipeDirectionCue.LEFT,
            action = endAction,
            excluded = startAction,
            onActionChange = onEndActionChange,
        )
    }
}

@Composable
private fun SwipeExecuteOneActionRow(
    title: String,
    direction: SwipeDirectionCue,
    action: NoteSwipeAction,
    excluded: NoteSwipeAction,
    onActionChange: (NoteSwipeAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (direction == SwipeDirectionCue.RIGHT) {
            NoteSwipeActionDropdown(
                current = action,
                excluded = excluded,
                onSelect = onActionChange,
                modifier = Modifier.width(156.dp),
            )
            SwipeDirectionTitle(title = title)
            SwipeDirectionCueText(direction = direction)
            Spacer(Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
            SwipeDirectionCueText(direction = direction)
            SwipeDirectionTitle(
                title = title,
                textAlign = TextAlign.End,
            )
            NoteSwipeActionDropdown(
                current = action,
                excluded = excluded,
                onSelect = onActionChange,
                modifier = Modifier.width(156.dp),
            )
        }
    }
}

@Composable
private fun SwipeRevealDirectionSlotsRow(
    title: String,
    direction: SwipeDirectionCue,
    slotStartIndex: Int,
    slotActions: List<NoteSwipeAction>,
    draggingSlot: Int?,
    dragOffset: Offset,
    slotBounds: Map<Int, Rect>,
    onSlotBoundsChange: (slotIndex: Int, bounds: Rect) -> Unit,
    onDragStart: (slotIndex: Int, pointerRoot: Offset) -> Unit,
    onDrag: (delta: Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SwipeDirectionHeader(title = title, direction = direction)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(SWIPE_REVEAL_SLOT_COUNT) { rowSlotIndex ->
                val slotIndex = slotStartIndex + rowSlotIndex
                val action = slotActions[slotIndex]
                val isDragging = draggingSlot == slotIndex
                SwipeRevealSlotChip(
                    action = action,
                    modifier =
                        Modifier
                            .weight(1f)
                            .onGloballyPositioned { coordinates ->
                                onSlotBoundsChange(slotIndex, coordinates.boundsInRoot())
                            }.zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationX = if (isDragging) dragOffset.x else 0f
                                translationY = if (isDragging) dragOffset.y else 0f
                                scaleX = if (isDragging) 1.04f else 1f
                                scaleY = if (isDragging) 1.04f else 1f
                            }.pointerInput(slotIndex, action) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { localOffset ->
                                        val slotTopLeft = slotBounds[slotIndex]?.topLeft ?: Offset.Zero
                                        onDragStart(slotIndex, slotTopLeft + localOffset)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onDrag(dragAmount)
                                    },
                                    onDragCancel = onDragEnd,
                                    onDragEnd = onDragEnd,
                                )
                            },
                )
            }
        }
    }
}

@Composable
private fun SwipeDirectionHeader(
    title: String,
    direction: SwipeDirectionCue,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (direction == SwipeDirectionCue.RIGHT) {
            SwipeDirectionTitle(title = title)
            SwipeDirectionCueText(direction = direction)
            Spacer(Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
            SwipeDirectionCueText(direction = direction)
            SwipeDirectionTitle(title = title, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun SwipeDirectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier,
    )
}

@Composable
private fun SwipeDirectionCueText(
    direction: SwipeDirectionCue,
    modifier: Modifier = Modifier,
) {
    Text(
        text =
            when (direction) {
                SwipeDirectionCue.LEFT -> "<--------"
                SwipeDirectionCue.RIGHT -> "-------->"
            },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
private fun SwipeRevealSlotChip(
    action: NoteSwipeAction,
    modifier: Modifier = Modifier,
) {
    ElevatedFilterChip(
        selected = true,
        onClick = {},
        modifier = modifier,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = noteSwipeActionLabel(action),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingIcon = { SwipeActionIcon(action = action) },
    )
}

@Composable
internal fun NoteSwipeActionDropdown(
    current: NoteSwipeAction,
    excluded: NoteSwipeAction,
    onSelect: (NoteSwipeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    RememberOutlinedButton(onClick = { expanded = true }, modifier = modifier) {
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

private fun fullSwipeSlotActions(
    startActions: List<NoteSwipeAction?>,
    endActions: List<NoteSwipeAction?>,
): List<NoteSwipeAction> {
    val actions =
        (startActions + endActions)
            .filterNotNull()
            .distinct()
            .toMutableList()
    SwipeActionDisplayOrder.forEach { action ->
        if (action !in actions) actions += action
    }
    return actions.take(SWIPE_REVEAL_TOTAL_SLOT_COUNT)
}

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
