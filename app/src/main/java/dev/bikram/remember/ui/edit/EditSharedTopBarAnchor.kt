package dev.bikram.remember.ui.edit

import androidx.compose.animation.BoundsTransform
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        EditorSharedHeaderIcon(
            iconKey = iconKey,
            isChecklist = isChecklist,
            modifier = iconModifier,
        )
        Spacer(Modifier.width(12.dp))
        Box(modifier = titleModifier) {
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
    val headerSymbol = iconSymbolName(iconKey)
    val headerBrandDrawable = iconDrawableRes(iconKey)
    val headerEmoji = iconEmojiPayload(iconKey)
    val defaultSymbol = if (isChecklist) DEFAULT_LIST_HEADER_SYMBOL else DEFAULT_NOTE_HEADER_SYMBOL
    when {
        headerSymbol != null ->
            RememberMaterialRoundedSymbol(
                name = headerSymbol,
                size = 28.dp,
                tint = MaterialTheme.colorScheme.primary,
                weight = FontWeight.Medium,
                modifier = modifier,
            )
        headerBrandDrawable != null ->
            Icon(
                painterResource(headerBrandDrawable),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = modifier.size(28.dp),
            )
        headerEmoji != null ->
            Text(
                text = headerEmoji,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                modifier = modifier,
            )
        else ->
            RememberMaterialRoundedSymbol(
                name = defaultSymbol,
                size = 28.dp,
                tint = MaterialTheme.colorScheme.primary,
                weight = FontWeight.Medium,
                modifier = modifier,
            )
    }
}
