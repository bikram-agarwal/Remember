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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
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
import dev.bikram.remember.ui.common.isLandscape
import dev.bikram.remember.ui.common.isSmallLandscape
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.RememberPredictiveBackHandler
import dev.bikram.remember.ui.common.rememberShareAppAction
import dev.bikram.remember.ui.components.AlertChromeSummary
import dev.bikram.remember.ui.components.AlertFloatingActionButtonMenu
import dev.bikram.remember.ui.components.RememberFloatingActionButton
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.components.RememberConfirmDialog
import dev.bikram.remember.ui.components.UpdateChromeState
import dev.bikram.remember.ui.edit.DEFAULT_LIST_HEADER_SYMBOL
import dev.bikram.remember.ui.edit.DEFAULT_NOTE_HEADER_SYMBOL
import dev.bikram.remember.ui.feedback.tapSoundClickable
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
    val navAnimatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val chromeTargetVisible =
        navAnimatedVisibilityScope?.transition?.targetState == EnterExitState.Visible
    val effectiveChromeVisible = chromeVisible && (navAnimatedVisibilityScope == null || chromeTargetVisible)
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
                            .tapSoundClickable(
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
                    val notesFabBottomInset =
                        if (currentTab == MainTab.Notes) {
                            if (isLandscape) 8.dp else 16.dp
                        } else {
                            0.dp
                        }
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
                        fabGap = 12.dp,
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
                        fabRightInset = if (currentTab == MainTab.Notes) 16.dp else 0.dp,
                        fabBottomInset = notesFabBottomInset,
                        leadingFabGap = 18.dp,
                        leadingFabBottomInset = if (isSmallLandscape) notesFabBottomInset else 0.dp,
                    )
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
        NavigationSuiteScaffold(
            layoutType = NavigationSuiteType.NavigationRail,
            containerColor = Color.Transparent,
            navigationSuiteItems = {
                MainTab.entries.forEach { tabItem ->
                    item(
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
            },
        ) {
            mainContent()
        }
    } else {
        mainContent()
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
    modifier: Modifier = Modifier,
    leadingFab: (@Composable () -> Unit)? = null,
    fabCoreSize: androidx.compose.ui.unit.Dp = 56.dp,
    fabRightInset: androidx.compose.ui.unit.Dp = 0.dp,
    fabBottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    leadingFabGap: androidx.compose.ui.unit.Dp = fabGap,
    leadingFabLeftInset: androidx.compose.ui.unit.Dp = 0.dp,
    leadingFabBottomInset: androidx.compose.ui.unit.Dp = 0.dp,
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
        val leadingGapPx = leadingFabGap.roundToPx()
        val fabCorePx = fabCoreSize.roundToPx()
        val fabRightInsetPx = fabRightInset.roundToPx()
        val fabBottomInsetPx = fabBottomInset.roundToPx()
        val leadingFabLeftInsetPx = leadingFabLeftInset.roundToPx()
        val leadingFabBottomInsetPx = leadingFabBottomInset.roundToPx()

        val width =
            if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                pillPlaceable.width + gapPx + fabPlaceable.width
            }
        val trailingSideFootprintPx =
            if (fabRightInsetPx > 0 || fabBottomInsetPx > 0) {
                fabCorePx
            } else {
                maxOf(fabCorePx, fabPlaceable.width)
            }
        val leadingSideFootprintPx =
            if (leadingFabPlaceable != null) {
                if (leadingFabLeftInsetPx > 0 || leadingFabBottomInsetPx > 0) {
                    fabCorePx
                } else {
                    maxOf(fabCorePx, leadingFabPlaceable.width)
                }
            } else {
                0
            }
        val sideRoomPx = maxOf(gapPx + trailingSideFootprintPx, leadingGapPx + leadingSideFootprintPx)
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
        val scaledFabRightInset = (fabRightInsetPx * chromeScale).roundToInt()
        val scaledFabBottomInset = (fabBottomInsetPx * chromeScale).roundToInt()
        val scaledLeadingGap = (leadingGapPx * chromeScale).roundToInt()
        val scaledLeadingFabLeftInset = (leadingFabLeftInsetPx * chromeScale).roundToInt()
        val scaledLeadingFabBottomInset = (leadingFabBottomInsetPx * chromeScale).roundToInt()
        val stripHeight = maxOf(scaledPillHeight, scaledFabCore)

        layout(width, stripHeight) {
            val pillX = (width - scaledPillWidth) / 2
            val pillY = (stripHeight - scaledPillHeight) / 2
            pillPlaceable.placeWithLayer(pillX, pillY) {
                scaleX = chromeScale
                scaleY = chromeScale
                transformOrigin = TransformOrigin(0f, 0f)
            }

            val fabX =
                if (scaledFabRightInset > 0 || scaledFabBottomInset > 0) {
                    val desiredFabElementRight = pillX + scaledPillWidth + scaledGap + scaledFabCore
                    val fabElementRight = desiredFabElementRight.coerceAtMost(width)
                    (fabElementRight - scaledFabWidth + scaledFabRightInset).coerceAtLeast(0)
                } else {
                    (pillX + scaledPillWidth + scaledGap)
                        .coerceAtMost(width - scaledFabWidth)
                        .coerceAtLeast(0)
                }
            val fabBottomY = (stripHeight + scaledFabCore) / 2 + scaledFabBottomInset
            val fabY = fabBottomY - scaledFabHeight
            fabPlaceable.placeWithLayer(fabX, fabY) {
                scaleX = chromeScale
                scaleY = chromeScale
                transformOrigin = TransformOrigin(0f, 0f)
            }

            leadingFabPlaceable?.let { leadingPlaceable ->
                val scaledLeadingFabWidth = (leadingPlaceable.width * chromeScale).roundToInt()
                val leadingX =
                    if (scaledLeadingFabLeftInset > 0 || scaledLeadingFabBottomInset > 0) {
                        val leadingFabElementLeft =
                            (pillX - scaledLeadingGap - scaledFabCore).coerceAtLeast(0)
                        (leadingFabElementLeft - scaledLeadingFabLeftInset).coerceAtLeast(0)
                    } else {
                        (pillX - scaledLeadingGap - scaledLeadingFabWidth).coerceAtLeast(0)
                    }
                val leadingFabBottomY = (stripHeight + scaledFabCore) / 2 + scaledLeadingFabBottomInset
                val leadingY =
                    leadingFabBottomY -
                        (leadingPlaceable.height * chromeScale).roundToInt()
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
    val isLandscape = isLandscape()
    val isSmallLandscape = isSmallLandscape()

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
                if (isSmallLandscape || !isArchive) {
                    22.dp
                } else {
                    24.dp
                }
            val shouldShowFab =
                if (isSmallLandscape) {
                    historyVisibleItemCount > 0
                } else {
                    true
                }

            if (shouldShowFab) {
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
        }
        MainTab.Settings ->
            SimpleHistoryOrSettingsFab(
                symbolName = "share",
                description = stringResource(R.string.main_menu_share_app),
                enabled = true,
                iconSize = if (isSmallLandscape) 22.dp else 26.dp,
                onClick = onShareApp,
            )
    }
}

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
                val isLandscape = isLandscape()
                val isSmallLandscape = isSmallLandscape()
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
                        size = if (isSmallLandscape) 22.dp else 26.dp,
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
