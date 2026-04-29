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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import dev.bikram.remember.ui.components.settings.GroupPosition
import dev.bikram.remember.ui.components.settings.groupedItemShape
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.theme.elevatedCardColors
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
                ImportError.Network -> context.getString(R.string.google_tasks_import_error_network)
                ImportError.ConsentDenied -> context.getString(R.string.google_tasks_import_error_consent)
                is ImportError.TakeoutParseFailed ->
                    err.message
                        ?: context.getString(R.string.google_tasks_import_error_takeout_parse)
                is ImportError.AuthFailed ->
                    err.message
                        ?: context.getString(R.string.google_tasks_import_error_auth_default)
                is ImportError.Unknown ->
                    err.message
                        ?: context.getString(R.string.google_tasks_import_error_unknown_default)
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
            context.getString(
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
                                .clip(RoundedCornerShape(20.dp))
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
                            onDisconnect = vm::switchAccount,
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
            shape = RoundedCornerShape(16.dp),
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
        if (totalSelected > 0) {
            pendingSourceSwitch = switch
        } else {
            sourceSheetExpanded = false
            switch.run(onSwitchAccount, onSwitchToTakeout, onSwitchToGoogle, onDisconnect)
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

        SourceSheet(
            modifier = Modifier.align(Alignment.BottomCenter),
            state = state,
            expanded = sourceSheetExpanded,
            selectedCount = totalSelected,
            onChipTap = { sourceSheetExpanded = !sourceSheetExpanded },
            onRefresh = {
                sourceSheetExpanded = false
                onRefresh()
            },
            onSwitchAccount = { attemptSwitch(PendingSourceSwitch.SwitchGoogleAccount) },
            onSwitchToTakeout = { attemptSwitch(PendingSourceSwitch.SwitchToTakeout) },
            onSwitchToGoogle = { attemptSwitch(PendingSourceSwitch.SwitchToGoogle) },
            onDisconnect = { attemptSwitch(PendingSourceSwitch.Disconnect) },
            onImport = onImport,
            isImporting = state.isImporting,
            importCompletedCount = state.importCompletedCount,
            importTotalCount = state.importTotalCount,
            lastOutcome = state.lastOutcome,
            onImportingDone = onImportingDone,
        )

        pendingSourceSwitch?.let { switch ->
            SourceSwitchConfirmation(
                selectedCount = totalSelected,
                onContinue = {
                    val confirmed = switch
                    pendingSourceSwitch = null
                    sourceSheetExpanded = false
                    confirmed.run(onSwitchAccount, onSwitchToTakeout, onSwitchToGoogle, onDisconnect)
                },
                onDismiss = { pendingSourceSwitch = null },
            )
        }
    }
}

/**
 * Decides which sub-action the user picked from the source sheet so the confirmation flow
 * can defer execution until the user resolves the dialog. The four cases line up with the
 * four rows in the expanded sheet.
 */
private enum class PendingSourceSwitch {
    SwitchGoogleAccount,
    SwitchToTakeout,
    SwitchToGoogle,
    Disconnect,
    ;

