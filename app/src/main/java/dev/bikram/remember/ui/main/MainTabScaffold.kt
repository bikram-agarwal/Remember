package dev.bikram.remember.ui.main

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import dev.bikram.remember.R
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.RememberPredictiveBackHandler
import dev.bikram.remember.ui.components.RememberFloatingActionButton
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.edit.DEFAULT_LIST_HEADER_SYMBOL
import dev.bikram.remember.ui.edit.DEFAULT_NOTE_HEADER_SYMBOL
import dev.bikram.remember.ui.feedback.rememberPlayTapSound
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.history.HistoryRoute
import dev.bikram.remember.ui.history.HistorySection
import dev.bikram.remember.ui.home.HomeRoute
import dev.bikram.remember.ui.settings.SettingsRoute
import dev.bikram.remember.ui.theme.MorphPolygonShape
import dev.bikram.remember.ui.theme.RoundedPolygonShape
import kotlinx.coroutines.launch

/**
 * Bottom tabs. Each [symbolName] must be listed by `font_subset/harvest_ligatures.py`
 * (then run `subset_font.py`) so the subset icon font actually contains that glyph.
 */
enum class MainTab(
    @param:androidx.annotation.StringRes val labelRes: Int,
    val symbolName: String,
) {
    Notes(R.string.main_tab_notes, "notes"),
    History(R.string.main_tab_history, "history"),
    Settings(R.string.main_tab_settings, "settings"),
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3AdaptiveNavigationSuiteApi::class,
)
@Composable
fun MainTabScaffold(
    repository: NoteRepository,
    interactionPrefs: InteractionPrefs,
    onCreateNote: () -> Unit,
    onCreateList: () -> Unit,
    onOpenNote: (NoteWithItems, Boolean) -> Unit,
    onImportGoogleTasks: () -> Unit,
    onOpenIntro: () -> Unit,
    openSettingsRequest: Int = 0,
    openUpdateSheetRequest: Int = 0,
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.Notes) }
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    var clearTrashOpen by rememberSaveable { mutableStateOf(false) }
    var moveArchiveToTrashOpen by rememberSaveable { mutableStateOf(false) }
    var historySection by rememberSaveable { mutableStateOf(HistorySection.ARCHIVE) }
    var historyVisibleItemCount by rememberSaveable { mutableStateOf(0) }
    val tabStateHolder = rememberSaveableStateHolder()
    val context = LocalContext.current

    // FAB position captured from the toolbar's floatingActionButton slot, in root-window
    // coordinates. The speed-dial overlay reads it to anchor menu items above the FAB
    // without contributing to the toolbar's layout, which is what kept the previous
    // version's nav pill stable.
    var fabPositionInRoot by remember { mutableStateOf<Offset?>(null) }
    var fabSizePx by remember { mutableStateOf(IntSize.Zero) }

    // Back handling: when the FAB speed dial is open, back closes it first.
    // On History/Settings tab, back returns to the Notes tab instead of exiting.
    // On Notes tab with nothing open, the default handler runs (exits the app).
    RememberPredictiveBackHandler(enabled = fabExpanded) { fabExpanded = false }
    RememberPredictiveBackHandler(enabled = !fabExpanded && tab != MainTab.Notes) {
        tab = MainTab.Notes
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(openSettingsRequest) {
        if (openSettingsRequest > 0) {
            tab = MainTab.Settings
        }
    }
    LaunchedEffect(tab) {
        // Switching tabs collapses the speed dial so the captured FAB bounds do not
        // briefly point at a stale location while the new tab's FAB rebinds.
        if (tab != MainTab.Notes) fabExpanded = false
    }

    val navigationSuiteType =
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    val useAdaptiveNavigationRail = navigationSuiteType != NavigationSuiteType.NavigationBar
    val mainContent: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    if (forward) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "tab",
                modifier = Modifier.fillMaxSize(),
            ) { current ->
                tabStateHolder.SaveableStateProvider(current.name) {
                    when (current) {
                        MainTab.Notes ->
                            HomeRoute(
                                interactionPrefs = interactionPrefs,
                                onOpenNote = { note, forceEdit -> onOpenNote(note, forceEdit) },
                                onCreateNote = {
                                    fabExpanded = false
                                    onCreateNote()
                                },
                                onCreateList = {
                                    fabExpanded = false
                                    onCreateList()
                                },
                            )
                        MainTab.History ->
                            HistoryRoute(
                                interactionPrefs = interactionPrefs,
                                section = historySection,
                                onSectionChange = { historySection = it },
                                onVisibleItemCountChange = { historyVisibleItemCount = it },
                                onOpenNote = { note, forceEdit -> onOpenNote(note, forceEdit) },
                            )
                        MainTab.Settings ->
                            SettingsRoute(
                                onOpenIntro = onOpenIntro,
                                openUpdateSheetRequest = openUpdateSheetRequest,
                            )
                    }
                }
            }

            // Scrim covers the page (not the toolbar / FAB) when the speed dial is open.
            // Tapping the scrim collapses the menu, matching standard speed-dial dismiss.
            AnimatedVisibility(
                visible = fabExpanded && tab == MainTab.Notes,
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .tapSoundClickable(onClick = { fabExpanded = false }),
                )
            }

            // Bottom strip: the nav pill is centered on screen on its own, and the FAB sits
            // 12.dp to the right of the pill. Layout is the same on all tabs - only the FAB
            // contents differ. Because the pill is screen-centered (not the pill+FAB unit),
            // the FAB ends up nearer the screen's right edge, which lets the speed dial that
            // expands above it sit close to the right edge too.
            if (useAdaptiveNavigationRail) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(end = 24.dp, bottom = 24.dp),
                ) {
                    MainFabSlot(
                        tab = tab,
                        fabExpanded = fabExpanded,
                        onToggleNotesFab = { fabExpanded = !fabExpanded },
                        historySection = historySection,
                        historyVisibleItemCount = historyVisibleItemCount,
                        onClearTrashRequest = { clearTrashOpen = true },
                        onMoveArchiveToTrashRequest = { moveArchiveToTrashOpen = true },
                        onShareApp = {
                            val shareText = context.getString(R.string.main_share_text)
                            val shareChooserTitle = context.getString(R.string.main_share_chooser_title)
                            val send =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                            context.startActivity(Intent.createChooser(send, shareChooserTitle))
                        },
                        onFabBounds = { offset, size ->
                            fabPositionInRoot = offset
                            fabSizePx = size
                        },
                    )
                }
            } else {
                CenteredPillWithSideFab(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = 12.dp, start = 24.dp, end = 24.dp),
                    fabGap = 12.dp,
                    pill = {
                        RememberFloatingNavPill(
                            currentTab = tab,
                            onTabClick = { selected ->
                                tab = selected
                                fabExpanded = false
                            },
                        )
                    },
                    fab = {
                        MainFabSlot(
                            tab = tab,
                            fabExpanded = fabExpanded,
                            onToggleNotesFab = { fabExpanded = !fabExpanded },
                            historySection = historySection,
                            historyVisibleItemCount = historyVisibleItemCount,
                            onClearTrashRequest = { clearTrashOpen = true },
                            onMoveArchiveToTrashRequest = { moveArchiveToTrashOpen = true },
                            onShareApp = {
                                val shareText = context.getString(R.string.main_share_text)
                                val shareChooserTitle = context.getString(R.string.main_share_chooser_title)
                                val send =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                context.startActivity(Intent.createChooser(send, shareChooserTitle))
                            },
                            onFabBounds = { offset, size ->
                                fabPositionInRoot = offset
                                fabSizePx = size
                            },
                        )
                    },
                )
            }

            // Speed dial overlay: absolutely positioned above the captured FAB bounds, so
            // expanding it cannot shift the toolbar. End-aligned with the FAB's right edge,
            // which matches the M3 Expressive `FloatingActionButtonMenu` default placement.
            SpeedDialOverlay(
                visible = fabExpanded && tab == MainTab.Notes && fabPositionInRoot != null,
                fabPositionInRoot = fabPositionInRoot,
                fabSizePx = fabSizePx,
                onPickImport = {
                    fabExpanded = false
                    onImportGoogleTasks()
                },
                onPickList = {
                    fabExpanded = false
                    onCreateList()
                },
                onPickNote = {
                    fabExpanded = false
                    onCreateNote()
                },
            )

            androidx.compose.material3.SnackbarHost(
                hostState = dev.bikram.remember.ui.theme.LocalSnackbarHostState.current,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 80.dp), // Push it above the floating nav bar
            )
        }
    }

    if (useAdaptiveNavigationRail) {
        NavigationSuiteScaffold(
            layoutType = navigationSuiteType,
            containerColor = Color.Transparent,
            navigationSuiteItems = {
                MainTab.entries.forEach { tabItem ->
                    item(
                        selected = tab == tabItem,
                        onClick = {
                            tab = tabItem
                            fabExpanded = false
                        },
                        icon = {
                            RememberMaterialRoundedSymbol(
                                name = tabItem.symbolName,
                                weight = FontWeight.Medium,
                                filled = tab == tabItem,
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(tabItem.labelRes),
                                style = MaterialTheme.typography.labelLargeEmphasized,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                    )
                }
            },
        ) {
            mainContent()
        }
    } else {
        mainContent()
    }

    if (clearTrashOpen) {
        AppBottomSheet(
            title = stringResource(R.string.main_empty_trash_title),
            subtitle = stringResource(R.string.main_empty_trash_subtitle),
            onDismiss = { clearTrashOpen = false },
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
            subtitleSpacing = 12.dp,
            actions = {
                RememberTextButton(onClick = { clearTrashOpen = false }) { Text(stringResource(R.string.common_cancel)) }
                RememberTextButton(onClick = {
                    clearTrashOpen = false
                    scope.launch { repository.emptyTrash() }
                }) { Text(stringResource(R.string.common_empty)) }
            },
        ) {
            // No body content - subtitle covers the warning.
        }
    }

    if (moveArchiveToTrashOpen) {
        AppBottomSheet(
            title = stringResource(R.string.main_move_archive_to_trash_title),
            subtitle = stringResource(R.string.main_move_archive_to_trash_subtitle),
            onDismiss = { moveArchiveToTrashOpen = false },
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
            subtitleSpacing = 12.dp,
            actions = {
                RememberTextButton(onClick = { moveArchiveToTrashOpen = false }) { Text(stringResource(R.string.common_cancel)) }
                RememberTextButton(onClick = {
                    moveArchiveToTrashOpen = false
                    scope.launch { repository.moveAllArchivedToTrash() }
                }) { Text(stringResource(R.string.edit_bottom_bar_trash)) }
            },
        ) {
            // No body content - subtitle covers the retention warning.
        }
    }
}

/**
 * Pill-only floating nav. The FAB is rendered as a sibling by [CenteredPillWithSideFab]
 * so that the pill alone is centered on screen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RememberFloatingNavPill(
    currentTab: MainTab,
    onTabClick: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors =
            FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                toolbarContainerColor = MaterialTheme.colorScheme.primary,
                toolbarContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        MainTab.entries.forEachIndexed { index, item ->
            FloatingNavTabItem(
                item = item,
                selected = currentTab == item,
                index = index,
                onTabClick = onTabClick,
            )
        }
    }
}

/**
 * Lays out [pill] horizontally centered within the available width, then places [fab]
 * to the right of the pill with a [fabGap] gutter. Both children are vertically
 * centered to one another so a 56.dp FAB and a 64.dp pill share a centerline.
 *
 * The layout reports the full constraint width as its measured size so the strip
 * spans navigation-bar to navigation-bar; only the children's positions move. If the
 * pill is wide enough that the FAB would overflow the right edge, the FAB's right
 * edge is clamped to the layout's right edge so it stays on-screen.
 */
@Composable
private fun CenteredPillWithSideFab(
    pill: @Composable () -> Unit,
    fab: @Composable () -> Unit,
    fabGap: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    androidx.compose.ui.layout.Layout(
        modifier = modifier,
        content = {
            Box { pill() }
            Box { fab() }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val pillPlaceable = measurables[0].measure(loose)
        val fabPlaceable = measurables[1].measure(loose)
        val gapPx = fabGap.roundToPx()

        val width =
            if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                pillPlaceable.width + gapPx + fabPlaceable.width
            }
        val height = maxOf(pillPlaceable.height, fabPlaceable.height)

        layout(width, height) {
            // Pill: horizontally centered against the layout's full width.
            val pillX = (width - pillPlaceable.width) / 2
            val pillY = (height - pillPlaceable.height) / 2
            pillPlaceable.place(pillX, pillY)

            // FAB: just to the right of the pill, clamped to the layout's right edge so
            // a wide pill cannot push the FAB off-screen.
            val desiredFabLeft = pillX + pillPlaceable.width + gapPx
            val maxFabLeft = (width - fabPlaceable.width).coerceAtLeast(0)
            val fabX = desiredFabLeft.coerceAtMost(maxFabLeft)
            val fabY = (height - fabPlaceable.height) / 2
            fabPlaceable.place(fabX, fabY)
        }
    }
}

@Composable
private fun FloatingNavTabItem(
    item: MainTab,
    selected: Boolean,
    index: Int,
    onTabClick: (MainTab) -> Unit,
) {
    val labelWidth by animateDpAsState(
        targetValue = if (selected) 72.dp else 0.dp,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "nav_label_$index",
    )
    val labelString = stringResource(item.labelRes)
    RememberIconButton(
        onClick = { onTabClick(item) },
        modifier =
            Modifier
                .height(48.dp)
                .width(48.dp + labelWidth)
                .semantics { contentDescription = labelString },
        colors =
            if (selected) {
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                )
            } else {
                IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = if (selected) 6.dp else 0.dp),
        ) {
            Box(modifier = Modifier.size(24.dp)) {
                RememberMaterialRoundedSymbol(
                    name = item.symbolName,
                    size = 24.dp,
                    tint = LocalContentColor.current,
                    weight = FontWeight.Medium,
                    // Selected tab renders from the FILL=1 subset; inactive tabs render from
                    // the FILL=0 subset so the pill reads with standard tab semantics.
                    filled = selected,
                )
            }
            if (labelWidth > 4.dp) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = labelString,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Per-tab FAB rendered into the toolbar's `floatingActionButton` slot. Notes uses the
 * M3 Expressive `ToggleFloatingActionButton` (which morphs container size, corner
 * radius, and color in lockstep with `checked`); History and Settings use a regular
 * FAB. The Notes FAB also reports its global position via [onFabBounds] so the
 * sibling speed-dial overlay can anchor itself to it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainFabSlot(
    tab: MainTab,
    fabExpanded: Boolean,
    onToggleNotesFab: () -> Unit,
    historySection: HistorySection,
    historyVisibleItemCount: Int,
    onClearTrashRequest: () -> Unit,
    onMoveArchiveToTrashRequest: () -> Unit,
    onShareApp: () -> Unit,
    onFabBounds: (Offset, IntSize) -> Unit,
) {
    when (tab) {
        MainTab.Notes -> {
            val playTap = rememberPlayTapSound()
            val createDescription = stringResource(R.string.main_fab_create)
            val closeDescription = stringResource(R.string.main_fab_close)
            val description = if (fabExpanded) closeDescription else createDescription
            val motionScheme = MaterialTheme.motionScheme
            val iconColor = MaterialTheme.colorScheme.onPrimaryContainer
            val fabMorph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny) }
            val shapeProgress by animateFloatAsState(
                targetValue = if (fabExpanded) 1f else 0f,
                animationSpec = motionScheme.defaultSpatialSpec(),
                label = "notes_fab_shape_morph",
            )
            val iconRotation by animateFloatAsState(
                targetValue = if (fabExpanded) 45f else 0f,
                animationSpec = motionScheme.defaultSpatialSpec(),
                label = "notes_fab_icon_rotation",
            )
            ToggleFloatingActionButton(
                checked = fabExpanded,
                onCheckedChange = {
                    playTap()
                    onToggleNotesFab()
                },
                modifier =
                    Modifier
                        .clip(MorphPolygonShape(fabMorph, shapeProgress))
                        .semantics { contentDescription = description }
                        .onGloballyPositioned { coords ->
                            onFabBounds(coords.positionInRoot(), coords.size)
                        },
            ) {
                RememberMaterialRoundedSymbol(
                    name = "add",
                    size = 26.dp,
                    tint = iconColor,
                    weight = FontWeight.Medium,
                    modifier = Modifier.graphicsLayer { rotationZ = iconRotation },
                )
            }
        }
        MainTab.History -> {
            val isArchive = historySection == HistorySection.ARCHIVE
            val symbolName = if (isArchive) "delete_sweep" else "delete_forever"
            val description =
                if (isArchive) {
                    stringResource(R.string.common_move_to_trash)
                } else {
                    stringResource(R.string.edit_bottom_bar_delete_forever)
                }
            val iconSize = if (isArchive) 24.dp else 22.dp
            SimpleHistoryOrSettingsFab(
                symbolName = symbolName,
                description = description,
                enabled = historyVisibleItemCount > 0,
                iconSize = iconSize,
                onClick = {
                    if (isArchive) {
                        onMoveArchiveToTrashRequest()
                    } else {
                        onClearTrashRequest()
                    }
                },
            )
        }
        MainTab.Settings ->
            SimpleHistoryOrSettingsFab(
                symbolName = "share",
                description = stringResource(R.string.main_menu_share_app),
                enabled = true,
                iconSize = 26.dp,
                onClick = onShareApp,
            )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SimpleHistoryOrSettingsFab(
    symbolName: String,
    description: String,
    enabled: Boolean,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val containerColor =
        if (enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val contentColor =
        if (enabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        }
    val fabShape =
        remember(symbolName) {
            RoundedPolygonShape(MaterialShapes.Cookie7Sided)
        }
    RememberFloatingActionButton(
        onClick = onClick,
        enabled = enabled,
        shape = fabShape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
        tooltipLabel = description,
    ) {
        RememberMaterialRoundedSymbol(
            name = symbolName,
            size = iconSize,
            tint = contentColor,
            weight = FontWeight.Medium,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

/**
 * Speed-dial overlay anchored to the captured FAB bounds. Items are end-aligned with
 * the FAB's right edge and stack upward from just above the FAB. The overlay does NOT
 * participate in the toolbar's layout, so toggling expanded/collapsed cannot shift
 * the nav pill or the FAB.
 *
 * Each pill sizes to its own intrinsic content (icon + label, left-aligned) so the
 * three pills end up three different widths - the M3 Expressive speed-dial look.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun androidx.compose.foundation.layout.BoxScope.SpeedDialOverlay(
    visible: Boolean,
    fabPositionInRoot: Offset?,
    fabSizePx: IntSize,
    onPickImport: () -> Unit,
    onPickList: () -> Unit,
    onPickNote: () -> Unit,
) {
    val density = LocalDensity.current
    val gapAboveFab = 12.dp
    AnimatedVisibility(
        visible = visible,
        enter =
            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                scaleIn(
                    MaterialTheme.motionScheme.defaultSpatialSpec(),
                    initialScale = 0.85f,
                    transformOrigin = TransformOrigin(1f, 1f),
                ),
        exit =
            fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                scaleOut(
                    MaterialTheme.motionScheme.fastSpatialSpec(),
                    targetScale = 0.85f,
                    transformOrigin = TransformOrigin(1f, 1f),
                ),
        modifier =
            Modifier.layout { measurable, constraints ->
                // Measure the menu unconstrained so each pill keeps its intrinsic width.
                val placeable =
                    measurable.measure(
                        constraints.copy(minWidth = 0, minHeight = 0),
                    )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val origin = fabPositionInRoot ?: return@layout
                    val gapPx = with(density) { gapAboveFab.roundToPx() }
                    // Right-align the menu's right edge with the FAB's right edge so the
                    // FAB and the menu's pills sit on the same vertical axis on the right.
                    val x = (origin.x + fabSizePx.width).toInt() - placeable.width
                    val y = origin.y.toInt() - placeable.height - gapPx
                    placeable.place(x.coerceAtLeast(0), y.coerceAtLeast(0))
                }
            },
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpeedDialPill(
                label = stringResource(R.string.main_speed_dial_import),
                symbolName = "download",
                onClick = onPickImport,
            )
            SpeedDialPill(
                label = stringResource(R.string.main_speed_dial_checklist),
                symbolName = DEFAULT_LIST_HEADER_SYMBOL,
                onClick = onPickList,
            )
            SpeedDialPill(
                label = stringResource(R.string.main_speed_dial_note),
                symbolName = DEFAULT_NOTE_HEADER_SYMBOL,
                onClick = onPickNote,
            )
        }
    }
}

/**
 * Single speed-dial item rendered with the M3 Expressive pill silhouette: icon on the
 * left, label after a 12.dp gap, both left-aligned. The Surface wraps to its content
 * width so each pill ends up exactly as wide as its label needs.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpeedDialPill(
    label: String,
    symbolName: String,
    onClick: () -> Unit,
) {
    val playTap = rememberPlayTapSound()
    Surface(
        onClick = {
            playTap()
            onClick()
        },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 24.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RememberMaterialRoundedSymbol(
                name = symbolName,
                size = 22.dp,
                tint = LocalContentColor.current,
                weight = FontWeight.Medium,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLargeEmphasized,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
