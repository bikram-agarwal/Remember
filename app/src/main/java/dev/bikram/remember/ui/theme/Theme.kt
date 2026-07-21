@file:Suppress("DEPRECATION")

package dev.bikram.remember.ui.theme

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import dev.bikram.remember.data.ThemeMode
import dev.bikram.remember.data.ThemeState
import dev.bikram.remember.ui.common.responsiveTextScaleForWidth

private const val MAX_APP_DISPLAY_SCALE = 1.15f

// Modest cap so text on extreme OS font settings stays large enough to honor the user's
// choice, but not so large that sheets/lists balloon far past the (necessarily shrunk)
// date/time pickers. Kept in parity with FilePipe.
private const val MAX_APP_FONT_SCALE = 1.10f

val LocalIsDark = staticCompositionLocalOf { false }

/**
 * Pass [paintBackground] = false when the host activity is translucent (e.g. the snooze
 * dialog floating over the home screen). The full-screen [GradientBackground] otherwise
 * paints [scheme.background] across the whole window and hides the live wallpaper /
 * caller activity behind it, defeating the translucent theme.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RememberTheme(
    themeState: ThemeState = ThemeState(),
    paintBackground: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme =
        when (themeState.themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.BLACK -> true
        }
    val black = themeState.themeMode == ThemeMode.BLACK
    val effectiveUseGradient = themeState.useGradient && !black

    val context = LocalContext.current
    val reducedMotion = rememberSystemReducedMotionEnabled(context)
    val customFontFamily =
        remember(themeState.customFontPath) {
            CustomFontStorage.loadFontFamily(themeState.customFontPath)
        }
    val typography =
        remember(customFontFamily) {
            customFontFamily?.let { customFontTypography(it) } ?: AppTypography
        }
    val baseDensity = LocalDensity.current
    val stableDensity = DisplayMetrics.DENSITY_DEVICE_STABLE.toFloat() / DisplayMetrics.DENSITY_DEFAULT
    val responsiveTextScale =
        with(baseDensity) {
            responsiveTextScaleForWidth(
                LocalWindowInfo.current.containerSize.width
                    .toDp(),
            )
        }
    val responsiveDensity =
        remember(baseDensity.density, baseDensity.fontScale, responsiveTextScale, stableDensity) {
            Density(
                density = baseDensity.density.coerceAtMost(stableDensity * MAX_APP_DISPLAY_SCALE),
                fontScale = (baseDensity.fontScale * responsiveTextScale).coerceAtMost(MAX_APP_FONT_SCALE),
            )
        }
    val wallpaperTint = rememberWallpaperTintColor(context, enabled = effectiveUseGradient)
    val colorResolution =
        rememberResolvedColorScheme(
            context = context,
            themeState = themeState,
            darkTheme = darkTheme,
            black = black,
        )

    val view = LocalView.current
    LaunchedEffect(view, darkTheme) {
        if (view.isInEditMode) return@LaunchedEffect
        val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val targetColorScheme = colorResolution.colorScheme
    val targetBackgroundScheme = colorResolution.backgroundScheme

    CompositionLocalProvider(
        LocalDensity provides responsiveDensity,
        LocalIsDark provides darkTheme,
        LocalUseGradient provides effectiveUseGradient,
        LocalHeroOnCards provides themeState.heroOnCards,
        LocalBlurBars provides themeState.blurBars,
        LocalUseEnhancedShading provides themeState.useEnhancedShading,
        LocalThemeState provides themeState,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialExpressiveTheme(
            colorScheme = targetColorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = AppShapes,
            typography = typography,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Page background uses the base scheme so card/panel surface adjustments
                // never change the page backdrop.
                if (paintBackground) {
                    GradientBackground(
                        useGradient = effectiveUseGradient,
                        pageBackground = targetBackgroundScheme.background,
                        gradientBase = targetBackgroundScheme.surface,
                        gradientTop = targetBackgroundScheme.primaryContainer,
                        wallpaperTint = wallpaperTint,
                    )
                }
                content()
            }
        }
    }
}

@Composable
private fun GradientBackground(
    useGradient: Boolean,
    pageBackground: Color,
    gradientBase: Color,
    gradientTop: Color,
    wallpaperTint: Color?,
) {
    if (useGradient) {
        val gradientBrush =
            remember(gradientBase, gradientTop, wallpaperTint) {
                val topColor =
                    if (wallpaperTint != null) {
                        blendColors(gradientTop, wallpaperTint, wallpaperWeight = 0.28f)
                    } else {
                        gradientTop
                    }
                Brush.verticalGradient(
                    colorStops =
                        arrayOf(
                            0f to topColor.copy(alpha = 0.48f),
                            0.55f to gradientBase.copy(alpha = 0f),
                        ),
                )
            }
        Box(
            Modifier
                .fillMaxSize()
                .background(gradientBase)
                .background(gradientBrush),
        )
    } else {
        Box(Modifier.fillMaxSize().background(pageBackground))
    }
}

@Composable
private fun rememberSystemReducedMotionEnabled(context: android.content.Context): Boolean {
    val contentResolver = context.contentResolver

    fun readReducedMotion(): Boolean {
        val animationScale =
            Settings.Global.getFloat(
                contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        return animationScale == 0f
    }

    var reducedMotion by remember(contentResolver) { mutableStateOf(readReducedMotion()) }
    DisposableEffect(contentResolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    reducedMotion = readReducedMotion()
                }
            }
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose {
            contentResolver.unregisterContentObserver(observer)
        }
    }
    return reducedMotion
}

@Composable
private fun rememberWallpaperTintColor(
    context: android.content.Context,
    enabled: Boolean,
): Color? {
    if (!enabled) return null

    val applicationContext = context.applicationContext
    val wallpaperManager = remember(applicationContext) { WallpaperManager.getInstance(applicationContext) }

    fun readWallpaperTint(): Color? {
        val colors =
            runCatching {
                wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            }.getOrNull()
        return colors?.toComposeTint()
    }

    var wallpaperTint by remember(wallpaperManager) { mutableStateOf(readWallpaperTint()) }
    DisposableEffect(wallpaperManager) {
        val listener =
            WallpaperManager.OnColorsChangedListener { colors, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                    wallpaperTint = colors?.toComposeTint()
                }
            }
        wallpaperManager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        onDispose {
            wallpaperManager.removeOnColorsChangedListener(listener)
        }
    }
    return wallpaperTint
}

private fun WallpaperColors.toComposeTint(): Color {
    val color = primaryColor
    return Color(color.toArgb())
}

private fun blendColors(
    base: Color,
    wallpaper: Color,
    wallpaperWeight: Float,
): Color {
    val clampedWeight = wallpaperWeight.coerceIn(0f, 1f)
    val baseWeight = 1f - clampedWeight
    return Color(
        red = base.red * baseWeight + wallpaper.red * clampedWeight,
        green = base.green * baseWeight + wallpaper.green * clampedWeight,
        blue = base.blue * baseWeight + wallpaper.blue * clampedWeight,
        alpha = base.alpha,
    )
}
