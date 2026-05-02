package dev.bikram.remember.ui.main

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import dev.bikram.remember.ui.theme.LocalReducedMotion
import dev.bikram.remember.ui.theme.MorphPolygonShape
import dev.bikram.remember.ui.theme.RoundedPolygonShape
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
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
    var closeNotesRevealRequest by rememberSaveable { mutableStateOf(0) }
    var historySection by rememberSaveable { mutableStateOf(HistorySection.ARCHIVE) }
    var historyVisibleItemCount by rememberSaveable { mutableStateOf(0) }
    val tabStateHolder = rememberSaveableStateHolder()
    val context = LocalContext.current
    val shareText = stringResource(R.string.main_share_text)
    val shareChooserTitle = stringResource(R.string.main_share_chooser_title)
    val reducedMotion = LocalReducedMotion.current

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
                    if (reducedMotion) {
                        fadeIn(animationSpec = tween(durationMillis = 0)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 0))
                    } else if (targetState.ordinal > initialState.ordinal) {
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
                                closeRevealRequest = closeNotesRevealRequest,
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
                enter = fadeIn(reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec())),
                exit = fadeOut(reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec())),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .tapSoundClickable(onClick = { fabExpanded = false }),
                )
            }

            // Bottom strip. Pill mode (phones) re-uses the original CenteredPillWithSideFab
            // layout so the FAB visually attaches to the pill at the same position as
            // before; the layout's measure logic clamps its reported height to the FAB's
            // *core* size, so when the FloatingActionButtonMenu's content expands the
            // wrapper grows upward beyond the strip's reported bounds without pushing the
            // pill. Rail mode is unaffected - the FAB sits in its own BottomEnd box.
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
                        onToggleNotesFab = {
                            closeNotesRevealRequest++
                            fabExpanded = !fabExpanded
                        },
                        historySection = historySection,
                        historyVisibleItemCount = historyVisibleItemCount,
                        onClearTrashRequest = { clearTrashOpen = true },
                        onMoveArchiveToTrashRequest = { moveArchiveToTrashOpen = true },
                        onShareApp = {
                            val send =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                            context.startActivity(Intent.createChooser(send, shareChooserTitle))
                        },
                        onPickImport = {
                            closeNotesRevealRequest++
                            fabExpanded = false
                            onImportGoogleTasks()
                        },
                        onPickList = {
                            closeNotesRevealRequest++
                            fabExpanded = false
                            onCreateList()
                        },
                        onPickNote = {
                            closeNotesRevealRequest++
                            fabExpanded = false
                            onCreateNote()
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
                                closeNotesRevealRequest++
                                tab = selected
                                fabExpanded = false
                            },
                        )
                    },
                    fab = {
                        MainFabSlot(
                            tab = tab,
                            fabExpanded = fabExpanded,
                            onToggleNotesFab = {
                                closeNotesRevealRequest++
                                fabExpanded = !fabExpanded
                            },
                            historySection = historySection,
                            historyVisibleItemCount = historyVisibleItemCount,
                            onClearTrashRequest = { clearTrashOpen = true },
                            onMoveArchiveToTrashRequest = { moveArchiveToTrashOpen = true },
                            onShareApp = {
                                val send =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                context.startActivity(Intent.createChooser(send, shareChooserTitle))
                            },
                            onPickImport = {
                                closeNotesRevealRequest++
                                fabExpanded = false
                                onImportGoogleTasks()
                            },
                            onPickList = {
                                closeNotesRevealRequest++
                                fabExpanded = false
                                onCreateList()
                            },
                            onPickNote = {
                                closeNotesRevealRequest++
                                fabExpanded = false
                                onCreateNote()
                            },
                        )
                    },
                    fabRightInset = if (tab == MainTab.Notes) 16.dp else 0.dp,
                    fabBottomInset = if (tab == MainTab.Notes) 16.dp else 0.dp,
                )
            }

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
                            closeNotesRevealRequest++
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
 * Bottom-strip layout: pill horizontally centered against the strip's full width, FAB
 * placed just to the right of the pill (or clamped to the strip's right edge if a wide
 * pill would push it off-screen). Both children share a vertical centerline so the FAB
 * and the pill optically sit on the same baseline.
 *
 * Designed to host an M3 Expressive [FloatingActionButtonMenu] in the [fab] slot
 * without letting the menu's expansion push the pill. Two tricks:
 *
 *   1. The [fab] child is measured with `Constraints.Infinity` for height, so the menu
 *      can grow as tall as it wants when expanded; the strip doesn't cap it.
 *   2. The strip's reported height is clamped to [fabCoreSize] (not the wrapper's
 *      potentially-much-larger measured height). The expanded menu overflows upward
 *      beyond the strip's reported bounds, but Compose draws and hit-tests it because
 *      no parent in the chain clips. The pill never sees the menu's height.
 *
 * Placement of the FAB child anchors the *FAB element* (assumed to sit at the wrapper's
 * right + bottom minus [fabRightInset] / [fabBottomInset]) at the same screen position
 * regardless of collapsed/expanded state - menu width and height grow inward
 * (left and up). The insets are zero for a regular FAB; the Notes
 * [FloatingActionButtonMenu] uses Material's 16.dp horizontal menu padding and 16.dp
 * bottom button padding.
 */
