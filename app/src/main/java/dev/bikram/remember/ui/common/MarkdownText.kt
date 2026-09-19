@file:Suppress("ktlint:standard:function-expression-body")

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import dev.bikram.remember.R
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.feedback.LocalHapticEnabled
import dev.bikram.remember.ui.feedback.appClickable
import dev.bikram.remember.ui.feedback.appCombinedClickable
import dev.bikram.remember.ui.feedback.performLongPressHaptic

private val MarkdownChecklistContinuationLineRegex = Regex("""^\s{4,}(.+)$""")
private const val MARKDOWN_PREVIEW_HORIZONTAL_RULE = "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500"

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
    onChecklistCheckAll: (() -> Unit)? = null,
    onChecklistUncheckAll: (() -> Unit)? = null,
    onTextTap: ((MarkdownTextTap) -> Unit)? = null,
    onTextLongPress: ((MarkdownTextTap) -> Unit)? = null,
    onLinkClick: ((MarkdownLinkInteraction) -> Unit)? = null,
    onLinkLongPress: ((MarkdownLinkInteraction) -> Unit)? = null,
) {
    val styler = rememberMarkdownStyler(style)
    val includeLinkAnnotations = onLinkClick == null && onLinkLongPress == null
    if (maxLines != Int.MAX_VALUE) {
        val preview = remember(markdown, styler, includeLinkAnnotations) { markdownCardPreview(markdown, styler, includeLinkAnnotations) }
        MarkdownInlineText(
            styler = styler,
            rendered = preview,
            style = style,
            maxLines = maxLines,
            overflow = overflow,
            modifier = modifier,
            onTextTap = onTextTap,
            onTextLongPress = onTextLongPress,
            onLinkClick = onLinkClick,
            onLinkLongPress = onLinkLongPress,
        )
        return
    }

    val blocks = remember(markdown) { parseMarkdownLines(markdown) }
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
                val quoteBlock = blocks[lineIndex].takeIf { it.kind == MarkdownBlockKind.Quote } ?: break
                quoteLines.add(markdown.substring(quoteBlock.contentStart, quoteBlock.end))
                quoteLineContentOffsets.add(
                    quoteBlock.contentStart,
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
            } else if (lineIndex < lines.size && blocks[lineIndex].kind == MarkdownBlockKind.CodeFence) {
                val codeBlockStartOffset = (blocks[lineIndex].end + 1).coerceAtMost(markdown.length)
                val codeLines = mutableListOf<String>()
                lineIndex++
                while (lineIndex < lines.size && blocks[lineIndex].kind != MarkdownBlockKind.CodeFence) {
                    codeLines.add(lines[lineIndex])
                    lineIndex++
                }
                if (lineIndex < lines.size && blocks[lineIndex].kind == MarkdownBlockKind.CodeFence) {
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
                val checklistContinuationLines =
                    if (blocks[lineIndex].kind == MarkdownBlockKind.Checklist) {
                        val continuationLines = mutableListOf<MarkdownContinuationLine>()
                        var continuationIndex = lineIndex + 1
                        while (continuationIndex < lines.size) {
                            val continuationLine = lines[continuationIndex]
                            val continuationMatch =
                                MarkdownChecklistContinuationLineRegex.matchEntire(continuationLine) ?: break
                            if (blocks[continuationIndex].kind != MarkdownBlockKind.Plain) break
                            val contentRange = continuationMatch.groups[1]?.range ?: break
                            continuationLines +=
                                MarkdownContinuationLine(
                                    text = continuationMatch.groupValues[1],
                                    lineStartOffset = blocks[continuationIndex].start,
                                    contentStartOffset = contentRange.first,
                                )
                            continuationIndex++
                        }
                        continuationLines
                    } else {
                        emptyList()
                    }
                MarkdownLine(
                    line = lines[lineIndex],
                    block = blocks[lineIndex],
                    lineIndex = lineIndex,
                    lineStartOffset = blocks[lineIndex].start,
                    checklistContinuationLines = checklistContinuationLines,
                    style = style,
                    styler = styler,
                    includeLinkAnnotations = includeLinkAnnotations,
                    onChecklistToggle = onChecklistToggle,
                    onChecklistCheckAll = onChecklistCheckAll,
                    onChecklistUncheckAll = onChecklistUncheckAll,
                    onTextTap = onTextTap,
                    onTextLongPress = onTextLongPress,
                    onLinkClick = onLinkClick,
                    onLinkLongPress = onLinkLongPress,
                )
                lineIndex += 1 + checklistContinuationLines.size
            }
        }
    }
}

private data class MarkdownContinuationLine(
    val text: String,
    val lineStartOffset: Int,
    val contentStartOffset: Int,
)

