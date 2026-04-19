package dev.bikram.remember.ui.theme

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkContent = Color(0xFFE6E6EA)
private val LightContent = Color(0xFF1C1B1F)

/**
 * Elevated card colors used by every list card so all cards read the same as
 * Settings and Edit panels. Uses the theme's surfaceContainer directly; the
 * `fixedCardColors` toggle is handled upstream in [RememberTheme] by gating the same
 * primary surface boost path as FilePipe (`useEnhancedShading` equivalent).
 *
 * Reads [LocalIsDark] (provided by [RememberTheme]) instead of recomputing luminance
 * per call - this is hit on every list card and shows up under repeated profiling.
 */
@Composable
fun elevatedCardColors(): CardColors {
    val container = MaterialTheme.colorScheme.surfaceContainer
    val content = if (LocalIsDark.current) DarkContent else LightContent
    return CardDefaults.elevatedCardColors(
        containerColor = container,
        contentColor = content,
    )
}
