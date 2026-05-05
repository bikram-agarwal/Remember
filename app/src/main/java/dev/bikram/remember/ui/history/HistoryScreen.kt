package dev.bikram.remember.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.bikram.remember.R
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.common.BulkUndoableAction
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.bulkActionSnackbarMessage
import dev.bikram.remember.ui.components.EmptyArchiveIllustration
import dev.bikram.remember.ui.components.EmptyTrashIllustration
import dev.bikram.remember.ui.components.MultiActionSwipeRevealCard
import dev.bikram.remember.ui.components.NoteCard
import dev.bikram.remember.ui.components.NoteCardUiModel
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton
import dev.bikram.remember.ui.components.RememberSegmentedButton
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.SwipeRevealTile
import dev.bikram.remember.ui.components.toNoteCardUiModel
import dev.bikram.remember.ui.modifiers.PillBottomBarHeight
import dev.bikram.remember.ui.modifiers.PillBottomScrimExtra
import dev.bikram.remember.ui.modifiers.applyToScrollableList
import dev.bikram.remember.ui.modifiers.rememberContentOverflowScrollEnabled
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import dev.bikram.remember.ui.theme.LocalSnackbarHostState
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

/** Which shelf the user is viewing. */
enum class HistorySection { ARCHIVE, TRASH }

/**
 * One-shot events emitted by [HistoryViewModel] for the UI layer to react to.
 * Currently only carries bulk-action completions so the screen can show an undo
 * snackbar; modeled as a sealed interface so additional event kinds can be added
 * without breaking existing collectors.
 */
sealed interface HistoryEvent {
    data class BulkActionPerformed(
        val action: BulkUndoableAction,
    ) : HistoryEvent
}

@Immutable
private data class HistoryNoteRow(
    val note: NoteWithItems,
    val card: NoteCardUiModel,
)

