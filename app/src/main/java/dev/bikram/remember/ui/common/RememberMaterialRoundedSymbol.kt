package dev.bikram.remember.ui.common

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R

// Single shared FontFamily backed by app/src/main/res/font/material_symbols_rounded.ttf.
// That file is a custom-built subset of Google Material Symbols Rounded (~140 KB)
// containing ONLY the icons we actually reference, instanced at FILL=1, wght=500,
// GRAD=0, opsz=24. See font_subset/ for the build pipeline.
private val MaterialSymbolsRoundedFontFamily: FontFamily =
    FontFamily(Font(R.font.material_symbols_rounded))

private val FlatLineHeightStyle =
    LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    )

/**
 * Renders a [Google Material Symbol](https://fonts.google.com/icons) in the **Rounded** style
 * with filled presentation, matching the app's primary icon language.
 *
 * Backed by an app-bundled subset font instead of pulling in a multi-MB icon library.
 * The font is pre-instanced at FILL=1, weight=500 (Medium), GRAD=0, opsz=24, so the
 * [weight] and [grade] parameters are accepted for API compatibility but currently
 * have no visual effect; callers can keep passing them without changes.
 *
 * @param opticalCenterYOffset Downward shift for glyphs that sit high in the em box (Material
 *   Symbols often center on the cap height; dense marks like the heart can look optically high).
 */
@Composable
fun RememberMaterialRoundedSymbol(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    @Suppress("UNUSED_PARAMETER") weight: FontWeight = FontWeight.Medium,
    @Suppress("UNUSED_PARAMETER") grade: Float = 0f,
    opticalCenterYOffset: Dp = 0.dp,
) {
    val density = LocalDensity.current
    val fontSize = remember(size, density) { with(density) { size.toSp() } }
    val brush = remember(tint) { SolidColor(tint) }
    val layoutModifier =
        if (opticalCenterYOffset == 0.dp) {
            modifier
        } else {
            modifier.offset(y = opticalCenterYOffset)
        }
    BasicText(
        text = name,
        modifier = layoutModifier,
        style = TextStyle(
            brush = brush,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontFamily = MaterialSymbolsRoundedFontFamily,
            // Material Symbols icons are driven by the `rlig` feature (required ligatures).
            fontFeatureSettings = "\"rlig\" 1, \"liga\" 1",
            textAlign = TextAlign.Center,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = FlatLineHeightStyle,
        ),
    )
}
