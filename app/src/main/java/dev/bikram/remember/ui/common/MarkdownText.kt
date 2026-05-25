package dev.bikram.remember.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bikram.remember.ui.feedback.tapSoundClickable

private val MarkdownLinkRegex = Regex("""\[([^\]]+)]\(([^)]+)\)""")

internal data class MarkdownTextTap(
    val markdownOffset: Int,
)

internal data class MarkdownLinkInteraction(
    val url: String,
    val text: String,
    val markdownOffset: Int,
    val textStartOffset: Int,
    val textEndOffset: Int,
)

@Composable
internal fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onChecklistToggle: ((lineIndex: Int, checked: Boolean) -> Unit)? = null,
    onTextTap: ((MarkdownTextTap) -> Unit)? = null,
    onTextLongPress: ((MarkdownTextTap) -> Unit)? = null,
    onLinkClick: ((MarkdownLinkInteraction) -> Unit)? = null,
    onLinkLongPress: ((MarkdownLinkInteraction) -> Unit)? = null,
) {
    val styler = rememberMarkdownStyler(style)
    val includeLinkAnnotations = onLinkClick == null && onLinkLongPress == null
    if (maxLines != Int.MAX_VALUE) {
        val previewSource = remember(markdown) { markdownPreviewSource(markdown) }
        val preview =
            remember(previewSource.source, styler, includeLinkAnnotations) {
                styler.markdownInlineAnnotatedString(
                    source = previewSource.source,
                    includeLinkAnnotations = includeLinkAnnotations,
                )
            }
        MarkdownInlineText(
            text = preview,
            style = style,
            maxLines = maxLines,
            overflow = overflow,
            modifier = modifier,
            source = previewSource.source,
            sourceOffset = 0,
            sourceOffsetByIndex = previewSource.sourceOffsetByIndex,
            onTextTap = onTextTap,
            onTextLongPress = onTextLongPress,
            onLinkClick = onLinkClick,
            onLinkLongPress = onLinkLongPress,
        )
        return
    }

    val lineStartOffsets = remember(markdown) { markdown.lineStartOffsets() }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val lines = markdown.lines()
        var lineIndex = 0
        while (lineIndex < lines.size) {
            val quoteLines = mutableListOf<String>()
            val quoteLineContentOffsets = mutableListOf<Int>()
            while (lineIndex < lines.size) {
                val quoteMatch = MarkdownQuoteLineRegex.matchEntire(lines[lineIndex]) ?: break
                quoteLines.add(quoteMatch.groupValues[1])
                quoteLineContentOffsets.add(
                    lineStartOffsets.getOrElse(lineIndex) { markdown.length } +
                        (quoteMatch.groups[1]?.range?.first ?: 0),
                )
                lineIndex++
            }
            if (quoteLines.isNotEmpty()) {
                MarkdownQuoteBlock(
                    lines = quoteLines,
                    lineContentOffsets = quoteLineContentOffsets,
                    style = style,
                    styler = styler,
                    includeLinkAnnotations = includeLinkAnnotations,
                    onTextTap = onTextTap,
                    onTextLongPress = onTextLongPress,
                    onLinkClick = onLinkClick,
                    onLinkLongPress = onLinkLongPress,
                )
            } else if (lineIndex < lines.size && MarkdownCodeFenceLineRegex.matches(lines[lineIndex])) {
                val codeBlockStartOffset = lineStartOffsets.getOrElse(lineIndex) { markdown.length }
                val codeLines = mutableListOf<String>()
                lineIndex++
                while (lineIndex < lines.size && !MarkdownCodeFenceLineRegex.matches(lines[lineIndex])) {
                    codeLines.add(lines[lineIndex])
                    lineIndex++
                }
                if (lineIndex < lines.size && MarkdownCodeFenceLineRegex.matches(lines[lineIndex])) {
                    lineIndex++
                }
                MarkdownCodeBlock(
                    code = codeLines.joinToString("\n"),
                    sourceOffset = codeBlockStartOffset,
                    style = style,
                    styler = styler,
                    onTextTap = onTextTap,
                    onTextLongPress = onTextLongPress,
                )
            } else {
                MarkdownLine(
                    line = lines[lineIndex],
                    lineIndex = lineIndex,
                    lineStartOffset = lineStartOffsets.getOrElse(lineIndex) { markdown.length },
                    style = style,
                    styler = styler,
                    includeLinkAnnotations = includeLinkAnnotations,
                    onChecklistToggle = onChecklistToggle,
                    onTextTap = onTextTap,
                    onTextLongPress = onTextLongPress,
                    onLinkClick = onLinkClick,
                    onLinkLongPress = onLinkLongPress,
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
    lineStartOffset: Int,
    style: TextStyle,
    styler: MarkdownStyler,
    includeLinkAnnotations: Boolean,
    onChecklistToggle: ((lineIndex: Int, checked: Boolean) -> Unit)?,
    onTextTap: ((MarkdownTextTap) -> Unit)?,
    onTextLongPress: ((MarkdownTextTap) -> Unit)?,
    onLinkClick: ((MarkdownLinkInteraction) -> Unit)?,
    onLinkLongPress: ((MarkdownLinkInteraction) -> Unit)?,
) {
    val headingMatch = MarkdownHeadingLineRegex.matchEntire(line)
    if (headingMatch != null) {
        val headingLevel = headingMatch.groupValues[1].length
        val contentRange = headingMatch.groups[2]?.range
        MarkdownInlineText(
            text =
                styler.markdownInlineAnnotatedString(
                    source = headingMatch.groupValues[2],
                    includeLinkAnnotations = includeLinkAnnotations,
                ),
            style = styler.headingTextStyle(headingLevel = headingLevel),
            modifier = Modifier.fillMaxWidth(),
            source = headingMatch.groupValues[2],
            sourceOffset = lineStartOffset + (contentRange?.first ?: 0),
            onTextTap = onTextTap,
            onTextLongPress = onTextLongPress,
            onLinkClick = onLinkClick,
            onLinkLongPress = onLinkLongPress,
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
            val contentRange = checklistMatch.groups[3]?.range
            MarkdownInlineText(
                text =
                    styler.markdownInlineAnnotatedString(
                        source = checklistMatch.groupValues[3],
                        includeLinkAnnotations = includeLinkAnnotations,
                    ),
                style = if (checked) style.copy(textDecoration = TextDecoration.LineThrough) else style,
                modifier = Modifier.weight(1f),
                source = checklistMatch.groupValues[3],
                sourceOffset = lineStartOffset + (contentRange?.first ?: 0),
                onTextTap = onTextTap,
                onTextLongPress = onTextLongPress,
                onLinkClick = onLinkClick,
                onLinkLongPress = onLinkLongPress,
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
            val contentRange = bulletMatch.groups[2]?.range
            MarkdownInlineText(
                text =
                    styler.markdownInlineAnnotatedString(
                        source = bulletMatch.groupValues[2],
                        includeLinkAnnotations = includeLinkAnnotations,
                    ),
                style = style,
                modifier = Modifier.weight(1f),
                source = bulletMatch.groupValues[2],
                sourceOffset = lineStartOffset + (contentRange?.first ?: 0),
                onTextTap = onTextTap,
                onTextLongPress = onTextLongPress,
                onLinkClick = onLinkClick,
                onLinkLongPress = onLinkLongPress,
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
            val contentRange = numberedMatch.groups[3]?.range
            MarkdownInlineText(
                text =
                    styler.markdownInlineAnnotatedString(
                        source = numberedMatch.groupValues[3],
                        includeLinkAnnotations = includeLinkAnnotations,
                    ),
                style = style,
                modifier = Modifier.weight(1f),
                source = numberedMatch.groupValues[3],
                sourceOffset = lineStartOffset + (contentRange?.first ?: 0),
                onTextTap = onTextTap,
                onTextLongPress = onTextLongPress,
                onLinkClick = onLinkClick,
                onLinkLongPress = onLinkLongPress,
            )
        }
        return
    }

    MarkdownInlineText(
        text =
            styler.markdownInlineAnnotatedString(
                source = line,
                includeLinkAnnotations = includeLinkAnnotations,
            ),
        style = style,
        modifier = Modifier.fillMaxWidth(),
        source = line,
        sourceOffset = lineStartOffset,
        onTextTap = onTextTap,
        onTextLongPress = onTextLongPress,
        onLinkClick = onLinkClick,
        onLinkLongPress = onLinkLongPress,
    )
}

@Composable
private fun MarkdownInlineText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    source: String = text.text,
    sourceOffset: Int = 0,
    sourceOffsetByIndex: IntArray? = null,
    onTextTap: ((MarkdownTextTap) -> Unit)? = null,
    onTextLongPress: ((MarkdownTextTap) -> Unit)? = null,
    onLinkClick: ((MarkdownLinkInteraction) -> Unit)? = null,
    onLinkLongPress: ((MarkdownLinkInteraction) -> Unit)? = null,
) {
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val inlineInteractionMap =
        remember(source, sourceOffset, sourceOffsetByIndex) {
            MarkdownInlineInteractionBuilder(
                source = source,
                sourceOffset = sourceOffset,
                sourceOffsetByIndex = sourceOffsetByIndex,
            ).build()
        }
    val interactionModifier =
        if (onTextTap != null || onTextLongPress != null || onLinkClick != null || onLinkLongPress != null) {
            Modifier.pointerInput(inlineInteractionMap, onTextTap, onTextLongPress, onLinkClick, onLinkLongPress) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val visibleOffset =
                            textLayoutResult
                                ?.getOffsetForPosition(tapOffset)
                                ?: return@detectTapGestures
                        when (val interaction = inlineInteractionMap.interactionAt(visibleOffset)) {
                            is MarkdownInlineInteraction.Link -> onLinkClick?.invoke(interaction.link)
                            is MarkdownInlineInteraction.Text -> {
                                onTextTap?.invoke(MarkdownTextTap(interaction.markdownOffset))
                            }
                        }
                    },
                    onLongPress = { pressOffset ->
                        val visibleOffset =
                            textLayoutResult
                                ?.getOffsetForPosition(pressOffset)
                                ?: return@detectTapGestures
                        when (val interaction = inlineInteractionMap.interactionAt(visibleOffset)) {
                            is MarkdownInlineInteraction.Link -> onLinkLongPress?.invoke(interaction.link)
                            is MarkdownInlineInteraction.Text ->
                                onTextLongPress?.invoke(MarkdownTextTap(interaction.markdownOffset))
                        }
                    },
                )
            }
        } else {
            Modifier
        }
    Text(
        text = text,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { textLayoutResult = it },
        modifier = modifier.then(interactionModifier),
    )
}

@Composable
private fun MarkdownQuoteBlock(
    lines: List<String>,
    lineContentOffsets: List<Int>,
    style: TextStyle,
    styler: MarkdownStyler,
    includeLinkAnnotations: Boolean,
    onTextTap: ((MarkdownTextTap) -> Unit)?,
    onTextLongPress: ((MarkdownTextTap) -> Unit)?,
    onLinkClick: ((MarkdownLinkInteraction) -> Unit)?,
    onLinkLongPress: ((MarkdownLinkInteraction) -> Unit)?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
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
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            lines.forEachIndexed { quoteLineIndex, quoteLine ->
                val lineStartOffset = lineContentOffsets.getOrElse(quoteLineIndex) { 0 }
                MarkdownInlineText(
                    text =
                        styler.markdownInlineAnnotatedString(
                            source = quoteLine,
                            includeLinkAnnotations = includeLinkAnnotations,
                        ),
                    style =
                        style.copy(
                            color = styler.quoteColor,
                            fontStyle = FontStyle.Italic,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                    source = quoteLine,
                    sourceOffset = lineStartOffset,
                    onTextTap = onTextTap,
                    onTextLongPress = onTextLongPress,
                    onLinkClick = onLinkClick,
                    onLinkLongPress = onLinkLongPress,
                )
            }
        }
    }
}

