package dev.bikram.remember.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.bikram.remember.ui.theme.CustomFontStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,

    /** Legacy: migrated to [DARK] + [ThemeState.useBlackTheme]. Kept so [valueOf] can parse old backups. */
    @Deprecated("Use DARK with useBlackTheme")
    BLACK,
}

/** Stored theme_mode value from versions that used a fourth "Black" segment in the picker. */
const val LEGACY_BLACK_THEME_MODE_NAME = "BLACK"

fun isLegacyBlackThemeModeName(raw: String?): Boolean = raw == LEGACY_BLACK_THEME_MODE_NAME

fun ThemeMode.migrated(): ThemeMode =
    if (name == LEGACY_BLACK_THEME_MODE_NAME) {
        ThemeMode.DARK
    } else {
        this
    }

/** Whether [themeMode] resolves to a dark UI, given current system appearance. */
fun ThemeMode.effectiveDarkTheme(systemDark: Boolean): Boolean =
    when (name) {
        ThemeMode.LIGHT.name -> false
        ThemeMode.DARK.name, LEGACY_BLACK_THEME_MODE_NAME -> true
        else -> systemDark
    }

/** Whether pure-black OLED styling may apply for this mode while the UI is dark. */
fun ThemeMode.blackThemeEligible(isDarkTheme: Boolean): Boolean =
    when (name) {
        ThemeMode.LIGHT.name -> false
        ThemeMode.DARK.name, LEGACY_BLACK_THEME_MODE_NAME -> true
        else -> isDarkTheme
    }

enum class ColorSource {
    DEFAULT,
    MATERIAL_YOU,
    CUSTOM,

    /**
     * Curated three-color palette sets. Each entry below is a hand-tuned
     * primary/secondary/tertiary triplet that's guaranteed to render as visually distinct
     * accents across all palette styles - including TonalSpot and other low-chroma styles
     * that would otherwise collapse single-seed schemes onto a single hue. Palette-style
     * chips still apply to surfaces, on-colors, and outlines when a curated triplet is
     * active.
     */
    CURATED_EMBER,
    CURATED_GROVE,
    CURATED_HONEY,
    CURATED_OCEAN,
    CURATED_IRIS,
    CURATED_DUSK,
    CURATED_BERRY,

    /**
     * Legacy single-seed presets - removed from the picker. Kept here so DataStore reads
     * and backup restores from older app versions still parse; [migrated] maps each to
     * its hue-family survivor before any UI sees the value.
     */
    @Deprecated("Migrated to CURATED_OCEAN")
    SAPPHIRE,

    @Deprecated("Migrated to DEFAULT (Forest is now the default)")
    EMERALD,

    @Deprecated("Forest promoted to DEFAULT; standalone preset removed")
    CURATED_FOREST,

    @Deprecated("Migrated to CURATED_GROVE")
    AMBER,

    @Deprecated("Migrated to CURATED_DUSK")
    VIOLET,

    @Deprecated("Migrated to CURATED_EMBER")
    CORAL,

    @Deprecated("Migrated to CURATED_OCEAN")
    TEAL,

    @Deprecated("Migrated to CURATED_FOREST")
    LIME,

    @Deprecated("Migrated to CURATED_BERRY")
    ROSE,

    @Deprecated("Migrated to DEFAULT")
    SLATE,
}

@Suppress("DEPRECATION")
internal fun ColorSource.migrated(): ColorSource =
    when (this) {
        ColorSource.SAPPHIRE, ColorSource.TEAL -> ColorSource.CURATED_OCEAN
        ColorSource.EMERALD, ColorSource.LIME, ColorSource.CURATED_FOREST -> ColorSource.DEFAULT
        ColorSource.AMBER -> ColorSource.CURATED_GROVE
        ColorSource.CORAL -> ColorSource.CURATED_EMBER
        ColorSource.ROSE -> ColorSource.CURATED_BERRY
        ColorSource.VIOLET -> ColorSource.CURATED_DUSK
        ColorSource.SLATE -> ColorSource.DEFAULT
        else -> this
    }

enum class PaletteStyleOpt {
    TONAL_SPOT,
    NEUTRAL,
    VIBRANT,
    EXPRESSIVE,
    RAINBOW,
    FRUIT_SALAD,
    MONOCHROME,
    FIDELITY,
    CONTENT,
}

