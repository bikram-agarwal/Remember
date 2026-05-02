package dev.bikram.remember.googletasks

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberButton
import dev.bikram.remember.ui.components.RememberCheckbox
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton
import dev.bikram.remember.ui.components.RememberOutlinedButton
import dev.bikram.remember.ui.components.RememberSegmentedButton
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Top-level destination for the Google Tasks import flow.
 *
 * Icons used (all already harvested via BundledMaterialSymbolIcons.kt or other files):
 *  - "download"      - intro illustration + settings entry
 *  - "cloud_download" - settings section header
 *  - "arrow_back"    - top app bar
 *  - "info"          - reminder-time caveat
 *  - "inbox"         - empty-state
 *
 * The account header intentionally does not use a leading icon (we don't have a generic
 * account avatar in the harvested set, so we lean on the email text + Switch button).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GoogleTasksImportRoute(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val vm: GoogleTasksImportViewModel = hiltViewModel()

    val state by vm.state.collectAsStateWithLifecycle()
    val effect by vm.effects.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    val consentLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            vm.onConsentResult(
                resultData = result.data,
                approved = result.resultCode == Activity.RESULT_OK,
            )
        }
    val takeoutLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) vm.loadTakeoutJson(uri)
        }

    LaunchedEffect(effect) {
        when (val current = effect) {
            is GoogleTasksImportEffect.LaunchConsent -> {
                consentLauncher.launch(current.request)
                vm.consumeEffect()
            }
            is GoogleTasksImportEffect.ImportFinished -> {
                vm.consumeEffect()
            }
            null -> Unit
        }
    }

    LaunchedEffect(state.error) {
        val message =
            when (val err = state.error) {
                ImportError.Network -> resources.getString(R.string.google_tasks_import_error_network)
                ImportError.ConsentDenied -> resources.getString(R.string.google_tasks_import_error_consent)
                is ImportError.TakeoutParseFailed ->
                    err.message
                        ?: resources.getString(R.string.google_tasks_import_error_takeout_parse)
                is ImportError.AuthFailed ->
                    err.message
                        ?: resources.getString(R.string.google_tasks_import_error_auth_default)
                is ImportError.Unknown ->
                    err.message
                        ?: resources.getString(R.string.google_tasks_import_error_unknown_default)
                null -> return@LaunchedEffect
            }
        snackbarHostState.showSnackbar(message)
    }

    LaunchedEffect(
        state.selectedMethod,
        state.isLoaded,
        state.takeoutStats?.collapsedInstanceCount,
        state.takeoutStats?.recurringSeriesCount,
    ) {
        val stats = state.takeoutStats ?: return@LaunchedEffect
        if (state.selectedMethod != ImportMethod.ManualImport || !state.isLoaded || stats.collapsedInstanceCount <= 0) {
            return@LaunchedEffect
        }
        val message =
            resources.getString(
                R.string.google_tasks_import_takeout_collapse_summary,
                stats.collapsedInstanceCount,
                stats.recurringSeriesCount,
            )
        withTimeoutOrNull(3000) {
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Indefinite,
            )
        }
        snackbarHostState.currentSnackbarData?.dismiss()
    }

    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                colors = transparentLargeTopAppBarColors(),
                navigationIcon = {
                    Box(
                        modifier =
                            Modifier
                                .padding(start = 4.dp)
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.extraExtraLarge)
                                .tapSoundClickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "arrow_back",
                            size = 24.dp,
                            tint = MaterialTheme.colorScheme.onSurface,
                            weight = FontWeight.Medium,
                        )
                    }
                },
                title = {
                    Text(
                        stringResource(R.string.google_tasks_import_tasks_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val pickJsonLauncher: () -> Unit = {
            takeoutLauncher.launch(
                arrayOf(
                    "application/json",
                    "application/octet-stream",
                    "text/json",
                    "text/plain",
                    "*/*",
                ),
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
        ) {
            // The Connect Google / Manual import segmented pill is a SETUP-time choice. Once
            // a source is loaded, the same affordance lives inside the bottom sheet's source
            // chip - showing the pill at the top would invite users to silently nuke their
            // selection by tapping the other tab.
            if (!state.isLoaded) {
                ImportMethodSelector(
                    selectedMethod = state.selectedMethod,
                    onChange = vm::setImportMethod,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isLoaded ->
                        LoadedPanel(
                            state = state,
                            onSearchQueryChange = vm::setSearchQuery,
                            onImportModeChange = vm::setImportMode,
                            onOverwriteChange = vm::setOverwriteAlreadyImported,
                            onSelectAllToggle = vm::toggleSelectAll,
                            onClearSelection = vm::clearSelection,
                            onTaskToggle = vm::toggleSelection,
                            onGroupToggleSelectAll = vm::toggleSelectAllInList,
                            onGroupToggleCollapse = vm::toggleListCollapse,
                            onRefresh = vm::refreshFromGoogle,
                            onSwitchAccount = vm::switchAccount,
                            onSwitchToTakeout = {
                                vm.setImportMethod(ImportMethod.ManualImport)
                                pickJsonLauncher()
                            },
                            onSwitchToGoogle = {
                                vm.setImportMethod(ImportMethod.GrantPermission)
                                vm.connect()
                            },
                            onDisconnect = vm::disconnect,
                            onCancelTakeout = vm::cancelTakeoutImport,
                            onImport = vm::runImport,
                            onImportingDone = {
                                vm.dismissImportOutcome()
                                onBack()
                            },
                        )
                    state.selectedMethod == ImportMethod.ManualImport && state.isFetching -> LoadingPanel()
                    state.selectedMethod == ImportMethod.ManualImport ->
                        TakeoutImportPanel(
                            onOpenTakeout = { uriHandler.openUri(GOOGLE_TAKEOUT_URL) },
                            onPickJson = pickJsonLauncher,
                        )
                    !state.connected && !state.isFetching ->
                        SignedOutPanel(
                            rememberedEmail = state.rememberedEmail,
                            onConnect = vm::connect,
                            onSwitchAccount = vm::switchAccount,
                        )
                    state.isFetching -> LoadingPanel()
                    state.isEmpty ->
                        EmptyPanel(
                            accountEmail = state.accountEmail.orEmpty(),
                            onSwitchAccount = vm::switchAccount,
                        )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportMethodSelector(
    selectedMethod: ImportMethod,
    onChange: (ImportMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = ImportMethod.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        entries.forEachIndexed { index, method ->
            RememberSegmentedButton(
                selected = selectedMethod == method,
                onClick = { onChange(method) },
                shape = SegmentedButtonDefaults.itemShape(index, entries.size),
                label = {
                    Text(
                        text = stringResource(importMethodLabelRes(method)),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

private fun importMethodLabelRes(method: ImportMethod): Int =
    when (method) {
        ImportMethod.GrantPermission -> R.string.google_tasks_import_method_grant_permission
        ImportMethod.ManualImport -> R.string.google_tasks_import_method_manual_import
    }

@Composable
private fun SignedOutPanel(
    rememberedEmail: String?,
    onConnect: () -> Unit,
    onSwitchAccount: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GoogleTasksLogoMark(modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.google_tasks_import_intro_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.google_tasks_import_intro_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        if (!rememberedEmail.isNullOrBlank()) {
            RememberButton(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.google_tasks_import_continue_as, rememberedEmail))
            }
            Spacer(Modifier.height(8.dp))
            // Routes through switchAccount(), not connect(), so the OAuth grant gets revoked
            // before the next authorize() runs - otherwise Identity Services silently returns
            // the cached account and the picker never appears.
            RememberOutlinedButton(onClick = onSwitchAccount, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.google_tasks_import_pick_other_account))
            }
        } else {
            RememberButton(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.google_tasks_import_pick_account))
            }
        }
    }
}

@Composable
private fun GoogleTasksLogoMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.google_tasks_icon),
        contentDescription = null,
        modifier = modifier,
    )
}

@Composable
private fun TakeoutImportPanel(
    onOpenTakeout: () -> Unit,
    onPickJson: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RememberMaterialRoundedSymbol(
            name = "download",
            size = 56.dp,
            tint = MaterialTheme.colorScheme.primary,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.google_tasks_import_takeout_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.google_tasks_import_takeout_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.google_tasks_import_takeout_step_1),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.google_tasks_import_takeout_step_2),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.google_tasks_import_takeout_step_3),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        RememberOutlinedButton(onClick = onOpenTakeout, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.google_tasks_import_takeout_open_takeout))
        }
        Spacer(Modifier.height(8.dp))
        RememberButton(onClick = onPickJson, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.google_tasks_import_takeout_pick_json))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingPanel() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoadingIndicator(
            modifier = Modifier.size(64.dp),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.google_tasks_import_loading),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EmptyPanel(
    accountEmail: String,
    onSwitchAccount: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RememberMaterialRoundedSymbol(
            name = "inbox",
            size = 56.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.height(12.dp))
        val titleText =
            if (accountEmail.isBlank()) {
                stringResource(R.string.google_tasks_import_empty_title_no_email)
            } else {
                stringResource(R.string.google_tasks_import_empty_title, accountEmail)
            }
        Text(
            titleText,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.google_tasks_import_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        RememberOutlinedButton(onClick = onSwitchAccount) {
            Text(stringResource(R.string.google_tasks_import_switch_account))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadedPanel(
    state: GoogleTasksImportUiState,
    onSearchQueryChange: (String) -> Unit,
    onImportModeChange: (ImportMode) -> Unit,
    onOverwriteChange: (Boolean) -> Unit,
    onSelectAllToggle: () -> Unit,
    onClearSelection: () -> Unit,
    onTaskToggle: (String) -> Unit,
    onGroupToggleSelectAll: (String) -> Unit,
    onGroupToggleCollapse: (String) -> Unit,
    onRefresh: () -> Unit,
    onSwitchAccount: () -> Unit,
    onSwitchToTakeout: () -> Unit,
    onSwitchToGoogle: () -> Unit,
    onDisconnect: () -> Unit,
    onCancelTakeout: () -> Unit,
    onImport: () -> Unit,
    onImportingDone: () -> Unit,
) {
    val visible = remember(state.tasks, state.listFilterId, state.searchQuery) { state.visibleTasks() }
    val totalSelected =
        remember(state.selectedTaskIds, visible) {
            state.selectedTaskIds.count { id -> visible.any { it.task.id == id } }
        }
    val groups = remember(visible, state.taskLists) { groupVisibleTasks(visible, state.taskLists) }

    var sourceSheetExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingSourceSwitch by rememberSaveable { mutableStateOf<PendingSourceSwitch?>(null) }

    val attemptSwitch: (PendingSourceSwitch) -> Unit = { switch ->
        val exitsLoadedTakeout =
            state.selectedMethod == ImportMethod.ManualImport &&
                state.tasks.isNotEmpty() &&
                (switch == PendingSourceSwitch.SwitchToGoogle || switch == PendingSourceSwitch.CancelTakeout)
        if (totalSelected > 0 || exitsLoadedTakeout) {
            pendingSourceSwitch = switch
        } else {
            sourceSheetExpanded = false
            switch.run(onSwitchAccount, onSwitchToTakeout, onSwitchToGoogle, onDisconnect, onCancelTakeout)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 168.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "hero") {
                ImportHeroCard(
                    selectedCount = totalSelected,
                    totalCount = state.tasks.size,
                )
            }
            item(key = "search") {
                SearchPill(
                    query = state.searchQuery,
                    placeholder = stringResource(R.string.google_tasks_import_search_hint, state.tasks.size),
                    onQueryChange = onSearchQueryChange,
                )
            }
            item(key = "controls") {
                ControlPillRow(
                    importMode = state.importMode,
                    overwrite = state.overwriteAlreadyImported,
                    selectedVisibleCount = totalSelected,
                    visibleCount = visible.size,
                    onImportModeChange = onImportModeChange,
                    onOverwriteChange = onOverwriteChange,
                    onSelectAllToggle = onSelectAllToggle,
                    onClearSelection = onClearSelection,
                )
            }
            if (groups.isEmpty()) {
                item(key = "no_results") {
                    NoSearchResultsRow(query = state.searchQuery)
                }
            } else {
                groups.forEach { group ->
                    item(key = "group:${group.list.id}") {
                        GroupCard(
                            group = group,
                            collapsed = group.list.id in state.collapsedListIds,
                            selectedTaskIds = state.selectedTaskIds,
                            alreadyImportedIds = state.alreadyImportedIds,
                            onHeaderClick = { onGroupToggleCollapse(group.list.id) },
                            onCheckboxClick = { onGroupToggleSelectAll(group.list.id) },
                            onTaskToggle = onTaskToggle,
                        )
                    }
                }
            }
        }

        // Bottom action stack:
        //   1) On-demand SourcePopup floats just above the import button when expanded.
        //   2) Always-visible ImportButtonGroup (segmented pill: Import N tasks + chevron).
        // The popup uses AnimatedVisibility for a smooth slide-up; the import button
        // stays anchored so the user's primary action is reachable in both states.
        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = sourceSheetExpanded,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                SourcePopup(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
                    state = state,
                    isImporting = state.isImporting,
                    onRefresh = {
                        sourceSheetExpanded = false
                        onRefresh()
                    },
                    onSwitchAccount = { attemptSwitch(PendingSourceSwitch.SwitchGoogleAccount) },
                    onSwitchToTakeout = { attemptSwitch(PendingSourceSwitch.SwitchToTakeout) },
                    onSwitchToGoogle = { attemptSwitch(PendingSourceSwitch.SwitchToGoogle) },
                    onDisconnect = { attemptSwitch(PendingSourceSwitch.Disconnect) },
                    onCancelTakeout = { attemptSwitch(PendingSourceSwitch.CancelTakeout) },
                )
            }
            Spacer(Modifier.height(if (sourceSheetExpanded) 8.dp else 0.dp))
            ImportButtonGroup(
                selectedCount = totalSelected,
                sourceExpanded = sourceSheetExpanded,
                isImporting = state.isImporting,
                importCompletedCount = state.importCompletedCount,
                importTotalCount = state.importTotalCount,
                lastOutcome = state.lastOutcome,
                onImport = onImport,
                onSourceToggle = { sourceSheetExpanded = !sourceSheetExpanded },
                onImportingDone = onImportingDone,
            )
        }

        pendingSourceSwitch?.let { switch ->
            SourceSwitchConfirmation(
                selectedCount = totalSelected,
                pendingSourceSwitch = switch,
                onContinue = {
                    val confirmed = switch
                    pendingSourceSwitch = null
                    sourceSheetExpanded = false
                    confirmed.run(
                        onSwitchAccount,
                        onSwitchToTakeout,
                        onSwitchToGoogle,
                        onDisconnect,
                        onCancelTakeout,
                    )
                },
                onDismiss = { pendingSourceSwitch = null },
            )
        }
    }
}

