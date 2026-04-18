package dev.bikram.remember.ui.main

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.history.HistoryRoute
import dev.bikram.remember.ui.home.HomeRoute
import dev.bikram.remember.ui.settings.SettingsRoute
import kotlinx.coroutines.launch

enum class MainTab(val label: String, val icon: ImageVector) {
    Notes("Notes", Icons.AutoMirrored.Filled.Notes),
    History("History", Icons.Filled.History),
    Settings("Settings", Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainTabScaffold(
    repository: NoteRepository,
    themePrefs: ThemePrefs,
    interactionPrefs: InteractionPrefs,
    onCreateNote: () -> Unit,
    onCreateList: () -> Unit,
    onOpenNote: (NoteWithItems) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.Notes) }
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    var clearTrashOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // Back handling: when the FAB speed dial is open, back closes it first.
    // On History/Settings tab, back returns to the Notes tab instead of exiting.
    // On Notes tab with nothing open, the default handler runs (exits the app).
    BackHandler(enabled = fabExpanded) { fabExpanded = false }
    BackHandler(enabled = !fabExpanded && tab != MainTab.Notes) {
        tab = MainTab.Notes
    }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                if (forward) {
                    (slideInHorizontally(initialOffsetX = { it }) + fadeIn()) togetherWith
                        (slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut())
                } else {
                    (slideInHorizontally(initialOffsetX = { -it }) + fadeIn()) togetherWith
                        (slideOutHorizontally(targetOffsetX = { it / 3 }) + fadeOut())
                }
            },
            label = "tab",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            when (current) {
                MainTab.Notes -> HomeRoute(
                    repository = repository,
                    themePrefs = themePrefs,
                    interactionPrefs = interactionPrefs,
                    onOpenNote = onOpenNote,
                    onCreateNote = {
                        fabExpanded = false
                        onCreateNote()
                    },
                    onCreateList = {
                        fabExpanded = false
                        onCreateList()
                    },
                )
                MainTab.History -> HistoryRoute(repository = repository)
                MainTab.Settings -> SettingsRoute()
            }
        }

        if (fabExpanded && tab == MainTab.Notes) {
            SpeedDialOverlay(
                onPickNote = { fabExpanded = false; onCreateNote() },
                onPickList = { fabExpanded = false; onCreateList() },
                onDismiss = { fabExpanded = false },
            )
        }

        val pillModifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 14.dp, start = 20.dp, end = 20.dp)
        val pillColors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
            toolbarContainerColor = MaterialTheme.colorScheme.primary,
            toolbarContentColor = MaterialTheme.colorScheme.onPrimary,
        )
        val tabs: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
            MainTab.entries.forEach { t ->
                TabButton(
                    target = t,
                    current = tab,
                    onSelect = {
                        tab = it
                        fabExpanded = false
                    },
                )
            }
        }

        val (fabIcon, fabDesc, fabAction) = when (tab) {
            MainTab.Notes -> Triple(
                if (fabExpanded) Icons.Filled.Close else Icons.Filled.Add,
                if (fabExpanded) "Close" else "Create",
                { fabExpanded = !fabExpanded },
            )
            MainTab.History -> Triple(
                Icons.Filled.DeleteSweep,
                "Clear all",
                { clearTrashOpen = true },
            )
            MainTab.Settings -> Triple(
                Icons.Filled.Share,
                "Share app",
                {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Remember — a fresh take on Collateral. " +
                                "https://play.google.com/store/apps/details?id=dev.bikram.remember",
                        )
                    }
                    context.startActivity(Intent.createChooser(send, "Share Remember"))
                },
            )
        }

        HorizontalFloatingToolbar(
            expanded = true,
            modifier = pillModifier,
            colors = pillColors,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = fabAction,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                ) {
                    Icon(fabIcon, contentDescription = fabDesc)
                }
            },
            content = tabs,
        )
    }

    if (clearTrashOpen) {
        AppBottomSheet(
            title = "Empty trash?",
            subtitle = "Permanently delete all trashed notes. This cannot be undone.",
            onDismiss = { clearTrashOpen = false },
            actions = {
                TextButton(onClick = { clearTrashOpen = false }) { Text("Cancel") }
                TextButton(onClick = {
                    clearTrashOpen = false
                    scope.launch { repository.emptyTrash() }
                }) { Text("Empty") }
            },
        ) {
            // No body content — subtitle covers the warning.
        }
    }
}

@Composable
private fun TabButton(
    target: MainTab,
    current: MainTab,
    onSelect: (MainTab) -> Unit,
) {
    val selected = current == target
    val labelWidth by animateDpAsState(
        targetValue = if (selected) 72.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "tab_$target",
    )
    IconButton(
        onClick = { onSelect(target) },
        modifier = Modifier
            .height(48.dp)
            .width(48.dp + labelWidth),
        colors = if (selected) {
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
            modifier = Modifier.padding(horizontal = if (selected) 10.dp else 4.dp),
        ) {
            Icon(target.icon, contentDescription = target.label, modifier = Modifier.size(22.dp))
            if (labelWidth > 4.dp) {
                Spacer(Modifier.width(6.dp))
                Text(target.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SpeedDialOverlay(
    onPickNote: () -> Unit,
    onPickList: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomEnd,
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 116.dp, end = 28.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SpeedDialItem("Checklist", Icons.AutoMirrored.Filled.List, onPickList)
                SpeedDialItem("Note", Icons.AutoMirrored.Filled.StickyNote2, onPickNote)
            }
        }
    }
}

@Composable
private fun SpeedDialItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Spacer(Modifier.width(12.dp))
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(icon, contentDescription = label)
        }
    }
}
