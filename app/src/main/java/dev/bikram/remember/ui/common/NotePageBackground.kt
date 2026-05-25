package dev.bikram.remember.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import dev.bikram.remember.ui.theme.LocalUseGradient

@Composable
fun NotePageBackground(
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    modifier: Modifier = Modifier,
) {
    if (LocalUseGradient.current) {
        val gradientBrush =
            remember(colorScheme.primaryContainer, colorScheme.surface) {
                Brush.verticalGradient(
                    colorStops =
                        arrayOf(
                            0f to colorScheme.primaryContainer.copy(alpha = 0.48f),
                            0.55f to colorScheme.surface.copy(alpha = 0f),
                        ),
                )
            }
        Box(
            modifier
                .fillMaxSize()
                .background(colorScheme.surface)
                .background(gradientBrush),
        )
    } else {
        Box(modifier.fillMaxSize().background(colorScheme.background))
    }
}
