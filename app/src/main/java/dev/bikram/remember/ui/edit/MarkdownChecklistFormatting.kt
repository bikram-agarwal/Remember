package dev.bikram.remember.ui.edit

private val MarkdownChecklistToggleRegex = Regex("""^(\s*- \[)[ xX](]\s+)(.*)$""")

internal fun String.withChecklistLineToggled(
    lineIndex: Int,
    checked: Boolean,
): String {
    val lines = lines().toMutableList()
    val line = lines.getOrNull(lineIndex) ?: return this
    val match = MarkdownChecklistToggleRegex.matchEntire(line) ?: return this
    val updatedContent =
        if (checked) {
            match.groupValues[3].withStrikethroughWrapper()
        } else {
            match.groupValues[3].withoutStrikethroughWrapper()
        }
    val updatedLine =
        match.groupValues[1] +
            (if (checked) "x" else " ") +
            match.groupValues[2] +
            updatedContent
    if (updatedLine == line) {
        return this
    }
    lines[lineIndex] = updatedLine
    return lines.joinToString("\n")
}

internal fun String.withAllChecklistLinesToggled(checked: Boolean): String {
    val lines =
        lines().map { line ->
            val match = MarkdownChecklistToggleRegex.matchEntire(line)
            if (match != null) {
                val updatedContent =
                    if (checked) {
                        match.groupValues[3].withStrikethroughWrapper()
                    } else {
                        match.groupValues[3].withoutStrikethroughWrapper()
                    }
                match.groupValues[1] +
                    (if (checked) "x" else " ") +
                    match.groupValues[2] +
                    updatedContent
            } else {
                line
            }
        }
    return lines.joinToString("\n")
}

private fun String.withStrikethroughWrapper(): String {
    if (isBlank() || hasStrikethroughWrapper()) return this
    return "~~$this~~"
}

private fun String.withoutStrikethroughWrapper(): String {
    if (!hasStrikethroughWrapper()) return this
    val leadingWhitespaceLength = indexOfFirst { !it.isWhitespace() }.let { if (it < 0) length else it }
    val trailingWhitespaceStart = indexOfLast { !it.isWhitespace() }.let { if (it < 0) 0 else it + 1 }
    val source = this
    return buildString {
        append(source.substring(0, leadingWhitespaceLength))
        append(source.substring(leadingWhitespaceLength + 2, trailingWhitespaceStart - 2))
        append(source.substring(trailingWhitespaceStart))
    }
}

private fun String.hasStrikethroughWrapper(): Boolean {
    val contentStart = indexOfFirst { !it.isWhitespace() }.let { if (it < 0) return false else it }
    val contentEndExclusive = indexOfLast { !it.isWhitespace() } + 1
    return contentEndExclusive - contentStart >= 4 &&
        startsWith("~~", contentStart) &&
        substring(contentStart, contentEndExclusive).endsWith("~~")
}
