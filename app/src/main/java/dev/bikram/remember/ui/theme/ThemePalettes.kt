package dev.bikram.remember.ui.theme

import androidx.compose.ui.graphics.Color
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
                    secondary = Color(0xFF059669),
                    tertiary = Color(0xFF65A30D),
                ),
            supportsPaletteStyle = true,
            swatchType = ColorSourceSwatchType.TRIPLET,
        ),
        curatedColorSourceSpec(
            source = ColorSource.CURATED_EMBER,
            primary = Color(0xFFDC2626),
            secondary = Color(0xFF92400E),
            tertiary = Color(0xFF9F1239),
        ),
        curatedColorSourceSpec(
            source = ColorSource.CURATED_GROVE,
            primary = Color(0xFFEA580C),
            secondary = Color(0xFFF59E0B),
            tertiary = Color(0xFFA3A847),
        ),
        curatedColorSourceSpec(
            source = ColorSource.CURATED_HONEY,
            primary = Color(0xFFFBBF24),
            secondary = Color(0xFF854D0E),
            tertiary = Color(0xFFFDE68A),
        ),
        curatedColorSourceSpec(
            source = ColorSource.CURATED_OCEAN,
            primary = Color(0xFF0891B2),
            secondary = Color(0xFF3B82F6),
            tertiary = Color(0xFF4F46E5),
        ),
        curatedColorSourceSpec(
            source = ColorSource.CURATED_IRIS,
            primary = Color(0xFF485CC7),
            secondary = Color(0xFF775A30),
            tertiary = Color(0xFF8C4A63),
        ),
        curatedColorSourceSpec(
            source = ColorSource.CURATED_DUSK,
            primary = Color(0xFF7C3AED),
            secondary = Color(0xFFA855F7),
            tertiary = Color(0xFF6366F1),
        ),
        curatedColorSourceSpec(
            source = ColorSource.CURATED_BERRY,
            primary = Color(0xFFDB2777),
            secondary = Color(0xFF831843),
            tertiary = Color(0xFFA21CAF),
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
            runCatching { Color(android.graphics.Color.parseColor(activeCustomHex)) }
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
