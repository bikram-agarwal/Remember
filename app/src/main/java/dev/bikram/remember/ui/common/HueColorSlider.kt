package dev.bikram.remember.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.luminance
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
    val thumbColor = colorFromHue(hue, saturation = HueSliderThumbSaturation, value = HueSliderThumbValue)
    val lightPanel = sliderPanelColor.luminance() > 0.5f
    val handleGapWidth = if (lightPanel) HueSliderLightHandleGapWidth else HueSliderHandleGapWidth
    val handleWidth = if (lightPanel) HueSliderLightHandleWidth else HueSliderHandleWidth
    val sliderColors =
        SliderDefaults.colors(
            thumbColor = Color.Transparent,
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
                        .height(HueSliderHandleHeight),
            ) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth()
                            .height(HueSliderTrackHeight)
                            .clip(HueSliderTrackShape)
                            .background(Brush.horizontalGradient(HueSliderColors)),
                )
                val handleGapOffset = maxWidth * (hue / 360f) - handleGapWidth / 2
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = handleGapOffset)
                            .size(width = handleGapWidth, height = HueSliderTrackHeight)
                            .background(sliderPanelColor),
                )
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = maxWidth * (hue / 360f) - handleWidth / 2)
                            .size(width = handleWidth, height = HueSliderHandleHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(thumbColor),
                )
                SliderDefaults.Track(
                    sliderState = sliderState,
                    trackCornerSize = HueSliderTrackCorner,
                    colors = trackColors,
                    drawStopIndicator = null,
                    thumbTrackGapSize = HueSliderThumbTrackGap,
                    trackInsideCornerSize = HueSliderInsideCorner,
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
    val colorInt = androidColorFromHue(hue, HueSliderGeneratedSaturation, HueSliderGeneratedValue)
    return String.format(Locale.US, "#%06X", 0xFFFFFF and colorInt)
}

private fun colorFromHue(
    hue: Float,
    saturation: Float,
    value: Float,
): Color = Color(androidColorFromHue(hue, saturation, value))

private fun androidColorFromHue(
    hue: Float,
    saturation: Float,
    value: Float,
): Int =
    AndroidColor.HSVToColor(
        floatArrayOf(
            hue.coerceIn(0f, 360f),
            saturation,
            value,
        ),
    )

internal fun colorFromHexOrDefault(
    hex: String,
    fallbackHue: Float = 270f,
): Color {
    val colorInt =
        runCatching { AndroidColor.parseColor(hex) }
            .getOrDefault(
                androidColorFromHue(fallbackHue, HueSliderGeneratedSaturation, HueSliderGeneratedValue),
            )
    return Color(colorInt)
}

private val HueSliderColors =
    listOf(
        Color(0xFFE95A50),
        Color(0xFFE8B84E),
        Color(0xFFD5DB4C),
        Color(0xFF58D95C),
        Color(0xFF43CDD0),
        Color(0xFF5569E8),
        Color(0xFFD64BDD),
        Color(0xFFE95A50),
    )

private const val HueSliderGeneratedSaturation = 0.66f
private const val HueSliderGeneratedValue = 0.90f
private const val HueSliderThumbSaturation = 0.58f
private const val HueSliderThumbValue = 0.86f
private val HueSliderTrackHeight = 28.dp
private val HueSliderTrackCorner = 10.dp
private val HueSliderTrackShape = RoundedCornerShape(HueSliderTrackCorner)
private val HueSliderInsideCorner = 3.dp
private val HueSliderThumbTrackGap = 4.dp
private val HueSliderHandleGapWidth = 14.dp
private val HueSliderHandleWidth = 5.dp
private val HueSliderLightHandleGapWidth = 13.dp
private val HueSliderLightHandleWidth = 6.dp
private val HueSliderHandleHeight = 42.dp
