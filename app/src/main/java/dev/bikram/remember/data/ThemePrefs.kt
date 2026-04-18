package dev.bikram.remember.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { SYSTEM, LIGHT, DARK, BLACK }

enum class ColorSource {
    DEFAULT,
    MATERIAL_YOU,
    SAPPHIRE,
    EMERALD,
    AMBER,
    VIOLET,
    CORAL,
    TEAL,
    LIME,
    ROSE,
    SLATE,
    CUSTOM,
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

data class ThemeState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorSource: ColorSource = ColorSource.DEFAULT,
    val paletteStyle: PaletteStyleOpt = PaletteStyleOpt.TONAL_SPOT,
    val customSeeds: List<String> = emptyList(),
    val activeCustomSeed: String = "",
    val useGradient: Boolean = false,
    val fixedCardColors: Boolean = false,
    val heroOnCards: Boolean = false,
    val blurBars: Boolean = true,
    val viewOptions: ViewOptions = ViewOptions(),
    /** Map of lowercased tag name → hex color string ("#RRGGBB"). */
    val tagColors: Map<String, String> = emptyMap(),
)

private val Context.themeDataStore by preferencesDataStore(name = "theme_prefs")

class ThemePrefs(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val COLOR_SOURCE = stringPreferencesKey("color_source")
        val PALETTE_STYLE = stringPreferencesKey("palette_style")
        val CUSTOM_SEEDS = stringPreferencesKey("custom_seeds")
        val ACTIVE_CUSTOM_SEED = stringPreferencesKey("active_custom_seed")
        val USE_GRADIENT = booleanPreferencesKey("use_gradient")
        val FIXED_CARD_COLORS = booleanPreferencesKey("fixed_card_colors")
        val HERO_ON_CARDS = booleanPreferencesKey("hero_on_cards")
        val BLUR_BARS = booleanPreferencesKey("blur_bars")
        val SORT_KEY = stringPreferencesKey("sort_key")
        val SORT_DIR = stringPreferencesKey("sort_dir")
        val GROUP_BY = stringPreferencesKey("group_by")
        val TAG_COLORS = stringPreferencesKey("tag_colors")
    }

    val state: Flow<ThemeState> = context.themeDataStore.data.map { p ->
        ThemeState(
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME_MODE] ?: "") }
                .getOrDefault(ThemeMode.SYSTEM),
            colorSource = runCatching { ColorSource.valueOf(p[Keys.COLOR_SOURCE] ?: "") }
                .getOrDefault(ColorSource.DEFAULT),
            paletteStyle = runCatching { PaletteStyleOpt.valueOf(p[Keys.PALETTE_STYLE] ?: "") }
                .getOrDefault(PaletteStyleOpt.TONAL_SPOT),
            customSeeds = decodeSeeds(p[Keys.CUSTOM_SEEDS].orEmpty()),
            activeCustomSeed = p[Keys.ACTIVE_CUSTOM_SEED].orEmpty(),
            useGradient = p[Keys.USE_GRADIENT] ?: false,
            fixedCardColors = p[Keys.FIXED_CARD_COLORS] ?: false,
            heroOnCards = p[Keys.HERO_ON_CARDS] ?: false,
            blurBars = p[Keys.BLUR_BARS] ?: true,
            viewOptions = ViewOptions(
                sortKey = runCatching { SortKey.valueOf(p[Keys.SORT_KEY] ?: "") }
                    .getOrDefault(SortKey.LAST_MODIFIED),
                sortDir = runCatching { SortDir.valueOf(p[Keys.SORT_DIR] ?: "") }
                    .getOrDefault(SortDir.DESC),
                groupBy = runCatching { GroupBy.valueOf(p[Keys.GROUP_BY] ?: "") }
                    .getOrDefault(GroupBy.NONE),
            ),
            tagColors = decodeTagColors(p[Keys.TAG_COLORS].orEmpty()),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setColorSource(source: ColorSource) {
        context.themeDataStore.edit { it[Keys.COLOR_SOURCE] = source.name }
    }

    suspend fun setActiveCustomSeed(hex: String) {
        context.themeDataStore.edit {
            it[Keys.ACTIVE_CUSTOM_SEED] = hex
            it[Keys.COLOR_SOURCE] = ColorSource.CUSTOM.name
        }
    }

    suspend fun addCustomSeed(hex: String) {
        val normalized = normalizeHex(hex) ?: return
        context.themeDataStore.edit { p ->
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
        val normalized = normalizeHex(hex) ?: return
        context.themeDataStore.edit { p ->
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
        context.themeDataStore.edit { it[Keys.PALETTE_STYLE] = style.name }
    }

    suspend fun setUseGradient(value: Boolean) {
        context.themeDataStore.edit { it[Keys.USE_GRADIENT] = value }
    }

    suspend fun setFixedCardColors(value: Boolean) {
        context.themeDataStore.edit { it[Keys.FIXED_CARD_COLORS] = value }
    }

    suspend fun setHeroOnCards(value: Boolean) {
        context.themeDataStore.edit { it[Keys.HERO_ON_CARDS] = value }
    }

    suspend fun setBlurBars(value: Boolean) {
        context.themeDataStore.edit { it[Keys.BLUR_BARS] = value }
    }

    suspend fun setViewOptions(value: ViewOptions) {
        context.themeDataStore.edit {
            it[Keys.SORT_KEY] = value.sortKey.name
            it[Keys.SORT_DIR] = value.sortDir.name
            it[Keys.GROUP_BY] = value.groupBy.name
        }
    }

    suspend fun setTagColor(tag: String, hex: String) {
        val normalized = normalizeHex(hex) ?: return
        val key = tag.trim().lowercase()
        if (key.isBlank()) return
        context.themeDataStore.edit { p ->
            val current = decodeTagColors(p[Keys.TAG_COLORS].orEmpty())
            p[Keys.TAG_COLORS] = encodeTagColors(current + (key to normalized))
        }
    }

    suspend fun removeTagColor(tag: String) {
        val key = tag.trim().lowercase()
        context.themeDataStore.edit { p ->
            val current = decodeTagColors(p[Keys.TAG_COLORS].orEmpty())
            p[Keys.TAG_COLORS] = encodeTagColors(current - key)
        }
    }

    private fun decodeTagColors(value: String): Map<String, String> {
        if (value.isBlank()) return emptyMap()
        return runCatching {
            val obj = org.json.JSONObject(value)
            buildMap {
                obj.keys().forEach { k -> put(k, obj.getString(k)) }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeTagColors(map: Map<String, String>): String {
        val obj = org.json.JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun decodeSeeds(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(value)
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    private fun encodeSeeds(v: List<String>): String {
        val arr = JSONArray()
        v.forEach { arr.put(it) }
        return arr.toString()
    }

    suspend fun exportForBackup(): JSONObject {
        val prefs = context.themeDataStore.data.first()
        return JSONObject().apply {
            put(Keys.THEME_MODE.name, prefs[Keys.THEME_MODE].orEmpty())
            put(Keys.COLOR_SOURCE.name, prefs[Keys.COLOR_SOURCE].orEmpty())
            put(Keys.PALETTE_STYLE.name, prefs[Keys.PALETTE_STYLE].orEmpty())
            put(Keys.CUSTOM_SEEDS.name, prefs[Keys.CUSTOM_SEEDS].orEmpty())
            put(Keys.ACTIVE_CUSTOM_SEED.name, prefs[Keys.ACTIVE_CUSTOM_SEED].orEmpty())
            put(Keys.USE_GRADIENT.name, prefs[Keys.USE_GRADIENT] ?: false)
            put(Keys.FIXED_CARD_COLORS.name, prefs[Keys.FIXED_CARD_COLORS] ?: false)
            put(Keys.HERO_ON_CARDS.name, prefs[Keys.HERO_ON_CARDS] ?: false)
            put(Keys.BLUR_BARS.name, prefs[Keys.BLUR_BARS] ?: true)
            put(Keys.SORT_KEY.name, prefs[Keys.SORT_KEY].orEmpty())
            put(Keys.SORT_DIR.name, prefs[Keys.SORT_DIR].orEmpty())
            put(Keys.GROUP_BY.name, prefs[Keys.GROUP_BY].orEmpty())
            put(Keys.TAG_COLORS.name, prefs[Keys.TAG_COLORS].orEmpty())
        }
    }

    suspend fun importFromBackup(json: JSONObject?) {
        if (json == null || json.length() == 0) return
        context.themeDataStore.edit { mutable ->
            fun stringOrNull(key: String): String? =
                if (json.has(key) && !json.isNull(key)) json.getString(key) else null
            stringOrNull(Keys.THEME_MODE.name)?.let { mutable[Keys.THEME_MODE] = it }
            stringOrNull(Keys.COLOR_SOURCE.name)?.let { mutable[Keys.COLOR_SOURCE] = it }
            stringOrNull(Keys.PALETTE_STYLE.name)?.let { mutable[Keys.PALETTE_STYLE] = it }
            stringOrNull(Keys.CUSTOM_SEEDS.name)?.let { mutable[Keys.CUSTOM_SEEDS] = it }
            stringOrNull(Keys.ACTIVE_CUSTOM_SEED.name)?.let { mutable[Keys.ACTIVE_CUSTOM_SEED] = it }
            if (json.has(Keys.USE_GRADIENT.name) && !json.isNull(Keys.USE_GRADIENT.name)) {
                mutable[Keys.USE_GRADIENT] = json.getBoolean(Keys.USE_GRADIENT.name)
            }
            if (json.has(Keys.FIXED_CARD_COLORS.name) && !json.isNull(Keys.FIXED_CARD_COLORS.name)) {
                mutable[Keys.FIXED_CARD_COLORS] = json.getBoolean(Keys.FIXED_CARD_COLORS.name)
            }
            if (json.has(Keys.HERO_ON_CARDS.name) && !json.isNull(Keys.HERO_ON_CARDS.name)) {
                mutable[Keys.HERO_ON_CARDS] = json.getBoolean(Keys.HERO_ON_CARDS.name)
            }
            if (json.has(Keys.BLUR_BARS.name) && !json.isNull(Keys.BLUR_BARS.name)) {
                mutable[Keys.BLUR_BARS] = json.getBoolean(Keys.BLUR_BARS.name)
            }
            stringOrNull(Keys.SORT_KEY.name)?.let { mutable[Keys.SORT_KEY] = it }
            stringOrNull(Keys.SORT_DIR.name)?.let { mutable[Keys.SORT_DIR] = it }
            stringOrNull(Keys.GROUP_BY.name)?.let { mutable[Keys.GROUP_BY] = it }
            stringOrNull(Keys.TAG_COLORS.name)?.let { mutable[Keys.TAG_COLORS] = it }
        }
    }
}

/**
 * Normalize a user-entered hex color string to canonical uppercase `#RRGGBB` form.
 * Returns null if the input is not a valid 3- or 6-digit hex color.
 */
fun normalizeHex(raw: String): String? {
    val stripped = raw.trim().removePrefix("#")
    val hex = when (stripped.length) {
        3 -> stripped.map { "$it$it" }.joinToString("")
        6 -> stripped
        else -> return null
    }
    if (!hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
    return "#" + hex.uppercase()
}
