package dev.bikram.remember.ui.edit

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
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
    showBoundary: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val iconSlotSize = iconSize + 12.dp
    val iconSlotShape = RoundedCornerShape(10.dp)
    val iconContentDescription = stringResource(R.string.options_icon_cd)
    val iconInteractionSource = remember { MutableInteractionSource() }
    val boundaryModifier =
        if (showBoundary) {
            Modifier.border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                shape = iconSlotShape,
            )
        } else {
            Modifier
        }
    val slotModifier = modifier.size(iconSlotSize).then(boundaryModifier)
    val interactiveModifier =
        if (onClick != null) {
            slotModifier
                .clickable(
                    interactionSource = iconInteractionSource,
                    indication = null,
                    onClick = {
                        onClick()
                    },
                ).semantics { contentDescription = iconContentDescription }
        } else {
            slotModifier
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier = interactiveModifier,
    ) {
        when (val headerIcon = resolveNoteIcon(iconKey, kind)) {
            is NoteIcon.Symbol ->
                RememberMaterialRoundedSymbol(
                    name = headerIcon.name,
                    size = iconSize,
                    tint = MaterialTheme.colorScheme.primary,
                    weight = FontWeight.Medium,
                    filled = headerIcon.filled,
                )
            is NoteIcon.Drawable ->
                Icon(
                    painterResource(headerIcon.resId),
                    contentDescription = if (onClick != null) iconContentDescription else null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(iconSize),
                )
            is NoteIcon.Emoji ->
                Text(
                    text = headerIcon.text,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = iconSize.value.sp),
                )
            NoteIcon.ListPlaceholder ->
                RememberMaterialRoundedSymbol(
                    name = DEFAULT_LIST_HEADER_SYMBOL,
                    size = iconSize,
                    tint = MaterialTheme.colorScheme.primary,
                    weight = FontWeight.Medium,
                )
            NoteIcon.NotePlaceholder ->
                RememberMaterialRoundedSymbol(
                    name = DEFAULT_NOTE_HEADER_SYMBOL,
                    size = iconSize,
                    tint = MaterialTheme.colorScheme.primary,
                    weight = FontWeight.Medium,
                )
        }
    }
}
