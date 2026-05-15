package dev.bikram.remember.ui.theme

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.bikram.remember.data.ThemeMode

private val DarkContent = Color(0xFFE6E6EA)
private val LightContent = Color(0xFF1C1B1F)

/**
 * Elevated card colors used by every list card so all cards read the same as
 * Settings and Edit panels. BLACK mode uses the lower surface rung so cards
 * stay OLED-dark while still separating from the page background.
 *
 * Reads [LocalIsDark] (provided by [RememberTheme]) instead of recomputing luminance
 * per call - this is hit on every list card and shows up under repeated profiling.
 */
@Composable
fun elevatedCardColors(): CardColors {
    val themeState = LocalThemeState.current
    val container =
        if (themeState.themeMode == ThemeMode.BLACK) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    val content = if (LocalIsDark.current) DarkContent else LightContent
    return CardDefaults.elevatedCardColors(
        containerColor = container,
        contentColor = content,
    )
}
