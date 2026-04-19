@file:Suppress("DEPRECATION")
package dev.bikram.remember.ui.theme

import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.launch
import android.view.SoundEffectConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import dev.bikram.remember.data.ColorSource
import dev.bikram.remember.data.InteractionState
import dev.bikram.remember.data.PaletteStyleOpt
import dev.bikram.remember.data.ThemeMode
import dev.bikram.remember.data.ThemeState
import dev.bikram.remember.ui.feedback.LocalHapticEnabled
import dev.bikram.remember.ui.feedback.LocalTapSound

val LocalIsDark = staticCompositionLocalOf { false }

/** Indigo seed for the DEFAULT color source - drives the same TonalSpot pipeline used
 *  by Material You and the preset swatches, so DEFAULT is uniformly indigo (not the
 *  multi-hue indigo + brown + pink expressive palette M3 ships with by default). */
private val DefaultSeed = Color(0xFF485CC7)

/** Seed colors for the 9 preset swatches. */
private val SeedColors = mapOf(
    ColorSource.SAPPHIRE to Color(0xFF1E63D6),
    ColorSource.EMERALD to Color(0xFF10B981),
    ColorSource.AMBER to Color(0xFFF59E0B),
    ColorSource.VIOLET to Color(0xFF8B5CF6),
    ColorSource.CORAL to Color(0xFFEF4444),
    ColorSource.TEAL to Color(0xFF14B8A6),
    ColorSource.LIME to Color(0xFF84CC16),
    ColorSource.ROSE to Color(0xFFF43F5E),
    ColorSource.SLATE to Color(0xFF64748B),
)

/** Public: pick the representative Color for a ColorSource (UI swatch rendering). */
fun seedColorFor(source: ColorSource, activeCustomHex: String): Color = when (source) {
    ColorSource.DEFAULT -> DefaultSeed
    ColorSource.MATERIAL_YOU -> Color(0xFF9B9DA7) // neutral - wallpaper drives real scheme
    ColorSource.CUSTOM -> runCatching { Color(android.graphics.Color.parseColor(activeCustomHex)) }
        .getOrElse { DefaultSeed }
    else -> SeedColors[source] ?: DefaultSeed
}

private fun PaletteStyleOpt.toLib(): PaletteStyle = when (this) {
    PaletteStyleOpt.TONAL_SPOT -> PaletteStyle.TonalSpot
    PaletteStyleOpt.NEUTRAL -> PaletteStyle.Neutral
    PaletteStyleOpt.VIBRANT -> PaletteStyle.Vibrant
    PaletteStyleOpt.EXPRESSIVE -> PaletteStyle.Expressive
    PaletteStyleOpt.RAINBOW -> PaletteStyle.Rainbow
    PaletteStyleOpt.FRUIT_SALAD -> PaletteStyle.FruitSalad
    PaletteStyleOpt.MONOCHROME -> PaletteStyle.Monochrome
    PaletteStyleOpt.FIDELITY -> PaletteStyle.Fidelity
    PaletteStyleOpt.CONTENT -> PaletteStyle.Content
}

