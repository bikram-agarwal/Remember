package dev.bikram.remember.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import dev.bikram.remember.R
import dev.bikram.remember.ui.components.RememberIconButton

/**
 * Full-screen image viewer. When [onDelete] is non-null, a delete button is shown at the top-start
 * corner (opposite the close button). Tapping it invokes [onDelete] and then dismisses the viewer,
 * so callers don't need to coordinate the dismiss themselves.
 */
@Composable
fun FullScreenImageDialog(
    imageUri: String,
    imageCacheRevision: Long = 0L,
    imageContentDescription: String,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val closeLabel = stringResource(R.string.common_back)
    val closeSemantics = remember(closeLabel) {
        Modifier.semantics { contentDescription = closeLabel }
    }
    val deleteLabel = stringResource(R.string.edit_remove_picture_cd)
    val deleteSemantics = remember(deleteLabel) {
        Modifier.semantics { contentDescription = deleteLabel }
    }
    val imageRequest = rememberHeroImageRequest(imageUri, imageCacheRevision, maxSidePx = 4096)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = imageContentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                if (onDelete != null) {
                    RememberIconButton(
                        onClick = {
                            onDelete()
                            onDismiss()
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .then(deleteSemantics),
                        colors = IconButtonDefaults.iconButtonColors(
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
                RememberIconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .then(closeSemantics),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.45f),
                        contentColor = Color.White,
                    ),
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "close",
                        size = 24.dp,
                        tint = Color.White,
                        weight = FontWeight.Medium,
                    )
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
) {
    var retainedImageUri by remember { mutableStateOf(imageUri) }
    var retainedCacheRevision by remember { mutableStateOf(imageCacheRevision) }
    var retainedSharedKey by remember { mutableStateOf(sharedElementKey) }
    LaunchedEffect(imageUri, imageCacheRevision, sharedElementKey) {
        if (imageUri != null) {
            retainedImageUri = imageUri
            retainedCacheRevision = imageCacheRevision
        }
        if (sharedElementKey != null) {
            retainedSharedKey = sharedElementKey
        }
    }
    val effectiveImageUri = imageUri ?: retainedImageUri
    // Retain the shared key during exit. Callers typically derive sharedElementKey
    // from the same nullable state that drives `visible`, so on dismiss the key
    // would otherwise drop to null mid-exit and the close transition would lose
    // its container-transform partner (it would just fade out instead of shrinking
    // back into the inline hero).
    val effectiveSharedKey = sharedElementKey ?: retainedSharedKey
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedVisibility(
        visible = visible && effectiveImageUri != null,
        enter = fadeIn(animationSpec = effectsSpec),
        exit = fadeOut(animationSpec = effectsSpec),
        modifier = modifier,
    ) {
        val visibleImageUri = effectiveImageUri ?: return@AnimatedVisibility
        BackHandler(onBack = onDismiss)
        val closeLabel = stringResource(R.string.common_back)
        val closeSemantics = remember(closeLabel) {
            Modifier.semantics { contentDescription = closeLabel }
        }
        val deleteLabel = stringResource(R.string.edit_remove_picture_cd)
        val deleteSemantics = remember(deleteLabel) {
            Modifier.semantics { contentDescription = deleteLabel }
        }
        val imageRequest = rememberHeroImageRequest(visibleImageUri, retainedCacheRevision, maxSidePx = 2048)
        val sharedScope = dev.bikram.remember.ui.nav.LocalSharedTransitionScope.current
        val imageModifier = if (sharedScope != null && effectiveSharedKey != null) {
            with(sharedScope) {
                // Default resizeMode (RemeasureToBounds): each end renders the image at
                // its current animated bounds with its own ContentScale. Using
                // scaleToBounds(FillBounds) here stretched the overlay's Fit-scaled
                // image to fill the intermediate bounds during the transition, which
                // looked off because the inline hero is rendered with HeroFramedImage's
                // framed crop. Letting each side keep its native scaling reads as a
                // smooth container transform.
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = effectiveSharedKey),
                    animatedVisibilityScope = this@AnimatedVisibility,
                )
            }
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f)),
        ) {
            Surface(
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxSize()
                    .then(imageModifier),
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = imageContentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (onDelete != null) {
                RememberIconButton(
                    onClick = {
                        onDelete()
                        onDismiss()
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .then(deleteSemantics),
                    colors = IconButtonDefaults.iconButtonColors(
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
            RememberIconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .then(closeSemantics),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White,
                ),
            ) {
                RememberMaterialRoundedSymbol(
                    name = "close",
                    size = 24.dp,
                    tint = Color.White,
                    weight = FontWeight.Medium,
                )
            }
        }
    }
}
