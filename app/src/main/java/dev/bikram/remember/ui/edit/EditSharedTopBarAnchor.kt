package dev.bikram.remember.ui.edit

import androidx.compose.animation.BoundsTransform
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol

@Composable
internal fun ExpandedEditorSharedTopBarAnchor(
    sharedNoteId: Long?,
    title: String,
    fallbackTitle: String,
    iconKey: String?,
    isChecklist: Boolean,
    modifier: Modifier = Modifier,
) {
    val sharedScope = dev.bikram.remember.ui.nav.LocalSharedTransitionScope.current
    val navScope = dev.bikram.remember.ui.nav.LocalNavAnimatedVisibilityScope.current
    if (sharedScope == null || navScope == null || sharedNoteId == null || !sharedScope.isTransitionActive) return

    val sharedBoundsSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    val sharedBoundsTransform = BoundsTransform { _, _ -> sharedBoundsSpec }
    val iconModifier =
        with(sharedScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "note-icon-$sharedNoteId"),
                animatedVisibilityScope = navScope,
                boundsTransform = sharedBoundsTransform,
            )
        }
    val titleModifier =
        with(sharedScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "note-title-$sharedNoteId"),
                animatedVisibilityScope = navScope,
                boundsTransform = sharedBoundsTransform,
            )
        }
    val titleText = title.ifBlank { fallbackTitle }

    // The Row fills width and the title takes weight(1f) so the shared-element bounds
    // shape matches the card's title bounds (also weight(1f) inside its row). Without
    // this match, the anchor's title bounds would be the intrinsic text width while
    // the card's would be the full remaining row width - and during a card->anchor
    // back animation the source content gets ScaleToBounds(FillWidth)-scaled UP by
    // ~1.87x to fill the destination's growing width, which is what made the title
    // visibly grow huge at center-screen during the back gesture.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        EditorSharedHeaderIcon(
            iconKey = iconKey,
            isChecklist = isChecklist,
            modifier = iconModifier,
        )
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f).then(titleModifier)) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EditorSharedHeaderIcon(
    iconKey: String?,
    isChecklist: Boolean,
    modifier: Modifier = Modifier,
) {
    val kind =
        if (isChecklist) {
            NoteKind.LIST
        } else {
            NoteKind.NOTE
        }
    when (val headerIcon = resolveNoteIcon(iconKey, kind)) {
        is NoteIcon.Symbol ->
            RememberMaterialRoundedSymbol(
                name = headerIcon.name,
                size = 28.dp,
                tint = MaterialTheme.colorScheme.primary,
                weight = FontWeight.Medium,
                modifier = modifier,
            )
        is NoteIcon.Drawable ->
            Icon(
                painterResource(headerIcon.resId),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = modifier.size(28.dp),
            )
        is NoteIcon.Emoji ->
            Text(
                text = headerIcon.text,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                modifier = modifier,
            )
        NoteIcon.ListPlaceholder ->
            RememberMaterialRoundedSymbol(
                name = DEFAULT_LIST_HEADER_SYMBOL,
                size = 28.dp,
                tint = MaterialTheme.colorScheme.primary,
                weight = FontWeight.Medium,
                modifier = modifier,
            )
        NoteIcon.NotePlaceholder ->
            RememberMaterialRoundedSymbol(
                name = DEFAULT_NOTE_HEADER_SYMBOL,
                size = 28.dp,
                tint = MaterialTheme.colorScheme.primary,
                weight = FontWeight.Medium,
                modifier = modifier,
            )
    }
}