@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        private val repository: NoteRepository,
    ) : ViewModel() {
        /**
         * Wall-clock instant captured when the view-model is created -- used to derive the
         * "days left" counter without re-invoking the system clock on every recomposition.
         */
        private val nowAtCreation = System.currentTimeMillis()

        val trashedItems: StateFlow<List<NoteWithItems>> =
            repository
                .observeTrashed()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val archivedItems: StateFlow<List<NoteWithItems>> =
            repository
                .observeArchived()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
        val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

        private val _events = MutableSharedFlow<HistoryEvent>()

        /**
         * One-shot events for the UI: bulk-action completions surface a snackbar with
         * Undo. The repository has already coalesced row writes into a single Flow
         * emission by the time these fire, so the list has reflowed and the snackbar
         * lands on settled state.
         */
        val events: SharedFlow<HistoryEvent> = _events.asSharedFlow()

        /**
         * Most recent bulk action originating from selection mode. Cleared after undo,
         * after deletion is performed (since delete-forever is intentionally not
         * undoable), or when superseded by the next bulk action.
         */
        @Volatile
        private var lastBulkAction: BulkUndoableAction? = null

        fun toggleSelection(id: Long) {
            _selectedIds.value =
                if (id in _selectedIds.value) {
                    _selectedIds.value - id
                } else {
                    _selectedIds.value + id
                }
        }

        fun selectNotes(ids: Set<Long>) {
            _selectedIds.value = ids
        }

        fun clearSelection() {
            _selectedIds.value = emptySet()
        }

        fun pruneSelection(validIds: Set<Long>) {
            _selectedIds.value = _selectedIds.value.intersect(validIds)
        }

        fun restore(note: NoteWithItems) {
            val id = note.note.id
            viewModelScope.launch {
                repository.restoreFromTrash(id)
                emitSingleCardAction(BulkUndoableAction.Restored(setOf(id)))
            }
        }

        fun archiveFromTrash(note: NoteWithItems) {
            val id = note.note.id
            viewModelScope.launch {
                repository.archiveNote(id)
                emitSingleCardAction(BulkUndoableAction.ArchivedFromTrash(setOf(id)))
            }
        }

        /**
         * Permanent single-card delete. The screen guards this path with a
         * confirmation dialog (mirror of the bulk delete sheet); no snackbar fires
         * after - delete-forever has no undo so the dialog IS the confirmation.
         * [lastBulkAction] is cleared so a stale undo from an earlier action cannot
         * fire after the user's confirmed permanent delete.
         */
        fun deleteForever(note: NoteWithItems) {
            val id = note.note.id
            viewModelScope.launch {
                repository.deleteForever(id)
                lastBulkAction = null
            }
        }

        fun unarchive(note: NoteWithItems) {
            val id = note.note.id
            viewModelScope.launch {
                repository.unarchiveNote(id)
                emitSingleCardAction(BulkUndoableAction.Unarchived(setOf(id)))
            }
        }

        fun moveArchivedToTrash(note: NoteWithItems) {
            val id = note.note.id
            viewModelScope.launch {
                repository.moveToTrash(id)
                emitSingleCardAction(BulkUndoableAction.MovedArchiveToTrash(setOf(id)))
            }
        }

        /**
         * Records [action] as the most-recent undoable action and notifies the screen
         * so it can show a snackbar. Single-card swipe / tap callbacks share the same
         * snackbar plumbing as bulk-selection mode; both routes through
         * [HistoryEvent.BulkActionPerformed] and [undoLastBulkAction].
         */
        private suspend fun emitSingleCardAction(action: BulkUndoableAction) {
            lastBulkAction = action
            _events.emit(HistoryEvent.BulkActionPerformed(action))
        }

        fun emptyTrash() {
            viewModelScope.launch { repository.emptyTrash() }
        }

        fun restoreSelected() {
            val ids = _selectedIds.value
            if (ids.isEmpty()) return
            val snapshot = ids.toSet()
            viewModelScope.launch {
                repository.restoreFromTrash(snapshot)
                _selectedIds.value = emptySet()
                val action = BulkUndoableAction.Restored(snapshot)
                lastBulkAction = action
                _events.emit(HistoryEvent.BulkActionPerformed(action))
            }
        }

        fun archiveSelectedFromTrash() {
            val ids = _selectedIds.value
            if (ids.isEmpty()) return
            val snapshot = ids.toSet()
            viewModelScope.launch {
                repository.archiveNotes(snapshot)
                _selectedIds.value = emptySet()
                val action = BulkUndoableAction.ArchivedFromTrash(snapshot)
                lastBulkAction = action
                _events.emit(HistoryEvent.BulkActionPerformed(action))
            }
        }

        fun unarchiveSelected() {
            val ids = _selectedIds.value
            if (ids.isEmpty()) return
            val snapshot = ids.toSet()
            viewModelScope.launch {
                repository.unarchiveNotes(snapshot)
                _selectedIds.value = emptySet()
                val action = BulkUndoableAction.Unarchived(snapshot)
                lastBulkAction = action
                _events.emit(HistoryEvent.BulkActionPerformed(action))
            }
        }

        fun moveSelectedArchivedToTrash() {
            val ids = _selectedIds.value
            if (ids.isEmpty()) return
            val snapshot = ids.toSet()
            viewModelScope.launch {
                repository.moveToTrash(snapshot)
                _selectedIds.value = emptySet()
                val action = BulkUndoableAction.MovedArchiveToTrash(snapshot)
                lastBulkAction = action
                _events.emit(HistoryEvent.BulkActionPerformed(action))
            }
        }

        /**
         * Permanent delete is intentionally not undoable - rows are gone and reviving
         * them isn't possible. The screen guards this path with a confirmation
         * AppBottomSheet before invoking us. We clear [lastBulkAction] so a stale undo
         * from an earlier action doesn't accidentally fire after the user confirms.
         */
        fun deleteSelectedForever() {
            val ids = _selectedIds.value
            if (ids.isEmpty()) return
            val snapshot = ids.toSet()
            viewModelScope.launch {
                repository.deleteForever(snapshot)
                _selectedIds.value = emptySet()
                lastBulkAction = null
            }
        }

        /**
         * Reverses the most recent bulk action by issuing the inverse repository call.
         * Mirrors [HomeViewModel.undoLastBulkAction]; see [BulkUndoableAction] for the
         * full inverse mapping table.
         */
        fun undoLastBulkAction() {
            val action = lastBulkAction ?: return
            lastBulkAction = null
            viewModelScope.launch {
                when (action) {
                    is BulkUndoableAction.Archived -> repository.unarchiveNotes(action.ids)
                    is BulkUndoableAction.Trashed -> repository.restoreFromTrash(action.ids)
                    is BulkUndoableAction.MarkedDone ->
                        if (action.snapshots.isNotEmpty()) {
                            repository.restoreCompletionStates(action.snapshots)
                        } else {
                            repository.markIncomplete(action.ids)
                        }
                    is BulkUndoableAction.Restored -> repository.moveToTrash(action.ids)
                    is BulkUndoableAction.Unarchived -> repository.archiveNotes(action.ids)
                    is BulkUndoableAction.ArchivedFromTrash -> repository.moveToTrash(action.ids)
                    is BulkUndoableAction.MovedArchiveToTrash -> repository.archiveNotes(action.ids)
                }
            }
        }

        /**
         * Returns the number of whole days remaining before [note] is auto-deleted from the trash.
         * A non-trashed note or a note whose [trashedAt] is null returns null so the caller can
         * suppress the badge.
         */
        fun daysLeftInTrash(note: NoteWithItems): Int? {
            val trashedAt = note.note.trashedAt ?: return null
            val millisLeft = (trashedAt + NoteRepository.TRASH_RETENTION_MILLIS) - nowAtCreation
            if (millisLeft <= 0) return 0
            // Integer division on (millis / day) rounds down; add one day so the user sees
            // "1 day left" on the last day rather than "0 days left".
            return max(0, (millisLeft / (24L * 60L * 60L * 1000L)).toInt())
        }
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HistoryRoute(
    interactionPrefs: InteractionPrefs,
    section: HistorySection,
    onSectionChange: (HistorySection) -> Unit,
    onVisibleItemCountChange: (Int) -> Unit,
    onOpenNote: (NoteWithItems, Boolean) -> Unit,
) {
    val vm: HistoryViewModel = hiltViewModel()
    val trashed by vm.trashedItems.collectAsStateWithLifecycle()
    val archived by vm.archivedItems.collectAsStateWithLifecycle()
    val interactionState by interactionPrefs.state.collectAsStateWithLifecycle(
        initialValue =
            dev.bikram.remember.data
                .InteractionState(),
    )
    val selectedIds by vm.selectedIds.collectAsStateWithLifecycle()
    val archivedListState = rememberLazyListState()
    val trashedListState = rememberLazyListState()
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)
    val blurStyle = rememberProgressiveBlurStyle()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pillInset = navBarInset + PillBottomBarHeight + PillBottomScrimExtra
    val blurMod = remember(blurStyle) { blurStyle?.applyToScrollableList() ?: Modifier }

    val items =
        when (section) {
            HistorySection.ARCHIVE -> archived
            HistorySection.TRASH -> trashed
        }
    val rows =
        remember(items) {
            items.map { noteWithItems ->
                HistoryNoteRow(
                    note = noteWithItems,
                    card = noteWithItems.toNoteCardUiModel(),
                )
            }
        }
    val listState =
        when (section) {
            HistorySection.ARCHIVE -> archivedListState
            HistorySection.TRASH -> trashedListState
        }
    val listScrollEnabled =
        rememberContentOverflowScrollEnabled(
            listState = listState,
            additionalScrollEnabled = topBarState.collapsedFraction > 0f,
        )
    val selectableVisibleIds = remember(rows) { rows.map { row -> row.card.id }.toSet() }
    val inSelectionMode = selectedIds.isNotEmpty()
    val snackbarHostState = LocalSnackbarHostState.current
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.bulk_action_undo)
    // Confirmation sheet state for the selection-mode delete-forever path. Permanent
    // delete has no undo so the user has to confirm explicitly before any rows go.
    // saveable so a config change mid-confirmation does not blow the dialog away.
    var bulkDeleteForeverOpen by rememberSaveable { mutableStateOf(false) }
    // Single-card delete-forever confirmation. Held as a NoteWithItems? so the dialog
    // also has the row context if we ever want to mention the note's title; the value
    // is non-saveable (NoteWithItems isn't Parcelable) so a config change while the
    // dialog is up dismisses it - acceptable for a transient permanent-delete prompt.
    var pendingDeleteForeverNote by remember { mutableStateOf<NoteWithItems?>(null) }

    BackHandler(enabled = inSelectionMode) { vm.clearSelection() }
    LaunchedEffect(section) { vm.clearSelection() }
    LaunchedEffect(selectableVisibleIds) { vm.pruneSelection(selectableVisibleIds) }
    LaunchedEffect(section, rows.size) { onVisibleItemCountChange(rows.size) }
    LaunchedEffect(section, rows.isEmpty()) {
        if (rows.isEmpty()) {
            topBarState.heightOffset = 0f
            topBarState.contentOffset = 0f
        }
    }
    LaunchedEffect(vm, snackbarHostState, context, undoLabel) {
        vm.events.collect { event ->
            when (event) {
                is HistoryEvent.BulkActionPerformed -> {
                    val message = bulkActionSnackbarMessage(context, event.action)
                    val result =
                        snackbarHostState.showSnackbar(
                            message = message,
                            actionLabel = undoLabel,
                            withDismissAction = true,
                            duration = SnackbarDuration.Short,
                        )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.undoLastBulkAction()
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        bottomBar = {
            HistorySelectionActionBar(
                visible = inSelectionMode,
                section = section,
                onClearSelection = vm::clearSelection,
                onRestoreSelected = vm::restoreSelected,
                onArchiveSelected = vm::archiveSelectedFromTrash,
                // Permanent delete is gated by a confirmation sheet; the actual VM
                // call only fires after the user taps Delete in that sheet.
                onDeleteForeverSelected = { bulkDeleteForeverOpen = true },
                onUnarchiveSelected = vm::unarchiveSelected,
                onTrashSelected = vm::moveSelectedArchivedToTrash,
                bottomPadding = navBarInset + PillBottomBarHeight + PillBottomScrimExtra + 24.dp,
            )
        },
        topBar = {
            LargeTopAppBar(
                colors = transparentLargeTopAppBarColors(),
                title = {
                    Text(
                        text = stringResource(R.string.main_tab_history),
                        style = MaterialTheme.typography.headlineLargeEmphasized,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    if (inSelectionMode) {
                        val cdSelectAll = stringResource(R.string.home_select_all)
                        Box(modifier = Modifier.size(48.dp)) {
                            RememberFilledTonalIconButton(
                                onClick = { vm.selectNotes(selectableVisibleIds) },
                                enabled = selectableVisibleIds.isNotEmpty(),
                                modifier = Modifier.align(Alignment.Center),
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "select_all",
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.semantics { contentDescription = cdSelectAll },
                                )
                            }
                            Badge(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .offset(x = 2.dp, y = (-2).dp),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ) {
                                Text(
                                    text = selectedIds.size.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        val cdUnselectAll = stringResource(R.string.home_unselect_all)
                        RememberFilledTonalIconButton(onClick = vm::clearSelection) {
                            RememberMaterialRoundedSymbol(
                                name = "deselect",
                                weight = FontWeight.Medium,
                                modifier = Modifier.semantics { contentDescription = cdUnselectAll },
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        // The Box fills the full Scaffold area so the progressive blur (which fades content
        // near the very top of the screen, behind the LargeTopAppBar) sees the same bounds
        // it always has - applying blur here, not on the inner Column, keeps the fade band
        // anchored to the top of the screen instead of riding down on top of the toggle.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(blurMod),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = padding.calculateTopPadding() + 4.dp),
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                ) {
                    val entries = HistorySection.entries
                    entries.forEachIndexed { index, entry ->
                        RememberSegmentedButton(
                            selected = section == entry,
                            onClick = { onSectionChange(entry) },
                            shape = SegmentedButtonDefaults.itemShape(index, entries.size),
                            label = { Text(entry.label()) },
                        )
                    }
                }
                if (section == HistorySection.TRASH && rows.isNotEmpty()) {
                    RetentionNotice(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                }
                if (rows.isNotEmpty()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding =
                            PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = pillInset + 24.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        userScrollEnabled = listScrollEnabled,
                    ) {
                        items(
                            items = rows,
                            key = { row -> row.card.id },
                            contentType = { if (section == HistorySection.TRASH) "trashedRow" else "archivedRow" },
                        ) { row ->
                            val noteId = row.card.id
                            val isSelected = noteId in selectedIds
                            HistorySwipeCard(
                                model = row.card,
                                section = section,
                                daysLeft = vm.daysLeftInTrash(row.note).takeIf { section == HistorySection.TRASH },
                                hapticEnabled = interactionState.hapticFeedbackEnabled,
                                onOpenNote = {
                                    if (inSelectionMode) {
                                        vm.toggleSelection(noteId)
                                    } else {
                                        onOpenNote(row.note, false)
                                    }
                                },
                                onRestore = { vm.restore(row.note) },
                                onArchive = { vm.archiveFromTrash(row.note) },
                                onDeleteForever = { pendingDeleteForeverNote = row.note },
                                onUnarchive = { vm.unarchive(row.note) },
                                onMoveToTrash = { vm.moveArchivedToTrash(row.note) },
                                selected = isSelected,
                                onLongClick = { vm.toggleSelection(noteId) },
                                swipeEnabled = !inSelectionMode,
                            )
                        }
                    }
                }
            }
            if (rows.isEmpty()) {
                // Top padding pushes the centre below the LargeTopAppBar; bottom padding
                // raises it above the floating pill bar. The two together let Alignment.Center
                // land on the true midpoint of the visible viewport instead of the midpoint
                // of the full Scaffold area.
                EmptyState(
                    section = section,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .padding(top = padding.calculateTopPadding(), bottom = pillInset),
                )
            }
        }
    }

    pendingDeleteForeverNote?.let { noteToDelete ->
        // Single-card delete-forever confirmation. Mirrors the bulk sheet so users
        // get the same warning regardless of whether they triggered the delete from
        // the swipe action on one card or from selection mode on many.
        AppBottomSheet(
            title = stringResource(R.string.bulk_delete_forever_confirm_title),
            subtitle = stringResource(R.string.bulk_delete_forever_confirm_subtitle),
            onDismiss = { pendingDeleteForeverNote = null },
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
            subtitleSpacing = 12.dp,
            actions = {
                RememberTextButton(onClick = { pendingDeleteForeverNote = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
                RememberTextButton(onClick = {
                    pendingDeleteForeverNote = null
                    vm.deleteForever(noteToDelete)
                }) {
                    Text(stringResource(R.string.edit_bottom_bar_delete_forever))
                }
            },
        ) {
            // Subtitle covers the warning; no extra body content.
        }
    }

    if (bulkDeleteForeverOpen) {
        // Mirrors the existing main-tab "Empty trash" sheet so the confirmation pattern
        // is consistent across the app. The selected-count snapshot is captured at open
        // time; the selection set itself is unchanged until the user confirms.
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
                RememberTextButton(onClick = {
                    bulkDeleteForeverOpen = false
                    vm.deleteSelectedForever()
                }) {
                    Text(stringResource(R.string.edit_bottom_bar_delete_forever))
                }
            },
        ) {
            // The subtitle covers the warning fully; no extra body content.
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun HistorySelectionActionBar(
    visible: Boolean,
    section: HistorySection,
    onClearSelection: () -> Unit,
    onRestoreSelected: () -> Unit,
    onArchiveSelected: () -> Unit,
    onDeleteForeverSelected: () -> Unit,
    onUnarchiveSelected: () -> Unit,
    onTrashSelected: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraExtraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
            ) {
                ButtonGroup(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    val exitLabel = stringResource(R.string.home_select_exit_cd)
                    val exitInteractionSource = remember { MutableInteractionSource() }
                    RememberFilledTonalIconButton(
                        onClick = onClearSelection,
                        modifier = Modifier.animateWidth(exitInteractionSource),
                        interactionSource = exitInteractionSource,
                        tooltipLabel = exitLabel,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "close",
                            weight = FontWeight.Medium,
                            modifier = Modifier.semantics { contentDescription = exitLabel },
                        )
                    }
                    if (section == HistorySection.TRASH) {
                        val restoreLabel = stringResource(R.string.edit_bottom_bar_restore)
                        val cdRestore = stringResource(R.string.edit_bottom_bar_restore_cd)
                        val restoreInteractionSource = remember { MutableInteractionSource() }
                        RememberFilledTonalIconButton(
                            onClick = onRestoreSelected,
                            modifier = Modifier.animateWidth(restoreInteractionSource),
                            interactionSource = restoreInteractionSource,
                            tooltipLabel = restoreLabel,
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "restore_from_trash",
                                weight = FontWeight.Medium,
                                modifier = Modifier.semantics { contentDescription = cdRestore },
                            )
                        }
                        val archiveLabel = stringResource(R.string.edit_bottom_bar_archive)
                        val cdArchive = stringResource(R.string.edit_bottom_bar_archive_cd)
                        val archiveInteractionSource = remember { MutableInteractionSource() }
                        RememberFilledTonalIconButton(
                            onClick = onArchiveSelected,
                            modifier = Modifier.animateWidth(archiveInteractionSource),
                            interactionSource = archiveInteractionSource,
                            tooltipLabel = archiveLabel,
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "archive",
                                weight = FontWeight.Medium,
                                modifier = Modifier.semantics { contentDescription = cdArchive },
                            )
                        }
                    } else {
                        val unarchiveLabel = stringResource(R.string.edit_bottom_bar_unarchive)
                        val cdUnarchive = stringResource(R.string.edit_bottom_bar_unarchive_cd)
                        val unarchiveInteractionSource = remember { MutableInteractionSource() }
                        RememberFilledTonalIconButton(
                            onClick = onUnarchiveSelected,
                            modifier = Modifier.animateWidth(unarchiveInteractionSource),
                            interactionSource = unarchiveInteractionSource,
                            tooltipLabel = unarchiveLabel,
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "unarchive",
                                weight = FontWeight.Medium,
                                modifier = Modifier.semantics { contentDescription = cdUnarchive },
                            )
                        }
                        val trashLabel = stringResource(R.string.common_move_to_trash)
                        val cdTrash = stringResource(R.string.history_archive_move_to_trash_cd)
                        val trashInteractionSource = remember { MutableInteractionSource() }
                        RememberFilledTonalIconButton(
                            onClick = onTrashSelected,
                            modifier = Modifier.animateWidth(trashInteractionSource),
                            interactionSource = trashInteractionSource,
                            tooltipLabel = trashLabel,
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "delete_sweep",
                                weight = FontWeight.Medium,
                                modifier = Modifier.semantics { contentDescription = cdTrash },
                            )
                        }
                    }
                    val deleteForeverLabel = stringResource(R.string.edit_bottom_bar_delete_forever)
                    val cdDeleteForever = stringResource(R.string.edit_bottom_bar_delete_forever_cd)
                    val deleteForeverInteractionSource = remember { MutableInteractionSource() }
                    RememberFilledTonalIconButton(
                        onClick = onDeleteForeverSelected,
                        modifier = Modifier.animateWidth(deleteForeverInteractionSource),
                        interactionSource = deleteForeverInteractionSource,
                        tooltipLabel = deleteForeverLabel,
                        colors =
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "delete_forever",
                            size = 20.dp,
                            weight = FontWeight.Medium,
                            modifier = Modifier.semantics { contentDescription = cdDeleteForever },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RetentionNotice(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.shapes.large,
                ).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberMaterialRoundedSymbol(
            name = "schedule",
            size = 18.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.history_trash_retention_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistorySwipeCard(
    model: NoteCardUiModel,
    section: HistorySection,
    daysLeft: Int?,
    hapticEnabled: Boolean,
    onOpenNote: () -> Unit,
    onRestore: () -> Unit,
    onArchive: () -> Unit,
    onDeleteForever: () -> Unit,
    onUnarchive: () -> Unit,
    onMoveToTrash: () -> Unit,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    swipeEnabled: Boolean = true,
) {
    if (!swipeEnabled) {
        Box(Modifier.fillMaxWidth()) {
            NoteCard(
                model = model,
                onClick = onOpenNote,
                selected = selected,
                onLongClick = onLongClick,
            )
            if (daysLeft != null) {
                TrashDaysLeftBadge(
                    daysLeft = daysLeft,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(
                                top = if (selected) 42.dp else 10.dp,
                                end = 10.dp,
                            ),
                )
            }
        }
        return
    }

    val startActions =
        when (section) {
            HistorySection.ARCHIVE ->
                listOf(
                    SwipeRevealTile(
                        key = "unarchive",
                        labelRes = R.string.edit_bottom_bar_unarchive,
                        symbolName = "unarchive",
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = onUnarchive,
                    ),
                )
            HistorySection.TRASH ->
                listOf(
                    SwipeRevealTile(
                        key = "restore",
                        labelRes = R.string.edit_bottom_bar_restore,
                        symbolName = "restore_from_trash",
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = onRestore,
                    ),
                    SwipeRevealTile(
                        key = "archive",
                        labelRes = R.string.edit_bottom_bar_archive,
                        symbolName = "archive",
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = onArchive,
                    ),
                )
        }
    val endActions =
        when (section) {
            HistorySection.ARCHIVE ->
                listOf(
                    SwipeRevealTile(
                        key = "trash",
                        labelRes = R.string.edit_bottom_bar_trash,
                        symbolName = "delete",
                        backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = onMoveToTrash,
                    ),
                )
            HistorySection.TRASH ->
                listOf(
                    SwipeRevealTile(
                        key = "delete_forever",
                        labelRes = R.string.edit_bottom_bar_delete_forever,
                        symbolName = "delete_forever",
                        backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = onDeleteForever,
                    ),
                )
        }
    MultiActionSwipeRevealCard(
        startActions = startActions,
        endActions = endActions,
        cardShape = MaterialTheme.shapes.medium,
        hapticEnabled = hapticEnabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth()) {
            NoteCard(
                model = model,
                onClick = onOpenNote,
                selected = selected,
                onLongClick = onLongClick,
            )
            if (daysLeft != null) {
                TrashDaysLeftBadge(
                    daysLeft = daysLeft,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(
                                top = if (selected) 42.dp else 10.dp,
                                end = 10.dp,
                            ),
                )
            }
        }
    }
}

@Composable
private fun TrashDaysLeftBadge(
    daysLeft: Int,
    modifier: Modifier = Modifier,
) {
    val label =
        if (daysLeft <= 0) {
            stringResource(R.string.history_expires_today)
        } else {
            pluralStringResource(R.plurals.history_days_left, daysLeft, daysLeft)
        }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color =
            if (daysLeft <= 3) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        modifier =
            modifier
                .background(
                    if (daysLeft <= 3) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    MaterialTheme.shapes.extraExtraLarge,
                ).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun EmptyState(
    section: HistorySection,
    modifier: Modifier = Modifier,
) {
    val titleRes =
        if (section == HistorySection.ARCHIVE) {
            R.string.history_archive_empty_title
        } else {
            R.string.history_trash_empty_title
        }
    val subtitleRes =
        if (section == HistorySection.ARCHIVE) {
            R.string.history_archive_empty_subtitle
        } else {
            R.string.history_trash_empty_subtitle
        }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 24.dp),
    ) {
        if (section == HistorySection.ARCHIVE) {
            EmptyArchiveIllustration()
        } else {
            EmptyTrashIllustration()
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(subtitleRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistorySection.label(): String =
    when (this) {
        HistorySection.ARCHIVE -> stringResource(R.string.history_section_archive)
        HistorySection.TRASH -> stringResource(R.string.history_section_trash)
    }
