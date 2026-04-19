package dev.bikram.remember.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.request.ImageRequest
import coil3.size.Size

/**
 * Coil caches aggressively by model URI. Bump [revision] whenever the bytes behind the same
 * URI can change (in-place edit, re-save) so the hero preview reloads.
 */
@Composable
fun rememberHeroImageRequest(
    uri: String,
    revision: Long,
    maxSidePx: Int,
): ImageRequest {
    val context = LocalContext.current
    val cacheKey = "$uri#${revision}"
    return remember(uri, revision, maxSidePx) {
        ImageRequest.Builder(context)
            .data(uri)
            .size(Size(maxSidePx, maxSidePx))
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .build()
    }
}

fun heroImageRequestForCard(context: Context, uri: String, revision: Long): ImageRequest {
    val cacheKey = "$uri#${revision}"
    return ImageRequest.Builder(context)
        .data(uri)
        .memoryCacheKey(cacheKey)
        .diskCacheKey(cacheKey)
        .build()
}
