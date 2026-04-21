package dev.bikram.remember.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