@Composable
private fun MarkdownCodeBlock(
    code: String,
    sourceOffset: Int,
    style: TextStyle,
    styler: MarkdownStyler,
    onTextTap: ((MarkdownTextTap) -> Unit)?,
    onTextLongPress: ((MarkdownTextTap) -> Unit)?,
) {
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    Surface(
        color = styler.codeBackground,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = code,
            style =
                style.copy(
                    color = styler.quoteColor,
                    fontFamily = styler.codeBlockSpanStyle.fontFamily,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .pointerInput(code, sourceOffset, onTextTap, onTextLongPress) {
                        if (onTextTap == null && onTextLongPress == null) {
                            return@pointerInput
                        }
                        detectTapGestures(
                            onTap = { tapOffset ->
                                val visibleOffset =
                                    textLayoutResult
                                        ?.getOffsetForPosition(tapOffset)
                                        ?: return@detectTapGestures
                                onTextTap?.invoke(MarkdownTextTap(sourceOffset + visibleOffset))
                            },
                            onLongPress = { pressOffset ->
                                val visibleOffset =
                                    textLayoutResult
                                        ?.getOffsetForPosition(pressOffset)
                                        ?: return@detectTapGestures
                                onTextLongPress?.invoke(MarkdownTextTap(sourceOffset + visibleOffset))
                            },
                        )
                    },
            onTextLayout = { textLayoutResult = it },
        )
    }
}

