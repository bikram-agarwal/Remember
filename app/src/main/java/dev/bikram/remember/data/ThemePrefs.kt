package dev.bikram.remember.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { SYSTEM, LIGHT, DARK, BLACK }

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
    val colorSource: ColorSource = ColorSource.MATERIAL_YOU,
    val paletteStyle: PaletteStyleOpt = PaletteStyleOpt.TONAL_SPOT,
    val customSeeds: List<String> = emptyList(),
    val activeCustomSeed: String = "",
    val useGradient: Boolean = true,
    val shadingIntensity: Float = DEFAULT_SHADING_INTENSITY,
    val heroOnCards: Boolean = true,
    val adaptiveNoteThemes: Boolean = true,
    val blurBars: Boolean = true,
) {
    val useEnhancedShading: Boolean
        get() = shadingIntensity > 0.0f
}

class ThemePrefs(
    private val context: Context,
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
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
        val HERO_ON_CARDS = booleanPreferencesKey("hero_on_cards")
        val ADAPTIVE_NOTE_THEMES = booleanPreferencesKey("adaptive_note_themes")
        val BLUR_BARS = booleanPreferencesKey("blur_bars")
    }

    val state: Flow<ThemeState> =
        context.themePrefsDataStore.data.map { p ->
            ThemeState(
                themeMode =
                    runCatching { ThemeMode.valueOf(p[Keys.THEME_MODE] ?: "") }
                        .getOrDefault(ThemeMode.SYSTEM),
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
                heroOnCards = p[Keys.HERO_ON_CARDS] ?: true,
                adaptiveNoteThemes = p[Keys.ADAPTIVE_NOTE_THEMES] ?: true,
                blurBars = p[Keys.BLUR_BARS] ?: true,
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.themePrefsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
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

    suspend fun setHeroOnCards(value: Boolean) {
        context.themePrefsDataStore.edit { it[Keys.HERO_ON_CARDS] = value }
    }

    suspend fun setAdaptiveNoteThemes(value: Boolean) {
        context.themePrefsDataStore.edit { it[Keys.ADAPTIVE_NOTE_THEMES] = value }
    }

    suspend fun setBlurBars(value: Boolean) {
        context.themePrefsDataStore.edit { it[Keys.BLUR_BARS] = value }
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
            put(Keys.THEME_MODE.name, prefs[Keys.THEME_MODE].orEmpty())
            put(Keys.COLOR_SOURCE.name, prefs[Keys.COLOR_SOURCE].orEmpty())
            put(Keys.PALETTE_STYLE.name, prefs[Keys.PALETTE_STYLE].orEmpty())
            put(Keys.CUSTOM_SEEDS.name, prefs[Keys.CUSTOM_SEEDS].orEmpty())
            put(Keys.ACTIVE_CUSTOM_SEED.name, prefs[Keys.ACTIVE_CUSTOM_SEED].orEmpty())
            put(Keys.USE_GRADIENT.name, prefs[Keys.USE_GRADIENT] ?: true)
            put(Keys.SHADING_INTENSITY_FACTOR.name, (prefs[Keys.SHADING_INTENSITY_FACTOR] ?: DEFAULT_SHADING_INTENSITY).toDouble())
            put(Keys.HERO_ON_CARDS.name, prefs[Keys.HERO_ON_CARDS] ?: true)
            put(Keys.BLUR_BARS.name, prefs[Keys.BLUR_BARS] ?: true)
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

            val themeMode =
                stringOrNull(Keys.THEME_MODE.name)?.let { raw ->
                    runCatching { ThemeMode.valueOf(raw) }.getOrNull()
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

            themeMode?.let { mutable[Keys.THEME_MODE] = it.name }
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
            booleanOrNull(Keys.HERO_ON_CARDS.name)?.let { value ->
                mutable[Keys.HERO_ON_CARDS] = value
            }
            booleanOrNull(Keys.BLUR_BARS.name)?.let { value ->
                mutable[Keys.BLUR_BARS] = value
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
