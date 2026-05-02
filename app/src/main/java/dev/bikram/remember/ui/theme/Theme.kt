@file:Suppress("DEPRECATION")

package dev.bikram.remember.ui.theme

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.SoundEffectConstants
import android.view.View
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
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.bikram.remember.data.InteractionState
import dev.bikram.remember.data.ThemeMode
import dev.bikram.remember.data.ThemeState
import dev.bikram.remember.ui.feedback.LocalHapticEnabled
import dev.bikram.remember.ui.feedback.LocalTapSound

val LocalIsDark = staticCompositionLocalOf { false }

private const val MIN_TAP_SOUND_SPACING_MS = 85L

private class TapSoundPlayer(
    private val view: View,
) {
    private var tapSoundReady = true

    fun play() {
        if (!tapSoundReady) return
        tapSoundReady = false
        view.playSoundEffect(SoundEffectConstants.CLICK)
        view.handler?.postDelayed(
            {
                tapSoundReady = true
            },
            MIN_TAP_SOUND_SPACING_MS,
        ) ?: run {
            tapSoundReady = true
        }
    }
}

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
    interactionState: InteractionState = InteractionState(),
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
    val wallpaperTint = rememberWallpaperTintColor(context, enabled = effectiveUseGradient)
    val colorResolution =
        rememberResolvedColorScheme(
            context = context,
            themeState = themeState,
            darkTheme = darkTheme,
            black = black,
        )

    val view = LocalView.current
    LaunchedEffect(view) {
        view.isSoundEffectsEnabled = true
        var walkContext: android.content.Context? = view.context
        var hostingActivity: android.app.Activity? = null
        while (walkContext != null) {
            if (walkContext is android.app.Activity) {
                hostingActivity = walkContext
                break
            }
            walkContext = (walkContext as? android.content.ContextWrapper)?.baseContext
        }
        hostingActivity?.window?.decorView?.isSoundEffectsEnabled = true
    }
    LaunchedEffect(view, darkTheme) {
        if (view.isInEditMode) return@LaunchedEffect
        val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val playTapSound =
        remember(view) {
            TapSoundPlayer(view)::play
        }

    CompositionLocalProvider(
        LocalIsDark provides darkTheme,
        LocalUseGradient provides effectiveUseGradient,
        LocalHeroOnCards provides themeState.heroOnCards,
        LocalBlurBars provides themeState.blurBars,
        LocalUseEnhancedShading provides themeState.useEnhancedShading,
        LocalThemeState provides themeState,
        LocalTapSound provides playTapSound,
        LocalHapticEnabled provides interactionState.hapticFeedbackEnabled,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorResolution.colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = AppShapes,
            typography = AppTypography,
        ) {
            Box(Modifier.fillMaxSize()) {
                // Page background uses the base scheme so card/panel surface adjustments
                // never change the page backdrop.
                if (paintBackground) {
                    GradientBackground(
                        useGradient = effectiveUseGradient,
                        pageBackground = colorResolution.backgroundScheme.background,
                        gradientBase = colorResolution.backgroundScheme.surface,
                        gradientTop = colorResolution.backgroundScheme.primaryContainer,
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
