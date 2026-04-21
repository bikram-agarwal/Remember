package dev.bikram.remember.ui.common
import androidx.compose.material3.TextButton

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.RichTextState
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.RememberIconButton
import androidx.compose.ui.res.stringResource
import dev.bikram.remember.R

// Ordered and unordered lists use different TextIndent math internally: unordered restLine is
// (indent*level + bulletStartTextWidth), ordered restLine is (indent*level). Setting a single
// listIndent value therefore produces different visible text positions. Tuning these separately
// aligns the text column across both list types. Tweak UnorderedListIndentSp by ±2-4 if visual
// drift appears at different typographic scales.
private const val OrderedListIndentSp = 42
private const val UnorderedListIndentSp = 30

@OptIn(ExperimentalRichTextApi::class)
@Composable
fun ApplyRichEditorListIndent(state: RichTextState) {
    val scheme = MaterialTheme.colorScheme
    LaunchedEffect(state, scheme.primary, scheme.background) {
        state.config.orderedListIndent = OrderedListIndentSp
        state.config.unorderedListIndent = UnorderedListIndentSp
        state.config.linkColor = scheme.primary
        state.config.linkTextDecoration = TextDecoration.Underline
    }
}

private fun applyHeadingToCurrentLine(state: RichTextState, style: SpanStyle) {
    // The library replaces '\n' with ' ' in annotatedString.text and encodes paragraph boundaries
    // via AnnotatedString.paragraphStyles ranges — so \n scanning silently treats the whole note
    // as one line. We pick paragraphs by their explicit ranges.
    //
    // Boundary handling: when a selection starts or a cursor sits exactly at the index where one
    // paragraph ends and the next begins, an inclusive `in start..end` check matches the earlier
    // paragraph. Use `cursor < end` (exclusive) so index 6 is treated as paragraph 1's start, not
    // paragraph 0's end.
    val annotated = state.annotatedString
    val sel = state.selection
    val paragraphs = annotated.paragraphStyles

    // Empty text OR no explicit paragraph ranges: just flip the pending/current span style so
    // H1/H2/H3 can be toggled off when the buffer is empty.
    if (annotated.text.isEmpty() || paragraphs.isEmpty()) {
        state.toggleSpanStyle(style)
        return
    }

    val overlapping = if (sel.collapsed) {
        val match = paragraphs.firstOrNull { sel.min >= it.start && sel.min < it.end }
            ?: paragraphs.last()
        listOf(match)
    } else {
        paragraphs.filter { p -> sel.min < p.end && sel.max > p.start }
    }

    if (overlapping.isEmpty()) return
    val startIdx = overlapping.first().start
    val endIdx = overlapping.last().end
    if (startIdx >= endIdx) {
        // Empty paragraph (blank line) — still let the user toggle the pending style.
        state.toggleSpanStyle(style)
        return
    }
    val previous = sel
    state.selection = TextRange(startIdx, endIdx)
    state.toggleSpanStyle(style)
    state.selection = previous
}

private fun isHeadingActive(state: RichTextState, emValue: Float): Boolean {
    val size = state.currentSpanStyle.fontSize
    return size.type == TextUnitType.Em && size.value == emValue
}

