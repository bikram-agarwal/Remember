package dev.bikram.remember.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import com.materialkolor.rememberDynamicColorScheme
import dev.bikram.remember.data.ColorSource
import dev.bikram.remember.data.ThemeState

internal data class RememberColorResolution(
    val colorScheme: ColorScheme,
    val backgroundScheme: ColorScheme,
)

@Composable
internal fun rememberResolvedColorScheme(
    context: Context,
    themeState: ThemeState,
    darkTheme: Boolean,
    black: Boolean,
): RememberColorResolution {
    val spec = colorSourceSpecFor(themeState.colorSource)
    val materialYouAvailable =
        spec.source == ColorSource.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base =
        when {
            materialYouAvailable -> {
                if (darkTheme) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
            }
            spec.triplet != null -> rememberCuratedColorScheme(spec.triplet, themeState, darkTheme, black)
            else -> rememberSeededColorScheme(spec, themeState, darkTheme, black)
        }
    val oledAdjusted = if (black) base.toOled() else base
    val tinted =
        if (!themeState.useEnhancedShading && !black) {
            oledAdjusted.tintSurfacesTowardPrimary(darkTheme)
        } else {
            oledAdjusted
        }
    val themed =
        if (materialYouAvailable) {
            tinted
        } else {
            tinted
                .boostOutlineForVisibility(darkTheme)
                .boostContainersForSeedThemes(darkTheme)
        }
    return RememberColorResolution(
        colorScheme = themed,
        backgroundScheme = oledAdjusted,
    )
}

@Composable
private fun rememberCuratedColorScheme(
    curated: CuratedPalette,
    themeState: ThemeState,
    darkTheme: Boolean,
    black: Boolean,
): ColorScheme {
    val style = themeState.paletteStyle.toLib()
    val primaryScheme =
        rememberDynamicColorScheme(
            seedColor = curated.primary,
            isDark = darkTheme,
            style = style,
            isAmoled = black,
        )
    val secondaryScheme =
        rememberDynamicColorScheme(
            seedColor = curated.secondary,
            isDark = darkTheme,
            style = style,
            isAmoled = black,
        )
    val tertiaryScheme =
        rememberDynamicColorScheme(
            seedColor = curated.tertiary,
            isDark = darkTheme,
            style = style,
            isAmoled = black,
        )
    return primaryScheme.copy(
        secondary = secondaryScheme.primary,
        onSecondary = secondaryScheme.onPrimary,
        secondaryContainer = secondaryScheme.primaryContainer,
        onSecondaryContainer = secondaryScheme.onPrimaryContainer,
        tertiary = tertiaryScheme.primary,
        onTertiary = tertiaryScheme.onPrimary,
        tertiaryContainer = tertiaryScheme.primaryContainer,
        onTertiaryContainer = tertiaryScheme.onPrimaryContainer,
    )
}

@Composable
private fun rememberSeededColorScheme(
    spec: ColorSourceSpec,
    themeState: ThemeState,
    darkTheme: Boolean,
    black: Boolean,
): ColorScheme {
    val seed =
        when (spec.fallbackBehavior) {
            ColorSourceFallback.DEFAULT_SEED_WHEN_UNAVAILABLE -> DefaultSeed
            ColorSourceFallback.DEFAULT_SEED_WHEN_INVALID,
            ColorSourceFallback.NONE,
            -> colorSourceSeedFor(spec, themeState.activeCustomSeed)
        }
    return rememberDynamicColorScheme(
        seedColor = seed,
        isDark = darkTheme,
        style = themeState.paletteStyle.toLib(),
        isAmoled = black,
    )
}
