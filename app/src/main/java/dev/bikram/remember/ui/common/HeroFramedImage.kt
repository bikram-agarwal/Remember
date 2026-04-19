package dev.bikram.remember.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size
import kotlin.math.max
import kotlin.math.roundToInt

/** Must match [HeroFramingEditor] mask so saved focal maps to cards. */
const val HERO_MASK_ASPECT_RATIO: Float = 16f / 9f

@Composable
fun HeroFramedImage(
    imageUri: String,
    framing: HeroFraming?,
    cacheRevision: Long,
    modifier: Modifier = Modifier,
    imageAlpha: Float = 1f,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier) {
        val containerW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val containerH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val model = remember(imageUri, cacheRevision, containerW, containerH) {
            ImageRequest.Builder(context)
                .data(imageUri)
                .size(coil3.size.Size.ORIGINAL)
                .memoryCacheKey("$imageUri#${cacheRevision}")
                .diskCacheKey("$imageUri#${cacheRevision}")
                .build()
        }
        val painter = rememberAsyncImagePainter(model)
        val loadState by painter.state.collectAsStateWithLifecycle()
        val intrinsic: ComposeSize = painter.intrinsicSize
        val ready = intrinsic.width > 0f && intrinsic.height > 0f &&
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
            val containerRatio = containerW / containerH
            val viewportW: Float
            val viewportH: Float
            if (containerRatio > HERO_MASK_ASPECT_RATIO) {
                viewportW = containerW
                viewportH = containerW / HERO_MASK_ASPECT_RATIO
            } else {
                viewportH = containerH
                viewportW = containerH * HERO_MASK_ASPECT_RATIO
            }

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
            val left = leftUnclamped.coerceIn(minLeft, maxLeft)
            val top = topUnclamped.coerceIn(minTop, maxTop)
            
            // requiredSize implicitly centers the content if it's larger than the parent.
            // We must subtract this centering offset so our calculated `left` and `top` are absolute.
            val centerOffsetX = (containerW - scaledW) / 2f
            val centerOffsetY = (containerH - scaledH) / 2f
            val finalLeft = left - centerOffsetX
            val finalTop = top - centerOffsetY

            val widthDp = with(density) { scaledW.toDp() }
            val heightDp = with(density) { scaledH.toDp() }
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .requiredSize(widthDp, heightDp)
                    .offset { IntOffset(finalLeft.roundToInt(), finalTop.roundToInt()) }
                    .graphicsLayer { alpha = imageAlpha },
            )
        }
    }
}
