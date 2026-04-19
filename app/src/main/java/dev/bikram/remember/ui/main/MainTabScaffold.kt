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
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.history.HistoryRoute
import dev.bikram.remember.ui.home.HomeRoute
import dev.bikram.remember.ui.settings.SettingsRoute
import kotlinx.coroutines.launch
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.RememberFilledIconButton
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.components.RememberFloatingActionButton
import dev.bikram.remember.ui.feedback.tapSoundClickable

/**
 * Bottom tabs. Each [symbolName] must be listed by `font_subset/harvest_ligatures.py`
 * (then run `subset_font.py`) so the subset icon font actually contains that glyph.
 */
enum class MainTab(val label: String, val symbolName: String) {
    Notes("Notes", "notes"),
    History("History", "history"),
    Settings("Settings", "settings"),
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
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { it } + fadeOut())
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

        val (fabSymbolName, fabDesc, fabAction) = when (tab) {
            MainTab.Notes -> Triple(
                if (fabExpanded) "close" else "add",
                if (fabExpanded) "Close" else "Create",
                { fabExpanded = !fabExpanded },
            )
            MainTab.History -> Triple(
                "delete_sweep",
                "Clear all",
                { clearTrashOpen = true },
            )
            MainTab.Settings -> Triple(
                "share",
                "Share app",
                {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Remember - Notes, Lists, Reminders - Done right. " +
                                "https://play.google.com/store/apps/details?id=dev.bikram.remember",
                        )
                    }
                    context.startActivity(Intent.createChooser(send, "Share Remember"))
                },
            )
        }

        RememberFloatingNavBar(
            currentTab = tab,
            onTabClick = { selected ->
                tab = selected
                fabExpanded = false
            },
            fabContent = {
                RememberFloatingActionButton(
                    onClick = fabAction,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                ) {
                    RememberMaterialRoundedSymbol(
                        name = fabSymbolName,
                        size = 26.dp,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        weight = FontWeight.Medium,
                    )
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (clearTrashOpen) {
        AppBottomSheet(
            title = "Empty trash?",
            subtitle = "Permanently delete all trashed notes. This cannot be undone.",
            onDismiss = { clearTrashOpen = false },
            actions = {
                RememberTextButton(onClick = { clearTrashOpen = false }) { Text("Cancel") }
                RememberTextButton(onClick = {
                    clearTrashOpen = false
                    scope.launch { repository.emptyTrash() }
                }) { Text("Empty") }
            },
        ) {
            // No body content — subtitle covers the warning.
        }
    }
}

/** Same structure and motion as FilePipe `AppNavigation.kt` `FloatingNavBar`. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RememberFloatingNavBar(
    currentTab: MainTab,
    onTabClick: (MainTab) -> Unit,
    fabContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        floatingActionButton = fabContent,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 12.dp, start = 24.dp, end = 24.dp),
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
            toolbarContainerColor = MaterialTheme.colorScheme.primary,
            toolbarContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        MainTab.entries.forEachIndexed { index, item ->
            val selected = currentTab == item
            val labelWidth by animateDpAsState(
                targetValue = if (selected) 72.dp else 0.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
                label = "nav_label_$index",
            )
            RememberIconButton(
                onClick = { onTabClick(item) },
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
                    modifier = Modifier.padding(horizontal = if (selected) 6.dp else 0.dp),
                ) {
                    Box(modifier = Modifier.size(24.dp)) {
                        RememberMaterialRoundedSymbol(
                            name = item.symbolName,
                            size = 24.dp,
                            tint = LocalContentColor.current,
                            weight = FontWeight.Medium,
                        )
                    }
                    if (labelWidth > 4.dp) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpeedDialOverlay(
    onPickNote: () -> Unit,
    onPickList: () -> Unit,
    onDismiss: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .tapSoundClickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomEnd,
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = motion.defaultEffectsSpec()),
            exit = fadeOut(animationSpec = motion.defaultEffectsSpec()),
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 116.dp, end = 28.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SpeedDialItem("Checklist", "list", onPickList)
                SpeedDialItem("Note", "sticky_note_2", onPickNote)
            }
        }
    }
}

@Composable
private fun SpeedDialItem(
    label: String,
    symbolName: String,
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
        RememberFilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            RememberMaterialRoundedSymbol(
                name = symbolName,
                size = 24.dp,
                tint = MaterialTheme.colorScheme.onPrimary,
                weight = FontWeight.Medium,
            )
        }
    }
}