/**
 * Decides which sub-action the user picked from the source sheet so the confirmation flow
 * can defer execution until the user resolves the dialog.
 */
private enum class PendingSourceSwitch {
    SwitchGoogleAccount,
    SwitchToTakeout,
    SwitchToGoogle,
    Disconnect,
    CancelTakeout,
    ;

    fun run(
        onSwitchAccount: () -> Unit,
        onSwitchToTakeout: () -> Unit,
        onSwitchToGoogle: () -> Unit,
        onDisconnect: () -> Unit,
        onCancelTakeout: () -> Unit,
    ) = when (this) {
        SwitchGoogleAccount -> onSwitchAccount()
        SwitchToTakeout -> onSwitchToTakeout()
        SwitchToGoogle -> onSwitchToGoogle()
        Disconnect -> onDisconnect()
        CancelTakeout -> onCancelTakeout()
    }
}

/**
 * Bins [visible] tasks under their owning [GoogleTaskList]s in the order the lists were
 * fetched. Lists with zero matches are dropped, so a search query that hits only one list
 * collapses the layout to one group instead of showing empty headers.
 */
private data class TaskGroup(
    val list: GoogleTaskList,
    val tasks: List<TaskToImport>,
    val totalInList: Int,
)

private fun groupVisibleTasks(
    visible: List<TaskToImport>,
    lists: List<GoogleTaskList>,
): List<TaskGroup> {
    if (visible.isEmpty()) return emptyList()
    val visibleByList = visible.groupBy { it.taskListId }
    val totalsByList = visible.groupBy { it.taskListId }.mapValues { it.value.size }
    return lists.mapNotNull { list ->
        val tasks = visibleByList[list.id] ?: return@mapNotNull null
        if (tasks.isEmpty()) return@mapNotNull null
        TaskGroup(list = list, tasks = tasks, totalInList = totalsByList[list.id] ?: tasks.size)
    }
}