/** Flatten surfaces to pure black for BLACK (OLED) mode. */
private fun ColorScheme.toOled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF0F0F0F),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF222222),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RememberTheme(
    themeState: ThemeState = ThemeState(),
    interactionState: InteractionState = InteractionState(),
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeState.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.BLACK -> true
    }
    val black = themeState.themeMode == ThemeMode.BLACK

    val context = LocalContext.current
    val materialYouAvailable =
        themeState.colorSource == ColorSource.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // All non-Material-You sources (DEFAULT, presets, CUSTOM) route through the same
    // materialkolor pipeline. materialkolor is a faithful Kotlin port of Google's
    // Material Color Utilities - the same library Android uses to derive Material You
    // schemes from a wallpaper-extracted seed - so given the same seed and PaletteStyle
    // we get an algorithmically equivalent palette to what Material You would produce.
    // On Android 12+ with Material You selected we still prefer the system's own
    // dynamicLightColorScheme/dynamicDarkColorScheme so the app inherits exactly the
    // wallpaper-tuned palette the OS already computed.
    val base: ColorScheme = when {
        materialYouAvailable -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        else -> {
            val seed = if (themeState.colorSource == ColorSource.MATERIAL_YOU) {
                DefaultSeed
            } else {
                seedColorFor(themeState.colorSource, themeState.activeCustomSeed)
            }
            rememberDynamicColorScheme(
                seedColor = seed,
                isDark = darkTheme,
                style = themeState.paletteStyle.toLib(),
                isAmoled = black,
            )
        }
    }

    val scheme = if (black) base.toOled() else base
    val tinted = if (!themeState.fixedCardColors && !black) scheme.tintSurfacesTowardPrimary(darkTheme)
    else scheme
    // Material You's wallpaper-tuned outlines and containers already have enough chroma
    // to read against our tinted surfaces. The default and seed-based schemes use M3's
    // stock muted outlines and fairly soft container roles, which fade into the same
    // tinted surfaces; boost both so OutlinedButton borders stay visible and so the
    // activated pill / FilledTonalButton fills (Cancel button, etc.) look distinct.
    val themed = if (materialYouAvailable) {
        tinted
    } else {
        tinted
            .boostOutlineForVisibility(darkTheme)
            .boostContainersForSeedThemes(darkTheme)
    }

    val view = LocalView.current
    SideEffect {
        view.isSoundEffectsEnabled = true
    }
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val realTapSound = remember(view) {
        val lastTapTimeMs = longArrayOf(0L)
        val minTapSoundSpacingMs = 85L
        {
            val now = SystemClock.uptimeMillis()
            if (now - lastTapTimeMs[0] >= minTapSoundSpacingMs) {
                lastTapTimeMs[0] = now
                val am = view.context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                am.playSoundEffect(android.media.AudioManager.FX_KEY_CLICK)
            }
        }
    }
    val playTapSound = realTapSound

    val defaultRipple = androidx.compose.material3.ripple()
    val tapSoundRipple = remember(defaultRipple, playTapSound) {
        object : androidx.compose.foundation.IndicationNodeFactory {
            override fun create(interactionSource: androidx.compose.foundation.interaction.InteractionSource): androidx.compose.ui.node.DelegatableNode {
                val rippleFactory = defaultRipple as? androidx.compose.foundation.IndicationNodeFactory
                val rippleNode = rippleFactory?.create(interactionSource) ?: object : androidx.compose.ui.Modifier.Node() {}
                return object : androidx.compose.ui.node.DelegatingNode() {
                    init {
                        delegate(rippleNode)
                    }
                    override fun onAttach() {
                        super.onAttach()
                        coroutineScope.launch {
                            interactionSource.interactions.collect { interaction ->
                                if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release) {
                                    playTapSound()
                                }
                            }
                        }
                    }
                }
            }
            override fun hashCode(): Int = defaultRipple.hashCode()
            override fun equals(other: Any?): Boolean = other === this
        }
    }

    CompositionLocalProvider(
        LocalIsDark provides darkTheme,
        LocalUseGradient provides themeState.useGradient,
        LocalHeroOnCards provides themeState.heroOnCards,
        LocalBlurBars provides themeState.blurBars,
        LocalFixedCardColors provides themeState.fixedCardColors,
        LocalTagColors provides themeState.tagColors,
        LocalThemeState provides themeState,
        LocalTapSound provides playTapSound,
        LocalHapticEnabled provides interactionState.hapticFeedbackEnabled,
        androidx.compose.foundation.LocalIndication provides tapSoundRipple,
    ) {
        MaterialExpressiveTheme(
            colorScheme = themed,
            motionScheme = MotionScheme.expressive(),
            typography = AppTypography,
        ) {
            Box(Modifier.fillMaxSize()) {
                // Page background uses the UNTINTED scheme so fixedCardColors
                // only affects cards, never the page.
                GradientBackground(
                    useGradient = themeState.useGradient,
                    pageBackground = scheme.background,
                    gradientBase = scheme.surface,
                    gradientTop = scheme.primaryContainer,
                )
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
        val gradientBrush = remember(gradientBase, gradientTop) {
            Brush.verticalGradient(
                colorStops = arrayOf(
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

/**
 * TopAppBarDefaults.topAppBarColors with transparent containers so scroll content
 * reads through. Chrome text/icons use onSurface so they stay legible on the gradient.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun transparentTopAppBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color.Transparent,
    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
    titleContentColor = MaterialTheme.colorScheme.onSurface,
    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun transparentLargeTopAppBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color.Transparent,
    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
    titleContentColor = MaterialTheme.colorScheme.onSurface,
    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
)

/** Blend every surface-container role toward the accent so cards pick up a visible theme hue. */
private fun ColorScheme.tintSurfacesTowardPrimary(dark: Boolean): ColorScheme {
    val accentArgb = ColorUtils.blendARGB(
        primary.toArgb(),
        primaryContainer.toArgb(),
        if (dark) 0.4f else 0.3f,
    )
    val amount = if (dark) 0.34f else 0.22f
    fun tint(c: Color) = Color(ColorUtils.blendARGB(c.toArgb(), accentArgb, amount))
    return copy(
        surface = tint(surface),
        surfaceVariant = tint(surfaceVariant),
        surfaceDim = tint(surfaceDim),
        surfaceBright = tint(surfaceBright),
        surfaceContainerLowest = tint(surfaceContainerLowest),
        surfaceContainerLow = tint(surfaceContainerLow),
        surfaceContainer = tint(surfaceContainer),
        surfaceContainerHigh = tint(surfaceContainerHigh),
        surfaceContainerHighest = tint(surfaceContainerHighest),
    )
}

/**
 * Pull `outline` and `outlineVariant` toward `onSurface` so OutlinedButton borders,
 * text-field strokes, and other outlined chrome read clearly against the surface.
 * Material 3's stock outline tones are intentionally muted; against the seed-derived
 * schemes they end up almost invisible after our surface tinting.
 */
private fun ColorScheme.boostOutlineForVisibility(dark: Boolean): ColorScheme {
    val targetArgb = onSurface.toArgb()
    val outlineBlend = if (dark) 0.32f else 0.28f
    val outlineVariantBlend = if (dark) 0.20f else 0.16f
    return copy(
        outline = Color(ColorUtils.blendARGB(outline.toArgb(), targetArgb, outlineBlend)),
        outlineVariant = Color(
            ColorUtils.blendARGB(outlineVariant.toArgb(), targetArgb, outlineVariantBlend),
        ),
    )
}

/**
 * Pull `primaryContainer` and `secondaryContainer` toward `primary` / `secondary` so the
 * activated pill backgrounds and FilledTonalButton fills (Cancel button, chip selections,
 * etc.) stay clearly distinct from our surface-tinted sheets and cards. Material You's
 * wallpaper-tuned containers already pop on their own; the seed-derived schemes from
 * materialkolor often land very close to the tinted surface tones below them, which
 * collapses the contrast.
 */
private fun ColorScheme.boostContainersForSeedThemes(dark: Boolean): ColorScheme {
    val primaryBlend = if (dark) 0.30f else 0.24f
    val secondaryBlend = if (dark) 0.26f else 0.20f
    return copy(
        primaryContainer = Color(
            ColorUtils.blendARGB(primaryContainer.toArgb(), primary.toArgb(), primaryBlend),
        ),
        secondaryContainer = Color(
            ColorUtils.blendARGB(secondaryContainer.toArgb(), secondary.toArgb(), secondaryBlend),
        ),
    )
}
