package dev.bikram.remember.ui.settings

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.bikram.remember.R
import dev.bikram.remember.data.DEFAULT_SWIPE_END_TO_START_REVEAL_ACTIONS
import dev.bikram.remember.data.DEFAULT_SWIPE_START_TO_END_REVEAL_ACTIONS
import dev.bikram.remember.data.NoteSwipeAction
import dev.bikram.remember.data.SwipeGestureMode
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.components.RememberToggleButton
import dev.bikram.remember.ui.feedback.LocalHapticEnabled
import dev.bikram.remember.ui.feedback.appClickable
import dev.bikram.remember.ui.feedback.performLongPressHaptic
import dev.bikram.remember.ui.theme.swipeActionAccent

private const val SWIPE_REVEAL_SLOT_COUNT = 3
private const val SWIPE_REVEAL_TOTAL_SLOT_COUNT = SWIPE_REVEAL_SLOT_COUNT * 2

private enum class SwipeDirectionCue(
    val iconName: String,
) {
    LEFT("arrow_back"),
    RIGHT("arrow_forward"),
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SwipeGestureSettingsPanel(
    currentMode: SwipeGestureMode,
    onModeChange: (SwipeGestureMode) -> Unit,
    startAction: NoteSwipeAction,
    endAction: NoteSwipeAction,
    onStartActionChange: (NoteSwipeAction) -> Unit,
    onEndActionChange: (NoteSwipeAction) -> Unit,
    startActions: List<NoteSwipeAction?>,
    endActions: List<NoteSwipeAction?>,
    onRevealActionsChange: (startActions: List<NoteSwipeAction?>, endActions: List<NoteSwipeAction?>) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SwipeGestureModeSegmentedControl(
            current = currentMode,
            onSelect = onModeChange,
        )
        if (currentMode == SwipeGestureMode.EXECUTE_ONE) {
            SwipeExecuteOneActionsEditor(
                startAction = startAction,
                endAction = endAction,
                onStartActionChange = onStartActionChange,
                onEndActionChange = onEndActionChange,
            )
        } else {
            SwipeRevealSlotsEditor(
                startActions = startActions,
                endActions = endActions,
                onActionsChange = onRevealActionsChange,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SwipeGestureModeSegmentedControl(
    current: SwipeGestureMode,
    onSelect: (SwipeGestureMode) -> Unit,
) {
    val entries =
        remember {
            listOf(
                SwipeGestureMode.REVEAL_ACTIONS,
                SwipeGestureMode.EXECUTE_ONE,
            )
        }
    val colors =
        ToggleButtonDefaults.toggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    val labels = entries.map { mode -> swipeGestureModeTitle(mode) }
    val shapes =
        entries.mapIndexed { index, _ ->
            when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
        }
    ButtonGroup(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
    ) {
        entries.forEachIndexed { index, mode ->
            val label = labels[index]
            customItem(
                buttonGroupContent = {
                    RememberToggleButton(
                        checked = current == mode,
                        onCheckedChange = { checked -> if (checked) onSelect(mode) },
                        modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                        shapes = shapes[index],
                        colors = colors,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                menuContent = { menuState ->
                    RememberDropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSelect(mode)
                            menuState.dismiss()
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun SwipeRevealSlotsEditor(
    startActions: List<NoteSwipeAction?>,
    endActions: List<NoteSwipeAction?>,
    onActionsChange: (startActions: List<NoteSwipeAction?>, endActions: List<NoteSwipeAction?>) -> Unit,
) {
    val slotActions = fullSwipeSlotActions(startActions, endActions)
    val slotBounds = remember { mutableStateMapOf<Int, Rect>() }
    var draggingSlot by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragPointerRoot by remember { mutableStateOf(Offset.Zero) }
    val dropTargetSlot =
        draggingSlot?.let { fromSlot ->
            slotBounds.entries
                .firstOrNull { (slotIndex, bounds) -> slotIndex != fromSlot && bounds.contains(dragPointerRoot) }
                ?.key
        }

    fun commitSlotActions(actions: List<NoteSwipeAction>) {
        onActionsChange(
            actions.take(SWIPE_REVEAL_SLOT_COUNT),
            actions.drop(SWIPE_REVEAL_SLOT_COUNT),
        )
    }

    fun replaceSlotAction(
        slotIndex: Int,
        newAction: NoteSwipeAction,
    ) {
        val currentAction = slotActions.getOrNull(slotIndex) ?: return
        if (currentAction == newAction) return

        val nextActions = slotActions.toMutableList()
        val existingIndex = nextActions.indexOf(newAction)
        if (existingIndex != -1) {
            nextActions[existingIndex] = currentAction
        }
        nextActions[slotIndex] = newAction
        commitSlotActions(nextActions)
    }

    fun finishDrag() {
        val fromSlot = draggingSlot ?: return
        val toSlot = slotBounds.entries.firstOrNull { (_, bounds) -> bounds.contains(dragPointerRoot) }?.key
        if (toSlot != null && toSlot != fromSlot) {
            val nextActions = slotActions.toMutableList()
            val fromAction = nextActions[fromSlot]
            nextActions[fromSlot] = nextActions[toSlot]
            nextActions[toSlot] = fromAction
            commitSlotActions(nextActions)
        }
        draggingSlot = null
        dragOffset = Offset.Zero
        dragPointerRoot = Offset.Zero
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SwipeRevealDirectionSection(
            title = stringResource(R.string.settings_swipe_right_label),
            direction = SwipeDirectionCue.RIGHT,
            slotStartIndex = 0,
            slotActions = slotActions,
            draggingSlot = draggingSlot,
            dropTargetSlot = dropTargetSlot,
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
            onSlotActionReplace = ::replaceSlotAction,
            modifier = Modifier.zIndex(if (draggingSlot != null && draggingSlot!! < SWIPE_REVEAL_SLOT_COUNT) 1f else 0f),
        )
        SwipePanelDivider()
        SwipeRevealDirectionSection(
            title = stringResource(R.string.settings_swipe_left_label),
            direction = SwipeDirectionCue.LEFT,
            slotStartIndex = SWIPE_REVEAL_SLOT_COUNT,
            slotActions = slotActions,
            draggingSlot = draggingSlot,
            dropTargetSlot = dropTargetSlot,
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
            onSlotActionReplace = ::replaceSlotAction,
            modifier = Modifier.zIndex(if (draggingSlot != null && draggingSlot!! >= SWIPE_REVEAL_SLOT_COUNT) 1f else 0f),
        )
        SwipePanelDivider()
        SwipeHintText(text = stringResource(R.string.settings_swipe_drag_hint))
    }
}

@Composable
private fun SwipeRevealDirectionSection(
    title: String,
    direction: SwipeDirectionCue,
    slotStartIndex: Int,
    slotActions: List<NoteSwipeAction>,
    draggingSlot: Int?,
    dropTargetSlot: Int?,
    dragOffset: Offset,
    slotBounds: Map<Int, Rect>,
    onSlotBoundsChange: (slotIndex: Int, bounds: Rect) -> Unit,
    onDragStart: (slotIndex: Int, pointerRoot: Offset) -> Unit,
    onDrag: (delta: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onSlotActionReplace: (slotIndex: Int, action: NoteSwipeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Reordering a slot starts with a long press, which appCombinedClickable cannot see because the
    // gesture lives in a raw pointerInput. Fire the shared haptic so it matches every other
    // long-press in the app.
    val hapticEnabled = LocalHapticEnabled.current
    val view = LocalView.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SwipeDirectionHeader(
            title = title,
            direction = direction,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(SWIPE_REVEAL_SLOT_COUNT) { rowSlotIndex ->
                val slotIndex = slotStartIndex + rowSlotIndex
                val action = slotActions[slotIndex]
                val isDragging = draggingSlot == slotIndex
                val isDropTarget = dropTargetSlot == slotIndex
                SwipeRevealSlotCard(
                    slotNumber = rowSlotIndex + 1,
                    action = action,
                    isDropTarget = isDropTarget,
                    onSelectAction = { selectedAction ->
                        onSlotActionReplace(slotIndex, selectedAction)
                    },
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
                                        if (hapticEnabled) view.performLongPressHaptic()
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
private fun SwipeExecuteOneActionsEditor(
    startAction: NoteSwipeAction,
    endAction: NoteSwipeAction,
    onStartActionChange: (NoteSwipeAction) -> Unit,
    onEndActionChange: (NoteSwipeAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SwipeExecuteDirectionColumn(
                title = stringResource(R.string.settings_swipe_right_label),
                direction = SwipeDirectionCue.RIGHT,
                action = startAction,
                availableActions = SwipeActionDisplayOrder.filter { it != endAction },
                onActionChange = onStartActionChange,
                modifier = Modifier.weight(1f),
            )
            SwipeExecuteDirectionColumn(
                title = stringResource(R.string.settings_swipe_left_label),
                direction = SwipeDirectionCue.LEFT,
                action = endAction,
                availableActions = SwipeActionDisplayOrder.filter { it != startAction },
                onActionChange = onEndActionChange,
                modifier = Modifier.weight(1f),
            )
        }
        SwipePanelDivider()
        SwipeHintText(text = stringResource(R.string.settings_swipe_tap_hint))
    }
}

@Composable
private fun SwipeExecuteDirectionColumn(
    title: String,
    direction: SwipeDirectionCue,
    action: NoteSwipeAction,
    availableActions: List<NoteSwipeAction>,
    onActionChange: (NoteSwipeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        SwipeExecuteDirectionHeader(
            title = title,
            direction = direction,
        )
        SwipeExecuteActionPicker(
            action = action,
            availableActions = availableActions,
            onActionChange = onActionChange,
        )
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
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        SwipeDirectionIcon(direction = direction)
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SwipeExecuteDirectionHeader(
    title: String,
    direction: SwipeDirectionCue,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement =
            if (direction == SwipeDirectionCue.LEFT) {
                Arrangement.End
            } else {
                Arrangement.Start
            },
    ) {
        if (direction == SwipeDirectionCue.RIGHT) {
            SwipeDirectionIcon(direction = direction)
            Spacer(Modifier.size(7.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (direction == SwipeDirectionCue.LEFT) {
            Spacer(Modifier.size(7.dp))
            SwipeDirectionIcon(direction = direction)
        }
    }
}

@Composable
private fun SwipeDirectionIcon(direction: SwipeDirectionCue) {
    Box(
        modifier =
            Modifier
                .size(28.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        RememberMaterialRoundedSymbol(
            name = direction.iconName,
            size = 15.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            weight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SwipeRevealSlotCard(
    slotNumber: Int,
    action: NoteSwipeAction,
    isDropTarget: Boolean,
    onSelectAction: (NoteSwipeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium
    val actionAccent = action.settingsSwipeAccent()
    val wiggleTransition = rememberInfiniteTransition(label = "swipe_drop_target_wiggle")
    val wiggleRotation by
        wiggleTransition.animateFloat(
            initialValue = -1.2f,
            targetValue = 1.2f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 90),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "swipe_drop_target_rotation",
        )
    Box(modifier = modifier) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .graphicsLayer {
                        rotationZ = if (isDropTarget) wiggleRotation else 0f
                        scaleX = if (isDropTarget) 1.03f else 1f
                        scaleY = if (isDropTarget) 1.03f else 1f
                    }.clip(shape)
                    .appClickable(role = Role.Button) { expanded = true },
            shape = shape,
            color = action.settingsSwipeTileColor(isDropTarget),
            border =
                BorderStroke(
                    width = 1.dp,
                    color = actionAccent.copy(alpha = if (isDropTarget) 0.82f else 0.55f),
                ),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SlotNumberBadge(number = slotNumber)
                Spacer(Modifier.size(3.dp))
                SwipeActionIconBubble(action = action, size = 22.dp, symbolSize = 13.dp)
                Spacer(Modifier.size(3.dp))
                Text(
                    text = swipeSettingsActionLabel(action),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                RememberMaterialRoundedSymbol(
                    name = "drag_indicator",
                    size = 12.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }
        }
        SwipeActionDropdownMenu(
            expanded = expanded,
            availableActions = SwipeActionDisplayOrder,
            onDismiss = { expanded = false },
            onSelect = { selectedAction ->
                expanded = false
                onSelectAction(selectedAction)
            },
        )
    }
}

@Composable
private fun SlotNumberBadge(
    number: Int,
) {
    Box(
        modifier =
            Modifier
                .size(15.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.42f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwipeExecuteActionPicker(
    action: NoteSwipeAction,
    availableActions: List<NoteSwipeAction>,
    onActionChange: (NoteSwipeAction) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium
    val actionAccent = action.settingsSwipeAccent()
    Box {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(shape)
                    .appClickable(role = Role.Button) { expanded = true },
            shape = shape,
            color = action.settingsSwipeTileColor(isDropTarget = false),
            border = BorderStroke(1.dp, actionAccent.copy(alpha = 0.55f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                SwipeActionIconBubble(action = action)
                Text(
                    text = swipeSettingsActionLabel(action),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                RememberMaterialRoundedSymbol(
                    name = "expand_more",
                    size = 17.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SwipeActionDropdownMenu(
            expanded = expanded,
            availableActions = availableActions,
            onDismiss = { expanded = false },
            onSelect = { selected ->
                onActionChange(selected)
                expanded = false
            },
        )
    }
}

@Composable
private fun SwipeActionDropdownMenu(
    expanded: Boolean,
    availableActions: List<NoteSwipeAction>,
    onDismiss: () -> Unit,
    onSelect: (NoteSwipeAction) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        availableActions.forEach { action ->
            RememberDropdownMenuItem(
                text = { Text(swipeSettingsActionLabel(action)) },
                leadingIcon = { SwipeActionIconBubble(action = action, size = 28.dp, symbolSize = 15.dp) },
                onClick = { onSelect(action) },
            )
        }
    }
}

@Composable
private fun SwipeActionIconBubble(
    action: NoteSwipeAction,
    size: Dp = 28.dp,
    symbolSize: Dp = 15.dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(MaterialTheme.shapes.medium)
                .background(action.settingsSwipeIconContainerColor()),
        contentAlignment = Alignment.Center,
    ) {
        RememberMaterialRoundedSymbol(
            name = action.materialSymbolName,
            size = symbolSize,
            tint = action.settingsSwipeIconColor(),
            weight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SwipePanelDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)),
    )
}

@Composable
private fun SwipeHintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Canonical action order, used for both jobs so the two modes never disagree:
 *
 *  - the direct-mode dropdowns and reveal-slot pickers list actions in exactly this order;
 *  - reveal slots the user left empty are back-filled from it, in order.
 *
 * The first [SWIPE_REVEAL_TOTAL_SLOT_COUNT] entries are therefore the default reveal layout, and
 * must stay in step with [DEFAULT_SWIPE_START_TO_END_REVEAL_ACTIONS] /
 * [DEFAULT_SWIPE_END_TO_START_REVEAL_ACTIONS] (SwipeRevealDefaultsTest enforces this).
 *
 * Edit is last on purpose: there are 7 actions for 6 slots, so the tail entry is the one that
 * is omitted in the default layout without customizing. Edit is the cheapest to reach another way - open the note.
 */
private val SwipeActionDisplayOrder: List<NoteSwipeAction> =
    listOf(
        NoteSwipeAction.TOGGLE_PIN,
        NoteSwipeAction.TOGGLE_STAR,
        NoteSwipeAction.DUPLICATE,
        NoteSwipeAction.MARK_DONE,
        NoteSwipeAction.ARCHIVE,
        NoteSwipeAction.TRASH,
        NoteSwipeAction.EDIT,
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
private fun NoteSwipeAction.settingsSwipeTileColor(isDropTarget: Boolean = false): Color = settingsSwipeAccent().copy(alpha = if (isDropTarget) 0.2f else 0.14f)

@Composable
private fun NoteSwipeAction.settingsSwipeAccent(): Color = swipeActionAccent()

@Composable
private fun NoteSwipeAction.settingsSwipeIconContainerColor(): Color = settingsSwipeAccent()

@Composable
private fun NoteSwipeAction.settingsSwipeIconColor(): Color = Color.White

@Composable
private fun swipeGestureModeTitle(mode: SwipeGestureMode): String =
    stringResource(
        when (mode) {
            SwipeGestureMode.REVEAL_ACTIONS -> R.string.settings_swipe_reveal_title
            SwipeGestureMode.EXECUTE_ONE -> R.string.settings_swipe_execute_title
        },
    )

@Composable
private fun swipeSettingsActionLabel(action: NoteSwipeAction): String =
    if (action == NoteSwipeAction.MARK_DONE) {
        stringResource(R.string.settings_swipe_action_done_short)
    } else {
        noteSwipeActionLabel(action)
    }

@Composable
internal fun noteSwipeActionLabel(action: NoteSwipeAction): String =
    stringResource(
        when (action) {
            NoteSwipeAction.EDIT -> R.string.swipe_action_open
            NoteSwipeAction.TRASH -> R.string.edit_bottom_bar_trash
            NoteSwipeAction.DUPLICATE -> R.string.swipe_action_duplicate
            NoteSwipeAction.TOGGLE_STAR -> R.string.swipe_action_toggle_star
            NoteSwipeAction.TOGGLE_PIN -> R.string.swipe_action_pin
            NoteSwipeAction.ARCHIVE -> R.string.edit_bottom_bar_archive
            NoteSwipeAction.MARK_DONE -> R.string.swipe_action_mark_done
        },
    )
