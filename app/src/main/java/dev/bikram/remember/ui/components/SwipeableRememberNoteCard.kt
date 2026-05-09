package dev.bikram.remember.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.data.InteractionState
import dev.bikram.remember.data.NoteSwipeAction
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.SwipeGestureMode
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import dev.bikram.remember.ui.theme.semanticSwipeBackground
import dev.bikram.remember.ui.theme.semanticSwipeIconTint

@Composable
fun SwipeableRememberNoteCard(
    note: NoteWithItems,
    interaction: InteractionState,
    onOpenNote: (NoteWithItems) -> Unit,
    onSwipeAction: (NoteWithItems, NoteSwipeAction) -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    swipeEnabled: Boolean = true,
    activeRevealKey: Any? = null,
    onRevealStarted: ((Long) -> Unit)? = null,
    onRevealClosed: ((Long) -> Unit)? = null,
) {
    SwipeableRememberNoteCard(
        note = note,
        model = remember(note) { note.toNoteCardUiModel() },
        interaction = interaction,
        onOpenNote = onOpenNote,
        onSwipeAction = onSwipeAction,
        modifier = modifier,
        selected = selected,
        onLongClick = onLongClick,
        swipeEnabled = swipeEnabled,
        activeRevealKey = activeRevealKey,
        onRevealStarted = onRevealStarted,
        onRevealClosed = onRevealClosed,
    )
}

@Composable
fun SwipeableRememberNoteCard(
    note: NoteWithItems,
    model: NoteCardUiModel,
    interaction: InteractionState,
    onOpenNote: (NoteWithItems) -> Unit,
    onSwipeAction: (NoteWithItems, NoteSwipeAction) -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    swipeEnabled: Boolean = true,
    activeRevealKey: Any? = null,
    onRevealStarted: ((Long) -> Unit)? = null,
    onRevealClosed: ((Long) -> Unit)? = null,
) {
    val swipeStart = interaction.swipeStartToEnd
    val swipeEnd = interaction.swipeEndToStart
    val noteCompleted = model.completed
    val noteStarred = model.starred
    val revealKey = note.note.id
    val noteCardShape = MaterialTheme.shapes.medium
    // Always route through one of the two swipe wrappers below so the inner
    // [NoteCard] stays in the same composable slot regardless of selection mode.
    // Previously we had an `if (!swipeEnabled) return NoteCard(...)` shortcut that
    // moved NoteCard into a different parent slot, causing remount on the
    // selection-mode boundary; that destroyed the badge bloom Animatable mid-
    // animation, leading to visible "instant disappear" on the last deselect.
    if (interaction.swipeGestureMode == SwipeGestureMode.REVEAL_ACTIONS) {
        val startTiles =
            if (swipeEnabled) {
                interaction.swipeStartToEndRevealActions
                    .filterNotNull()
                    .map { action ->
                        action.revealTile(
                            noteCompleted = noteCompleted,
                            noteStarred = noteStarred,
                            onClick = { onSwipeAction(note, action) },
                        )
                    }
            } else {
                emptyList()
            }
        val endTiles =
            if (swipeEnabled) {
                interaction.swipeEndToStartRevealActions
                    .filterNotNull()
                    .map { action ->
                        action.revealTile(
                            noteCompleted = noteCompleted,
                            noteStarred = noteStarred,
                            onClick = { onSwipeAction(note, action) },
                        )
                    }
            } else {
                emptyList()
            }
        MultiActionSwipeRevealCard(
            modifier = modifier.fillMaxWidth(),
            startActions = startTiles,
            endActions = endTiles,
            cardShape = noteCardShape,
            hapticEnabled = interaction.hapticFeedbackEnabled,
            revealKey = revealKey,
            activeRevealKey = activeRevealKey,
            onRevealStarted = { onRevealStarted?.invoke(revealKey) },
            onRevealClosed = { onRevealClosed?.invoke(revealKey) },
        ) {
            NoteCard(
                model = model,
                onClick = { onOpenNote(note) },
                selected = selected,
                onLongClick = onLongClick,
            )
        }
        return
    }
    DeliberateSwipeRevealCard(
        modifier = modifier.fillMaxWidth(),
        commitThresholdFraction = 0.35f,
        cardShape = noteCardShape,
        hapticEnabled = interaction.hapticFeedbackEnabled,
        allowSwipeStartToEnd = swipeEnabled,
        allowSwipeEndToStart = swipeEnabled,
        onSwipeStartToEnd = { onSwipeAction(note, swipeStart) },
        onSwipeEndToStart = { onSwipeAction(note, swipeEnd) },
        backgroundContent = { fromStart, revealProgress ->
            val action = if (fromStart) swipeStart else swipeEnd
            val backgroundColor by animateColorAsState(
                targetValue = action.semanticSwipeBackground(),
                animationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec()),
                label = "swipeBg",
            )
            val tint = action.semanticSwipeIconTint()
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .background(backgroundColor)
                        .padding(horizontal = 16.dp),
                contentAlignment = if (fromStart) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                // For MARK_DONE the FILL axis flips with completion state so the
                // glyph itself reads done vs not-done at a glance:
                //   active note  -> outlined check_circle (FILL=0) "Mark done"
                //   completed    -> filled check_circle (FILL=1)   "Mark not done"
                // All other actions ignore the flag.
                val iconFilled =
                    action == NoteSwipeAction.MARK_DONE &&
                        noteCompleted ||
                        action == NoteSwipeAction.TOGGLE_STAR &&
                        noteStarred
                val contentScale = 0.88f + 0.12f * revealProgress
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier.graphicsLayer {
                            alpha = revealProgress
                            scaleX = contentScale
                            scaleY = contentScale
                        },
                ) {
                    if (fromStart) {
                        RememberMaterialRoundedSymbol(
                            name = action.materialSymbolName,
                            filled = iconFilled,
                            size = 20.dp,
                            tint = tint,
                            weight = FontWeight.Medium,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = action.labelString(noteCompleted, noteStarred),
                            style = MaterialTheme.typography.labelMedium,
                            color = tint,
                        )
                    } else {
                        Text(
                            text = action.labelString(noteCompleted, noteStarred),
                            style = MaterialTheme.typography.labelMedium,
                            color = tint,
                        )
                        Spacer(Modifier.width(6.dp))
                        RememberMaterialRoundedSymbol(
                            name = action.materialSymbolName,
                            filled = iconFilled,
                            size = 20.dp,
                            tint = tint,
                            weight = FontWeight.Medium,
                        )
                    }
                }
            }
        },
    ) {
        NoteCard(
            model = model,
            onClick = { onOpenNote(note) },
            selected = selected,
            onLongClick = onLongClick,
        )
    }
}

