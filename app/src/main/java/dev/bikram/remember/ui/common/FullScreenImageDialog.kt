package dev.bikram.remember.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import dev.bikram.remember.R
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.feedback.tapSoundCombinedClickable
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Size as ComposeSize

/**
 * Full-screen image viewer. Tapping outside the fitted image frame dismisses the viewer.
 * When [onDelete] is non-null, a delete button is shown at the top-start corner.
 */
@Composable
fun FullScreenImageDialog(
    imageUri: String,
    imageCacheRevision: Long = 0L,
    imageContentDescription: String,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val deleteLabel = stringResource(R.string.edit_remove_picture_cd)
    val deleteSemantics =
        remember(deleteLabel) {
            Modifier.semantics { contentDescription = deleteLabel }
        }
    val imageRequest = rememberHeroImageRequest(imageUri, imageCacheRevision, maxSidePx = 4096)
    val overlayInteractionSource = remember { MutableInteractionSource() }
    val imageInteractionSource = remember { MutableInteractionSource() }
    val imagePainter = rememberAsyncImagePainter(imageRequest)
    val imageState by imagePainter.state.collectAsStateWithLifecycle()
    val intrinsic = imagePainter.intrinsicSize
    val imageReady =
        intrinsic.width.isFinite() &&
            intrinsic.height.isFinite() &&
            intrinsic.width > 0f &&
            intrinsic.height > 0f &&
            imageState is AsyncImagePainter.State.Success
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = overlayInteractionSource,
                        indication = null,
                        onClick = onDismiss,
                    ),
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val containerWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    val containerHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                    val containerRatio = containerWidth / containerHeight
                    val imageRatio =
                        if (imageReady) {
                            intrinsic.width / intrinsic.height
                        } else {
                            containerRatio
                        }
                    val frameWidth =
                        if (containerRatio > imageRatio) {
                            maxHeight * imageRatio
                        } else {
                            maxWidth
                        }
                    val frameHeight =
                        if (containerRatio > imageRatio) {
                            maxHeight
                        } else {
                            maxWidth / imageRatio
                        }
                    Image(
                        painter = imagePainter,
                        contentDescription = imageContentDescription,
                        contentScale = ContentScale.FillBounds,
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .requiredSize(frameWidth, frameHeight)
                                .clickable(
                                    interactionSource = imageInteractionSource,
                                    indication = null,
                                    onClick = {},
                                ),
                    )
                }
                if (onDelete != null) {
                    RememberIconButton(
                        onClick = {
                            onDelete()
                            onDismiss()
                        },
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .then(deleteSemantics),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.45f),
                                contentColor = Color.White,
                            ),
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "delete_outline",
                            size = 24.dp,
                            tint = Color.White,
                            weight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun FullScreenHeroImageOverlay(
    visible: Boolean,
    imageUri: String?,
    imageCacheRevision: Long,
    imageContentDescription: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sharedElementKey: String? = null,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onReplace: (() -> Unit)? = null,
    onReplaceLongClick: (() -> Unit)? = null,
    initialFraming: HeroFraming? = null,
    startInReframeMode: Boolean = false,
    dismissOnCancelReframe: Boolean = false,
    dismissAfterCommit: Boolean = false,
    onCommitFraming: ((HeroFraming) -> Unit)? = null,
) {
    var retainedImageUri by remember { mutableStateOf(imageUri) }
    var retainedCacheRevision by remember { mutableLongStateOf(imageCacheRevision) }
    var retainedSharedKey by remember { mutableStateOf(sharedElementKey) }
    var reframeMode by remember { mutableStateOf(startInReframeMode) }
    var zoom by remember(retainedImageUri) { mutableFloatStateOf(initialFraming?.zoom?.coerceIn(1f, 8f) ?: 1f) }
    var panXPx by remember(retainedImageUri) { mutableFloatStateOf(0f) }
    var panYPx by remember(retainedImageUri) { mutableFloatStateOf(0f) }
    var lastFrameSizePx by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var lastReframeGeometry by remember { mutableStateOf<ReframeGeometry?>(null) }
    var showCropMorphLayer by remember { mutableStateOf(false) }
    LaunchedEffect(imageUri, imageCacheRevision, sharedElementKey, startInReframeMode) {
        if (imageUri != null) {
            retainedImageUri = imageUri
            retainedCacheRevision = imageCacheRevision
        }
        if (sharedElementKey != null) {
            retainedSharedKey = sharedElementKey
        }
        if (imageUri != null) {
            reframeMode = startInReframeMode
            showCropMorphLayer = false
        }
    }
    val effectiveImageUri = imageUri ?: retainedImageUri
    // Retain the shared key during exit. Callers typically derive sharedElementKey
    // from the same nullable state that drives `visible`, so on dismiss the key
    // would otherwise drop to null mid-exit and the close transition would lose
    // its container-transform partner (it would just fade out instead of shrinking
    // back into the inline hero).
    val effectiveSharedKey = sharedElementKey ?: retainedSharedKey
    val fadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val fadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    AnimatedVisibility(
        visible = visible && effectiveImageUri != null,
        enter = fadeIn(animationSpec = fadeInSpec),
        exit = fadeOut(animationSpec = fadeOutSpec),
        modifier = modifier,
    ) {
        val visibleImageUri = effectiveImageUri ?: return@AnimatedVisibility
        RememberPredictiveBackHandler(onBack = onDismiss)
        val density = LocalDensity.current
        val overlayScope = rememberCoroutineScope()
        val deleteLabel = stringResource(R.string.edit_remove_picture_cd)
        val deleteSemantics =
            remember(deleteLabel) {
                Modifier.semantics { contentDescription = deleteLabel }
            }
        val editLabel = stringResource(R.string.edit_picture_framing_cd)
        val editSemantics =
            remember(editLabel) {
                Modifier.semantics { contentDescription = editLabel }
            }
        val replaceLabel = stringResource(R.string.edit_replace_picture_cd)
        val browseWithAppLabel = stringResource(R.string.hero_image_picker_browse_with_app)
        val replaceSemantics =
            remember(replaceLabel) {
                Modifier.semantics { contentDescription = replaceLabel }
            }
        val closeLabel = stringResource(R.string.common_cancel)
        val closeSemantics =
            remember(closeLabel) {
                Modifier.semantics { contentDescription = closeLabel }
            }
        val imageRequest = rememberHeroImageRequest(visibleImageUri, retainedCacheRevision, maxSidePx = 2048)
        val overlayInteractionSource = remember { MutableInteractionSource() }
        val imageInteractionSource = remember { MutableInteractionSource() }
        val imagePainter = rememberAsyncImagePainter(imageRequest)
        val imageState by imagePainter.state.collectAsStateWithLifecycle()
        val intrinsic = imagePainter.intrinsicSize
        val imageReady =
            intrinsic.width.isFinite() &&
                intrinsic.height.isFinite() &&
                intrinsic.width > 0f &&
                intrinsic.height > 0f &&
                imageState is AsyncImagePainter.State.Success
        val sharedScope = dev.bikram.remember.ui.nav.LocalSharedTransitionScope.current
        val imageModifier =
            if (sharedScope != null && effectiveSharedKey != null) {
                with(sharedScope) {
                    Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState(key = effectiveSharedKey),
                        animatedVisibilityScope = this@AnimatedVisibility,
                    )
                }
            } else {
                Modifier
            }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = overlayInteractionSource,
                        indication = null,
                        onClick = onDismiss,
                    ),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val maxW = constraints.maxWidth.toFloat()
                val maxH = constraints.maxHeight.toFloat()
                val imageAspect =
                    if (imageReady) {
                        (intrinsic.width / intrinsic.height).coerceIn(0.5f, 3.5f)
                    } else {
                        HERO_MASK_ASPECT_RATIO
                    }
                val frameMaxWidth = (maxWidth - 16.dp).coerceAtLeast(1.dp)
                val frameMaxHeight = (maxHeight - 188.dp).coerceAtLeast(160.dp)
                val frameMaxRatio = frameMaxWidth / frameMaxHeight
                val baseImageWidth: Dp
                val baseImageHeight: Dp
                if (frameMaxRatio > imageAspect) {
                    baseImageHeight = frameMaxHeight
                    baseImageWidth = baseImageHeight * imageAspect
                } else {
                    baseImageWidth = frameMaxWidth
                    baseImageHeight = baseImageWidth / imageAspect
                }
                val baseImageW = with(density) { baseImageWidth.toPx() }
                val baseImageH = with(density) { baseImageHeight.toPx() }
                val baseImageLeft = (maxW - baseImageW) / 2f
                val baseImageTop = (maxH - baseImageH) / 2f
                val baseImageWidthDp = with(density) { baseImageW.toDp() }
                val baseImageHeightDp = with(density) { baseImageH.toDp() }
                val baseImageOffsetXDp = with(density) { baseImageLeft.toDp() }
                val baseImageOffsetYDp = with(density) { baseImageTop.toDp() }
                if (reframeMode && imageReady) {
                    val baseFitScale = min(baseImageW / intrinsic.width, baseImageH / intrinsic.height)
                    val maskW = min(baseImageW, baseImageH * HERO_MASK_ASPECT_RATIO)
                    val maskH = maskW / HERO_MASK_ASPECT_RATIO
                    val baseMaskLeft = baseImageLeft + (baseImageW - maskW) / 2f
                    val baseMaskTop = baseImageTop + (baseImageH - maskH) / 2f
                    val cornerPx = with(density) { 8.dp.toPx() }
                    lastFrameSizePx = maskW to maskH

                    LaunchedEffect(
                        retainedImageUri,
                        imageReady,
                        initialFraming,
                        maskW,
                        maskH,
                        intrinsic.width,
                        intrinsic.height,
                    ) {
                        val iw = intrinsic.width
                        val ih = intrinsic.height
                        val framing = (initialFraming ?: HeroFraming()).clamped()
                        val cover = max(maskW / iw, maskH / ih)
                        val displayScale = baseFitScale
                        zoom = (displayScale / cover).coerceIn(1f, 8f)
                        val desiredMaskLeft = baseImageLeft + framing.focalX * baseImageW - maskW / 2f
                        val desiredMaskTop = baseImageTop + framing.focalY * baseImageH - maskH / 2f
                        val rangeX = max(0f, (baseImageW - maskW) / 2f)
                        val rangeY = max(0f, (baseImageH - maskH) / 2f)
                        panXPx = (desiredMaskLeft - baseMaskLeft).coerceIn(-rangeX, rangeX)
                        panYPx = (desiredMaskTop - baseMaskTop).coerceIn(-rangeY, rangeY)
                    }

                    val iw = intrinsic.width
                    val ih = intrinsic.height
                    val cover = max(maskW / iw, maskH / ih)
                    val displayScale = baseFitScale
                    val rangeX = max(0f, (baseImageW - maskW) / 2f)
                    val rangeY = max(0f, (baseImageH - maskH) / 2f)
                    val frameOffsetX = panXPx.coerceIn(-rangeX, rangeX)
                    val frameOffsetY = panYPx.coerceIn(-rangeY, rangeY)
                    val maskLeft = baseMaskLeft + frameOffsetX
                    val maskTop = baseMaskTop + frameOffsetY
                    lastReframeGeometry =
                        ReframeGeometry(
                            maskWidthPx = maskW,
                            maskHeightPx = maskH,
                            baseMaskLeftPx = baseMaskLeft,
                            baseMaskTopPx = baseMaskTop,
                            imageLeftPx = baseImageLeft,
                            imageTopPx = baseImageTop,
                            displayScale = displayScale,
                        )
                    Surface(
                        color = Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier =
                            Modifier
                                .requiredSize(baseImageWidthDp, baseImageHeightDp)
                                .offset {
                                    IntOffset(
                                        baseImageOffsetXDp.roundToPx(),
                                        baseImageOffsetYDp.roundToPx(),
                                    )
                                }.pointerInput(maskW, maskH, iw, ih) {
                                    detectTransformGestures { _, pan, _, _ ->
                                        panXPx += pan.x
                                        panYPx += pan.y
                                        panXPx = panXPx.coerceIn(-rangeX, rangeX)
                                        panYPx = panYPx.coerceIn(-rangeY, rangeY)
                                    }
                                },
                    ) {
                        Image(
                            painter = imagePainter,
                            contentDescription = imageContentDescription,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (showCropMorphLayer && sharedScope != null && effectiveSharedKey != null) {
                        val previewImageOffsetXDp = with(density) { (baseImageLeft - maskLeft).toDp() }
                        val previewImageOffsetYDp = with(density) { (baseImageTop - maskTop).toDp() }
                        Surface(
                            color = Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            modifier =
                                Modifier
                                    .offset { IntOffset(maskLeft.roundToInt(), maskTop.roundToInt()) }
                                    .requiredSize(
                                        with(density) { maskW.toDp() },
                                        with(density) { maskH.toDp() },
                                    ).then(imageModifier),
                        ) {
                            Image(
                                painter = imagePainter,
                                contentDescription = null,
                                contentScale = ContentScale.FillBounds,
                                modifier =
                                    Modifier
                                        .requiredSize(baseImageWidthDp, baseImageHeightDp)
                                        .offset {
                                            IntOffset(
                                                previewImageOffsetXDp.roundToPx(),
                                                previewImageOffsetYDp.roundToPx(),
                                            )
                                        },
                            )
                        }
                    }
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
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.76f),
                            topLeft = Offset(maskLeft, maskTop),
                            size = ComposeSize(maskW, maskH),
                            cornerRadius = CornerRadius(cornerPx, cornerPx),
                            style = Stroke(width = with(density) { 1.5.dp.toPx() }),
                        )
                    }
                } else {
                    Surface(
                        color = Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier =
                            Modifier
                                .requiredSize(baseImageWidthDp, baseImageHeightDp)
                                .offset {
                                    IntOffset(
                                        baseImageOffsetXDp.roundToPx(),
                                        baseImageOffsetYDp.roundToPx(),
                                    )
                                }.then(imageModifier)
                                .clickable(
                                    interactionSource = imageInteractionSource,
                                    indication = null,
                                    onClick = {},
                                ),
                    ) {
                        Image(
                            painter = imagePainter,
                            contentDescription = imageContentDescription,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            PhotoOverlayCircleButton(
                icon = "close",
                contentDescriptionModifier = closeSemantics,
                onClick = onDismiss,
                buttonSize = 40.dp,
                iconSize = 22.dp,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = 20.dp, top = 20.dp),
            )
            if (reframeMode) {
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    PhotoOverlayActionPill(
                        label = stringResource(R.string.common_cancel),
                        icon = "close",
                        onClick = {
                            showCropMorphLayer = false
                            if (dismissOnCancelReframe) {
                                onDismiss()
                            } else {
                                reframeMode = false
                            }
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    PhotoOverlayActionPill(
                        label = stringResource(R.string.common_save),
                        icon = "check",
                        onClick = {
                            val geometry = lastReframeGeometry
                            if (!imageReady || geometry == null) {
                                return@PhotoOverlayActionPill
                            }
                            val frameWidthPx = geometry.maskWidthPx
                            val frameHeightPx = geometry.maskHeightPx
                            val iw = intrinsic.width
                            val ih = intrinsic.height
                            val cover = max(frameWidthPx / iw, frameHeightPx / ih)
                            val scaledW = iw * geometry.displayScale
                            val scaledH = ih * geometry.displayScale
                            val rangeX = max(0f, (scaledW - frameWidthPx) / 2f)
                            val rangeY = max(0f, (scaledH - frameHeightPx) / 2f)
                            val frameOffsetX = panXPx.coerceIn(-rangeX, rangeX)
                            val frameOffsetY = panYPx.coerceIn(-rangeY, rangeY)
                            val frameLeft = geometry.baseMaskLeftPx + frameOffsetX
                            val frameTop = geometry.baseMaskTopPx + frameOffsetY
                            val focalX =
                                (
                                    (frameLeft + frameWidthPx / 2f - geometry.imageLeftPx) /
                                        (iw * geometry.displayScale)
                                ).coerceIn(0f, 1f)
                            val focalY =
                                (
                                    (frameTop + frameHeightPx / 2f - geometry.imageTopPx) /
                                        (ih * geometry.displayScale)
                                ).coerceIn(0f, 1f)
                            val savedZoom = (geometry.displayScale / cover).coerceIn(1f, 8f)
                            onCommitFraming?.invoke(HeroFraming(focalX = focalX, focalY = focalY, zoom = savedZoom))
                            if (dismissAfterCommit) {
                                showCropMorphLayer = true
                                overlayScope.launch {
                                    withFrameNanos { }
                                    onDismiss()
                                }
                            } else {
                                showCropMorphLayer = false
                                reframeMode = false
                            }
                        },
                    )
                }
            } else {
                Surface(
                    color = Color.Black.copy(alpha = 0.58f),
                    shape = RoundedCornerShape(44.dp),
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PhotoOverlayActionPill(
                            label = stringResource(R.string.edit_picture_reframe),
                            icon = "crop",
                            contentDescriptionModifier = editSemantics,
                            onClick = {
                                onEdit?.invoke()
                                showCropMorphLayer = false
                                reframeMode = true
                            },
                        )
                        if (onReplace != null) {
                            PhotoOverlayActionPill(
                                label = stringResource(R.string.edit_picture_replace),
                                icon = "undo",
                                contentDescriptionModifier = replaceSemantics,
                                onClick = onReplace,
                                onLongClick = onReplaceLongClick,
                                onLongClickLabel = browseWithAppLabel,
                            )
                        }
                        if (onDelete != null) {
                            PhotoOverlayCircleButton(
                                icon = "delete",
                                destructive = true,
                                contentDescriptionModifier = deleteSemantics,
                                buttonSize = 40.dp,
                                iconSize = 22.dp,
                                onClick = {
                                    onDelete()
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoOverlayActionPill(
    label: String,
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescriptionModifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val container =
        if (pressed) {
            Color(0xFFEBC982)
        } else {
            Color(0xFF1D1D1B).copy(alpha = 0.72f)
        }
    val content = if (pressed) Color(0xFF17110B) else Color.White.copy(alpha = 0.84f)
    Surface(
        color = container,
        shape = RoundedCornerShape(34.dp),
        modifier =
            modifier
                .then(contentDescriptionModifier)
                .tapSoundCombinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onLongClickLabel = onLongClickLabel,
                ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RememberMaterialRoundedSymbol(
                name = icon,
                size = 20.dp,
                tint = content,
                weight = FontWeight.Medium,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = content,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private data class ReframeGeometry(
    val maskWidthPx: Float,
    val maskHeightPx: Float,
    val baseMaskLeftPx: Float,
    val baseMaskTopPx: Float,
    val imageLeftPx: Float,
    val imageTopPx: Float,
    val displayScale: Float,
)

@Composable
private fun PhotoOverlayCircleButton(
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    buttonSize: Dp = 52.dp,
    iconSize: Dp = 24.dp,
    contentDescriptionModifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val container =
        when {
            destructive && pressed -> Color(0xFF5A271B)
            destructive -> Color(0xFF3A1C15).copy(alpha = 0.82f)
            pressed -> Color.White.copy(alpha = 0.18f)
            else -> Color(0xFF151515).copy(alpha = 0.78f)
        }
    val content =
        when {
            destructive -> Color(0xFFFF8A75)
            else -> Color.White.copy(alpha = if (pressed) 1f else 0.84f)
        }
    Surface(
        color = container,
        shape = CircleShape,
        modifier =
            modifier
                .size(buttonSize)
                .then(contentDescriptionModifier)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            RememberMaterialRoundedSymbol(
                name = icon,
                size = iconSize,
                tint = content,
                weight = FontWeight.Medium,
            )
        }
    }
}
