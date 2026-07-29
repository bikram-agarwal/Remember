package dev.bikram.remember.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R

// Two shared FontFamilies backed by app/src/main/res/font/. Both files are
// custom-built subsets of Google Material Symbols Rounded (~100-300 KB each)
// containing ONLY the icons we actually reference. They are pre-instanced at
// wght=500, GRAD=0, opsz=24, and differ only in FILL:
//   material_symbols_rounded.ttf           -> FILL=1 (filled)
//   material_symbols_rounded_outlined.ttf  -> FILL=0 (outlined)
// See font_subset/ for the build pipeline. Picking by FontFamily is the only
// reliable way to get a truly unfilled outline; instancing collapses
// "star_border"-style alt names into the same filled glyph shape.
private val MaterialSymbolsRoundedFilledFontFamily: FontFamily =
    FontFamily(Font(R.font.material_symbols_rounded))

private val MaterialSymbolsRoundedOutlinedFontFamily: FontFamily =
    FontFamily(Font(R.font.material_symbols_rounded_outlined))

private val FlatLineHeightStyle =
    LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    )

/**
 * Renders a [Google Material Symbol](https://fonts.google.com/icons) in the **Rounded** style.
 *
 * Backed by app-bundled subset fonts instead of pulling in a multi-MB icon library.
 * Both subsets are pre-instanced at weight=500 (Medium), GRAD=0, opsz=24, so the
 * [weight] and [grade] parameters are accepted for API compatibility but currently
 * have no visual effect; callers can keep passing them without changes. To switch
 * between filled and outlined presentations, pass [filled].
 *
 * @param filled `true` renders from the filled TTF (FILL=1, default), `false` renders
 *   from the outlined TTF (FILL=0). This is the only way to get a visually distinct
 *   outlined glyph: the `_border` / `_outline` Material name aliases collapse to the
 *   filled shape once FILL is instanced.
 * @param opticalCenterYOffset Downward shift for glyphs that sit high in the em box (Material
 *   Symbols often center on the cap height; dense marks like the star can look optically high).
 */
@Composable
fun RememberMaterialRoundedSymbol(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
    @Suppress("UNUSED_PARAMETER") weight: FontWeight = FontWeight.Medium,
    @Suppress("UNUSED_PARAMETER") grade: Float = 0f,
    filled: Boolean = true,
    autoMirror: Boolean = false,
    opticalCenterYOffset: Dp = 0.dp,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val fontSize = remember(size, density) { with(density) { size.toSp() } }
    val brush = remember(tint) { SolidColor(tint) }
    val fontFamily =
        if (filled) {
            MaterialSymbolsRoundedFilledFontFamily
        } else {
            MaterialSymbolsRoundedOutlinedFontFamily
        }
    val mirroredModifier =
        if (autoMirror && layoutDirection == LayoutDirection.Rtl) {
            modifier.graphicsLayer { scaleX = -1f }
        } else {
            modifier
        }
    val offsetModifier =
        if (opticalCenterYOffset == 0.dp) {
            mirroredModifier
        } else {
            mirroredModifier.offset(y = opticalCenterYOffset)
        }
    val semanticsModifier =
        offsetModifier.clearAndSetSemantics {
            if (contentDescription != null) {
                this.contentDescription = contentDescription
            }
        }

    Box(
        modifier = semanticsModifier,
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = name,
            style =
                TextStyle(
                    brush = brush,
                    fontSize = fontSize,
                    lineHeight = fontSize,
                    fontFamily = fontFamily,
                    // Material Symbols icons are driven by the `rlig` feature (required ligatures).
                    fontFeatureSettings = "\"rlig\" 1, \"liga\" 1",
                    textAlign = TextAlign.Center,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = FlatLineHeightStyle,
                ),
        )
    }
}