/**
 * The label shown on the swipe-reveal background. For [NoteSwipeAction.MARK_DONE]
 * the label flips to "Mark not done" when the note is already completed, so the
 * single configured swipe action visually advertises both directions of the toggle.
 */
@Composable
private fun NoteSwipeAction.labelString(
    noteCompleted: Boolean = false,
    noteStarred: Boolean = false,
): String =
    stringResource(
        when (this) {
            NoteSwipeAction.EDIT -> R.string.swipe_action_open
            NoteSwipeAction.TRASH -> R.string.edit_bottom_bar_trash
            NoteSwipeAction.DUPLICATE -> R.string.swipe_action_duplicate
            NoteSwipeAction.TOGGLE_STAR -> {
                if (noteStarred) R.string.swipe_action_unstar else R.string.swipe_action_toggle_star
            }
            NoteSwipeAction.ARCHIVE -> R.string.edit_bottom_bar_archive
            NoteSwipeAction.MARK_DONE ->
                if (noteCompleted) {
                    R.string.swipe_action_mark_not_done
                } else {
                    R.string.swipe_action_mark_done
                }
        },
    )

@Composable
private fun NoteSwipeAction.revealTile(
    noteCompleted: Boolean,
    noteStarred: Boolean,
    onClick: () -> Unit,
): SwipeRevealTile {
    val iconFilled =
        this == NoteSwipeAction.MARK_DONE &&
            noteCompleted ||
            this == NoteSwipeAction.TOGGLE_STAR &&
            noteStarred
    val labelRes =
        when (this) {
            NoteSwipeAction.EDIT -> R.string.swipe_action_open
            NoteSwipeAction.TRASH -> R.string.edit_bottom_bar_trash
            NoteSwipeAction.DUPLICATE -> R.string.swipe_action_duplicate
            NoteSwipeAction.TOGGLE_STAR -> {
                if (noteStarred) R.string.swipe_action_unstar else R.string.swipe_action_toggle_star
            }
            NoteSwipeAction.ARCHIVE -> R.string.edit_bottom_bar_archive
            NoteSwipeAction.MARK_DONE -> {
                if (noteCompleted) {
                    R.string.swipe_action_mark_not_done
                } else {
                    R.string.swipe_action_mark_done
                }
            }
        }
    return SwipeRevealTile(
        key = name,
        labelRes = labelRes,
        symbolName = materialSymbolName,
        backgroundColor = semanticSwipeBackground(),
        contentColor = semanticSwipeIconTint(),
        filled = iconFilled,
        onClick = onClick,
    )
}
