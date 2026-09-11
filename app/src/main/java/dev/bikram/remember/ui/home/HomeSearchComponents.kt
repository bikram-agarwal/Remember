package dev.bikram.remember.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bikram.remember.R
import dev.bikram.remember.data.InteractionState
import dev.bikram.remember.data.NoteLayoutMode
import dev.bikram.remember.data.NoteSwipeAction
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.ui.common.RememberInlineSearchField
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.NoteCardUiModel
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton
import dev.bikram.remember.ui.components.SwipeableRememberNoteCard
import dev.bikram.remember.ui.components.rememberResponsiveActionButtonSize
import dev.bikram.remember.ui.feedback.appClickable
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec

/**
 * Top-bar title that doubles as the search entry point. Renders a single Row of:
 *
 *   [animated content area (weight 1)] [layout icon button] [expand/collapse-all icon button] [search/close icon button]
 *
 * The animated content area crossfades between empty space and [RememberInlineSearchField].
 * Only the layout/expand-collapse [AnimatedVisibility] below drives horizontal width
 * changes; giving this slot its own expand/shrink as well used to fight that animation
 * and made the search bar appear to slide in from inconsistent directions.
 *
 * Intended call site: the LargeTopAppBar's `title` slot. The `actions` slot is left
 * for selection-mode chrome only.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchableTopBarTitle(
    searchOpen: Boolean,
    searchFocusRequestKey: Int,
    query: String,
    noteLayoutMode: NoteLayoutMode,
    showLayoutToggle: Boolean,
    layoutToggleEnabled: Boolean,
    showExpandCollapseAllToggle: Boolean,
    allSectionsCollapsed: Boolean,
    onQueryChange: (String) -> Unit,
    onToggleLayout: () -> Unit,
    onToggleExpandCollapseAll: () -> Unit,
    onToggleSearch: () -> Unit,
) {
    val spatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>())
    val fadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val fadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    val scaleIconSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    val layoutMorphSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = searchOpen,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                (fadeIn(fadeInSpec) togetherWith fadeOut(fadeOutSpec))
                    .using(SizeTransform(clip = false))
            },
            label = "topBarTitleSearchExpand",
        ) { open ->
            if (open) {
                RememberInlineSearchField(
                    focusRequestKey = searchFocusRequestKey,
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholderText = stringResource(R.string.home_search_placeholder),
                )
            } else {
                // Empty title placeholder to remove the big title
            }
        }
        // Layout and expand/collapse-all buttons hide while searching so the weight-1
        // slot above grows into the space they free up. clip = false (matching the
        // search field's own SizeTransform above) so the buttons fade/scale away instead
        // of getting cropped by a hard-edged wipe as the row width animates.
        AnimatedVisibility(
            visible = !searchOpen,
            enter = fadeIn(fadeInSpec) + expandHorizontally(spatialSpec, clip = false),
            exit = fadeOut(fadeOutSpec) + shrinkHorizontally(spatialSpec, clip = false),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Breathing room so the open search pill doesn't touch the action buttons.
                Spacer(Modifier.width(8.dp))
                if (showLayoutToggle) {
                    val switchToMosaic = noteLayoutMode == NoteLayoutMode.LIST
                    val layoutToggleDescription =
                        stringResource(
                            if (switchToMosaic) {
                                R.string.home_layout_switch_to_mosaic_cd
                            } else {
                                R.string.home_layout_switch_to_list_cd
                            },
                        )
                    RememberFilledTonalIconButton(
                        onClick = onToggleLayout,
                        modifier = Modifier.size(rememberResponsiveActionButtonSize()),
                        enabled = layoutToggleEnabled,
                        tooltipLabel = layoutToggleDescription,
                    ) {
                        val layoutMorphProgress by animateFloatAsState(
                            targetValue = if (noteLayoutMode == NoteLayoutMode.MOSAIC) 1f else 0f,
                            animationSpec = layoutMorphSpec,
                            label = "layoutIconMorphProgress",
                        )
                        Box(
                            modifier =
                                Modifier
                                    .size(rememberResponsiveActionButtonSize())
                                    .clearAndSetSemantics {
                                        contentDescription = layoutToggleDescription
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            alpha = 1f - layoutMorphProgress
                                            scaleX = 1f - (0.08f * layoutMorphProgress)
                                            scaleY = 1f - (0.08f * layoutMorphProgress)
                                            rotationZ = 90f * layoutMorphProgress
                                        },
                                contentAlignment = Alignment.Center,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "grid_view",
                                    weight = FontWeight.Medium,
                                )
                            }
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            alpha = layoutMorphProgress
                                            scaleX = 0.92f + (0.08f * layoutMorphProgress)
                                            scaleY = 0.92f + (0.08f * layoutMorphProgress)
                                            rotationZ = -90f + (90f * layoutMorphProgress)
                                        },
                                contentAlignment = Alignment.Center,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "view_agenda",
                                    weight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
                if (showExpandCollapseAllToggle) {
                    Spacer(Modifier.width(8.dp))
                    val expandCollapseAllLabel =
                        stringResource(
                            if (allSectionsCollapsed) {
                                R.string.home_expand_all_sections_cd
                            } else {
                                R.string.home_collapse_all_sections_cd
                            },
                        )
                    RememberFilledTonalIconButton(
                        onClick = onToggleExpandCollapseAll,
                        modifier =
                            Modifier
                                .size(rememberResponsiveActionButtonSize())
                                .semantics { contentDescription = expandCollapseAllLabel },
                        tooltipLabel = expandCollapseAllLabel,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = if (allSectionsCollapsed) "unfold_more" else "unfold_less",
                            weight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
        // Unconditional gap between the field and the search toggle button. Kept outside
        // the AnimatedVisibility above so it survives when that content collapses to zero
        // width while searching - otherwise the search pill would butt up against the
        // search/close button.
        Spacer(Modifier.width(8.dp))
        // Toggle button stays in place at the End. The icon morphs between search and
        // close, scoped inside its own AnimatedContent so the rest of the row's
        // expansion animation isn't gated on the icon swap.
        val cdCloseSearch = stringResource(R.string.cd_close_search)
        val cdSearch = stringResource(R.string.cd_search)
        RememberFilledTonalIconButton(
            onClick = onToggleSearch,
            modifier = Modifier.size(rememberResponsiveActionButtonSize()),
        ) {
            AnimatedContent(
                targetState = searchOpen,
                transitionSpec = {
                    (scaleIn(scaleIconSpec) + fadeIn(fadeInSpec)) togetherWith
                        (scaleOut(scaleIconSpec) + fadeOut(fadeOutSpec))
                },
                label = "searchIconSwap",
            ) { open ->
                RememberMaterialRoundedSymbol(
                    name = if (open) "close" else "search",
                    weight = FontWeight.Medium,
                    modifier =
                        Modifier.semantics {
                            contentDescription = if (open) cdCloseSearch else cdSearch
                        },
                )
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}

internal enum class SectionBadgeStyle { ARCHIVE, TRASH }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchSectionPillDivider(
    label: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    muted: Boolean,
    modifier: Modifier = Modifier,
) {
    val spatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<Float>())
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spatialSpec,
        label = "sectionChevron",
    )
    val contentDescriptionExpand = stringResource(R.string.section_expand_cd, label)
    val contentDescriptionCollapse = stringResource(R.string.section_collapse_cd, label)
    val pillBackground =
        if (muted) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val labelColor =
        if (muted) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val countBackground =
        if (muted) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    val countColor =
        if (muted) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Row(
            modifier =
                Modifier
                    .background(pillBackground, MaterialTheme.shapes.extraExtraLarge)
                    .semantics {
                        contentDescription =
                            if (expanded) {
                                contentDescriptionCollapse
                            } else {
                                contentDescriptionExpand
                            }
                    }.appClickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
            )
            Box(
                modifier =
                    Modifier
                        .background(countBackground, MaterialTheme.shapes.extraExtraLarge)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = countColor,
                )
            }
            RememberMaterialRoundedSymbol(
                name = "expand_more",
                size = 18.dp,
                tint = labelColor,
                weight = FontWeight.Medium,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
            )
        }
    }
}

@Composable
internal fun StateBadgedNoteCard(
    note: NoteWithItems,
    model: NoteCardUiModel,
    interaction: InteractionState,
    onOpen: (NoteWithItems) -> Unit,
    onSwipeAction: (NoteWithItems, NoteSwipeAction) -> Unit,
    badgeText: String,
    badgeStyle: SectionBadgeStyle,
    modifier: Modifier = Modifier,
    reminderNotificationsAllowed: Boolean = true,
) {
    val backgroundColor =
        when (badgeStyle) {
            SectionBadgeStyle.ARCHIVE -> MaterialTheme.colorScheme.secondaryContainer
            SectionBadgeStyle.TRASH -> MaterialTheme.colorScheme.errorContainer
        }
    val foregroundColor =
        when (badgeStyle) {
            SectionBadgeStyle.ARCHIVE -> MaterialTheme.colorScheme.onSecondaryContainer
            SectionBadgeStyle.TRASH -> MaterialTheme.colorScheme.onErrorContainer
        }
    Box(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.graphicsLayer { alpha = 0.88f }) {
            SwipeableRememberNoteCard(
                note = note,
                model = model,
                interaction = interaction,
                onOpenNote = onOpen,
                onSwipeAction = onSwipeAction,
                swipeEnabled = false,
                reminderNotificationsAllowed = reminderNotificationsAllowed,
            )
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 14.dp)
                    .background(backgroundColor, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                color = foregroundColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
