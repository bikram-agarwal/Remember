@file:Suppress("DEPRECATION")

package dev.bikram.remember.ui.theme

import android.os.SystemClock
import android.view.SoundEffectConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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

    val context = LocalContext.current
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

    val realTapSound =
        remember(view) {
            val lastTapTimeMs = longArrayOf(0L)
            val minTapSoundSpacingMs = 85L
            {
                val now = SystemClock.uptimeMillis()
                if (now - lastTapTimeMs[0] >= minTapSoundSpacingMs) {
                    lastTapTimeMs[0] = now
                    // view.isShown is sometimes false in dialogs/sheets
                    view.playSoundEffect(SoundEffectConstants.CLICK)
                }
            }
        }
    val playTapSound = realTapSound

    CompositionLocalProvider(
        LocalIsDark provides darkTheme,
        LocalUseGradient provides themeState.useGradient,
        LocalHeroOnCards provides themeState.heroOnCards,
        LocalBlurBars provides themeState.blurBars,
        LocalUseEnhancedShading provides themeState.useEnhancedShading,
        LocalThemeState provides themeState,
        LocalTapSound provides playTapSound,
        LocalHapticEnabled provides interactionState.hapticFeedbackEnabled,
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
                        useGradient = themeState.useGradient,
                        pageBackground = colorResolution.backgroundScheme.background,
                        gradientBase = colorResolution.backgroundScheme.surface,
                        gradientTop = colorResolution.backgroundScheme.primaryContainer,
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
) {
    if (useGradient) {
        val gradientBrush =
            remember(gradientBase, gradientTop) {
                Brush.verticalGradient(
                    colorStops =
                        arrayOf(
                            0f to gradientTop.copy(alpha = 0.45f),
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
