package dev.bikram.remember.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

val AppTypography = Typography()

private val MaterialDefaultTypography = Typography()

/** Material default type scale with a user-supplied font (ObtainX parity). */
fun customFontTypography(fontFamily: FontFamily): Typography =
    MaterialDefaultTypography.withFontFamilyOnly(fontFamily)

/**
 * ObtainX parity: swap only [fontFamily] on each role, keep sizes/line heights from [this].
 * Call on [Typography] defaults, not a custom explicit scale.
 */
fun Typography.withFontFamilyOnly(fontFamily: FontFamily): Typography =
    copy(
        displayLarge = displayLarge.withFontFamilyOnly(fontFamily),
        displayMedium = displayMedium.withFontFamilyOnly(fontFamily),
        displaySmall = displaySmall.withFontFamilyOnly(fontFamily),
        headlineLarge = headlineLarge.withFontFamilyOnly(fontFamily),
        headlineMedium = headlineMedium.withFontFamilyOnly(fontFamily),
        headlineSmall = headlineSmall.withFontFamilyOnly(fontFamily),
        titleLarge = titleLarge.withFontFamilyOnly(fontFamily),
        titleMedium = titleMedium.withFontFamilyOnly(fontFamily),
        titleSmall = titleSmall.withFontFamilyOnly(fontFamily),
        bodyLarge = bodyLarge.withFontFamilyOnly(fontFamily),
        bodyMedium = bodyMedium.withFontFamilyOnly(fontFamily),
        bodySmall = bodySmall.withFontFamilyOnly(fontFamily),
        labelLarge = labelLarge.withFontFamilyOnly(fontFamily),
        labelMedium = labelMedium.withFontFamilyOnly(fontFamily),
        labelSmall = labelSmall.withFontFamilyOnly(fontFamily),
        displayLargeEmphasized = displayLargeEmphasized.withFontFamilyOnly(fontFamily),
        displayMediumEmphasized = displayMediumEmphasized.withFontFamilyOnly(fontFamily),
        displaySmallEmphasized = displaySmallEmphasized.withFontFamilyOnly(fontFamily),
        headlineLargeEmphasized = headlineLargeEmphasized.withFontFamilyOnly(fontFamily),
        headlineMediumEmphasized = headlineMediumEmphasized.withFontFamilyOnly(fontFamily),
        headlineSmallEmphasized = headlineSmallEmphasized.withFontFamilyOnly(fontFamily),
        titleLargeEmphasized = titleLargeEmphasized.withFontFamilyOnly(fontFamily),
        titleMediumEmphasized = titleMediumEmphasized.withFontFamilyOnly(fontFamily),
        titleSmallEmphasized = titleSmallEmphasized.withFontFamilyOnly(fontFamily),
        bodyLargeEmphasized = bodyLargeEmphasized.withFontFamilyOnly(fontFamily),
        bodyMediumEmphasized = bodyMediumEmphasized.withFontFamilyOnly(fontFamily),
        bodySmallEmphasized = bodySmallEmphasized.withFontFamilyOnly(fontFamily),
        labelLargeEmphasized = labelLargeEmphasized.withFontFamilyOnly(fontFamily),
        labelMediumEmphasized = labelMediumEmphasized.withFontFamilyOnly(fontFamily),
        labelSmallEmphasized = labelSmallEmphasized.withFontFamilyOnly(fontFamily),
    )

private fun TextStyle.withFontFamilyOnly(fontFamily: FontFamily): TextStyle =
    copy(fontFamily = fontFamily)
