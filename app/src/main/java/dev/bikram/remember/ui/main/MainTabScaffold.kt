package dev.bikram.remember.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.graphics.shapes.Morph
import dev.bikram.remember.R
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.notifications.appNotificationSettingsIntent
import dev.bikram.remember.ui.common.LocalAllowCompactControls
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.RememberPredictiveBackHandler
import dev.bikram.remember.ui.common.isLandscape
import dev.bikram.remember.ui.common.isSmallLandscape
import dev.bikram.remember.ui.common.rememberShareAppAction
import dev.bikram.remember.ui.components.AlertChromeSummary
import dev.bikram.remember.ui.components.AlertFloatingActionButtonMenu
import dev.bikram.remember.ui.components.RememberConfirmDialog
import dev.bikram.remember.ui.components.RememberFloatingActionButton
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.components.UpdateChromeState
import dev.bikram.remember.ui.edit.DEFAULT_LIST_HEADER_SYMBOL
import dev.bikram.remember.ui.edit.DEFAULT_NOTE_HEADER_SYMBOL
import dev.bikram.remember.ui.feedback.appClickable
import dev.bikram.remember.ui.history.HistorySection
import dev.bikram.remember.ui.nav.LocalNavAnimatedVisibilityScope
import dev.bikram.remember.ui.nav.LocalSharedTransitionScope
import dev.bikram.remember.ui.theme.MorphPolygonShape
import dev.bikram.remember.ui.theme.RoundedPolygonShape
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    ExperimentalSharedTransitionApi::class,
)
@Composable
fun MainTabScaffold(
    repository: NoteRepository,
    currentTab: MainTab,
    chromeVisible: Boolean = true,
    useDualPaneMode: Boolean = false,
    onTabSelected: (MainTab) -> Unit,
    onCreateNote: () -> Unit,
    onCreateList: () -> Unit,
    onImportGoogleTasks: () -> Unit,
    historySection: HistorySection,
    historyVisibleItemCount: Int,
    updateBarState: UpdateChromeState,
    onUpdateClick: () -> Unit,
    onDismissUpdateAvailable: () -> Unit,
    onInstallUpdate: () -> Unit,
    alertSummary: AlertChromeSummary,
    blockedReminderCount: Int,
    alertBarsExpanded: Boolean,
    onAlertBarsExpandedChange: (Boolean) -> Unit,
    content: @Composable (closeRevealRequest: Int) -> Unit,
) {
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    var clearTrashOpen by rememberSaveable { mutableStateOf(false) }
    var moveArchiveToTrashOpen by rememberSaveable { mutableStateOf(false) }
    var closeNotesRevealRequest by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val shareApp = rememberShareAppAction()
    // [chromeVisible] is the whole story. This scaffold sits OUTSIDE the NavHost (PARITY with
    // FilePipe, which gates its chrome on `showBottomBar`), so the caller derives visibility from
    // whether the current destination is a main tab. There is no enclosing nav AnimatedVisibility
    // scope to cross-check against any more - it used to be consulted when the scaffold lived inside
    // the single `main` destination.
    val effectiveChromeVisible = chromeVisible
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val chromeOverlayModifier =
        if (sharedTransitionScope != null) {
            with(sharedTransitionScope) {
                Modifier.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 10f)
            }
        } else {
            Modifier
        }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        }
    val enableReminderNotifications = {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(appNotificationSettingsIntent(context))
        }
    }

    RememberPredictiveBackHandler(enabled = effectiveChromeVisible && fabExpanded) { fabExpanded = false }
    RememberPredictiveBackHandler(enabled = effectiveChromeVisible && alertBarsExpanded && !fabExpanded) {
        onAlertBarsExpandedChange(false)
    }
    val scope = rememberCoroutineScope()
    val isLandscape = isLandscape()
    val isSmallLandscape = isSmallLandscape()

    LaunchedEffect(effectiveChromeVisible, currentTab) {
        if (!effectiveChromeVisible || currentTab != MainTab.Notes) fabExpanded = false
    }
    val useAdaptiveNavigationRail = useDualPaneMode
    val mainContent: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            content(closeNotesRevealRequest)

            AnimatedVisibility(
                visible = effectiveChromeVisible && ((fabExpanded && currentTab == MainTab.Notes) || alertBarsExpanded),
                enter = fadeIn(reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec())),
                exit = fadeOut(reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec())),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .appClickable(
                                onClick = {
                                    fabExpanded = false
                                    onAlertBarsExpandedChange(false)
                                },
                            ),
                )
            }

            if (effectiveChromeVisible) {
                if (useAdaptiveNavigationRail) {
                    if (alertSummary.count > 0) {
                        BoxWithConstraints(modifier = Modifier.matchParentSize()) {
                            val railBarsMaxWidth = maxWidth * 0.4f - 30.dp
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .then(chromeOverlayModifier)
                                        .navigationBarsPadding()
                                        .padding(
                                            start = 20.dp,
                                            bottom = if (isSmallLandscape) 10.dp else 20.dp,
                                        ),
                            ) {
                                AlertFloatingActionButtonMenu(
                                    expanded = alertBarsExpanded,
                                    onExpandedChange = { expanded ->
                                        fabExpanded = false
                                        onAlertBarsExpandedChange(expanded)
                                    },
                                    summary = alertSummary,
                                    updateState = updateBarState,
                                    blockedReminderCount = blockedReminderCount,
                                    onEnableReminderNotifications = enableReminderNotifications,
                                    onUpdateClick = onUpdateClick,
                                    onDismissUpdateAvailable = onDismissUpdateAvailable,
                                    onInstallUpdate = onInstallUpdate,
                                    barsMaxWidth = railBarsMaxWidth,
                                )
                            }
                        }
                    }
                } else {
                    // Single-pane chrome keeps full-size controls even on a short landscape window:
                    // it is one row of buttons over the content, so it can afford the height, and
                    // shrinking them made the same FABs a different size per orientation. The rail
                    // branch above deliberately stays compact - it shares its window with a pane of
                    // list content.
                    CompositionLocalProvider(LocalAllowCompactControls provides false) {
                        CenteredPillWithSideFab(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .then(chromeOverlayModifier)
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .padding(
                                        bottom = if (isLandscape) 6.dp else 12.dp,
                                        start = 24.dp,
                                        end = 24.dp,
                                    ),
                            fabGap = 16.dp,
                            fabCoreSize = 56.dp,
                            // Notes is the only tab whose FAB is a FloatingActionButtonMenu; the rest
                            // are bare FABs whose placeable is already just the button.
                            fabWrapperInset = if (currentTab == MainTab.Notes) NotesFabMenuButtonInset else 0.dp,
                            pill = {
                                RememberFloatingNavPill(
                                    currentTab = currentTab,
                                    onTabClick = { selected ->
                                        closeNotesRevealRequest++
                                        onTabSelected(selected)
                                        fabExpanded = false
                                    },
                                )
                            },
                            leadingFab =
                                if (alertSummary.count == 0) {
                                    null
                                } else {
                                    {
                                        AlertFloatingActionButtonMenu(
                                            expanded = alertBarsExpanded,
                                            onExpandedChange = { expanded ->
                                                fabExpanded = false
                                                onAlertBarsExpandedChange(expanded)
                                            },
                                            summary = alertSummary,
                                            updateState = updateBarState,
                                            blockedReminderCount = blockedReminderCount,
                                            onEnableReminderNotifications = enableReminderNotifications,
                                            onUpdateClick = onUpdateClick,
                                            onDismissUpdateAvailable = onDismissUpdateAvailable,
                                            onInstallUpdate = onInstallUpdate,
                                            centerBarsInWindow = true,
                                        )
                                    }
                                },
                            fab = {
                                MainFabSlot(
                                    tab = currentTab,
                                    fabExpanded = fabExpanded,
                                    onToggleNotesFab = {
                                        closeNotesRevealRequest++
                                        onAlertBarsExpandedChange(false)
                                        fabExpanded = !fabExpanded
                                    },
                                    historySection = historySection,
                                    historyVisibleItemCount = historyVisibleItemCount,
                                    onClearTrashRequest = { clearTrashOpen = true },
                                    onMoveArchiveToTrashRequest = { moveArchiveToTrashOpen = true },
                                    onShareApp = shareApp,
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
                        )
                    }
                }
            }

            androidx.compose.material3.SnackbarHost(
                hostState = dev.bikram.remember.ui.theme.LocalSnackbarHostState.current,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 80.dp),
            )
        }
    }

    if (effectiveChromeVisible && useAdaptiveNavigationRail) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Landscape 3-button nav sits on a side edge, not the bottom, so the rail and
                    // both panes have to clear it. Insetting here (rather than inside the rail) keeps
                    // the theme background bleeding under the bar.
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)),
        ) {
            NavigationRail(
                containerColor = Color.Transparent,
                // Vertical only: with full systemBars the rail also absorbed the opposite edge's
                // horizontal inset, widening itself and pushing the list pane off-centre.
                windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Vertical),
                modifier = Modifier.padding(start = 24.dp),
            ) {
                MainTab.entries.forEach { tabItem ->
                    NavigationRailItem(
                        selected = currentTab == tabItem,
                        onClick = {
                            closeNotesRevealRequest++
                            onTabSelected(tabItem)
                            fabExpanded = false
                        },
                        icon = {
                            RememberMaterialRoundedSymbol(
                                name = tabItem.symbolName,
                                weight = FontWeight.Medium,
                                filled = currentTab == tabItem,
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
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                mainContent()
            }
        }
    } else {
        // Single pane needs the same side-bar clearance as the rail branch above: in landscape the
        // 3-button nav bar sits on a side edge, and nothing else here insets content horizontally.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)),
        ) {
            mainContent()
        }
    }

    if (clearTrashOpen) {
        RememberConfirmDialog(
            title = stringResource(R.string.main_empty_trash_title),
            text = stringResource(R.string.main_empty_trash_subtitle),
            confirmLabel = stringResource(R.string.common_empty),
            onConfirm = {
                clearTrashOpen = false
                scope.launch { repository.emptyTrash() }
            },
            onDismiss = { clearTrashOpen = false },
            destructive = true,
        )
    }

    if (moveArchiveToTrashOpen) {
        RememberConfirmDialog(
            title = stringResource(R.string.main_move_archive_to_trash_title),
            text = stringResource(R.string.main_move_archive_to_trash_subtitle),
            confirmLabel = stringResource(R.string.edit_bottom_bar_trash),
            onConfirm = {
                moveArchiveToTrashOpen = false
                scope.launch { repository.moveAllArchivedToTrash() }
            },
            onDismiss = { moveArchiveToTrashOpen = false },
            destructive = true,
        )
    }
}

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