private sealed interface MarkdownInlineInteraction {
    data class Text(
        val markdownOffset: Int,
    ) : MarkdownInlineInteraction

    data class Link(
        val link: MarkdownLinkInteraction,
    ) : MarkdownInlineInteraction
}

private data class MarkdownInlineLinkRange(
    val visibleStart: Int,
    val visibleEnd: Int,
    val link: MarkdownLinkInteraction,
)

private data class MarkdownInlineInteractionMap(
    val sourceOffsetByVisibleOffset: IntArray,
    val links: List<MarkdownInlineLinkRange>,
) {
    fun interactionAt(visibleOffset: Int): MarkdownInlineInteraction {
        val boundedVisibleOffset = visibleOffset.coerceIn(0, sourceOffsetByVisibleOffset.lastIndex)
        val linkRange =
            links.firstOrNull { candidate ->
                boundedVisibleOffset >= candidate.visibleStart && boundedVisibleOffset < candidate.visibleEnd
            }
        if (linkRange != null) {
            return MarkdownInlineInteraction.Link(linkRange.link)
        }
        return MarkdownInlineInteraction.Text(sourceOffsetByVisibleOffset[boundedVisibleOffset])
    }
}

private class MarkdownInlineInteractionBuilder(
    private val source: String,
    private val sourceOffset: Int,
    private val sourceOffsetByIndex: IntArray? = null,
) {
    private val sourceOffsets = mutableListOf<Int>()
    private val links = mutableListOf<MarkdownInlineLinkRange>()

    fun build(): MarkdownInlineInteractionMap {
        appendInlineMarkdown(sourceSegment = source, segmentOffset = 0)
        sourceOffsets.add(sourceOffsetAt(source.length))
        return MarkdownInlineInteractionMap(
            sourceOffsetByVisibleOffset = sourceOffsets.toIntArray(),
            links = links,
        )
    }

    private fun appendInlineMarkdown(
        sourceSegment: String,
        segmentOffset: Int,
    ) {
        var currentIndex = 0
        while (currentIndex < sourceSegment.length) {
            val linkMatch = MarkdownLinkRegex.find(sourceSegment, currentIndex)
            if (linkMatch != null && linkMatch.range.first == currentIndex) {
                val linkTextRange = linkMatch.groups[1]?.range
                val visibleStart = sourceOffsets.size
                if (linkTextRange != null) {
                    appendInlineMarkdown(
                        sourceSegment = linkMatch.groupValues[1],
                        segmentOffset = segmentOffset + linkTextRange.first,
                    )
                    val visibleEnd = sourceOffsets.size
                    if (visibleEnd > visibleStart) {
                        links.add(
                            MarkdownInlineLinkRange(
                                visibleStart = visibleStart,
                                visibleEnd = visibleEnd,
                                link =
                                    MarkdownLinkInteraction(
                                        url = linkMatch.groupValues[2].withHttpScheme(),
                                        text = linkMatch.groupValues[1],
                                        markdownOffset = sourceOffsetAt(segmentOffset + linkMatch.range.first),
                                        textStartOffset = sourceOffsetAt(segmentOffset + linkTextRange.first),
                                        textEndOffset = sourceOffsetAt(segmentOffset + linkTextRange.last + 1),
                                    ),
                            ),
                        )
                    }
                }
                currentIndex = linkMatch.range.last + 1
                continue
            }

            val inlineCodeClose = sourceSegment.indexOfMarkdownClosingMarker("`", currentIndex + 1)
            if (sourceSegment.startsWith("`", currentIndex) && sourceSegment.isValidOpening(currentIndex, 1) && inlineCodeClose > currentIndex) {
                appendPlainTextRange(
                    rangeStart = currentIndex + 1,
                    rangeEnd = inlineCodeClose,
                    segmentOffset = segmentOffset,
                )
                currentIndex = inlineCodeClose + 1
                continue
            }

            val underlineClose = sourceSegment.indexOf("</u>", currentIndex + 3, ignoreCase = true)
            if (sourceSegment.startsWith("<u>", currentIndex, ignoreCase = true) && underlineClose > currentIndex) {
                appendInlineMarkdown(
                    sourceSegment = sourceSegment.substring(currentIndex + 3, underlineClose),
                    segmentOffset = segmentOffset + currentIndex + 3,
                )
                currentIndex = underlineClose + 4
                continue
            }

            val strikeClose = sourceSegment.indexOfMarkdownClosingMarker("~~", currentIndex + 2)
            if (sourceSegment.startsWith("~~", currentIndex) && sourceSegment.isValidOpening(currentIndex, 2) && strikeClose > currentIndex) {
                appendInlineMarkdown(
                    sourceSegment = sourceSegment.substring(currentIndex + 2, strikeClose),
                    segmentOffset = segmentOffset + currentIndex + 2,
                )
                currentIndex = strikeClose + 2
                continue
            }

            val boldItalicClose = sourceSegment.indexOfMarkdownClosingMarker("***", currentIndex + 3)
            if (sourceSegment.startsWith("***", currentIndex) && sourceSegment.isValidOpening(currentIndex, 3) && boldItalicClose > currentIndex) {
                appendInlineMarkdown(
                    sourceSegment = sourceSegment.substring(currentIndex + 3, boldItalicClose),
                    segmentOffset = segmentOffset + currentIndex + 3,
                )
                currentIndex = boldItalicClose + 3
                continue
            }

            val boldClose = sourceSegment.indexOfMarkdownClosingMarker("**", currentIndex + 2)
            if (sourceSegment.startsWith("**", currentIndex) && sourceSegment.isValidOpening(currentIndex, 2) && boldClose > currentIndex) {
                appendInlineMarkdown(
                    sourceSegment = sourceSegment.substring(currentIndex + 2, boldClose),
                    segmentOffset = segmentOffset + currentIndex + 2,
                )
                currentIndex = boldClose + 2
                continue
            }

            val italicClose = sourceSegment.indexOfMarkdownClosingMarker("*", currentIndex + 1)
            if (sourceSegment.startsWith("*", currentIndex) && sourceSegment.isValidOpening(currentIndex, 1) && italicClose > currentIndex) {
                appendInlineMarkdown(
                    sourceSegment = sourceSegment.substring(currentIndex + 1, italicClose),
                    segmentOffset = segmentOffset + currentIndex + 1,
                )
                currentIndex = italicClose + 1
                continue
            }

            sourceOffsets.add(sourceOffsetAt(segmentOffset + currentIndex))
            currentIndex++
        }
    }

    private fun appendPlainTextRange(
        rangeStart: Int,
        rangeEnd: Int,
        segmentOffset: Int,
    ) {
        var currentIndex = rangeStart
        while (currentIndex < rangeEnd) {
            sourceOffsets.add(sourceOffsetAt(segmentOffset + currentIndex))
            currentIndex++
        }
    }

    private fun sourceOffsetAt(index: Int): Int {
        val offsets = sourceOffsetByIndex
        return if (offsets != null) {
            offsets[index.coerceIn(0, offsets.lastIndex)]
        } else {
            sourceOffset + index
        }
    }
}

