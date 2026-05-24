package dev.bikram.remember.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal const val SETTINGS_SECTION_HIGHLIGHT_DURATION_MS = 4_500L

@Composable
internal fun rememberSectionHighlightPulseAlpha(active: Boolean): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "settingsSectionHighlight")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.42f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 850, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulse",
    )
    return if (active) pulse else 1f
}

internal fun Modifier.pulsingSectionHighlightOutline(
    active: Boolean,
    outlineColor: Color,
    expandDp: Dp = 10.dp,
    cornerRadiusDp: Dp = 18.dp,
    strokeWidthDp: Dp = 3.dp,
): Modifier =
    this
        .graphicsLayer { clip = false }
        .drawBehind {
            if (!active) return@drawBehind
            val expandPx = expandDp.toPx()
            val strokeWidthPx = strokeWidthDp.toPx()
            val cornerPx = cornerRadiusDp.toPx()
            drawRoundRect(
                color = outlineColor,
                topLeft = Offset(-expandPx, -expandPx),
                size = Size(size.width + 2f * expandPx, size.height + 2f * expandPx),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = strokeWidthPx),
            )
        }
