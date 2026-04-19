package dev.bikram.remember.ui.common
import androidx.compose.material3.TextButton

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size
import dev.bikram.remember.R
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.RememberButton

@Composable
fun HeroFramingEditorDialog(
    imageUri: String,
    pendingCopiedFile: File?,
    initialFraming: HeroFraming?,
    onDismiss: () -> Unit,
    onConfirm: (HeroFraming) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val model = remember(imageUri) {
        ImageRequest.Builder(context)
            .data(imageUri)
            .size(Size.ORIGINAL)
            .build()
    }
    val painter = rememberAsyncImagePainter(model)
    val loadState by painter.state.collectAsStateWithLifecycle()
    val intrinsic: ComposeSize = painter.intrinsicSize
    val imageReady = intrinsic.width > 0f && intrinsic.height > 0f &&
        loadState is AsyncImagePainter.State.Success
    var zoom by remember(imageUri) { mutableFloatStateOf(initialFraming?.zoom?.coerceIn(1f, 8f) ?: 1f) }
    var panXPx by remember(imageUri) { mutableFloatStateOf(0f) }
    var panYPx by remember(imageUri) { mutableFloatStateOf(0f) }
    var lastMask by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    Dialog(
        onDismissRequest = {
            pendingCopiedFile?.delete()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    val maxW = constraints.maxWidth.toFloat()
                    val maxH = constraints.maxHeight.toFloat()
                    val maskW = min(maxW * 0.92f, maxH * HERO_MASK_ASPECT_RATIO * 0.92f)
                    val maskH = maskW / HERO_MASK_ASPECT_RATIO
                    val maskLeft = (maxW - maskW) / 2f
                    val maskTop = (maxH - maskH) / 2f
                    lastMask = maskW to maskH

                    LaunchedEffect(imageReady, initialFraming, maskW, maskH, intrinsic.width, intrinsic.height) {
                        if (!imageReady) return@LaunchedEffect
                        val iw = intrinsic.width
                        val ih = intrinsic.height
                        val framing = (initialFraming ?: HeroFraming()).clamped()
                        zoom = framing.zoom
                        val cover = max(maskW / iw, maskH / ih)
                        val displayScale = cover * zoom
                        val scaledW = iw * displayScale
                        val scaledH = ih * displayScale
                        val leftUnclamped = maskW / 2f - framing.focalX * iw * displayScale
                        val topUnclamped = maskH / 2f - framing.focalY * ih * displayScale
                        val minLeft = maskW - scaledW
                        val minTop = maskH - scaledH
                        val left = leftUnclamped.coerceIn(minLeft, 0f)
                        val top = topUnclamped.coerceIn(minTop, 0f)
                        val centerLeft = (maskW - scaledW) / 2f
                        val centerTop = (maskH - scaledH) / 2f
                        panXPx = left - centerLeft
                        panYPx = top - centerTop
                    }

                    if (!imageReady) {
                        Text(
                            "Loading…",
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        val iw = intrinsic.width
                        val ih = intrinsic.height
                        val cover = max(maskW / iw, maskH / ih)
                        val displayScale = cover * zoom.coerceIn(1f, 8f)
                        val scaledW = iw * displayScale
                        val scaledH = ih * displayScale
                        val centerLeft = (maskW - scaledW) / 2f
                        val centerTop = (maskH - scaledH) / 2f
                        val left = (centerLeft + panXPx).coerceIn(maskW - scaledW, 0f)
                        val top = (centerTop + panYPx).coerceIn(maskH - scaledH, 0f)
                        val widthDp = with(density) { scaledW.toDp() }
                        val heightDp = with(density) { scaledH.toDp() }
                        val offsetXDp = with(density) { (maskLeft + left).toDp() }
                        val offsetYDp = with(density) { (maskTop + top).toDp() }
                        val cornerPx = with(density) { 18.dp.toPx() }
                        Image(
                            painter = painter,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .requiredSize(widthDp, heightDp)
                                .offset { IntOffset(offsetXDp.roundToPx(), offsetYDp.roundToPx()) }
                                .pointerInput(maskW, maskH, iw, ih, zoom) {
                                    detectTransformGestures { _, pan, zoomChange, _ ->
                                        zoom = (zoom * zoomChange).coerceIn(1f, 8f)
                                        panXPx += pan.x
                                        panYPx += pan.y
                                        val coverNow = max(maskW / iw, maskH / ih)
                                        val displayNow = coverNow * zoom.coerceIn(1f, 8f)
                                        val sw = iw * displayNow
                                        val sh = ih * displayNow
                                        val rangeX = max(0f, (sw - maskW) / 2f)
                                        val rangeY = max(0f, (sh - maskH) / 2f)
                                        panXPx = panXPx.coerceIn(-rangeX, rangeX)
                                        panYPx = panYPx.coerceIn(-rangeY, rangeY)
                                    }
                                },
                        )
                        Canvas(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                        ) {
                            drawRect(Color.Black.copy(alpha = 0.58f))
                            drawRoundRect(
                                color = Color.Transparent,
                                topLeft = Offset(maskLeft, maskTop),
                                size = ComposeSize(maskW, maskH),
                                cornerRadius = CornerRadius(cornerPx, cornerPx),
                                blendMode = BlendMode.Clear,
                            )
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RememberTextButton(
                        onClick = {
                            pendingCopiedFile?.delete()
                            onDismiss()
                        },
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Text(
                        stringResource(R.string.hero_framing_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    RememberButton(
                        onClick = {
                            val maskPair = lastMask
                            if (!imageReady || maskPair == null) {
                                onDismiss()
                                return@RememberButton
                            }
                            val maskW = maskPair.first
                            val maskH = maskPair.second
                            val iw = intrinsic.width
                            val ih = intrinsic.height
                            val cover = max(maskW / iw, maskH / ih)
                            val displayScale = cover * zoom.coerceIn(1f, 8f)
                            val scaledW = iw * displayScale
                            val scaledH = ih * displayScale
                            val centerLeft = (maskW - scaledW) / 2f
                            val centerTop = (maskH - scaledH) / 2f
                            val left = centerLeft + panXPx
                            val top = centerTop + panYPx
                            val focalX = ((maskW / 2f - left) / (iw * displayScale)).coerceIn(0f, 1f)
                            val focalY = ((maskH / 2f - top) / (ih * displayScale)).coerceIn(0f, 1f)
                            onConfirm(HeroFraming(focalX = focalX, focalY = focalY, zoom = zoom.coerceIn(1f, 8f)))
                        },
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            }
        }
    }
}
