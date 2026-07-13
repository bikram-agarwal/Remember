package dev.bikram.remember.ui.edit

import android.annotation.SuppressLint
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.domain.checklist.EditableItem
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.feedback.tapSoundClickable

/**
 * A synthetic, non-persisted header that stands in for the real parent row when a child has been
 * separated from its parent by a toggle. Lives in the UI layer only -- [realParentLocalId] points
 * at the actual parent's [EditableItem.localId] so tapping a child's toggle does not also mutate
 * the ghost. [parentChecked] is the persisted state of the real parent, used to render the ghost
 * with the correct strikethrough/checkbox visual.
 */
internal data class GhostParentHeader(
    val realParentLocalId: Long,
    val text: String,
    val parentChecked: Boolean,
)

/**
 * Fully-computed display state for the completed section. A plain list would lose ghost-parent
 * grouping, so we emit a flat list of entries the Composable can render 1:1.
 */
internal sealed interface CompletedEntry {
    val sortKey: Double

    data class Ghost(
        val header: GhostParentHeader,
        override val sortKey: Double,
    ) : CompletedEntry

    data class Row(
        val item: EditableItem,
        override val sortKey: Double,
    ) : CompletedEntry
}

/**
 * Mirror of [CompletedEntry] for the active section. Used when an unchecked child's real parent
 * is still in the completed section -- we synthesise a ghost header above the orphan child so the
 * context stays visible while the parent remains struck through in the other half of the screen.
 */
internal sealed interface ActiveEntry {
    val sortKey: Double

    data class Ghost(
        val header: GhostParentHeader,
        override val sortKey: Double,
    ) : ActiveEntry

    data class Row(
        val item: EditableItem,
        override val sortKey: Double,
    ) : ActiveEntry
}

