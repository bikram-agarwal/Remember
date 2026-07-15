package dev.bikram.remember.ui.settings

private val markdownHorizontalRuleLine = Regex("""^\s*([-*_])(?:\s*\1){2,}\s*$""")

private fun trimTrailingHorizontalRulesAndBlankLines(text: String): String {
    val lines = text.lines().toMutableList()
    while (lines.isNotEmpty()) {
        val lastLine = lines.last()
        if (lastLine.isBlank() || markdownHorizontalRuleLine.matches(lastLine)) {
            lines.removeAt(lines.lastIndex)
        } else {
            break
        }
    }
    return lines.joinToString("\n")
}

fun splitChangelogIntoPages(markdown: String): List<String> {
    val trimmed = markdown.trimEnd()
    if (trimmed.isEmpty()) return listOf("")
    val chunks = mutableListOf<MutableList<String>>()
    var current = mutableListOf<String>()
    for (line in trimmed.lines()) {
        val isTopLevelReleaseHeading = line.startsWith("## ") && !line.startsWith("###")
        if (isTopLevelReleaseHeading && current.isNotEmpty()) {
            chunks.add(current)
            current = mutableListOf()
        }
        current.add(line)
    }
    if (current.isNotEmpty()) chunks.add(current)
    return chunks.map { chunk -> trimTrailingHorizontalRulesAndBlankLines(chunk.joinToString("\n")) }
}
