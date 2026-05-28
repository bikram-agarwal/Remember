package dev.bikram.remember.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/** Keep BLACK mode OLED-dark while preserving enough surface separation for cards and sheets. */
internal fun ColorScheme.toOled(): ColorScheme =
    copy(
        background = Color.Black,
        surface = Color(0xFF050505),
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF111111),
        surfaceContainer = Color(0xFF1A1A1A),
        surfaceContainerHigh = Color(0xFF242424),
        surfaceContainerHighest = Color(0xFF303030),
    )

/**
 * Blend every surface-container role toward the accent so cards pick up a visible theme hue.
 */
internal fun ColorScheme.tintSurfacesTowardPrimary(
    dark: Boolean,
    intensityFactor: Float,
): ColorScheme {
    if (intensityFactor <= 0.0f) return this
    val accentArgb =
        ColorUtils.blendARGB(
            primary.toArgb(),
            primaryContainer.toArgb(),
            if (dark) 0.4f else 0.3f,
        )
    val baseAmount = if (dark) 0.24f else 0.15f
    val amount = baseAmount * intensityFactor

    fun tint(color: Color) = Color(ColorUtils.blendARGB(color.toArgb(), accentArgb, amount))
    return copy(
        surface = tint(surface),
        surfaceVariant = tint(surfaceVariant),
        surfaceDim = tint(surfaceDim),
        surfaceBright = tint(surfaceBright),
        surfaceContainerLowest = tint(surfaceContainerLowest),
        surfaceContainerLow = tint(surfaceContainerLow),
        surfaceContainer = tint(surfaceContainer),
        surfaceContainerHigh = tint(surfaceContainerHigh),
        surfaceContainerHighest = tint(surfaceContainerHighest),
    )
}

/**
 * Pull `outline` and `outlineVariant` toward `onSurface` so outlined chrome stays legible.
 */
internal fun ColorScheme.boostOutlineForVisibility(dark: Boolean): ColorScheme {
    val targetArgb = onSurface.toArgb()
    val outlineBlend = if (dark) 0.32f else 0.28f
    val outlineVariantBlend = if (dark) 0.20f else 0.16f
    return copy(
        outline = Color(ColorUtils.blendARGB(outline.toArgb(), targetArgb, outlineBlend)),
        outlineVariant =
            Color(
                ColorUtils.blendARGB(outlineVariant.toArgb(), targetArgb, outlineVariantBlend),
            ),
    )
}

/**
 * Pull accent containers toward their accent hues so selected pills and tonal buttons pop.
 */
internal fun ColorScheme.boostContainersForSeedThemes(dark: Boolean): ColorScheme {
    val primaryBlend = if (dark) 0.30f else 0.24f
    val secondaryBlend = if (dark) 0.26f else 0.20f
    val tertiaryBlend = if (dark) 0.28f else 0.22f
    return copy(
        primaryContainer =
            Color(
                ColorUtils.blendARGB(primaryContainer.toArgb(), primary.toArgb(), primaryBlend),
            ),
        secondaryContainer =
            Color(
                ColorUtils.blendARGB(secondaryContainer.toArgb(), secondary.toArgb(), secondaryBlend),
            ),
        tertiaryContainer =
            Color(
                ColorUtils.blendARGB(tertiaryContainer.toArgb(), tertiary.toArgb(), tertiaryBlend),
            ),
    )
}