    fun run(
        onSwitchAccount: () -> Unit,
        onSwitchToTakeout: () -> Unit,
        onSwitchToGoogle: () -> Unit,
        onDisconnect: () -> Unit,
    ) = when (this) {
        SwitchGoogleAccount -> onSwitchAccount()
        SwitchToTakeout -> onSwitchToTakeout()
        SwitchToGoogle -> onSwitchToGoogle()
        Disconnect -> onDisconnect()
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

private fun groupPositionFor(
    index: Int,
    totalCount: Int,
): GroupPosition =
    when {
        totalCount <= 1 -> GroupPosition.ONLY
        index == 0 -> GroupPosition.FIRST
        index == totalCount - 1 -> GroupPosition.LAST
        else -> GroupPosition.MIDDLE
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
        shape = RoundedCornerShape(28.dp),
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
        shape = RoundedCornerShape(28.dp),
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
                            .clip(RoundedCornerShape(14.dp))
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
        Spacer(Modifier.weight(1f))
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
 * Source-list group card. Header has a tri-state checkbox (all / some / none of the visible
 * tasks in this list selected), the list title, the per-group selected/total counter, and a
 * chevron that rotates 90deg when expanded. Tap the header anywhere to collapse/expand;
 * tap the checkbox specifically to toggle select-all-in-list without changing collapse state.
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
    val rotation by animateFloatAsState(
        targetValue = if (collapsed) 0f else 90f,
        label = "groupCardChevron",
    )
    val expandLabel = stringResource(R.string.google_tasks_import_group_expand_cd, group.list.title)
    val collapseLabel = stringResource(R.string.google_tasks_import_group_collapse_cd, group.list.title)

    // Fully-selected groups get a primary-coloured border and a faint primaryContainer wash
    // so they pop visually against the rest of the list. Empty / partially-selected groups
    // sit on a regular surfaceContainerHigh card with a quiet outline border.
    val cardColor =
        if (allSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val cardBorder =
        if (allSelected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
        }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        border = cardBorder,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Checkbox area gets its own click target - tapping it toggles select-all-in-list
                // without affecting collapse state. Disable the inner Checkbox's own click (set
                // onCheckedChange = null) so only the outer Box click fires, avoiding double-toggle.
                Box(
                    modifier =
                        Modifier
                            .tapSoundClickable(onClick = onCheckboxClick)
                            .padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
                ) {
                    RememberCheckbox(
                        checked = allSelected,
                        onCheckedChange = null,
                    )
                }
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .semantics { contentDescription = if (collapsed) expandLabel else collapseLabel }
                            .tapSoundClickable(onClick = onHeaderClick)
                            .padding(end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.list.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.google_tasks_import_group_selection_count,
                                    selectedInGroup,
                                    group.totalInList,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RememberMaterialRoundedSymbol(
                        name = "chevron_right",
                        size = 20.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        weight = FontWeight.Medium,
                        modifier = Modifier.graphicsLayer { rotationZ = rotation },
                    )
                }
            }
            AnimatedVisibility(
                visible = !collapsed,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                Column {
                    group.tasks.forEachIndexed { index, wrapper ->
                        TaskRow(
                            wrapper = wrapper,
                            position = groupPositionFor(index, group.tasks.size),
                            selected = wrapper.task.id in selectedTaskIds,
                            alreadyImported = wrapper.task.id in alreadyImportedIds,
                            onToggle = { onTaskToggle(wrapper.task.id) },
                        )
                    }
                }
            }
        }
    }
}

/** Empty-state placeholder shown when the search query matches zero tasks. */
@Composable
private fun NoSearchResultsRow(query: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
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
 * Inline bottom sheet hosting the source chip and the import action. Two visual states:
 *  - Collapsed: drag handle, source chip, import button. Mirrors the previous BottomBar's
 *    footprint so the layout doesn't jump.
 *  - Expanded: drag handle, "Source" label, current-source identity card, list of switch
 *    actions (refresh / switch account / use the other method / disconnect), then the import
 *    button below a divider so the primary action stays accessible while picking a source.
 *
 * While an import is in progress (or has just finished) the sheet renders a wavy progress
 * card in place of the import button, replacing the previous ImportingProgressCard logic.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SourceSheet(
    modifier: Modifier = Modifier,
    state: GoogleTasksImportUiState,
    expanded: Boolean,
    selectedCount: Int,
    onChipTap: () -> Unit,
    onRefresh: () -> Unit,
    onSwitchAccount: () -> Unit,
    onSwitchToTakeout: () -> Unit,
    onSwitchToGoogle: () -> Unit,
    onDisconnect: () -> Unit,
    onImport: () -> Unit,
    isImporting: Boolean,
    importCompletedCount: Int,
    importTotalCount: Int,
    lastOutcome: ImportOutcome?,
    onImportingDone: () -> Unit,
) {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isGoogle = state.selectedMethod == ImportMethod.GrantPermission
    val canRefresh = isGoogle && !isImporting

    // Sheet uses surfaceContainerHighest plus a chunky tonal elevation so it visually lifts
    // off the page. The thin top border is outline-tinted so there's an unmistakable visual
    // boundary between the sheet and the LazyColumn behind it - even when the surface tones
    // happen to be close in this seed-driven palette.
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 6.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + navInset),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .height(4.dp)
                            .width(36.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                )
            }
            Spacer(Modifier.height(10.dp))

            SourceChipRow(
                state = state,
                onClick = onChipTap,
                chevronUp = expanded,
            )
            Spacer(Modifier.height(10.dp))

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.google_tasks_import_source_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                    // Each action row gets its own colored chip-icon so the list of actions
                    // doesn't read as a wall of identical-tinted glyphs. The colors map to
                    // semantic intent: refresh = primary (the most common action), switch
                    // account = secondary (related to current source), takeout = tertiary
                    // (different hue), disconnect = error (destructive).
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
                    } else {
                        SourceActionRow(
                            symbolName = "upload_file",
                            label = stringResource(R.string.google_tasks_import_source_action_use_takeout),
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            onClick = onSwitchToTakeout,
                        )
                        SourceActionRow(
                            symbolName = "account_circle",
                            label = stringResource(R.string.google_tasks_import_source_action_connect_google),
                            iconTint = MaterialTheme.colorScheme.primary,
                            onClick = onSwitchToGoogle,
                        )
                    }
                    SourceActionRow(
                        symbolName = "logout",
                        label = stringResource(R.string.google_tasks_import_source_action_disconnect),
                        destructive = true,
                        onClick = onDisconnect,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (isImporting || lastOutcome != null) {
                ImportProgressInline(
                    completedCount = importCompletedCount,
                    totalCount = importTotalCount,
                    outcome = lastOutcome,
                    onDone = onImportingDone,
                )
            } else {
                RememberButton(
                    onClick = onImport,
                    enabled = selectedCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.google_tasks_import_run, selectedCount))
                }
            }
        }
    }
}

