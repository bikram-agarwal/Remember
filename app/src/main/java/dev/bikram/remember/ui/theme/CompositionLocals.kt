package dev.bikram.remember.ui.theme

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import dev.bikram.remember.data.ThemeState

val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> { error("No SnackbarHostState provided") }

data class ProgressiveBlurStyle(
    val topHeightPx: Float,
    val bottomHeightPx: Float,
    val blurRadius: Float,
    val overlayAlpha: Float,
    val overlayAlphaBottom: Float,
    /** Top-edge blur curve exponent; lower values keep blur stronger over overlaid chrome. */
    val topBlurProgressPower: Float = 2.5f,
)

val LocalProgressiveBlurStyle = staticCompositionLocalOf<ProgressiveBlurStyle?> { null }

val LocalUseGradient = compositionLocalOf { false }

val LocalHeroOnCards = compositionLocalOf { false }

val LocalBlurBars = compositionLocalOf { true }

val LocalUseEnhancedShading = compositionLocalOf { false }

val LocalReducedMotion = compositionLocalOf { false }

/**
 * The full, already-collected [ThemeState]. Provided once from RememberTheme so downstream
 * screens (Settings, etc.) can read it synchronously on first composition — no flash from
 * default values → DataStore-backed values.
 */
val LocalThemeState = compositionLocalOf { ThemeState() }
