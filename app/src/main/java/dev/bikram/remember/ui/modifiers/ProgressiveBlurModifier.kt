package dev.bikram.remember.ui.modifiers

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.bikram.remember.ui.theme.LocalBlurBars
import dev.bikram.remember.ui.theme.ProgressiveBlurStyle

enum class BlurDirection { TOP, BOTTOM }

private val dualEdgeBlurAgsl = """
    uniform shader content;
    uniform float blurRadius;
    uniform float topHeight;
    uniform float bottomHeight;
    uniform float contentHeight;

    half4 main(float2 fragCoord) {
        float topProgress = topHeight > 0.0
            ? 1.0 - clamp(fragCoord.y / topHeight, 0.0, 1.0)
            : 0.0;
        float bottomProgress = bottomHeight > 0.0
            ? 1.0 - clamp((contentHeight - fragCoord.y) / bottomHeight, 0.0, 1.0)
            : 0.0;

        // Higher exponent = blur stays low longer and only ramps up near the edge,
        // which avoids the "solid halo" look when the band is tall.
        float progress = pow(max(topProgress, bottomProgress), 2.5);
        float radius = progress * blurRadius;

        if (radius <= 0.0) {
            return content.eval(fragCoord);
        }

        half4 accum = half4(0.0);
        float weightSum = 0.0;

        float dither = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
        float2 jitter = float2(dither - 0.5, fract(dither * 1.618) - 0.5);

        const int SAMPLES = 5;
        float offsetScale = radius / float(SAMPLES);

        for (int x = -SAMPLES; x <= SAMPLES; x++) {
            for (int y = -SAMPLES; y <= SAMPLES; y++) {
                float2 offset = (float2(float(x), float(y)) + jitter) * offsetScale;
                float distSq = dot(offset, offset);
                float radiusSq = radius * radius;

                if (distSq <= radiusSq) {
                    float weight = exp(-3.0 * distSq / radiusSq);
                    accum += content.eval(fragCoord + offset) * weight;
                    weightSum += weight;
                }
            }
        }

        return accum / weightSum;
    }
""".trimIndent()

fun Modifier.progressiveBlur(
    blurRadius: Float,
    topHeight: Float = 0f,
    bottomHeight: Float = 0f,
    showGradientOverlay: Boolean = true,
    overlayAlpha: Float = 0.28f,
    overlayAlphaBottom: Float = overlayAlpha,
): Modifier = composed {
    val overlayColorTop = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = overlayAlpha)
    val overlayColorBottom = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = overlayAlphaBottom)

    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && blurRadius > 0f) {
        Modifier.graphicsLayer {
            val shader = RuntimeShader(dualEdgeBlurAgsl)
            shader.setFloatUniform("blurRadius", blurRadius)
            shader.setFloatUniform("topHeight", topHeight)
            shader.setFloatUniform("bottomHeight", bottomHeight)
            shader.setFloatUniform("contentHeight", size.height)
            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
                .asComposeRenderEffect()
        }
    } else {
        Modifier
    }

    val gradientModifier = if (showGradientOverlay) {
        Modifier.drawWithContent {
            drawContent()
            if (topHeight > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(overlayColorTop, Color.Transparent),
                        endY = topHeight,
                    ),
                )
            }
            if (bottomHeight > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, overlayColorBottom),
                        startY = size.height - bottomHeight,
                    ),
                )
            }
        }
    } else {
        Modifier
    }

    this.then(blurModifier).then(gradientModifier)
}

/** Default pill height (matches HorizontalFloatingToolbar + its bottom padding in MainTabScaffold). */
val PillBottomBarHeight = 64.dp
val PillBottomScrimExtra = 24.dp
/** Blur band above the fold. Extended to cover LargeTopAppBar's expanded area when the title is in-frame. */
val TopAppBarHeight = 56.dp

/**
 * Compose-ready progressive blur style for a screen. Returns null when [LocalBlurBars] is off
 * so consumers can avoid applying the modifier. [bottomExtra] is the additional blur band past
 * the navigation bar inset (e.g. height of the floating pill on tab screens; 0 on edit screens).
 */
@Composable
fun rememberProgressiveBlurStyle(
    bottomExtra: androidx.compose.ui.unit.Dp = PillBottomBarHeight + PillBottomScrimExtra,
    topExtra: androidx.compose.ui.unit.Dp = TopAppBarHeight,
    blurRadius: Float = 90f,
    overlayAlpha: Float = 0.36f,
    overlayAlphaBottom: Float = 0.48f,
): ProgressiveBlurStyle? {
    val enabled = LocalBlurBars.current
    if (!enabled) return null
    val density = LocalDensity.current
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topPx = with(density) { (statusBarInset + topExtra).toPx() }
    val bottomPx = with(density) { (navBarInset + bottomExtra).toPx() }
    val radius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) blurRadius else 0f
    return remember(topPx, bottomPx, radius, overlayAlpha, overlayAlphaBottom) {
        ProgressiveBlurStyle(
            topHeightPx = topPx,
            bottomHeightPx = bottomPx,
            blurRadius = radius,
            overlayAlpha = overlayAlpha,
            overlayAlphaBottom = overlayAlphaBottom,
        )
    }
}

fun ProgressiveBlurStyle.applyToScrollableList(): Modifier = Modifier.progressiveBlur(
    blurRadius = blurRadius,
    topHeight = topHeightPx,
    bottomHeight = bottomHeightPx,
    showGradientOverlay = true,
    overlayAlpha = overlayAlpha,
    overlayAlphaBottom = overlayAlphaBottom,
)

fun ProgressiveBlurStyle.applyToFullBleedLayer(): Modifier = Modifier.progressiveBlur(
    blurRadius = blurRadius,
    topHeight = topHeightPx,
    bottomHeight = bottomHeightPx,
    showGradientOverlay = true,
    overlayAlpha = overlayAlpha,
    overlayAlphaBottom = overlayAlphaBottom,
)