private data class MarkdownPreviewSource(
    val source: String,
    val sourceOffsetByIndex: IntArray,
)

private fun markdownPreviewSource(markdown: String): MarkdownPreviewSource {
    val preview = StringBuilder()
    val offsets = mutableListOf<Int>()
    val lineStartOffsets = markdown.lineStartOffsets()
    val lines = markdown.lines()

    fun appendText(
        text: String,
        offset: Int,
    ) {
        text.forEach { character ->
            preview.append(character)
            offsets.add(offset.coerceIn(0, markdown.length))
        }
    }

    fun appendSourceRange(
        line: String,
        lineStartOffset: Int,
        range: IntRange,
    ) {
        for (sourceIndex in range) {
            preview.append(line[sourceIndex])
            offsets.add(lineStartOffset + sourceIndex)
        }
    }

    lines.forEachIndexed { lineIndex, line ->
        val lineStartOffset = lineStartOffsets.getOrElse(lineIndex) { markdown.length }
        if (lineIndex > 0) {
            preview.append('\n')
            offsets.add((lineStartOffset - 1).coerceIn(0, markdown.length))
        }
        appendPreviewLine(
            line = line,
            lineStartOffset = lineStartOffset,
            appendText = ::appendText,
            appendSourceRange = ::appendSourceRange,
        )
    }
    offsets.add(markdown.length)
    return MarkdownPreviewSource(
        source = preview.toString(),
        sourceOffsetByIndex = offsets.toIntArray(),
    )
}

