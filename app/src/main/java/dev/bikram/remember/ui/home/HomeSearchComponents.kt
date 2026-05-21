package dev.bikram.remember.ui.home

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bikram.remember.R
import dev.bikram.remember.data.InteractionState
import dev.bikram.remember.data.NoteSwipeAction
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.NoteCardUiModel
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton
import dev.bikram.remember.ui.components.SwipeableRememberNoteCard
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec

/**
 * Top-bar title that doubles as the search entry point. Renders a single Row of:
 *
 *   [animated content area (weight 1)] [search/close icon button]
 *
 * The animated content area swaps between the app-name title and [InlineSearchField]
 * with `expandHorizontally(expandFrom = Alignment.End)`, so the search bar emerges
 * from the right edge of the slot - which sits immediately to the left of the toggle
 * button. Visually the bar appears to grow out of the button's left edge, instead of
 * sliding in from the title's start (the previous setup placed the toggle button in
 * the LargeTopAppBar's `actions` slot, which made the expansion anchor sit in the
 * middle of the row, disconnected from where the user just tapped).
 *
 * Intended call site: the LargeTopAppBar's `title` slot. The `actions` slot is left
 * for selection-mode chrome only.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchableTopBarTitle(
    searchOpen: Boolean,
    requestSearchFocus: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchFocusRequested: () -> Unit,
    onToggleSearch: () -> Unit,
) {
    val spatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>())
    val fadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val fadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    val scaleIconSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = searchOpen,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val enter =
                    fadeIn(fadeInSpec) +
                        expandHorizontally(spatialSpec, expandFrom = Alignment.End)
                val exit =
                    fadeOut(fadeOutSpec) +
                        shrinkHorizontally(spatialSpec, shrinkTowards = Alignment.End)
                (enter togetherWith exit).using(SizeTransform(clip = false))
            },
            label = "topBarTitleSearchExpand",
        ) { open ->
            if (open) {
                InlineSearchField(
                    query = query,
                    requestFocus = requestSearchFocus,
                    onQueryChange = onQueryChange,
                    onFocusRequested = onSearchFocusRequested,
                )
            } else {
                // Empty title placeholder to remove the big title
            }
        }
        // Toggle button stays in place at the End. The icon morphs between search and
        // close, scoped inside its own AnimatedContent so the rest of the row's
        // expansion animation isn't gated on the icon swap.
        val cdCloseSearch = stringResource(R.string.cd_close_search)
        val cdSearch = stringResource(R.string.cd_search)
        RememberFilledTonalIconButton(onClick = onToggleSearch) {
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

@Composable
internal fun InlineSearchField(
    query: String,
    requestFocus: Boolean,
    onQueryChange: (String) -> Unit,
    onFocusRequested: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(query, selection = TextRange(query.length)))
    }
    LaunchedEffect(query) {
        if (query != searchFieldValue.text) {
            searchFieldValue = TextFieldValue(query, selection = TextRange(query.length))
        }
    }
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
            onFocusRequested()
        }
    }
    val searchContentDescription = stringResource(R.string.cd_search)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.extraLargeIncreased,
                ).background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.shapes.extraLargeIncreased,
                ).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberMaterialRoundedSymbol(
            name = "search",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = searchFieldValue,
            onValueChange = { newValue ->
                searchFieldValue = newValue
                if (newValue.text != query) onQueryChange(newValue.text)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            textStyle =
                MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .semantics {
                        contentDescription = searchContentDescription
                    },
            decorationBox = { innerTextField ->
                if (searchFieldValue.text.isEmpty()) {
                    Text(
                        stringResource(R.string.home_search_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            },
        )
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
                    }.tapSoundClickable(onClick = onToggle)
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
