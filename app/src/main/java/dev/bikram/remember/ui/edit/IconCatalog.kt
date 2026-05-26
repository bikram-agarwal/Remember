package dev.bikram.remember.ui.edit

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import dev.bikram.remember.data.NoteKind
import java.util.Locale

/** Stored in note `iconKey`; payload is one emoji grapheme (keyboard / picker). */
const val ICON_EMOJI_PREFIX: String = "emoji:"

/** Material Symbols ligature for rich-text notes with no custom [iconKey] (flat single icon). */
const val DEFAULT_NOTE_HEADER_SYMBOL: String = "feed"

/** Material Symbols ligature for checklists with no custom [iconKey]. */
const val DEFAULT_LIST_HEADER_SYMBOL: String = "checklist"

/** Catalog key used when the note has no persisted [iconKey] (picker selection + labels). */
fun defaultIconCatalogKey(isChecklist: Boolean): String =
    if (isChecklist) {
        "${ICON_SYMBOL_PREFIX}$DEFAULT_LIST_HEADER_SYMBOL"
    } else {
        "${ICON_SYMBOL_PREFIX}$DEFAULT_NOTE_HEADER_SYMBOL"
    }

/** Stored in note `iconKey`; [Material Symbols](https://fonts.google.com/icons) ligature name. */
const val ICON_SYMBOL_PREFIX: String = "symbol:"

/** Stored in note `iconKey`; forces a filled Material Symbols variant. */
const val ICON_SYMBOL_FILLED_PREFIX: String = "symbol_filled:"

/** Stored in note `iconKey`; forces an outlined Material Symbols variant. */
const val ICON_SYMBOL_OUTLINED_PREFIX: String = "symbol_outlined:"

/** Stored in note `iconKey`; bundled raster (brand marks omitted from Symbols). */
const val ICON_DRAWABLE_PREFIX: String = "drawable:"

data class IconChoice(
    val key: String,
    val symbolName: String?,
    @param:DrawableRes val drawableRes: Int?,
    val label: String? = null,
    val filled: Boolean = true,
) {
    init {
        require((symbolName != null) xor (drawableRes != null)) {
            "IconChoice must have exactly one of symbolName or drawableRes"
        }
    }
}

data class IconCategory(
    @param:StringRes val nameRes: Int,
    val icons: List<IconChoice>,
)

@Immutable
sealed class NoteIcon {
    data class Symbol(
        val name: String,
        val filled: Boolean = true,
    ) : NoteIcon()

    data class Drawable(
        @param:DrawableRes val resId: Int,
    ) : NoteIcon()

    data class Emoji(
        val text: String,
    ) : NoteIcon()

    data object NotePlaceholder : NoteIcon()

    data object ListPlaceholder : NoteIcon()
}

/** Deduplicate by [IconChoice.key] so the icon grid never sees duplicate lazy keys (scroll crash). */
val iconCatalog: List<IconCategory> =
    bundledMaterialSymbolIconCategories.map { category ->
        IconCategory(
            nameRes = category.nameRes,
            icons = category.icons.distinctBy { choice -> choice.key },
        )
    }

private val symbolNameByKey: Map<String, String> =
    iconCatalog
        .flatMap { it.icons }
        .mapNotNull { choice ->
            choice.symbolName?.let { symbol -> choice.key to symbol }
        }.toMap()

private val symbolFilledByKey: Map<String, Boolean> =
    iconCatalog
        .flatMap { it.icons }
        .mapNotNull { choice ->
            choice.symbolName?.let { choice.key to choice.filled }
        }.toMap()

private val drawableResByKey: Map<String, Int> =
    iconCatalog
        .flatMap { it.icons }
        .mapNotNull { choice ->
            choice.drawableRes?.let { resId -> choice.key to resId }
        }.toMap()

private val labelByKey: Map<String, String> =
    iconCatalog
        .flatMap { it.icons }
        .mapNotNull { choice ->
            choice.label?.let { label -> choice.key to label }
        }.toMap()

private val legacySymbolIconKeyNormalizations: Map<String, String> =
    mapOf(
        "${ICON_SYMBOL_PREFIX}directions_car" to "${ICON_SYMBOL_PREFIX}local_taxi",
        "${ICON_SYMBOL_FILLED_PREFIX}directions_car" to "${ICON_SYMBOL_FILLED_PREFIX}local_taxi",
        "${ICON_SYMBOL_OUTLINED_PREFIX}directions_car" to "${ICON_SYMBOL_OUTLINED_PREFIX}local_taxi",
        "${ICON_SYMBOL_PREFIX}home" to "${ICON_SYMBOL_PREFIX}house",
        "${ICON_SYMBOL_FILLED_PREFIX}home" to "${ICON_SYMBOL_FILLED_PREFIX}house",
        "${ICON_SYMBOL_OUTLINED_PREFIX}home" to "${ICON_SYMBOL_OUTLINED_PREFIX}house",
        "${ICON_SYMBOL_PREFIX}try" to "${ICON_SYMBOL_PREFIX}currency_lira",
        "${ICON_SYMBOL_FILLED_PREFIX}try" to "${ICON_SYMBOL_FILLED_PREFIX}currency_lira",
        "${ICON_SYMBOL_OUTLINED_PREFIX}try" to "${ICON_SYMBOL_OUTLINED_PREFIX}currency_lira",
    )

/**
 * Returns the canonical catalog key for [iconKey], including legacy symbol keys that
 * predated aligned `symbol:` / ligature names (see [legacySymbolIconKeyNormalizations]).
 */
fun normalizeIconKey(iconKey: String?): String? {
    if (iconKey.isNullOrEmpty()) return iconKey
    return legacySymbolIconKeyNormalizations[iconKey] ?: iconKey
}

