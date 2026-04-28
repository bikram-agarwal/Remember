package dev.bikram.remember.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.EmptyArchiveIllustration
import dev.bikram.remember.ui.components.EmptyTrashIllustration
import dev.bikram.remember.ui.components.MultiActionSwipeRevealCard
import dev.bikram.remember.ui.components.NoteCard
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton
import dev.bikram.remember.ui.components.RememberSegmentedButton
import dev.bikram.remember.ui.components.SwipeRevealTile
import dev.bikram.remember.ui.modifiers.PillBottomBarHeight
import dev.bikram.remember.ui.modifiers.PillBottomScrimExtra
import dev.bikram.remember.ui.modifiers.applyToScrollableList
import dev.bikram.remember.ui.modifiers.rememberContentOverflowScrollEnabled
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

/** Which shelf the user is viewing. */
enum class HistorySection { ARCHIVE, TRASH }

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
            viewModelScope.launch { repository.restoreFromTrash(note.note.id) }
        }

        fun archiveFromTrash(note: NoteWithItems) {
            viewModelScope.launch { repository.archiveNote(note.note.id) }
        }

        fun deleteForever(note: NoteWithItems) {
            viewModelScope.launch { repository.deleteForever(note.note.id) }
        }

        fun unarchive(note: NoteWithItems) {
            viewModelScope.launch { repository.unarchiveNote(note.note.id) }
        }

        fun moveArchivedToTrash(note: NoteWithItems) {
            viewModelScope.launch { repository.moveToTrash(note.note.id) }
        }

        fun emptyTrash() {
            viewModelScope.launch { repository.emptyTrash() }
        }

        fun restoreSelected() {
            val ids = _selectedIds.value.toList()
            if (ids.isEmpty()) return
            viewModelScope.launch {
                ids.forEach { repository.restoreFromTrash(it) }
                _selectedIds.value = emptySet()
            }
        }

        fun archiveSelectedFromTrash() {
            val ids = _selectedIds.value.toList()
            if (ids.isEmpty()) return
            viewModelScope.launch {
                ids.forEach { repository.archiveNote(it) }
                _selectedIds.value = emptySet()
            }
        }

        fun unarchiveSelected() {
            val ids = _selectedIds.value.toList()
            if (ids.isEmpty()) return
            viewModelScope.launch {
                ids.forEach { repository.unarchiveNote(it) }
                _selectedIds.value = emptySet()
            }
        }

        fun moveSelectedArchivedToTrash() {
            val ids = _selectedIds.value.toList()
            if (ids.isEmpty()) return
            viewModelScope.launch {
                ids.forEach { repository.moveToTrash(it) }
                _selectedIds.value = emptySet()
            }
        }

        fun deleteSelectedForever() {
            val ids = _selectedIds.value.toList()
            if (ids.isEmpty()) return
            viewModelScope.launch {
                ids.forEach { repository.deleteForever(it) }
                _selectedIds.value = emptySet()
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
    val selectableVisibleIds = remember(items) { items.map { it.note.id }.toSet() }
    val inSelectionMode = selectedIds.isNotEmpty()

    BackHandler(enabled = inSelectionMode) { vm.clearSelection() }
    LaunchedEffect(section) { vm.clearSelection() }
    LaunchedEffect(selectableVisibleIds) { vm.pruneSelection(selectableVisibleIds) }
    LaunchedEffect(section, items.size) { onVisibleItemCountChange(items.size) }

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
                onDeleteForeverSelected = vm::deleteSelectedForever,
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
                        style = MaterialTheme.typography.headlineMedium,
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
                if (section == HistorySection.TRASH && trashed.isNotEmpty()) {
                    RetentionNotice(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                }
                if (items.isNotEmpty()) {
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
                            items = items,
                            key = { it.note.id },
                            contentType = { if (section == HistorySection.TRASH) "trashedRow" else "archivedRow" },
                        ) { note ->
                            val noteId = note.note.id
                            val isSelected = noteId in selectedIds
                            HistorySwipeCard(
                                note = note,
                                section = section,
                                daysLeft = vm.daysLeftInTrash(note).takeIf { section == HistorySection.TRASH },
                                hapticEnabled = interactionState.hapticFeedbackEnabled,
                                onOpenNote = {
                                    if (inSelectionMode) {
                                        vm.toggleSelection(noteId)
                                    } else {
                                        onOpenNote(note, false)
                                    }
                                },
                                onRestore = { vm.restore(note) },
                                onArchive = { vm.archiveFromTrash(note) },
                                onDeleteForever = { vm.deleteForever(note) },
                                onUnarchive = { vm.unarchive(note) },
                                onMoveToTrash = { vm.moveArchivedToTrash(note) },
                                selected = isSelected,
                                onLongClick = { vm.toggleSelection(noteId) },
                                swipeEnabled = !inSelectionMode,
                            )
                        }
                    }
                }
            }
            if (items.isEmpty()) {
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
}

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
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val exitLabel = stringResource(R.string.home_select_exit_cd)
                    RememberFilledTonalIconButton(
                        onClick = onClearSelection,
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
                        RememberFilledTonalIconButton(
                            onClick = onRestoreSelected,
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
                        RememberFilledTonalIconButton(
                            onClick = onArchiveSelected,
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
                        RememberFilledTonalIconButton(
                            onClick = onUnarchiveSelected,
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
                        RememberFilledTonalIconButton(
                            onClick = onTrashSelected,
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
                    RememberFilledTonalIconButton(
                        onClick = onDeleteForeverSelected,
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
                    RoundedCornerShape(16.dp),
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
    note: NoteWithItems,
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
                note = note,
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
        cardShape = RoundedCornerShape(12.dp),
        hapticEnabled = hapticEnabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth()) {
            NoteCard(
                note = note,
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
            stringResource(R.string.history_days_left, daysLeft)
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
                    RoundedCornerShape(999.dp),
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