@Composable
private fun MarkdownLine(
    line: String,
    block: MarkdownLineSyntax,
    lineIndex: Int,
    lineStartOffset: Int,
    checklistContinuationLines: List<MarkdownContinuationLine>,
    style: TextStyle,
    styler: MarkdownStyler,
    includeLinkAnnotations: Boolean,
    onChecklistToggle: ((lineIndex: Int, checked: Boolean) -> Unit)?,
    onChecklistCheckAll: (() -> Unit)? = null,
    onChecklistUncheckAll: (() -> Unit)? = null,
    onTextTap: ((MarkdownTextTap) -> Unit)?,
    onTextLongPress: ((MarkdownTextTap) -> Unit)?,
    onLinkClick: ((MarkdownLinkInteraction) -> Unit)?,
    onLinkLongPress: ((MarkdownLinkInteraction) -> Unit)?,
) {
    if (block.kind == MarkdownBlockKind.Rule) {
        MarkdownHorizontalRule(
            // Tapping the rule enters editing at the end of the dashes, so a backspace right after
            // is all it takes to turn the rule back into plain text.
            markdownOffset = lineStartOffset + line.length,
            onTextTap = onTextTap,
            onTextLongPress = onTextLongPress,
        )
        return
    }

    val content = line.substring(block.contentStart - lineStartOffset)
    if (block.kind == MarkdownBlockKind.Heading) {
        val headingLevel = block.headingLevel
        MarkdownInlineText(
            styler = styler,
            includeLinkAnnotations = includeLinkAnnotations,
            style = styler.headingTextStyle(headingLevel = headingLevel),
            modifier = Modifier.fillMaxWidth(),
            source = content,
            sourceOffset = block.contentStart,
            onTextTap = onTextTap,
            onTextLongPress = onTextLongPress,
            onLinkClick = onLinkClick,
            onLinkLongPress = onLinkLongPress,
        )
        return
    }

    if (block.kind == MarkdownBlockKind.Checklist) {
        val checked = block.checked
        val checkboxSize = markdownChecklistCheckboxSize(style, LocalDensity.current)
        val hasLongPressAction = onChecklistCheckAll != null || onChecklistUncheckAll != null
        var showMenu by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.padding(start = styler.listStartPadding(block.indent, baseIndent = 0.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                RememberMaterialRoundedSymbol(
                    name = if (checked) "check_box" else "check_box_outline_blank",
                    size = checkboxSize,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    weight = FontWeight.Medium,
                    modifier =
                        if (onChecklistToggle != null) {
                            if (hasLongPressAction) {
                                Modifier.appCombinedClickable(
                                    role = Role.Checkbox,
                                    onClick = { onChecklistToggle(lineIndex, !checked) },
                                    // appCombinedClickable fires the long-press haptic itself.
                                    onLongClick = { showMenu = true },
                                )
                            } else {
                                Modifier.appClickable(role = Role.Checkbox) {
                                    onChecklistToggle(lineIndex, !checked)
                                }
                            }
                        } else {
                            Modifier
                        },
                )
                if (hasLongPressAction) {
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        RememberDropdownMenuItem(
                            text = { Text(stringResource(R.string.checklist_action_check_all)) },
                            leadingIcon = {
                                RememberMaterialRoundedSymbol(
                                    name = "done_all",
                                    size = 20.dp,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onChecklistCheckAll?.invoke()
                            },
                        )
                        RememberDropdownMenuItem(
                            text = { Text(stringResource(R.string.checklist_action_uncheck_all)) },
                            leadingIcon = {
                                RememberMaterialRoundedSymbol(
                                    name = "remove_done",
                                    size = 20.dp,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onChecklistUncheckAll?.invoke()
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MarkdownInlineText(
                    styler = styler,
                    includeLinkAnnotations = includeLinkAnnotations,
                    style = if (checked) style.copy(textDecoration = TextDecoration.LineThrough) else style,
                    modifier = Modifier.fillMaxWidth(),
                    source = content,
                    sourceOffset = block.contentStart,
                    onTextTap = onTextTap,
                    onTextLongPress = onTextLongPress,
                    onLinkClick = onLinkClick,
                    onLinkLongPress = onLinkLongPress,
                )
                checklistContinuationLines.forEach { continuationLine ->
                    MarkdownInlineText(
                        styler = styler,
                        includeLinkAnnotations = includeLinkAnnotations,
                        style = style,
                        modifier = Modifier.fillMaxWidth(),
                        source = continuationLine.text,
                        sourceOffset = continuationLine.lineStartOffset + continuationLine.contentStartOffset,
                        onTextTap = onTextTap,
                        onTextLongPress = onTextLongPress,
                        onLinkClick = onLinkClick,
                        onLinkLongPress = onLinkLongPress,
                    )
                }
            }
        }
        return
    }

    if (block.kind == MarkdownBlockKind.Bullet) {
        Row(
            modifier = Modifier.padding(start = styler.listStartPadding(block.indent, baseIndent = 16.dp)),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "\u2022",
                style = style,
            )
            Spacer(Modifier.width(8.dp))

            MarkdownInlineText(
                styler = styler,
                includeLinkAnnotations = includeLinkAnnotations,
                style = style,
                modifier = Modifier.weight(1f),
                source = content,
                sourceOffset = block.contentStart,
                onTextTap = onTextTap,
                onTextLongPress = onTextLongPress,
                onLinkClick = onLinkClick,
                onLinkLongPress = onLinkLongPress,
            )
        }
        return
    }

    if (block.kind == MarkdownBlockKind.Numbered) {
        Row(
            modifier = Modifier.padding(start = styler.listStartPadding(block.indent, baseIndent = 8.dp)),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "${block.number}.",
                style = style,
            )
            Spacer(Modifier.width(8.dp))

            MarkdownInlineText(
                styler = styler,
                includeLinkAnnotations = includeLinkAnnotations,
                style = style,
                modifier = Modifier.weight(1f),
                source = content,
                sourceOffset = block.contentStart,
                onTextTap = onTextTap,
                onTextLongPress = onTextLongPress,
                onLinkClick = onLinkClick,
                onLinkLongPress = onLinkLongPress,
            )
        }
        return
    }

    MarkdownInlineText(
        styler = styler,
        includeLinkAnnotations = includeLinkAnnotations,
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
private fun MarkdownHorizontalRule(
    markdownOffset: Int,
    onTextTap: ((MarkdownTextTap) -> Unit)?,
    onTextLongPress: ((MarkdownTextTap) -> Unit)?,
) {
    val hapticEnabled = LocalHapticEnabled.current
    val view = LocalView.current
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                // Roughly one text line tall, so a rule takes about as much room as the blank line
                // it replaces and stays as easy to tap as the text around it.
                .height(20.dp)
                .pointerInput(markdownOffset, onTextTap, onTextLongPress) {
                    if (onTextTap == null && onTextLongPress == null) {
                        return@pointerInput
                    }
                    detectTapGestures(
                        onTap = { onTextTap?.invoke(MarkdownTextTap(markdownOffset)) },
                        // Raw pointerInput long-presses cannot inherit appCombinedClickable's
                        // haptic, so it fires here - only when a handler exists to act on it.
                        onLongPress = {
                            onTextLongPress?.let { callback ->
                                if (hapticEnabled) view.performLongPressHaptic()
                                callback(MarkdownTextTap(markdownOffset))
                            }
                        },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun MarkdownInlineText(
    styler: MarkdownStyler,
    style: TextStyle,
    modifier: Modifier = Modifier,
    includeLinkAnnotations: Boolean = true,
    rendered: MarkdownRenderedContent? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    source: String = "",
    sourceOffset: Int = 0,
    onTextTap: ((MarkdownTextTap) -> Unit)? = null,
    onTextLongPress: ((MarkdownTextTap) -> Unit)? = null,
    onLinkClick: ((MarkdownLinkInteraction) -> Unit)? = null,
    onLinkLongPress: ((MarkdownLinkInteraction) -> Unit)? = null,
) {
    val hapticEnabled = LocalHapticEnabled.current
    val view = LocalView.current
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val content =
        rendered ?: remember(source, sourceOffset, styler, includeLinkAnnotations, style.textDecoration) {
            val projection = MarkdownInlineProjection(source)
            MarkdownRenderedContent(
                styler.renderInline(projection, includeLinkAnnotations, style.textDecoration),
                MarkdownInlineInteractionBuilder(source, sourceOffset).build(projection),
            )
        }
    val text = content.text
    val inlineInteractionMap = content.interactions
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
                    // Raw pointerInput long-presses cannot inherit appCombinedClickable's haptic, so
                    // they fire it here - but only once a handler is known to exist, so a long-press
                    // that nothing acts on stays silent.
                    onLongPress = { pressOffset ->
                        val visibleOffset =
                            textLayoutResult
                                ?.getOffsetForPosition(pressOffset)
                                ?: return@detectTapGestures
                        when (val interaction = inlineInteractionMap.interactionAt(visibleOffset)) {
                            is MarkdownInlineInteraction.Link ->
                                onLinkLongPress?.let { callback ->
                                    if (hapticEnabled) view.performLongPressHaptic()
                                    callback(interaction.link)
                                }
                            is MarkdownInlineInteraction.Text ->
                                onTextLongPress?.let { callback ->
                                    if (hapticEnabled) view.performLongPressHaptic()
                                    callback(MarkdownTextTap(interaction.markdownOffset))
                                }
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
                    styler = styler,
                    includeLinkAnnotations = includeLinkAnnotations,
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
    val hapticEnabled = LocalHapticEnabled.current
    val view = LocalView.current
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
                                onTextLongPress?.let { callback ->
                                    if (hapticEnabled) view.performLongPressHaptic()
                                    callback(MarkdownTextTap(sourceOffset + visibleOffset))
                                }
                            },
                        )
                    },
            onTextLayout = { textLayoutResult = it },
        )
    }
}

internal sealed interface MarkdownInlineInteraction {
    data class Text(
        val markdownOffset: Int,
    ) : MarkdownInlineInteraction

    data class Link(
        val link: MarkdownLinkInteraction,
    ) : MarkdownInlineInteraction
}

internal data class MarkdownInlineLinkRange(
    val visibleStart: Int,
    val visibleEnd: Int,
    val link: MarkdownLinkInteraction,
)

internal data class MarkdownInlineInteractionMap(
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

internal class MarkdownInlineInteractionBuilder(
    private val source: String,
    private val sourceOffset: Int,
    private val sourceOffsetByIndex: IntArray? = null,
) {
    fun build(): MarkdownInlineInteractionMap {
        return build(MarkdownInlineProjection(source))
    }

    fun build(projection: MarkdownInlineProjection): MarkdownInlineInteractionMap {
        val links =
            projection.spans.filter { it.kind == MarkdownInlineKind.Link }.mapNotNull { span ->
                val start = projection.visibleOffsets[span.openEnd]
                val end = projection.visibleOffsets[span.closeStart]
                if (end <= start) return@mapNotNull null
                MarkdownInlineLinkRange(
                    start,
                    end,
                    MarkdownLinkInteraction(
                        requireNotNull(span.url).markdownLinkUrl(),
                        source.substring(span.openEnd, span.closeStart),
                        sourceOffsetAt(span.openStart),
                        sourceOffsetAt(span.openEnd),
                        sourceOffsetAt(span.closeStart),
                    ),
                )
            }
        return MarkdownInlineInteractionMap(
            IntArray(projection.sourceOffsets.size) { sourceOffsetAt(projection.sourceOffsets[it]) },
            links,
        )
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

internal fun markdownChecklistCheckboxSize(
    style: TextStyle,
    density: Density,
): Dp {
    val fallbackSize = 24.dp
    if (!style.fontSize.isSpecified || style.fontSize.value <= 0f) {
        return fallbackSize
    }
    val requestedSize = with(density) { style.fontSize.toDp() }
    return if (requestedSize > 0.dp) maxOf(requestedSize, 18.dp) else fallbackSize
}

internal data class MarkdownRenderedContent(
    val text: AnnotatedString,
    val interactions: MarkdownInlineInteractionMap,
)

/** Card layout changes only presentation; inline meaning and source positions come from syntax. */
internal fun markdownCardPreview(
    markdown: String,
    styler: MarkdownStyler,
    includeLinkAnnotations: Boolean = true,
): MarkdownRenderedContent {
    val builder = AnnotatedString.Builder()
    val offsets = mutableListOf<Int>()
    val links = mutableListOf<MarkdownInlineLinkRange>()
    for (line in parseMarkdownLines(markdown)) {
        if (line.start > 0) {
            builder.append('\n')
            offsets.add(line.start - 1)
        }
        val prefix =
            when (line.kind) {
                MarkdownBlockKind.Rule -> MARKDOWN_PREVIEW_HORIZONTAL_RULE
                MarkdownBlockKind.Checklist -> line.indent + if (line.checked) "\u2611 " else "\u2610 "
                MarkdownBlockKind.Bullet -> "  " + line.indent + "\u2022 "
                MarkdownBlockKind.Numbered -> " " + line.indent + line.number + ". "
                else -> ""
            }
        builder.append(prefix)
        repeat(prefix.length) { offsets.add(line.start) }
        val content = markdown.substring(line.contentStart, line.end)
        val projection = if (line.kind == MarkdownBlockKind.Code) MarkdownInlineProjection(content, emptyList()) else MarkdownInlineProjection(content)
        val visibleStart = builder.length
        builder.append(styler.renderInline(projection, includeLinkAnnotations, if (line.checked) TextDecoration.LineThrough else null))
        if (line.kind == MarkdownBlockKind.Code && builder.length > visibleStart) {
            builder.addStyle(styler.codeBlockSpanStyle, visibleStart, builder.length)
        }
        val interactions = MarkdownInlineInteractionBuilder(content, line.contentStart).build(projection)
        offsets.addAll(interactions.sourceOffsetByVisibleOffset.dropLast(1))
        links.addAll(interactions.links.map { it.copy(visibleStart = it.visibleStart + visibleStart, visibleEnd = it.visibleEnd + visibleStart) })
    }
    offsets.add(markdown.length)
    return MarkdownRenderedContent(builder.toAnnotatedString().withCombinedMarkdownDecorations(), MarkdownInlineInteractionMap(offsets.toIntArray(), links))
}
