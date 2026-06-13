package dev.bikram.remember.ui.nav

import androidx.activity.ComponentActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.PaneExpansionStateKey
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.EntryPointAccessors
import dev.bikram.remember.R
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.di.SettingsDependenciesEntryPoint
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.common.isLandscape
import dev.bikram.remember.ui.common.isSmallLandscape
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberButton
import dev.bikram.remember.ui.components.RememberFilledTonalButton
import dev.bikram.remember.ui.components.RememberFloatingActionButton
import dev.bikram.remember.ui.components.RememberOutlinedButton
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.edit.EditListRoute
import dev.bikram.remember.ui.edit.EditNoteRoute
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.history.HistoryRoute
import dev.bikram.remember.ui.history.HistorySection
import dev.bikram.remember.ui.history.HistoryViewModel
import dev.bikram.remember.ui.home.BulkTagSheet
import dev.bikram.remember.ui.home.HomeListItem
import dev.bikram.remember.ui.home.HomeRoute
import dev.bikram.remember.ui.home.HomeViewModel
import dev.bikram.remember.ui.home.buildBulkTagCoverage
import dev.bikram.remember.ui.main.NotesCreateFabMenu
import dev.bikram.remember.ui.settings.AboutSection
import dev.bikram.remember.ui.settings.DevOptionsRoute
import dev.bikram.remember.ui.settings.RememberUpdateViewModel
import dev.bikram.remember.ui.settings.SettingsRoute
import dev.bikram.remember.ui.settings.SettingsSectionKey
import dev.bikram.remember.ui.settings.settingsPaneSections
import dev.bikram.remember.ui.settings.settingsSectionKeyForHighlight
import dev.bikram.remember.ui.theme.RoundedPolygonShape
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val NEW_NOTE_DETAIL_ID = -10L
private const val NEW_LIST_DETAIL_ID = -11L

