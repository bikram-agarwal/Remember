package dev.bikram.remember.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.components.RememberTextButton

@Composable
internal fun MarkdownToolbar(
    state: MarkdownEditorState,
    modifier: Modifier = Modifier,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndo: (() -> Unit)? = null,
    onRedo: (() -> Unit)? = null,
) {
    var showLinkDialog by remember { mutableStateOf(false) }
    val selectionRevision = state.selectionRevision
    val activeHeading = remember(selectionRevision) { state.headingLevel }
    val cursorInLink = remember(selectionRevision) { state.selectedLinkUrl != null }

    if (showLinkDialog) {
        MarkdownLinkDialog(
            state = state,
            editing = cursorInLink,
            onDismiss = { showLinkDialog = false },
        )
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .horizontalScroll(rememberScrollState())
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val colors =
            IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        val activeColors =
            IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            )

        if (onUndo != null) {
            MarkdownToolbarIconButton(
                symbolName = "undo",
                contentDescription = stringResource(R.string.common_undo),
                enabled = canUndo,
                colors = colors,
                onClick = onUndo,
            )
        }
        if (onRedo != null) {
            MarkdownToolbarIconButton(
                symbolName = "redo",
                contentDescription = stringResource(R.string.cd_redo),
                enabled = canRedo,
                colors = colors,
                onClick = onRedo,
            )
        }

        MarkdownToolbarIconButton(
            symbolName = "format_bold",
            contentDescription = stringResource(R.string.cd_bold),
            colors = colors,
            onClick = state::toggleBold,
        )
        MarkdownToolbarIconButton(
            symbolName = "format_italic",
            contentDescription = stringResource(R.string.cd_italic),
            colors = colors,
            onClick = state::toggleItalic,
        )
        MarkdownToolbarIconButton(
            symbolName = "format_underlined",
            contentDescription = stringResource(R.string.cd_underline),
            colors = colors,
            onClick = state::toggleUnderline,
        )
        MarkdownToolbarIconButton(
            symbolName = "format_strikethrough",
            contentDescription = stringResource(R.string.cd_strikethrough),
            colors = colors,
            onClick = state::toggleStrikethrough,
        )

        HeadingButton(
            label = stringResource(R.string.rt_h1),
            contentDescription = stringResource(R.string.rt_h1_cd),
            active = activeHeading == 1,
            onClick = { state.applyHeading(1) },
            colors = colors,
            activeColors = activeColors,
        )
        HeadingButton(
            label = stringResource(R.string.rt_h2),
            contentDescription = stringResource(R.string.rt_h2_cd),
            active = activeHeading == 2,
            onClick = { state.applyHeading(2) },
            colors = colors,
            activeColors = activeColors,
        )
        HeadingButton(
            label = stringResource(R.string.rt_h3),
            contentDescription = stringResource(R.string.rt_h3_cd),
            active = activeHeading == 3,
            onClick = { state.applyHeading(3) },
            colors = colors,
            activeColors = activeColors,
        )

        MarkdownToolbarIconButton(
            symbolName = "format_list_bulleted",
            contentDescription = stringResource(R.string.cd_bullet_list),
            colors = if (remember(selectionRevision) { state.isBulletList }) activeColors else colors,
            onClick = state::applyBulletList,
        )
        MarkdownToolbarIconButton(
            symbolName = "format_list_numbered",
            contentDescription = stringResource(R.string.cd_numbered_list),
            colors = if (remember(selectionRevision) { state.isNumberedList }) activeColors else colors,
            onClick = state::applyNumberedList,
        )
        MarkdownToolbarIconButton(
            symbolName = "checklist",
            contentDescription = stringResource(R.string.cd_checklist),
            colors = if (remember(selectionRevision) { state.isChecklist }) activeColors else colors,
            onClick = state::applyChecklist,
        )
        MarkdownToolbarIconButton(
            symbolName = "format_quote",
            contentDescription = stringResource(R.string.cd_quote),
            colors = if (remember(selectionRevision) { state.isQuote }) activeColors else colors,
            onClick = state::applyQuote,
        )
        MarkdownToolbarIconButton(
            symbolName = "code",
            contentDescription = stringResource(R.string.cd_inline_code),
            colors = colors,
            onClick = state::toggleInlineCode,
        )
        MarkdownToolbarTextButton(
            label = "```",
            contentDescription = stringResource(R.string.cd_code_block),
            colors = colors,
            onClick = state::applyCodeBlock,
            fontFamily = FontFamily.Monospace,
        )
        MarkdownToolbarIconButton(
            symbolName = "add_link",
            contentDescription =
                if (cursorInLink) {
                    stringResource(R.string.rt_edit_link)
                } else {
                    stringResource(R.string.rt_add_link)
                },
            colors = if (cursorInLink) activeColors else colors,
            onClick = { showLinkDialog = true },
        )
    }
}

@Composable
private fun MarkdownToolbarIconButton(
    symbolName: String,
    contentDescription: String,
    colors: IconButtonColors,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    RememberIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
    ) {
        RememberMaterialRoundedSymbol(
            name = symbolName,
            weight = FontWeight.Medium,
            modifier = Modifier.semantics { this.contentDescription = contentDescription },
        )
    }
}

@Composable
private fun MarkdownToolbarTextButton(
    label: String,
    contentDescription: String,
    colors: IconButtonColors,
    onClick: () -> Unit,
    fontFamily: FontFamily? = null,
) {
    RememberIconButton(
        onClick = onClick,
        colors = colors,
    ) {
        Text(
            text = label,
            fontFamily = fontFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { this.contentDescription = contentDescription },
        )
    }
}

@Composable
private fun HeadingButton(
    label: String,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    colors: IconButtonColors,
    activeColors: IconButtonColors,
) {
    RememberIconButton(
        onClick = onClick,
        colors = if (active) activeColors else colors,
    ) {
        Text(
            text = label,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { this.contentDescription = contentDescription },
        )
    }
}

@Composable
private fun MarkdownLinkDialog(
    state: MarkdownEditorState,
    editing: Boolean,
    onDismiss: () -> Unit,
) {
    val selectionRevision = state.selectionRevision
    val initialText = remember(selectionRevision) { state.selectedText() }
    val initialUrl = remember(selectionRevision) { state.selectedLinkUrl.orEmpty() }
    var linkText by remember(selectionRevision) { mutableStateOf(initialText) }
    var linkUrl by remember(selectionRevision) { mutableStateOf(initialUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (editing) {
                    stringResource(R.string.rt_edit_link)
                } else {
                    stringResource(R.string.rt_add_link)
                },
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = linkText,
                    onValueChange = { linkText = it },
                    label = { Text(stringResource(R.string.rt_text_to_display)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = linkUrl,
                    onValueChange = { linkUrl = it },
                    label = { Text(stringResource(R.string.rt_url_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            RememberTextButton(
                onClick = {
                    if (linkUrl.isNotBlank()) {
                        state.addOrUpdateLink(
                            displayText = linkText,
                            rawUrl = linkUrl,
                        )
                    }
                    onDismiss()
                },
            ) {
                Text(
                    if (editing) {
                        stringResource(R.string.common_save)
                    } else {
                        stringResource(R.string.common_add)
                    },
                )
            }
        },
        dismissButton = {
            RememberTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
