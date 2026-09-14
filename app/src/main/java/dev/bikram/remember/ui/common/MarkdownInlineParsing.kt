@file:Suppress("ktlint:standard:function-expression-body")

package dev.bikram.remember.ui.common

internal fun String.indexOfMarkdownClosingMarker(
    marker: String,
    startIndex: Int,
    allowTrailingWhitespace: Boolean = false,
): Int {
    return MarkdownInlineMatcher(this, allowTrailingWhitespace).closingMarker(marker, startIndex)
}

/** Per-line matching state is discarded after parsing; it never caches an edited document. */
internal class MarkdownInlineMatcher(
    private val source: String,
    private val allowTrailingWhitespace: Boolean = true,
) {
    private val underlineClosers = mutableMapOf<Int, Int>()
    private val strikeClosers = mutableMapOf<Pair<Int, Int>, Int>()
    private val codeClosers = mutableMapOf<Pair<Int, Int>, Int>()

    fun closingMarker(
        marker: String,
        startIndex: Int,
        endIndex: Int = source.length,
    ): Int =
        when (marker) {
            "<u>" -> underlineClosingMarker(startIndex - 3).takeIf { it >= 0 && it + 4 <= endIndex } ?: -1
            "~~" ->
                strikeClosers.getOrPut(startIndex to endIndex) {
                    source.indexOfRunClosingMarker('~', 2, startIndex, endIndex, allowTrailingWhitespace, this)
                }
            "`" ->
                codeClosers.getOrPut(startIndex to endIndex) {
                    source.indexOfNonAsteriskClosingMarker(marker, startIndex, endIndex, allowTrailingWhitespace)
                }
            else -> source.indexOfRunClosingMarker('*', marker.length, startIndex, endIndex, allowTrailingWhitespace, this)
        }

    fun protectedWrapperEnd(
        index: Int,
        includeStrike: Boolean,
        endIndex: Int,
        includeEmphasis: Boolean = false,
    ): Int {
        val literalEnd = literalWrapperEnd(index, endIndex)
        if (literalEnd > index) return literalEnd
        if (source.startsWith("<u>", index, ignoreCase = true)) {
            val close = underlineClosingMarker(index)
            if (close >= 0 && close + 4 <= endIndex) return close + 4
        }
        if (includeStrike && source.startsWith("~~", index) && source.isValidOpening(index, 2)) {
            val close = closingMarker("~~", index + 2, endIndex)
            if (close >= 0) return close + 2
        }
        if (includeEmphasis && source.getOrNull(index) == '*') {
            if (source.startsWith("****", index) && source.getOrNull(index + 4) != '*' && index + 4 <= endIndex) return index + 4
            for (width in 3 downTo 1) {
                if (!source.startsWith("*".repeat(width), index) || !source.isValidOpening(index, width)) continue
                // This search treats strike as content, keeping matching iterative even when
                // emphasis and strike are nested repeatedly in alternating order.
                val close = source.indexOfRunClosingMarker('*', width, index + width, endIndex, allowTrailingWhitespace, this, protectStrike = false)
                if (close >= 0) return close + width
            }
        }
        return -1
    }

    private fun literalWrapperEnd(
        index: Int,
        endIndex: Int = source.length,
    ): Int {
        if (source.getOrNull(index) == '[') {
            MarkdownLinkRegex.matchAt(source, index)?.takeIf { it.range.last < endIndex }?.let { return it.range.last + 1 }
        }
        if (source.getOrNull(index) == '`' && source.isValidOpening(index, 1)) {
            val close = closingMarker("`", index + 1, endIndex)
            if (close >= 0) return close + 1
        }
        return -1
    }

    private fun underlineClosingMarker(opener: Int): Int {
        underlineClosers[opener]?.let { return it }
        val openers = ArrayDeque<Int>()
        openers.addLast(opener)
        var index = opener + 3
        while (index < source.length) {
            val literalEnd = literalWrapperEnd(index)
            if (literalEnd > index) {
                index = literalEnd
            } else if (source.startsWith("<u>", index, ignoreCase = true)) {
                openers.addLast(index)
                index += 3
            } else if (source.startsWith("</u>", index, ignoreCase = true)) {
                val matchedOpener = openers.removeLast()
                underlineClosers[matchedOpener] = index
                if (openers.isEmpty()) return index
                index += 4
            } else {
                index++
            }
        }
        // Record unmatched nested tags too, avoiding another full scan for each inner tag.
        for (unmatched in openers) underlineClosers[unmatched] = -1
        return -1
    }
}

