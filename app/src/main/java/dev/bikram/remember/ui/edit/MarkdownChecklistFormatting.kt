@file:Suppress("ktlint:standard:function-expression-body")

package dev.bikram.remember.ui.edit

import dev.bikram.remember.ui.common.MarkdownBlockKind
import dev.bikram.remember.ui.common.parseMarkdownLines

internal fun String.withChecklistLineToggled(
    lineIndex: Int,
    checked: Boolean,
): String {
    return withChecklistStatesToggled(checked, lineIndex)
}

internal fun String.withAllChecklistLinesToggled(checked: Boolean): String {
    return withChecklistStatesToggled(checked, lineIndex = null)
}

private fun String.withChecklistStatesToggled(
    checked: Boolean,
    lineIndex: Int?,
): String {
    val lines = parseMarkdownLines(this)
    return StringBuilder(this)
        .apply {
            for ((index, line) in lines.withIndex()) {
                if (line.kind != MarkdownBlockKind.Checklist || (lineIndex != null && index != lineIndex) || line.checked == checked) continue
                // Completion owns only the checkbox marker. Wrapping the label in more Markdown
                // can break nested formatting and cannot distinguish authored strike from completion.
                setCharAt(line.start + line.indent.length + 3, if (checked) 'x' else ' ')
            }
        }.toString()
}