fun isSymbolIconKey(iconKey: String?): Boolean =
    iconKey?.startsWith(ICON_SYMBOL_PREFIX) == true ||
        iconKey?.startsWith(ICON_SYMBOL_FILLED_PREFIX) == true ||
        iconKey?.startsWith(ICON_SYMBOL_OUTLINED_PREFIX) == true

fun iconSymbolVariantFilled(iconKey: String?): Boolean? =
    when {
        iconKey?.startsWith(ICON_SYMBOL_FILLED_PREFIX) == true -> true
        iconKey?.startsWith(ICON_SYMBOL_OUTLINED_PREFIX) == true -> false
        else -> null
    }

fun iconSymbolCatalogKey(iconKey: String?): String? {
    val normalized = normalizeIconKey(iconKey) ?: return null
    val symbolName =
        when {
            normalized.startsWith(ICON_SYMBOL_PREFIX) -> normalized.removePrefix(ICON_SYMBOL_PREFIX)
            normalized.startsWith(ICON_SYMBOL_FILLED_PREFIX) -> normalized.removePrefix(ICON_SYMBOL_FILLED_PREFIX)
            normalized.startsWith(ICON_SYMBOL_OUTLINED_PREFIX) -> normalized.removePrefix(ICON_SYMBOL_OUTLINED_PREFIX)
            else -> return null
        }
    return "$ICON_SYMBOL_PREFIX$symbolName"
}

fun iconKeyWithSymbolFilled(
    iconKey: String,
    filled: Boolean,
): String {
    val catalogKey = iconSymbolCatalogKey(iconKey) ?: return iconKey
    val symbolName = catalogKey.removePrefix(ICON_SYMBOL_PREFIX)
    return if (filled) {
        "$ICON_SYMBOL_FILLED_PREFIX$symbolName"
    } else {
        "$ICON_SYMBOL_OUTLINED_PREFIX$symbolName"
    }
}

fun resolvedSymbolFilled(iconKey: String?): Boolean? {
    val normalized = normalizeIconKey(iconKey) ?: return null
    val catalogKey = iconSymbolCatalogKey(normalized) ?: return null
    return iconSymbolVariantFilled(normalized) ?: symbolFilledByKey[catalogKey] ?: true
}

fun iconSymbolName(key: String?): String? {
    val catalogKey = iconSymbolCatalogKey(key) ?: return null
    return symbolNameByKey[catalogKey]
}

@DrawableRes
fun iconDrawableRes(key: String?): Int? {
    val normalized = normalizeIconKey(key) ?: return null
    if (normalized.startsWith(ICON_EMOJI_PREFIX)) return null
    return drawableResByKey[normalized]
}

fun iconEmojiPayload(iconKey: String?): String? {
    val raw =
        iconKey
            ?.takeIf { it.startsWith(ICON_EMOJI_PREFIX) }
            ?.removePrefix(ICON_EMOJI_PREFIX)
            ?.takeIf { it.isNotBlank() }
            ?: return null
    val boundary = java.text.BreakIterator.getCharacterInstance()
    boundary.setText(raw)
    val start = boundary.first()
    val end = boundary.next()
    return raw.substring(start, end).takeIf { it.isNotBlank() }
}

fun resolveNoteIcon(
    iconKey: String?,
    kind: NoteKind,
): NoteIcon {
    val normalized = normalizeIconKey(iconKey)
    val emoji = iconEmojiPayload(normalized)
    if (emoji != null) return NoteIcon.Emoji(emoji)

    iconSymbolCatalogKey(normalized)?.let { catalogKey ->
        symbolNameByKey[catalogKey]?.let { symbolName ->
            return NoteIcon.Symbol(symbolName, filled = resolvedSymbolFilled(normalized) ?: true)
        }
    }

    normalized?.let { key ->
        drawableResByKey[key]?.let { drawableRes ->
            return NoteIcon.Drawable(drawableRes)
        }
    }

    return when (kind) {
        NoteKind.LIST -> NoteIcon.ListPlaceholder
        NoteKind.NOTE -> NoteIcon.NotePlaceholder
    }
}

fun humanizeIconKey(iconKey: String): String {
    labelByKey[iconKey]?.let { label -> return label }
    val base =
        when {
            iconKey.startsWith(ICON_SYMBOL_PREFIX) ->
                iconKey.removePrefix(ICON_SYMBOL_PREFIX)
            iconKey.startsWith(ICON_SYMBOL_FILLED_PREFIX) ->
                iconKey.removePrefix(ICON_SYMBOL_FILLED_PREFIX)
            iconKey.startsWith(ICON_SYMBOL_OUTLINED_PREFIX) ->
                iconKey.removePrefix(ICON_SYMBOL_OUTLINED_PREFIX)
            iconKey.startsWith(ICON_DRAWABLE_PREFIX) ->
                iconKey
                    .removePrefix(ICON_DRAWABLE_PREFIX)
                    .removePrefix("ic_")
                    .removePrefix("brand_")
                    .removePrefix("action_")
                    .removePrefix("stat_")
            else -> iconKey
        }
    if (base.isBlank()) return iconKey
    return base
        .split('_')
        .joinToString(" ") { word ->
            word.replaceFirstChar { character ->
                if (character.isLowerCase()) character.titlecase(Locale.US) else character.toString()
            }
        }
}

fun iconLabelFor(key: String?): String? {
    if (key.isNullOrEmpty()) return null
    val normalized = normalizeIconKey(key) ?: return null
    return when {
        normalized.startsWith(ICON_EMOJI_PREFIX) -> iconEmojiPayload(normalized)
        isSymbolIconKey(normalized) -> humanizeIconKey(normalized)
        normalized.startsWith(ICON_DRAWABLE_PREFIX) -> humanizeIconKey(normalized)
        else -> null
    }
}
