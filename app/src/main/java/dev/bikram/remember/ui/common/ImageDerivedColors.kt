package dev.bikram.remember.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.get
import androidx.core.net.toUri
import com.materialkolor.rememberDynamicColorScheme
import dev.bikram.remember.data.ThemeMode
import dev.bikram.remember.ui.theme.AppShapes
import dev.bikram.remember.ui.theme.AppTypography
import dev.bikram.remember.ui.theme.LocalIsDark
import dev.bikram.remember.ui.theme.LocalThemeState
import dev.bikram.remember.ui.theme.boostContainersForSeedThemes
import dev.bikram.remember.ui.theme.boostOutlineForVisibility
import dev.bikram.remember.ui.theme.tintSurfacesTowardPrimary
import dev.bikram.remember.ui.theme.toLib
import dev.bikram.remember.ui.theme.toOled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import android.graphics.Color as AndroidColor

data class ImageDerivedColors(
    val seedColor: Color,
    val onImageColor: Color,
    val imageScrimColor: Color,
)

@Composable
fun rememberImageDerivedColors(
    imageUri: String?,
    cacheRevision: Long = 0L,
): ImageDerivedColors? {
    val context = LocalContext.current
    val seedColor by produceState<Color?>(initialValue = null, imageUri, cacheRevision, context) {
        value =
            imageUri?.let { uri ->
                ImageColorCache.colorFor(context.applicationContext, uri, cacheRevision)
            }
    }
    return remember(seedColor) {
        seedColor?.toImageDerivedColors()
    }
}

/**
 * Mirrors [dev.bikram.remember.ui.theme.RememberColorResolution]: the full tinted [colorScheme]
 * is used by [MaterialExpressiveTheme] for UI components, while [backgroundScheme] (pre-tinting,
 * same as [dev.bikram.remember.ui.theme.RememberColorResolution.backgroundScheme]) is used for
 * the page gradient so that surface containers retain contrast against the background.
 */
data class ImageDerivedColorResolution(
    val colorScheme: ColorScheme,
    val backgroundScheme: ColorScheme,
)

@Composable
fun rememberImageDerivedColorScheme(imageColors: ImageDerivedColors?): ImageDerivedColorResolution? {
    val seed = imageColors?.seedColor ?: return null
    val darkTheme = LocalIsDark.current
    val themeState = LocalThemeState.current
    val black = themeState.themeMode == ThemeMode.BLACK
    val generated =
        rememberDynamicColorScheme(
            seedColor = seed,
            isDark = darkTheme,
            style = themeState.paletteStyle.toLib(),
            isAmoled = black,
        )
    return remember(generated, darkTheme, black, themeState.shadingIntensity) {
        val base = if (black) generated.toOled() else generated
        val shaded =
            if (!black) {
                base.tintSurfacesTowardPrimary(darkTheme, themeState.shadingIntensity)
            } else {
                base
            }
        val themed =
            shaded
                .boostOutlineForVisibility(darkTheme)
                .boostContainersForSeedThemes(darkTheme)
        ImageDerivedColorResolution(
            colorScheme = themed,
            backgroundScheme = base,
        )
    }
}

/**
 * Applies the image-derived theme as a [MaterialExpressiveTheme] override only when [resolution]
 * is non-null. Pass-through when null so imageless notes use the parent theme unchanged.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoteAdaptiveTheme(
    resolution: ImageDerivedColorResolution?,
    content: @Composable () -> Unit,
) {
    if (resolution != null) {
        MaterialExpressiveTheme(
            colorScheme = resolution.colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    } else {
        content()
    }
}

private object ImageColorCache {
    private val cache = LruCache<String, Int>(64)

    suspend fun colorFor(
        context: Context,
        imageUri: String,
        cacheRevision: Long,
    ): Color? {
        val key = "$imageUri#$cacheRevision"
        cache.get(key)?.let { return Color(it) }
        val colorInt = extractAverageColor(context, imageUri) ?: return null
        cache.put(key, colorInt)
        return Color(colorInt)
    }
}

private suspend fun extractAverageColor(
    context: Context,
    imageUri: String,
): Int? =
    withContext(Dispatchers.IO) {
        val uri = imageUri.toUri()
        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val decodeOptions =
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetSide = 96)
            }
        val bitmap =
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return@withContext null

        try {
            bitmap.averageColor()
        } finally {
            bitmap.recycle()
        }
    }

private fun sampleSizeFor(
    width: Int,
    height: Int,
    targetSide: Int,
): Int {
    var sample = 1
    val largest = max(width, height)
    while (largest / (sample * 2) >= targetSide) {
        sample *= 2
    }
    return sample
}

private fun Bitmap.averageColor(): Int? {
    if (width <= 0 || height <= 0) return null

    val stride = max(1, max(width, height) / 72)
    var selectedRed = 0.0
    var selectedGreen = 0.0
    var selectedBlue = 0.0
    var selectedCount = 0
    var fallbackRed = 0.0
    var fallbackGreen = 0.0
    var fallbackBlue = 0.0
    var fallbackCount = 0
    val hsv = FloatArray(3)
    var y = stride / 2
    while (y < height) {
        var x = stride / 2
        while (x < width) {
            val pixel = this[x, y]
            if (AndroidColor.alpha(pixel) >= 160) {
                val r = AndroidColor.red(pixel).toDouble()
                val g = AndroidColor.green(pixel).toDouble()
                val b = AndroidColor.blue(pixel).toDouble()
                fallbackRed += r * r
                fallbackGreen += g * g
                fallbackBlue += b * b
                fallbackCount++
                AndroidColor.colorToHSV(pixel, hsv)
                if (hsv[1] >= 0.08f && hsv[2] >= 0.16f && hsv[2] <= 0.94f) {
                    selectedRed += r * r
                    selectedGreen += g * g
                    selectedBlue += b * b
                    selectedCount++
                }
            }
            x += stride
        }
        y += stride
    }
    val count = if (selectedCount > 0) selectedCount else fallbackCount
    if (count == 0) return null
    val red = if (selectedCount > 0) selectedRed else fallbackRed
    val green = if (selectedCount > 0) selectedGreen else fallbackGreen
    val blue = if (selectedCount > 0) selectedBlue else fallbackBlue
    return AndroidColor.rgb(
        sqrt(red / count).roundToInt().coerceIn(0, 255),
        sqrt(green / count).roundToInt().coerceIn(0, 255),
        sqrt(blue / count).roundToInt().coerceIn(0, 255),
    )
}

private fun Color.toImageDerivedColors(): ImageDerivedColors {
    val onImage = readableContentFor(this)
    return ImageDerivedColors(
        seedColor = this,
        onImageColor = onImage,
        imageScrimColor = if (onImage.luminance() > 0.5f) Color.Black else Color.White,
    )
}

private fun readableContentFor(background: Color): Color =
    if (background.luminance() > 0.48f) {
        Color(0xFF1C1B1F)
    } else {
        Color(0xFFF3EFF7)
    }
