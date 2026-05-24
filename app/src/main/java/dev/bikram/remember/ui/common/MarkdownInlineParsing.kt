package dev.bikram.remember.ui.common

internal fun String.indexOfMarkdownClosingMarker(
    marker: String,
    startIndex: Int,
): Int {
    var index = indexOf(marker, startIndex)
    while (index != -1) {
        val markerChar = marker[0]
        val hasPrecedingMarkerChar = index > 0 && this[index - 1] == markerChar
        val hasFollowingMarkerChar = index + marker.length < length && this[index + marker.length] == markerChar
        if (!hasPrecedingMarkerChar && !hasFollowingMarkerChar) {
            val prevChar = getOrNull(index - 1)
            if (prevChar != null && !prevChar.isWhitespace()) {
                return index
            }
        }
        index = indexOf(marker, index + 1)
    }
    return -1
}