private fun String.indexOfNonAsteriskClosingMarker(
    marker: String,
    startIndex: Int,
    endIndex: Int,
    allowTrailingWhitespace: Boolean,
): Int {
    val markerChar = marker[0]
    var index = indexOf(marker, startIndex)
    var gluedFallbackIndex = -1
    while (index != -1 && index + marker.length <= endIndex) {
        val hasPrecedingMarkerChar = index > 0 && this[index - 1] == markerChar
        val hasFollowingMarkerChar = index + marker.length < length && this[index + marker.length] == markerChar
        val prevChar = getOrNull(index - 1)
        val editingClose = allowTrailingWhitespace && isEditingWhitespaceClose(index, index + marker.length)
        if ((prevChar != null && !prevChar.isWhitespace()) || editingClose) {
            if (!hasPrecedingMarkerChar && !hasFollowingMarkerChar) {
                return index
            }
            if (gluedFallbackIndex == -1) {
                gluedFallbackIndex = index
            }
        }
        index = indexOf(marker, index + 1)
    }
    return gluedFallbackIndex
}

// Single forward pass over delimiter runs, maintaining a stack of not-yet-closed opener run
// lengths seen after startIndex (CommonMark-style delimiter matching: an opener is closed by the
// most recently seen still-open run first). Each character is visited once — this must stay O(n)
// per call, since it is invoked from an O(n) outer scan; rebuilding the opener stack from scratch
// for every candidate (as an earlier version of this function did) makes the whole thing O(n^2)
// for any note with several bold/italic spans.
private fun String.indexOfRunClosingMarker(
    markerChar: Char,
    markerLength: Int,
    startIndex: Int,
    endIndex: Int,
    allowTrailingWhitespace: Boolean,
    matcher: MarkdownInlineMatcher,
    protectStrike: Boolean = true,
): Int {
    val openStack = mutableListOf<Int>()
    var gluedFallbackIndex = -1
    var openingFallbackIndex = -1
    var currentIndex = startIndex
    var protectedEnd = -1
    while (currentIndex < endIndex) {
        if (this[currentIndex] != markerChar) {
            // Balanced wrappers own their contents. Literal code/link markers and nested
            // underline tags cannot close a formatting wrapper outside them.
            val nestedEnd = matcher.protectedWrapperEnd(currentIndex, includeStrike = markerChar == '*' && protectStrike, endIndex, includeEmphasis = markerChar == '~')
            if (nestedEnd > currentIndex) {
                currentIndex = nestedEnd
                protectedEnd = nestedEnd
                continue
            }
            currentIndex++
            continue
        }

        val runStart = currentIndex
        while (currentIndex < endIndex && this[currentIndex] == markerChar) {
            currentIndex++
        }
        val runEnd = currentIndex
        val runLength = runEnd - runStart
        val prevChar = getOrNull(runStart - 1)
        val nextChar = getOrNull(runEnd)
        val editingClose = allowTrailingWhitespace && isEditingWhitespaceClose(runStart, runEnd)
        val previousPunctuation = prevChar?.isMarkdownPunctuation() == true
        val nextPunctuation = nextChar?.isMarkdownPunctuation() == true
        val betweenPunctuation = previousPunctuation && nextPunctuation
        val emptyWrapperClose = runStart == startIndex && getOrNull(startIndex - 1) == markerChar && runLength == markerLength
        // An exact adjacent pair is editor scaffolding, not an opener for a later span.
        // Closing it here prevents empty formatting from consuming another wrapper's opener.
        if (emptyWrapperClose) return runStart
        val canClose =
            (
                prevChar != null && !prevChar.isWhitespace() &&
                    (!previousPunctuation || nextChar == null || nextChar.isWhitespace() || nextPunctuation)
            ) || editingClose || (markerChar == '~' && runStart == protectedEnd)
        val canOpen =
            nextChar != null && !nextChar.isWhitespace() &&
                (!nextPunctuation || prevChar == null || prevChar.isWhitespace() || previousPunctuation)

        // The remainder of a leading run belongs to nested emphasis. For example, when
        // trying bold at the start of ***one**two*, the third star opens italic and must
        // consume a closer before bold can close. Empty symmetric wrappers stay eligible
        // for the fallback below so toolbar scaffolding can still be rendered.
        if (runStart == startIndex && getOrNull(startIndex - 1) == markerChar && canOpen && runLength != markerLength) {
            openStack.add(runLength)
            continue
        }

        if (canOpen && canClose && openStack.isEmpty() && runLength > markerLength && betweenPunctuation) {
            // Between tags, a longer run can either end this span or open a nested one. Prefer
            // the nested match when it has its own closer; retain the split-run alternative.
            if (openingFallbackIndex == -1) openingFallbackIndex = runStart
            openStack.add(runLength)
            continue
        }

        if (canClose) {
            var remaining = runLength
            while (remaining > 0 && openStack.isNotEmpty()) {
                val lastOpener = openStack.last()
                if (lastOpener <= remaining) {
                    openStack.removeAt(openStack.lastIndex)
                    remaining -= lastOpener
                } else {
                    openStack[openStack.lastIndex] = lastOpener - remaining
                    remaining = 0
                }
            }
            if (remaining >= markerLength) {
                val matchedIndex = runEnd - remaining
                val consumedBeforeMatch = runLength - remaining
                // A run starting exactly at startIndex that's immediately preceded by another
                // asterisk is a direct continuation of the opener's own run (e.g. the "**" in an
                // empty "****" wrapper) — glued, same as leftover consumed by a nested opener.
                val hasPreceding =
                    consumedBeforeMatch > 0 ||
                        (runStart == startIndex && getOrNull(startIndex - 1) == markerChar)
                val hasFollowing = remaining > markerLength
                if (!hasPreceding && !hasFollowing) {
                    return matchedIndex
                }
                if (gluedFallbackIndex == -1) {
                    gluedFallbackIndex = matchedIndex
                }
            } else if (remaining > 0 && canOpen) {
                // A shorter run cannot close this wrapper, but can open nested emphasis after
                // another closing tag: **<u>*Word *</u>*next *** must leave the final two stars
                // for bold after the new italic span has consumed its own closing star.
                openStack.add(remaining)
            }
        } else if (canOpen) {
            openStack.add(runLength)
        }
    }
    return if (gluedFallbackIndex >= 0) gluedFallbackIndex else openingFallbackIndex
}

