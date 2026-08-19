package dev.bikram.remember.ui.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import dev.bikram.remember.data.TagPalette
import dev.bikram.remember.ui.common.rememberMarkdownStyler
import dev.bikram.remember.ui.components.TagChipFilled
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import kotlin.math.roundToInt

internal enum class MarkdownEditorDisplayMode { MarkdownCode, LivePreview }

private const val LIVE_PREVIEW_DEBOUNCE_DELAY_MS = 250L

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MarkdownTextEditor(
    state: MarkdownEditorState,
    bodyPlaceholder: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = null,
    displayMode: MarkdownEditorDisplayMode = MarkdownEditorDisplayMode.LivePreview,
    assignedTags: List<String> = emptyList(),
    onStylusInput: () -> Unit = {},
    onAddTag: (String, String) -> Unit = { _, _ -> },
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val view = LocalView.current
    val keyboardBottomInsetPx = WindowInsets.ime.getBottom(density)
    val keyboardMarginPx = with(density) { 96.dp.toPx() }
    val scrollAnimationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<Float>())
    val tagPreviewEffectsSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val tagPreviewSpatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<Float>())
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

    // For bodies at/above LIVE_PREVIEW_DEBOUNCE_THRESHOLD_CHARS, only refresh highlighting once
    // typing has paused for LIVE_PREVIEW_DEBOUNCE_DELAY_MS, instead of re-parsing on every
    // keystroke. Below the threshold this mirrors state.markdown immediately (no debounce).
    var settledMarkdownForHighlighting by remember { mutableStateOf(state.markdown) }
    LaunchedEffect(state.markdown) {
        if (state.markdown.length < LIVE_PREVIEW_DEBOUNCE_THRESHOLD_CHARS) {
            settledMarkdownForHighlighting = state.markdown
        } else {
            kotlinx.coroutines.delay(LIVE_PREVIEW_DEBOUNCE_DELAY_MS)
            settledMarkdownForHighlighting = state.markdown
        }
    }

    val visualTransformation =
        remember(displayMode, styler, settledMarkdownForHighlighting) {
            if (displayMode == MarkdownEditorDisplayMode.LivePreview) {
                MarkdownVisualTransformation(styler, settledSource = settledMarkdownForHighlighting)
            } else {
                VisualTransformation.None
            }
        }
    var focused by remember { mutableStateOf(false) }
    var editorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    // A `---` line is hidden entirely by the live-preview transformation, so the rule itself is
    // painted here across the blank line that is left behind. Resolving the offsets outside the
    // draw phase keeps parsing off the draw path, and bodies without any dash run never parse at
    // all (markdownHorizontalRuleLineStarts short-circuits on them).
    val horizontalRuleTransformedOffsets =
        remember(displayMode, visualTransformation, state.markdown) {
            if (displayMode != MarkdownEditorDisplayMode.LivePreview) {
                emptyList()
            } else {
                val ruleLineStarts = markdownHorizontalRuleLineStarts(state.markdown)
                if (ruleLineStarts.isEmpty()) {
                    emptyList()
                } else {
                    // filter() is memoized on the source, so this reuses the parse the text field
                    // itself needs for the same frame.
                    val transformedText = visualTransformation.filter(AnnotatedString(state.markdown))
                    if (transformedText.text.text == state.markdown) {
                        // Nothing was hidden, so highlighting is off for this body (over
                        // LIVE_PREVIEW_HIGHLIGHT_MAX_CHARS, or still mid-debounce) and the dashes
                        // are showing as plain text. Painting a rule over them would be noise.
                        emptyList()
                    } else {
                        ruleLineStarts.map { lineStart ->
                            transformedText.offsetMapping.originalToTransformed(lineStart)
                        }
                    }
                }
            }
        }
    val horizontalRuleColor = MaterialTheme.colorScheme.outlineVariant
    val horizontalRuleStrokeWidthPx = with(density) { 1.dp.toPx() }
    val activeTagToken =
        remember(state.markdown, state.textFieldValue.selection) {
            activeHashTagToken(
                markdown = state.markdown,
                cursor = state.textFieldValue.selection.end,
            )
        }
    val activeTagColor =
        remember(activeTagToken?.tokenStart) {
            activeTagToken
                ?.tag
                ?.lowercase()
                ?.let { tagKey -> TagPalette.defaultFor(tagKey) }
                ?: Color.Transparent
        }
    val activeTagAlreadyAssigned =
        remember(activeTagToken?.tag, assignedTags) {
            activeTagToken?.tag?.let { activeTag ->
                assignedTags.any { assignedTag -> assignedTag.equals(activeTag, ignoreCase = true) }
            } ?: false
        }

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
            val layoutResult = textLayoutResult
            val layoutText = layoutResult?.layoutInput?.text?.text
            val cursorRect =
                if (layoutResult != null && layoutText == transformedText.text.text) {
                    runCatching {
                        layoutResult.getCursorRect(transformedCursor.coerceIn(0, layoutText.length))
                    }.getOrNull()
                } else {
                    null
                }
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

    Column(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = state.textFieldValue,
            onValueChange = { value ->
                state.update(
                    value = value,
                    cleanUpEmptyMarkdownWrappers = displayMode == MarkdownEditorDisplayMode.LivePreview,
                    // Only live preview hides the dashes, so only live preview needs the cursor
                    // moved off the finished rule. Markdown-code mode stays a literal text editor.
                    breakLineAfterHorizontalRule = displayMode == MarkdownEditorDisplayMode.LivePreview,
                )
            },
            textStyle = editorTextStyle,
            visualTransformation = visualTransformation,
            // capitalization is intentionally constant: switching it at runtime (it used to
            // flip to Words while state.shouldCapitalizeNextInputInEmptyInlineWrapper was true)
            // forces Compose to renegotiate the IME session, which visibly hid and reshowed the
            // keyboard every time typing began inside an empty formatting marker at a sentence
            // start. MarkdownEditorState.update() now capitalizes that first character itself.
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default,
                ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            onTextLayout = { textLayoutResult = it },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp)
                    .drawBehind {
                        val layoutResult = textLayoutResult ?: return@drawBehind
                        val layoutTextLength = layoutResult.layoutInput.text.length
                        horizontalRuleTransformedOffsets.forEach { transformedOffset ->
                            // The layout can lag a keystroke behind the offsets computed above; an
                            // out-of-range offset just skips this frame rather than crashing.
                            if (transformedOffset > layoutTextLength) {
                                return@forEach
                            }
                            val lineIndex = layoutResult.getLineForOffset(transformedOffset)
                            val lineCenterY =
                                (layoutResult.getLineTop(lineIndex) + layoutResult.getLineBottom(lineIndex)) / 2f
                            drawLine(
                                color = horizontalRuleColor,
                                start = Offset(x = 0f, y = lineCenterY),
                                end = Offset(x = size.width, y = lineCenterY),
                                strokeWidth = horizontalRuleStrokeWidthPx,
                            )
                        }
                    }.semantics { contentDescription = bodyPlaceholder }
                    .onGloballyPositioned { editorCoordinates = it }
                    .focusRequester(focusRequester)
                    .pointerInput(onStylusInput) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.any { change -> change.type == PointerType.Stylus }) {
                                    onStylusInput()
                                }
                            }
                        }
                    }.onFocusChanged {
                        focused = it.isFocused
                        onFocusChanged(it.isFocused)
                    },
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
        AnimatedVisibility(
            visible = activeTagToken != null && !activeTagAlreadyAssigned,
            enter = fadeIn(tagPreviewEffectsSpec) + scaleIn(tagPreviewSpatialSpec, initialScale = 0.92f),
            exit = fadeOut(tagPreviewEffectsSpec) + scaleOut(tagPreviewSpatialSpec, targetScale = 0.92f),
        ) {
            val previewTag = activeTagToken?.tag.orEmpty()
            TagChipFilled(
                tag = previewTag,
                color = activeTagColor,
                compact = true,
                highlighted = true,
                leadingIconName = "add",
                highlightedIconName = null,
                onClick = { onAddTag(previewTag, paletteHex(activeTagColor)) },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private data class ActiveHashTagToken(
    val tag: String,
    val tokenStart: Int,
)

private fun activeHashTagToken(
    markdown: String,
    cursor: Int,
): ActiveHashTagToken? {
    val boundedCursor = cursor.coerceIn(0, markdown.length)
    if (boundedCursor < 2) return null

    var tokenStart = boundedCursor - 1
    while (tokenStart >= 0 && !markdown[tokenStart].isWhitespace()) {
        if (markdown[tokenStart] == '#') {
            break
        }
        tokenStart--
    }
    if (tokenStart < 0 || markdown[tokenStart] != '#') return null
    if (tokenStart > 0 && !markdown[tokenStart - 1].isWhitespace()) return null

    val token = markdown.substring(tokenStart + 1, boundedCursor)
    if (token.isBlank()) return null
    val validToken =
        token.all { character ->
            character.isLetterOrDigit() || character == '_' || character == '-'
        }
    return if (validToken) {
        ActiveHashTagToken(
            tag = token,
            tokenStart = tokenStart,
        )
    } else {
        null
    }
}
