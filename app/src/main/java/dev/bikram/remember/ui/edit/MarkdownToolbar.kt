package dev.bikram.remember.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.components.RememberTextButton

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val isBulletList = remember(selectionRevision) { state.isBulletList }
    val isNumberedList = remember(selectionRevision) { state.isNumberedList }
    val isChecklist = remember(selectionRevision) { state.isChecklist }
    val isQuote = remember(selectionRevision) { state.isQuote }

    if (showLinkDialog) {
        MarkdownLinkDialog(
            state = state,
            editing = cursorInLink,
            onDismiss = { showLinkDialog = false },
        )
    }

    val colors =
        IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    val activeColors =
        IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        )
    val undoLabel = stringResource(R.string.common_undo)
    val redoLabel = stringResource(R.string.cd_redo)
    val boldLabel = stringResource(R.string.cd_bold)
    val italicLabel = stringResource(R.string.cd_italic)
    val underlineLabel = stringResource(R.string.cd_underline)
    val strikethroughLabel = stringResource(R.string.cd_strikethrough)
    val headingOneLabel = stringResource(R.string.rt_h1)
    val headingOneContentDescription = stringResource(R.string.rt_h1_cd)
    val headingTwoLabel = stringResource(R.string.rt_h2)
    val headingTwoContentDescription = stringResource(R.string.rt_h2_cd)
    val headingThreeLabel = stringResource(R.string.rt_h3)
    val headingThreeContentDescription = stringResource(R.string.rt_h3_cd)
    val bulletListLabel = stringResource(R.string.cd_bullet_list)
    val numberedListLabel = stringResource(R.string.cd_numbered_list)
    val checklistLabel = stringResource(R.string.cd_checklist)
    val quoteLabel = stringResource(R.string.cd_quote)
    val inlineCodeLabel = stringResource(R.string.cd_inline_code)
    val codeBlockLabel = stringResource(R.string.cd_code_block)
    val linkLabel =
        if (cursorInLink) {
            stringResource(R.string.rt_edit_link)
        } else {
            stringResource(R.string.rt_add_link)
        }

    ButtonGroup(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .horizontalScroll(rememberScrollState())
                .padding(4.dp),
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
    ) {
        if (onUndo != null) {
            markdownToolbarIconButton(
                symbolName = "undo",
                contentDescription = undoLabel,
                enabled = canUndo,
                colors = colors,
                onClick = onUndo,
            )
        }
        if (onRedo != null) {
            markdownToolbarIconButton(
                symbolName = "redo",
                contentDescription = redoLabel,
                enabled = canRedo,
                colors = colors,
                onClick = onRedo,
            )
        }

        markdownToolbarIconButton(
            symbolName = "format_bold",
            contentDescription = boldLabel,
            colors = colors,
            onClick = state::toggleBold,
        )
        markdownToolbarIconButton(
            symbolName = "format_italic",
            contentDescription = italicLabel,
            colors = colors,
            onClick = state::toggleItalic,
        )
        markdownToolbarIconButton(
            symbolName = "format_underlined",
            contentDescription = underlineLabel,
            colors = colors,
            onClick = state::toggleUnderline,
        )
        markdownToolbarIconButton(
            symbolName = "format_strikethrough",
            contentDescription = strikethroughLabel,
            colors = colors,
            onClick = state::toggleStrikethrough,
        )

        headingButton(
            label = headingOneLabel,
            contentDescription = headingOneContentDescription,
            active = activeHeading == 1,
            onClick = { state.applyHeading(1) },
            colors = colors,
            activeColors = activeColors,
        )
        headingButton(
            label = headingTwoLabel,
            contentDescription = headingTwoContentDescription,
            active = activeHeading == 2,
            onClick = { state.applyHeading(2) },
            colors = colors,
            activeColors = activeColors,
        )
        headingButton(
            label = headingThreeLabel,
            contentDescription = headingThreeContentDescription,
            active = activeHeading == 3,
            onClick = { state.applyHeading(3) },
            colors = colors,
            activeColors = activeColors,
        )

        markdownToolbarIconButton(
            symbolName = "format_list_bulleted",
            contentDescription = bulletListLabel,
            colors = if (isBulletList) activeColors else colors,
            onClick = state::applyBulletList,
        )
        markdownToolbarIconButton(
            symbolName = "format_list_numbered",
            contentDescription = numberedListLabel,
            colors = if (isNumberedList) activeColors else colors,
            onClick = state::applyNumberedList,
        )
        markdownToolbarIconButton(
            symbolName = "checklist",
            contentDescription = checklistLabel,
            colors = if (isChecklist) activeColors else colors,
            onClick = state::applyChecklist,
        )
        markdownToolbarIconButton(
            symbolName = "format_quote",
            contentDescription = quoteLabel,
            colors = if (isQuote) activeColors else colors,
            onClick = state::applyQuote,
        )
        markdownToolbarIconButton(
            symbolName = "code",
            contentDescription = inlineCodeLabel,
            colors = colors,
            onClick = state::toggleInlineCode,
        )
        markdownToolbarTextButton(
            label = "```",
            contentDescription = codeBlockLabel,
            colors = colors,
            onClick = state::applyCodeBlock,
            fontFamily = FontFamily.Monospace,
        )
        markdownToolbarIconButton(
            symbolName = "add_link",
            contentDescription = linkLabel,
            colors = if (cursorInLink) activeColors else colors,
            onClick = { showLinkDialog = true },
        )
    }
}

