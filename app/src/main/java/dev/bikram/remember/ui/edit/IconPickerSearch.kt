package dev.bikram.remember.ui.edit

import java.util.Locale
import kotlin.math.abs

/*
 * Pure-Kotlin search ranking helpers shared by the icon and emoji tabs of [IconPicker].
 *
 * The picker corpus is small (~510 icons + ~3.7k emojis) so we do a full linear
 * scan per keystroke. The interesting work is in deciding which entries match a
 * concept query like "spicy", "calender" (typo), or "songs". This file owns that
 * decision and is intentionally free of Android types so it can be unit tested.
 *
 * Scoring contract:
 * - Each item exposes a list of [SearchableField]s with per-field weights
 *   (name > slug > keywords > category).
 * - Tokens AND together: if any token fails to match any field, the item is dropped.
 * - For each token, the best matching field's score wins. Token scores sum into
 *   the item's final rank value.
 */

/** A weighted slice of searchable text attached to one icon/emoji entry. */
internal data class SearchableField(
    val text: String,
    val weight: Float,
)

/** Score buckets per match style; multiplied by [SearchableField.weight]. */
private const val EXACT_WORD_SCORE = 1.0f
private const val STEMMED_WORD_SCORE = 0.85f
private const val WORD_PREFIX_SCORE = 0.75f
private const val FUZZY_ONE_EDIT_SCORE = 0.5f

/** Minimum query token length before fuzzy matching engages (avoid 'cake' == 'care'). */
private const val FUZZY_MIN_TOKEN_LENGTH = 5

private val WHITESPACE_SPLIT = Regex("\\s+")

/** Splits field text into searchable words on whitespace, underscores, and hyphens. */
private val FIELD_WORD_SPLIT = Regex("[\\s_\\-]+")

/** Split a free-text query into normalized lowercase tokens. */
internal fun tokenizeQuery(rawQuery: String): List<String> =
    rawQuery
        .lowercase(Locale.getDefault())
        .split(WHITESPACE_SPLIT)
        .filter { it.isNotEmpty() }

/**
 * Rank an item against a tokenized query. Returns 0f when any token fails to
 * find a hit (caller should drop the item). Higher results sort earlier.
 */
internal fun scoreSearchable(
    queryTokens: List<String>,
    fields: List<SearchableField>,
): Float {
    if (queryTokens.isEmpty() || fields.isEmpty()) return 0f
    var total = 0f
    for (token in queryTokens) {
        val tokenStem = lightStem(token)
        var bestForToken = 0f
        for (field in fields) {
            val candidate = scoreFieldForToken(token, tokenStem, field)
            if (candidate > bestForToken) bestForToken = candidate
        }
        if (bestForToken <= 0f) return 0f
        total += bestForToken
    }
    return total
}

private fun scoreFieldForToken(
    token: String,
    tokenStem: String,
    field: SearchableField,
): Float {
    val lower = field.text.lowercase(Locale.getDefault())
    if (lower.isEmpty()) return 0f
    val words = lower.split(FIELD_WORD_SPLIT)
    val fuzzyEligible = token.length >= FUZZY_MIN_TOKEN_LENGTH
    var bestScore = 0f
    for (word in words) {
        if (word.isEmpty()) continue
        // Exact word match wins outright; no further tiers can beat it.
        if (word == token) {
            return field.weight * EXACT_WORD_SCORE + token.length * 0.02f
        }
        // Stemmed equality (e.g. user typed "songs" -> "song", field word "song" or "songs").
        if (tokenStem != token && (word == tokenStem || lightStem(word) == tokenStem)) {
            val candidate = field.weight * STEMMED_WORD_SCORE + tokenStem.length * 0.02f
            if (candidate > bestScore) bestScore = candidate
            continue
        }
        // Word prefix (e.g. user typed "fire" against "fireworks"). Excludes the
        // false-positive case of the token being a substring _inside_ a word
        // (e.g. "lit" inside "light").
        if (word.length > token.length && word.startsWith(token)) {
            val candidate = field.weight * WORD_PREFIX_SCORE + token.length * 0.01f
            if (candidate > bestScore) bestScore = candidate
            continue
        }
        // Last resort: 1-edit Levenshtein for typo tolerance on longer tokens.
        if (fuzzyEligible && isWithinOneEdit(word, token)) {
            val candidate = field.weight * FUZZY_ONE_EDIT_SCORE
            if (candidate > bestScore) bestScore = candidate
        }
    }
    return bestScore
}

/**
 * Conservative English stemmer: strips common plural/tense suffixes plus a
 * collapsing pass for doubled consonants left over from "-ing"/"-ed" forms
 * ("running" -> "run", "kicked" -> "kick"). Skips Porter's heavier rules to
 * keep behaviour predictable; we don't need a real stemming library.
 */
internal fun lightStem(token: String): String {
    val length = token.length
    if (length < 4) return token
    if (length >= 5 && token.endsWith("ies")) return token.substring(0, length - 3) + "y"
    if (length >= 5 && token.endsWith("ing")) return collapseDoubledConsonant(token.substring(0, length - 3))
    if (length >= 5 && token.endsWith("es")) return token.substring(0, length - 2)
    if (length >= 4 && token.endsWith("ed")) return collapseDoubledConsonant(token.substring(0, length - 2))
    if (length >= 4 && token.endsWith("s") && !token.endsWith("ss")) return token.substring(0, length - 1)
    return token
}

/** Collapse a trailing doubled consonant (running -> runn -> run) but leave 'll',
 *  'ss', 'zz' alone (call -> call, miss -> miss). */
private fun collapseDoubledConsonant(stem: String): String {
    if (stem.length < 3) return stem
    val last = stem[stem.length - 1]
    val secondLast = stem[stem.length - 2]
    if (last != secondLast) return stem
    if (last == 'l' || last == 's' || last == 'z') return stem
    return stem.substring(0, stem.length - 1)
}

/**
 * Levenshtein distance <= 1 in O(min(|a|,|b|)) without building the full DP
 * matrix. Returns true on equal strings, single substitution, single insertion,
 * or single deletion.
 */
internal fun isWithinOneEdit(
    a: String,
    b: String,
): Boolean {
    val la = a.length
    val lb = b.length
    if (la == lb) {
        var diffs = 0
        var index = 0
        while (index < la) {
            if (a[index] != b[index]) {
                diffs++
                if (diffs > 1) return false
            }
            index++
        }
        return true
    }
    if (abs(la - lb) != 1) return false
    val (shorter, longer) = if (la < lb) a to b else b to a
    var indexShort = 0
    var indexLong = 0
    var seenSkip = false
    while (indexShort < shorter.length && indexLong < longer.length) {
        if (shorter[indexShort] != longer[indexLong]) {
            if (seenSkip) return false
            seenSkip = true
            indexLong++
        } else {
            indexShort++
            indexLong++
        }
    }
    return true
}
