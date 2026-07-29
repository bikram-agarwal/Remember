package dev.bikram.remember.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun RememberExpandableSectionHeader(
    iconName: String,
    title: String,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (collapsed) 0f else 90f,
        animationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<Float>()),
        label = "expandable_section_chevron_rotation",
    )
    val cdExpand = stringResource(R.string.section_expand_cd, title)
    val cdCollapse = stringResource(R.string.section_collapse_cd, title)
    val interactionSource = remember { MutableInteractionSource() }
    val spatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<Dp>())
    val colorSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Color>())
    val headerCorner by animateDpAsState(
        targetValue = if (collapsed) 28.dp else 4.dp,
        animationSpec = spatialSpec,
        label = "expandable_section_header_corner",
    )
    val headerColor by animateColorAsState(
        targetValue = if (collapsed) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        animationSpec = colorSpec,
        label = "expandable_section_header_color",
    )
    val horizontalPadding by animateDpAsState(
        targetValue = if (collapsed) 12.dp else 0.dp,
        animationSpec = spatialSpec,
        label = "expandable_section_header_horizontal_padding",
    )
    val verticalPadding by animateDpAsState(
        targetValue = if (collapsed) 8.dp else 4.dp,
        animationSpec = spatialSpec,
        label = "expandable_section_header_vertical_padding",
    )
    val iconContainerSize by animateDpAsState(
        targetValue = if (collapsed) 36.dp else 20.dp,
        animationSpec = spatialSpec,
        label = "expandable_section_icon_container_size",
    )
    val iconSize by animateDpAsState(
        targetValue = if (collapsed) 21.dp else 19.dp,
        animationSpec = spatialSpec,
        label = "expandable_section_icon_size",
    )
    val iconContainerColor by animateColorAsState(
        targetValue =
            if (collapsed) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
            } else {
                Color.Transparent
            },
        animationSpec = colorSpec,
        label = "expandable_section_icon_container_color",
    )
    val chevronContainerSize by animateDpAsState(
        targetValue = if (collapsed) 32.dp else 20.dp,
        animationSpec = spatialSpec,
        label = "expandable_section_chevron_container_size",
    )
    val chevronSize by animateDpAsState(
        targetValue = if (collapsed) 20.dp else 18.dp,
        animationSpec = spatialSpec,
        label = "expandable_section_chevron_size",
    )
    val chevronContainerColor by animateColorAsState(
        targetValue = if (collapsed) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
        animationSpec = colorSpec,
        label = "expandable_section_chevron_container_color",
    )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = headerColor,
                    shape = RoundedCornerShape(headerCorner.coerceAtLeast(0.dp)),
                ).clip(MaterialTheme.shapes.extraLargeIncreased)
                .semantics { contentDescription = if (collapsed) cdExpand else cdCollapse }
                .tapSoundClickable(
                    onClick = onToggle,
                    indication = LocalIndication.current,
                    interactionSource = interactionSource,
                ).padding(
                    horizontal = horizontalPadding.coerceAtLeast(0.dp),
                    vertical = verticalPadding.coerceAtLeast(0.dp),
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(iconContainerSize)
                    .clip(MaterialTheme.shapes.extraExtraLarge)
                    .background(iconContainerColor),
            contentAlignment = Alignment.Center,
        ) {
            RememberMaterialRoundedSymbol(
                name = iconName,
                size = iconSize,
                tint = MaterialTheme.colorScheme.primary,
                weight = FontWeight.Medium,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier =
                Modifier
                    .size(chevronContainerSize)
                    .clip(MaterialTheme.shapes.extraExtraLarge)
                    .background(chevronContainerColor),
            contentAlignment = Alignment.Center,
        ) {
            RememberMaterialRoundedSymbol(
                name = "chevron_right",
                autoMirror = true,
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
                size = chevronSize,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                weight = FontWeight.Medium,
            )
        }
    }
}