/**
 * Compact source chip shown in the collapsed sheet. Title + subtitle line communicate the
 * active source at a glance; tap surface forwards to the sheet's expand handler. Up-chevron
 * hint signals "tap to see more".
 */
@Composable
private fun SourceChipRow(
    state: GoogleTasksImportUiState,
    onClick: () -> Unit,
    chevronUp: Boolean,
) {
    val isGoogle = state.selectedMethod == ImportMethod.GrantPermission
    val title =
        if (isGoogle) {
            stringResource(
                R.string.google_tasks_import_source_chip_google_title,
                state.accountEmail.orEmpty().ifBlank { stringResource(R.string.google_tasks_import_signed_in_unknown_email) },
            )
        } else {
            stringResource(R.string.google_tasks_import_source_chip_takeout_title)
        }
    val subtitle =
        stringResource(
            R.string.google_tasks_import_source_google_subtitle,
            state.tasks.size,
            state.taskLists.size,
        )
    val cdLabel =
        if (chevronUp) {
            stringResource(R.string.google_tasks_import_source_chip_collapse_cd)
        } else {
            stringResource(R.string.google_tasks_import_source_chip_expand_cd)
        }
    // secondaryContainer pulls the chip toward a different color slot than the sheet's
    // surface tone, so the chip reads as a clearly distinct, tappable thing inside the sheet
    // rather than melting into it.
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = cdLabel }
                .tapSoundClickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                RememberMaterialRoundedSymbol(
                    name = if (isGoogle) "account_circle" else "upload_file",
                    size = 18.dp,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    weight = FontWeight.Medium,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RememberMaterialRoundedSymbol(
                name = if (chevronUp) "expand_more" else "expand_less",
                size = 20.dp,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                weight = FontWeight.Medium,
            )
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
        shape = RoundedCornerShape(16.dp),
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
                        .clip(RoundedCornerShape(8.dp))
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
                    .clip(RoundedCornerShape(10.dp))
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
        shape = RoundedCornerShape(24.dp),
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
 * Confirmation shown when a user taps a source-switch action with a non-empty selection
 * (going through with the switch silently nukes [selectedCount] tasks). The continue button
 * is the destructive role, since the user is acknowledging the data loss.
 */
@Composable
private fun SourceSwitchConfirmation(
    selectedCount: Int,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.google_tasks_import_source_switch_confirm_title, selectedCount))
        },
        text = {
            Text(stringResource(R.string.google_tasks_import_source_switch_confirm_body))
        },
        confirmButton = {
            RememberTextButton(onClick = onContinue) {
                Text(stringResource(R.string.google_tasks_import_source_switch_confirm_continue))
            }
        },
        dismissButton = {
            RememberTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun TakeoutCollapseSummary(stats: GoogleTasksTakeoutStats) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
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

@Composable
private fun TaskRow(
    wrapper: TaskToImport,
    position: GroupPosition,
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
    val cardColors = elevatedCardColors()

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .tapSoundClickable(onClick = onToggle),
        shape = groupedItemShape(position),
        color = cardColors.containerColor,
        contentColor = cardColors.contentColor,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                RememberCheckbox(
                    checked = selected,
                    onCheckedChange = { onToggle() },
                )
            },
            headlineContent = {
                Text(
                    text = displayTitle.ifBlank { stringResource(R.string.google_tasks_import_untitled_task) },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Column {
                    if (noteSnippet.isNotBlank()) {
                        Text(
                            text = noteSnippet,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    wrapper.taskListTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors =
                                AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                        )
                        if (dueLabel != null) {
                            Text(
                                text = stringResource(R.string.google_tasks_import_due_label, dueLabel),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (statusCompleted) {
                            Text(
                                text = stringResource(R.string.google_tasks_import_completed_chip),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        if (alreadyImported) {
                            Text(
                                text = stringResource(R.string.google_tasks_import_already_imported_chip),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            },
        )
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