@Composable
private fun CenteredPillWithSideFab(
    pill: @Composable () -> Unit,
    fab: @Composable () -> Unit,
    fabGap: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    fabCoreSize: androidx.compose.ui.unit.Dp = 56.dp,
    fabRightInset: androidx.compose.ui.unit.Dp = 0.dp,
    fabBottomInset: androidx.compose.ui.unit.Dp = 0.dp,
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
        // Measure the FAB child unconstrained vertically so the FloatingActionButtonMenu's
        // expanded items can be as tall as they need without the strip capping them.
        val fabPlaceable =
            measurables[1].measure(
                loose.copy(maxHeight = androidx.compose.ui.unit.Constraints.Infinity),
            )
        val gapPx = fabGap.roundToPx()
        val fabCorePx = fabCoreSize.roundToPx()
        val fabRightInsetPx = fabRightInset.roundToPx()
        val fabBottomInsetPx = fabBottomInset.roundToPx()

        val width =
            if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                pillPlaceable.width + gapPx + fabPlaceable.width
            }
        // Strip height tracks the FAB's *core* size rather than the wrapper's measured
        // height. When the menu is expanded the wrapper is much taller; we deliberately
        // ignore that so the parent layout doesn't see the bigger height and re-center
        // the strip (which would push the pill).
        val stripHeight = maxOf(pillPlaceable.height, fabCorePx)

        layout(width, stripHeight) {
            // Pill: horizontally centered against the strip's full width and vertically
            // centered within the strip.
            val pillX = (width - pillPlaceable.width) / 2
            val pillY = (stripHeight - pillPlaceable.height) / 2
            pillPlaceable.place(pillX, pillY)

            // FAB child: anchored by the FAB *element* rather than by the wrapper's
            // outer bounds.
            //
            // Horizontally: the FAB element's right edge sits at clamp(pill.right + gap
            // + fabCore, stripWidth). For a wrapper wider than the FAB (menu expanded)
            // the wrapper extends LEFT, leaving the FAB element anchored at the same
            // screen position. FloatingActionButtonMenu adds 16.dp side padding, so
            // Notes passes that as [fabRightInset].
            //
            // Vertically: the FAB element's center sits at the strip's vertical center
            // (same as pill's). FloatingActionButtonMenu places the button 16.dp above
            // the wrapper bottom, so Notes passes that as [fabBottomInset].
            val desiredFabElementRight =
                pillX + pillPlaceable.width + gapPx + fabCorePx
            val fabElementRight = desiredFabElementRight.coerceAtMost(width)
            val fabX = (fabElementRight - fabPlaceable.width + fabRightInsetPx).coerceAtLeast(0)
            val fabBottomY = (stripHeight + fabCorePx) / 2 + fabBottomInsetPx
            val fabY = fabBottomY - fabPlaceable.height
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
            reducedMotionAwareSpec(
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
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
 * Per-tab FAB. Notes wraps an M3 Expressive [ToggleFloatingActionButton] inside the
 * official [FloatingActionButtonMenu] so the speed dial gets the framework's stagger,
 * predictive-back collapse, and accessibility focus order for free. History and
 * Settings render a regular FAB without a menu.
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
    onPickImport: () -> Unit,
    onPickList: () -> Unit,
    onPickNote: () -> Unit,
) {
    when (tab) {
        MainTab.Notes -> {
            val playTap = rememberPlayTapSound()
            val createDescription = stringResource(R.string.main_fab_create)
            val closeDescription = stringResource(R.string.main_fab_close)
            val description = if (fabExpanded) closeDescription else createDescription
            val motionScheme = MaterialTheme.motionScheme
            val iconColor by animateColorAsState(
                targetValue =
                    if (fabExpanded) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                animationSpec = reducedMotionAwareSpec(motionScheme.defaultEffectsSpec()),
                label = "notes_fab_icon_color",
            )
            val fabMorph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny) }
            val shapeProgress by animateFloatAsState(
                targetValue = if (fabExpanded) 1f else 0f,
                animationSpec = reducedMotionAwareSpec(motionScheme.defaultSpatialSpec()),
                label = "notes_fab_shape_morph",
            )
            val iconRotation by animateFloatAsState(
                targetValue = if (fabExpanded) 45f else 0f,
                animationSpec = reducedMotionAwareSpec(motionScheme.defaultSpatialSpec()),
                label = "notes_fab_icon_rotation",
            )
            val fabShape = MorphPolygonShape(fabMorph, shapeProgress)
            // FloatingActionButtonMenu hosts BOTH the toggle FAB and the menu items. The
            // FAB element stays anchored (right + bottom of the wrapper); the menu items
            // expand upward and leftward when [expanded] flips on. The pill-mode caller
            // ([CenteredPillWithSideFab]) deliberately measures this child with infinite
            // max height and reports a strip height clamped to the FAB's core size, so
            // the menu's expansion overflows up into screen space without re-flowing the
            // pill - what the hand-rolled SpeedDialOverlay used to enforce, now folded
            // into the surrounding Layout instead.
            FloatingActionButtonMenu(
                expanded = fabExpanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = fabExpanded,
                        onCheckedChange = {
                            playTap()
                            onToggleNotesFab()
                        },
                        modifier =
                            Modifier
                                .shadow(
                                    elevation = 2.dp,
                                    shape = fabShape,
                                    clip = false,
                                ).clip(fabShape)
                                .semantics { contentDescription = description },
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "add",
                            size = 26.dp,
                            tint = iconColor,
                            weight = FontWeight.Medium,
                            modifier = Modifier.graphicsLayer { rotationZ = iconRotation },
                        )
                    }
                },
                horizontalAlignment = Alignment.End,
            ) {
                FloatingActionButtonMenuItem(
                    onClick = onPickImport,
                    icon = {
                        RememberMaterialRoundedSymbol(
                            name = "download",
                            size = 22.dp,
                            weight = FontWeight.Medium,
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.main_speed_dial_import),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    },
                )
                FloatingActionButtonMenuItem(
                    onClick = onPickList,
                    icon = {
                        RememberMaterialRoundedSymbol(
                            name = DEFAULT_LIST_HEADER_SYMBOL,
                            size = 22.dp,
                            weight = FontWeight.Medium,
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.main_speed_dial_checklist),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    },
                )
                FloatingActionButtonMenuItem(
                    onClick = onPickNote,
                    icon = {
                        RememberMaterialRoundedSymbol(
                            name = DEFAULT_NOTE_HEADER_SYMBOL,
                            size = 22.dp,
                            weight = FontWeight.Medium,
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.main_speed_dial_note),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    },
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
            RoundedPolygonShape(MaterialShapes.Cookie9Sided)
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
