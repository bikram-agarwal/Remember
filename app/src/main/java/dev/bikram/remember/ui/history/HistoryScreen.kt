package dev.bikram.remember.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.bikram.remember.R
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.NoteLayoutMode
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.ViewOptionsPrefs
import dev.bikram.remember.ui.common.BulkUndoableAction
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.bulkActionSnackbarMessage
import dev.bikram.remember.ui.common.emptyStateSpacing
import dev.bikram.remember.ui.common.isLandscape
import dev.bikram.remember.ui.common.noteMosaicColumnCount
import dev.bikram.remember.ui.common.rememberNotificationsAllowed
import dev.bikram.remember.ui.components.EmptyArchiveIllustration
import dev.bikram.remember.ui.components.EmptyTrashIllustration
import dev.bikram.remember.ui.components.MultiActionSwipeRevealCard
import dev.bikram.remember.ui.components.NoteCard
import dev.bikram.remember.ui.components.NoteCardUiModel
import dev.bikram.remember.ui.components.RememberConfirmDialog
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton
import dev.bikram.remember.ui.components.RememberToggleButton
import dev.bikram.remember.ui.components.SwipeRevealTile
import dev.bikram.remember.ui.components.rememberResponsiveActionButtonSize
import dev.bikram.remember.ui.components.rememberSelectionActionBarListClearance
import dev.bikram.remember.ui.components.toNoteCardUiModel
import dev.bikram.remember.ui.modifiers.PillBottomBarHeight
import dev.bikram.remember.ui.modifiers.PillBottomScrimExtra
import dev.bikram.remember.ui.modifiers.applyToScrollableList
import dev.bikram.remember.ui.modifiers.rememberContentOverflowScrollEnabled
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import dev.bikram.remember.ui.theme.LocalReducedMotion
import dev.bikram.remember.ui.theme.LocalSnackbarHostState
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

/** Which shelf the user is viewing. */
enum class HistorySection { ARCHIVE, TRASH }

/** Toolbar row below the status bar (Archive / Trash toggles). */
private val HistoryTopBarContentHeight = 48.dp

/** Extra progressive-blur band past the toggle bottoms so scroll content fades under them. */
private val HistoryTopBarBlurBelowExtra = 20.dp