// A note can be saved with spaces still inside its active formatting wrappers. Preview, saved
// rendering and interaction mapping opt into the same bounded whitespace rule. Do not accept a
// later word's opening marker as a whitespace close, or a newline immediately before the close.
private fun String.isEditingWhitespaceClose(
    markerStart: Int,
    markerEnd: Int,
): Boolean {
    val preceding = getOrNull(markerStart - 1)
    val following = getOrNull(markerEnd)
    return (preceding == ' ' || preceding == '\t') &&
        (following == null || following.isWhitespace() || following in "*~`" || startsWith("</u>", markerEnd, ignoreCase = true))
}

internal fun String.isValidOpening(
    index: Int,
    markerLength: Int,
): Boolean {
    val nextChar = getOrNull(index + markerLength)
    return nextChar != null && !nextChar.isWhitespace()
}

private fun Char.isMarkdownPunctuation(): Boolean =
    when (category) {
        CharCategory.CONNECTOR_PUNCTUATION, CharCategory.DASH_PUNCTUATION,
        CharCategory.START_PUNCTUATION, CharCategory.END_PUNCTUATION,
        CharCategory.INITIAL_QUOTE_PUNCTUATION, CharCategory.FINAL_QUOTE_PUNCTUATION,
        CharCategory.OTHER_PUNCTUATION, CharCategory.MATH_SYMBOL,
        CharCategory.CURRENCY_SYMBOL, CharCategory.MODIFIER_SYMBOL, CharCategory.OTHER_SYMBOL,
        -> true
        else -> false
    }
