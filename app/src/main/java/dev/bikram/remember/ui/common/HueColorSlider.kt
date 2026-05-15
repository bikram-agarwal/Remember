package dev.bikram.remember.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import dev.bikram.remember.data.normalizeHex
import java.util.Locale
import android.graphics.Color as AndroidColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HueColorSlider(
    selectedHex: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    fallbackHue: Float = 270f,
    sliderPanelColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    onValueChangeFinished: ((String) -> Unit)? = null,
) {
    val fallbackHex = colorHexFromHue(fallbackHue)
    val normalizedHex = normalizeHex(selectedHex.orEmpty()) ?: fallbackHex
    var hue by rememberSaveable(normalizedHex) {
        mutableStateOf(hueFromHexColor(normalizedHex) ?: fallbackHue)
    }
    var currentHex by rememberSaveable(normalizedHex) { mutableStateOf(normalizedHex) }
    val currentColor = colorFromHexOrDefault(currentHex, fallbackHue)
    val sliderColors =
        SliderDefaults.colors(
            thumbColor = currentColor,
            activeTrackColor = Color.Transparent,
            activeTickColor = Color.Transparent,
            inactiveTrackColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        )
    val trackColors =
        SliderDefaults.colors(
            thumbColor = Color.Transparent,
            activeTrackColor = Color.Transparent,
            activeTickColor = Color.Transparent,
            inactiveTrackColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        )

    Slider(
        value = hue,
        onValueChange = { nextHue ->
            hue = nextHue
            currentHex = colorHexFromHue(nextHue)
            onSelect(currentHex)
        },
        modifier = modifier.fillMaxWidth(),
        valueRange = 0f..360f,
        colors = sliderColors,
        onValueChangeFinished = { onValueChangeFinished?.invoke(currentHex) },
        track = { sliderState ->
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(Brush.horizontalGradient(HueSliderColors)),
            ) {
                val handleGapOffset = maxWidth * (hue / 360f) - HueSliderHandleGapWidth / 2
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = handleGapOffset)
                            .size(width = HueSliderHandleGapWidth, height = 28.dp)
                            .background(sliderPanelColor),
                )
                SliderDefaults.Track(
                    sliderState = sliderState,
                    trackCornerSize = 14.dp,
                    colors = trackColors,
                    drawStopIndicator = null,
                    thumbTrackGapSize = HueSliderThumbTrackGap,
                    trackInsideCornerSize = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

internal fun hueFromHexColor(hex: String): Float? {
    val normalized = normalizeHex(hex) ?: return null
    val hsv = FloatArray(3)
    return runCatching {
        AndroidColor.colorToHSV(AndroidColor.parseColor(normalized), hsv)
        hsv[0]
    }.getOrNull()
}

internal fun colorHexFromHue(hue: Float): String {
    val colorInt = AndroidColor.HSVToColor(floatArrayOf(hue.coerceIn(0f, 360f), 0.72f, 0.96f))
    return String.format(Locale.US, "#%06X", 0xFFFFFF and colorInt)
}

internal fun colorFromHexOrDefault(
    hex: String,
    fallbackHue: Float = 270f,
): Color {
    val colorInt =
        runCatching { AndroidColor.parseColor(hex) }
            .getOrDefault(AndroidColor.HSVToColor(floatArrayOf(fallbackHue, 0.72f, 0.96f)))
    return Color(colorInt)
}

private val HueSliderColors =
    listOf(
        Color(0xFFF54545),
        Color(0xFFF5F545),
        Color(0xFF45F545),
        Color(0xFF45F5F5),
        Color(0xFF4545F5),
        Color(0xFFF545F5),
        Color(0xFFF54545),
    )

private val HueSliderThumbTrackGap = 6.dp
private val HueSliderHandleGapWidth = 16.dp
