package dev.bikram.remember.ui.theme

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import dev.bikram.remember.data.NotesUiState
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

/** When false, note cards show the title and metadata only. Defaults to showing content. */
val LocalShowNoteContentOnCards = compositionLocalOf { true }

val LocalAdaptiveNoteThemes = compositionLocalOf { true }

/**
 * Notes UI preferences, already collected. Provided from [RememberTheme] so settings can read them
 * on first composition.
 */
val LocalNotesUiState = compositionLocalOf { NotesUiState() }

val LocalBlurBars = compositionLocalOf { true }

val LocalUseEnhancedShading = compositionLocalOf { false }

val LocalReducedMotion = compositionLocalOf { false }

/**
 * The full, already-collected [ThemeState]. Provided once from RememberTheme so downstream
 * screens (Settings, etc.) can read it synchronously on first composition — no flash from
 * default values → DataStore-backed values.
 */
val LocalThemeState = compositionLocalOf { ThemeState() }
