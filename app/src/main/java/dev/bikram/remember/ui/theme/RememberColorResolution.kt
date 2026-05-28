package dev.bikram.remember.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import com.materialkolor.rememberDynamicColorScheme
import dev.bikram.remember.data.ColorSource
import dev.bikram.remember.data.PaletteStyleOpt
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
    val materialYouAvailable = spec.source == ColorSource.MATERIAL_YOU
    val customTriplet =
        if (spec.source == ColorSource.CUSTOM) {
            parseCustomTriplet(themeState.activeCustomSeed)
        } else {
            null
        }
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
            customTriplet != null -> rememberCuratedColorScheme(customTriplet, themeState, darkTheme, black)
            else -> rememberSeededColorScheme(spec, themeState, darkTheme, black)
        }
    val oledAdjusted = if (black) base.toOled() else base
    val tinted =
        if (!black) {
            oledAdjusted.tintSurfacesTowardPrimary(darkTheme, themeState.shadingIntensity)
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
    val tripletOverrides =
        curated.takeIf { themeState.paletteStyle == PaletteStyleOpt.TONAL_SPOT }
    return rememberDynamicColorScheme(
        seedColor = curated.primary,
        isDark = darkTheme,
        primary = tripletOverrides?.primary,
        secondary = tripletOverrides?.secondary,
        tertiary = tripletOverrides?.tertiary,
        style = style,
        isAmoled = black,
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

internal fun parseCustomTriplet(activeCustomSeed: String): CuratedPalette? {
    if (activeCustomSeed.isBlank()) return null
    val parts = activeCustomSeed.split("|")
    val primaryHex = parts.getOrNull(0) ?: return null
    val primaryColor = runCatching { Color(primaryHex.toColorInt()) }.getOrNull() ?: return null

    if (parts.size >= 3) {
        val secondaryHex = parts[1]
        val tertiaryHex = parts[2]
        val secondaryColor = runCatching { Color(secondaryHex.toColorInt()) }.getOrNull()
        val tertiaryColor = runCatching { Color(tertiaryHex.toColorInt()) }.getOrNull()
        if (secondaryColor != null && tertiaryColor != null) {
            return CuratedPalette(primaryColor, secondaryColor, tertiaryColor)
        }
    }
    return generateTripletForSeed(primaryColor)
}