@OptIn(ExperimentalRichTextApi::class)
@Composable
fun RichTextToolbar(
    state: RichTextState,
    modifier: Modifier = Modifier,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndo: (() -> Unit)? = null,
    onRedo: (() -> Unit)? = null,
) {
    var showLinkDialog by remember { mutableStateOf(false) }
    val cursorInLink = state.isLink

    if (showLinkDialog) {
        LinkDialog(
            state = state,
            editing = cursorInLink,
            onDismiss = { showLinkDialog = false },
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .horizontalScroll(rememberScrollState())
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val activeColors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        )

        // Font sizes match library's H1/H2/H3 markdown-parser constants so "# / ## / ###"
        // round-trips through toMarkdown()/setMarkdown() instead of serializing as plain text.
        val h1Style = remember { SpanStyle(fontSize = 2.em, fontWeight = FontWeight.Bold) }
        val h2Style = remember { SpanStyle(fontSize = 1.5.em, fontWeight = FontWeight.Bold) }
        val h3Style = remember { SpanStyle(fontSize = 1.17.em, fontWeight = FontWeight.Bold) }

        if (onUndo != null) {
            RememberIconButton(onClick = { onUndo() }, enabled = canUndo, colors = colors) {
                    val cdUndo = stringResource(R.string.common_undo)
                    RememberMaterialRoundedSymbol(
                        name = "undo",
                        weight = FontWeight.Medium,
                        modifier = Modifier.semantics { contentDescription = cdUndo },
                    )
            }
        }
        if (onRedo != null) {
            RememberIconButton(onClick = { onRedo() }, enabled = canRedo, colors = colors) {
                    val cdRedo = stringResource(R.string.cd_redo)
                    RememberMaterialRoundedSymbol(
                        name = "redo",
                        weight = FontWeight.Medium,
                        modifier = Modifier.semantics { contentDescription = cdRedo },
                    )
            }
        }

        RememberIconButton(
            onClick = { state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
            colors = if (state.currentSpanStyle.fontWeight == FontWeight.Bold) activeColors else colors,
        ) {
                val cdBold = stringResource(R.string.cd_bold)
                RememberMaterialRoundedSymbol(
                    name = "format_bold",
                    weight = FontWeight.Medium,
                    modifier = Modifier.semantics { contentDescription = cdBold },
                )
        }

        RememberIconButton(
            onClick = { state.toggleSpanStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) },
            colors = if (state.currentSpanStyle.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic) activeColors else colors
        ) {
                val cdItalic = stringResource(R.string.cd_italic)
                RememberMaterialRoundedSymbol(
                    name = "format_italic",
                    weight = FontWeight.Medium,
                    modifier = Modifier.semantics { contentDescription = cdItalic },
                )
        }

        RememberIconButton(
            onClick = { state.toggleSpanStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) },
            colors = if (state.currentSpanStyle.textDecoration?.contains(androidx.compose.ui.text.style.TextDecoration.Underline) == true) activeColors else colors
        ) {
                val cdUnderline = stringResource(R.string.cd_underline)
                RememberMaterialRoundedSymbol(
                    name = "format_underlined",
                    weight = FontWeight.Medium,
                    modifier = Modifier.semantics { contentDescription = cdUnderline },
                )
        }

        RememberIconButton(
            onClick = { state.toggleSpanStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)) },
            colors = if (state.currentSpanStyle.textDecoration?.contains(androidx.compose.ui.text.style.TextDecoration.LineThrough) == true) activeColors else colors
        ) {
                val cdStrikethrough = stringResource(R.string.cd_strikethrough)
                RememberMaterialRoundedSymbol(
                    name = "format_strikethrough",
                    weight = FontWeight.Medium,
                    modifier = Modifier.semantics { contentDescription = cdStrikethrough },
                )
        }

        val cdH1 = stringResource(R.string.rt_h1_cd)
        RememberIconButton(
            onClick = { applyHeadingToCurrentLine(state, h1Style) },
            colors = if (isHeadingActive(state, 2f)) activeColors else colors,
        ) {
            Text(
                stringResource(R.string.rt_h1),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = cdH1 },
            )
        }

        val cdH2 = stringResource(R.string.rt_h2_cd)
        RememberIconButton(
            onClick = { applyHeadingToCurrentLine(state, h2Style) },
            colors = if (isHeadingActive(state, 1.5f)) activeColors else colors,
        ) {
            Text(
                stringResource(R.string.rt_h2),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = cdH2 },
            )
        }

        val cdH3 = stringResource(R.string.rt_h3_cd)
        RememberIconButton(
            onClick = { applyHeadingToCurrentLine(state, h3Style) },
            colors = if (isHeadingActive(state, 1.17f)) activeColors else colors,
        ) {
            Text(
                stringResource(R.string.rt_h3),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = cdH3 },
            )
        }

        RememberIconButton(
            onClick = { state.toggleUnorderedList() },
            colors = if (state.isUnorderedList) activeColors else colors
        ) {
                val cdBulletList = stringResource(R.string.cd_bullet_list)
                RememberMaterialRoundedSymbol(
                    name = "format_list_bulleted",
                    weight = FontWeight.Medium,
                    modifier = Modifier.semantics { contentDescription = cdBulletList },
                )
        }

        RememberIconButton(
            onClick = { state.toggleOrderedList() },
            colors = if (state.isOrderedList) activeColors else colors
        ) {
                val cdNumberedList = stringResource(R.string.cd_numbered_list)
                RememberMaterialRoundedSymbol(
                    name = "format_list_numbered",
                    weight = FontWeight.Medium,
                    modifier = Modifier.semantics { contentDescription = cdNumberedList },
                )
        }

        val cdAddLink = stringResource(R.string.rt_add_link)
        val cdEditLink = stringResource(R.string.rt_edit_link)
        RememberIconButton(
            onClick = { showLinkDialog = true },
            colors = if (cursorInLink) activeColors else colors,
        ) {
            RememberMaterialRoundedSymbol(
                name = "add_link",
                weight = FontWeight.Medium,
                modifier = Modifier.semantics {
                    contentDescription = if (cursorInLink) cdEditLink else cdAddLink
                },
            )
        }
    }
}