/**
 * Hero card showing the live count of selected tasks. The big display number is the dramatic
 * focal point - users glance at this to understand "how much have I staged for import" without
 * reading any other UI. The reminder-time caveat folds inline as a small line below the
 * subtitle so it doesn't take its own row.
 */
@Composable
private fun ImportHeroCard(
    selectedCount: Int,
    totalCount: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = selectedCount.toString(),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.google_tasks_import_hero_selected_of_total, totalCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.google_tasks_import_hero_caveat_compact),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                )
            }
        }
    }
}

/**
 * Pill-shaped search field. BasicTextField gives us full control over the chrome so the field
 * matches the rest of the M3E pill aesthetic instead of inheriting the boxed TextField look.
 * Empty state shows a placeholder; non-empty shows a clear-affordance icon button.
 *
 * Background uses [surfaceContainerLow] so the search field SITS BACK from the surrounding
 * surface, instead of competing with the group cards above (which use surfaceContainerHigh).
 * This creates a clear ladder: search (recessed) → groups (raised) → sheet (most raised).
 */
@Composable
private fun SearchPill(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cursorColor = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RememberMaterialRoundedSymbol(
                name = "search",
                size = 20.dp,
                tint = placeholderColor,
                weight = FontWeight.Medium,
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                    cursorBrush = SolidColor(cursorColor),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = placeholderColor,
                    )
                }
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier =
                        Modifier
                            .size(28.dp)
                            .clip(MaterialTheme.shapes.extraExtraLarge)
                            .tapSoundClickable { onQueryChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "close",
                        size = 16.dp,
                        tint = placeholderColor,
                        weight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/**
 * The pill row underneath the search bar. Three controls in left-to-right order of decreasing
 * frequency-of-use:
 *   1. Mode dropdown - decides how tasks become notes (one note each / grouped / checklist).
 *   2. Skip-vs-overwrite dropdown - controls behavior on already-imported tasks.
 *   3. Select-all - flips between "Select all N" and "Deselect all" based on current state.
 *
 * The select-all pill is right-aligned because it is action-on-current-state rather than a
 * mode toggle, so it's grouped visually with the upcoming bottom-sheet "Import" action.
 */
@Composable
private fun ControlPillRow(
    importMode: ImportMode,
    overwrite: Boolean,
    selectedVisibleCount: Int,
    visibleCount: Int,
    onImportModeChange: (ImportMode) -> Unit,
    onOverwriteChange: (Boolean) -> Unit,
    onSelectAllToggle: () -> Unit,
    onClearSelection: () -> Unit,
) {
    // Pack the four controls left-to-right with a consistent 8dp gap. The previous design
    // pushed the icon buttons to the right edge with a Spacer(weight=1f), which on narrow
    // phones squeezed the last icon into an oval. Letting them sit flush after the
    // dropdowns gives every control its natural size and keeps tap targets uniform.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ImportModeDropdown(
            importMode = importMode,
            onChange = onImportModeChange,
        )
        SkipOverwriteDropdown(
            overwrite = overwrite,
            onChange = onOverwriteChange,
        )
        val allSelected = visibleCount > 0 && selectedVisibleCount >= visibleCount
        RememberFilledTonalIconButton(
            onClick = onSelectAllToggle,
            enabled = visibleCount > 0 && !allSelected,
            tooltipLabel = stringResource(R.string.home_select_all),
        ) {
            RememberMaterialRoundedSymbol(
                name = "select_all",
                weight = FontWeight.Medium,
            )
        }
        RememberFilledTonalIconButton(
            onClick = onClearSelection,
            enabled = selectedVisibleCount > 0,
            tooltipLabel = stringResource(R.string.home_unselect_all),
        ) {
            RememberMaterialRoundedSymbol(
                name = "deselect",
                weight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Skip / Overwrite dropdown for already-imported tasks. Default is Skip (don't re-import a
 * task that's already in the vault). Switching to Overwrite causes the importer to replace
 * the existing note with the freshly-fetched task data.
 */
@Composable
private fun SkipOverwriteDropdown(
    overwrite: Boolean,
    onChange: (Boolean) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val pillLabel =
        if (overwrite) {
            stringResource(R.string.google_tasks_import_overwrite_imported_pill)
        } else {
            stringResource(R.string.google_tasks_import_skip_imported_pill)
        }
    Box {
        RememberOutlinedButton(onClick = { expanded = true }) {
            Text(
                text = pillLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 4.dp),
            )
            RememberMaterialRoundedSymbol(
                name = "expand_more",
                size = 18.dp,
                tint = MaterialTheme.colorScheme.onSurface,
                weight = FontWeight.Medium,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            RememberDropdownMenuItem(
                text = { Text(stringResource(R.string.google_tasks_import_skip_imported_menu_label)) },
                onClick = {
                    onChange(false)
                    expanded = false
                },
            )
            RememberDropdownMenuItem(
                text = { Text(stringResource(R.string.google_tasks_import_overwrite_imported_menu_label)) },
                onClick = {
                    onChange(true)
                    expanded = false
                },
            )
        }
    }
}

/**
 * Source-list group block: a header card on top + (when expanded) a stack of independent
 * task cards below. Header layout from left to right:
 *   - Checkbox (tri-state-ish; checked when all-in-group selected) - tap toggles
 *     select-all for the visible tasks in this group.
 *   - Chevron + title + body - tap toggles collapse.
 *   - Selection-count chip (e.g. "2 / 22") on the far right - primary-tinted when any
 *     selected, muted when zero. Visually decoupled from the checkbox so the count is
 *     a *passive* status indicator while the checkbox is the active control.
 *
 * Tasks render as separate Surface cards in a Column with vertical spacing rather than
 * a single shared Surface. The expand/collapse animation animates the entire stack
 * in/out; AnimatedVisibility skips composition while collapsed so off-screen task cards
 * don't pay recomposition cost.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupCard(
    group: TaskGroup,
    collapsed: Boolean,
    selectedTaskIds: Set<String>,
    alreadyImportedIds: Set<String>,
    onHeaderClick: () -> Unit,
    onCheckboxClick: () -> Unit,
    onTaskToggle: (String) -> Unit,
) {
    val selectedInGroup =
        remember(group.tasks, selectedTaskIds) {
            group.tasks.count { it.task.id in selectedTaskIds }
        }
    val allSelected = selectedInGroup == group.tasks.size && group.tasks.isNotEmpty()
    val anySelected = selectedInGroup > 0
    val rotation by animateFloatAsState(
        targetValue = if (collapsed) 0f else 90f,
        label = "groupCardChevron",
    )
    val expandLabel = stringResource(R.string.google_tasks_import_group_expand_cd, group.list.title)
    val collapseLabel = stringResource(R.string.google_tasks_import_group_collapse_cd, group.list.title)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GroupHeaderCard(
            title = group.list.title,
            selectedInGroup = selectedInGroup,
            totalInGroup = group.totalInList,
            allSelected = allSelected,
            anySelected = anySelected,
            collapsed = collapsed,
            chevronRotation = rotation,
            expandLabel = expandLabel,
            collapseLabel = collapseLabel,
            onHeaderClick = onHeaderClick,
            onCheckboxClick = onCheckboxClick,
        )
        AnimatedVisibility(
            visible = !collapsed,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            // Indent task cards 16dp from the left edge of the group header so they
            // visually nest under the group rather than sitting at the same level.
            Column(
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                group.tasks.forEach { wrapper ->
                    TaskRow(
                        wrapper = wrapper,
                        selected = wrapper.task.id in selectedTaskIds,
                        alreadyImported = wrapper.task.id in alreadyImportedIds,
                        onToggle = { onTaskToggle(wrapper.task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupHeaderCard(
    title: String,
    selectedInGroup: Int,
    totalInGroup: Int,
    allSelected: Boolean,
    anySelected: Boolean,
    collapsed: Boolean,
    chevronRotation: Float,
    expandLabel: String,
    collapseLabel: String,
    onHeaderClick: () -> Unit,
    onCheckboxClick: () -> Unit,
) {
    // Match the TaskRow M3 multi-select pattern: selected = background colour shift,
    // unselected = quiet surface fill, no borders. Fully-selected group uses the same
    // secondaryContainer slot tasks use so a "fully selected group" reads as one
    // continuous selection band when expanded.
    val cardColor =
        if (allSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val headerShape = MaterialTheme.shapes.largeIncreased
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = headerShape,
        color = cardColor,
    ) {
        // Single click target spanning the entire surface, with clip applied BEFORE the
        // clickable so the ripple is bounded by the rounded shape rather than the
        // rectangular Row layout. The checkbox inside owns its own click via
        // RememberCheckbox(onCheckedChange = ...) and consumes the tap, so tapping it
        // toggles select-all-in-group without firing the surface's expand/collapse.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(headerShape)
                    .semantics { contentDescription = if (collapsed) expandLabel else collapseLabel }
                    .tapSoundClickable(onClick = onHeaderClick)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RememberCheckbox(
                checked = allSelected,
                onCheckedChange = { onCheckboxClick() },
            )
            RememberMaterialRoundedSymbol(
                name = "chevron_right",
                size = 20.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                weight = FontWeight.Medium,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            GroupCountChip(
                selected = selectedInGroup,
                total = totalInGroup,
                anySelected = anySelected,
            )
        }
    }
}

/**
 * Compact "n / N" chip that sits at the far right of the group header. When any task in
 * the group is selected the chip uses primary-on-primaryContainer so the user gets a
 * quick visual cue scrolling past collapsed groups - "this one already has selections".
 * Zero-selected groups stay on the muted surface palette.
 */
@Composable
private fun GroupCountChip(
    selected: Int,
    total: Int,
    anySelected: Boolean,
) {
    val container =
        if (anySelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
    val labelColor =
        if (anySelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Surface(
        shape = MaterialTheme.shapes.extraExtraLarge,
        color = container,
    ) {
        Text(
            text = stringResource(R.string.google_tasks_import_group_count_chip, selected, total),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Empty-state placeholder shown when the search query matches zero tasks. */
@Composable
private fun NoSearchResultsRow(query: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RememberMaterialRoundedSymbol(
                name = "search_off",
                size = 20.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                weight = FontWeight.Medium,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.google_tasks_import_no_search_match, query),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Floating segmented import button anchored at the bottom of the LoadedPanel. Two segments:
 *   - Wide left: primary "Import N tasks" action.
 *   - Narrow right: chevron toggle that raises [SourcePopup] above the button without
 *     pushing the import action out of the way.
 *
 * While an import is running (or has just finished) the wide segment is replaced by an
 * inline progress / done card; the narrow chevron stays in place so the user can still
 * reach the source picker if they need to abort and retry.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ImportButtonGroup(
    modifier: Modifier = Modifier,
    selectedCount: Int,
    sourceExpanded: Boolean,
    isImporting: Boolean,
    importCompletedCount: Int,
    importTotalCount: Int,
    lastOutcome: ImportOutcome?,
    onImport: () -> Unit,
    onSourceToggle: () -> Unit,
    onImportingDone: () -> Unit,
) {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val chevronRotation by animateFloatAsState(
        targetValue = if (sourceExpanded) 180f else 0f,
        label = "sourceChevron",
    )
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = navInset + 12.dp),
    ) {
        if (isImporting || lastOutcome != null) {
            ImportProgressInline(
                completedCount = importCompletedCount,
                totalCount = importTotalCount,
                outcome = lastOutcome,
                onDone = onImportingDone,
            )
        } else {
            // The official split button keeps the source picker and primary import action
            // together while letting the leading action animate into its disabled state.
            val hasSelection = selectedCount > 0
            val cdLabel =
                if (sourceExpanded) {
                    stringResource(R.string.google_tasks_import_source_chip_collapse_cd)
                } else {
                    stringResource(R.string.google_tasks_import_source_chip_expand_cd)
                }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    if (hasSelection) {
                        Arrangement.spacedBy(SplitButtonDefaults.Spacing)
                    } else {
                        Arrangement.End
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasSelection) {
                    SplitButtonDefaults.LeadingButton(
                        onClick = onImport,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(48.dp),
                    ) {
                        Text(text = stringResource(R.string.google_tasks_import_run, selectedCount))
                    }
                }
                SplitButtonDefaults.TrailingButton(
                    checked = sourceExpanded,
                    onCheckedChange = { onSourceToggle() },
                    modifier =
                        Modifier
                            .width(56.dp)
                            .height(48.dp),
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "expand_less",
                        size = 22.dp,
                        weight = FontWeight.Medium,
                        modifier =
                            Modifier
                                .graphicsLayer { rotationZ = chevronRotation }
                                .semantics { contentDescription = cdLabel },
                    )
                }
            }
        }
    }
}

/**
 * Inline popup that rises above the import button when the user taps the chevron. Hosts
 * the source identity card + four action rows (refresh / switch / use Takeout / disconnect).
 * Tapping the chevron again or any action row collapses the popup. This is intentionally
 * NOT a ModalBottomSheet - we want the page content to stay visible behind it and the
 * import button to remain reachable below it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SourcePopup(
    modifier: Modifier = Modifier,
    state: GoogleTasksImportUiState,
    isImporting: Boolean,
    onRefresh: () -> Unit,
    onSwitchAccount: () -> Unit,
    onSwitchToTakeout: () -> Unit,
    onSwitchToGoogle: () -> Unit,
    onDisconnect: () -> Unit,
    onCancelTakeout: () -> Unit,
) {
    val isGoogle = state.selectedMethod == ImportMethod.GrantPermission
    val canRefresh = isGoogle && !isImporting
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 6.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.google_tasks_import_source_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            SourceIdentityCard(state = state)
            Spacer(Modifier.height(8.dp))
            // Contextual action set: Google mode and Takeout mode each show a different
            // list. We do NOT show "Use Takeout JSON instead" when already in Takeout mode,
            // and the destructive row reads "Disconnect" for Google (sign-out semantics)
            // vs "Cancel" for Takeout (just clears the loaded file - there's no account
            // to disconnect from).
            if (isGoogle) {
                SourceActionRow(
                    symbolName = "refresh",
                    label = stringResource(R.string.google_tasks_import_source_action_refresh),
                    enabled = canRefresh,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = onRefresh,
                )
                SourceActionRow(
                    symbolName = "switch_account",
                    label = stringResource(R.string.google_tasks_import_source_action_switch_account),
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onClick = onSwitchAccount,
                )
                SourceActionRow(
                    symbolName = "upload_file",
                    label = stringResource(R.string.google_tasks_import_source_action_use_takeout),
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    onClick = onSwitchToTakeout,
                )
                SourceActionRow(
                    symbolName = "logout",
                    label = stringResource(R.string.google_tasks_import_source_action_disconnect),
                    destructive = true,
                    onClick = onDisconnect,
                )
            } else {
                SourceActionRow(
                    symbolName = "account_circle",
                    label = stringResource(R.string.google_tasks_import_source_action_connect_google),
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = onSwitchToGoogle,
                )
                SourceActionRow(
                    symbolName = "close",
                    label = stringResource(R.string.google_tasks_import_source_action_cancel_takeout),
                    destructive = true,
                    onClick = onCancelTakeout,
                )
            }
        }
    }
}

/** Highlighted card showing the active source inside the expanded sheet. */
@Composable
private fun SourceIdentityCard(state: GoogleTasksImportUiState) {
    val isGoogle = state.selectedMethod == ImportMethod.GrantPermission
    val title =
        if (isGoogle) {
            state.accountEmail.orEmpty().ifBlank { stringResource(R.string.google_tasks_import_signed_in_unknown_email) }
        } else {
            stringResource(R.string.google_tasks_import_source_takeout_title)
        }
    val subtitle =
        stringResource(
            R.string.google_tasks_import_source_google_subtitle,
            state.tasks.size,
            state.taskLists.size,
        )
    // tertiaryContainer is the second non-primary accent slot in the M3 palette - on a
    // seed-driven amber theme it lands on a complementary hue and stands out clearly
    // from the surrounding surfaceContainerHighest sheet.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(32.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                RememberMaterialRoundedSymbol(
                    name = if (isGoogle) "account_circle" else "upload_file",
                    size = 18.dp,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    weight = FontWeight.Medium,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.78f),
                )
            }
            RememberMaterialRoundedSymbol(
                name = "check_circle",
                size = 22.dp,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                weight = FontWeight.Medium,
                filled = true,
            )
        }
    }
}

/**
 * One row in the expanded sheet's actions list. The icon sits in a tinted chip so we get a
 * splash of color per action without the label competing. [iconTint] picks the chip color;
 * destructive rows ignore [iconTint] and bathe the whole row in error red.
 */
@Composable
private fun SourceActionRow(
    symbolName: String,
    label: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    val labelColor =
        when {
            destructive -> MaterialTheme.colorScheme.error
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            else -> MaterialTheme.colorScheme.onSurface
        }
    val chipBackground =
        when {
            destructive -> MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            else -> iconTint.copy(alpha = 0.18f)
        }
    val chipIconTint =
        when {
            destructive -> MaterialTheme.colorScheme.error
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            else -> iconTint
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { if (enabled) it.tapSoundClickable(onClick = onClick) else it }
                .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(chipBackground),
            contentAlignment = Alignment.Center,
        ) {
            RememberMaterialRoundedSymbol(
                name = symbolName,
                size = 18.dp,
                tint = chipIconTint,
                weight = FontWeight.Medium,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor,
        )
    }
}

/**
 * Wavy progress + done state for the import. Replaces the import button while [totalCount]
 * tasks are being written; once the outcome arrives, swaps to a "Done" button that returns
 * the user to the previous screen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ImportProgressInline(
    completedCount: Int,
    totalCount: Int,
    outcome: ImportOutcome?,
    onDone: () -> Unit,
) {
    val completed = outcome != null
    val progress =
        if (completed) {
            1f
        } else if (totalCount > 0) {
            (completedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        label = "tasksImportProgress",
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RememberMaterialRoundedSymbol(
                    name = if (completed) "check_circle" else "download",
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    weight = FontWeight.Medium,
                )
                Text(
                    text =
                        if (outcome != null) {
                            stringResource(R.string.google_tasks_import_done_snack, outcome.writtenCount)
                        } else if (totalCount > 0) {
                            stringResource(
                                R.string.google_tasks_import_importing_progress,
                                completedCount.coerceAtMost(totalCount),
                                totalCount,
                            )
                        } else {
                            stringResource(R.string.google_tasks_import_importing)
                        },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            LinearWavyProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f),
            )
            if (completed) {
                RememberButton(
                    onClick = onDone,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.common_done))
                }
            }
        }
    }
}

/**
 * Confirmation shown when a user taps a source action with a non-empty selection.
 * Continuing clears the current task selection/source before anything is saved to Remember.
 */
@Composable
private fun SourceSwitchConfirmation(
    selectedCount: Int,
    pendingSourceSwitch: PendingSourceSwitch,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (pendingSourceSwitch) {
                    PendingSourceSwitch.SwitchToGoogle,
                    PendingSourceSwitch.CancelTakeout,
                    -> stringResource(R.string.google_tasks_import_cancel_takeout_confirm_title)
                    else -> stringResource(R.string.google_tasks_import_source_switch_confirm_title, selectedCount)
                },
            )
        },
        text = {
            Text(
                when (pendingSourceSwitch) {
                    PendingSourceSwitch.CancelTakeout ->
                        stringResource(R.string.google_tasks_import_cancel_takeout_confirm_body)
                    else -> stringResource(R.string.google_tasks_import_source_switch_confirm_body)
                },
            )
        },
        confirmButton = {
            RememberTextButton(onClick = onContinue) {
                Text(
                    when (pendingSourceSwitch) {
                        PendingSourceSwitch.SwitchToGoogle,
                        PendingSourceSwitch.CancelTakeout,
                        -> stringResource(R.string.common_yes)
                        else -> stringResource(R.string.google_tasks_import_source_switch_confirm_continue)
                    },
                )
            }
        },
        dismissButton = {
            RememberTextButton(onClick = onDismiss) {
                Text(
                    when (pendingSourceSwitch) {
                        PendingSourceSwitch.SwitchToGoogle,
                        PendingSourceSwitch.CancelTakeout,
                        -> stringResource(R.string.common_no)
                        else -> stringResource(R.string.common_cancel)
                    },
                )
            }
        },
    )
}