private fun ButtonGroupScope.markdownToolbarIconButton(
    symbolName: String,
    contentDescription: String,
    colors: IconButtonColors,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    customItem(
        buttonGroupContent = {
            val interactionSource = remember { MutableInteractionSource() }
            RememberIconButton(
                onClick = onClick,
                enabled = enabled,
                colors = colors,
                modifier = Modifier.animateWidth(interactionSource),
                interactionSource = interactionSource,
            ) {
                RememberMaterialRoundedSymbol(
                    name = symbolName,
                    weight = FontWeight.Medium,
                    modifier = Modifier.semantics { this.contentDescription = contentDescription },
                )
            }
        },
        menuContent = { menuState ->
            RememberDropdownMenuItem(
                text = { Text(contentDescription) },
                onClick = {
                    onClick()
                    menuState.dismiss()
                },
                enabled = enabled,
            )
        },
    )
}

private fun ButtonGroupScope.markdownToolbarTextButton(
    label: String,
    contentDescription: String,
    colors: IconButtonColors,
    onClick: () -> Unit,
    fontFamily: FontFamily? = null,
) {
    customItem(
        buttonGroupContent = {
            val interactionSource = remember { MutableInteractionSource() }
            RememberIconButton(
                onClick = onClick,
                colors = colors,
                modifier = Modifier.animateWidth(interactionSource),
                interactionSource = interactionSource,
            ) {
                Text(
                    text = label,
                    fontFamily = fontFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { this.contentDescription = contentDescription },
                )
            }
        },
        menuContent = { menuState ->
            RememberDropdownMenuItem(
                text = { Text(contentDescription) },
                onClick = {
                    onClick()
                    menuState.dismiss()
                },
            )
        },
    )
}

private fun ButtonGroupScope.headingButton(
    label: String,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    colors: IconButtonColors,
    activeColors: IconButtonColors,
) {
    customItem(
        buttonGroupContent = {
            val interactionSource = remember { MutableInteractionSource() }
            RememberIconButton(
                onClick = onClick,
                colors = if (active) activeColors else colors,
                modifier = Modifier.animateWidth(interactionSource),
                interactionSource = interactionSource,
            ) {
                Text(
                    text = label,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { this.contentDescription = contentDescription },
                )
            }
        },
        menuContent = { menuState ->
            RememberDropdownMenuItem(
                text = { Text(contentDescription) },
                onClick = {
                    onClick()
                    menuState.dismiss()
                },
            )
        },
    )
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
