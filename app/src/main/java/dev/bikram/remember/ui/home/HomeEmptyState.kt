package dev.bikram.remember.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.data.NotesFilter
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.EmptyFilterIllustration
import dev.bikram.remember.ui.components.EmptyNotesIllustration
import dev.bikram.remember.ui.components.RememberButton
import dev.bikram.remember.ui.components.RememberOutlinedButton

@Composable
internal fun NotesEmptyState(
    filter: NotesFilter,
    totalUnfilteredNotes: Int,
    onCreateNote: () -> Unit,
    onCreateList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pristineVault =
        totalUnfilteredNotes == 0 && filter.text.isBlank() && !filter.facetActive
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (pristineVault) {
            EmptyNotesIllustration()
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.home_no_notes_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_no_notes_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(24.dp))
            RememberButton(
                onClick = onCreateNote,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(0.78f),
            ) {
                RememberMaterialRoundedSymbol(
                    name = "add",
                    size = 20.dp,
                    weight = FontWeight.Medium,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_create_note))
            }
            Spacer(Modifier.height(12.dp))
            RememberOutlinedButton(
                onClick = onCreateList,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(0.78f),
            ) {
                RememberMaterialRoundedSymbol(
                    name = "add",
                    size = 20.dp,
                    weight = FontWeight.Medium,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_create_list))
            }
        } else {
            EmptyFilterIllustration()
            Spacer(Modifier.height(18.dp))
            val titleText =
                when {
                    filter.text.isNotBlank() ->
                        stringResource(R.string.home_no_results_for, filter.text)
                    filter.facetActive ->
                        stringResource(R.string.home_no_results_filters_title)
                    else -> stringResource(R.string.home_nothing_here)
                }
            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            val hintText =
                if (filter.text.isNotBlank()) {
                    stringResource(R.string.home_no_results_hint)
                } else {
                    stringResource(R.string.home_no_results_filters_hint)
                }
            Text(
                text = hintText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