@Composable
private fun TakeoutCollapseSummary(stats: GoogleTasksTakeoutStats) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RememberMaterialRoundedSymbol(
                name = "check_circle",
                size = 20.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                weight = FontWeight.Medium,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text =
                    stringResource(
                        R.string.google_tasks_import_takeout_collapse_summary,
                        stats.collapsedInstanceCount,
                        stats.recurringSeriesCount,
                    ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ImportModeDropdown(
    importMode: ImportMode,
    onChange: (ImportMode) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box {
        RememberOutlinedButton(onClick = { expanded = true }) {
            Text(
                text = stringResource(modeLabelRes(importMode)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 4.dp),
            )
            RememberMaterialRoundedSymbol(
                name = "expand_more",
                size = 18.dp,
                tint = MaterialTheme.colorScheme.onSurface,
                weight = FontWeight.Medium,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            ImportMode.entries.forEach { mode ->
                RememberDropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(modeLabelRes(mode)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        onChange(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Standalone task card following the M3 multi-select list pattern. The visual delta
 * between selected and unselected is purely a background-colour shift to
 * `secondaryContainer` (the M3 "this is selected" container) - NO border treatment, no
 * extra elevation, no opacity changes. That keeps the list scannable and avoids the
 * "every selected card has a chunky outline" problem from the previous design.
 *
 * Already-imported tasks dim to 0.6 alpha and show a muted "imported" label so users
 * can see they've already handled them at a glance, without making them unselectable
 * (the user might want to re-import via the Overwrite imported pill).
 *
 * The list-name chip is intentionally absent - the GroupCard header above already
 * labels the source list, so re-stating it on every row was redundant noise.
 */
@Composable
private fun TaskRow(
    wrapper: TaskToImport,
    selected: Boolean,
    alreadyImported: Boolean,
    onToggle: () -> Unit,
) {
    val displayTitle =
        wrapper.task.title.orEmpty().ifBlank {
            wrapper.task.notes
                .orEmpty()
                .lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                .orEmpty()
        }
    val due = wrapper.task.due
    val dueLabel = remember(due) { due?.let { formatDueDate(it) } }
    val noteSnippet =
        wrapper.task.notes
            ?.replace('\n', ' ')
            ?.trim()
            .orEmpty()
    val statusCompleted = wrapper.task.status.equals(GoogleTaskStatus.COMPLETED, ignoreCase = true)

    val cardColor =
        if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    val titleColor =
        if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val supportingColor =
        if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    val cardAlpha = if (alreadyImported && !selected) 0.6f else 1f

    val cardShape = MaterialTheme.shapes.medium
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = cardAlpha }
                // Clip to the rounded shape BEFORE the clickable so the ripple is bounded
                // by the same rounded outline the surface paints. Without this clip, the
                // clickable's ripple uses the rectangular layout bounds and the long-press
                // ripple shows sharp corners that bleed past the rounded card edges.
                .clip(cardShape)
                .tapSoundClickable(onClick = onToggle),
        shape = cardShape,
        color = cardColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RememberCheckbox(
                checked = selected,
                onCheckedChange = null,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle.ifBlank { stringResource(R.string.google_tasks_import_untitled_task) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (noteSnippet.isNotBlank()) {
                    Text(
                        text = noteSnippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = supportingColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                val hasMeta = dueLabel != null || statusCompleted || alreadyImported
                if (hasMeta) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dueLabel != null) {
                            Text(
                                text = stringResource(R.string.google_tasks_import_due_label, dueLabel),
                                style = MaterialTheme.typography.labelSmall,
                                color = supportingColor,
                            )
                        }
                        if (statusCompleted) {
                            Text(
                                text = stringResource(R.string.google_tasks_import_completed_chip),
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.tertiary
                                    },
                            )
                        }
                        if (alreadyImported) {
                            Text(
                                text = stringResource(R.string.google_tasks_import_already_imported_chip),
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun modeLabelRes(mode: ImportMode): Int =
    when (mode) {
        ImportMode.ONE_NOTE_PER_TASK -> R.string.google_tasks_import_mode_one_note_per_task
        ImportMode.GROUP_BY_LIST -> R.string.google_tasks_import_mode_group_by_list
        ImportMode.LIST_AS_CHECKLIST -> R.string.google_tasks_import_mode_list_as_checklist
    }

private val DUE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

private fun formatDueDate(rfc3339: String): String? =
    runCatching {
        val instant = Instant.parse(rfc3339)
        DUE_FORMATTER.format(instant.atZone(ZoneId.systemDefault()).toLocalDate())
    }.getOrNull()

private const val GOOGLE_TAKEOUT_URL = "https://takeout.google.com/settings/takeout/custom/tasks"
