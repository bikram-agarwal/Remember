package dev.bikram.remember.googletasks

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import dev.bikram.remember.ui.components.RememberOutlinedButton
import dev.bikram.remember.ui.components.RememberSegmentedButton
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.settings.GroupPosition
import dev.bikram.remember.ui.components.settings.groupedItemShape
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.theme.elevatedCardColors
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors
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
                val message =
                    context.getString(
                        R.string.google_tasks_import_done_snack,
                        current.outcome.writtenCount,
                    )
                snackbarHostState.showSnackbar(message)
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
        bottomBar = {
            if (state.isLoaded) {
                ImportBottomBar(
                    selectedCount =
                        state.selectedTaskIds.count { id ->
                            state.visibleTasks().any { it.task.id == id }
                        },
                    visibleCount = state.visibleTasks().size,
                    importMode = state.importMode,
                    overwrite = state.overwriteAlreadyImported,
                    isImporting = state.isImporting,
                    onSelectAllToggle = vm::toggleSelectAll,
                    onImportModeChange = vm::setImportMode,
                    onOverwriteChange = vm::setOverwriteAlreadyImported,
                    onImport = vm::runImport,
                )
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
        ) {
            ImportMethodSelector(
                selectedMethod = state.selectedMethod,
                onChange = vm::setImportMethod,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.selectedMethod == ImportMethod.ManualImport && state.isFetching -> LoadingPanel()
                    state.selectedMethod == ImportMethod.ManualImport && state.isLoaded ->
                        LoadedPanel(
                            state = state,
                            onSwitchAccount = vm::switchAccount,
                            onListFilterChange = vm::toggleListFilter,
                            onTaskToggle = vm::toggleSelection,
                        )
                    state.selectedMethod == ImportMethod.ManualImport ->
                        TakeoutImportPanel(
                            onOpenTakeout = { uriHandler.openUri(GOOGLE_TAKEOUT_URL) },
                            onPickJson = {
                                takeoutLauncher.launch(
                                    arrayOf(
                                        "application/json",
                                        "application/octet-stream",
                                        "text/json",
                                        "text/plain",
                                        "*/*",
                                    ),
                                )
                            },
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
                    state.isLoaded ->
                        LoadedPanel(
                            state = state,
                            onSwitchAccount = vm::switchAccount,
                            onListFilterChange = vm::toggleListFilter,
                            onTaskToggle = vm::toggleSelection,
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
        Text(
            stringResource(R.string.google_tasks_import_loading),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadedPanel(
    state: GoogleTasksImportUiState,
    onSwitchAccount: () -> Unit,
    onListFilterChange: (String?) -> Unit,
    onTaskToggle: (String) -> Unit,
) {
    val visible = remember(state.tasks, state.listFilterId) { state.visibleTasks() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "account_chip") {
            if (state.selectedMethod == ImportMethod.ManualImport) {
                ManualImportHeader()
            } else {
                AccountHeader(
                    email = state.accountEmail.orEmpty(),
                    onSwitchAccount = onSwitchAccount,
                )
            }
        }
        if (state.selectedMethod == ImportMethod.ManualImport &&
            state.takeoutStats?.collapsedInstanceCount?.let { collapsedCount -> collapsedCount > 0 } == true
        ) {
            item(key = "takeout_collapse_summary") {
                TakeoutCollapseSummary(state.takeoutStats)
            }
        }
        item(key = "caveat") {
            ReminderTimeCaveat()
        }
        item(key = "list_filter") {
            if (state.taskLists.size > 1) {
                ListFilterRow(
                    lists = state.taskLists,
                    selectedId = state.listFilterId,
                    onChange = onListFilterChange,
                )
            }
        }
        itemsIndexed(items = visible, key = { _, wrapper -> wrapper.task.id }) { index, wrapper ->
            TaskRow(
                wrapper = wrapper,
                position = groupPositionFor(index, visible.size),
                selected = wrapper.task.id in state.selectedTaskIds,
                alreadyImported = wrapper.task.id in state.alreadyImportedIds,
                onToggle = { onTaskToggle(wrapper.task.id) },
            )
        }
        item(key = "spacer") { Spacer(Modifier.height(120.dp)) }
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

@Composable
private fun ManualImportHeader() {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RememberMaterialRoundedSymbol(
                name = "download",
                size = 20.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                weight = FontWeight.Medium,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.google_tasks_import_takeout_loaded),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }
    }
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
private fun AccountHeader(
    email: String,
    onSwitchAccount: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.google_tasks_import_signed_in_as),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = email.ifBlank { stringResource(R.string.google_tasks_import_signed_in_unknown_email) },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            RememberTextButton(onClick = onSwitchAccount) {
                Text(stringResource(R.string.google_tasks_import_switch_account))
            }
        }
    }
}

@Composable
private fun ReminderTimeCaveat() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RememberMaterialRoundedSymbol(
                name = "info",
                size = 18.dp,
                tint = MaterialTheme.colorScheme.primary,
                weight = FontWeight.Medium,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.google_tasks_import_time_caveat),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Filter for the visible task list. Renders as an outlined pill with a chevron that opens a
 * dropdown menu of choices ("All lists" + each Google task list). A segmented button row was
 * the previous design and stretched poorly when the user has many lists.
 */
@Composable
private fun ListFilterRow(
    lists: List<GoogleTaskList>,
    selectedId: String?,
    onChange: (String?) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedTitle = lists.firstOrNull { it.id == selectedId }?.title
    val label = selectedTitle ?: stringResource(R.string.google_tasks_import_filter_all_lists)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.google_tasks_import_filter_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        Box {
            RememberOutlinedButton(onClick = { expanded = true }) {
                Text(
                    text = label,
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
            ) {
                RememberDropdownMenuItem(
                    text = { Text(stringResource(R.string.google_tasks_import_filter_all_lists)) },
                    onClick = {
                        onChange(null)
                        expanded = false
                    },
                )
                lists.forEach { list ->
                    RememberDropdownMenuItem(
                        text = {
                            Text(
                                text = list.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onClick = {
                            onChange(list.id)
                            expanded = false
                        },
                    )
                }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ImportBottomBar(
    selectedCount: Int,
    visibleCount: Int,
    importMode: ImportMode,
    overwrite: Boolean,
    isImporting: Boolean,
    onSelectAllToggle: () -> Unit,
    onImportModeChange: (ImportMode) -> Unit,
    onOverwriteChange: (Boolean) -> Unit,
    onImport: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ImportModeRow(importMode, onImportModeChange)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RememberCheckbox(
                    checked = overwrite,
                    onCheckedChange = onOverwriteChange,
                )
                Text(
                    text = stringResource(R.string.google_tasks_import_overwrite),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier =
                        Modifier
                            .weight(1f)
                            .tapSoundClickable { onOverwriteChange(!overwrite) }
                            .padding(start = 4.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RememberTextButton(onClick = onSelectAllToggle) {
                    val allSelected = selectedCount >= visibleCount && visibleCount > 0
                    Text(
                        text =
                            if (allSelected) {
                                stringResource(R.string.google_tasks_import_clear_selection)
                            } else {
                                stringResource(R.string.google_tasks_import_select_all)
                            },
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.google_tasks_import_selection_count, selectedCount, visibleCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                RememberButton(
                    onClick = onImport,
                    enabled = !isImporting && selectedCount > 0,
                ) {
                    Text(
                        text = stringResource(R.string.google_tasks_import_run, selectedCount),
                    )
                }
            }
            AnimatedVisibility(
                visible = isImporting,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportModeRow(
    importMode: ImportMode,
    onChange: (ImportMode) -> Unit,
) {
    val entries = ImportMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        entries.forEachIndexed { index, mode ->
            RememberSegmentedButton(
                selected = importMode == mode,
                onClick = { onChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, entries.size),
                label = {
                    Text(
                        text = stringResource(modeLabelRes(mode)),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
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