@Composable
fun NotesTwoPaneRoute(
    interactionPrefs: InteractionPrefs,
    appScope: CoroutineScope,
    closeRevealRequest: Int,
    onOpenIntro: () -> Unit,
    onImportGoogleTasks: () -> Unit,
    onOpenNoteInSinglePane: (NoteWithItems, Boolean) -> Unit,
    onCreateNoteInSinglePane: () -> Unit,
    onCreateListInSinglePane: () -> Unit,
    onRegisterCreateNoteInPane: ((() -> Unit)?) -> Unit,
    onRegisterCreateListInPane: ((() -> Unit)?) -> Unit,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Long>()
    val isMultiPane = navigator.scaffoldDirective.maxHorizontalPartitions > 1
    val isLandscape = isLandscape()

    if (!isMultiPane) {
        HomeRoute(
            interactionPrefs = interactionPrefs,
            closeRevealRequest = closeRevealRequest,
            onOpenNote = onOpenNoteInSinglePane,
            onCreateNote = onCreateNoteInSinglePane,
            onCreateList = onCreateListInSinglePane,
        )
        return
    }

    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var activeDetailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var activeDetailKind by rememberSaveable { mutableStateOf(NoteKind.NOTE) }
    var activeDetailForceEdit by rememberSaveable { mutableStateOf(false) }
    var previousDetailIdBeforeNew by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingSavedNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var tagSheetOpen by rememberSaveable { mutableStateOf(false) }
    var createFabExpanded by rememberSaveable { mutableStateOf(false) }
    val visibleNotes =
        remember(state.items) {
            state.items.mapNotNull { item ->
                (item as? HomeListItem.NoteRow)?.note
            }
        }
    // Archived/trashed search hits live outside state.items but are tappable in the
    // list pane, so the detail pane must be allowed to keep hosting them.
    val searchShelfIds =
        remember(state.archivedMatches, state.trashedMatches) {
            buildSet {
                state.archivedMatches.forEach { match -> add(match.note.id) }
                state.trashedMatches.forEach { match -> add(match.note.id) }
            }
        }

    fun showNoteInDetailPane(
        note: NoteWithItems,
        forceEdit: Boolean = false,
    ) {
        activeDetailId = note.note.id
        activeDetailKind = note.note.kind
        activeDetailForceEdit = forceEdit
        previousDetailIdBeforeNew = null
        pendingSavedNoteId = null
        scope.launch {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, note.note.id)
        }
    }

    fun showNewDetailPane(kind: NoteKind) {
        previousDetailIdBeforeNew = activeDetailId
        activeDetailKind = kind
        activeDetailForceEdit = false
        activeDetailId =
            when (kind) {
                NoteKind.NOTE -> NEW_NOTE_DETAIL_ID
                NoteKind.LIST -> NEW_LIST_DETAIL_ID
            }
        scope.launch {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, activeDetailId)
        }
    }

    DisposableEffect(onRegisterCreateNoteInPane, onRegisterCreateListInPane) {
        onRegisterCreateNoteInPane { showNewDetailPane(NoteKind.NOTE) }
        onRegisterCreateListInPane { showNewDetailPane(NoteKind.LIST) }
        onDispose {
            onRegisterCreateNoteInPane(null)
            onRegisterCreateListInPane(null)
        }
    }

    fun closePendingNewDetail() {
        val visibleIds = visibleNotes.map { note -> note.note.id }
        val fallbackId =
            when {
                previousDetailIdBeforeNew != null && previousDetailIdBeforeNew in visibleIds -> previousDetailIdBeforeNew
                visibleNotes.isNotEmpty() -> visibleNotes.first().note.id
                else -> null
            }
        val fallbackNote = visibleNotes.firstOrNull { note -> note.note.id == fallbackId }
        previousDetailIdBeforeNew = null
        activeDetailId = fallbackId
        activeDetailKind = fallbackNote?.note?.kind ?: NoteKind.NOTE
        scope.launch {
            if (fallbackId == null) {
                navigator.navigateBack()
            } else {
                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, fallbackId)
            }
        }
    }

    // No event collector here: the HomeRoute hosted in the list pane shares this
    // ViewModel and already handles OpenNote (via onOpenNote) and bulk-action
    // snackbars. A second collector would double-handle every event.

    LaunchedEffect(visibleNotes, activeDetailId, searchShelfIds) {
        val visibleIds = visibleNotes.map { note -> note.note.id }
        val currentDetailId = activeDetailId
        if (pendingSavedNoteId != null && pendingSavedNoteId in visibleIds) {
            pendingSavedNoteId = null
            previousDetailIdBeforeNew = null
        }
        val targetNote =
            when {
                currentDetailId == NEW_NOTE_DETAIL_ID || currentDetailId == NEW_LIST_DETAIL_ID -> null
                currentDetailId != null && currentDetailId == pendingSavedNoteId -> null
                currentDetailId != null -> visibleNotes.firstOrNull { note -> note.note.id == currentDetailId }
                else -> visibleNotes.firstOrNull()
            }
        if (targetNote != null && targetNote.note.id != currentDetailId) {
            activeDetailId = targetNote.note.id
            activeDetailKind = targetNote.note.kind
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, targetNote.note.id)
        } else if (currentDetailId != null &&
            currentDetailId > 0 &&
            // A just-saved note may not have landed in the Room flow yet; don't treat
            // it as deleted and steal its selection.
            currentDetailId != pendingSavedNoteId &&
            currentDetailId !in visibleIds &&
            // Archived/trashed search hits stay hosted while their list rows exist.
            currentDetailId !in searchShelfIds
        ) {
            val fallbackNote = visibleNotes.firstOrNull()
            activeDetailId = fallbackNote?.note?.id
            activeDetailKind = fallbackNote?.note?.kind ?: NoteKind.NOTE
            if (fallbackNote != null) {
                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, fallbackNote.note.id)
            } else {
                navigator.navigateBack()
            }
        }
    }

    val paneExpansionState =
        rememberFlatScreenBalancedPaneExpansionState(
            directive = navigator.scaffoldDirective,
        )
    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        scaffoldState = navigator.scaffoldState,
        listPane = {
            AnimatedPane {
                Box(Modifier.fillMaxSize()) {
                    HomeRoute(
                        interactionPrefs = interactionPrefs,
                        closeRevealRequest = closeRevealRequest,
                        onOpenNote = { note, forceEdit -> showNoteInDetailPane(note, forceEdit) },
                        onCreateNote = { showNewDetailPane(NoteKind.NOTE) },
                        onCreateList = { showNewDetailPane(NoteKind.LIST) },
                        activeNoteId = activeDetailId?.takeIf { it > 0L },
                        pendingNewNoteKind =
                            when (activeDetailId) {
                                NEW_NOTE_DETAIL_ID -> NoteKind.NOTE
                                NEW_LIST_DETAIL_ID -> NoteKind.LIST
                                else -> null
                            },
                        showSelectionActionBar = false,
                    )
                    // Touch catcher under the FAB menu: tapping the list while the speed
                    // dial is open collapses it instead of activating the tap target,
                    // matching the phone scrim in MainTabScaffold.
                    if (createFabExpanded) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .tapSoundClickable(onClick = { createFabExpanded = false }),
                        )
                    }
                    NotesListPaneFabMenu(
                        expanded = createFabExpanded,
                        onExpandedChange = { expanded -> createFabExpanded = expanded },
                        onImportGoogleTasks = onImportGoogleTasks,
                        onCreateList = { showNewDetailPane(NoteKind.LIST) },
                        onCreateNote = { showNewDetailPane(NoteKind.NOTE) },
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                // FloatingActionButtonMenu pads its button 16dp from the
                                // wrapper's edges; 4dp lands the FAB element on the same
                                // 20dp baseline as the History/Settings pane FABs.
                                .padding(
                                    end = 4.dp,
                                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + (if (isLandscape) 0.dp else 4.dp),
                                ),
                    )
                }
            }
        },
        detailPane = {
            AnimatedPane {
                val selectedDetailId = activeDetailId
                Box(Modifier.fillMaxSize()) {
                    if (state.selectedIds.isNotEmpty()) {
                        NotesSelectionActionPane(
                            selectedCount = state.selectedIds.size,
                            totalVisibleCount = visibleNotes.size,
                            onSelectAll = {
                                viewModel.selectNotes(visibleNotes.map { note -> note.note.id }.toSet())
                            },
                            onClearSelection = viewModel::clearSelection,
                            onTagSelected = { tagSheetOpen = true },
                            onMarkDoneSelected = viewModel::markSelectedDone,
                            onArchiveSelected = viewModel::archiveSelected,
                            onTrashSelected = viewModel::trashSelected,
                        )
                    } else if (selectedDetailId == null ||
                        // Never trust activeDetailId alone: the pane may only host notes
                        // still reachable from the list pane (active rows, search shelf
                        // hits), placeholders, or a just-saved note awaiting the Room
                        // emission. Anything else (e.g. a note that was bulk-archived
                        // while open) immediately falls back to the empty state instead
                        // of lingering on screen.
                        !(
                            selectedDetailId < 0L ||
                                selectedDetailId == pendingSavedNoteId ||
                                selectedDetailId in searchShelfIds ||
                                visibleNotes.any { note -> note.note.id == selectedDetailId }
                        )
                    ) {
                        AboutEmptyDetailPane(onOpenIntro = onOpenIntro)
                    } else {
                        NoteDetailPaneHost(
                            detailId = selectedDetailId,
                            detailKind = activeDetailKind,
                            forceEdit = activeDetailForceEdit,
                            appScope = appScope,
                            onPersistedNoteId = { noteId, kind ->
                                if (activeDetailId == NEW_NOTE_DETAIL_ID ||
                                    activeDetailId == NEW_LIST_DETAIL_ID ||
                                    activeDetailId != noteId
                                ) {
                                    activeDetailId = noteId
                                    activeDetailKind = kind
                                    pendingSavedNoteId = noteId
                                    previousDetailIdBeforeNew = null
                                    scope.launch {
                                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, noteId)
                                    }
                                }
                            },
                            onBack = {
                                // Read activeDetailId live: a save-and-back reports the persisted
                                // id (switching the selection) before this runs, and the pending
                                // placeholder must only be closed when nothing was saved.
                                if (activeDetailId == NEW_NOTE_DETAIL_ID || activeDetailId == NEW_LIST_DETAIL_ID) {
                                    closePendingNewDetail()
                                }
                            },
                        )
                    }
                    // Companion touch catcher: taps in the detail pane also collapse the
                    // open speed dial instead of reaching the editor.
                    if (createFabExpanded) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .tapSoundClickable(onClick = { createFabExpanded = false }),
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        paneExpansionState = paneExpansionState,
    )

    if (tagSheetOpen) {
        val bulkTagCoverage =
            remember(visibleNotes, state.availableTags, state.selectedIds) {
                buildBulkTagCoverage(
                    availableTags = state.availableTags,
                    selectedNoteTags =
                        visibleNotes
                            .filter { note -> note.note.id in state.selectedIds }
                            .map { note -> note.note.tags },
                )
            }
        BulkTagSheet(
            tagCoverage = bulkTagCoverage,
            selectedNoteCount = state.selectedIds.size,
            onApply = viewModel::applyTagsToSelection,
            onDismiss = { tagSheetOpen = false },
        )
    }
}