@OptIn(ExperimentalRichTextApi::class)
@Composable
private fun LinkDialog(
    state: RichTextState,
    editing: Boolean,
    onDismiss: () -> Unit,
) {
    val initialText = remember(state, editing) {
        val fromLink = state.selectedLinkText
        if (fromLink != null) {
            fromLink
        } else {
            val text = state.annotatedString
            val textLength = text.length
            if (textLength == 0) {
                ""
            } else {
                val start = state.selection.min.coerceIn(0, textLength)
                val end = state.selection.max.coerceIn(0, textLength)
                val rangeStart = minOf(start, end)
                val rangeEnd = maxOf(start, end).coerceIn(rangeStart, textLength)
                if (rangeStart >= rangeEnd) {
                    ""
                } else {
                    text.substring(rangeStart, rangeEnd)
                }
            }
        }
    }
    val initialUrl = remember(state, editing) { state.selectedLinkUrl.orEmpty() }
    var linkText by remember(state, editing) { mutableStateOf(initialText) }
    var linkUrl by remember(state, editing) { mutableStateOf(initialUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (editing) stringResource(R.string.rt_edit_link)
                else stringResource(R.string.rt_add_link)
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = linkText,
                    onValueChange = { linkText = it },
                    label = { Text(stringResource(R.string.rt_text_to_display)) },
                    singleLine = true,
                    enabled = !editing,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = linkUrl,
                    onValueChange = { linkUrl = it },
                    label = { Text(stringResource(R.string.rt_url_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            RememberTextButton(
                onClick = {
                    if (linkUrl.isNotBlank()) {
                        val finalUrl = if (!linkUrl.startsWith("http")) "https://$linkUrl" else linkUrl
                        if (editing) {
                            state.updateLink(finalUrl)
                        } else {
                            val finalText = linkText.ifBlank { finalUrl }
                            state.addLink(text = finalText, url = finalUrl)
                        }
                    }
                    onDismiss()
                }
            ) {
                Text(if (editing) "Save" else "Add")
            }
        },
        dismissButton = {
            Row {
                if (editing) {
                    RememberTextButton(onClick = {
                        state.removeLink()
                        onDismiss()
                    }) { Text(stringResource(R.string.common_remove)) }
                }
                RememberTextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        }
    )
}
