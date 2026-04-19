package dev.bikram.remember.ui.history

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.modifiers.PillBottomBarHeight
import dev.bikram.remember.ui.modifiers.PillBottomScrimExtra
import dev.bikram.remember.ui.modifiers.applyToScrollableList
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.ui.components.NoteCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.bikram.remember.ui.components.RememberIconButton

class HistoryViewModel(private val repository: NoteRepository) : ViewModel() {
    val items: StateFlow<List<NoteWithItems>> = repository.observeTrashed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(note: NoteWithItems) {
        viewModelScope.launch { repository.restoreFromTrash(note.note.id) }
    }

    fun deleteForever(note: NoteWithItems) {
        viewModelScope.launch { repository.deleteForever(note.note.id) }
    }

    fun emptyTrash() {
        viewModelScope.launch { repository.emptyTrash() }
    }

    companion object {
        fun factory(repository: NoteRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HistoryViewModel(repository) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryRoute(repository: NoteRepository) {
    val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(repository))
    val items by vm.items.collectAsStateWithLifecycle()
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)
    val blurStyle = rememberProgressiveBlurStyle()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pillInset = navBarInset + PillBottomBarHeight + PillBottomScrimExtra

    val blurMod = remember(blurStyle) { blurStyle?.applyToScrollableList() ?: Modifier }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            LargeTopAppBar(
                colors = transparentLargeTopAppBarColors(),
                title = {
                    Text(text = "History", style = MaterialTheme.typography.headlineMedium)
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurMod)
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RememberMaterialRoundedSymbol(
                        name = "inbox",
                        size = 64.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        weight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Nothing here",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Trashed notes show up here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurMod),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = pillInset + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = items,
                    key = { it.note.id },
                    contentType = { "trashedNoteRow" },
                ) { note ->
                    Column {
                        NoteCard(note = note, onClick = {})
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            RememberIconButton(onClick = { vm.restore(note) }) {
                                RememberMaterialRoundedSymbol(
                                    name = "restore_from_trash",
                                    size = 24.dp,
                                    tint = MaterialTheme.colorScheme.primary,
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.semantics { contentDescription = "Restore" },
                                )
                            }
                            RememberIconButton(onClick = { vm.deleteForever(note) }) {
                                RememberMaterialRoundedSymbol(
                                    name = "delete_forever",
                                    size = 24.dp,
                                    tint = MaterialTheme.colorScheme.error,
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.semantics { contentDescription = "Delete forever" },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
