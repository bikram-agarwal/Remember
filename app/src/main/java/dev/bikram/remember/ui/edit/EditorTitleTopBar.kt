package dev.bikram.remember.ui.edit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.components.rememberResponsiveActionButtonSize
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val EDITOR_TITLE_MAX_LINES = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorTitleTopBar(
    contentKind: NoteKind,
    title: String,
    titlePlaceholder: String,
    iconKey: String?,
    existing: Boolean,
    isEditMode: Boolean,
    readOnly: Boolean,
    hasUnsavedChanges: Boolean,
    titleFocusOffset: Int?,
    onTitleChange: (String) -> Unit,
    onBack: () -> Unit,
    onTitleTappedInViewMode: (Int) -> Unit,
    onTitleFocusOffsetConsumed: () -> Unit,
    onOpenIcon: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onTitleFocusChanged: (Boolean) -> Unit = {},
    showNavigateBack: Boolean = true,
    allowInitialTitleFocus: Boolean = true,
    showEditableWhenTitleEmpty: Boolean = false,
    titleCollapseProgress: Float = 0f,
    markdownDisplayMode: MarkdownEditorDisplayMode? = null,
    onToggleMarkdownDisplayMode: (() -> Unit)? = null,
) {
    val titleFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val titleBoundaryCueScope = rememberCoroutineScope()
    val titleBoundaryOffset = remember { Animatable(0f) }
    var titleFieldValue by remember {
        mutableStateOf(TextFieldValue(text = title, selection = TextRange(title.length)))
    }
    LaunchedEffect(title) {
        if (title != titleFieldValue.text) {
            val selection = titleFieldValue.selection
            titleFieldValue =
                TextFieldValue(
                    text = title,
                    selection =
                        TextRange(
                            start = selection.start.coerceIn(0, title.length),
                            end = selection.end.coerceIn(0, title.length),
                        ),
                )
        }
    }
    LaunchedEffect(existing, isEditMode, readOnly) {
        if (allowInitialTitleFocus && !existing && isEditMode && !readOnly) {
            delay(80)
            titleFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    LaunchedEffect(isEditMode, readOnly, titleFocusOffset) {
        val offset = titleFocusOffset
        if (isEditMode && !readOnly && offset != null) {
            val boundedOffset = offset.coerceIn(0, title.length)
            titleFieldValue = TextFieldValue(text = title, selection = TextRange(boundedOffset))
            delay(80)
            titleFocusRequester.requestFocus()
            keyboardController?.show()
            onTitleFocusOffsetConsumed()
        }
    }
    val titleEditable = (isEditMode && !readOnly) || (showEditableWhenTitleEmpty && title.isEmpty())
    LaunchedEffect(titleEditable) {
        if (!titleEditable) onTitleFocusChanged(false)
    }
    val clampedTitleCollapseProgress = titleCollapseProgress.coerceIn(0f, 1f)
    val expandedTitleProgress = 1f - clampedTitleCollapseProgress

    Box(modifier = modifier) {
        val titleStyle =
            MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                lineBreak = LineBreak.Simple,
            )
        val collapsedTitle = title.ifEmpty { titlePlaceholder }
        val collapsedTitleStyle =
            MaterialTheme.typography.titleMedium.copy(
                color =
                    if (title.isEmpty()) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        Column {
            TopAppBar(
                colors = transparentLargeTopAppBarColors(),
                title = {
                    Text(
                        text = collapsedTitle,
                        style = collapsedTitleStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .graphicsLayer { alpha = clampedTitleCollapseProgress },
                    )
                },
                navigationIcon = {
                    if (showNavigateBack) {
                        RememberIconButton(
                            onClick = onBack,
                            modifier = Modifier.size(rememberResponsiveActionButtonSize()),
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "arrow_back",
                                autoMirror = true,
                                size = 24.dp,
                                tint = MaterialTheme.colorScheme.onSurface,
                                weight = FontWeight.Medium,
                            )
                        }
                    }
                },
                actions = {
                    val mode = markdownDisplayMode
                    if (isEditMode && !readOnly && mode != null && onToggleMarkdownDisplayMode != null) {
                        val toggleCd =
                            stringResource(
                                if (mode == MarkdownEditorDisplayMode.MarkdownCode) {
                                    R.string.cd_switch_to_live_preview
                                } else {
                                    R.string.cd_switch_to_markdown_code
                                },
                            )
                        RememberIconButton(
                            onClick = onToggleMarkdownDisplayMode,
                            modifier =
                                Modifier
                                    .size(rememberResponsiveActionButtonSize())
                                    .semantics { contentDescription = toggleCd },
                        ) {
                            RememberMaterialRoundedSymbol(
                                name =
                                    if (mode == MarkdownEditorDisplayMode.MarkdownCode) {
                                        "visibility"
                                    } else {
                                        "code"
                                    },
                                size = 24.dp,
                                tint = MaterialTheme.colorScheme.primary,
                                weight = FontWeight.Medium,
                            )
                        }
                    }
                    if ((isEditMode || hasUnsavedChanges) && !readOnly) {
                        val saveCd = stringResource(R.string.edit_save_cd)
                        RememberIconButton(
                            onClick = onSave,
                            modifier =
                                Modifier
                                    .size(rememberResponsiveActionButtonSize())
                                    .semantics { contentDescription = saveCd },
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "check",
                                size = 24.dp,
                                tint = MaterialTheme.colorScheme.primary,
                                weight = FontWeight.Medium,
                            )
                        }
                    }
                },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .verticalCollapse(expandedTitleProgress)
                        .graphicsLayer {
                            alpha = expandedTitleProgress
                            translationY = -12.dp.toPx() * clampedTitleCollapseProgress
                            scaleX = 1f - (0.06f * clampedTitleCollapseProgress)
                            scaleY = 1f - (0.06f * clampedTitleCollapseProgress)
                        }.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            ) {
                EditorHeaderIcon(
                    iconKey = iconKey,
                    kind = contentKind,
                    iconSize = 28.dp,
                    showBoundary = isEditMode && !readOnly,
                    onClick = if (readOnly) null else onOpenIcon,
                )
                Spacer(Modifier.width(8.dp))
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val textMeasurer = rememberTextMeasurer()
                    val titleTextWidth = constraints.maxWidth
                    if (titleEditable) {
                        BasicTextField(
                            value = titleFieldValue,
                            onValueChange = {
                                val nextText = sanitizeEditorTitle(it.text)
                                val nextValue = it.withEditorTitleText(nextText)
                                val fullTextFits =
                                    titleFitsEditorHeader(
                                        text = nextText,
                                        textMeasurer = textMeasurer,
                                        style = titleStyle,
                                        maxWidth = titleTextWidth,
                                    )
                                val isShortening = nextText.length < titleFieldValue.text.length
                                val acceptedValue =
                                    when {
                                        fullTextFits || isShortening -> nextValue
                                        else ->
                                            longestFittingEditorTitleValue(
                                                current = titleFieldValue,
                                                attempted = nextValue,
                                                textMeasurer = textMeasurer,
                                                style = titleStyle,
                                                maxWidth = titleTextWidth,
                                            )
                                    }
                                if (acceptedValue != null) {
                                    titleFieldValue = acceptedValue
                                    if (acceptedValue.text != title) {
                                        onTitleChange(acceptedValue.text)
                                    }
                                }
                                if (!fullTextFits && !isShortening) {
                                    titleBoundaryCueScope.launch {
                                        titleBoundaryOffset.snapTo(0f)
                                        titleBoundaryOffset.animateTo(
                                            targetValue = -3f,
                                            animationSpec = tween(durationMillis = 25),
                                        )
                                        titleBoundaryOffset.animateTo(
                                            targetValue = 3f,
                                            animationSpec = tween(durationMillis = 50),
                                        )
                                        titleBoundaryOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(durationMillis = 25),
                                        )
                                    }
                                }
                            },
                            textStyle = titleStyle,
                            enabled = !readOnly,
                            keyboardOptions =
                                KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Next,
                                ),
                            singleLine = false,
                            maxLines = EDITOR_TITLE_MAX_LINES,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(titleFocusRequester)
                                    .onFocusChanged { onTitleFocusChanged(it.isFocused) }
                                    .graphicsLayer {
                                        translationX = titleBoundaryOffset.value
                                    },
                            decorationBox = { inner ->
                                if (title.isEmpty()) {
                                    Text(
                                        text = titlePlaceholder,
                                        style =
                                            titleStyle.copy(
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                            ),
                                        maxLines = EDITOR_TITLE_MAX_LINES,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                inner()
                            },
                        )
                    } else {
                        val displayTitle = title.ifEmpty { titlePlaceholder }
                        val displayTitleStyle =
                            if (title.isEmpty()) {
                                titleStyle.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                )
                            } else {
                                titleStyle
                            }
                        var titleLayout by remember(displayTitle) { mutableStateOf<TextLayoutResult?>(null) }
                        SelectionContainer {
                            Text(
                                text = displayTitle,
                                style = displayTitleStyle,
                                maxLines = EDITOR_TITLE_MAX_LINES,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = { titleLayout = it },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .pointerInput(readOnly, titleLayout) {
                                            if (!readOnly) {
                                                detectTapGestures { tapOffset ->
                                                    val offset =
                                                        if (title.isEmpty()) {
                                                            0
                                                        } else {
                                                            titleLayout
                                                                ?.getOffsetForPosition(tapOffset)
                                                                ?: title.length
                                                        }
                                                    onTitleTappedInViewMode(offset)
                                                }
                                            }
                                        },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.verticalCollapse(progress: Float): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val height = (placeable.height * progress.coerceIn(0f, 1f)).roundToInt()
        layout(placeable.width, height) {
            placeable.placeRelative(0, 0)
        }
    }

private fun sanitizeEditorTitle(text: String): String = text.replace('\r', ' ').replace('\n', ' ')

private fun TextFieldValue.withEditorTitleText(text: String): TextFieldValue =
    copy(
        text = text,
        selection =
            TextRange(
                start = selection.start.coerceIn(0, text.length),
                end = selection.end.coerceIn(0, text.length),
            ),
    )

private fun longestFittingEditorTitleValue(
    current: TextFieldValue,
    attempted: TextFieldValue,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    maxWidth: Int,
): TextFieldValue? {
    val currentText = current.text
    val attemptedText = attempted.text
    val prefixLength = commonPrefixLength(currentText, attemptedText)
    val suffixLength = commonSuffixLength(currentText, attemptedText, prefixLength)
    val insertedEnd = attemptedText.length - suffixLength
    val insertedText = attemptedText.substring(prefixLength, insertedEnd)
    val insertedCodePointLength = insertedText.codePointCount(0, insertedText.length)
    val suffix = attemptedText.substring(insertedEnd)
    var low = 0
    var high = insertedCodePointLength
    var best: String? = null
    var bestSelection = prefixLength

    while (low <= high) {
        val keptInsertedCodePoints = (low + high) / 2
        val keptInsertedLength = insertedText.offsetByCodePoints(0, keptInsertedCodePoints)
        val candidate =
            attemptedText.substring(0, prefixLength) +
                insertedText.substring(0, keptInsertedLength) +
                suffix
        if (
            titleFitsEditorHeader(
                text = candidate,
                textMeasurer = textMeasurer,
                style = style,
                maxWidth = maxWidth,
            )
        ) {
            best = candidate
            bestSelection = prefixLength + keptInsertedLength
            low = keptInsertedCodePoints + 1
        } else {
            high = keptInsertedCodePoints - 1
        }
    }

    return best?.let { text ->
        attempted.copy(
            text = text,
            selection = TextRange(bestSelection.coerceIn(0, text.length)),
        )
    }
}

private fun commonPrefixLength(
    first: String,
    second: String,
): Int {
    val limit = minOf(first.length, second.length)
    var index = 0
    while (index < limit && first[index] == second[index]) {
        index++
    }
    return index
}

private fun commonSuffixLength(
    first: String,
    second: String,
    prefixLength: Int,
): Int {
    val maxSuffix = minOf(first.length, second.length) - prefixLength
    var count = 0
    while (
        count < maxSuffix &&
        first[first.lastIndex - count] == second[second.lastIndex - count]
    ) {
        count++
    }
    return count
}

private fun titleFitsEditorHeader(
    text: String,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    maxWidth: Int,
): Boolean {
    if (text.isEmpty() || maxWidth <= 0) return true
    val layoutResult =
        textMeasurer.measure(
            text = text,
            style = style,
            maxLines = EDITOR_TITLE_MAX_LINES,
            overflow = TextOverflow.Clip,
            constraints = Constraints(maxWidth = maxWidth),
        )
    return !layoutResult.hasVisualOverflow
}
