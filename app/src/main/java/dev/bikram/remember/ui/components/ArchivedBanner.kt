package dev.bikram.remember.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol

/**
 * Read-only badge displayed at the top of the Edit Note / Edit List body whenever the note is
 * archived or trashed. Makes it obvious why the pickers, text fields, and reminder pill are
 * disabled, and which bottom-bar action restores editability.
 */
@Composable
fun ArchivedBanner(
    state: ArchivedBannerState,
    modifier: Modifier = Modifier,
) {
    // ARCHIVED uses secondaryContainer: a neutral muted tint that reads "this item is in a
    // special shelf" without implying something positive happened (tertiaryContainer often
    // resolves to green in Material You, which signals success - wrong affordance for archive).
    val containerColor = when (state) {
        ArchivedBannerState.ARCHIVED -> MaterialTheme.colorScheme.secondaryContainer
        ArchivedBannerState.TRASHED -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (state) {
        ArchivedBannerState.ARCHIVED -> MaterialTheme.colorScheme.onSecondaryContainer
        ArchivedBannerState.TRASHED -> MaterialTheme.colorScheme.onErrorContainer
    }
    val iconName = when (state) {
        ArchivedBannerState.ARCHIVED -> "archive"
        ArchivedBannerState.TRASHED -> "delete_outline"
    }
    val titleRes = when (state) {
        ArchivedBannerState.ARCHIVED -> R.string.edit_archived_banner_title
        ArchivedBannerState.TRASHED -> R.string.edit_trashed_banner_title
    }
    val bodyRes = when (state) {
        ArchivedBannerState.ARCHIVED -> R.string.edit_archived_banner_body
        ArchivedBannerState.TRASHED -> R.string.edit_trashed_banner_body
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            RememberMaterialRoundedSymbol(
                name = iconName,
                size = 22.dp,
                tint = contentColor,
                weight = FontWeight.Medium,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                )
                Text(
                    text = stringResource(bodyRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.85f),
                )
            }
        }
    }
}

enum class ArchivedBannerState { ARCHIVED, TRASHED }
