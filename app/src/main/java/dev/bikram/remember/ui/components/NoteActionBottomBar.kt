@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.bikram.remember.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec

/**
 * Note/list action state. Exactly one of [archived]/[trashed] is expected to be true; when both
 * are false the note is in the normal "active" shelf.
 */
enum class NoteShelfState { ACTIVE, ARCHIVED, TRASHED }

/**
 * Action buttons for the Edit Note / Edit List screens, rendered as a full-width bottom bar
 * anchored to the navigation inset. The button set swaps based on [shelfState]:
 *
 *   ACTIVE   -> Edit/Done, Star, Archive, Trash
 *   ARCHIVED -> Unarchive, Trash
 *   TRASHED  -> Restore, Delete forever
 *
 * Each action is an icon stacked above its label. [visible] gates the slide-in/slide-out
 * animation. Callers flip it to false when the IME is up (the rich-text toolbar takes over)
 * or when the content has been scrolled away from the top.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoteActionBottomBar(
    shelfState: NoteShelfState,
    existing: Boolean,
    isEditMode: Boolean,
    starred: Boolean,
    completed: Boolean,
    visible: Boolean,
    onToggleEdit: () -> Unit,
    onToggleStar: () -> Unit,
    onToggleCompleted: () -> Unit,
    onArchive: () -> Unit,
    onNotification: () -> Unit,
    onUnarchive: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier,
    showEditAction: Boolean = true,
) {
    val spatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>())
    val fadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val fadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = spatialSpec, initialOffsetY = { it }) + fadeIn(animationSpec = fadeInSpec),
        exit = slideOutVertically(animationSpec = spatialSpec, targetOffsetY = { it }) + fadeOut(animationSpec = fadeOutSpec),
        modifier = modifier,
    ) {
        NoteActionBottomBarContent(
            shelfState = shelfState,
            existing = existing,
            isEditMode = isEditMode,
            starred = starred,
            completed = completed,
            onToggleEdit = onToggleEdit,
            onToggleStar = onToggleStar,
            onToggleCompleted = onToggleCompleted,
            onArchive = onArchive,
            onNotification = onNotification,
            onUnarchive = onUnarchive,
            onTrash = onTrash,
            onRestore = onRestore,
            onDeleteForever = onDeleteForever,
            showEditAction = showEditAction,
        )
    }
}

/**
 * Static content of the action bottom bar (no visibility animation wrapping).
 *
 * Extracted from [NoteActionBottomBar] so callers that want to drive the show/hide
 * transition themselves (e.g. a parent [androidx.compose.animation.AnimatedContent]
 * that swaps this bar with a format toolbar) can mount the content directly without
 * fighting a nested `AnimatedVisibility`. The original [NoteActionBottomBar] keeps
 * its `AnimatedVisibility` wrapper for callers that just want a scroll-hide bar.
 */
