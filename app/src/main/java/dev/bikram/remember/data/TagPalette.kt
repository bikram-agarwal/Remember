package dev.bikram.remember.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlin.math.absoluteValue

/**
 * 12 hues × 5 shades (light → dark). Rendered as a 12-col × 5-row grid.
 * Shades picked from Material 2 palette: 300 / 400 / 500 / 700 / 900.
 */
object TagPalette {
    val grid: List<List<Color>> =
        listOf(
            // Red
            listOf(Color(0xFFE57373), Color(0xFFEF5350), Color(0xFFF44336), Color(0xFFD32F2F), Color(0xFFB71C1C)),
            // Orange
            listOf(Color(0xFFFFB74D), Color(0xFFFFA726), Color(0xFFFF9800), Color(0xFFF57C00), Color(0xFFE65100)),
            // Amber
            listOf(Color(0xFFFFD54F), Color(0xFFFFCA28), Color(0xFFFFC107), Color(0xFFFFA000), Color(0xFFFF6F00)),
            // Yellow
            listOf(Color(0xFFFFF176), Color(0xFFFFEE58), Color(0xFFFFEB3B), Color(0xFFFBC02D), Color(0xFFF57F17)),
            // Lime
            listOf(Color(0xFFDCE775), Color(0xFFD4E157), Color(0xFFCDDC39), Color(0xFFAFB42B), Color(0xFF827717)),
            // Green
            listOf(Color(0xFF81C784), Color(0xFF66BB6A), Color(0xFF4CAF50), Color(0xFF388E3C), Color(0xFF1B5E20)),
            // Teal
            listOf(Color(0xFF4DB6AC), Color(0xFF26A69A), Color(0xFF009688), Color(0xFF00796B), Color(0xFF004D40)),
            // Cyan
            listOf(Color(0xFF4DD0E1), Color(0xFF26C6DA), Color(0xFF00BCD4), Color(0xFF0097A7), Color(0xFF006064)),
            // Blue
            listOf(Color(0xFF64B5F6), Color(0xFF42A5F5), Color(0xFF2196F3), Color(0xFF1976D2), Color(0xFF0D47A1)),
            // Indigo
            listOf(Color(0xFF7986CB), Color(0xFF5C6BC0), Color(0xFF3F51B5), Color(0xFF303F9F), Color(0xFF1A237E)),
            // Purple
            listOf(Color(0xFFBA68C8), Color(0xFFAB47BC), Color(0xFF9C27B0), Color(0xFF7B1FA2), Color(0xFF4A148C)),
            // Pink
            listOf(Color(0xFFF06292), Color(0xFFEC407A), Color(0xFFE91E63), Color(0xFFC2185B), Color(0xFF880E4F)),
        )

    /** Flat list of all 60 swatches, used for default-color lookup by name hash. */
    val presets: List<Color> = grid.flatten()

    /** Stable default color for a tag without an explicit mapping — derived from name hash. */
    fun defaultFor(tag: String): Color {
        if (tag.isBlank()) return presets[0]
        val idx = (tag.hashCode().absoluteValue) % presets.size
        return presets[idx]
    }

    /** White or black text on top of [background] based on luminance. */
    fun textOn(background: Color): Color {
        val lum = ColorUtils.calculateLuminance(background.toArgb())
        return if (lum < 0.55) Color.White else Color(0xFF1C1B1F)
    }
}
