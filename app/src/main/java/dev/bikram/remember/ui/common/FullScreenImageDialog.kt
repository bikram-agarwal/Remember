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

@Composable
fun FullScreenImageDialog(
    imageUri: String,
    imageCacheRevision: Long = 0L,
    imageContentDescription: String,
    onDismiss: () -> Unit,
) {
    val closeLabel = stringResource(R.string.common_back)
    val closeSemantics = remember(closeLabel) {
        Modifier.semantics { contentDescription = closeLabel }
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
