package dev.bikram.remember.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Size as ComposeSize

/** Must match [HeroFramingEditor] mask so saved focal maps to cards. */
const val HERO_MASK_ASPECT_RATIO: Float = 16f / 9f

/** Decode widths are rounded up to this step so near-equal window widths share one cache entry. */
private const val HERO_DECODE_BUCKET_PX = 512

private const val HERO_DECODE_MAX_SIDE_PX = 2048

@Composable
fun HeroFramedImage(
    imageUri: String,
    framing: HeroFraming?,
    cacheRevision: Long,
    modifier: Modifier = Modifier,
    imageAlpha: Float = 1f,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier) {
        val containerW =
            if (constraints.hasBoundedWidth) {
                constraints.maxWidth.toFloat()
            } else {
                1080f
            }.coerceIn(1f, 4096f)
        val containerH =
            if (constraints.hasBoundedHeight) {
                constraints.maxHeight.toFloat()
            } else {
                containerW / HERO_MASK_ASPECT_RATIO
            }.coerceIn(1f, 4096f)
        val containerRatio = containerW / containerH
        val viewportW: Float
        val viewportH: Float
        if (framing != null && containerRatio > HERO_MASK_ASPECT_RATIO) {
            viewportW = containerW
            viewportH = containerW / HERO_MASK_ASPECT_RATIO
        } else if (framing != null) {
            viewportH = containerH
            viewportW = containerH * HERO_MASK_ASPECT_RATIO
        } else {
            viewportW = containerW
            viewportH = containerH
        }
        // The decode size is derived from the window, not from this container. The card <-> editor
        // morph remeasures the container every frame, and a request's cache key embeds its size,
        // so a container-derived size gives the card and the editor separate cache entries: the
        // editor's copy is then a guaranteed miss on open and fades in over the top of the morph.
        // One window-width entry per photo is shared by the card, the editor and every frame in
        // between, which is also fewer bitmaps than the old per-container-size keys held.
        // A hero is never wider than the window, so this never upscales.
        val windowWidthPx = LocalWindowInfo.current.containerSize.width
        val requestMaxSidePx =
            (((windowWidthPx + HERO_DECODE_BUCKET_PX - 1) / HERO_DECODE_BUCKET_PX) * HERO_DECODE_BUCKET_PX)
                .coerceIn(HERO_DECODE_BUCKET_PX, HERO_DECODE_MAX_SIDE_PX)
        val painter = rememberAsyncImagePainter(rememberHeroImageRequest(imageUri, cacheRevision, requestMaxSidePx))
        val loadState by painter.state.collectAsStateWithLifecycle()
        val intrinsic: ComposeSize = painter.intrinsicSize
        val ready =
            intrinsic.width.isFinite() &&
                intrinsic.height.isFinite() &&
                intrinsic.width > 0f &&
                intrinsic.height > 0f &&
                loadState is AsyncImagePainter.State.Success

        if (!ready || framing == null) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().graphicsLayer { alpha = imageAlpha },
            )
        } else {
            val imageWidthPx = intrinsic.width
            val imageHeightPx = intrinsic.height

            val coverScale = max(viewportW / imageWidthPx, viewportH / imageHeightPx)
            val displayScale = coverScale * framing.zoom.coerceIn(1f, 8f)
            val scaledW = imageWidthPx * displayScale
            val scaledH = imageHeightPx * displayScale
            val leftUnclamped = containerW / 2f - framing.focalX * imageWidthPx * displayScale
            val topUnclamped = containerH / 2f - framing.focalY * imageHeightPx * displayScale
            val minLeft = (containerW + viewportW) / 2f - scaledW
            val maxLeft = (containerW - viewportW) / 2f
            val minTop = (containerH + viewportH) / 2f - scaledH
            val maxTop = (containerH - viewportH) / 2f
            val left =
                if (minLeft <= maxLeft) {
                    leftUnclamped.coerceIn(minLeft, maxLeft)
                } else {
                    (minLeft + maxLeft) / 2f
                }
            val top =
                if (minTop <= maxTop) {
                    topUnclamped.coerceIn(minTop, maxTop)
                } else {
                    (minTop + maxTop) / 2f
                }

            // requiredSize implicitly centers the content if it's larger than the parent.
            // We must subtract this centering offset so our calculated `left` and `top` are absolute.
            val centerOffsetX = (containerW - scaledW) / 2f
            val centerOffsetY = (containerH - scaledH) / 2f
            val finalLeft = left - centerOffsetX
            val finalTop = top - centerOffsetY

            val widthDp = with(density) { scaledW.coerceIn(1f, 4096f).toDp() }
            val heightDp = with(density) { scaledH.coerceIn(1f, 4096f).toDp() }
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier =
                    Modifier
                        .requiredSize(widthDp, heightDp)
                        .offset { IntOffset(finalLeft.roundToInt(), finalTop.roundToInt()) }
                        .graphicsLayer { alpha = imageAlpha },
            )
        }
    }
}
