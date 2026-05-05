package dev.bikram.remember.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import com.materialkolor.PaletteStyle
import dev.bikram.remember.data.ColorSource
import dev.bikram.remember.data.PaletteStyleOpt

/** Primary seed for DEFAULT and fallback seed for invalid custom / unavailable Material You. */
internal val DefaultSeed = Color(0xFF16A34A)

/**
 * Hand-tuned primary/secondary/tertiary triplets. The primary seed drives surfaces,
 * outlines, and error roles; secondary/tertiary slots are then overridden from their
 * own separately seeded schemes.
 */
data class CuratedPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

enum class ColorSourceSwatchType {
    MATERIAL_YOU,
    SOLID,
    TRIPLET,
}

enum class ColorSourceFallback {
    NONE,
    DEFAULT_SEED_WHEN_UNAVAILABLE,
    DEFAULT_SEED_WHEN_INVALID,
}

data class ColorSourceSpec(
    val source: ColorSource,
    val representativeColor: Color,
    val triplet: CuratedPalette? = null,
    val supportsPaletteStyle: Boolean,
    val swatchType: ColorSourceSwatchType,
    val fallbackBehavior: ColorSourceFallback = ColorSourceFallback.NONE,
)

val colorSourceSpecsInPickerOrder: List<ColorSourceSpec> =
    listOf(
        ColorSourceSpec(
            source = ColorSource.MATERIAL_YOU,
            representativeColor = Color(0xFF9B9DA7),
            supportsPaletteStyle = false,
            swatchType = ColorSourceSwatchType.MATERIAL_YOU,
            fallbackBehavior = ColorSourceFallback.DEFAULT_SEED_WHEN_UNAVAILABLE,
        ),
        ColorSourceSpec(
            source = ColorSource.DEFAULT,
            representativeColor = DefaultSeed,
            triplet =
                CuratedPalette(
                    primary = DefaultSeed,
                    secondary = Color(0xFF0F766E),
                    tertiary = Color(0xFF84CC16),
                ),
            supportsPaletteStyle = true,
            swatchType = ColorSourceSwatchType.TRIPLET,
        ),
        curatedColorSourceSpec(
            source = ColorSource.CURATED_EMBER,
            primary = Color(0xFFF97316),
            secondary = Color(0xFFDC2626),
            tertiary = Color(0xFFF59E0B),
        ),
        curatedColorSourceSpec(
            source = ColorSource.CURATED_GROVE,
            primary = Color(0xFF6B8E23),
            secondary = Color(0xFF0F766E),
            tertiary = Color(0xFFA16207),
        ),
        curatedColorSourceSpec(
            source = ColorSource.CURATED_HONEY,
            primary = Color(0xFFFACC15),
            secondary = Color(0xFFD97706),
            tertiary = Color(0xFF7C2D12),
        ),
        curatedColorSourceSpec(
            source = ColorSource.CURATED_OCEAN,
            primary = Color(0xFF0284C7),
            secondary = Color(0xFF0D9488),
            tertiary = Color(0xFF2563EB),
        ),
        curatedColorSourceSpec(
            source = ColorSource.CURATED_IRIS,
            primary = Color(0xFF7C3AED),
            secondary = Color(0xFF4F46E5),
            tertiary = Color(0xFFC084FC),
        ),
        curatedColorSourceSpec(
            source = ColorSource.CURATED_DUSK,
            primary = Color(0xFF6B7280),
            secondary = Color(0xFFA78BFA),
            tertiary = Color(0xFFF97316),
        ),
        curatedColorSourceSpec(
            source = ColorSource.CURATED_BERRY,
            primary = Color(0xFFD946EF),
            secondary = Color(0xFFBE185D),
            tertiary = Color(0xFF7C3AED),
        ),
    )

private val customColorSourceSpec =
    ColorSourceSpec(
        source = ColorSource.CUSTOM,
        representativeColor = DefaultSeed,
        supportsPaletteStyle = true,
        swatchType = ColorSourceSwatchType.SOLID,
        fallbackBehavior = ColorSourceFallback.DEFAULT_SEED_WHEN_INVALID,
    )

private val colorSourceSpecsBySource =
    (colorSourceSpecsInPickerOrder + customColorSourceSpec).associateBy { spec -> spec.source }

private fun curatedColorSourceSpec(
    source: ColorSource,
    primary: Color,
    secondary: Color,
    tertiary: Color,
): ColorSourceSpec =
    ColorSourceSpec(
        source = source,
        representativeColor = primary,
        triplet =
            CuratedPalette(
                primary = primary,
                secondary = secondary,
                tertiary = tertiary,
            ),
        supportsPaletteStyle = true,
        swatchType = ColorSourceSwatchType.TRIPLET,
    )

fun colorSourceSpecFor(source: ColorSource): ColorSourceSpec =
    colorSourceSpecsBySource[source]
        ?: ColorSourceSpec(
            source = source,
            representativeColor = DefaultSeed,
            supportsPaletteStyle = true,
            swatchType = ColorSourceSwatchType.SOLID,
            fallbackBehavior = ColorSourceFallback.DEFAULT_SEED_WHEN_INVALID,
        )

internal fun colorSourceSeedFor(
    spec: ColorSourceSpec,
    activeCustomHex: String,
): Color =
    when (spec.source) {
        ColorSource.CUSTOM ->
            runCatching { Color(activeCustomHex.toColorInt()) }
                .getOrElse { spec.representativeColor }
        else -> spec.representativeColor
    }

internal fun PaletteStyleOpt.toLib(): PaletteStyle =
    when (this) {
        PaletteStyleOpt.TONAL_SPOT -> PaletteStyle.TonalSpot
        PaletteStyleOpt.NEUTRAL -> PaletteStyle.Neutral
        PaletteStyleOpt.VIBRANT -> PaletteStyle.Vibrant
        PaletteStyleOpt.EXPRESSIVE -> PaletteStyle.Expressive
        PaletteStyleOpt.RAINBOW -> PaletteStyle.Rainbow
        PaletteStyleOpt.FRUIT_SALAD -> PaletteStyle.FruitSalad
        PaletteStyleOpt.MONOCHROME -> PaletteStyle.Monochrome
        PaletteStyleOpt.FIDELITY -> PaletteStyle.Fidelity
        PaletteStyleOpt.CONTENT -> PaletteStyle.Content
    }
