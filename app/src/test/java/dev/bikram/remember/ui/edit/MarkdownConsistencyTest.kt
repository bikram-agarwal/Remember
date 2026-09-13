@file:Suppress("ktlint:standard:function-expression-body")

package dev.bikram.remember.ui.edit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import dev.bikram.remember.ui.common.MarkdownBlockKind
import dev.bikram.remember.ui.common.MarkdownInlineInteraction
import dev.bikram.remember.ui.common.MarkdownInlineInteractionBuilder
import dev.bikram.remember.ui.common.MarkdownInlineProjection
import dev.bikram.remember.ui.common.MarkdownStyler
import dev.bikram.remember.ui.common.markdownCardPreview
import dev.bikram.remember.ui.common.parseMarkdownDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownConsistencyTest {
    private val styler =
        MarkdownStyler(
            bodyStyle = TextStyle.Default,
            linkColor = Color.Blue,
            codeBackground = Color.LightGray,
            quoteColor = Color.Gray,
            quoteBarColor = Color.Blue,
            syntaxMarkerColor = Color.Gray,
            headingOneBaseStyle = TextStyle.Default,
            headingTwoBaseStyle = TextStyle.Default,
            headingThreeBaseStyle = TextStyle.Default,
        )
    private val formats = listOf(MarkdownInlineFormat.BOLD, MarkdownInlineFormat.ITALIC, MarkdownInlineFormat.UNDERLINE, MarkdownInlineFormat.STRIKETHROUGH)

    @Test
    fun everyFormatSubsetAndToggleOrderSurvivesEachTypedCharacterAndReopening() {
        val orders = formatOrders(emptyList(), formats)
        assertEquals(65, orders.size)
        for (order in orders) {
            val state = MarkdownEditorState()
            order.forEach { toggle(state, it) }
            var expected = ""
            for (character in "Word \t") {
                input(state, character.toString())
                expected += character
                assertInlineConsistency(state.markdown, expected, order.toSet())
                assertEquals("Toolbar for $order", order.toSet(), state.inlineFormats)
            }
            val reopened = MarkdownEditorState(state.markdown)
            val wordStart = reopened.markdown.indexOf("Word")
            reopened.textFieldState.edit { selection = TextRange(wordStart + 2) }
            assertEquals("Reopened toolbar for $order", order.toSet(), reopened.inlineFormats)
            assertInlineConsistency(reopened.markdown, expected, order.toSet())
        }
    }

    @Test
    fun eachFormatCanBeDisabledIndependentlyInEveryToggleOrder() {
        for (order in formatOrders(emptyList(), formats).filter { it.isNotEmpty() }) {
            for (removed in order) {
                val state = MarkdownEditorState()
                order.forEach { toggle(state, it) }
                input(state, "Word ")
                val beforeToggle = state.markdown
                toggle(state, removed)
                val afterToggle = state.markdown
                input(state, "next ")
                assertEquals("$order removing $removed: ${state.markdown}", order.toSet() - removed, state.inlineFormats)
                val preview = MarkdownOutputTransformation(styler).preview(state.markdown).text
                val saved = styler.markdownInlineAnnotatedString(state.markdown)
                assertEquals("$order removing $removed: ${state.markdown}", "Word next ", preview.text)
                assertEquals(preview.text, saved.text)
                for (index in saved.indices) {
                    val expected = if (index < 5) order.toSet() else order.toSet() - removed
                    // Ending a format may move the separating space outside its wrapper.
                    // Preserve that existing command policy while checking every rendered style.
                    if (index != 4) assertFormats("$order removing $removed at $index: $beforeToggle -> $afterToggle -> ${state.markdown}", resolvedStyle(saved, index), expected)
                    assertEquals(resolvedStyle(preview, index), resolvedStyle(saved, index))
                }
            }
        }
    }

    @Test
    fun nestedWhitespaceAndLiteralSyntaxAgreeAcrossAllSurfaces() {
        val cases =
            linkedMapOf(
                "**bold and *both ***" to "bold and both ",
                "*italic and **both ***" to "italic and both ",
                "~~<u>***Word \t***</u>~~" to "Word \t",
                "<u>~~under strike ~~</u> ~~strike~~" to "under strike  strike",
                "~~unfinished" to "~~unfinished",
                "~~ leading ~~" to "~~ leading ~~",
                "*italic\nnext*" to "*italic\nnext*",
                "~~strike\nnext~~" to "~~strike\nnext~~",
                "<u>under\nnext</u>" to "<u>under\nnext</u>",
                "[split\nlabel](example.com)" to "[split\nlabel](example.com)",
                "`**literal** ~~text~~`" to "**literal** ~~text~~",
                "**before `*literal*` after**" to "before *literal* after",
                "*[Word](https://example.com/a*b) next*" to "Word next",
                "**[Word](https://example.com/a**b) next**" to "Word next",
            )
        for ((source, expected) in cases) assertInlineConsistency(source, expected)
    }

    @Test
    fun nestedUnderlineAndStrikeOwnTheirClosersAndIgnoreLiteralMarkers() {
        val cases =
            linkedMapOf(
                "<u>outer <u>inner</u> end</u>" to "outer inner end",
                "<U>outer <u>inner</U> end</u>" to "outer inner end",
                "~~outer ~~inner~~ end~~" to "outer inner end",
                "~~***<u>~~item 1~~</u>***~~" to "item 1",
                "~~<u>~~<u>text</u>~~</u>~~" to "text",
                "~~before `a~~b` after~~" to "before a~~b after",
                "<u>before `</u>` after</u>" to "before </u> after",
                "<u>before [<u>label</u>](example.com/</u>) after</u>" to "before label after",
                "~~before [~~label~~](example.com/a~~b) after~~" to "before label after",
                "***<u>outer <u>inner </u> end </u>***" to "outer inner  end ",
                "~~unfinished and ~~complete~~" to "~~unfinished and complete",
                "<u>unfinished and <u>complete</u>" to "<u>unfinished and complete",
            )
        for ((source, expected) in cases) assertInlineConsistency(source, expected)
        assertInlineConsistency("~~***<u>~~item 1~~</u>***~~", "item 1", formats.toSet())
        assertInlineConsistency("<u>outer <u>inner</u> end</u>", "outer inner end", setOf(MarkdownInlineFormat.UNDERLINE))
        assertInlineConsistency("~~outer ~~inner~~ end~~", "outer inner end", setOf(MarkdownInlineFormat.STRIKETHROUGH))
    }

    @Test(timeout = 5_000)
    fun deeplyNestedUnderlineDoesNotUseTheCallStackOrLeaveTagsVisible() {
        val source = "<u>".repeat(2_000) + "Word" + "</u>".repeat(2_000)
        val projection = MarkdownInlineProjection(source)
        assertEquals("Word", projection.text)
        assertEquals(2_000, projection.spans.size)
        assertEquals(6_000, projection.sourceOffsets[0])
        val unmatched = "<u>".repeat(2_000) + "Word"
        assertEquals(unmatched, MarkdownInlineProjection(unmatched).text)
    }

    @Test
    fun formattedLinksKeepTheirLabelStylesUrlAndSourceHitTargets() {
        val source = "Before [***<u>~~Word ~~</u>***](example.com/path) after"
        val expected = "Before Word  after"
        assertInlineConsistency(source, expected)
        val saved = styler.markdownInlineAnnotatedString(source)
        val links = saved.getLinkAnnotations(0, saved.length)
        assertEquals(1, links.size)
        assertEquals(7, links.single().start)
        assertEquals(12, links.single().end)
        val map = MarkdownInlineInteractionBuilder(source, 31).build()
        val interaction = map.interactionAt(8) as MarkdownInlineInteraction.Link
        assertEquals("https://example.com/path", interaction.link.url)
        assertEquals(31 + source.indexOf('['), interaction.link.markdownOffset)
        assertEquals(31 + source.indexOf("***"), interaction.link.textStartOffset)
        assertEquals(31 + source.indexOf("](example"), interaction.link.textEndOffset)
        assertTrue(map.interactionAt(0) is MarkdownInlineInteraction.Text)
        assertTrue(map.interactionAt(12) is MarkdownInlineInteraction.Text)
        for (index in 7 until 12) assertFormats(source, resolvedStyle(saved, index), formats.toSet())
    }

    @Test
    fun fencedCodeStaysLiteralInEditorCardsAndToolbarContext() {
        val code = "**literal** [link](example.com)\n---\n- [x] task"
        for (closing in listOf("\n```", "")) {
            val source = "```\n$code$closing"
            val expected = "\n$code" + if (closing.isEmpty()) "" else "\n"
            val preview = MarkdownOutputTransformation(styler).preview(source).text
            val card = markdownCardPreview(source, styler)
            assertEquals(expected, preview.text)
            assertEquals(expected, card.text.text)
            assertTrue(card.interactions.links.isEmpty())
            assertTrue(card.text.getLinkAnnotations(0, card.text.length).isEmpty())
            val state = MarkdownEditorState(source)
            state.textFieldState.edit { selection = TextRange(source.indexOf("link") + 1) }
            assertTrue(state.isCodeBlock)
            assertFalse(state.isBold)
            assertFalse(state.isChecklist)
            assertNull(state.selectedLinkUrl)
            val visibleLiteral = card.text.text.indexOf("literal")
            assertEquals(FontFamily.Monospace, resolvedStyle(card.text, visibleLiteral).fontFamily)
            assertEquals(source.indexOf("literal"), card.interactions.sourceOffsetByVisibleOffset[visibleLiteral])
        }
    }

    @Test
    fun blockClassificationAndContentPositionsAreShared() {
        val cases =
            listOf(
                "###   **Word **" to MarkdownBlockKind.Heading,
                "  - **Word **" to MarkdownBlockKind.Bullet,
                "  12) **Word **" to MarkdownBlockKind.Numbered,
                "  - [ ] **Word **" to MarkdownBlockKind.Checklist,
                "> **Word **" to MarkdownBlockKind.Quote,
            )
        for ((source, kind) in cases) {
            val syntax = parseMarkdownDocument(source)
            assertEquals(kind, syntax.lines.single().kind)
            assertEquals(source.indexOf("**"), syntax.lines.single().contentStart)
            val card = markdownCardPreview(source, styler)
            val visibleStart = card.text.text.indexOf("Word ")
            assertTrue(visibleStart >= 0)
            assertEquals(source.indexOf("Word "), card.interactions.sourceOffsetByVisibleOffset[visibleStart])
            val preview = MarkdownOutputTransformation(styler).preview(source)
            val previewStart = preview.text.text.indexOf("Word ")
            assertEquals(source.indexOf("Word "), preview.transformedToOriginal(previewStart))
            val state = MarkdownEditorState(source)
            state.textFieldState.edit { selection = TextRange(source.indexOf("Word") + 1) }
            assertEquals(kind == MarkdownBlockKind.Heading, state.headingLevel == 3)
            assertEquals(kind == MarkdownBlockKind.Bullet, state.isBulletList)
            assertEquals(kind == MarkdownBlockKind.Numbered, state.isNumberedList)
            assertEquals(kind == MarkdownBlockKind.Checklist, state.isChecklist)
            assertEquals(kind == MarkdownBlockKind.Quote, state.isQuote)
            assertTrue(state.isBold)
        }
    }

    @Test
    fun checkedCardAddsStrikethroughWithoutReparsingSyntheticMarkdown() {
        val source = "- [x] <u>**Word **</u>"
        val card = markdownCardPreview(source, styler)
        assertEquals("\u2611 Word ", card.text.text)
        for (index in 2 until card.text.length) {
            assertFormats(source, resolvedStyle(card.text, index), setOf(MarkdownInlineFormat.BOLD, MarkdownInlineFormat.UNDERLINE, MarkdownInlineFormat.STRIKETHROUGH))
        }
        assertEquals(source.indexOf("Word"), card.interactions.sourceOffsetByVisibleOffset[2])
    }

    @Test
    fun checkingFullyFormattedParentKeepsItsTextAndChildIntact() {
        val source = "- [ ] ***<u>~~item 1~~</u>***\n  - [ ] child 1\n- [ ] item 2"
        val checked = source.withChecklistLineToggled(0, true)
        val expected = "\u2611 item 1\n  \u2610 child 1\n\u2610 item 2"
        val card = markdownCardPreview(checked, styler)
        val preview = MarkdownOutputTransformation(styler).preview(checked)

        assertEquals(expected, card.text.text)
        assertEquals(expected, preview.text.text)
        assertEquals(source.replaceFirst("[ ]", "[x]"), checked)
        for (index in 2 until 8) {
            assertFormats(checked, resolvedStyle(card.text, index), formats.toSet())
            assertFormats(checked, resolvedStyle(preview.text, index), formats.toSet())
            assertEquals(checked.indexOf("item 1") + index - 2, card.interactions.sourceOffsetByVisibleOffset[index])
        }
        assertEquals(source, checked.withChecklistLineToggled(0, false))
    }

    @Test
    fun checklistCompletionPreservesEveryFormatSubsetAndToggleOrderAcrossSurfaces() {
        for (order in formatOrders(emptyList(), formats)) {
            val state = MarkdownEditorState()
            order.forEach { toggle(state, it) }
            input(state, "item \t")
            val label = state.markdown
            val source = "- [ ] $label"
            var current = source
            for (checked in listOf(true, false, true, false)) {
                current = current.withChecklistLineToggled(0, checked)
                assertEquals("- [${if (checked) "x" else " "}] $label", current)
                assertEquals(current, current.withChecklistLineToggled(0, checked))
                val expectedFormats = if (checked) order.toSet() + MarkdownInlineFormat.STRIKETHROUGH else order.toSet()
                val preview = MarkdownOutputTransformation(styler).preview(current)
                val card = markdownCardPreview(current, styler)
                // The full note view supplies completion as a base decoration to renderInline.
                val saved = styler.renderInline(MarkdownInlineProjection(label), textDecoration = if (checked) TextDecoration.LineThrough else null)
                assertEquals("item \t", saved.text)
                assertEquals("${if (checked) "\u2611" else "\u2610"} item \t", preview.text.text)
                assertEquals(preview.text.text, card.text.text)
                for (index in saved.indices) {
                    assertFormats("Saved $order, checked=$checked", resolvedStyle(saved, index), expectedFormats)
                    assertFormats("Preview $order, checked=$checked", resolvedStyle(preview.text, index + 2), expectedFormats)
                    assertFormats("Card $order, checked=$checked", resolvedStyle(card.text, index + 2), expectedFormats)
                }
                val reopened = MarkdownEditorState(current)
                reopened.textFieldState.edit { selection = TextRange(current.indexOf("item") + 2) }
                // A completion strike is visual; the toolbar still describes authored formatting.
                assertEquals(order.toSet(), reopened.inlineFormats)
            }
            assertEquals(source, current)
        }
    }

    @Test
    fun checkedLabelsKeepPartialFormattingLiteralCodeLinksAndSourceOffsets() {
        val label = "~~old~~ <u>under</u> `~~literal~~` [***link***](example.com/a~~b)"
        val source = "- [ ] $label"
        val checked = source.withChecklistLineToggled(0, true)
        val card = markdownCardPreview(checked, styler)
        val preview = MarkdownOutputTransformation(styler).preview(checked)
        val saved = styler.renderInline(MarkdownInlineProjection(label), textDecoration = TextDecoration.LineThrough)
        assertEquals("old under ~~literal~~ link", saved.text)
        assertEquals("\u2611 ${saved.text}", card.text.text)
        assertEquals(card.text.text, preview.text.text)
        for (index in saved.indices) {
            assertTrue(resolvedStyle(saved, index).textDecoration!!.contains(TextDecoration.LineThrough))
            assertEquals(resolvedStyle(saved, index), resolvedStyle(card.text, index + 2))
            assertEquals(resolvedStyle(saved, index), resolvedStyle(preview.text, index + 2))
        }
        val visibleLinkStart = card.text.text.indexOf("link")
        val interaction = card.interactions.interactionAt(visibleLinkStart) as MarkdownInlineInteraction.Link
        assertEquals("https://example.com/a~~b", interaction.link.url)
        assertEquals(checked.indexOf('[', 6), interaction.link.markdownOffset)
        assertEquals(source, checked.withChecklistLineToggled(0, false))
    }

    @Test
    fun reversedSelectionReplacementAndUndoPreserveInterpretation() {
        val state = MarkdownEditorState()
        formats.forEach { toggle(state, it) }
        input(state, "Word ")
        val original = state.markdown
        val start = original.indexOf("Word")
        state.textFieldState.edit { selection = TextRange(start + 4, start) }
        input(state, "Pasted")
        assertInlineConsistency(state.markdown, "Pasted ", formats.toSet())
        state.undo()
        assertEquals(original, state.markdown)
        assertInlineConsistency(state.markdown, "Word ", formats.toSet())
        state.redo()
        assertInlineConsistency(state.markdown, "Pasted ", formats.toSet())
    }

    @Test
    fun selectionsAcrossWrappersPreserveTextAndOnlyToggleTheRequestedStyle() {
        val cases =
            listOf(
                Triple("🙂~~Xa BW~~o**r**d", TextRange(6, 8), MarkdownInlineFormat.BOLD),
                Triple("*<u>🙂</u><u>🙂🙂🙂</u>r*", TextRange(9, 0), MarkdownInlineFormat.STRIKETHROUGH),
                Triple("A**lph a **B~~e~~~~ta~~", TextRange(10, 0), MarkdownInlineFormat.ITALIC),
            )
        for ((source, selection, format) in cases) {
            val state = MarkdownEditorState(source)
            val projection = MarkdownInlineProjection(source, parseMarkdownDocument(source).spans)
            val before = styler.markdownInlineAnnotatedString(source)
            state.textFieldState.edit {
                this.selection = TextRange(projection.sourceOffsets[selection.start], projection.sourceOffsets[selection.end])
            }
            toggle(state, format)
            assertInlineConsistency(state.markdown, before.text)
            val after = styler.markdownInlineAnnotatedString(state.markdown)
            for (index in before.indices) {
                if (before[index].isWhitespace()) continue
                val existing = resolvedStyle(before, index)
                val expected =
                    buildSet {
                        if (existing.fontWeight == FontWeight.Bold) add(MarkdownInlineFormat.BOLD)
                        if (existing.fontStyle == FontStyle.Italic) add(MarkdownInlineFormat.ITALIC)
                        if (existing.textDecoration?.contains(TextDecoration.Underline) == true) add(MarkdownInlineFormat.UNDERLINE)
                        if (existing.textDecoration?.contains(TextDecoration.LineThrough) == true) add(MarkdownInlineFormat.STRIKETHROUGH)
                        if (index in selection.min until selection.max) add(format)
                    }
                assertFormats("$source at $index: ${state.markdown}", resolvedStyle(after, index), expected)
            }
        }
    }

    @Test
    fun deferredTypingFormatsSurviveUndoRedoAndApplyToEveryInsertedCharacter() {
        val state = MarkdownEditorState()
        state.toggleBold()
        state.toggleUnderline()
        state.toggleStrikethrough()
        state.toggleItalic()
        input(state, "Word ")
        val source = state.markdown
        state.toggleUnderline()
        assertEquals(source, state.markdown)
        assertEquals(formats.toSet() - MarkdownInlineFormat.UNDERLINE, state.inlineFormats)
        state.undo()
        assertEquals(formats.toSet(), state.inlineFormats)
        state.redo()
        for (character in "next ") input(state, character.toString())
        assertInlineConsistency(state.markdown, "Word next ")
        val saved = styler.markdownInlineAnnotatedString(state.markdown)
        for (index in 5 until saved.length) {
            assertFormats(state.markdown, resolvedStyle(saved, index), formats.toSet() - MarkdownInlineFormat.UNDERLINE)
        }
    }

    @Test
    fun autocorrectionAndCompositionKeepPreviewAndSavedTextInAgreement() {
        val state = MarkdownEditorState()
        formats.forEach { toggle(state, it) }
        for (word in listOf("W", "Wor", "Wrod", "Word")) {
            state.textFieldState.editAsUserForTest(state.inputTransformation(livePreview = true)) {
                val start = imeComposition?.start ?: selection.min
                val end = imeComposition?.end ?: selection.max
                replace(start, end, word)
                selection = TextRange(start + word.length)
                setImeComposition(start, start + word.length)
            }
            assertInlineConsistency(state.markdown, word, formats.toSet())
            assertEquals(formats.toSet(), state.inlineFormats)
        }
    }

    @Test
    fun previewCutoffAndDebounceThresholdRemainUnchanged() {
        assertEquals(4_000, LIVE_PREVIEW_DEBOUNCE_THRESHOLD_CHARS)
        assertEquals(20_000, LIVE_PREVIEW_HIGHLIGHT_MAX_CHARS)
        for (size in listOf(3_999, 4_000, 20_000, 20_001)) {
            val source = "**" + "w".repeat(size - 4) + "**"
            val typing = MarkdownOutputTransformation(styler).preview(source)
            val settled = MarkdownOutputTransformation(styler, settledSource = source).preview(source)
            assertEquals(if (size < 4_000) size - 4 else size, typing.text.length)
            assertEquals(if (size <= 20_000) size - 4 else size, settled.text.length)
            if (size >= 4_000) {
                assertEquals(source, typing.text.text)
                assertTrue(typing.edits.isEmpty())
                assertEquals(size / 2, typing.originalToTransformed(size / 2))
            }
            if (size > 20_000) assertTrue(settled.edits.isEmpty())
        }
    }

    private fun assertInlineConsistency(
        source: String,
        expected: String,
        formats: Set<MarkdownInlineFormat>? = null,
    ) {
        val preview = MarkdownOutputTransformation(styler).preview(source)
        val saved = styler.markdownInlineAnnotatedString(source)
        val card = markdownCardPreview(source, styler)
        val map = MarkdownInlineInteractionBuilder(source, 0).build()
        assertEquals(source, expected, preview.text.text)
        assertEquals(source, expected, saved.text)
        assertEquals(source, expected, card.text.text)
        assertEquals(expected.length + 1, map.sourceOffsetByVisibleOffset.size)
        assertEquals(source.length, map.sourceOffsetByVisibleOffset.last())
        for (index in saved.indices) {
            val savedStyle = resolvedStyle(saved, index)
            assertEquals("Style at $index in $source", savedStyle, resolvedStyle(preview.text, index))
            assertEquals("Card style at $index in $source", savedStyle, resolvedStyle(card.text, index))
            val sourceOffset = map.sourceOffsetByVisibleOffset[index]
            assertEquals(expected[index], source[sourceOffset])
            assertEquals(sourceOffset, card.interactions.sourceOffsetByVisibleOffset[index])
            assertEquals(index, preview.originalToTransformed(sourceOffset))
            if (formats != null) assertFormats(source, savedStyle, formats)
        }
    }

    private fun assertFormats(
        message: String,
        style: SpanStyle,
        expected: Set<MarkdownInlineFormat>,
    ) {
        assertEquals(message, MarkdownInlineFormat.BOLD in expected, style.fontWeight == FontWeight.Bold)
        assertEquals(message, MarkdownInlineFormat.ITALIC in expected, style.fontStyle == FontStyle.Italic)
        assertEquals(message, MarkdownInlineFormat.UNDERLINE in expected, style.textDecoration?.contains(TextDecoration.Underline) == true)
        assertEquals(message, MarkdownInlineFormat.STRIKETHROUGH in expected, style.textDecoration?.contains(TextDecoration.LineThrough) == true)
    }

    private fun resolvedStyle(
        text: AnnotatedString,
        index: Int,
    ): SpanStyle {
        return text.spanStyles.filter { index in it.start until it.end }.fold(SpanStyle()) { style, range -> style.merge(range.item) }
    }

    private fun formatOrders(
        prefix: List<MarkdownInlineFormat>,
        remaining: List<MarkdownInlineFormat>,
    ): List<List<MarkdownInlineFormat>> {
        return listOf(prefix) + remaining.flatMap { formatOrders(prefix + it, remaining - it) }
    }

    private fun toggle(
        state: MarkdownEditorState,
        format: MarkdownInlineFormat,
    ) {
        when (format) {
            MarkdownInlineFormat.BOLD -> state.toggleBold()
            MarkdownInlineFormat.ITALIC -> state.toggleItalic()
            MarkdownInlineFormat.UNDERLINE -> state.toggleUnderline()
            MarkdownInlineFormat.STRIKETHROUGH -> state.toggleStrikethrough()
            MarkdownInlineFormat.INLINE_CODE -> state.toggleInlineCode()
        }
    }

    private fun input(
        state: MarkdownEditorState,
        text: String,
    ) {
        state.textFieldState.editAsUserForTest(state.inputTransformation(livePreview = true)) {
            val start = selection.min
            replace(start, selection.max, text)
            selection = TextRange(start + text.length)
        }
    }
}
