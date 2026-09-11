package dev.bikram.remember.ui.common

internal fun String.indexOfMarkdownClosingMarker(
    marker: String,
    startIndex: Int,
    allowTrailingWhitespace: Boolean = false,
): Int =
    if (marker[0] == '*') {
        indexOfAsteriskClosingMarker(marker.length, startIndex, allowTrailingWhitespace)
    } else {
        indexOfNonAsteriskClosingMarker(marker, startIndex, allowTrailingWhitespace)
    }

private fun String.indexOfNonAsteriskClosingMarker(
    marker: String,
    startIndex: Int,
    allowTrailingWhitespace: Boolean,
): Int {
    val markerChar = marker[0]
    var index = indexOf(marker, startIndex)
    var gluedFallbackIndex = -1
    while (index != -1) {
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

// Single forward pass over asterisk runs, maintaining a stack of not-yet-closed opener run
// lengths seen after startIndex (CommonMark-style delimiter matching: an opener is closed by the
// most recently seen still-open run first). Each character is visited once — this must stay O(n)
// per call, since it is invoked from an O(n) outer scan; rebuilding the opener stack from scratch
// for every candidate (as an earlier version of this function did) makes the whole thing O(n^2)
// for any note with several bold/italic spans.
private fun String.indexOfAsteriskClosingMarker(
    markerLength: Int,
    startIndex: Int,
    allowTrailingWhitespace: Boolean,
): Int {
    val openStack = mutableListOf<Int>()
    var gluedFallbackIndex = -1
    var currentIndex = startIndex
    while (currentIndex < length) {
        if (this[currentIndex] != '*') {
            currentIndex++
            continue
        }

        val runStart = currentIndex
        while (currentIndex < length && this[currentIndex] == '*') {
            currentIndex++
        }
        val runEnd = currentIndex
        val runLength = runEnd - runStart
        val prevChar = getOrNull(runStart - 1)
        val nextChar = getOrNull(runEnd)
        val editingClose = allowTrailingWhitespace && isEditingWhitespaceClose(runStart, runEnd)
        val canClose = (prevChar != null && !prevChar.isWhitespace()) || editingClose
        val canOpen = nextChar != null && !nextChar.isWhitespace()

        // The remainder of a leading run belongs to nested emphasis. For example, when
        // trying bold at the start of ***one**two*, the third star opens italic and must
        // consume a closer before bold can close. Empty symmetric wrappers stay eligible
        // for the fallback below so toolbar scaffolding can still be rendered.
        if (runStart == startIndex && getOrNull(startIndex - 1) == '*' && canOpen && runLength != markerLength) {
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
                        (runStart == startIndex && getOrNull(startIndex - 1) == '*')
                val hasFollowing = remaining > markerLength
                if (!hasPreceding && !hasFollowing) {
                    return matchedIndex
                }
                if (gluedFallbackIndex == -1) {
                    gluedFallbackIndex = matchedIndex
                }
            }
        } else if (canOpen) {
            openStack.add(runLength)
        }
    }
    return gluedFallbackIndex
}

// While editing, a space before a closing delimiter is an intermediate typing state.
// Keep that wrapper visible as formatting without accepting a later word's opening marker
// as its close, or allowing emphasis to continue across a newline. Saved rendering stays strict.
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
