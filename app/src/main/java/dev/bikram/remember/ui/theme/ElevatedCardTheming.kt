package dev.bikram.remember.ui.theme

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * Elevated card colors used by every list card so all cards read the same as
 * Settings and Edit panels. Uses the theme's surfaceContainer directly; the
 * `fixedCardColors` toggle is handled upstream in Theme.kt by gating
 * `tintSurfacesTowardPrimary` so the scheme itself changes shade.
 */
@Composable
fun elevatedCardColors(): CardColors {
    val scheme = MaterialTheme.colorScheme
    val dark = ColorUtils.calculateLuminance(scheme.background.toArgb()) < 0.35
    val content = if (dark) Color(0xFFE6E6EA) else Color(0xFF1C1B1F)
    return CardDefaults.elevatedCardColors(
        containerColor = scheme.surfaceContainer,
        contentColor = content,
    )
}
