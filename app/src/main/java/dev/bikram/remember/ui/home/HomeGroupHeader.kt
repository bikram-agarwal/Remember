package dev.bikram.remember.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GroupHeader(
    label: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    collapsible: Boolean = false,
    collapsed: Boolean = false,
    onToggle: (() -> Unit)? = null,
    pinned: Boolean = false,
) {
    val headerInteractionSource = remember { MutableInteractionSource() }
    val collapsedStateDescription = stringResource(R.string.home_group_state_collapsed)
    val expandedStateDescription = stringResource(R.string.home_group_state_expanded)
    val spatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.Dp>())
    val colorSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Color>())
    val headerCorner by animateDpAsState(
        targetValue = if (collapsed) 28.dp else 4.dp,
        animationSpec = spatialSpec,
        label = "home_group_header_corner",
    )
    val headerColor by animateColorAsState(
        targetValue = if (collapsed) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        animationSpec = colorSpec,
        label = "home_group_header_color",
    )
    val horizontalPadding by animateDpAsState(
        targetValue = if (collapsed) 12.dp else 4.dp,
        animationSpec = spatialSpec,
        label = "home_group_header_horizontal_padding",
    )
    val verticalPadding by animateDpAsState(
        targetValue = if (collapsed) 8.dp else 4.dp,
        animationSpec = spatialSpec,
        label = "home_group_header_vertical_padding",
    )
    val titleStartPadding by animateDpAsState(
        targetValue = if (collapsed && !pinned) 8.dp else 0.dp,
        animationSpec = spatialSpec,
        label = "home_group_header_title_start_padding",
    )
    val pinnedIconContainerSize by animateDpAsState(
        targetValue = if (collapsed) 30.dp else 18.dp,
        animationSpec = spatialSpec,
        label = "home_group_pinned_icon_container_size",
    )
    val pinnedIconSize by animateDpAsState(
        targetValue = if (collapsed) 16.dp else 14.dp,
        animationSpec = spatialSpec,
        label = "home_group_pinned_icon_size",
    )
    val pinnedIconContainerColor by animateColorAsState(
        targetValue =
            if (collapsed) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
            } else {
                Color.Transparent
            },
        animationSpec = colorSpec,
        label = "home_group_pinned_icon_container_color",
    )
    val chevronContainerSize by animateDpAsState(
        targetValue = if (collapsed) 32.dp else 20.dp,
        animationSpec = spatialSpec,
        label = "home_group_chevron_container_size",
    )
    val chevronSize by animateDpAsState(
        targetValue = if (collapsed) 20.dp else 18.dp,
        animationSpec = spatialSpec,
        label = "home_group_chevron_size",
    )
    val chevronContainerColor by animateColorAsState(
        targetValue = if (collapsed) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
        animationSpec = colorSpec,
        label = "home_group_chevron_container_color",
    )
    val safeHeaderCorner = headerCorner.coerceAtLeast(0.dp)
    val safeHorizontalPadding = horizontalPadding.coerceAtLeast(0.dp)
    val safeVerticalPadding = verticalPadding.coerceAtLeast(0.dp)
    val safeTitleStartPadding = titleStartPadding.coerceAtLeast(0.dp)

    val outerSpacing =
        modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp)
    val rowModifier =
        (
            if (collapsible && onToggle != null) {
                outerSpacing
                    .background(
                        color = headerColor,
                        shape = RoundedCornerShape(safeHeaderCorner),
                    ).clip(MaterialTheme.shapes.extraLargeIncreased)
                    .clickable(
                        interactionSource = headerInteractionSource,
                        indication = LocalIndication.current,
                    ) {
                        onToggle()
                    }.padding(horizontal = safeHorizontalPadding, vertical = safeVerticalPadding)
            } else {
                outerSpacing.padding(horizontal = safeHorizontalPadding, vertical = safeVerticalPadding)
            }
        ).semantics {
            heading()
            if (collapsible) {
                stateDescription = if (collapsed) collapsedStateDescription else expandedStateDescription
            }
        }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (pinned) {
            Box(
                modifier =
                    Modifier
                        .size(pinnedIconContainerSize)
                        .clip(MaterialTheme.shapes.extraExtraLarge)
                        .background(pinnedIconContainerColor),
                contentAlignment = Alignment.Center,
            ) {
                RememberMaterialRoundedSymbol(
                    name = "push_pin",
                    filled = true,
                    size = pinnedIconSize,
                    tint = MaterialTheme.colorScheme.primary,
                    weight = FontWeight.Medium,
                    modifier = Modifier.graphicsLayer { rotationZ = 30f },
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = safeTitleStartPadding),
        )
        if (count != null) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
            )
        }
        if (collapsible) {
            Spacer(Modifier.weight(1f))
            val rotation by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (collapsed) 0f else 90f,
                animationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<Float>()),
                label = "section_chevron_rotation",
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
                    size = chevronSize,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    weight = FontWeight.Medium,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation },
                )
            }
        }
    }
}
