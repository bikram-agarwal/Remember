package dev.bikram.remember.ui.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import dev.bikram.remember.R
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol

@Composable
internal fun EditorHeaderIcon(
    iconKey: String?,
    kind: NoteKind,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val iconContentDescription = stringResource(R.string.options_icon_cd)
    val iconInteractionSource = remember { MutableInteractionSource() }
    val interactiveModifier =
        modifier.let { baseModifier ->
            if (onClick != null) {
                baseModifier
                    .clickable(
                        interactionSource = iconInteractionSource,
                        indication = null,
                        onClick = {
                            onClick()
                        },
                    ).semantics { contentDescription = iconContentDescription }
            } else {
                baseModifier
            }
        }
    when (val headerIcon = resolveNoteIcon(iconKey, kind)) {
        is NoteIcon.Symbol ->
            RememberMaterialRoundedSymbol(
                name = headerIcon.name,
                size = iconSize,
                tint = MaterialTheme.colorScheme.primary,
                weight = FontWeight.Medium,
                modifier = interactiveModifier,
            )
        is NoteIcon.Drawable ->
            Icon(
                painterResource(headerIcon.resId),
                contentDescription = if (onClick != null) iconContentDescription else null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = interactiveModifier.size(iconSize),
            )
        is NoteIcon.Emoji ->
            Text(
                text = headerIcon.text,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = iconSize.value.sp),
                modifier = interactiveModifier,
            )
        NoteIcon.ListPlaceholder ->
            RememberMaterialRoundedSymbol(
                name = DEFAULT_LIST_HEADER_SYMBOL,
                size = iconSize,
                tint = MaterialTheme.colorScheme.primary,
                weight = FontWeight.Medium,
                modifier = interactiveModifier,
            )
        NoteIcon.NotePlaceholder ->
            RememberMaterialRoundedSymbol(
                name = DEFAULT_NOTE_HEADER_SYMBOL,
                size = iconSize,
                tint = MaterialTheme.colorScheme.primary,
                weight = FontWeight.Medium,
                modifier = interactiveModifier,
            )
    }
}