@Composable
fun NoteActionBottomBarContent(
    shelfState: NoteShelfState,
    existing: Boolean,
    isEditMode: Boolean,
    starred: Boolean,
    completed: Boolean,
    onToggleEdit: () -> Unit,
    onToggleStar: () -> Unit,
    onToggleCompleted: () -> Unit,
    onArchive: () -> Unit,
    onNotification: () -> Unit,
    onUnarchive: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier,
    showEditAction: Boolean = true,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (shelfState) {
                NoteShelfState.ACTIVE -> {
                    if (showEditAction) {
                        EditActionItem(isEditMode = isEditMode, onClick = onToggleEdit)
                    }
                    StarActionItem(starred = starred, onClick = onToggleStar)
                    if (existing) {
                        ActionItem(
                            icon = "notifications",
                            label = stringResource(R.string.edit_bottom_bar_notification),
                            onClick = onNotification,
                            modifier = Modifier.width(72.dp),
                        )
                        // Mark done / not done. Filled check_circle when completed,
                        // outlined when active, so the toggle state reads at a glance.
                        // Label flips to match: "Mark done" vs "Mark not done".
                        DoneActionItem(completed = completed, onClick = onToggleCompleted)
                        ActionItem(
                            icon = "archive",
                            label = stringResource(R.string.edit_bottom_bar_archive),
                            onClick = onArchive,
                            modifier = Modifier.width(72.dp),
                        )
                        ActionItem(
                            icon = "delete_outline",
                            label = stringResource(R.string.edit_bottom_bar_trash),
                            onClick = onTrash,
                            modifier = Modifier.width(72.dp),
                        )
                    }
                }
                NoteShelfState.ARCHIVED -> {
                    ActionItem(
                        icon = "unarchive",
                        label = stringResource(R.string.edit_bottom_bar_unarchive),
                        onClick = onUnarchive,
                        modifier = Modifier.width(72.dp),
                    )
                    ActionItem(
                        icon = "delete_outline",
                        label = stringResource(R.string.edit_bottom_bar_trash),
                        onClick = onTrash,
                        modifier = Modifier.width(72.dp),
                    )
                }
                NoteShelfState.TRASHED -> {
                    ActionItem(
                        icon = "restore_from_trash",
                        label = stringResource(R.string.edit_bottom_bar_restore),
                        onClick = onRestore,
                        modifier = Modifier.weight(1f),
                    )
                    ActionItem(
                        icon = "archive",
                        label = stringResource(R.string.edit_bottom_bar_archive),
                        onClick = onArchive,
                        modifier = Modifier.weight(1f),
                    )
                    ActionItem(
                        icon = "delete_forever",
                        label = stringResource(R.string.edit_bottom_bar_delete_forever),
                        onClick = onDeleteForever,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Standard action item: icon above label, tappable column with rounded-rect ripple.
 * Width is fixed by default; callers can pass weighted modifiers when a smaller action
 * set should share the whole bar.
 */
@Composable
private fun ActionItem(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    iconScale: Float = 1f,
) {
    Column(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.large)
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RememberMaterialRoundedSymbol(
            name = icon,
            size = 24.dp,
            tint = iconTint,
            weight = FontWeight.Medium,
            modifier = Modifier.scale(iconScale),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * Edit/Done toggle: animates the icon + label between the two states with a shared motion spec,
 * with save haptics only when leaving edit mode.
 */
@Composable
private fun EditActionItem(
    isEditMode: Boolean,
    onClick: () -> Unit,
) {
    val effectsSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val haptic = LocalHapticFeedback.current
    Column(
        modifier =
            Modifier
                .width(72.dp)
                .clip(MaterialTheme.shapes.large)
                .clickable(
                    onClick = {
                        if (isEditMode) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onClick()
                    },
                ).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = isEditMode,
            label = "bottomBarEditIcon",
            transitionSpec = {
                androidx.compose.animation.scaleIn(animationSpec = effectsSpec) togetherWith
                    androidx.compose.animation.scaleOut(animationSpec = effectsSpec)
            },
        ) { editing ->
            RememberMaterialRoundedSymbol(
                name = if (editing) "done" else "edit",
                size = 24.dp,
                tint = MaterialTheme.colorScheme.onSurface,
                weight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(4.dp))
        androidx.compose.animation.AnimatedContent(
            targetState = isEditMode,
            label = "bottomBarEditLabel",
            transitionSpec = {
                androidx.compose.animation.fadeIn(animationSpec = effectsSpec) togetherWith
                    androidx.compose.animation.fadeOut(animationSpec = effectsSpec)
            },
        ) { editing ->
            Text(
                text =
                    stringResource(
                        if (editing) R.string.edit_bottom_bar_done else R.string.edit_bottom_bar_edit,
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/**
 * Mark done / not done. Toggle pulses on activate (scale bump + primary tint), and
 * the FILL=1 vs FILL=0 variant of `check_circle` flips with completion state so the
 * affordance reads visually as well as via label. Recurrence is handled at the call
 * site - the VM routes the click through repository.markCompleted, which in turn
 * advances rather than completes a recurring note.
 */
@Composable
private fun DoneActionItem(
    completed: Boolean,
    onClick: () -> Unit,
) {
    val effectsSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val colorEffectsSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Color>())
    val haptic = LocalHapticFeedback.current
    var pulsing by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pulsing) 1.35f else 1f,
        animationSpec = effectsSpec,
        finishedListener = { if (pulsing) pulsing = false },
        label = "bottomBarDoneScale",
    )
    val activeTint = MaterialTheme.colorScheme.primary
    val inactiveTint = MaterialTheme.colorScheme.onSurface
    val tint by animateColorAsState(
        targetValue = if (pulsing || completed) activeTint else inactiveTint,
        animationSpec = colorEffectsSpec,
        label = "bottomBarDoneColor",
    )
    Column(
        modifier =
            Modifier
                .width(72.dp)
                .clip(MaterialTheme.shapes.large)
                .clickable(
                    onClick = {
                        if (!completed) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pulsing = true
                        }
                        onClick()
                    },
                ).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RememberMaterialRoundedSymbol(
            name = "check_circle",
            filled = completed,
            size = 24.dp,
            tint = tint,
            weight = FontWeight.Medium,
            modifier = Modifier.scale(scale),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text =
                stringResource(
                    if (completed) {
                        R.string.edit_bottom_bar_mark_not_done
                    } else {
                        R.string.edit_bottom_bar_mark_done
                    },
                ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * Star toggle: pulses on activate (scale bump + yellow tint). When starred, stays yellow.
 */
@Composable
private fun StarActionItem(
    starred: Boolean,
    onClick: () -> Unit,
) {
    val effectsSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val colorEffectsSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Color>())
    val haptic = LocalHapticFeedback.current
    var starPulsing by remember { mutableStateOf(false) }
    val starScale by animateFloatAsState(
        targetValue = if (starPulsing) 1.35f else 1f,
        animationSpec = effectsSpec,
        finishedListener = { if (starPulsing) starPulsing = false },
        label = "bottomBarStarScale",
    )
    val starColor by animateColorAsState(
        targetValue = if (starPulsing || starred) Color(0xFFF9A825) else MaterialTheme.colorScheme.onSurface,
        animationSpec = colorEffectsSpec,
        label = "bottomBarStarColor",
    )
    Column(
        modifier =
            Modifier
                .width(72.dp)
                .clip(MaterialTheme.shapes.large)
                .clickable(
                    onClick = {
                        if (!starred) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            starPulsing = true
                        }
                        onClick()
                    },
                ).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Shape + color together, so the starred state reads clearly even for users who
        // can't distinguish the yellow tint (color-blindness, high-contrast themes, grayscale).
        // We swap the underlying FILL-instanced font family via `filled = starred` rather
        // than switching the ligature name, because in our instanced subset font both
        // "starred" and "star_border" resolve to the same FILL=1 glyph.
        RememberMaterialRoundedSymbol(
            name = "star",
            filled = starred,
            size = 24.dp,
            tint = starColor,
            weight = FontWeight.Medium,
            opticalCenterYOffset = 1.5.dp,
            modifier = Modifier.scale(starScale),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.edit_bottom_bar_star),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
