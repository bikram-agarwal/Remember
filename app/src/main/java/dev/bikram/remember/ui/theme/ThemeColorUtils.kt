package dev.bikram.remember.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.min
import android.graphics.Color as AndroidColor

enum class PaletteDifferentiationStatus { GOOD, LOW }

private const val LADDER_THRESHOLD_LIGHT = 0.045f
private const val LADDER_THRESHOLD_DARK = 0.025f
private const val ACCENT_MIN_PAIR_DISTANCE = 0.04f

/**
 * Quick heuristic that decides whether the active scheme has enough variation between its
 * surface tones AND its accent containers to feel rich rather than monochromatic. Returns
 * GOOD only when both axes pass:
 *  - Surface ladder: lowest → highest luminance spread is at least 0.045 (light mode) /
 *    0.025 (dark mode). Below that, the user can't tell surfaceContainerLow apart from
 *    surfaceContainerHighest at a glance.
 *  - Accent containers: every pairwise distance among primary/secondary/tertiary is at
 *    least 0.04 in [colorPairDistance] terms. See [accentSeparationOk] for rationale.
 */
fun paletteDifferentiationStatus(scheme: ColorScheme): PaletteDifferentiationStatus {
    val isDark = scheme.surface.luminance() < 0.5f
    val ladderSpread =
        abs(scheme.surfaceContainerHighest.luminance() - scheme.surfaceContainerLowest.luminance())
    val ladderThreshold = if (isDark) LADDER_THRESHOLD_DARK else LADDER_THRESHOLD_LIGHT
    val ladderOk = ladderSpread >= ladderThreshold

    val accentOk =
        accentSeparationOk(scheme.primary, scheme.secondary, scheme.tertiary)

    return if (ladderOk && accentOk) {
        PaletteDifferentiationStatus.GOOD
    } else {
        PaletteDifferentiationStatus.LOW
    }
}

/**
 * A palette is "well differentiated" when no two accents are practically identical -
 * specifically, every pairwise distance must be at least [ACCENT_MIN_PAIR_DISTANCE]. That
 * accepts ANALOGOUS palettes (where all three accents share a hue family but still differ
 * enough in tone or saturation to be told apart) AND split-complementary palettes alike,
 * while still rejecting genuinely flat schemes where materialkolor's pipeline collapses
 * secondary or tertiary onto primary's tone.
 *
 * Earlier versions of this check also required `maxPair >= 0.40` (i.e. at least one pair
 * had to have strong contrast) but that incorrectly rejected curated analogous palettes
 * where every pair stays within ~50deg of every other. Harmony is a valid design choice;
 * it shouldn't trigger a "Try Vibrant or Expressive" nudge.
 */
private fun accentSeparationOk(
    primary: Color,
    secondary: Color,
    tertiary: Color,
): Boolean {
    val pPS = colorPairDistance(primary, secondary)
    val pPT = colorPairDistance(primary, tertiary)
    val pST = colorPairDistance(secondary, tertiary)
    val minPair = min(min(pPS, pPT), pST)
    return minPair >= ACCENT_MIN_PAIR_DISTANCE
}

/**
 * A loose perceptual distance between two colours. Normalises ARGB into HSV, takes the
 * minimum of (a) hue distance scaled to 0..1 and (b) value+saturation difference scaled
 * to 0..1, and returns the larger. The intent is "they look different by EITHER hue OR
 * tone+chroma" - so a near-grayscale theme can still read as differentiated if its
 * accents diverge in lightness, while a saturated theme passes via hue alone.
 */
private fun colorPairDistance(
    a: Color,
    b: Color,
): Float {
    val hsvA = FloatArray(3)
    val hsvB = FloatArray(3)
    AndroidColor.colorToHSV(a.toArgb(), hsvA)
    AndroidColor.colorToHSV(b.toArgb(), hsvB)
    val hueDiff = abs(hsvA[0] - hsvB[0]).let { d -> if (d > 180f) 360f - d else d } / 180f
    val toneDiff = abs(hsvA[2] - hsvB[2]) + abs(hsvA[1] - hsvB[1]) * 0.6f
    return maxOf(hueDiff, toneDiff.coerceIn(0f, 1f))
}

/**
 * Pick black or white text for a small label sitting on top of [bg]. Matches WCAG-style
 * decisions without spinning up a full contrast solver - sufficient for tiny labels.
 */
fun contrastingTextColor(bg: Color): Color =
    if (bg.luminance() > 0.45f) {
        Color.Black.copy(alpha = 0.78f)
    } else {
        Color.White.copy(alpha = 0.86f)
    }
