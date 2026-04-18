package dev.bikram.remember.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    LaunchedEffect(state) {
        state.config.orderedListIndent = OrderedListIndentSp
        state.config.unorderedListIndent = UnorderedListIndentSp
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
        val initialText = state.selectedLinkText
            ?: state.annotatedString.substring(state.selection.min, state.selection.max)
        val initialUrl = state.selectedLinkUrl.orEmpty()
        var linkText by remember { mutableStateOf(initialText) }
        var linkUrl by remember { mutableStateOf(initialUrl) }
        val editing = cursorInLink
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text(if (editing) "Edit Link" else "Add Link") },
            text = {
                Column {
                    OutlinedTextField(
                        value = linkText,
                        onValueChange = { linkText = it },
                        label = { Text("Text to display") },
                        singleLine = true,
                        enabled = !editing,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = linkUrl,
                        onValueChange = { linkUrl = it },
                        label = { Text("URL (e.g. https://...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
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
                        showLinkDialog = false
                    }
                ) {
                    Text(if (editing) "Save" else "Add")
                }
            },
            dismissButton = {
                Row {
                    if (editing) {
                        TextButton(onClick = {
                            state.removeLink()
                            showLinkDialog = false
                        }) { Text("Remove") }
                    }
                    TextButton(onClick = { showLinkDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
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
            IconButton(onClick = { onUndo() }, enabled = canUndo, colors = colors) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
        }
        if (onRedo != null) {
            IconButton(onClick = { onRedo() }, enabled = canRedo, colors = colors) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }
        }

        IconButton(
            onClick = { state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
            colors = if (state.currentSpanStyle.fontWeight == FontWeight.Bold && state.currentSpanStyle.fontSize == androidx.compose.ui.unit.TextUnit.Unspecified) activeColors else colors
        ) {
            Icon(Icons.Filled.FormatBold, contentDescription = "Bold")
        }

        IconButton(
            onClick = { state.toggleSpanStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) },
            colors = if (state.currentSpanStyle.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic) activeColors else colors
        ) {
            Icon(Icons.Filled.FormatItalic, contentDescription = "Italic")
        }

        IconButton(
            onClick = { state.toggleSpanStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) },
            colors = if (state.currentSpanStyle.textDecoration?.contains(androidx.compose.ui.text.style.TextDecoration.Underline) == true) activeColors else colors
        ) {
            Icon(Icons.Filled.FormatUnderlined, contentDescription = "Underline")
        }

        IconButton(
            onClick = { state.toggleSpanStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)) },
            colors = if (state.currentSpanStyle.textDecoration?.contains(androidx.compose.ui.text.style.TextDecoration.LineThrough) == true) activeColors else colors
        ) {
            Icon(Icons.Filled.FormatStrikethrough, contentDescription = "Strikethrough")
        }

        IconButton(
            onClick = { applyHeadingToCurrentLine(state, h1Style) },
            colors = if (isHeadingActive(state, 2f)) activeColors else colors
        ) {
            Text("H1", fontWeight = FontWeight.Bold)
        }

        IconButton(
            onClick = { applyHeadingToCurrentLine(state, h2Style) },
            colors = if (isHeadingActive(state, 1.5f)) activeColors else colors
        ) {
            Text("H2", fontWeight = FontWeight.Bold)
        }

        IconButton(
            onClick = { applyHeadingToCurrentLine(state, h3Style) },
            colors = if (isHeadingActive(state, 1.17f)) activeColors else colors
        ) {
            Text("H3", fontWeight = FontWeight.Bold)
        }

        IconButton(
            onClick = { state.toggleUnorderedList() },
            colors = if (state.isUnorderedList) activeColors else colors
        ) {
            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Bullet List")
        }

        IconButton(
            onClick = { state.toggleOrderedList() },
            colors = if (state.isOrderedList) activeColors else colors
        ) {
            Icon(Icons.Filled.FormatListNumbered, contentDescription = "Numbered List")
        }

        IconButton(
            onClick = { showLinkDialog = true },
            colors = if (cursorInLink) activeColors else colors
        ) {
            Icon(Icons.Filled.AddLink, contentDescription = if (cursorInLink) "Edit Link" else "Add Link")
        }
    }
}
