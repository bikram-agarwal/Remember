package dev.bikram.remember.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.bikram.remember.ui.theme.pillShape

@Composable
internal fun AlertBarSurface(
    contentAlpha: Float,
    shadowAlpha: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.graphicsLayer { alpha = shadowAlpha },
        shape = pillShape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
    ) {
        Box(modifier = Modifier.graphicsLayer { alpha = contentAlpha }) {
            Surface(
                shape = pillShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 0.dp,
                content = content,
            )
        }
    }
}
