package dev.bikram.remember.ui.edit

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import dev.bikram.remember.ui.common.rememberMarkdownStyler
import kotlin.math.roundToInt

internal enum class MarkdownEditorDisplayMode { MarkdownCode, LivePreview }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MarkdownTextEditor(
    state: MarkdownEditorState,
    bodyPlaceholder: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = null,
    displayMode: MarkdownEditorDisplayMode = MarkdownEditorDisplayMode.LivePreview,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val view = LocalView.current
    val keyboardBottomInsetPx = WindowInsets.ime.getBottom(density)
    val keyboardMarginPx = with(density) { 96.dp.toPx() }
    val scrollAnimationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val livePreviewLineGap = with(density) { 2.dp.toSp() }
    val editorTextStyle =
        remember(displayMode, textStyle, livePreviewLineGap) {
            if (displayMode == MarkdownEditorDisplayMode.LivePreview && textStyle.lineHeight.isSpecified) {
                textStyle.copy(lineHeight = (textStyle.lineHeight.value + livePreviewLineGap.value).sp)
            } else {
                textStyle
            }
        }
    val styler = rememberMarkdownStyler(editorTextStyle)
    val visualTransformation =
        remember(displayMode, styler) {
            if (displayMode == MarkdownEditorDisplayMode.LivePreview) {
                MarkdownVisualTransformation(styler)
            } else {
                VisualTransformation.None
            }
        }
    var focused by remember { mutableStateOf(false) }
    var editorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    LaunchedEffect(state.focusRequestRevision) {
        if (state.focusRequestRevision > 0) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(focused, state.textFieldValue.selection, state.markdown.length, keyboardBottomInsetPx) {
        if (focused) {
            kotlinx.coroutines.delay(140)
            val transformedText =
                visualTransformation.filter(
                    AnnotatedString(state.markdown),
                )
            val transformedCursor =
                transformedText.offsetMapping
                    .originalToTransformed(state.textFieldValue.selection.end)
                    .coerceIn(0, transformedText.text.length)
            val cursorRect = textLayoutResult?.getCursorRect(transformedCursor)
            val coordinates = editorCoordinates
            if (cursorRect != null && coordinates != null && scrollState != null && keyboardBottomInsetPx > 0) {
                val cursorBottomInWindow =
                    coordinates
                        .localToWindow(
                            Offset(x = cursorRect.left, y = cursorRect.bottom),
                        ).y
                val visibleBottom = view.height - keyboardBottomInsetPx - keyboardMarginPx
                val overlap = cursorBottomInWindow - visibleBottom
                if (overlap > 0f) {
                    scrollState.animateScrollTo(
                        value = (scrollState.value + overlap.roundToInt()).coerceAtMost(scrollState.maxValue),
                        animationSpec = scrollAnimationSpec,
                    )
                }
            }
        }
    }

    BasicTextField(
        value = state.textFieldValue,
        onValueChange = { value ->
            state.update(
                value = value,
                cleanUpEmptyMarkdownWrappers = displayMode == MarkdownEditorDisplayMode.LivePreview,
            )
        },
        textStyle = editorTextStyle,
        visualTransformation = visualTransformation,
        keyboardOptions =
            KeyboardOptions(
                capitalization =
                    if (state.shouldCapitalizeNextInputInEmptyInlineWrapper) {
                        KeyboardCapitalization.Words
                    } else {
                        KeyboardCapitalization.Sentences
                    },
                imeAction = ImeAction.Default,
            ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        onTextLayout = { textLayoutResult = it },
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp)
                .onGloballyPositioned { editorCoordinates = it }
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (state.markdown.isEmpty()) {
                    Text(
                        text = bodyPlaceholder,
                        style = editorTextStyle.copy(color = editorTextStyle.color.copy(alpha = 0.35f)),
                    )
                }
                innerTextField()
            }
        },
    )
}
