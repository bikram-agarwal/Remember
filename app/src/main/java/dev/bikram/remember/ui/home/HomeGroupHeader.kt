package dev.bikram.remember.ui.home

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.feedback.rememberPlayTapSound

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GroupHeader(
    label: String,
    count: Int? = null,
    collapsible: Boolean = false,
    collapsed: Boolean = false,
    onToggle: (() -> Unit)? = null,
    pinned: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val headerInteractionSource = remember { MutableInteractionSource() }
    val playTap = rememberPlayTapSound()
    val outerSpacing =
        modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp)
    val rowModifier =
        if (collapsible && onToggle != null) {
            outerSpacing
                .clip(MaterialTheme.shapes.small)
                .clickable(
                    interactionSource = headerInteractionSource,
                    indication = LocalIndication.current,
                ) {
                    playTap()
                    onToggle()
                }.padding(top = 6.dp, bottom = 4.dp, start = 4.dp)
        } else {
            outerSpacing.padding(top = 6.dp, bottom = 4.dp, start = 4.dp)
        }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pinned) {
            RememberMaterialRoundedSymbol(
                name = "push_pin",
                filled = true,
                size = 14.dp,
                tint = MaterialTheme.colorScheme.primary,
                weight = FontWeight.Medium,
                modifier =
                    Modifier
                        .padding(end = 6.dp)
                        .graphicsLayer { rotationZ = 30f },
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
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
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
                label = "section_chevron_rotation",
            )
            RememberMaterialRoundedSymbol(
                name = "chevron_right",
                size = 18.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                weight = FontWeight.Medium,
                modifier =
                    Modifier
                        .padding(end = 8.dp)
                        .graphicsLayer { rotationZ = rotation },
            )
        }
    }
}