/** Surface-shading intensity used when nothing is stored yet. 1.0 == the slider's "medium" notch. */
const val DEFAULT_SHADING_INTENSITY = 1.0f

/** ObtainX-style UI scale. Multiplies Compose density so layout, icons, and text grow together. */
const val DEFAULT_UI_SCALE = 1.0f
const val UI_SCALE_MIN = 0.75f
const val UI_SCALE_MAX = 1.25f

fun clampUiScale(raw: Float): Float {
    if (!raw.isFinite() || raw <= 0f) return DEFAULT_UI_SCALE
    return raw.coerceIn(UI_SCALE_MIN, UI_SCALE_MAX)
}

/**
 * Legacy discrete shading levels from versions before the continuous [shading_intensity_factor].
 * Read-only: kept so installs that stored one of these still migrate to a factor on read. New
 * writes only persist the factor.
 */
private enum class ShadingIntensity { NONE, SUBTLE, MEDIUM, INTENSE }

private fun ShadingIntensity.toFactor(): Float =
    when (this) {
        ShadingIntensity.NONE -> 0.0f
        ShadingIntensity.SUBTLE -> 0.4f
        ShadingIntensity.MEDIUM -> 1.0f
        ShadingIntensity.INTENSE -> 1.8f
    }

data class ThemeState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useBlackTheme: Boolean = false,
    val colorSource: ColorSource = ColorSource.MATERIAL_YOU,
    val paletteStyle: PaletteStyleOpt = PaletteStyleOpt.TONAL_SPOT,
    val customSeeds: List<String> = emptyList(),
    val activeCustomSeed: String = "",
    val useGradient: Boolean = true,
    val shadingIntensity: Float = DEFAULT_SHADING_INTENSITY,
    val uiScale: Float = DEFAULT_UI_SCALE,
    val heroOnCards: Boolean = true,
    val adaptiveNoteThemes: Boolean = true,
    val blurBars: Boolean = true,
    val customFontPath: String = "",
    val customFontName: String = "",
) {
    val useEnhancedShading: Boolean
        get() = shadingIntensity > 0.0f

    /** ObtainX parity: pure black only while the app is effectively on a dark theme. */
    fun blackThemeActive(isDarkTheme: Boolean): Boolean = useBlackTheme && themeMode.blackThemeEligible(isDarkTheme)

    fun effectiveShadingIntensity(blackThemeActive: Boolean): Float = if (blackThemeActive) DEFAULT_SHADING_INTENSITY else shadingIntensity

    fun effectiveUseGradient(blackThemeActive: Boolean): Boolean = if (blackThemeActive) false else useGradient
}