@Composable
@SuppressLint("ModifierParameter")
internal fun ChecklistRow(
    item: EditableItem,
    isEditMode: Boolean,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    detailsFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    initialTitleSelection: Int? = null,
    initialDetailsSelection: Int? = null,
    onTitleFocusOffsetConsumed: () -> Unit = {},
    onDetailsFocusOffsetConsumed: () -> Unit = {},
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    /**
     * Whether to render the leading drag-handle gutter. Defaults to true so the active section
     * keeps reorder affordance; the completed section sets this to false because checked items
     * are not reorderable (matches Google Keep, which also freezes the checked-items order).
     */
    showDragHandle: Boolean = true,
    onTextChange: (String) -> Unit,
    onDetailsChange: (String) -> Unit = {},
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onNext: () -> Unit = {},
    onTextTap: ((Int) -> Unit)? = null,
    onDetailsTap: ((Int) -> Unit)? = null,
    /**
     * Horizontal drag callback. `+1` means the user dragged right past the indent threshold
     * (request to nest under the previous top-level sibling). `-1` means the user dragged left
     * (request to promote back to depth 0). `null` disables the gesture entirely -- used in the
     * completed section where hierarchy is frozen.
     */
    onIndentChange: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scale by androidx.compose.animation.core
        .animateFloatAsState(if (isDragging) 1.02f else 1f)
    val alpha by androidx.compose.animation.core
        .animateFloatAsState(if (isDragging) 0.8f else 1f)
    // Depth indent. 28.dp per level matches the thumb/toggle gutter so child toggles line up with
    // the parent's text, not their own toggle.
    val depthIndent = (item.depth.coerceIn(0, 1) * 28).dp
    // Animate the indent so reparenting slides visibly instead of snapping.
    val animatedIndent by androidx.compose.animation.core
        .animateDpAsState(depthIndent, label = "checklistDepthIndent")

    val haptic = LocalHapticFeedback.current
    var detailsExpanded by rememberSaveable(item.localId) { mutableStateOf(false) }

    var titleFieldValue by remember(item.localId) {
        mutableStateOf(TextFieldValue(text = item.text, selection = TextRange(item.text.length)))
    }
    var detailsFieldValue by remember(item.localId) {
        mutableStateOf(TextFieldValue(text = item.details, selection = TextRange(item.details.length)))
    }

    LaunchedEffect(item.text) {
        if (item.text != titleFieldValue.text) {
            val selection = titleFieldValue.selection
            titleFieldValue =
                TextFieldValue(
                    text = item.text,
                    selection =
                        TextRange(
                            start = selection.start.coerceIn(0, item.text.length),
                            end = selection.end.coerceIn(0, item.text.length),
                        ),
                )
        }
    }

    LaunchedEffect(item.details) {
        if (item.details != detailsFieldValue.text) {
            val selection = detailsFieldValue.selection
            detailsFieldValue =
                TextFieldValue(
                    text = item.details,
                    selection =
                        TextRange(
                            start = selection.start.coerceIn(0, item.details.length),
                            end = selection.end.coerceIn(0, item.details.length),
                        ),
                )
        }
    }

    LaunchedEffect(isEditMode, initialTitleSelection) {
        if (isEditMode && initialTitleSelection != null) {
            val clamped = initialTitleSelection.coerceIn(0, item.text.length)
            titleFieldValue = titleFieldValue.copy(selection = TextRange(clamped))
            onTitleFocusOffsetConsumed()
        }
    }

    LaunchedEffect(isEditMode, initialDetailsSelection) {
        if (isEditMode && initialDetailsSelection != null) {
            val clamped = initialDetailsSelection.coerceIn(0, item.details.length)
            detailsFieldValue = detailsFieldValue.copy(selection = TextRange(clamped))
            onDetailsFocusOffsetConsumed()
        }
    }

    val detailLineCount = item.details.lineSequence().count { it.isNotBlank() }
    val detailsCanExpand = detailLineCount > 1
    val showDetailsAffordance = isEditMode || detailsCanExpand
    val detailPreview =
        item.details
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()
    val detailsToggleLabel = stringResource(R.string.edit_list_item_details_placeholder)

    LaunchedEffect(isDragging) {
        if (isDragging) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // RTL awareness for the gesture surfaces below.
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // Drag-to-indent gesture: accumulates horizontal movement during a single drag and fires the
    // callback once when a threshold (48.dp ~= fingertip width) is crossed. We only fire once per
    // drag to avoid flickering between depth 0 and 1.
    //
    // In RTL layouts the visual indent direction is mirrored: swiping LEFT should nest (the child
    // sits visually to the left of its parent in RTL), and swiping RIGHT should promote. We keep
    // the accumulator in raw pointer deltas and flip the sign when comparing to the threshold so
    // the +1 / -1 contract with the caller stays identical regardless of layout direction.
    val indentThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
    val indentModifier =
        if (isEditMode && onIndentChange != null) {
            Modifier.pointerInput(item.localId, item.depth, isRtl) {
                var accumulated = 0f
                var fired = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        accumulated = 0f
                        fired = false
                    },
                    onDragEnd = {
                        accumulated = 0f
                        fired = false
                    },
                    onDragCancel = {
                        accumulated = 0f
                        fired = false
                    },
                    onHorizontalDrag = { _, delta ->
                        if (fired) return@detectHorizontalDragGestures
                        accumulated += delta
                        // Effective "toward-nest" drag distance. In LTR that means rightward (positive
                        // delta); in RTL that means leftward (negative delta) -- flip the sign so the
                        // same comparison works in both layouts.
                        val effective = if (isRtl) -accumulated else accumulated
                        when {
                            effective > indentThresholdPx -> {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onIndentChange(+1)
                                fired = true
                            }
                            effective < -indentThresholdPx -> {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onIndentChange(-1)
                                fired = true
                            }
                        }
                    },
                )
            }
        } else {
            Modifier
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = animatedIndent)
                .then(indentModifier)
                .scale(scale)
                .alpha(alpha),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isEditMode && showDragHandle) {
                // The drag handle is a vertical-only gesture surface: only `dragHandleModifier`
                // (the library's `draggableHandle()`) is applied here. We deliberately do NOT also
                // attach the horizontal indentModifier to this Box because detectHorizontalDragGestures
                // and draggableHandle() live on independent pointer-input blocks and both observe
                // the same pointer stream. During a long vertical reorder drag the user's finger
                // tends to drift horizontally by small amounts; that drift can accumulate past the
                // indent threshold and silently reparent the item, which then breaks the parent
                // cascade in `toggleChecked` (a child's parentLocalId no longer matches the tapped
                // parent, or the parent itself ends up at depth 1 and the `target.depth == 0`
                // cascade branch is skipped). The row body retains the horizontal indent gesture,
                // so users can still swipe the text area left or right to change depth.
                val cdReorder = stringResource(R.string.cd_reorder_drag_handle)
                Box(
                    modifier =
                        dragHandleModifier
                            .padding(start = 4.dp, end = 4.dp)
                            .size(32.dp)
                            .semantics { contentDescription = cdReorder },
                    contentAlignment = Alignment.Center,
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "drag_indicator",
                        size = 20.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        weight = FontWeight.Medium,
                    )
                }
            }
            RememberIconButton(onClick = onToggle) {
                RememberMaterialRoundedSymbol(
                    name = if (item.checked) "check_box" else "check_box_outline_blank",
                    size = 24.dp,
                    tint = if (item.checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    weight = FontWeight.Medium,
                )
            }
            if (isEditMode) {
                BasicTextField(
                    value = titleFieldValue,
                    onValueChange = { newValue ->
                        val oldText = titleFieldValue.text
                        titleFieldValue = newValue
                        if (newValue.text != oldText) {
                            onTextChange(newValue.text)
                        }
                    },
                    textStyle =
                        MaterialTheme.typography.bodyLarge.copy(
                            color =
                                if (item.checked) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                        ),
                    keyboardOptions =
                        androidx.compose.foundation.text.KeyboardOptions(
                            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                        ),
                    keyboardActions =
                        androidx.compose.foundation.text.KeyboardActions(
                            onNext = { onNext() },
                        ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier =
                        Modifier
                            .weight(1f)
                            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                    decorationBox = { inner ->
                        if (item.text.isEmpty()) {
                            Text(
                                stringResource(R.string.edit_list_new_item_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            )
                        }
                        inner()
                    },
                )
                if (showDetailsAffordance) {
                    RememberIconButton(
                        onClick = { detailsExpanded = !detailsExpanded },
                        tooltipLabel = detailsToggleLabel,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = if (detailsExpanded) "expand_less" else "notes",
                            size = 22.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            weight = FontWeight.Medium,
                        )
                    }
                }
                RememberIconButton(onClick = onRemove) {
                    RememberMaterialRoundedSymbol(
                        name = "close",
                        size = 24.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        weight = FontWeight.Medium,
                    )
                }
            } else {
                var titleLayout by remember(item.text) { mutableStateOf<TextLayoutResult?>(null) }
                Text(
                    text = item.text.ifEmpty { stringResource(R.string.edit_list_new_item_placeholder) },
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            color =
                                if (item.checked) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                } else if (item.text.isEmpty()) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                        ),
                    onTextLayout = { titleLayout = it },
                    modifier =
                        if (onTextTap != null) {
                            Modifier
                                .weight(1f)
                                .pointerInput(onTextTap, titleLayout) {
                                    detectTapGestures { tapOffset ->
                                        val offset =
                                            if (item.text.isEmpty()) {
                                                0
                                            } else {
                                                titleLayout?.getOffsetForPosition(tapOffset) ?: item.text.length
                                            }
                                        onTextTap(offset)
                                    }
                                }
                        } else {
                            Modifier.weight(1f)
                        },
                )
                if (showDetailsAffordance) {
                    RememberIconButton(
                        onClick = { detailsExpanded = !detailsExpanded },
                        tooltipLabel = detailsToggleLabel,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = if (detailsExpanded) "expand_less" else "expand_more",
                            size = 22.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            weight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        if (detailsExpanded || (!isEditMode && detailPreview.isNotBlank())) {
            val detailsStartPadding = (if (isEditMode && showDragHandle) 40.dp else 0.dp) + 40.dp
            if (isEditMode) {
                BasicTextField(
                    value = detailsFieldValue,
                    onValueChange = { newValue ->
                        val oldText = detailsFieldValue.text
                        detailsFieldValue = newValue
                        if (newValue.text != oldText) {
                            onDetailsChange(newValue.text)
                        }
                    },
                    textStyle =
                        MaterialTheme.typography.bodyMedium.copy(
                            color =
                                if (item.checked) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        ),
                    keyboardOptions =
                        androidx.compose.foundation.text.KeyboardOptions(
                            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Default,
                        ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = detailsStartPadding, end = 48.dp, bottom = 8.dp)
                            .then(if (detailsFocusRequester != null) Modifier.focusRequester(detailsFocusRequester) else Modifier),
                    decorationBox = { inner ->
                        if (item.details.isEmpty()) {
                            Text(
                                stringResource(R.string.edit_list_item_details_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            )
                        }
                        inner()
                    },
                )
            } else {
                var detailsLayout by remember(item.details) { mutableStateOf<TextLayoutResult?>(null) }
                val detailsModifier =
                    if (onDetailsTap != null) {
                        Modifier.pointerInput(onDetailsTap, detailsLayout) {
                            detectTapGestures { tapOffset ->
                                val offset =
                                    if (item.details.isEmpty()) {
                                        0
                                    } else {
                                        detailsLayout?.getOffsetForPosition(tapOffset) ?: item.details.length
                                    }
                                detailsExpanded = true
                                onDetailsTap(offset)
                            }
                        }
                    } else if (detailsCanExpand) {
                        Modifier.tapSoundClickable { detailsExpanded = !detailsExpanded }
                    } else {
                        Modifier
                    }
                Text(
                    text = if (detailsExpanded) item.details else detailPreview,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            color =
                                if (item.checked) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        ),
                    maxLines = if (detailsExpanded) Int.MAX_VALUE else 1,
                    overflow = if (detailsExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                    onTextLayout = { detailsLayout = it },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = detailsStartPadding, end = 48.dp, bottom = 8.dp)
                            .then(detailsModifier),
                )
            }
        }
    }
}

/**
 * Read-only header that stands in for the real parent when the parent lives in the opposite
 * section from its child. Styled to match a regular checklist row (checkbox gutter + text) but
 * at reduced opacity so it reads as context rather than an interactive row. The ghost uses a
 * disabled-looking checkbox that never toggles and a strikethrough when the ghost represents a
 * checked parent (appearing in the active section).
 */
@Composable
internal fun GhostParentHeaderRow(
    header: GhostParentHeader,
    isParentChecked: Boolean,
    /**
     * True when the section rendering this ghost draws a 40dp drag-handle gutter before each row
     * (currently: only the active section in edit mode). The ghost mirrors that gutter so its
     * checkbox lines up with real rows below it. Completed rows never show a drag handle (we
     * match Google Keep: checked items cannot be reordered), so completed ghosts set this to
     * false and sit flush at depth 0.
     */
    showDragHandleGutter: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .alpha(0.45f),
    ) {
        if (showDragHandleGutter) {
            Spacer(Modifier.width(40.dp))
        }
        // Static checkbox icon (no click behaviour). Using the Box + icon avoids the ripple and
        // minSize guarantees of RememberIconButton so the ghost can't steal taps meant for a
        // child below it.
        Box(
            modifier =
                Modifier
                    .size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            RememberMaterialRoundedSymbol(
                name = if (isParentChecked) "check_box" else "check_box_outline_blank",
                size = 24.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                weight = FontWeight.Medium,
            )
        }
        Text(
            text = header.text.ifEmpty { stringResource(R.string.edit_list_new_item_placeholder) },
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (isParentChecked) TextDecoration.LineThrough else TextDecoration.None,
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Walks the sorted list of checked items and returns a flat display list that interleaves
 * [CompletedEntry.Ghost] headers in front of any checked child whose real parent is still in the
 * active section. Ghost headers collapse consecutive children of the same active parent into a
 * single header (we don't repeat the ghost on every child). The synthesised ghost carries
 * `parentChecked = false` because the real parent lives in the active half.
 */
internal fun buildCompletedEntries(
    completedItems: List<EditableItem>,
    activeParents: Map<Long, EditableItem>,
    checkedParents: Map<Long, EditableItem>,
): List<CompletedEntry> {
    val out = mutableListOf<CompletedEntry>()
    var lastGhostParentId: Long? = null
    for (item in completedItems) {
        val parentId = item.parentLocalId
        val needsGhost =
            parentId != null &&
                parentId !in checkedParents &&
                parentId in activeParents
        if (needsGhost && parentId != lastGhostParentId) {
            val parent = activeParents[parentId]
            if (parent != null) {
                out +=
                    CompletedEntry.Ghost(
                        header =
                            GhostParentHeader(
                                realParentLocalId = parent.localId,
                                text = parent.text,
                                parentChecked = false,
                            ),
                        sortKey = item.sortOrder,
                    )
                lastGhostParentId = parentId
            }
        } else if (!needsGhost) {
            lastGhostParentId = null
        }
        out += CompletedEntry.Row(item = item, sortKey = item.sortOrder)
    }
    return out
}

/**
 * Mirror of [buildCompletedEntries] for the active half. When a child is unchecked but its real
 * parent is still in the completed section, we synthesise a ghost parent row above that child so
 * the visual grouping survives. The ghost uses `parentChecked = true` so the header renders with
 * the strikethrough/checked visual that matches the parent's persisted state.
 */
internal fun buildActiveEntries(
    activeItems: List<EditableItem>,
    activeParents: Map<Long, EditableItem>,
    checkedParents: Map<Long, EditableItem>,
): List<ActiveEntry> {
    val out = mutableListOf<ActiveEntry>()
    var lastGhostParentId: Long? = null
    for (item in activeItems) {
        val parentId = item.parentLocalId
        val needsGhost =
            parentId != null &&
                parentId !in activeParents &&
                parentId in checkedParents
        if (needsGhost && parentId != lastGhostParentId) {
            val parent = checkedParents[parentId]
            if (parent != null) {
                out +=
                    ActiveEntry.Ghost(
                        header =
                            GhostParentHeader(
                                realParentLocalId = parent.localId,
                                text = parent.text,
                                parentChecked = true,
                            ),
                        sortKey = item.sortOrder,
                    )
                lastGhostParentId = parentId
            }
        } else if (!needsGhost) {
            lastGhostParentId = null
        }
        out += ActiveEntry.Row(item = item, sortKey = item.sortOrder)
    }
    return out
}