/** Softer top blur curve so strength reaches the Archive / Trash row (default 2.5 is mostly status-bar only). */
private const val HISTORY_TOP_BLUR_PROGRESS_POWER = 1.1f

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
        viewOptionsPrefs: ViewOptionsPrefs,
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

        val noteLayoutMode: StateFlow<NoteLayoutMode> =
            viewOptionsPrefs
                .state
                .map { viewOptions -> viewOptions.noteLayoutMode }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteLayoutMode.LIST)

        private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
        val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

        /**
         * One-shot events for the UI: bulk-action completions surface a snackbar with
         * Undo. The repository has already coalesced row writes into a single Flow
         * emission by the time these fire, so the list has reflowed and the snackbar
         * lands on settled state. Backed by a Channel (not a SharedFlow) so emission never
         * suspends the view model, each event delivers exactly once, and nothing replays on rotation.
         */
        private val _events = Channel<HistoryEvent>(Channel.BUFFERED)
        val events: Flow<HistoryEvent> = _events.receiveAsFlow()

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
        private fun emitSingleCardAction(action: BulkUndoableAction) {
            lastBulkAction = action
            _events.trySend(HistoryEvent.BulkActionPerformed(action))
        }

        fun emptyTrash() {
            viewModelScope.launch { repository.emptyTrash() }
        }

        fun moveAllArchivedToTrash() {
            viewModelScope.launch { repository.moveAllArchivedToTrash() }
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
                _events.trySend(HistoryEvent.BulkActionPerformed(action))
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
                _events.trySend(HistoryEvent.BulkActionPerformed(action))
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
                _events.trySend(HistoryEvent.BulkActionPerformed(action))
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
                _events.trySend(HistoryEvent.BulkActionPerformed(action))
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
                        repository.markIncomplete(action.ids, action.snapshots)
                    is BulkUndoableAction.Restored -> repository.moveToTrash(action.ids)
                    // Not produced by History (pin / star are Home actions), but the exhaustive
                    // mapping keeps the inverse correct if that ever changes.
                    is BulkUndoableAction.Pinned -> repository.setPinned(action.ids, false)
                    is BulkUndoableAction.Starred -> repository.setStarred(action.ids, false)
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
    activeNoteId: Long? = null,
    showSelectionActionBar: Boolean = true,
) {
    val vm: HistoryViewModel = hiltViewModel()
    val trashed by vm.trashedItems.collectAsStateWithLifecycle()
    val archived by vm.archivedItems.collectAsStateWithLifecycle()
    val noteLayoutMode by vm.noteLayoutMode.collectAsStateWithLifecycle()
    val interactionState by interactionPrefs.state.collectAsStateWithLifecycle(
        initialValue =
            dev.bikram.remember.data
                .InteractionState(),
    )
    val selectedIds by vm.selectedIds.collectAsStateWithLifecycle()
    val archivedListState = rememberLazyListState()
    val trashedListState = rememberLazyListState()
    val archivedGridState = rememberLazyStaggeredGridState()
    val trashedGridState = rememberLazyStaggeredGridState()
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarInset = statusBarInset + HistoryTopBarContentHeight
    val blurStyle =
        rememberProgressiveBlurStyle(
            topExtra = HistoryTopBarContentHeight + HistoryTopBarBlurBelowExtra,
            topBlurProgressPower = HISTORY_TOP_BLUR_PROGRESS_POWER,
        )
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pillInset = navBarInset + PillBottomBarHeight + PillBottomScrimExtra

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
    val gridState =
        when (section) {
            HistorySection.ARCHIVE -> archivedGridState
            HistorySection.TRASH -> trashedGridState
        }
    val density = LocalDensity.current
    val topAlphaMultiplier by remember(listState, gridState, noteLayoutMode) {
        derivedStateOf {
            val thresholdPx = with(density) { 24.dp.toPx() }
            when (noteLayoutMode) {
                NoteLayoutMode.LIST ->
                    if (listState.firstVisibleItemIndex > 0) {
                        1f
                    } else {
                        val offsetPx = listState.firstVisibleItemScrollOffset.toFloat()
                        (offsetPx / thresholdPx).coerceIn(0f, 1f)
                    }
                NoteLayoutMode.MOSAIC ->
                    if (gridState.firstVisibleItemIndex > 0) {
                        1f
                    } else {
                        val offsetPx = gridState.firstVisibleItemScrollOffset.toFloat()
                        (offsetPx / thresholdPx).coerceIn(0f, 1f)
                    }
            }
        }
    }
    // Pane mode (showSelectionActionBar = false) has no floating pill over this list,
    // so the pill-sized bottom blur band is dropped.
    val listBlurMod =
        blurStyle?.applyToScrollableList(
            topAlphaMultiplier = topAlphaMultiplier,
            bottomAlphaMultiplier = if (showSelectionActionBar) 1f else 0f,
        ) ?: Modifier
    val listScrollEnabled =
        rememberContentOverflowScrollEnabled(
            listState = listState,
            additionalScrollEnabled = true,
        )
    val reducedMotion = LocalReducedMotion.current
    val historySectionSpatialSpec =
        reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>())
    val historySectionFadeInSpec =
        reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val historySectionFadeOutSpec =
        reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    val selectableVisibleIds = remember(rows) { rows.map { row -> row.card.id }.toSet() }
    val inSelectionMode = selectedIds.isNotEmpty()
    val snackbarHostState = LocalSnackbarHostState.current
    val context = LocalContext.current
    val isLandscape = isLandscape()
    val notificationsAllowed = rememberNotificationsAllowed()
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
    var revealedNoteCardId by rememberSaveable { mutableStateOf<Long?>(null) }
    var revealedNoteCardBounds by remember { mutableStateOf<Rect?>(null) }
    var historyScreenBounds by remember { mutableStateOf<Rect?>(null) }

    BackHandler(enabled = inSelectionMode) { vm.clearSelection() }
    LaunchedEffect(section) { vm.clearSelection() }
    LaunchedEffect(section, noteLayoutMode) { revealedNoteCardId = null }
    LaunchedEffect(inSelectionMode) {
        if (inSelectionMode) {
            revealedNoteCardId = null
        }
    }
    LaunchedEffect(revealedNoteCardId) {
        if (revealedNoteCardId == null) {
            revealedNoteCardBounds = null
        }
    }
    LaunchedEffect(selectableVisibleIds) { vm.pruneSelection(selectableVisibleIds) }
    LaunchedEffect(section, rows.size) { onVisibleItemCountChange(rows.size) }
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

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    historyScreenBounds = coordinates.boundsInRoot()
                }.pointerInput(revealedNoteCardId, revealedNoteCardBounds) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val downChange =
                                event.changes.firstOrNull { change ->
                                    change.changedToDownIgnoreConsumed()
                                } ?: continue
                            val activeCardBounds = revealedNoteCardBounds
                            val screenBounds = historyScreenBounds
                            val tapPositionInRoot =
                                if (screenBounds != null) {
                                    Offset(
                                        x = screenBounds.left + downChange.position.x,
                                        y = screenBounds.top + downChange.position.y,
                                    )
                                } else {
                                    downChange.position
                                }
                            if (
                                revealedNoteCardId != null &&
                                activeCardBounds != null &&
                                !activeCardBounds.contains(tapPositionInRoot)
                            ) {
                                revealedNoteCardId = null
                            }
                        }
                    }
                },
    ) {
        // Blur only on the scroll layer (Home pattern). Top bar sits above so scrim/blur do not
        // paint over the toggles; list contentPadding lets rows scroll under the transparent bar.
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = section,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (reducedMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (
                            slideInHorizontally(animationSpec = historySectionSpatialSpec) { fullWidth ->
                                direction * fullWidth
                            } + fadeIn(animationSpec = historySectionFadeInSpec)
                        ) togetherWith (
                            slideOutHorizontally(animationSpec = historySectionSpatialSpec) { fullWidth ->
                                -direction * fullWidth
                            } + fadeOut(animationSpec = historySectionFadeOutSpec)
                        )
                    }.using(SizeTransform(clip = false))
                },
                label = "historySectionContent",
            ) { targetSection ->
                val targetItems =
                    when (targetSection) {
                        HistorySection.ARCHIVE -> archived
                        HistorySection.TRASH -> trashed
                    }
                val targetRows =
                    remember(targetItems) {
                        targetItems.map { noteWithItems ->
                            HistoryNoteRow(
                                note = noteWithItems,
                                card = noteWithItems.toNoteCardUiModel(),
                            )
                        }
                    }
                val targetListState =
                    when (targetSection) {
                        HistorySection.ARCHIVE -> archivedListState
                        HistorySection.TRASH -> trashedListState
                    }
                val targetGridState =
                    when (targetSection) {
                        HistorySection.ARCHIVE -> archivedGridState
                        HistorySection.TRASH -> trashedGridState
                    }
                val targetListScrollEnabled =
                    rememberContentOverflowScrollEnabled(
                        listState = targetListState,
                        additionalScrollEnabled = true,
                    )
                val listTopPadding =
                    remember(targetSection, targetRows.isNotEmpty()) {
                        if (targetRows.isEmpty()) {
                            topBarInset
                        } else if (targetSection == HistorySection.TRASH) {
                            topBarInset + 4.dp
                        } else {
                            topBarInset + 12.dp
                        }
                    }
                val selectionBarClearance =
                    rememberSelectionActionBarListClearance(
                        visible = showSelectionActionBar && inSelectionMode,
                    )
                if (targetRows.isNotEmpty()) {
                    val contentPadding =
                        PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = listTopPadding,
                            bottom = pillInset + 24.dp + selectionBarClearance,
                        )
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val mosaicColumnCount = noteMosaicColumnCount(maxWidth)
                        if (noteLayoutMode == NoteLayoutMode.MOSAIC && mosaicColumnCount > 1) {
                            LazyVerticalStaggeredGrid(
                                columns = StaggeredGridCells.Fixed(mosaicColumnCount),
                                state = targetGridState,
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .then(listBlurMod),
                                contentPadding = contentPadding,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalItemSpacing = 6.dp,
                            ) {
                                if (targetSection == HistorySection.TRASH) {
                                    item(
                                        key = "retention_notice",
                                        contentType = "retention",
                                        span = StaggeredGridItemSpan.FullLine,
                                    ) {
                                        RetentionNotice(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(
                                                        top = HistoryTopBarBlurBelowExtra,
                                                        bottom = 12.dp,
                                                    ),
                                        )
                                    }
                                }
                                targetRows.forEach { row ->
                                    item(
                                        key = row.card.id,
                                        contentType =
                                            if (targetSection == HistorySection.TRASH) {
                                                "trashedRow"
                                            } else {
                                                "archivedRow"
                                            },
                                    ) {
                                        val noteId = row.card.id
                                        val isSelected = noteId in selectedIds
                                        val isActiveInDetailPane = !inSelectionMode && activeNoteId == noteId
                                        var cardBounds by remember { mutableStateOf<Rect?>(null) }
                                        HistorySwipeCard(
                                            model = row.card,
                                            section = targetSection,
                                            daysLeft =
                                                vm
                                                    .daysLeftInTrash(row.note)
                                                    .takeIf { targetSection == HistorySection.TRASH },
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
                                            activeInDetailPane = isActiveInDetailPane,
                                            onLongClick = { vm.toggleSelection(noteId) },
                                            swipeEnabled = !inSelectionMode,
                                            reminderNotificationsAllowed = notificationsAllowed,
                                            modifier =
                                                Modifier.onGloballyPositioned { coordinates ->
                                                    cardBounds = coordinates.boundsInRoot()
                                                    if (revealedNoteCardId == noteId) {
                                                        revealedNoteCardBounds = cardBounds
                                                    }
                                                },
                                            revealKey = noteId,
                                            activeRevealKey = revealedNoteCardId,
                                            onRevealStarted = {
                                                revealedNoteCardId = noteId
                                                revealedNoteCardBounds = cardBounds
                                            },
                                            onRevealClosed = {
                                                if (revealedNoteCardId == noteId) {
                                                    revealedNoteCardId = null
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                state = targetListState,
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .then(listBlurMod),
                                contentPadding = contentPadding,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                userScrollEnabled = targetListScrollEnabled,
                            ) {
                                if (targetSection == HistorySection.TRASH) {
                                    item(key = "retention_notice", contentType = "retention") {
                                        RetentionNotice(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(
                                                        top = HistoryTopBarBlurBelowExtra,
                                                        bottom = 12.dp,
                                                    ),
                                        )
                                    }
                                }
                                items(
                                    items = targetRows,
                                    key = { row -> row.card.id },
                                    contentType = {
                                        if (targetSection == HistorySection.TRASH) {
                                            "trashedRow"
                                        } else {
                                            "archivedRow"
                                        }
                                    },
                                ) { row ->
                                    val noteId = row.card.id
                                    val isSelected = noteId in selectedIds
                                    val isActiveInDetailPane = !inSelectionMode && activeNoteId == noteId
                                    var cardBounds by remember { mutableStateOf<Rect?>(null) }
                                    HistorySwipeCard(
                                        model = row.card,
                                        section = targetSection,
                                        daysLeft =
                                            vm
                                                .daysLeftInTrash(row.note)
                                                .takeIf { targetSection == HistorySection.TRASH },
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
                                        activeInDetailPane = isActiveInDetailPane,
                                        onLongClick = { vm.toggleSelection(noteId) },
                                        swipeEnabled = !inSelectionMode,
                                        reminderNotificationsAllowed = notificationsAllowed,
                                        modifier =
                                            Modifier.onGloballyPositioned { coordinates ->
                                                cardBounds = coordinates.boundsInRoot()
                                                if (revealedNoteCardId == noteId) {
                                                    revealedNoteCardBounds = cardBounds
                                                }
                                            },
                                        revealKey = noteId,
                                        activeRevealKey = revealedNoteCardId,
                                        onRevealStarted = {
                                            revealedNoteCardId = noteId
                                            revealedNoteCardBounds = cardBounds
                                        },
                                        onRevealClosed = {
                                            if (revealedNoteCardId == noteId) {
                                                revealedNoteCardId = null
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if (isLandscape) {
                                        Modifier.padding(top = topBarInset)
                                    } else {
                                        Modifier
                                    },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            section = targetSection,
                        )
                    }
                }
            }
            HistoryScreenTopBar(
                section = section,
                onSectionChange = onSectionChange,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(2f),
            )
        }
        if (showSelectionActionBar) {
            HistorySelectionActionBar(
                visible = inSelectionMode,
                section = section,
                selectedCount = selectedIds.size,
                selectableVisibleIds = selectableVisibleIds,
                onClearSelection = vm::clearSelection,
                onSelectAll = { vm.selectNotes(selectableVisibleIds) },
                onUnselectAll = vm::clearSelection,
                onRestoreSelected = vm::restoreSelected,
                onArchiveSelected = vm::archiveSelectedFromTrash,
                // Permanent delete is gated by a confirmation sheet; the actual VM
                // call only fires after the user taps Delete in that sheet.
                onDeleteForeverSelected = { bulkDeleteForeverOpen = true },
                onUnarchiveSelected = vm::unarchiveSelected,
                onTrashSelected = vm::moveSelectedArchivedToTrash,
                bottomPadding = navBarInset + PillBottomBarHeight + PillBottomScrimExtra + 24.dp,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    pendingDeleteForeverNote?.let { noteToDelete ->
        // Single-card delete-forever confirmation. Mirrors the bulk dialog so users
        // get the same warning regardless of whether they triggered the delete from
        // the swipe action on one card or from selection mode on many.
        RememberConfirmDialog(
            title = stringResource(R.string.bulk_delete_forever_confirm_title),
            text = stringResource(R.string.bulk_delete_forever_confirm_subtitle),
            confirmLabel = stringResource(R.string.edit_bottom_bar_delete_forever),
            onConfirm = {
                pendingDeleteForeverNote = null
                vm.deleteForever(noteToDelete)
            },
            onDismiss = { pendingDeleteForeverNote = null },
            destructive = true,
        )
    }

    if (bulkDeleteForeverOpen) {
        // Mirrors the single-card dialog so the confirmation pattern is consistent. The
        // selected-count snapshot is captured at open time; the selection set itself is
        // unchanged until the user confirms.
        RememberConfirmDialog(
            title = stringResource(R.string.bulk_delete_forever_confirm_title),
            text = stringResource(R.string.bulk_delete_forever_confirm_subtitle),
            confirmLabel = stringResource(R.string.edit_bottom_bar_delete_forever),
            onConfirm = {
                bulkDeleteForeverOpen = false
                vm.deleteSelectedForever()
            },
            onDismiss = { bulkDeleteForeverOpen = false },
            destructive = true,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistorySelectionActionBar(
    visible: Boolean,
    section: HistorySection,
    selectedCount: Int,
    selectableVisibleIds: Set<Long>,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onUnselectAll: () -> Unit,
    onRestoreSelected: () -> Unit,
    onArchiveSelected: () -> Unit,
    onDeleteForeverSelected: () -> Unit,
    onUnarchiveSelected: () -> Unit,
    onTrashSelected: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val spatialSpec =
        reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>())
    val fadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val fadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter =
                expandVertically(animationSpec = spatialSpec, expandFrom = Alignment.Bottom) +
                    fadeIn(animationSpec = fadeInSpec),
            exit =
                shrinkVertically(animationSpec = spatialSpec, shrinkTowards = Alignment.Bottom) +
                    fadeOut(animationSpec = fadeOutSpec),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraExtraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
            ) {
                val exitLabel = stringResource(R.string.home_select_exit_cd)
                val exitInteractionSource = remember { MutableInteractionSource() }
                val exitColors =
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                val cdSelectAll = stringResource(R.string.home_select_all)
                val selectAllInteractionSource = remember { MutableInteractionSource() }
                val cdUnselectAll = stringResource(R.string.home_unselect_all)
                val unselectAllInteractionSource = remember { MutableInteractionSource() }
                val unselectAllColors =
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                val restoreLabel = stringResource(R.string.edit_bottom_bar_restore)
                val cdRestore = stringResource(R.string.edit_bottom_bar_restore_cd)
                val restoreInteractionSource = remember { MutableInteractionSource() }
                val archiveLabel = stringResource(R.string.edit_bottom_bar_archive)
                val cdArchive = stringResource(R.string.edit_bottom_bar_archive_cd)
                val archiveInteractionSource = remember { MutableInteractionSource() }
                val unarchiveLabel = stringResource(R.string.edit_bottom_bar_unarchive)
                val cdUnarchive = stringResource(R.string.edit_bottom_bar_unarchive_cd)
                val unarchiveInteractionSource = remember { MutableInteractionSource() }
                val trashLabel = stringResource(R.string.common_move_to_trash)
                val cdTrash = stringResource(R.string.history_archive_move_to_trash_cd)
                val trashInteractionSource = remember { MutableInteractionSource() }
                val deleteForeverLabel = stringResource(R.string.edit_bottom_bar_delete_forever)
                val cdDeleteForever = stringResource(R.string.edit_bottom_bar_delete_forever_cd)
                val deleteForeverInteractionSource = remember { MutableInteractionSource() }
                val deleteForeverColors =
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                val actionButtonSize = rememberResponsiveActionButtonSize()
                ButtonGroup(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    overflowIndicator = { menuState ->
                        ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                    },
                ) {
                    customItem(
                        buttonGroupContent = {
                            RememberFilledTonalIconButton(
                                onClick = onClearSelection,
                                modifier = Modifier.size(actionButtonSize).animateWidth(exitInteractionSource),
                                interactionSource = exitInteractionSource,
                                tooltipLabel = exitLabel,
                                colors = exitColors,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "close",
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.semantics { contentDescription = exitLabel },
                                )
                            }
                        },
                        menuContent = { menuState ->
                            RememberDropdownMenuItem(
                                text = { Text(exitLabel) },
                                onClick = {
                                    onClearSelection()
                                    menuState.dismiss()
                                },
                            )
                        },
                    )
                    customItem(
                        buttonGroupContent = {
                            Box(modifier = Modifier.size(actionButtonSize)) {
                                RememberFilledTonalIconButton(
                                    onClick = onSelectAll,
                                    enabled = selectableVisibleIds.isNotEmpty(),
                                    modifier =
                                        Modifier
                                            .align(Alignment.Center)
                                            .size(actionButtonSize)
                                            .animateWidth(selectAllInteractionSource),
                                    interactionSource = selectAllInteractionSource,
                                    tooltipLabel = cdSelectAll,
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
                                        text = selectedCount.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        },
                        menuContent = { menuState ->
                            RememberDropdownMenuItem(
                                text = { Text(cdSelectAll) },
                                onClick = {
                                    onSelectAll()
                                    menuState.dismiss()
                                },
                            )
                        },
                    )
                    customItem(
                        buttonGroupContent = {
                            RememberFilledTonalIconButton(
                                onClick = onUnselectAll,
                                enabled = selectedCount > 0,
                                modifier = Modifier.size(actionButtonSize).animateWidth(unselectAllInteractionSource),
                                interactionSource = unselectAllInteractionSource,
                                tooltipLabel = cdUnselectAll,
                                colors = unselectAllColors,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "deselect",
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.semantics { contentDescription = cdUnselectAll },
                                )
                            }
                        },
                        menuContent = { menuState ->
                            RememberDropdownMenuItem(
                                text = { Text(cdUnselectAll) },
                                onClick = {
                                    onUnselectAll()
                                    menuState.dismiss()
                                },
                            )
                        },
                    )
                    if (section == HistorySection.TRASH) {
                        customItem(
                            buttonGroupContent = {
                                RememberFilledTonalIconButton(
                                    onClick = onRestoreSelected,
                                    modifier = Modifier.size(actionButtonSize).animateWidth(restoreInteractionSource),
                                    interactionSource = restoreInteractionSource,
                                    tooltipLabel = restoreLabel,
                                ) {
                                    RememberMaterialRoundedSymbol(
                                        name = "restore_from_trash",
                                        weight = FontWeight.Medium,
                                        modifier = Modifier.semantics { contentDescription = cdRestore },
                                    )
                                }
                            },
                            menuContent = { menuState ->
                                RememberDropdownMenuItem(
                                    text = { Text(restoreLabel) },
                                    onClick = {
                                        onRestoreSelected()
                                        menuState.dismiss()
                                    },
                                )
                            },
                        )
                        customItem(
                            buttonGroupContent = {
                                RememberFilledTonalIconButton(
                                    onClick = onArchiveSelected,
                                    modifier = Modifier.size(actionButtonSize).animateWidth(archiveInteractionSource),
                                    interactionSource = archiveInteractionSource,
                                    tooltipLabel = archiveLabel,
                                ) {
                                    RememberMaterialRoundedSymbol(
                                        name = "archive",
                                        weight = FontWeight.Medium,
                                        modifier = Modifier.semantics { contentDescription = cdArchive },
                                    )
                                }
                            },
                            menuContent = { menuState ->
                                RememberDropdownMenuItem(
                                    text = { Text(archiveLabel) },
                                    onClick = {
                                        onArchiveSelected()
                                        menuState.dismiss()
                                    },
                                )
                            },
                        )
                    } else {
                        customItem(
                            buttonGroupContent = {
                                RememberFilledTonalIconButton(
                                    onClick = onUnarchiveSelected,
                                    modifier = Modifier.size(actionButtonSize).animateWidth(unarchiveInteractionSource),
                                    interactionSource = unarchiveInteractionSource,
                                    tooltipLabel = unarchiveLabel,
                                ) {
                                    RememberMaterialRoundedSymbol(
                                        name = "unarchive",
                                        weight = FontWeight.Medium,
                                        modifier = Modifier.semantics { contentDescription = cdUnarchive },
                                    )
                                }
                            },
                            menuContent = { menuState ->
                                RememberDropdownMenuItem(
                                    text = { Text(unarchiveLabel) },
                                    onClick = {
                                        onUnarchiveSelected()
                                        menuState.dismiss()
                                    },
                                )
                            },
                        )
                        customItem(
                            buttonGroupContent = {
                                RememberFilledTonalIconButton(
                                    onClick = onTrashSelected,
                                    modifier = Modifier.size(actionButtonSize).animateWidth(trashInteractionSource),
                                    interactionSource = trashInteractionSource,
                                    tooltipLabel = trashLabel,
                                ) {
                                    RememberMaterialRoundedSymbol(
                                        name = "delete_sweep",
                                        weight = FontWeight.Medium,
                                        modifier = Modifier.semantics { contentDescription = cdTrash },
                                    )
                                }
                            },
                            menuContent = { menuState ->
                                RememberDropdownMenuItem(
                                    text = { Text(trashLabel) },
                                    onClick = {
                                        onTrashSelected()
                                        menuState.dismiss()
                                    },
                                )
                            },
                        )
                    }
                    customItem(
                        buttonGroupContent = {
                            RememberFilledTonalIconButton(
                                onClick = onDeleteForeverSelected,
                                modifier = Modifier.size(actionButtonSize).animateWidth(deleteForeverInteractionSource),
                                interactionSource = deleteForeverInteractionSource,
                                tooltipLabel = deleteForeverLabel,
                                colors = deleteForeverColors,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "delete_forever",
                                    size = 20.dp,
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.semantics { contentDescription = cdDeleteForever },
                                )
                            }
                        },
                        menuContent = { menuState ->
                            RememberDropdownMenuItem(
                                text = { Text(deleteForeverLabel) },
                                onClick = {
                                    onDeleteForeverSelected()
                                    menuState.dismiss()
                                },
                            )
                        },
                    )
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
    onOpenNote: () -> Unit,
    onRestore: () -> Unit,
    onArchive: () -> Unit,
    onDeleteForever: () -> Unit,
    onUnarchive: () -> Unit,
    onMoveToTrash: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    activeInDetailPane: Boolean = false,
    swipeEnabled: Boolean = true,
    reminderNotificationsAllowed: Boolean = true,
    revealKey: Any? = null,
    activeRevealKey: Any? = null,
    onRevealStarted: (() -> Unit)? = null,
    onRevealClosed: (() -> Unit)? = null,
) {
    if (!swipeEnabled) {
        Box(modifier.fillMaxWidth()) {
            NoteCard(
                model = model,
                onClick = onOpenNote,
                selected = selected,
                activeInDetailPane = activeInDetailPane,
                onLongClick = onLongClick,
                reminderNotificationsAllowed = reminderNotificationsAllowed,
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
        modifier = modifier.fillMaxWidth(),
        revealKey = revealKey,
        activeRevealKey = activeRevealKey,
        onRevealStarted = onRevealStarted,
        onRevealClosed = onRevealClosed,
    ) {
        Box(Modifier.fillMaxWidth()) {
            NoteCard(
                model = model,
                onClick = onOpenNote,
                selected = selected,
                activeInDetailPane = activeInDetailPane,
                onLongClick = onLongClick,
                reminderNotificationsAllowed = reminderNotificationsAllowed,
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
    val spacing = emptyStateSpacing(prominent = false)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 24.dp),
    ) {
        if (section == HistorySection.ARCHIVE) {
            EmptyArchiveIllustration()
        } else {
            EmptyTrashIllustration()
        }
        Spacer(Modifier.height(spacing.titleSpacer))
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(spacing.subtitleSpacer))
        Text(
            text = stringResource(subtitleRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryScreenTopBar(
    section: HistorySection,
    onSectionChange: (HistorySection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(HistoryTopBarContentHeight)
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HistorySectionToggleGroup(
            section = section,
            onSectionChange = onSectionChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistorySectionToggleGroup(
    section: HistorySection,
    onSectionChange: (HistorySection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sectionButtonColors =
        ToggleButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    val entries = HistorySection.entries
    val labels = entries.map { entry -> entry.label() }
    val shapes =
        entries.mapIndexed { index, _ ->
            when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
        }
    ButtonGroup(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
    ) {
        entries.forEachIndexed { index, entry ->
            val label = labels[index]
            customItem(
                buttonGroupContent = {
                    RememberToggleButton(
                        checked = section == entry,
                        onCheckedChange = { checked -> if (checked) onSectionChange(entry) },
                        modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                        shapes = shapes[index],
                        colors = sectionButtonColors,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                menuContent = { menuState ->
                    RememberDropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSectionChange(entry)
                            menuState.dismiss()
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun HistorySection.label(): String =
    when (this) {
        HistorySection.ARCHIVE -> stringResource(R.string.history_section_archive)
        HistorySection.TRASH -> stringResource(R.string.history_section_trash)
    }
