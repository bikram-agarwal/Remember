package dev.bikram.remember.ui.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.MarkdownText
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton
import dev.bikram.remember.ui.components.rememberResponsiveActionButtonSize
import dev.bikram.remember.ui.components.settings.GroupPosition
import dev.bikram.remember.ui.components.settings.GroupedListItem
import dev.bikram.remember.ui.feedback.appClickable
import dev.bikram.remember.ui.modifiers.applyToScrollableList
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HelpScreen(
    onBack: () -> Unit,
    onOpenAppSection: (sectionKey: String) -> Unit,
    helpVm: HelpViewModel,
) {
    val expandedKeys by helpVm.expandedKeys.collectAsStateWithLifecycle()
    val searchQuery by helpVm.searchQuery.collectAsStateWithLifecycle()
    val filteredSections by helpVm.filteredSections.collectAsStateWithLifecycle()

    val allSubsectionKeys =
        remember(filteredSections) {
            filteredSections.flatMap { s -> s.subsections.map { "${s.title}/${it.title}" } }
        }
    val allExpanded = allSubsectionKeys.isNotEmpty() && allSubsectionKeys.all { it in expandedKeys }

    val initialScrollIndex = helpVm.scrollIndex
    val initialScrollOffset = helpVm.scrollOffset
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = initialScrollIndex,
            initialFirstVisibleItemScrollOffset = initialScrollOffset,
        )
    val density = LocalDensity.current
    LaunchedEffect(scrollBehavior.state.heightOffsetLimit) {
        val heightOffsetLimit = scrollBehavior.state.heightOffsetLimit
        val restoredAwayFromTop = initialScrollIndex > 0 || initialScrollOffset > 0
        if (restoredAwayFromTop && heightOffsetLimit != 0f) {
            val collapseFraction =
                if (initialScrollIndex > 0) {
                    1f
                } else {
                    val thresholdPx = with(density) { 24.dp.toPx() }
                    (initialScrollOffset.toFloat() / thresholdPx).coerceIn(0f, 1f)
                }
            scrollBehavior.state.heightOffset = heightOffsetLimit * collapseFraction
        }
    }
    val topAlphaMultiplier by remember(listState) {
        derivedStateOf {
            val collapsedFraction = scrollBehavior.state.collapsedFraction
            if (listState.firstVisibleItemIndex > 0) {
                collapsedFraction
            } else {
                val offsetPx = listState.firstVisibleItemScrollOffset.toFloat()
                val thresholdPx = with(density) { 24.dp.toPx() }
                val scrollFraction = (offsetPx / thresholdPx).coerceIn(0f, 1f)
                collapsedFraction * scrollFraction
            }
        }
    }
    val blurStyle =
        rememberProgressiveBlurStyle(
            bottomExtra = 0.dp,
            topExtra = 76.dp,
            topBlurProgressPower = 1.1f,
        )
    val blurMod =
        blurStyle?.applyToScrollableList(topAlphaMultiplier = topAlphaMultiplier)
            ?: Modifier
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(Unit) {
        onDispose { helpVm.saveScrollState(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
    }
    DisposableEffect(lifecycleOwner, focusManager, keyboardController) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            LargeTopAppBar(
                colors = transparentLargeTopAppBarColors(),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    Box(
                        modifier =
                            Modifier
                                .padding(start = 4.dp)
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.extraExtraLarge)
                                .appClickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "arrow_back",
                            autoMirror = true,
                            size = 24.dp,
                            tint = MaterialTheme.colorScheme.onSurface,
                            weight = FontWeight.Medium,
                        )
                    }
                },
                title = { Text(stringResource(R.string.help_title)) },
                actions = {
                    val expandCollapseLabel =
                        stringResource(
                            if (allExpanded) R.string.help_collapse_all_cd else R.string.help_expand_all_cd,
                        )
                    RememberFilledTonalIconButton(
                        onClick = {
                            if (allExpanded) {
                                helpVm.collapseAll(allSubsectionKeys)
                            } else {
                                helpVm.expandAll(allSubsectionKeys)
                            }
                        },
                        modifier =
                            Modifier
                                .size(rememberResponsiveActionButtonSize())
                                .semantics { contentDescription = expandCollapseLabel },
                        tooltipLabel = expandCollapseLabel,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = if (allExpanded) "unfold_less" else "unfold_more",
                            size = 22.dp,
                            weight = FontWeight.Medium,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().then(blurMod),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = helpVm::setSearchQuery,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    placeholder = { Text(stringResource(R.string.help_search_placeholder)) },
                    leadingIcon = {
                        RememberMaterialRoundedSymbol(
                            name = "search",
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            weight = FontWeight.Normal,
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(32.dp)
                                        .clip(MaterialTheme.shapes.extraExtraLarge)
                                        .appClickable { helpVm.setSearchQuery("") },
                                contentAlignment = Alignment.Center,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "close",
                                    size = 18.dp,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    weight = FontWeight.Normal,
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraExtraLarge,
                )
            }

            if (filteredSections.isEmpty()) {
                item(key = "no_results") {
                    Text(
                        text = stringResource(R.string.help_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, start = 4.dp),
                    )
                }
            }

            filteredSections.forEach { section ->
                item(key = "label_${section.title}") {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp, start = 4.dp),
                    )
                }
                itemsIndexed(
                    items = section.subsections,
                    key = { _, sub -> "sub_${section.title}_${sub.title}" },
                ) { index, subsection ->
                    val isFirst = index == 0
                    val isLast = index == section.subsections.lastIndex
                    val groupPosition =
                        when {
                            isFirst && isLast -> GroupPosition.ONLY
                            isFirst -> GroupPosition.FIRST
                            isLast -> GroupPosition.LAST
                            else -> GroupPosition.MIDDLE
                        }
                    val key = "${section.title}/${subsection.title}"
                    val isExpanded = key in expandedKeys
                    val actions = helpSubsectionActions[subsection.title] ?: emptyList()
                    HelpSubsectionCard(
                        subsection = subsection,
                        groupPosition = groupPosition,
                        isExpanded = isExpanded,
                        onToggle = { helpVm.setExpanded(key, !isExpanded) },
                        actions = actions,
                        onOpenAppSection = onOpenAppSection,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun HelpSubsectionCard(
    subsection: HelpSubsection,
    groupPosition: GroupPosition,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    actions: List<HelpAction>,
    onOpenAppSection: (sectionKey: String) -> Unit,
) {
    val spatialSpec =
        reducedMotionAwareSpec(MaterialTheme.motionScheme.slowSpatialSpec<androidx.compose.ui.unit.IntSize>())
    val dpSpatialSpec =
        reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.Dp>())
    val colorSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Color>())
    val fadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val fadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec()),
        label = "help_chevron_rotation",
    )
    val chevronContainerSize by animateDpAsState(
        targetValue = if (!isExpanded) 32.dp else 20.dp,
        animationSpec = dpSpatialSpec,
        label = "help_chevron_container_size",
    )
    val chevronContainerColor by animateColorAsState(
        targetValue = if (!isExpanded) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
        animationSpec = colorSpec,
        label = "help_chevron_container_color",
    )

    val contentDescriptionExpand = stringResource(R.string.section_expand_cd, subsection.title)
    val contentDescriptionCollapse = stringResource(R.string.section_collapse_cd, subsection.title)
    val interactionSource = remember { MutableInteractionSource() }

    GroupedListItem(position = groupPosition) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                if (!isExpanded) contentDescriptionExpand else contentDescriptionCollapse
                        }.clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                        ) {
                            onToggle()
                        }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = subsection.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier =
                        Modifier
                            .size(chevronContainerSize)
                            .clip(MaterialTheme.shapes.extraExtraLarge)
                            .background(chevronContainerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "chevron_right",
                        autoMirror = true,
                        size = 18.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        weight = FontWeight.Medium,
                        modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter =
                    expandVertically(
                        animationSpec = spatialSpec,
                        expandFrom = Alignment.Top,
                    ) + fadeIn(fadeInSpec),
                exit =
                    shrinkVertically(
                        animationSpec = spatialSpec,
                        shrinkTowards = Alignment.Top,
                    ) + fadeOut(fadeOutSpec),
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MarkdownText(
                        markdown = subsection.body,
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    )
                    if (actions.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            actions.forEach { action ->
                                FilledTonalButton(
                                    onClick = {
                                        when (action) {
                                            is HelpAction.OpenAppSection -> onOpenAppSection(action.sectionKey)
                                        }
                                    },
                                ) {
                                    Text(action.label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
