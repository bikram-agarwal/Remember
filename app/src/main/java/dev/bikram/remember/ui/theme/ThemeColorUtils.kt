package dev.bikram.remember.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

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