private fun appendPreviewLine(
    line: String,
    lineStartOffset: Int,
    appendText: (String, Int) -> Unit,
    appendSourceRange: (String, Int, IntRange) -> Unit,
) {
    MarkdownHeadingLineRegex
        .matchEntire(line)
        ?.groups
        ?.get(2)
        ?.range
        ?.let { range ->
            appendSourceRange(line, lineStartOffset, range)
            return
        }
    MarkdownChecklistLineRegex.matchEntire(line)?.let { match ->
        val contentRange = match.groups[3]?.range ?: return@let
        val checked = match.groupValues[2].equals("x", ignoreCase = true)
        appendText(match.groupValues[1] + if (checked) "\u2611 " else "\u2610 ", lineStartOffset)
        if (checked && !match.groupValues[3].hasStrikethroughWrapper()) {
            appendText("~~", lineStartOffset + contentRange.first)
            appendSourceRange(line, lineStartOffset, contentRange)
            appendText("~~", lineStartOffset + contentRange.last + 1)
        } else {
            appendSourceRange(line, lineStartOffset, contentRange)
        }
        return
    }
    MarkdownBulletLineRegex.matchEntire(line)?.let { match ->
        val contentRange = match.groups[2]?.range ?: return@let
        appendText("  " + match.groupValues[1] + "\u2022 ", lineStartOffset)
        appendSourceRange(line, lineStartOffset, contentRange)
        return
    }
    MarkdownNumberedLineRegex.matchEntire(line)?.let { match ->
        val contentRange = match.groups[3]?.range ?: return@let
        appendText(" " + match.groupValues[1] + match.groupValues[2] + ". ", lineStartOffset)
        appendSourceRange(line, lineStartOffset, contentRange)
        return
    }
    MarkdownQuoteLineRegex
        .matchEntire(line)
        ?.groups
        ?.get(1)
        ?.range
        ?.let { range ->
            appendSourceRange(line, lineStartOffset, range)
            return
        }
    appendSourceRange(line, lineStartOffset, line.indices)
}

private fun String.hasStrikethroughWrapper(): Boolean {
    val contentStart = indexOfFirst { !it.isWhitespace() }.let { if (it < 0) return false else it }
    val contentEndExclusive = indexOfLast { !it.isWhitespace() } + 1
    return contentEndExclusive - contentStart >= 4 &&
        startsWith("~~", contentStart) &&
        substring(contentStart, contentEndExclusive).endsWith("~~")
}

private fun String.lineStartOffsets(): List<Int> {
    val offsets = mutableListOf(0)
    forEachIndexed { index, character ->
        if (character == '\n') {
            offsets.add(index + 1)
        }
    }
    return offsets
}

private fun String.withHttpScheme(): String =
    if (startsWith("http://") || startsWith("https://")) {
        this
    } else {
        "https://$this"
    }

private fun String.isValidOpening(
    index: Int,
    markerLength: Int,
): Boolean {
    val nextChar = getOrNull(index + markerLength)
    return nextChar != null && !nextChar.isWhitespace()
}
