package dev.bikram.remember.ui.history

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.bikram.remember.R
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.NoteCard
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.components.RememberSegmentedButton
import dev.bikram.remember.ui.modifiers.PillBottomBarHeight
import dev.bikram.remember.ui.modifiers.PillBottomScrimExtra
import dev.bikram.remember.ui.modifiers.applyToScrollableList
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max

/** Which shelf the user is viewing. */
enum class HistorySection { ARCHIVE, TRASH }

class HistoryViewModel(private val repository: NoteRepository) : ViewModel() {

    /**
     * Wall-clock instant captured when the view-model is created -- used to derive the
     * "days left" counter without re-invoking the system clock on every recomposition.
     */
    private val nowAtCreation = System.currentTimeMillis()

    val trashedItems: StateFlow<List<NoteWithItems>> = repository.observeTrashed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val archivedItems: StateFlow<List<NoteWithItems>> = repository.observeArchived()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(note: NoteWithItems) {
        viewModelScope.launch { repository.restoreFromTrash(note.note.id) }
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

    companion object {
        fun factory(repository: NoteRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HistoryViewModel(repository) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HistoryRoute(
    repository: NoteRepository,
    onOpenNote: (NoteWithItems, Boolean) -> Unit,
) {
    val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(repository))
    val trashed by vm.trashedItems.collectAsStateWithLifecycle()
    val archived by vm.archivedItems.collectAsStateWithLifecycle()
    var section by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(HistorySection.ARCHIVE)
    }
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)
    val blurStyle = rememberProgressiveBlurStyle()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pillInset = navBarInset + PillBottomBarHeight + PillBottomScrimExtra
    val blurMod = remember(blurStyle) { blurStyle?.applyToScrollableList() ?: Modifier }

    val items = when (section) {
        HistorySection.ARCHIVE -> archived
        HistorySection.TRASH -> trashed
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            LargeTopAppBar(
                colors = transparentLargeTopAppBarColors(),
                title = {
                    Text(
                        text = stringResource(R.string.main_tab_history),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(blurMod)
                .padding(
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = 0.dp,
                ),
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                val entries = HistorySection.entries
                entries.forEachIndexed { index, entry ->
                    RememberSegmentedButton(
                        selected = section == entry,
                        onClick = { section = entry },
                        shape = SegmentedButtonDefaults.itemShape(index, entries.size),
                        label = { Text(entry.label()) },
                    )
                }
            }
            if (section == HistorySection.TRASH && trashed.isNotEmpty()) {
                RetentionNotice(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            } else {
                Spacer(Modifier.height(12.dp))
            }
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(section = section)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = pillInset + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = items,
                        key = { it.note.id },
                        contentType = { if (section == HistorySection.TRASH) "trashedRow" else "archivedRow" },
                    ) { note ->
                        Column {
                            NoteCard(note = note, onClick = { onOpenNote(note, false) })
                            when (section) {
                                HistorySection.TRASH -> TrashRowActions(
                                    note = note,
                                    daysLeft = vm.daysLeftInTrash(note),
                                    onRestore = { vm.restore(note) },
                                    onDeleteForever = { vm.deleteForever(note) },
                                )
                                HistorySection.ARCHIVE -> ArchiveRowActions(
                                    onUnarchive = { vm.unarchive(note) },
                                    onMoveToTrash = { vm.moveArchivedToTrash(note) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RetentionNotice(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
private fun TrashRowActions(
    note: NoteWithItems,
    daysLeft: Int?,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (daysLeft != null) {
            val label = if (daysLeft <= 0) {
                stringResource(R.string.history_expires_today)
            } else {
                stringResource(R.string.history_days_left, daysLeft)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (daysLeft <= 3) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        val cdRestore = stringResource(R.string.history_restore_cd)
        RememberIconButton(onClick = onRestore) {
            RememberMaterialRoundedSymbol(
                name = "restore_from_trash",
                size = 24.dp,
                tint = MaterialTheme.colorScheme.primary,
                weight = FontWeight.Medium,
                modifier = Modifier.semantics { contentDescription = cdRestore },
            )
        }
        val cdDeleteForever = stringResource(R.string.history_delete_forever_cd)
        RememberIconButton(onClick = onDeleteForever) {
            RememberMaterialRoundedSymbol(
                name = "delete_forever",
                size = 24.dp,
                tint = MaterialTheme.colorScheme.error,
                weight = FontWeight.Medium,
                modifier = Modifier.semantics { contentDescription = cdDeleteForever },
            )
        }
    }
}

@Composable
private fun ArchiveRowActions(
    onUnarchive: () -> Unit,
    onMoveToTrash: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        val cdUnarchive = stringResource(R.string.history_unarchive_cd)
        RememberIconButton(onClick = onUnarchive) {
            RememberMaterialRoundedSymbol(
                name = "unarchive",
                size = 24.dp,
                tint = MaterialTheme.colorScheme.primary,
                weight = FontWeight.Medium,
                modifier = Modifier.semantics { contentDescription = cdUnarchive },
            )
        }
        val cdMoveToTrash = stringResource(R.string.history_archive_move_to_trash_cd)
        RememberIconButton(onClick = onMoveToTrash) {
            RememberMaterialRoundedSymbol(
                name = "delete",
                size = 24.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                weight = FontWeight.Medium,
                modifier = Modifier.semantics { contentDescription = cdMoveToTrash },
            )
        }
    }
}

@Composable
private fun EmptyState(section: HistorySection) {
    val iconName = if (section == HistorySection.ARCHIVE) "archive" else "delete_sweep"
    val titleRes = if (section == HistorySection.ARCHIVE) {
        R.string.history_archive_empty_title
    } else {
        R.string.history_trash_empty_title
    }
    val subtitleRes = if (section == HistorySection.ARCHIVE) {
        R.string.history_archive_empty_subtitle
    } else {
        R.string.history_trash_empty_subtitle
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 24.dp),
    ) {
        RememberMaterialRoundedSymbol(
            name = iconName,
            size = 64.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.height(16.dp))
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
private fun HistorySection.label(): String = when (this) {
    HistorySection.ARCHIVE -> stringResource(R.string.history_section_archive)
    HistorySection.TRASH -> stringResource(R.string.history_section_trash)
}
