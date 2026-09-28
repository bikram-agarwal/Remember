package dev.bikram.remember.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.bikram.remember.R
import dev.bikram.remember.data.NotesUiPrefs
import dev.bikram.remember.data.NotesUiState
import dev.bikram.remember.ui.components.settings.GroupPosition
import dev.bikram.remember.ui.components.settings.GroupedListColumn
import dev.bikram.remember.ui.components.settings.GroupedListItem
import kotlinx.coroutines.launch

@Composable
fun NotesUiSection(
    prefs: NotesUiPrefs,
    state: NotesUiState,
) {
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxWidth()) {
        GroupedListColumn {
            GroupedListItem(position = GroupPosition.FIRST) {
                AppearanceSettingsToggleItem(
                    title = stringResource(R.string.appearance_adaptive_note_themes_title),
                    subtitle = stringResource(R.string.appearance_adaptive_note_themes_subtitle),
                    checked = state.adaptiveNoteThemes,
                    onCheckedChange = { scope.launch { prefs.setAdaptiveNoteThemes(it) } },
                )
            }
            GroupedListItem(position = GroupPosition.MIDDLE) {
                AppearanceSettingsToggleItem(
                    title = stringResource(R.string.appearance_cover_title),
                    subtitle = stringResource(R.string.appearance_cover_subtitle),
                    checked = state.heroOnCards,
                    onCheckedChange = { scope.launch { prefs.setHeroOnCards(it) } },
                )
            }
            GroupedListItem(position = GroupPosition.LAST) {
                AppearanceSettingsToggleItem(
                    title = stringResource(R.string.appearance_note_content_title),
                    subtitle = stringResource(R.string.appearance_note_content_subtitle),
                    checked = state.showNoteContentOnCards,
                    onCheckedChange = { scope.launch { prefs.setShowNoteContentOnCards(it) } },
                )
            }
        }
    }
}
