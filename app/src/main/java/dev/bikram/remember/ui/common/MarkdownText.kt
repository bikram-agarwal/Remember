package dev.bikram.remember.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bikram.remember.ui.feedback.tapSoundClickable

@Composable
internal fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onChecklistToggle: ((lineIndex: Int, checked: Boolean) -> Unit)? = null,
) {
    val styler = rememberMarkdownStyler(style)
    if (maxLines != Int.MAX_VALUE) {
        val preview =
            remember(markdown, styler) {
                styler.markdownPreviewAnnotatedString(markdown = markdown)
            }
        MarkdownInlineText(
            text = preview,
            style = style,
            maxLines = maxLines,
            overflow = overflow,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val lines = markdown.lines()
        var lineIndex = 0
        while (lineIndex < lines.size) {
            val quoteLines = mutableListOf<String>()
            while (lineIndex < lines.size) {
                val quoteContent = styler.quoteContentForLine(lines[lineIndex]) ?: break
                quoteLines.add(quoteContent)
                lineIndex++
            }
            if (quoteLines.isNotEmpty()) {
                MarkdownQuoteBlock(
                    lines = quoteLines,
                    style = style,
                    styler = styler,
                )
            } else if (MarkdownCodeFenceLineRegex.matches(lines[lineIndex])) {
                val codeLines = mutableListOf<String>()
                lineIndex++
                while (lineIndex < lines.size && !MarkdownCodeFenceLineRegex.matches(lines[lineIndex])) {
                    codeLines.add(lines[lineIndex])
                    lineIndex++
                }
                if (lineIndex < lines.size && MarkdownCodeFenceLineRegex.matches(lines[lineIndex])) {
                    lineIndex++
                }
                MarkdownCodeBlock(code = codeLines.joinToString("\n"), style = style, styler = styler)
            } else {
                MarkdownLine(
                    line = lines[lineIndex],
                    lineIndex = lineIndex,
                    style = style,
                    styler = styler,
                    onChecklistToggle = onChecklistToggle,
                )
                lineIndex++
            }
        }
    }
}

@Composable
private fun MarkdownLine(
    line: String,
    lineIndex: Int,
    style: TextStyle,
    styler: MarkdownStyler,
    onChecklistToggle: ((lineIndex: Int, checked: Boolean) -> Unit)?,
) {
    val headingMatch = MarkdownHeadingLineRegex.matchEntire(line)
    if (headingMatch != null) {
        val headingLevel = headingMatch.groupValues[1].length
        MarkdownInlineText(
            text = styler.markdownInlineAnnotatedString(headingMatch.groupValues[2]),
            style = styler.headingTextStyle(headingLevel = headingLevel),
        )
        return
    }

    val checklistMatch = MarkdownChecklistLineRegex.matchEntire(line)
    if (checklistMatch != null) {
        val checked = checklistMatch.groupValues[2].equals("x", ignoreCase = true)
        Row(
            modifier = Modifier.padding(start = styler.listStartPadding(checklistMatch.groupValues[1], baseIndent = 0.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RememberMaterialRoundedSymbol(
                name = if (checked) "check_box" else "check_box_outline_blank",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                weight = FontWeight.Medium,
                modifier =
                    if (onChecklistToggle != null) {
                        Modifier.tapSoundClickable(role = Role.Checkbox) {
                            onChecklistToggle(lineIndex, !checked)
                        }
                    } else {
                        Modifier
                    },
            )
            Spacer(Modifier.width(8.dp))
            MarkdownInlineText(
                text = styler.markdownInlineAnnotatedString(checklistMatch.groupValues[3]),
                style = style,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    val bulletMatch = MarkdownBulletLineRegex.matchEntire(line)
    if (bulletMatch != null) {
        Row(
            modifier = Modifier.padding(start = styler.listStartPadding(bulletMatch.groupValues[1], baseIndent = 16.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "\u2022",
                style = style,
            )
            Spacer(Modifier.width(8.dp))
            MarkdownInlineText(
                text = styler.markdownInlineAnnotatedString(bulletMatch.groupValues[2]),
                style = style,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    val numberedMatch = MarkdownNumberedLineRegex.matchEntire(line)
    if (numberedMatch != null) {
        Row(
            modifier = Modifier.padding(start = styler.listStartPadding(numberedMatch.groupValues[1], baseIndent = 8.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${numberedMatch.groupValues[2]}.",
                style = style,
            )
            Spacer(Modifier.width(8.dp))
            MarkdownInlineText(
                text = styler.markdownInlineAnnotatedString(numberedMatch.groupValues[3]),
                style = style,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    MarkdownInlineText(
        text = styler.markdownInlineAnnotatedString(line),
        style = style,
    )
}

@Composable
private fun MarkdownInlineText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
    )
}

@Composable
private fun MarkdownQuoteBlock(
    lines: List<String>,
    style: TextStyle,
    styler: MarkdownStyler,
) {
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .heightIn(min = 24.dp)
                    .background(styler.quoteBarColor),
        )
        Column(
            modifier = Modifier.padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            lines.forEach { quoteLine ->
                MarkdownInlineText(
                    text = styler.markdownInlineAnnotatedString(quoteLine),
                    style =
                        style.copy(
                            color = styler.quoteColor,
                            fontStyle = FontStyle.Italic,
                        ),
                )
            }
        }
    }
}

@Composable
private fun MarkdownCodeBlock(
    code: String,
    style: TextStyle,
    styler: MarkdownStyler,
) {
    Surface(
        color = styler.codeBackground,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = code,
            style =
                style.copy(
                    color = styler.quoteColor,
                    fontFamily = styler.codeBlockSpanStyle.fontFamily,
                ),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
