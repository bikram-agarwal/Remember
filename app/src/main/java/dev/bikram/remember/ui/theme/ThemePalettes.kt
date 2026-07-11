package dev.bikram.remember.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import dev.bikram.remember.data.ColorSource
import dev.bikram.remember.data.PaletteStyleOpt

/** Primary seed for DEFAULT and fallback seed for invalid custom / unavailable Material You. */
internal val DefaultSeed = Color(0xFF16A34A)

internal val RememberColorSpecVersion = ColorSpec.SpecVersion.SPEC_2025

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
            triplet = generateTripletForSeed(DefaultSeed),
            supportsPaletteStyle = true,
            swatchType = ColorSourceSwatchType.TRIPLET,
        ),
        curatedColorSourceSpec(
            source = ColorSource.CURATED_EMBER,
            primary = Color(0xFFF97316),
        ),
        curatedColorSourceSpec(
            source = ColorSource.CURATED_GROVE,
            primary = Color(0xFF6B8E23),
        ),
        curatedColorSourceSpec(
            source = ColorSource.CURATED_HONEY,
            primary = Color(0xFFFACC15),
        ),
        curatedColorSourceSpec(
            source = ColorSource.CURATED_OCEAN,
            primary = Color(0xFF0284C7),
        ),
        curatedColorSourceSpec(
            source = ColorSource.CURATED_IRIS,
            primary = Color(0xFF7C3AED),
        ),
        curatedColorSourceSpec(
            source = ColorSource.CURATED_DUSK,
            primary = Color(0xFF6B7280),
        ),
        curatedColorSourceSpec(
            source = ColorSource.CURATED_BERRY,
            primary = Color(0xFFD946EF),
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
): ColorSourceSpec =
    ColorSourceSpec(
        source = source,
        representativeColor = primary,
        triplet = generateTripletForSeed(primary),
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
        ColorSource.CUSTOM -> {
            val primaryHex = activeCustomHex.split("|").first()
            runCatching { Color(primaryHex.toColorInt()) }
                .getOrElse { spec.representativeColor }
        }
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

internal fun colorToHsl(
    color: Color,
    hslArray: FloatArray,
) {
    val redVal = color.red
    val greenVal = color.green
    val blueVal = color.blue

    val maxVal = maxOf(redVal, maxOf(greenVal, blueVal))
    val minVal = minOf(redVal, minOf(greenVal, blueVal))
    val delta = maxVal - minVal

    var hueVal = 0f
    var saturationVal = 0f
    val lightnessVal = (maxVal + minVal) / 2f

    if (delta != 0f) {
        saturationVal =
            if (lightnessVal < 0.5f) {
                delta / (maxVal + minVal)
            } else {
                delta / (2f - maxVal - minVal)
            }
        hueVal =
            when (maxVal) {
                redVal -> (greenVal - blueVal) / delta + (if (greenVal < blueVal) 6f else 0f)
                greenVal -> (blueVal - redVal) / delta + 2f
                else -> (redVal - greenVal) / delta + 4f
            }
        hueVal *= 60f
    }

    hslArray[0] = hueVal
    hslArray[1] = saturationVal
    hslArray[2] = lightnessVal
}

internal fun hslToColor(
    hueVal: Float,
    saturationVal: Float,
    lightnessVal: Float,
): Color {
    val chroma = (1f - kotlin.math.abs(2f * lightnessVal - 1f)) * saturationVal
    val xValue = chroma * (1f - kotlin.math.abs((hueVal / 60f) % 2f - 1f))
    val matchValue = lightnessVal - chroma / 2f

    val (redValue, greenValue, blueValue) =
        when {
            hueVal < 60f -> Triple(chroma, xValue, 0f)
            hueVal < 120f -> Triple(xValue, chroma, 0f)
            hueVal < 180f -> Triple(0f, chroma, xValue)
            hueVal < 240f -> Triple(0f, xValue, chroma)
            hueVal < 300f -> Triple(xValue, 0f, chroma)
            else -> Triple(chroma, 0f, xValue)
        }

    return Color(
        red = redValue + matchValue,
        green = greenValue + matchValue,
        blue = blueValue + matchValue,
        alpha = 1f,
    )
}

internal fun generateTripletForSeed(primaryColor: Color): CuratedPalette {
    val hslArray = FloatArray(3)
    colorToHsl(primaryColor, hslArray)
    val hueVal = hslArray[0]
    val saturationVal = hslArray[1]
    val lightnessVal = hslArray[2]

    // Secondary: shift hue +20, soft but clear saturation (e.g. 70% of primary, min 35%), slightly adjusted lightness
    val secHue = (hueVal + 20f) % 360f
    val secSaturation = (saturationVal * 0.70f).coerceIn(0.35f, 0.85f)
    val finalSecSaturation = if (saturationVal < 0.30f) saturationVal else secSaturation
    val secLightness =
        if (lightnessVal < 0.5f) {
            (lightnessVal + 0.04f).coerceIn(0f, 1f)
        } else {
            (lightnessVal - 0.04f).coerceIn(0f, 1f)
        }
    val secondaryColor = hslToColor(secHue, finalSecSaturation, secLightness)

    // Tertiary: shift hue -40, vibrant saturation (e.g. 85% of primary, min 40%), slightly adjusted lightness
    val terHue = (hueVal - 40f + 360f) % 360f
    val terSaturation = (saturationVal * 0.85f).coerceIn(0.40f, 0.90f)
    val finalTerSaturation = if (saturationVal < 0.35f) saturationVal else terSaturation
    val terLightness =
        if (lightnessVal < 0.5f) {
            (lightnessVal + 0.08f).coerceIn(0f, 1f)
        } else {
            (lightnessVal - 0.08f).coerceIn(0f, 1f)
        }
    val tertiaryColor = hslToColor(terHue, finalTerSaturation, terLightness)

    return CuratedPalette(primaryColor, secondaryColor, tertiaryColor)
}
