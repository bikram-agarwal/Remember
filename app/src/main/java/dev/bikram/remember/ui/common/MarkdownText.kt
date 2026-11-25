@file:Suppress("ktlint:standard:function-expression-body")

package dev.bikram.remember.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import dev.bikram.remember.R
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.feedback.LocalHapticEnabled
import dev.bikram.remember.ui.feedback.appClickable
import dev.bikram.remember.ui.feedback.appCombinedClickable
import dev.bikram.remember.ui.feedback.performLongPressHaptic
import dev.bikram.remember.ui.theme.pillShape
import java.time.LocalDate

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

internal data class MarkdownDateInteraction(
    val date: LocalDate,
    val text: String,
    val spanStartOffset: Int,
    val spanEndOffset: Int,
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
    onDateClick: ((MarkdownDateInteraction) -> Unit)? = null,
    onDateLongPress: ((MarkdownDateInteraction) -> Unit)? = null,
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
            onDateClick = onDateClick,
            onDateLongPress = onDateLongPress,
        )
        return
    }

    val blocks = remember(markdown) { parseMarkdownLines(markdown) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        var lineIndex = 0
        while (lineIndex < blocks.size) {
            val quoteLines = mutableListOf<String>()
            val quoteLineContentOffsets = mutableListOf<Int>()
            while (lineIndex < blocks.size) {
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
                    onDateClick = onDateClick,
                    onDateLongPress = onDateLongPress,
                )
            } else if (blocks[lineIndex].kind == MarkdownBlockKind.CodeFence) {
                val codeBlockStartOffset =
                    if (lineIndex + 1 < blocks.size) {
                        blocks[lineIndex + 1].start
                    } else {
                        blocks[lineIndex].end
                    }
                val codeLines = mutableListOf<String>()
                lineIndex++
                while (lineIndex < blocks.size && blocks[lineIndex].kind != MarkdownBlockKind.CodeFence) {
                    val codeBlock = blocks[lineIndex]
                    codeLines.add(markdown.substring(codeBlock.start, codeBlock.end))
                    lineIndex++
                }
                if (lineIndex < blocks.size && blocks[lineIndex].kind == MarkdownBlockKind.CodeFence) {
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
                val currentBlock = blocks[lineIndex]
                val checklistContinuationLines =
                    if (currentBlock.kind == MarkdownBlockKind.Checklist) {
                        val continuationLines = mutableListOf<MarkdownContinuationLine>()
                        var continuationIndex = lineIndex + 1
                        while (continuationIndex < blocks.size) {
                            val continuationBlock = blocks[continuationIndex]
                            if (continuationBlock.kind != MarkdownBlockKind.Plain) break
                            val continuationLine = markdown.substring(continuationBlock.start, continuationBlock.end)
                            val continuationMatch =
                                MarkdownChecklistContinuationLineRegex.matchEntire(continuationLine) ?: break
                            val contentRange = continuationMatch.groups[1]?.range ?: break
                            continuationLines +=
                                MarkdownContinuationLine(
                                    text = continuationMatch.groupValues[1],
                                    lineStartOffset = continuationBlock.start,
                                    contentStartOffset = contentRange.first,
                                )
                            continuationIndex++
                        }
                        continuationLines
                    } else {
                        emptyList()
                    }
                MarkdownLine(
                    line = markdown.substring(currentBlock.start, currentBlock.end),
                    block = currentBlock,
                    lineIndex = lineIndex,
                    lineStartOffset = currentBlock.start,
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
                    onDateClick = onDateClick,
                    onDateLongPress = onDateLongPress,
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
    onDateClick: ((MarkdownDateInteraction) -> Unit)?,
    onDateLongPress: ((MarkdownDateInteraction) -> Unit)?,
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
            onDateClick = onDateClick,
            onDateLongPress = onDateLongPress,
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
            verticalAlignment = Alignment.Top,
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
                    onDateClick = onDateClick,
                    onDateLongPress = onDateLongPress,
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
                        onDateClick = onDateClick,
                        onDateLongPress = onDateLongPress,
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
                onDateClick = onDateClick,
                onDateLongPress = onDateLongPress,
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
                onDateClick = onDateClick,
                onDateLongPress = onDateLongPress,
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
        onDateClick = onDateClick,
        onDateLongPress = onDateLongPress,
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
    onDateClick: ((MarkdownDateInteraction) -> Unit)? = null,
    onDateLongPress: ((MarkdownDateInteraction) -> Unit)? = null,
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
    val displayContent =
        rememberMarkdownDateDisplayContent(
            baseText = text,
            dates = inlineInteractionMap.dates,
            styler = styler,
            style = style,
            onDateClick = onDateClick,
            onDateLongPress = onDateLongPress,
        )
    val hasTapHandler = onTextTap != null || onLinkClick != null || onDateClick != null
    val hasLongPressHandler = onTextLongPress != null || onLinkLongPress != null || onDateLongPress != null
    val interactionModifier =
        if (hasTapHandler || hasLongPressHandler) {
            Modifier.pointerInput(
                inlineInteractionMap,
                onTextTap,
                onTextLongPress,
                onLinkClick,
                onLinkLongPress,
                onDateClick,
                onDateLongPress,
            ) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val visibleOffset =
                            textLayoutResult
                                ?.getOffsetForPosition(tapOffset)
                                ?: return@detectTapGestures
                        when (val interaction = inlineInteractionMap.interactionAt(visibleOffset)) {
                            is MarkdownInlineInteraction.Link -> onLinkClick?.invoke(interaction.link)
                            is MarkdownInlineInteraction.Date -> {
                                if (onDateClick != null) {
                                    onDateClick(interaction.date)
                                } else {
                                    onTextTap?.invoke(MarkdownTextTap(interaction.date.spanEndOffset))
                                }
                            }
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
                            is MarkdownInlineInteraction.Date ->
                                onDateLongPress?.let { callback ->
                                    if (hapticEnabled) view.performLongPressHaptic()
                                    callback(interaction.date)
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
        text = displayContent.text,
        style = style,
        inlineContent = displayContent.inlineContent,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { textLayoutResult = it },
        modifier = modifier.then(interactionModifier),
    )
}

private data class MarkdownDateDisplayContent(
    val text: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>,
)

private val DATE_CHIP_PAD_START = 5.dp
private val DATE_CHIP_PAD_END = 6.dp
private val DATE_CHIP_ICON_GAP = 3.5.dp
private val DATE_CHIP_VERTICAL_PAD = 2.dp
private val DATE_CHIP_MEASURE_BUFFER = 1.dp

@Composable
private fun rememberMarkdownDateDisplayContent(
    baseText: AnnotatedString,
    dates: List<MarkdownInlineDateRange>,
    styler: MarkdownStyler,
    style: TextStyle,
    onDateClick: ((MarkdownDateInteraction) -> Unit)?,
    onDateLongPress: ((MarkdownDateInteraction) -> Unit)?,
): MarkdownDateDisplayContent {
    if (dates.isEmpty()) {
        return remember(baseText) { MarkdownDateDisplayContent(baseText, emptyMap()) }
    }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    // Callers pass fresh lambdas each recomposition; keying on them would re-measure every chip
    // every time. Key on presence only and read the latest lambda when a chip is actually used.
    val latestOnDateClick by rememberUpdatedState(onDateClick)
    val latestOnDateLongPress by rememberUpdatedState(onDateLongPress)
    val hasDateClick = onDateClick != null
    val hasDateLongPress = onDateLongPress != null
    return remember(baseText, dates, styler, style, density, textMeasurer, hasDateClick, hasDateLongPress) {
        val baseFontSize = if (style.fontSize.isSpecified && style.fontSize.value > 0f) style.fontSize else 16.sp
        val chipFontSize = (baseFontSize.value * 0.85f).sp
        val iconSizeDp = with(density) { (chipFontSize.toDp() * 1.12f).coerceIn(12.dp, 22.dp) }
        val inlineMap = LinkedHashMap<String, InlineTextContent>(dates.size)
        val annotated =
            buildAnnotatedString {
                var cursor = 0
                dates.forEachIndexed { index, dateRange ->
                    val rangeStart = dateRange.visibleStart.coerceIn(cursor, baseText.length)
                    val rangeEnd = dateRange.visibleEnd.coerceIn(rangeStart, baseText.length)
                    if (rangeStart > cursor) {
                        append(baseText.subSequence(cursor, rangeStart))
                    }
                    if (rangeEnd > rangeStart) {
                        val chipId = "markdown_date_chip_$index"
                        val chipTextStyle = resolveDateChipTextStyle(baseText, rangeStart, rangeEnd, style, chipFontSize)
                        val measured =
                            textMeasurer.measure(
                                text = AnnotatedString(dateRange.date.text),
                                style = chipTextStyle,
                                maxLines = 1,
                                softWrap = false,
                            )
                        val textWidthDp = with(density) { measured.size.width.toDp() }
                        val textHeightDp = with(density) { measured.size.height.toDp() }
                        val pillHeightDp = maxOf(textHeightDp, iconSizeDp) + DATE_CHIP_VERTICAL_PAD * 2
                        val slotWidthDp =
                            DATE_CHIP_PAD_START +
                                iconSizeDp +
                                DATE_CHIP_ICON_GAP +
                                textWidthDp +
                                DATE_CHIP_PAD_END +
                                DATE_CHIP_MEASURE_BUFFER
                        val slotHeightDp = pillHeightDp + 2.dp
                        val placeholder =
                            Placeholder(
                                width = with(density) { slotWidthDp.toSp() },
                                height = with(density) { slotHeightDp.toSp() },
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                            )
                        val lineStruckThrough = style.textDecoration?.contains(TextDecoration.LineThrough) == true
                        inlineMap[chipId] =
                            InlineTextContent(placeholder) {
                                MarkdownDateChipPill(
                                    dateInteraction = dateRange.date,
                                    styler = styler,
                                    chipTextStyle = chipTextStyle,
                                    iconSize = iconSizeDp,
                                    pillHeight = pillHeightDp,
                                    faded = lineStruckThrough,
                                    onDateClick = if (hasDateClick) { date -> latestOnDateClick?.invoke(date) } else null,
                                    onDateLongPress = if (hasDateLongPress) { date -> latestOnDateLongPress?.invoke(date) } else null,
                                )
                            }
                        appendInlineContent(
                            id = chipId,
                            alternateText = baseText.text.substring(rangeStart, rangeEnd),
                        )
                    }
                    cursor = rangeEnd
                }
                if (cursor < baseText.length) {
                    append(baseText.subSequence(cursor, baseText.length))
                }
            }
        MarkdownDateDisplayContent(annotated, inlineMap)
    }
}

private fun resolveDateChipTextStyle(
    baseText: AnnotatedString,
    rangeStart: Int,
    rangeEnd: Int,
    lineStyle: TextStyle,
    chipFontSize: androidx.compose.ui.unit.TextUnit,
): TextStyle {
    val overlapping = baseText.spanStyles.filter { it.start < rangeEnd && it.end > rangeStart }
    val hasBold = overlapping.any { it.item.fontWeight == FontWeight.Bold }
    val hasItalic = overlapping.any { it.item.fontStyle == FontStyle.Italic }
    val decorations = mutableListOf<TextDecoration>()
    if (lineStyle.textDecoration?.contains(TextDecoration.LineThrough) == true ||
        overlapping.any { it.item.textDecoration?.contains(TextDecoration.LineThrough) == true }
    ) {
        decorations += TextDecoration.LineThrough
    }
    if (overlapping.any { it.item.textDecoration?.contains(TextDecoration.Underline) == true }) {
        decorations += TextDecoration.Underline
    }
    return lineStyle.copy(
        fontSize = chipFontSize,
        lineHeight = chipFontSize,
        fontWeight = if (hasBold) FontWeight.Bold else FontWeight.Medium,
        fontStyle = if (hasItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (decorations.isEmpty()) TextDecoration.None else TextDecoration.combine(decorations),
    )
}

@Composable
private fun MarkdownDateChipPill(
    dateInteraction: MarkdownDateInteraction,
    styler: MarkdownStyler,
    chipTextStyle: TextStyle,
    iconSize: Dp,
    pillHeight: Dp,
    faded: Boolean,
    onDateClick: ((MarkdownDateInteraction) -> Unit)?,
    onDateLongPress: ((MarkdownDateInteraction) -> Unit)?,
) {
    val clickModifier =
        when {
            onDateClick != null && onDateLongPress != null ->
                Modifier
                    .clip(pillShape)
                    .appCombinedClickable(
                        role = Role.Button,
                        onClick = { onDateClick(dateInteraction) },
                        onLongClick = { onDateLongPress(dateInteraction) },
                    )
            onDateClick != null ->
                Modifier
                    .clip(pillShape)
                    .appClickable(role = Role.Button) { onDateClick(dateInteraction) }
            else -> Modifier
        }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .alpha(if (faded) 0.7f else 1f),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DATE_CHIP_ICON_GAP, Alignment.CenterHorizontally),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(pillHeight)
                    .background(styler.dateContainerColor, pillShape)
                    .border(1.dp, styler.dateBorderColor, pillShape)
                    .then(clickModifier)
                    .padding(start = DATE_CHIP_PAD_START, end = DATE_CHIP_PAD_END),
        ) {
            RememberMaterialRoundedSymbol(
                name = "calendar_month",
                size = iconSize,
                tint = styler.linkColor,
                filled = false,
            )
            Text(
                text = dateInteraction.text,
                style = chipTextStyle,
                color = styler.dateContentColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
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
    onDateClick: ((MarkdownDateInteraction) -> Unit)?,
    onDateLongPress: ((MarkdownDateInteraction) -> Unit)?,
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
                    onDateClick = onDateClick,
                    onDateLongPress = onDateLongPress,
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

    data class Date(
        val date: MarkdownDateInteraction,
    ) : MarkdownInlineInteraction
}

internal data class MarkdownInlineLinkRange(
    val visibleStart: Int,
    val visibleEnd: Int,
    val link: MarkdownLinkInteraction,
)

internal data class MarkdownInlineDateRange(
    val visibleStart: Int,
    val visibleEnd: Int,
    val date: MarkdownDateInteraction,
)

internal data class MarkdownInlineInteractionMap(
    val sourceOffsetByVisibleOffset: IntArray,
    val links: List<MarkdownInlineLinkRange>,
    val dates: List<MarkdownInlineDateRange> = emptyList(),
) {
    fun interactionAt(visibleOffset: Int): MarkdownInlineInteraction {
        val boundedVisibleOffset = visibleOffset.coerceIn(0, sourceOffsetByVisibleOffset.lastIndex)
        val dateRange =
            dates.firstOrNull { candidate ->
                boundedVisibleOffset >= candidate.visibleStart && boundedVisibleOffset < candidate.visibleEnd
            }
        if (dateRange != null) {
            return MarkdownInlineInteraction.Date(dateRange.date)
        }
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
        val dates =
            projection.spans.filter { it.kind == MarkdownInlineKind.Date }.mapNotNull { span ->
                val start = projection.visibleOffsets[span.openEnd]
                val end = projection.visibleOffsets[span.closeStart]
                val parsedDate = span.url?.let(::parseMarkdownDateTarget) ?: return@mapNotNull null
                if (end <= start) return@mapNotNull null
                MarkdownInlineDateRange(
                    start,
                    end,
                    MarkdownDateInteraction(
                        date = parsedDate,
                        text = projection.text.substring(start, end),
                        spanStartOffset = sourceOffsetAt(span.openStart),
                        spanEndOffset = sourceOffsetAt(span.closeEnd),
                    ),
                )
            }
        return MarkdownInlineInteractionMap(
            IntArray(projection.sourceOffsets.size) { sourceOffsetAt(projection.sourceOffsets[it]) },
            links,
            dates,
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

/**
 * List rows and note markdown checklists share this: a 24dp box on 16sp bodyLarge, so 1.5x the
 * text. Notes used to clamp to 18dp (barely larger than the text) and looked small next to lists.
 */
internal const val CHECKLIST_CHECKBOX_TO_TEXT_RATIO = 1.5f

internal fun markdownChecklistCheckboxSize(
    style: TextStyle,
    density: Density,
): Dp {
    val fallbackSize = 24.dp
    if (!style.fontSize.isSpecified || style.fontSize.value <= 0f) {
        return fallbackSize
    }
    val textSize = with(density) { style.fontSize.toDp() }
    return if (textSize > 0.dp) textSize * CHECKLIST_CHECKBOX_TO_TEXT_RATIO else fallbackSize
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
    val dates = mutableListOf<MarkdownInlineDateRange>()
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
        dates.addAll(interactions.dates.map { it.copy(visibleStart = it.visibleStart + visibleStart, visibleEnd = it.visibleEnd + visibleStart) })
    }
    offsets.add(markdown.length)
    return MarkdownRenderedContent(
        builder.toAnnotatedString().withCombinedMarkdownDecorations(),
        MarkdownInlineInteractionMap(offsets.toIntArray(), links, dates),
    )
}
