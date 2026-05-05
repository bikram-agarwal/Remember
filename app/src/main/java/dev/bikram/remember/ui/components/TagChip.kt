package dev.bikram.remember.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import dev.bikram.remember.R
import dev.bikram.remember.data.TagPalette
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.tags.LocalTagColors

/** Parse "#RRGGBB" into a Compose Color. Returns null if invalid. */
fun parseHexColor(hex: String): Color? =
    runCatching {
        Color(hex.toColorInt())
    }.getOrNull()

/** Lookup the stored color for [tag], or derive a stable default from its hash. */
@Composable
fun tagColor(tag: String): Color {
    val map = LocalTagColors.current
    val key = tag.trim().lowercase()
    val hex = map[key]
    return hex?.let { parseHexColor(it) } ?: TagPalette.defaultFor(key)
}

/** Left edge of note cards: colored strip from tag palette (gradient when several tags). */
@Composable
fun TagAccentCardStrip(
    tags: List<String>,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    val tagColorMap = LocalTagColors.current
    val tagAccentStripShape =
        MaterialTheme.shapes.large.copy(
            topEnd = CornerSize(0.dp),
            bottomEnd = CornerSize(0.dp),
        )
    val brush =
        remember(tags, tagColorMap) {
            val orderedColors =
                tags
                    .map { tag ->
                        val key = tag.trim().lowercase()
                        tagColorMap[key]?.let { parseHexColor(it) } ?: TagPalette.defaultFor(key)
                    }.take(4)
            when (orderedColors.size) {
                1 -> SolidColor(orderedColors[0])
                else -> Brush.verticalGradient(orderedColors)
            }
        }
    Box(
        modifier =
            modifier
                .width(5.dp)
                .fillMaxHeight()
                .clip(tagAccentStripShape)
                .background(brush),
    )
}

/** Small accent bar under the editor title, driven by tag colors. */
@Composable
fun TagAccentEditorStrip(tags: List<String>) {
    if (tags.isEmpty()) {
        Spacer(Modifier.height(12.dp))
        return
    }
    val tagColorMap = LocalTagColors.current
    val brush =
        remember(tags, tagColorMap) {
            val orderedColors =
                tags
                    .map { tag ->
                        val key = tag.trim().lowercase()
                        tagColorMap[key]?.let { parseHexColor(it) } ?: TagPalette.defaultFor(key)
                    }.take(4)
            when (orderedColors.size) {
                1 -> SolidColor(orderedColors[0])
                else -> Brush.horizontalGradient(orderedColors)
            }
        }
    Box(
        modifier =
            Modifier
                .padding(top = 4.dp, bottom = 12.dp)
                .height(4.dp)
                .width(56.dp)
                .clip(CircleShape)
                .background(brush),
    )
}

/**
 * Filled pill with auto-contrast text. Used on note cards, filter sheet, tag editor.
 *
 * @param faded renders the pill at reduced alpha — used for "not selected" in filters.
 */
@Composable
fun TagChipFilled(
    tag: String,
    modifier: Modifier = Modifier,
    color: Color = tagColor(tag),
    faded: Boolean = false,
    highlighted: Boolean = false,
    leadingIconName: String? = null,
    highlightedIconName: String? = "edit",
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    val cdRemoveTag = stringResource(R.string.remove_tag_cd, tag)
    val contentColor = TagPalette.textOn(color)
    val horizontal = if (compact) 8.dp else 12.dp
    val vertical = if (compact) 3.dp else 6.dp
    val shape = if (compact) MaterialTheme.shapes.small else MaterialTheme.shapes.medium
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            modifier
                .alpha(if (faded) 0.4f else 1f)
                .background(color, shape)
                .border(
                    width = if (highlighted) 2.dp else 0.dp,
                    color = if (highlighted) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = shape,
                )
                // .clip(shape) before the clickable bounds the ripple to the chip's pill
                // outline. Without it the ripple draws as a rectangle that bleeds past
                // the rounded edges of the chip.
                .let {
                    if (onClick != null) {
                        it.clip(shape).tapSoundClickable(onClick = onClick)
                    } else {
                        it
                    }
                }.padding(horizontal = horizontal, vertical = vertical),
    ) {
        if (leadingIconName != null) {
            RememberMaterialRoundedSymbol(
                name = leadingIconName,
                size = 14.dp,
                tint = contentColor,
                weight = FontWeight.Medium,
            )
        }
        Text(
            text = tag,
            style =
                if (compact) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.labelMedium
                },
            color = contentColor,
            maxLines = 1,
        )
        if (highlighted && highlightedIconName != null) {
            RememberMaterialRoundedSymbol(
                name = highlightedIconName,
                size = 14.dp,
                tint = contentColor,
                weight = FontWeight.Medium,
            )
        }
        if (onRemove != null) {
            RememberMaterialRoundedSymbol(
                name = "close",
                size = 14.dp,
                tint = contentColor,
                weight = FontWeight.Medium,
                modifier =
                    Modifier
                        .semantics { contentDescription = cdRemoveTag }
                        .tapSoundClickable(onClick = onRemove),
            )
        }
    }
}