class ThemePrefs(
    private val context: Context,
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_BLACK_THEME = booleanPreferencesKey("use_black_theme")
        val COLOR_SOURCE = stringPreferencesKey("color_source")
        val PALETTE_STYLE = stringPreferencesKey("palette_style")
        val CUSTOM_SEEDS = stringPreferencesKey("custom_seeds")
        val ACTIVE_CUSTOM_SEED = stringPreferencesKey("active_custom_seed")
        val USE_GRADIENT = booleanPreferencesKey("use_gradient")

        // Legacy shading keys, read-only: migrated to SHADING_INTENSITY_FACTOR on read for installs
        // that predate it. setShadingIntensity / backup import no longer write them.
        val USE_ENHANCED_SHADING = booleanPreferencesKey("use_enhanced_shading")
        val SHADING_INTENSITY = stringPreferencesKey("shading_intensity")
        val SHADING_INTENSITY_FACTOR = floatPreferencesKey("shading_intensity_factor")
        val UI_SCALE = floatPreferencesKey("ui_scale")
        val HERO_ON_CARDS = booleanPreferencesKey("hero_on_cards")
        val ADAPTIVE_NOTE_THEMES = booleanPreferencesKey("adaptive_note_themes")
        val BLUR_BARS = booleanPreferencesKey("blur_bars")
        val CUSTOM_FONT_PATH = stringPreferencesKey("custom_font_path")
        val CUSTOM_FONT_NAME = stringPreferencesKey("custom_font_name")
    }

    val state: Flow<ThemeState> =
        context.themePrefsDataStore.data.map { p ->
            val storedThemeModeRaw = p[Keys.THEME_MODE].orEmpty()
            val storedThemeMode =
                runCatching { ThemeMode.valueOf(storedThemeModeRaw) }
                    .getOrDefault(ThemeMode.SYSTEM)
            val legacyBlackTheme = isLegacyBlackThemeModeName(storedThemeModeRaw)
            ThemeState(
                themeMode = storedThemeMode.migrated(),
                useBlackTheme = (p[Keys.USE_BLACK_THEME] ?: false) || legacyBlackTheme,
                colorSource =
                    runCatching { ColorSource.valueOf(p[Keys.COLOR_SOURCE] ?: "") }
                        .getOrDefault(ColorSource.MATERIAL_YOU)
                        .migrated(),
                paletteStyle =
                    runCatching { PaletteStyleOpt.valueOf(p[Keys.PALETTE_STYLE] ?: "") }
                        .getOrDefault(PaletteStyleOpt.TONAL_SPOT),
                customSeeds = decodeSeeds(p[Keys.CUSTOM_SEEDS].orEmpty()),
                activeCustomSeed = normalizeCustomSeed(p[Keys.ACTIVE_CUSTOM_SEED].orEmpty()).orEmpty(),
                useGradient = p[Keys.USE_GRADIENT] ?: true,
                shadingIntensity =
                    p[Keys.SHADING_INTENSITY_FACTOR]
                        ?: runCatching {
                            ShadingIntensity.valueOf(p[Keys.SHADING_INTENSITY] ?: "").toFactor()
                        }.getOrElse {
                            if (p[Keys.USE_ENHANCED_SHADING] == true) 0.0f else DEFAULT_SHADING_INTENSITY
                        },
                uiScale = clampUiScale(p[Keys.UI_SCALE] ?: DEFAULT_UI_SCALE),
                heroOnCards = p[Keys.HERO_ON_CARDS] ?: true,
                adaptiveNoteThemes = p[Keys.ADAPTIVE_NOTE_THEMES] ?: true,
                blurBars = p[Keys.BLUR_BARS] ?: true,
                customFontPath = p[Keys.CUSTOM_FONT_PATH].orEmpty(),
                customFontName = p[Keys.CUSTOM_FONT_NAME].orEmpty(),
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.themePrefsDataStore.edit { it[Keys.THEME_MODE] = mode.migrated().name }
    }

    suspend fun setUseBlackTheme(value: Boolean) {
        context.themePrefsDataStore.edit { it[Keys.USE_BLACK_THEME] = value }
    }

    /**
     * Rewrite legacy theme_mode BLACK to DARK + use_black_theme in DataStore.
     */
    suspend fun migrateLegacyBlackThemeIfNeeded() {
        context.themePrefsDataStore.edit { prefs ->
            if (!isLegacyBlackThemeModeName(prefs[Keys.THEME_MODE])) return@edit
            prefs[Keys.THEME_MODE] = ThemeMode.DARK.name
            prefs[Keys.USE_BLACK_THEME] = true
        }
    }

    /**
     * Rewrite a deprecated color_source string (e.g. "SAPPHIRE", "ROSE") to its hue-family
     * survivor in DataStore so subsequent reads don't pay the migration cost. Safe to call
     * on every app start - a no-op when the stored value is already current.
     */
    suspend fun migrateLegacyColorSourceIfNeeded() {
        context.themePrefsDataStore.edit { p ->
            val raw = p[Keys.COLOR_SOURCE] ?: return@edit
            val parsed = runCatching { ColorSource.valueOf(raw) }.getOrNull() ?: return@edit
            val migrated = parsed.migrated()
            if (migrated != parsed) p[Keys.COLOR_SOURCE] = migrated.name
        }
    }

    suspend fun setColorSource(source: ColorSource) {
        context.themePrefsDataStore.edit { it[Keys.COLOR_SOURCE] = source.name }
    }

    suspend fun setActiveCustomSeed(hex: String) {
        val normalized = normalizeCustomSeed(hex) ?: return
        context.themePrefsDataStore.edit { p ->
            val storedSeeds = decodeSeeds(p[Keys.CUSTOM_SEEDS].orEmpty())
            val matchedStored =
                storedSeeds
                    .firstOrNull { stored ->
                        normalizeCustomSeed(stored) == normalized
                    }
            if (matchedStored == null) {
                p[Keys.CUSTOM_SEEDS] = encodeSeeds(storedSeeds + normalized)
            }
            p[Keys.ACTIVE_CUSTOM_SEED] =
                matchedStored?.let { stored ->
                    normalizeCustomSeed(stored) ?: stored
                } ?: normalized
            p[Keys.COLOR_SOURCE] = ColorSource.CUSTOM.name
        }
    }

    suspend fun previewCustomSeed(hex: String) {
        val normalized = normalizeCustomSeed(hex) ?: return
        context.themePrefsDataStore.edit { p ->
            p[Keys.ACTIVE_CUSTOM_SEED] = normalized
            p[Keys.COLOR_SOURCE] = ColorSource.CUSTOM.name
        }
    }

    suspend fun addCustomSeed(hex: String) {
        val normalized = normalizeCustomSeed(hex) ?: return
        context.themePrefsDataStore.edit { p ->
            val current = decodeSeeds(p[Keys.CUSTOM_SEEDS].orEmpty())
            if (current.contains(normalized)) {
                p[Keys.ACTIVE_CUSTOM_SEED] = normalized
            } else {
                p[Keys.CUSTOM_SEEDS] = encodeSeeds(current + normalized)
                p[Keys.ACTIVE_CUSTOM_SEED] = normalized
            }
            p[Keys.COLOR_SOURCE] = ColorSource.CUSTOM.name
        }
    }

    suspend fun removeCustomSeed(hex: String) {
        val normalized = normalizeCustomSeed(hex) ?: return
        context.themePrefsDataStore.edit { p ->
            val current = decodeSeeds(p[Keys.CUSTOM_SEEDS].orEmpty())
            val next = current.filterNot { it.equals(normalized, ignoreCase = true) }
            p[Keys.CUSTOM_SEEDS] = encodeSeeds(next)
            if ((p[Keys.ACTIVE_CUSTOM_SEED] ?: "").equals(normalized, ignoreCase = true)) {
                p[Keys.ACTIVE_CUSTOM_SEED] = ""
                p[Keys.COLOR_SOURCE] = ColorSource.DEFAULT.name
            }
        }
    }

    suspend fun setPaletteStyle(style: PaletteStyleOpt) {
        context.themePrefsDataStore.edit { it[Keys.PALETTE_STYLE] = style.name }
    }

    suspend fun setUseGradient(value: Boolean) {
        context.themePrefsDataStore.edit { it[Keys.USE_GRADIENT] = value }
    }

    suspend fun setShadingIntensity(intensity: Float) {
        context.themePrefsDataStore.edit { it[Keys.SHADING_INTENSITY_FACTOR] = intensity }
    }

    suspend fun setUiScale(scale: Float) {
        context.themePrefsDataStore.edit { it[Keys.UI_SCALE] = clampUiScale(scale) }
    }

    suspend fun setHeroOnCards(value: Boolean) {
        context.themePrefsDataStore.edit { it[Keys.HERO_ON_CARDS] = value }
    }

    suspend fun setAdaptiveNoteThemes(value: Boolean) {
        context.themePrefsDataStore.edit { it[Keys.ADAPTIVE_NOTE_THEMES] = value }
    }

    suspend fun setBlurBars(value: Boolean) {
        context.themePrefsDataStore.edit { it[Keys.BLUR_BARS] = value }
    }

    suspend fun setCustomFont(
        path: String,
        displayName: String,
    ) {
        context.themePrefsDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_FONT_PATH] = path
            prefs[Keys.CUSTOM_FONT_NAME] = displayName
        }
    }

    suspend fun clearCustomFont() {
        CustomFontStorage.deleteStoredFontFiles(context)
        context.themePrefsDataStore.edit { prefs ->
            prefs.remove(Keys.CUSTOM_FONT_PATH)
            prefs.remove(Keys.CUSTOM_FONT_NAME)
        }
    }

    private fun decodeSeeds(value: String): List<String> {
        val normalizedSeeds = decodeNormalizedSeeds(value)
        return normalizedSeeds ?: emptyList()
    }

    private fun decodeNormalizedSeeds(value: String): List<String>? {
        if (value.isBlank()) return emptyList()
        return runCatching {
            val jsonArray = JSONArray(value)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val normalized = normalizeCustomSeed(jsonArray.getString(index)) ?: continue
                    if (!contains(normalized)) {
                        add(normalized)
                    }
                }
            }
        }.getOrNull()
    }

    private fun encodeSeeds(seeds: List<String>): String {
        val jsonArray = JSONArray()
        seeds.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }

    suspend fun exportForBackup(): JSONObject {
        val prefs = context.themePrefsDataStore.data.first()
        return JSONObject().apply {
            val storedThemeMode = prefs[Keys.THEME_MODE].orEmpty()
            val exportBlackTheme =
                (prefs[Keys.USE_BLACK_THEME] ?: false) || isLegacyBlackThemeModeName(storedThemeMode)
            put(
                Keys.THEME_MODE.name,
                if (isLegacyBlackThemeModeName(storedThemeMode)) {
                    ThemeMode.DARK.name
                } else {
                    storedThemeMode
                },
            )
            put(Keys.USE_BLACK_THEME.name, exportBlackTheme)
            put(Keys.COLOR_SOURCE.name, prefs[Keys.COLOR_SOURCE].orEmpty())
            put(Keys.PALETTE_STYLE.name, prefs[Keys.PALETTE_STYLE].orEmpty())
            put(Keys.CUSTOM_SEEDS.name, prefs[Keys.CUSTOM_SEEDS].orEmpty())
            put(Keys.ACTIVE_CUSTOM_SEED.name, prefs[Keys.ACTIVE_CUSTOM_SEED].orEmpty())
            put(Keys.USE_GRADIENT.name, prefs[Keys.USE_GRADIENT] ?: true)
            put(Keys.SHADING_INTENSITY_FACTOR.name, (prefs[Keys.SHADING_INTENSITY_FACTOR] ?: DEFAULT_SHADING_INTENSITY).toDouble())
            put(Keys.UI_SCALE.name, clampUiScale(prefs[Keys.UI_SCALE] ?: DEFAULT_UI_SCALE).toDouble())
            put(Keys.HERO_ON_CARDS.name, prefs[Keys.HERO_ON_CARDS] ?: true)
            put(Keys.ADAPTIVE_NOTE_THEMES.name, prefs[Keys.ADAPTIVE_NOTE_THEMES] ?: true)
            put(Keys.BLUR_BARS.name, prefs[Keys.BLUR_BARS] ?: true)
            put(Keys.CUSTOM_FONT_PATH.name, prefs[Keys.CUSTOM_FONT_PATH].orEmpty())
            put(Keys.CUSTOM_FONT_NAME.name, prefs[Keys.CUSTOM_FONT_NAME].orEmpty())
        }
    }

    suspend fun importFromBackup(json: JSONObject?) {
        if (json == null || json.length() == 0) return
        context.themePrefsDataStore.edit { mutable ->
            fun stringOrNull(key: String): String? {
                if (!json.has(key) || json.isNull(key)) return null
                return runCatching { json.getString(key) }.getOrNull()
            }

            fun booleanOrNull(key: String): Boolean? {
                if (!json.has(key) || json.isNull(key)) return null
                return when (val rawValue = json.opt(key)) {
                    null -> null
                    is Boolean -> rawValue
                    is String ->
                        when (rawValue.trim().lowercase()) {
                            "true" -> true
                            "false" -> false
                            else -> null
                        }
                    else -> null
                }
            }

            val colorSource =
                stringOrNull(Keys.COLOR_SOURCE.name)?.let { raw ->
                    runCatching { ColorSource.valueOf(raw) }.getOrNull()?.migrated()
                }
            val paletteStyle =
                stringOrNull(Keys.PALETTE_STYLE.name)?.let { raw ->
                    runCatching { PaletteStyleOpt.valueOf(raw) }.getOrNull()
                }
            val activeCustomSeed = stringOrNull(Keys.ACTIVE_CUSTOM_SEED.name)?.let { normalizeCustomSeed(it) }
            val customSeeds =
                stringOrNull(Keys.CUSTOM_SEEDS.name)?.let { rawSeeds ->
                    decodeNormalizedSeeds(rawSeeds)
                }
            val seedsToStore =
                if (customSeeds != null && activeCustomSeed != null && !customSeeds.contains(activeCustomSeed)) {
                    customSeeds + activeCustomSeed
                } else {
                    customSeeds
                }

            val themeModeRaw = stringOrNull(Keys.THEME_MODE.name)
            when {
                isLegacyBlackThemeModeName(themeModeRaw) -> {
                    mutable[Keys.THEME_MODE] = ThemeMode.DARK.name
                    mutable[Keys.USE_BLACK_THEME] = true
                }

                themeModeRaw != null -> {
                    val themeMode = runCatching { ThemeMode.valueOf(themeModeRaw) }.getOrNull()
                    if (themeMode != null) {
                        mutable[Keys.THEME_MODE] = themeMode.migrated().name
                        booleanOrNull(Keys.USE_BLACK_THEME.name)?.let { value ->
                            mutable[Keys.USE_BLACK_THEME] = value
                        }
                    }
                }
            }
            if (colorSource != ColorSource.CUSTOM || activeCustomSeed != null) {
                colorSource?.let { mutable[Keys.COLOR_SOURCE] = it.name }
            }
            paletteStyle?.let { mutable[Keys.PALETTE_STYLE] = it.name }
            seedsToStore?.let { mutable[Keys.CUSTOM_SEEDS] = encodeSeeds(it) }
            activeCustomSeed?.let { mutable[Keys.ACTIVE_CUSTOM_SEED] = it }
            booleanOrNull(Keys.USE_GRADIENT.name)?.let { value ->
                mutable[Keys.USE_GRADIENT] = value
            }
            val importedShadingFactor =
                if (json.has(Keys.SHADING_INTENSITY_FACTOR.name) && !json.isNull(Keys.SHADING_INTENSITY_FACTOR.name)) {
                    runCatching { json.getDouble(Keys.SHADING_INTENSITY_FACTOR.name).toFloat() }.getOrNull()
                } else {
                    // Older backups carry only the discrete enum; migrate it to a factor.
                    stringOrNull(Keys.SHADING_INTENSITY.name)?.let { raw ->
                        runCatching { ShadingIntensity.valueOf(raw).toFactor() }.getOrNull()
                    }
                }
            importedShadingFactor?.let { value -> mutable[Keys.SHADING_INTENSITY_FACTOR] = value }
            if (json.has(Keys.UI_SCALE.name) && !json.isNull(Keys.UI_SCALE.name)) {
                runCatching { json.getDouble(Keys.UI_SCALE.name).toFloat() }.getOrNull()?.let { rawScale ->
                    mutable[Keys.UI_SCALE] = clampUiScale(rawScale)
                }
            }
            booleanOrNull(Keys.HERO_ON_CARDS.name)?.let { value ->
                mutable[Keys.HERO_ON_CARDS] = value
            }
            booleanOrNull(Keys.ADAPTIVE_NOTE_THEMES.name)?.let { value ->
                mutable[Keys.ADAPTIVE_NOTE_THEMES] = value
            }
            booleanOrNull(Keys.BLUR_BARS.name)?.let { value ->
                mutable[Keys.BLUR_BARS] = value
            }

            val restoredFontPath = stringOrNull(Keys.CUSTOM_FONT_PATH.name).orEmpty().trim()
            if (restoredFontPath.isNotBlank() && java.io.File(restoredFontPath).isFile) {
                mutable[Keys.CUSTOM_FONT_PATH] = restoredFontPath
                val restoredFontName = stringOrNull(Keys.CUSTOM_FONT_NAME.name).orEmpty().trim()
                if (restoredFontName.isNotBlank()) {
                    mutable[Keys.CUSTOM_FONT_NAME] = restoredFontName
                } else {
                    mutable.remove(Keys.CUSTOM_FONT_NAME)
                }
            } else {
                mutable.remove(Keys.CUSTOM_FONT_PATH)
                mutable.remove(Keys.CUSTOM_FONT_NAME)
            }
        }
    }

    suspend fun reset() {
        context.themePrefsDataStore.edit { it.clear() }
    }
}

/**
 * Normalize a user-entered hex color string to canonical uppercase `#RRGGBB` form.
 * Returns null if the input is not a valid 3- or 6-digit hex color.
 */
fun normalizeHex(raw: String): String? {
    val stripped = raw.trim().removePrefix("#")
    val hex =
        when (stripped.length) {
            3 -> stripped.map { "$it$it" }.joinToString("")
            6 -> stripped
            else -> return null
        }
    if (!hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
    return "#" + hex.uppercase()
}

/**
 * Normalize a custom seed, which can be a single hex or a pipe-separated triplet of hexes.
 */
fun normalizeCustomSeed(raw: String): String? {
    if (raw.contains("|")) {
        val parts = raw.split("|")
        val normalizedParts =
            parts.map { part ->
                normalizeHex(part) ?: return null
            }
        return normalizedParts.joinToString("|")
    }
    return normalizeHex(raw)
}