@Composable
private fun CenteredPillWithSideFab(
    pill: @Composable () -> Unit,
    fab: @Composable () -> Unit,
    fabGap: androidx.compose.ui.unit.Dp,
    // Must be the real rendered FAB diameter: a core that disagrees with the buttons is what used to
    // push each tab's FABs to a slightly different spot. Callers that let their FABs shrink on short
    // landscape windows have to pass the shrunken size here too.
    fabCoreSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    leadingFab: (@Composable () -> Unit)? = null,
    // Padding the trailing FAB's own wrapper leaves between its visible button and its placeable's
    // right/bottom edges. Cancelling it is what lets a wrapped FAB and a bare one land identically.
    fabWrapperInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    androidx.compose.ui.layout.Layout(
        modifier = modifier,
        content = {
            Box { pill() }
            Box { fab() }
            if (leadingFab != null) {
                Box { leadingFab() }
            }
        },
    ) { measurables, constraints ->
        val loose =
            constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxWidth = androidx.compose.ui.unit.Constraints.Infinity,
            )
        val pillPlaceable = measurables[0].measure(loose)
        val fabPlaceable =
            measurables[1].measure(
                loose.copy(maxHeight = androidx.compose.ui.unit.Constraints.Infinity),
            )
        val leadingFabPlaceable =
            measurables
                .getOrNull(2)
                ?.measure(loose.copy(maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
        val gapPx = fabGap.roundToPx()
        val fabCorePx = fabCoreSize.roundToPx()
        val fabWrapperInsetPx = fabWrapperInset.roundToPx()

        val width =
            if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                pillPlaceable.width + gapPx + fabPlaceable.width
            }
        // Identical slot on both sides keeps the pill centred and the two FABs equidistant from it.
        // Deliberately ignores the measured FAB widths: an expanded FAB menu is far wider than its
        // button and must not feed back into the scale or shift the pill.
        val sideRoomPx = gapPx + fabCorePx
        val rowNaturalWidth = pillPlaceable.width + sideRoomPx * 2
        val chromeScale =
            if (rowNaturalWidth > width && rowNaturalWidth > 0) {
                width.toFloat() / rowNaturalWidth.toFloat()
            } else {
                1f
            }
        val scaledPillWidth = (pillPlaceable.width * chromeScale).roundToInt()
        val scaledPillHeight = (pillPlaceable.height * chromeScale).roundToInt()
        val scaledFabWidth = (fabPlaceable.width * chromeScale).roundToInt()
        val scaledFabHeight = (fabPlaceable.height * chromeScale).roundToInt()
        val scaledFabCore = (fabCorePx * chromeScale).roundToInt()
        val scaledGap = (gapPx * chromeScale).roundToInt()
        val scaledFabWrapperInset = (fabWrapperInsetPx * chromeScale).roundToInt()
        val stripHeight = maxOf(scaledPillHeight, scaledFabCore)

        layout(width, stripHeight) {
            val pillX = (width - scaledPillWidth) / 2
            val pillY = (stripHeight - scaledPillHeight) / 2
            pillPlaceable.placeWithLayer(pillX, pillY) {
                scaleX = chromeScale
                scaleY = chromeScale
                transformOrigin = TransformOrigin(0f, 0f)
            }

            // Anchored by the visible button's bottom-right corner, so the button lands on the pill's
            // centre line scaledGap away, and an expanded menu grows up and to the left from there.
            // fabWrapperInset backs the placeable off by whatever padding sits outside the button.
            val fabCoreRight = (pillX + scaledPillWidth + scaledGap + scaledFabCore).coerceAtMost(width)
            val fabCoreBottom = (stripHeight + scaledFabCore) / 2
            val fabX = (fabCoreRight - scaledFabWidth + scaledFabWrapperInset).coerceAtLeast(0)
            val fabY = fabCoreBottom + scaledFabWrapperInset - scaledFabHeight
            fabPlaceable.placeWithLayer(fabX, fabY) {
                scaleX = chromeScale
                scaleY = chromeScale
                transformOrigin = TransformOrigin(0f, 0f)
            }

            leadingFabPlaceable?.let { leadingPlaceable ->
                val scaledLeadingFabWidth = (leadingPlaceable.width * chromeScale).roundToInt()
                val scaledLeadingFabHeight = (leadingPlaceable.height * chromeScale).roundToInt()
                // The alert FAB's placeable is always just its button - the unfurled bars are placed
                // in a zero-size layer - so mirroring the trailing gap and centring on the strip puts
                // it the same distance from the pill, on the same centre line.
                val leadingX = (pillX - scaledGap - scaledLeadingFabWidth).coerceAtLeast(0)
                val leadingY = (stripHeight - scaledLeadingFabHeight) / 2
                leadingPlaceable.placeWithLayer(leadingX, leadingY) {
                    scaleX = chromeScale
                    scaleY = chromeScale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
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
            RememberMaterialRoundedSymbol(
                name = item.symbolName,
                size = 24.dp,
                tint = LocalContentColor.current,
                weight = FontWeight.Medium,
                filled = selected,
            )
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
    // Follows the FAB size rather than the window on its own: this slot's buttons stay full size in
    // the single-pane chrome strip, so their glyphs have to as well.
    val useCompactControls = isSmallLandscape() && LocalAllowCompactControls.current

    when (tab) {
        MainTab.Notes ->
            NotesCreateFabMenu(
                expanded = fabExpanded,
                onToggle = onToggleNotesFab,
                onPickImport = onPickImport,
                onPickList = onPickList,
                onPickNote = onPickNote,
            )
        MainTab.History -> {
            val isArchive = historySection == HistorySection.ARCHIVE
            val symbolName = if (isArchive) "delete_sweep" else "delete_forever"
            val description =
                if (isArchive) {
                    stringResource(R.string.common_move_to_trash)
                } else {
                    stringResource(R.string.edit_bottom_bar_delete_forever)
                }
            val iconSize =
                if (useCompactControls || !isArchive) {
                    22.dp
                } else {
                    24.dp
                }
            // Kept mounted (just disabled) even on short landscape windows, so the chrome has the
            // same two FABs on every tab instead of the slot emptying out when the list is empty.
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
                iconSize = if (useCompactControls) 22.dp else 26.dp,
                onClick = onShareApp,
            )
    }
}

/**
 * Padding M3's `FloatingActionButtonMenu` leaves outside its visible button: it applies
 * `padding(horizontal = FabMenuPaddingHorizontal)` to itself and places the button
 * `FabMenuButtonPaddingBottom` above its own bottom edge. Both are 16.dp, and both are inside
 * [NotesCreateFabMenu]'s measured size, so [CenteredPillWithSideFab] must cancel them to line the
 * button up with the nav pill the way the bare FABs on the other tabs already do.
 */
private val NotesFabMenuButtonInset = 16.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NotesCreateFabMenu(
    expanded: Boolean,
    onToggle: () -> Unit,
    onPickImport: () -> Unit,
    onPickList: () -> Unit,
    onPickNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val createDescription = stringResource(R.string.main_fab_create)
    val closeDescription = stringResource(R.string.main_fab_close)
    val description = if (expanded) closeDescription else createDescription
    val motionScheme = MaterialTheme.motionScheme
    val iconColor by animateColorAsState(
        targetValue =
            if (expanded) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
        animationSpec = reducedMotionAwareSpec(motionScheme.defaultEffectsSpec()),
        label = "notes_fab_icon_color",
    )
    val spatialSpec = reducedMotionAwareSpec(motionScheme.defaultSpatialSpec<Float>())
    val fabProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spatialSpec,
        label = "notes_fab_progress",
    )
    val fabMorph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny) }
    val fabShape = remember(fabMorph, fabProgress) { MorphPolygonShape(fabMorph, fabProgress) }
    val iconRotation by animateFloatAsState(
        targetValue = if (expanded) 135f else 0f,
        animationSpec = reducedMotionAwareSpec(motionScheme.defaultSpatialSpec()),
        label = "notes_fab_icon_rotation",
    )
    Box(
        modifier = modifier.semantics { contentDescription = description },
        contentAlignment = Alignment.BottomEnd,
    ) {
        FloatingActionButtonMenu(
            expanded = expanded,
            button = {
                // Compact only where the host chrome is compact: this menu is used both in the
                // single-pane strip (full size) and in the two-pane list pane (compact).
                val useCompactControls = isSmallLandscape() && LocalAllowCompactControls.current
                val containerColor =
                    if (expanded) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                RememberFloatingActionButton(
                    onClick = onToggle,
                    modifier =
                        Modifier
                            .shadow(
                                elevation =
                                    animateDpAsState(
                                        targetValue = if (expanded) 2.dp else 4.dp,
                                        animationSpec = reducedMotionAwareSpec(motionScheme.defaultSpatialSpec()),
                                        label = "notes_fab_elevation",
                                    ).value,
                                shape = fabShape,
                                clip = false,
                            ).clip(fabShape)
                            .semantics { contentDescription = description },
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "add",
                        size = if (useCompactControls) 22.dp else 26.dp,
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