@Composable
fun SettingsTwoPaneRoute(
    onOpenIntro: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenDevOptions: () -> Unit,
    updateVm: RememberUpdateViewModel,
    onUpdateCheckStarted: () -> Unit,
    onShareApp: () -> Unit,
    highlightSectionKey: String?,
    onHighlightHandled: () -> Unit,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val isMultiPane = navigator.scaffoldDirective.maxHorizontalPartitions > 1
    val isLandscape = isLandscape()
    val isSmallLandscape = isSmallLandscape()

    if (!isMultiPane) {
        SettingsRoute(
            onOpenIntro = onOpenIntro,
            onOpenHelp = onOpenHelp,
            onOpenDevOptions = onOpenDevOptions,
            updateVm = updateVm,
            onUpdateCheckStarted = onUpdateCheckStarted,
            highlightSectionKey = highlightSectionKey,
            onHighlightHandled = onHighlightHandled,
        )
        return
    }

    val context = LocalContext.current
    val settingsDependencies =
        remember(context) {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                SettingsDependenciesEntryPoint::class.java,
            )
        }
    val devModePrefs = settingsDependencies.devModePrefs()
    val devModeEnabled by devModePrefs.isEnabled.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    var selectedSectionKey by rememberSaveable { mutableStateOf(SettingsSectionKey.Appearance) }

    fun showSection(sectionKey: SettingsSectionKey) {
        selectedSectionKey = sectionKey
        scope.launch {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, sectionKey.routeKey)
        }
    }

    LaunchedEffect(highlightSectionKey) {
        val sectionKey = settingsSectionKeyForHighlight(highlightSectionKey) ?: return@LaunchedEffect
        showSection(sectionKey)
    }
    LaunchedEffect(devModeEnabled, selectedSectionKey) {
        if (!devModeEnabled && selectedSectionKey == SettingsSectionKey.DevOptions) {
            showSection(SettingsSectionKey.About)
        }
    }

    val paneExpansionState =
        rememberFlatScreenBalancedPaneExpansionState(
            directive = navigator.scaffoldDirective,
        )
    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        scaffoldState = navigator.scaffoldState,
        listPane = {
            AnimatedPane {
                Box(Modifier.fillMaxSize()) {
                    SettingsSectionListPane(
                        selectedSectionKey = selectedSectionKey,
                        developerOptionsEnabled = devModeEnabled,
                        onSectionSelected = ::showSection,
                    )
                    SimplePaneFab(
                        symbolName = "share",
                        description = stringResource(R.string.main_menu_share_app),
                        enabled = true,
                        iconSize = if (isSmallLandscape) 22.dp else 26.dp,
                        onClick = onShareApp,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .padding(
                                    end = 20.dp,
                                    bottom = if (isSmallLandscape) 10.dp else 20.dp,
                                ),
                    )
                }
            }
        },
        detailPane = {
            AnimatedPane {
                Box(Modifier.fillMaxSize()) {
                    if (selectedSectionKey == SettingsSectionKey.DevOptions) {
                        DevOptionsRoute(
                            onBack = { showSection(SettingsSectionKey.About) },
                            showNavigateBack = false,
                        )
                    } else {
                        SettingsRoute(
                            onOpenIntro = onOpenIntro,
                            onOpenHelp = onOpenHelp,
                            onOpenDevOptions = {
                                showSection(SettingsSectionKey.DevOptions)
                            },
                            updateVm = updateVm,
                            onUpdateCheckStarted = onUpdateCheckStarted,
                            selectedSectionKey = selectedSectionKey,
                            showTopActions = false,
                            showSectionHeaders = false,
                            showAboutHeaderTitle = false,
                            highlightSectionKey = highlightSectionKey,
                            onHighlightHandled = onHighlightHandled,
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        paneExpansionState = paneExpansionState,
    )
}

@Composable
fun HistoryTwoPaneRoute(
    interactionPrefs: InteractionPrefs,
    appScope: CoroutineScope,
    onOpenIntro: () -> Unit,
    section: HistorySection,
    onSectionChange: (HistorySection) -> Unit,
    onVisibleItemCountChange: (Int) -> Unit,
    onOpenNoteInSinglePane: (NoteWithItems, Boolean) -> Unit,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Long>()
    val isMultiPane = navigator.scaffoldDirective.maxHorizontalPartitions > 1
    val isLandscape = isLandscape()

    if (!isMultiPane) {
        HistoryRoute(
            interactionPrefs = interactionPrefs,
            section = section,
            onSectionChange = onSectionChange,
            onVisibleItemCountChange = onVisibleItemCountChange,
            onOpenNote = onOpenNoteInSinglePane,
        )
        return
    }

    val viewModel: HistoryViewModel = hiltViewModel()
    val archived by viewModel.archivedItems.collectAsStateWithLifecycle()
    val trashed by viewModel.trashedItems.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val visibleNotes =
        when (section) {
            HistorySection.ARCHIVE -> archived
            HistorySection.TRASH -> trashed
        }
    val scope = rememberCoroutineScope()
    var activeDetailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var activeDetailKind by rememberSaveable { mutableStateOf(NoteKind.NOTE) }
    var activeDetailForceEdit by rememberSaveable { mutableStateOf(false) }
    var clearTrashOpen by rememberSaveable { mutableStateOf(false) }
    var moveArchiveToTrashOpen by rememberSaveable { mutableStateOf(false) }
    var bulkDeleteForeverOpen by rememberSaveable { mutableStateOf(false) }

    fun showNoteInDetailPane(
        note: NoteWithItems,
        forceEdit: Boolean = false,
    ) {
        activeDetailId = note.note.id
        activeDetailKind = note.note.kind
        activeDetailForceEdit = forceEdit
        scope.launch {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, note.note.id)
        }
    }

    // No event collector here: the HistoryRoute hosted in the list pane shares this
    // ViewModel and already shows the bulk-action snackbar. A second collector would
    // show every snackbar twice.

    LaunchedEffect(section) {
        activeDetailId = null
    }

    LaunchedEffect(visibleNotes, activeDetailId) {
        val visibleIds = visibleNotes.map { note -> note.note.id }
        val currentDetailId = activeDetailId
        val targetNote =
            when {
                currentDetailId != null -> visibleNotes.firstOrNull { note -> note.note.id == currentDetailId }
                else -> visibleNotes.firstOrNull()
            }
        if (targetNote != null && targetNote.note.id != currentDetailId) {
            activeDetailId = targetNote.note.id
            activeDetailKind = targetNote.note.kind
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, targetNote.note.id)
        } else if (currentDetailId != null && currentDetailId !in visibleIds) {
            val fallbackNote = visibleNotes.firstOrNull()
            activeDetailId = fallbackNote?.note?.id
            activeDetailKind = fallbackNote?.note?.kind ?: NoteKind.NOTE
            if (fallbackNote != null) {
                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, fallbackNote.note.id)
            } else {
                navigator.navigateBack()
            }
        }
    }

    val paneExpansionState =
        rememberFlatScreenBalancedPaneExpansionState(
            directive = navigator.scaffoldDirective,
        )
    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        scaffoldState = navigator.scaffoldState,
        listPane = {
            AnimatedPane {
                Box(Modifier.fillMaxSize()) {
                    HistoryRoute(
                        interactionPrefs = interactionPrefs,
                        section = section,
                        onSectionChange = onSectionChange,
                        onVisibleItemCountChange = onVisibleItemCountChange,
                        onOpenNote = { note, forceEdit -> showNoteInDetailPane(note, forceEdit) },
                        activeNoteId = activeDetailId,
                        showSelectionActionBar = false,
                    )
                    val isSmallLandscape = isSmallLandscape()
                    val shouldShowPaneFab =
                        if (isSmallLandscape) {
                            visibleNotes.isNotEmpty()
                        } else {
                            true
                        }
                    if (shouldShowPaneFab) {
                        HistoryPaneFab(
                            section = section,
                            visibleItemCount = visibleNotes.size,
                            useCompactIcon = isSmallLandscape,
                            onMoveArchiveToTrashRequest = { moveArchiveToTrashOpen = true },
                            onClearTrashRequest = { clearTrashOpen = true },
                            modifier =
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(
                                        end = 20.dp,
                                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + (if (isLandscape) 10.dp else 20.dp),
                                    ),
                        )
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                val selectedDetailId = activeDetailId
                if (selectedIds.isNotEmpty()) {
                    HistorySelectionActionPane(
                        section = section,
                        selectedCount = selectedIds.size,
                        totalVisibleCount = visibleNotes.size,
                        onSelectAll = {
                            viewModel.selectNotes(visibleNotes.map { note -> note.note.id }.toSet())
                        },
                        onClearSelection = viewModel::clearSelection,
                        onRestoreSelected = viewModel::restoreSelected,
                        onArchiveSelected = viewModel::archiveSelectedFromTrash,
                        onUnarchiveSelected = viewModel::unarchiveSelected,
                        onTrashSelected = viewModel::moveSelectedArchivedToTrash,
                        // Permanent delete is gated by the confirmation sheet below, matching
                        // the single-pane action bar.
                        onDeleteForeverSelected = { bulkDeleteForeverOpen = true },
                    )
                } else if (selectedDetailId == null ||
                    // Same guard as the Notes route: only host notes still present in the
                    // current section so bulk restore/delete can't leave a stale editor.
                    visibleNotes.none { note -> note.note.id == selectedDetailId }
                ) {
                    AboutEmptyDetailPane(onOpenIntro = onOpenIntro)
                } else {
                    NoteDetailPaneHost(
                        detailId = selectedDetailId,
                        detailKind = activeDetailKind,
                        forceEdit = activeDetailForceEdit,
                        appScope = appScope,
                        onPersistedNoteId = { noteId, kind ->
                            if (activeDetailId != noteId) {
                                activeDetailId = noteId
                                activeDetailKind = kind
                                scope.launch {
                                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, noteId)
                                }
                            }
                        },
                        onBack = {},
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        paneExpansionState = paneExpansionState,
    )

    if (clearTrashOpen) {
        AppBottomSheet(
            title = stringResource(R.string.main_empty_trash_title),
            subtitle = stringResource(R.string.main_empty_trash_subtitle),
            onDismiss = { clearTrashOpen = false },
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
            subtitleSpacing = 12.dp,
            actions = {
                RememberTextButton(onClick = { clearTrashOpen = false }) { Text(stringResource(R.string.common_cancel)) }
                RememberTextButton(
                    onClick = {
                        clearTrashOpen = false
                        viewModel.emptyTrash()
                    },
                ) {
                    Text(stringResource(R.string.common_empty))
                }
            },
        ) {
            // No body content - subtitle covers the warning.
        }
    }

    if (bulkDeleteForeverOpen) {
        // Mirrors the single-pane action bar's confirmation so two-pane mode never
        // permanently deletes without asking.
        AppBottomSheet(
            title = stringResource(R.string.bulk_delete_forever_confirm_title),
            subtitle = stringResource(R.string.bulk_delete_forever_confirm_subtitle),
            onDismiss = { bulkDeleteForeverOpen = false },
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
            subtitleSpacing = 12.dp,
            actions = {
                RememberTextButton(onClick = { bulkDeleteForeverOpen = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
                RememberTextButton(
                    onClick = {
                        bulkDeleteForeverOpen = false
                        viewModel.deleteSelectedForever()
                    },
                ) {
                    Text(stringResource(R.string.edit_bottom_bar_delete_forever))
                }
            },
        ) {
            // The subtitle covers the warning fully; no extra body content.
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
                RememberTextButton(
                    onClick = {
                        moveArchiveToTrashOpen = false
                        viewModel.moveAllArchivedToTrash()
                    },
                ) {
                    Text(stringResource(R.string.edit_bottom_bar_trash))
                }
            },
        ) {
            // No body content - subtitle covers the retention warning.
        }
    }
}

@Composable
private fun NoteDetailPaneHost(
    detailId: Long,
    detailKind: NoteKind,
    forceEdit: Boolean,
    appScope: CoroutineScope,
    onPersistedNoteId: (Long, NoteKind) -> Unit,
    onBack: () -> Unit,
) {
    val noteId = detailId.takeIf { it > 0L }
    val route =
        Routes.editContent(
            id = noteId,
            type = detailKind,
        )
    // forceEdit joins the key so a swipe-Edit on the already-selected note reopens its
    // host in edit mode; unsaved edits are flushed by the editors' dispose autosave.
    key(route, forceEdit) {
        val detailNavController = rememberNavController()
        NavHost(
            navController = detailNavController,
            startDestination = route,
        ) {
            composable(
                route = "${Routes.EDIT_CONTENT}?${Routes.ARG_ID}={${Routes.ARG_ID}}&${Routes.ARG_TYPE}={${Routes.ARG_TYPE}}&${Routes.ARG_PREFILL}={${Routes.ARG_PREFILL}}&${Routes.ARG_FORCE_EDIT}={${Routes.ARG_FORCE_EDIT}}&${Routes.ARG_EXIT_ON_BACK}={${Routes.ARG_EXIT_ON_BACK}}",
                arguments =
                    listOf(
                        navArgument(Routes.ARG_ID) {
                            type = NavType.LongType
                            defaultValue = -1L
                        },
                        navArgument(Routes.ARG_TYPE) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument(Routes.ARG_PREFILL) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument(Routes.ARG_FORCE_EDIT) {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                        navArgument(Routes.ARG_EXIT_ON_BACK) {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    ),
            ) {
                // Only a pending new note intercepts system back (back cancels the
                // placeholder); existing notes leave back to the system so the app
                // stays exitable while the editor lives permanently in this pane.
                when (detailKind) {
                    NoteKind.NOTE ->
                        EditNoteRoute(
                            appScope = appScope,
                            noteId = noteId,
                            forceEdit = forceEdit,
                            showNavigateBack = false,
                            allowInitialTitleFocus = false,
                            interceptBack = noteId == null,
                            onPersistedNoteIdChanged = { persistedNoteId ->
                                onPersistedNoteId(persistedNoteId, NoteKind.NOTE)
                            },
                            onBack = onBack,
                            onNavigateUp = onBack,
                        )
                    NoteKind.LIST ->
                        EditListRoute(
                            appScope = appScope,
                            noteId = noteId,
                            forceEdit = forceEdit,
                            showNavigateBack = false,
                            allowInitialTitleFocus = false,
                            interceptBack = noteId == null,
                            onPersistedNoteIdChanged = { persistedNoteId ->
                                onPersistedNoteId(persistedNoteId, NoteKind.LIST)
                            },
                            onBack = onBack,
                            onNavigateUp = onBack,
                        )
                }
            }
        }
    }
}

/**
 * Detail-pane filler when nothing is selected: the Settings About card, mirroring
 * FilePipe's two-pane filler. No explicit background so the shared app background
 * (gradient or black) shows through and both panes match.
 */
@Composable
private fun AboutEmptyDetailPane(onOpenIntro: () -> Unit) {
    val context = LocalContext.current
    val settingsDependencies =
        remember(context) {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                SettingsDependenciesEntryPoint::class.java,
            )
        }
    val devModePrefs = settingsDependencies.devModePrefs()
    val appReviewLauncher = remember(settingsDependencies) { settingsDependencies.appReviewLauncher() }
    val devModeEnabled by devModePrefs.isEnabled.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            // Full-bleed scroll with the insets/margins inside, like the selection panes:
            // short windows scroll edge-to-edge instead of clipping the card.
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(24.dp),
        ) {
            AboutSection(
                onOpenIntro = onOpenIntro,
                showHeader = false,
                devModeEnabled = devModeEnabled,
                onDevModeActivated = {
                    scope.launch { devModePrefs.setEnabled(true) }
                },
                onLaunchPlayReview = { onFlowFinished ->
                    val hostActivity = context as? ComponentActivity
                    if (hostActivity != null) {
                        appReviewLauncher.tryLaunchInAppReview(hostActivity, onFlowFinished)
                    } else {
                        onFlowFinished()
                    }
                },
            )
        }
    }
}

@Composable
private fun NotesListPaneFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onImportGoogleTasks: () -> Unit,
    onCreateList: () -> Unit,
    onCreateNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Same speed dial as phone mode (shape morph included) so the FAB does not change
    // appearance between single- and dual-pane layouts.
    NotesCreateFabMenu(
        expanded = expanded,
        onToggle = { onExpandedChange(!expanded) },
        onPickImport = {
            onExpandedChange(false)
            onImportGoogleTasks()
        },
        onPickList = {
            onExpandedChange(false)
            onCreateList()
        },
        onPickNote = {
            onExpandedChange(false)
            onCreateNote()
        },
        modifier = modifier,
    )
}

@Composable
private fun HistoryPaneFab(
    section: HistorySection,
    visibleItemCount: Int,
    useCompactIcon: Boolean,
    onMoveArchiveToTrashRequest: () -> Unit,
    onClearTrashRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isArchive = section == HistorySection.ARCHIVE
    SimplePaneFab(
        symbolName = if (isArchive) "delete_sweep" else "delete_forever",
        description =
            if (isArchive) {
                stringResource(R.string.common_move_to_trash)
            } else {
                stringResource(R.string.edit_bottom_bar_delete_forever)
            },
        enabled = visibleItemCount > 0,
        iconSize = if (useCompactIcon || !isArchive) 22.dp else 24.dp,
        onClick =
            if (isArchive) {
                onMoveArchiveToTrashRequest
            } else {
                onClearTrashRequest
            },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SimplePaneFab(
    symbolName: String,
    description: String,
    enabled: Boolean,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
    // RememberFloatingActionButton applies its modifier to the FAB inside a tooltip
    // wrapper, which would drop the caller's BoxScope.align parentData — anchor it on
    // a plain Box instead so pane placement actually takes effect.
    Box(modifier) {
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
}

@Composable
private fun SettingsSectionListPane(
    selectedSectionKey: SettingsSectionKey,
    developerOptionsEnabled: Boolean,
    onSectionSelected: (SettingsSectionKey) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 72.dp,
                // 56dp FAB + its 20dp bottom margin + 24dp clearance: the last row must
                // scroll above the share FAB instead of sitting underneath it.
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = settingsPaneSections,
            key = { sectionKey -> sectionKey.routeKey },
        ) { sectionKey ->
            SettingsPaneSectionRow(
                iconName = sectionKey.iconName,
                title = stringResource(sectionKey.titleRes),
                selected = selectedSectionKey == sectionKey,
                onClick = { onSectionSelected(sectionKey) },
            )
        }
        if (developerOptionsEnabled) {
            item(key = SettingsSectionKey.DevOptions.routeKey) {
                SettingsPaneSectionRow(
                    iconName = SettingsSectionKey.DevOptions.iconName,
                    title = stringResource(SettingsSectionKey.DevOptions.titleRes),
                    selected = selectedSectionKey == SettingsSectionKey.DevOptions,
                    onClick = { onSectionSelected(SettingsSectionKey.DevOptions) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsPaneSectionRow(
    iconName: String,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailingIconName: String = "chevron_right",
) {
    val colorSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Color>())
    val containerColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        animationSpec = colorSpec,
        label = "settings_section_list_container",
    )
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .tapSoundClickable(
                    onClick = onClick,
                    indication = null,
                ),
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(MaterialTheme.shapes.extraExtraLarge)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                RememberMaterialRoundedSymbol(
                    name = iconName,
                    size = 21.dp,
                    tint =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    weight = FontWeight.Medium,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            RememberMaterialRoundedSymbol(
                name = trailingIconName,
                size = 20.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                weight = FontWeight.Medium,
            )
        }
    }
}

// Mirrors FilePipe's RulesSelectionActionPane: a centered stack of full-width pill
// buttons (icon + label), tonal for regular actions, outlined for cancel, filled
// error for the destructive one. Keep the two apps' selection panes in sync.
private val SelectionPaneButtonShape = RoundedCornerShape(percent = 50)
private val SelectionPaneButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
private val CompactSelectionPaneButtonPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)

@Composable
private fun SelectionPaneButtonContent(
    iconName: String,
    label: String,
    compact: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberMaterialRoundedSymbol(
            name = iconName,
            size = if (compact) 18.dp else 20.dp,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun NotesSelectionActionPane(
    selectedCount: Int,
    totalVisibleCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onTagSelected: () -> Unit,
    onMarkDoneSelected: () -> Unit,
    onArchiveSelected: () -> Unit,
    onTrashSelected: () -> Unit,
) {
    val compact = isSmallLandscape()
    val buttonHeight = if (compact) 40.dp else 56.dp
    val buttonPadding = if (compact) CompactSelectionPaneButtonPadding else SelectionPaneButtonPadding
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            // The scroll container spans the whole pane and the insets/margins live
            // INSIDE it: on low-height windows the stack scrolls edge-to-edge instead
            // of being clipped at an invisible padded boundary (which reads as a solid
            // bar covering the bottom button).
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(if (compact) 12.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            Text(
                text = stringResource(R.string.two_pane_notes_selection_title, selectedCount),
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            RememberFilledTonalButton(
                onClick = onSelectAll,
                enabled = totalVisibleCount > 0 && selectedCount < totalVisibleCount,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(buttonHeight),
                shape = SelectionPaneButtonShape,
                contentPadding = buttonPadding,
            ) {
                SelectionPaneButtonContent("select_all", stringResource(R.string.home_select_all), compact)
            }
            RememberOutlinedButton(
                onClick = onClearSelection,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(buttonHeight),
                shape = SelectionPaneButtonShape,
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                contentPadding = buttonPadding,
            ) {
                SelectionPaneButtonContent("close", stringResource(R.string.home_unselect_all), compact)
            }
            RememberFilledTonalButton(
                onClick = onTagSelected,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(buttonHeight),
                shape = SelectionPaneButtonShape,
                contentPadding = buttonPadding,
            ) {
                SelectionPaneButtonContent("label", stringResource(R.string.home_bulk_tag), compact)
            }
            RememberFilledTonalButton(
                onClick = onMarkDoneSelected,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(buttonHeight),
                shape = SelectionPaneButtonShape,
                contentPadding = buttonPadding,
            ) {
                SelectionPaneButtonContent("check_circle", stringResource(R.string.edit_bottom_bar_mark_done), compact)
            }
            RememberFilledTonalButton(
                onClick = onArchiveSelected,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(buttonHeight),
                shape = SelectionPaneButtonShape,
                contentPadding = buttonPadding,
            ) {
                SelectionPaneButtonContent("archive", stringResource(R.string.edit_bottom_bar_archive), compact)
            }
            RememberButton(
                onClick = onTrashSelected,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(buttonHeight),
                shape = SelectionPaneButtonShape,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                contentPadding = buttonPadding,
            ) {
                SelectionPaneButtonContent("delete", stringResource(R.string.home_bulk_trash), compact)
            }
        }
    }
}

@Composable
private fun HistorySelectionActionPane(
    section: HistorySection,
    selectedCount: Int,
    totalVisibleCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRestoreSelected: () -> Unit,
    onArchiveSelected: () -> Unit,
    onUnarchiveSelected: () -> Unit,
    onTrashSelected: () -> Unit,
    onDeleteForeverSelected: () -> Unit,
) {
    val compact = isSmallLandscape()
    val buttonHeight = if (compact) 40.dp else 56.dp
    val buttonPadding = if (compact) CompactSelectionPaneButtonPadding else SelectionPaneButtonPadding
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            // Same full-bleed scroll structure as the Notes pane: insets/margins live
            // inside the scroll container so nothing gets clipped at a padded boundary.
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(if (compact) 12.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            Text(
                text = stringResource(R.string.two_pane_notes_selection_title, selectedCount),
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            RememberFilledTonalButton(
                onClick = onSelectAll,
                enabled = totalVisibleCount > 0 && selectedCount < totalVisibleCount,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(buttonHeight),
                shape = SelectionPaneButtonShape,
                contentPadding = buttonPadding,
            ) {
                SelectionPaneButtonContent("select_all", stringResource(R.string.home_select_all), compact)
            }
            RememberOutlinedButton(
                onClick = onClearSelection,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(buttonHeight),
                shape = SelectionPaneButtonShape,
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                contentPadding = buttonPadding,
            ) {
                SelectionPaneButtonContent("close", stringResource(R.string.home_unselect_all), compact)
            }
            if (section == HistorySection.TRASH) {
                RememberFilledTonalButton(
                    onClick = onRestoreSelected,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(buttonHeight),
                    shape = SelectionPaneButtonShape,
                    contentPadding = buttonPadding,
                ) {
                    SelectionPaneButtonContent("restore_from_trash", stringResource(R.string.edit_bottom_bar_restore), compact)
                }
                RememberFilledTonalButton(
                    onClick = onArchiveSelected,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(buttonHeight),
                    shape = SelectionPaneButtonShape,
                    contentPadding = buttonPadding,
                ) {
                    SelectionPaneButtonContent("archive", stringResource(R.string.edit_bottom_bar_archive), compact)
                }
            } else {
                RememberFilledTonalButton(
                    onClick = onUnarchiveSelected,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(buttonHeight),
                    shape = SelectionPaneButtonShape,
                    contentPadding = buttonPadding,
                ) {
                    SelectionPaneButtonContent("unarchive", stringResource(R.string.edit_bottom_bar_unarchive), compact)
                }
                RememberFilledTonalButton(
                    onClick = onTrashSelected,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(buttonHeight),
                    shape = SelectionPaneButtonShape,
                    contentPadding = buttonPadding,
                ) {
                    SelectionPaneButtonContent("delete_sweep", stringResource(R.string.common_move_to_trash), compact)
                }
            }
            RememberButton(
                onClick = onDeleteForeverSelected,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(buttonHeight),
                shape = SelectionPaneButtonShape,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                contentPadding = buttonPadding,
            ) {
                SelectionPaneButtonContent("delete_forever", stringResource(R.string.edit_bottom_bar_delete_forever), compact)
            }
        }
    }
}

@Composable
private fun rememberFlatScreenBalancedPaneExpansionState(
    directive: PaneScaffoldDirective,
): PaneExpansionState {
    val targetFirstPaneProportion: Float? =
        if (directive.excludedBounds.isEmpty() && directive.maxHorizontalPartitions > 1) {
            0.4f
        } else {
            null
        }
    val paneExpansionAnchors =
        remember(targetFirstPaneProportion) {
            if (targetFirstPaneProportion == null) {
                emptyList()
            } else {
                listOf(PaneExpansionAnchor.Proportion(targetFirstPaneProportion))
            }
        }
    val paneExpansionState =
        rememberPaneExpansionState(
            key = PaneExpansionStateKey.Default,
            anchors = paneExpansionAnchors,
        )
    LaunchedEffect(paneExpansionState, targetFirstPaneProportion) {
        if (targetFirstPaneProportion == null) {
            paneExpansionState.clear()
        } else {
            paneExpansionState.setFirstPaneProportion(targetFirstPaneProportion)
        }
    }
    return paneExpansionState
}
