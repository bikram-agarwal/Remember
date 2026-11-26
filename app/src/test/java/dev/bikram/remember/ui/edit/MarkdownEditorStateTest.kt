package dev.bikram.remember.ui.edit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class MarkdownEditorStateTest {
    @Test
    fun boldWithSelectionWrapsSelectedTextAndKeepsSelectionInsideMarkers() {
        val state = MarkdownEditorState("test")
        state.update(TextFieldValue("test", selection = TextRange(0, 4)))

        state.toggleBold()

        assertEquals("**test**", state.markdown)
        assertEquals(TextRange(2, 6), state.textFieldValue.selection)
    }

    @Test
    fun boldWithoutSelectionInsertsMarkersAndPlacesCursorInMiddle() {
        val state = MarkdownEditorState()

        state.toggleBold()

        assertEquals("****", state.markdown)
        assertEquals(TextRange(2), state.textFieldValue.selection)
    }

    @Test
    fun backspaceInsideEmptyBoldMarkersClearsFormatting() {
        val state = MarkdownEditorState()
        state.toggleBold()

        state.update(TextFieldValue("***", selection = TextRange(1)))

        assertEquals("", state.markdown)
        assertEquals(TextRange(0), state.textFieldValue.selection)
        assertFalse(state.isBold)
    }

    @Test
    fun completingHorizontalRuleMovesCursorToFreshLineBelowIt() {
        val state = MarkdownEditorState("--")
        state.update(TextFieldValue("--", selection = TextRange(2)))

        state.update(TextFieldValue("---", selection = TextRange(3)))

        assertEquals("---\n", state.markdown)
        assertEquals(TextRange(4), state.textFieldValue.selection)
    }

    @Test
    fun completingHorizontalRuleKeepsFollowingContentOnItsOwnLine() {
        val state = MarkdownEditorState("intro\n--\nafter")
        state.update(TextFieldValue("intro\n--\nafter", selection = TextRange(8)))

        state.update(TextFieldValue("intro\n---\nafter", selection = TextRange(9)))

        assertEquals("intro\n---\n\nafter", state.markdown)
        assertEquals(TextRange(10), state.textFieldValue.selection)
    }

    @Test
    fun typingBeyondACompletedHorizontalRuleDoesNotBreakTheLineAgain() {
        val state = MarkdownEditorState("---")
        state.update(TextFieldValue("---", selection = TextRange(3)))

        state.update(TextFieldValue("----", selection = TextRange(4)))

        assertEquals("----", state.markdown)
        assertEquals(TextRange(4), state.textFieldValue.selection)
    }

    @Test
    fun twoDashesDoNotBreakTheLine() {
        val state = MarkdownEditorState("-")
        state.update(TextFieldValue("-", selection = TextRange(1)))

        state.update(TextFieldValue("--", selection = TextRange(2)))

        assertEquals("--", state.markdown)
        assertEquals(TextRange(2), state.textFieldValue.selection)
    }

    @Test
    fun pastedHorizontalRuleDoesNotBreakTheLine() {
        val state = MarkdownEditorState()

        state.update(TextFieldValue("---", selection = TextRange(3)))

        assertEquals("---", state.markdown)
        assertEquals(TextRange(3), state.textFieldValue.selection)
    }

    @Test
    fun dashTypedMidLineDoesNotBreakTheLine() {
        val state = MarkdownEditorState("-- tail")
        state.update(TextFieldValue("-- tail", selection = TextRange(2)))

        state.update(TextFieldValue("--- tail", selection = TextRange(3)))

        assertEquals("--- tail", state.markdown)
        assertEquals(TextRange(3), state.textFieldValue.selection)
    }

    @Test
    fun dashRunCompletedInsideCodeBlockDoesNotBreakTheLine() {
        val state = MarkdownEditorState("```\n--\n```")
        state.update(TextFieldValue("```\n--\n```", selection = TextRange(6)))

        state.update(TextFieldValue("```\n---\n```", selection = TextRange(7)))

        assertEquals("```\n---\n```", state.markdown)
        assertEquals(TextRange(7), state.textFieldValue.selection)
    }

    @Test
    fun horizontalRuleLineBreakCanBeDisabledForMarkdownCodeMode() {
        val state = MarkdownEditorState("--")
        state.update(TextFieldValue("--", selection = TextRange(2)), breakLineAfterHorizontalRule = false)

        state.update(TextFieldValue("---", selection = TextRange(3)), breakLineAfterHorizontalRule = false)

        assertEquals("---", state.markdown)
        assertEquals(TextRange(3), state.textFieldValue.selection)
    }

    @Test
    fun backspaceAtStartOfLeadingNewlineDoesNotCrash() {
        val state = MarkdownEditorState("\n")
        state.update(TextFieldValue("\n", selection = TextRange(0)))

        state.update(TextFieldValue("", selection = TextRange(0)))

        assertEquals("", state.markdown)
        assertEquals(TextRange(0), state.textFieldValue.selection)
    }

    @Test
    fun boldWithSelectionInsideExistingBoldMarkersRemovesMarkers() {
        val state = MarkdownEditorState("**Test**")
        state.update(TextFieldValue("**Test**", selection = TextRange(2, 6)))

        state.toggleBold()

        assertEquals("Test", state.markdown)
        assertEquals(TextRange(0, 4), state.textFieldValue.selection)
    }

    @Test
    fun boldWithSelectionIncludingExistingBoldMarkersRemovesMarkers() {
        val state = MarkdownEditorState("**Test**")
        state.update(TextFieldValue("**Test**", selection = TextRange(0, 8)))

        state.toggleBold()

        assertEquals("Test", state.markdown)
        assertEquals(TextRange(0, 4), state.textFieldValue.selection)
    }

    @Test
    fun boldWithoutSelectionInsideEmptyBoldMarkersRemovesMarkers() {
        val state = MarkdownEditorState("****")
        state.update(TextFieldValue("****", selection = TextRange(2)))

        state.toggleBold()

        assertEquals("", state.markdown)
        assertEquals(TextRange(0), state.textFieldValue.selection)
    }

    @Test
    fun italicDoesNotTreatBoldMarkersAsItalicWrapper() {
        val state = MarkdownEditorState("**Test**")
        state.update(TextFieldValue("**Test**", selection = TextRange(2, 6)))

        state.toggleItalic()

        assertEquals("***Test***", state.markdown)
        assertEquals(TextRange(3, 7), state.textFieldValue.selection)
    }

    @Test
    fun boldToggledOffRightAfterTypedTextStepsOverExistingCloseMarkerInsteadOfInsertingNewOne() {
        val state = MarkdownEditorState("New ")
        state.toggleBold()
        state.update(TextFieldValue("New **bold line**", selection = TextRange(15)))

        state.toggleBold()

        assertEquals("New **bold line**", state.markdown)
        assertEquals(TextRange(17), state.textFieldValue.selection)
    }

    @Test
    fun boldToggledOffAfterTrailingSpaceRelocatesCloseMarkerBeforeTheSpace() {
        val state = MarkdownEditorState("New ")
        state.toggleBold()
        state.update(TextFieldValue("New **bold line **", selection = TextRange(16)))

        state.toggleBold()

        assertEquals("New **bold line** ", state.markdown)
        assertEquals(TextRange(18), state.textFieldValue.selection)
    }

    @Test
    fun boldToggledOffAfterTrailingSpaceThenMoreTypingProducesWellFormedMarkdown() {
        val state = MarkdownEditorState("New ")
        state.toggleBold()
        state.update(TextFieldValue("New **bold line **", selection = TextRange(16)))
        state.toggleBold()

        state.update(TextFieldValue("New **bold line** stops", selection = TextRange(23)))

        assertEquals("New **bold line** stops", state.markdown)
    }

    @Test
    fun boldToggledOffThenEnterThenMoreTextDoesNotLeaveStrayMarkersOnEitherLine() {
        val state = MarkdownEditorState("# highlights\nNew ")

        state.toggleBold()
        state.update(TextFieldValue("# highlights\nNew **bold line**", selection = TextRange(28)))
        state.toggleBold()
        state.update(TextFieldValue("# highlights\nNew **bold line** stops", selection = TextRange(36)))
        state.update(TextFieldValue("# highlights\nNew **bold line** stops\n", selection = TextRange(37)))
        state.update(TextFieldValue("# highlights\nNew **bold line** stops\nNext line", selection = TextRange(46)))

        assertEquals("# highlights\nNew **bold line** stops\nNext line", state.markdown)
    }

    @Test
    fun emptyBoldAtSentenceStartRequestsCapitalizedKeyboard() {
        val state = MarkdownEditorState()

        state.toggleBold()

        assertEquals(true, state.shouldCapitalizeNextInputInEmptyInlineWrapper)
    }

    @Test
    fun emptyBoldAfterExistingWordDoesNotRequestCapitalizedKeyboard() {
        val state = MarkdownEditorState("hello ")
        state.update(TextFieldValue("hello ", selection = TextRange(6)))

        state.toggleBold()

        assertEquals(false, state.shouldCapitalizeNextInputInEmptyInlineWrapper)
    }

    @Test
    fun typingInsideEmptyBoldAtSentenceStartCapitalizesFirstLetterWithoutKeyboardOptionsChanging() {
        val state = MarkdownEditorState()
        state.toggleBold()

        state.update(TextFieldValue("**a**", selection = TextRange(3)))

        assertEquals("**A**", state.markdown)
        assertEquals(TextRange(3), state.textFieldValue.selection)
    }

    @Test
    fun typingInsideEmptyBoldAfterExistingWordDoesNotForceCapitalization() {
        val state = MarkdownEditorState("hello ")
        state.update(TextFieldValue("hello ", selection = TextRange(6)))
        state.toggleBold()

        state.update(TextFieldValue("hello **a**", selection = TextRange(9)))

        assertEquals("hello **a**", state.markdown)
    }

    @Test
    fun emptyHeadingPrefixRequestsCapitalizedKeyboard() {
        val state = MarkdownEditorState()

        state.applyHeading(2)

        assertEquals(true, state.shouldCapitalizeNextInputInEmptyInlineWrapper)
    }

    @Test
    fun backspaceInsideEmptyHeadingPrefixRemovesFormattedLine() {
        val state = MarkdownEditorState("First\n")
        state.update(TextFieldValue("First\n", selection = TextRange(6)))
        state.applyHeading(3)

        state.update(TextFieldValue("First\n###", selection = TextRange(9)))

        assertEquals("First", state.markdown)
        assertEquals(TextRange(5), state.textFieldValue.selection)
    }

    @Test
    fun backspaceInsideEmptyBulletPrefixRemovesFormattedLine() {
        val state = MarkdownEditorState("First\n")
        state.update(TextFieldValue("First\n", selection = TextRange(6)))
        state.applyBulletList()

        state.update(TextFieldValue("First\n-", selection = TextRange(7)))

        assertEquals("First", state.markdown)
        assertEquals(TextRange(5), state.textFieldValue.selection)
    }

    @Test
    fun backspaceInsideEmptyChecklistPrefixRemovesFormattedLine() {
        val state = MarkdownEditorState("First\n")
        state.update(TextFieldValue("First\n", selection = TextRange(6)))
        state.applyChecklist()

        state.update(TextFieldValue("First\n- [ ]", selection = TextRange(11)))

        assertEquals("First", state.markdown)
        assertEquals(TextRange(5), state.textFieldValue.selection)
    }

    @Test
    fun backspaceInsideEmptyNumberedPrefixRemovesFormattedLine() {
        val state = MarkdownEditorState("First\n")
        state.update(TextFieldValue("First\n", selection = TextRange(6)))
        state.applyNumberedList()

        state.update(TextFieldValue("First\n1.", selection = TextRange(8)))

        assertEquals("First", state.markdown)
        assertEquals(TextRange(5), state.textFieldValue.selection)
    }

    @Test
    fun headingPrefixesCurrentLine() {
        val state = MarkdownEditorState("hello")
        state.update(TextFieldValue("hello", selection = TextRange(5)))

        state.applyHeading(2)

        assertEquals("## hello", state.markdown)
        assertEquals(TextRange(8), state.textFieldValue.selection)
    }

    @Test
    fun headingAtSameLevelRemovesHeadingPrefix() {
        val state = MarkdownEditorState("## hello")
        state.update(TextFieldValue("## hello", selection = TextRange(8)))

        state.applyHeading(2)

        assertEquals("hello", state.markdown)
        assertEquals(TextRange(5), state.textFieldValue.selection)
    }

    @Test
    fun headingAtDifferentLevelChangesHeadingPrefix() {
        val state = MarkdownEditorState("## hello")
        state.update(TextFieldValue("## hello", selection = TextRange(8)))

        state.applyHeading(3)

        assertEquals("### hello", state.markdown)
        assertEquals(TextRange(9), state.textFieldValue.selection)
    }

    @Test
    fun deletingBoldContentClearsEmptyFormatting() {
        val state = MarkdownEditorState("**BOLD**")

        state.update(TextFieldValue("****", selection = TextRange(2)))

        assertEquals("", state.markdown)
        assertEquals(TextRange(0), state.textFieldValue.selection)
        assertFalse(state.isBold)
    }

    @Test
    fun deletingInlineContentClearsEmptyFormattingAndPreservesSurroundingText() {
        val state = MarkdownEditorState("Start **BOLD** end")

        state.update(TextFieldValue("Start **** end", selection = TextRange(8)))

        assertEquals("Start  end", state.markdown)
        assertEquals(TextRange(6), state.textFieldValue.selection)
        assertFalse(state.isBold)
    }

    @Test
    fun deletingHeadingContentRemovesEmptyHeadingPrefix() {
        val state = MarkdownEditorState("## Heading")

        state.update(TextFieldValue("## ", selection = TextRange(3)))

        assertEquals("", state.markdown)
        assertEquals(TextRange(0), state.textFieldValue.selection)
    }

    @Test
    fun deletingHeadingContentKeepsSurroundingLines() {
        val state = MarkdownEditorState("Before\n## Heading\nAfter")

        state.update(TextFieldValue("Before\n## \nAfter", selection = TextRange(10)))

        assertEquals("Before\n\nAfter", state.markdown)
        assertEquals(TextRange(7), state.textFieldValue.selection)
    }

    @Test
    fun strikethroughWithSelectionWrapsSelectedText() {
        val state = MarkdownEditorState("test")
        state.update(TextFieldValue("test", selection = TextRange(0, 4)))

        state.toggleStrikethrough()

        assertEquals("~~test~~", state.markdown)
        assertEquals(TextRange(2, 6), state.textFieldValue.selection)
    }

    @Test
    fun underlineWithSelectionWrapsSelectedText() {
        val state = MarkdownEditorState("test")
        state.update(TextFieldValue("test", selection = TextRange(0, 4)))

        state.toggleUnderline()

        assertEquals("<u>test</u>", state.markdown)
        assertEquals(TextRange(3, 7), state.textFieldValue.selection)
    }

    @Test
    fun surroundSelectionAdjustsBoundariesToPreventInterleavedTags() {
        // Selection includes closing "**" of bold tag but not opening "**"
        // "**test**" selected as "test**" (indices 2 to 8)
        val state = MarkdownEditorState("**test**")
        state.update(TextFieldValue("**test**", selection = TextRange(2, 8)))

        state.toggleUnderline()

        assertEquals("**<u>test</u>**", state.markdown)
        assertEquals(TextRange(5, 9), state.textFieldValue.selection)
    }

    @Test
    fun pressingEnterAtEndOfUnderlineContinuesAfterClosingMarker() {
        val state = MarkdownEditorState("<u>text</u>")
        state.update(TextFieldValue("<u>text</u>", selection = TextRange(7)))

        state.update(TextFieldValue("<u>text\n</u>", selection = TextRange(8)))

        assertEquals("<u>text</u>\n", state.markdown)
        assertEquals(TextRange(12), state.textFieldValue.selection)
    }

    @Test
    fun pressingEnterAtEndOfUnderlineInsideBulletClosesUnderlineThenContinuesList() {
        val state = MarkdownEditorState("- <u>text</u>")
        state.update(TextFieldValue("- <u>text</u>", selection = TextRange(9)))

        state.update(TextFieldValue("- <u>text\n</u>", selection = TextRange(10)))

        assertEquals("- <u>text</u>\n- ", state.markdown)
        assertEquals(TextRange(16), state.textFieldValue.selection)
    }

    @Test
    fun backspaceAfterUnderlineClosingMarkerDeletesLastVisibleCharacter() {
        val state = MarkdownEditorState("<u>text</u>")

        state.update(TextFieldValue("<u>text</u", selection = TextRange(10)))

        assertEquals("<u>tex</u>", state.markdown)
        assertEquals(TextRange(6), state.textFieldValue.selection)
    }

    @Test
    fun backspaceInsideUnderlineClosingMarkerDeletesLastVisibleCharacter() {
        val state = MarkdownEditorState("<u>text</u>")
        state.update(TextFieldValue("<u>text</u>", selection = TextRange(10)))

        state.update(TextFieldValue("<u>text</>", selection = TextRange(9)))

        assertEquals("<u>tex</u>", state.markdown)
        assertEquals(TextRange(6), state.textFieldValue.selection)
    }

    @Test
    fun pressingEnterAtEndOfBoldContinuesAfterClosingMarker() {
        val state = MarkdownEditorState("**text**")
        state.update(TextFieldValue("**text**", selection = TextRange(6)))

        state.update(TextFieldValue("**text\n**", selection = TextRange(7)))

        assertEquals("**text**\n", state.markdown)
        assertEquals(TextRange(9), state.textFieldValue.selection)
    }

    @Test
    fun backspaceAfterBoldClosingMarkerDeletesLastVisibleCharacter() {
        val state = MarkdownEditorState("**text**")

        state.update(TextFieldValue("**text*", selection = TextRange(7)))

        assertEquals("**tex**", state.markdown)
        assertEquals(TextRange(5), state.textFieldValue.selection)
    }

    @Test
    fun inlineCodeWithoutSelectionInsertsBackticksAndPlacesCursorInMiddle() {
        val state = MarkdownEditorState()

        state.toggleInlineCode()

        assertEquals("``", state.markdown)
        assertEquals(TextRange(1), state.textFieldValue.selection)
    }

    @Test
    fun codeBlockWithoutSelectionInsertsFenceAndPlacesCursorInside() {
        val state = MarkdownEditorState()

        state.applyCodeBlock()

        assertEquals("```\n\n```", state.markdown)
        assertEquals(TextRange(4), state.textFieldValue.selection)
    }

    @Test
    fun checklistPrefixesCurrentLine() {
        val state = MarkdownEditorState("item")
        state.update(TextFieldValue("item", selection = TextRange(0)))

        state.applyChecklist()

        assertEquals("- [ ] item", state.markdown)
        assertEquals(TextRange(6), state.textFieldValue.selection)
    }

    @Test
    fun numberedListPrefixesSelectedLines() {
        val state = MarkdownEditorState("one\ntwo")
        state.update(TextFieldValue("one\ntwo", selection = TextRange(0, 7)))

        state.applyNumberedList()

        assertEquals("1. one\n2. two", state.markdown)
        assertEquals(TextRange(3, 13), state.textFieldValue.selection)
    }

    @Test
    fun bulletListActionNestsTopLevelBullet() {
        val state = MarkdownEditorState("- item")
        state.update(TextFieldValue("- item", selection = TextRange(0)))

        state.applyBulletList()

        assertEquals("  - item", state.markdown)
    }

    @Test
    fun numberedListActionNestsTopLevelNumberedItem() {
        val state = MarkdownEditorState("1. item")
        state.update(TextFieldValue("1. item", selection = TextRange(0)))

        state.applyNumberedList()

        assertEquals("  1. item", state.markdown)
    }

    @Test
    fun addingLinkToSelectionKeepsSelectedTextAsLabel() {
        val state = MarkdownEditorState("test")
        state.update(TextFieldValue("test", selection = TextRange(0, 4)))

        state.addOrUpdateLink(
            displayText = "",
            rawUrl = "example.com",
        )

        assertEquals("[test](https://example.com)", state.markdown)
        assertEquals(TextRange(1, 5), state.textFieldValue.selection)
    }

    @Test
    fun pressingEnterAfterBulletItemContinuesBulletList() {
        val state = MarkdownEditorState("- item")

        state.update(TextFieldValue("- item\n", selection = TextRange(7)))

        assertEquals("- item\n- ", state.markdown)
        assertEquals(TextRange(9), state.textFieldValue.selection)
    }

    @Test
    fun pressingEnterOnEmptyBulletEndsBulletList() {
        val state = MarkdownEditorState("- item\n- ")

        state.update(TextFieldValue("- item\n- \n", selection = TextRange(10)))

        assertEquals("- item\n\n", state.markdown)
        assertEquals(TextRange(8), state.textFieldValue.selection)
    }

    @Test
    fun pressingEnterAfterChecklistItemContinuesChecklist() {
        val state = MarkdownEditorState("- [ ] item")

        state.update(TextFieldValue("- [ ] item\n", selection = TextRange(11)))

        assertEquals("- [ ] item\n- [ ] ", state.markdown)
        assertEquals(TextRange(17), state.textFieldValue.selection)
    }

    @Test
    fun pressingEnterOnEmptyChecklistEndsChecklist() {
        val state = MarkdownEditorState("- [ ] item\n- [ ] ")

        state.update(TextFieldValue("- [ ] item\n- [ ] \n", selection = TextRange(18)))

        assertEquals("- [ ] item\n\n", state.markdown)
        assertEquals(TextRange(12), state.textFieldValue.selection)
    }

    @Test
    fun pressingEnterAfterNumberedItemContinuesNumberedList() {
        val state = MarkdownEditorState("1. item")

        state.update(TextFieldValue("1. item\n", selection = TextRange(8)))

        assertEquals("1. item\n2. ", state.markdown)
        assertEquals(TextRange(11), state.textFieldValue.selection)
    }

    @Test
    fun pressingEnterAfterNumberedItemContinuesWhenImeCommitsComposition() {
        val state = MarkdownEditorState("1. adress")
        val updatedMarkdown = "1. address\n"

        state.update(TextFieldValue(updatedMarkdown, selection = TextRange(updatedMarkdown.length)))

        val expectedMarkdown = "1. address\n2. "
        assertEquals(expectedMarkdown, state.markdown)
        assertEquals(TextRange(expectedMarkdown.length), state.textFieldValue.selection)
    }

    @Test
    fun pressingEnterAfterNestedBulletItemContinuesNestedBulletList() {
        val state = MarkdownEditorState("  - item")

        state.update(TextFieldValue("  - item\n", selection = TextRange(9)))

        assertEquals("  - item\n  - ", state.markdown)
        assertEquals(TextRange(13), state.textFieldValue.selection)
    }

    @Test
    fun pressingEnterOnEmptyNestedBulletOutdentsToTopLevelBullet() {
        val state = MarkdownEditorState("  - item\n  - ")

        state.update(TextFieldValue("  - item\n  - \n", selection = TextRange(14)))

        assertEquals("  - item\n- ", state.markdown)
        assertEquals(TextRange(11), state.textFieldValue.selection)
    }

    @Test
    fun pressingEnterAfterNestedChecklistItemContinuesNestedChecklist() {
        val state = MarkdownEditorState("  - [ ] item")

        state.update(TextFieldValue("  - [ ] item\n", selection = TextRange(13)))

        assertEquals("  - [ ] item\n  - [ ] ", state.markdown)
        assertEquals(TextRange(21), state.textFieldValue.selection)
    }

    @Test
    fun pressingEnterOnEmptyNestedChecklistOutdentsToTopLevelChecklist() {
        val state = MarkdownEditorState("  - [ ] item\n  - [ ] ")

        state.update(TextFieldValue("  - [ ] item\n  - [ ] \n", selection = TextRange(22)))

        assertEquals("  - [ ] item\n- [ ] ", state.markdown)
        assertEquals(TextRange(19), state.textFieldValue.selection)
    }

    @Test
    fun pressingEnterOnEmptyNestedNumberedItemOutdentsToNextTopLevelNumber() {
        val state = MarkdownEditorState("1. item\n  1. nested\n  2. ")

        state.update(TextFieldValue("1. item\n  1. nested\n  2. \n", selection = TextRange(28)))

        assertEquals("1. item\n  1. nested\n2. ", state.markdown)
        assertEquals(TextRange(23), state.textFieldValue.selection)
    }

    @Test
    fun pressingEnterOnEmptyNestedNumberedItemRenumbersFollowingTopLevelItems() {
        val state = MarkdownEditorState("1. First\n  1. Child 1\n  2. Child 2\n  3. \n2. Second\n3. Third")
        val updatedMarkdown = "1. First\n  1. Child 1\n  2. Child 2\n  3. \n\n2. Second\n3. Third"

        state.update(
            TextFieldValue(
                updatedMarkdown,
                selection = TextRange(updatedMarkdown.indexOf("\n\n2. Second") + 1),
            ),
        )

        val expectedMarkdown = "1. First\n  1. Child 1\n  2. Child 2\n2. \n3. Second\n4. Third"
        assertEquals(expectedMarkdown, state.markdown)
        assertEquals(TextRange(expectedMarkdown.indexOf("2. \n3. Second") + 3), state.textFieldValue.selection)
    }

    @Test
    fun pressingEnterOnEmptyNumberedItemEndsNumberedList() {
        val state = MarkdownEditorState("1. item\n2. ")

        state.update(TextFieldValue("1. item\n2. \n", selection = TextRange(12)))

        assertEquals("1. item\n\n", state.markdown)
        assertEquals(TextRange(9), state.textFieldValue.selection)
    }

    @Test
    fun boldActiveStateReflectsToggledEmptyWrapperAndTypingInside() {
        val state = MarkdownEditorState()
        assertFalse(state.isBold)

        state.toggleBold()
        assertEquals("****", state.markdown)
        assertEquals(TextRange(2), state.textFieldValue.selection)
        assertTrue(state.isBold)

        state.update(TextFieldValue("**hello**", selection = TextRange(4)))
        assertTrue(state.isBold)

        // Cursor at the close marker boundary
        state.update(TextFieldValue("**hello**", selection = TextRange(7)))
        assertTrue(state.isBold)

        // Cursor past the close marker is outside bold
        state.update(TextFieldValue("**hello**", selection = TextRange(9)))
        assertFalse(state.isBold)
    }

    @Test
    fun formattingActiveStatesDetectSelectionAndDelimiters() {
        val state = MarkdownEditorState("**bold** and *italic*")

        // Inside bold
        state.update(TextFieldValue("**bold** and *italic*", selection = TextRange(2, 6)))
        assertTrue(state.isBold)
        assertFalse(state.isItalic)

        // Exact match with delimiters
        state.update(TextFieldValue("**bold** and *italic*", selection = TextRange(0, 8)))
        assertTrue(state.isBold)

        // Inside italic
        state.update(TextFieldValue("**bold** and *italic*", selection = TextRange(15)))
        assertFalse(state.isBold)
        assertTrue(state.isItalic)

        // Outside formatting
        state.update(TextFieldValue("**bold** and *italic*", selection = TextRange(9, 12)))
        assertFalse(state.isBold)
        assertFalse(state.isItalic)
    }

    @Test
    fun boldItalicActiveStateActivatesBothBoldAndItalic() {
        val state = MarkdownEditorState("***bold-italic***")
        state.update(TextFieldValue("***bold-italic***", selection = TextRange(5)))

        assertTrue(state.isBold)
        assertTrue(state.isItalic)
    }

    @Test
    fun underlineStrikethroughAndInlineCodeActiveStatesWorkCorrectly() {
        val state = MarkdownEditorState("<u>under</u> ~~strike~~ `code`")

        state.update(TextFieldValue("<u>under</u> ~~strike~~ `code`", selection = TextRange(5)))
        assertTrue(state.isUnderline)
        assertFalse(state.isStrikethrough)
        assertFalse(state.isInlineCode)

        state.update(TextFieldValue("<u>under</u> ~~strike~~ `code`", selection = TextRange(16)))
        assertFalse(state.isUnderline)
        assertTrue(state.isStrikethrough)
        assertFalse(state.isInlineCode)

        state.update(TextFieldValue("<u>under</u> ~~strike~~ `code`", selection = TextRange(26)))
        assertFalse(state.isUnderline)
        assertFalse(state.isStrikethrough)
        assertTrue(state.isInlineCode)
    }

    @Test
    fun codeBlockActiveStateDetectsCursorAndLinesInsideFence() {
        val state = MarkdownEditorState("intro\n```\nval x = 1\n```\noutro")

        // Cursor in intro (outside code block)
        state.update(TextFieldValue("intro\n```\nval x = 1\n```\noutro", selection = TextRange(2)))
        assertFalse(state.isCodeBlock)

        // Cursor inside code block
        state.update(TextFieldValue("intro\n```\nval x = 1\n```\noutro", selection = TextRange(12)))
        assertTrue(state.isCodeBlock)

        // Cursor in outro (outside code block)
        state.update(TextFieldValue("intro\n```\nval x = 1\n```\noutro", selection = TextRange(24)))
        assertFalse(state.isCodeBlock)
    }

    @Test
    fun addOrUpdateDateInsertsChipMovesCursorAfterAndUpdatesExistingDateInPlace() {
        val state = MarkdownEditorState("Deadline: ")
        state.update(TextFieldValue("Deadline: ", selection = TextRange(10)))

        val initialDate = LocalDate.of(2026, 11, 24)
        state.addOrUpdateDate(initialDate, Locale.US)

        val expectedInserted = "Deadline: [11/24/2026](date:2026-11-24)"
        assertEquals(expectedInserted, state.markdown)
        assertEquals(TextRange(expectedInserted.length), state.textFieldValue.selection)
        assertNull(state.selectedDate)
        assertNull(state.selectedLinkUrl)

        // Undo & Redo round-trip
        state.undo()
        assertEquals("Deadline: ", state.markdown)
        state.redo()
        assertEquals(expectedInserted, state.markdown)

        // Move cursor inside the date chip -> selectedDate becomes active, selectedLinkUrl stays null
        state.update(TextFieldValue(expectedInserted, selection = TextRange(15)))
        assertEquals(initialDate, state.selectedDate)
        assertNull(state.selectedLinkUrl)

        // Updating in-place replaces the existing date chip without duplicating brackets
        val updatedDate = LocalDate.of(2026, 12, 31)
        state.addOrUpdateDate(updatedDate, Locale.US)
        val expectedUpdated = "Deadline: [12/31/2026](date:2026-12-31)"
        assertEquals(expectedUpdated, state.markdown)
        assertEquals(TextRange(expectedUpdated.length), state.textFieldValue.selection)
        assertNull(state.selectedDate)
    }

    @Test
    fun addDateOverPartialChipSelectionReplacesWholeChipInsteadOfCuttingIt() {
        val chip = "[11/24/2026](date:2026-11-24)"
        val state = MarkdownEditorState("A $chip B")
        state.update(TextFieldValue(state.markdown, selection = TextRange(0, 8)))

        state.addOrUpdateDate(LocalDate.of(2026, 12, 1), Locale.US)

        assertEquals("[12/01/2026](date:2026-12-01) B", state.markdown)
    }

    @Test
    fun addDateWithCursorInsideLinkLandsAfterTheLinkNotInsideItsLabel() {
        val state = MarkdownEditorState("See [docs](https://x.com) now")
        state.update(TextFieldValue(state.markdown, selection = TextRange(7)))

        state.addOrUpdateDate(LocalDate.of(2026, 12, 1), Locale.US)

        assertEquals("See [docs](https://x.com)[12/01/2026](date:2026-12-01) now", state.markdown)
    }

    @Test
    fun addDateOverSelectedPlainDateTextTurnsItIntoAChip() {
        val state = MarkdownEditorState("Due 11/24/2026 sharp")
        state.update(TextFieldValue(state.markdown, selection = TextRange(4, 14)))
        assertEquals("11/24/2026", state.selectedText())

        state.addOrUpdateDate(LocalDate.of(2026, 11, 24), Locale.US)

        assertEquals("Due [11/24/2026](date:2026-11-24) sharp", state.markdown)
    }

    @Test
    fun editingInsideDateChipLabelFallsBackToPlainEditableText() {
        val chip = "[11/24/2026](date:2026-11-24)"
        val state = MarkdownEditorState(chip)
        // Backspace after the "4": source loses the char at index 5.
        state.update(TextFieldValue("[11/2/2026](date:2026-11-24)", selection = TextRange(5)))
        assertEquals("11/2/2026", state.markdown)
        assertEquals(TextRange(4), state.textFieldValue.selection)
        assertNull(state.selectedDate)

        state.update(TextFieldValue("11/25/2026", selection = TextRange(5)))
        assertEquals("11/25/2026", state.markdown)

        val typed = MarkdownEditorState(chip)
        typed.update(TextFieldValue("[11/924/2026](date:2026-11-24)", selection = TextRange(5)))
        assertEquals("11/924/2026", typed.markdown)
    }

    @Test
    fun deletingTheWholeDateChipLabelStillRemovesTheChipCleanly() {
        val state = MarkdownEditorState("A [11/24/2026](date:2026-11-24)")
        state.update(TextFieldValue("A [](date:2026-11-24)", selection = TextRange(3)))
        assertEquals("A ", state.markdown)
    }

    @Test
    fun caretInsideDateLookingWordIsEnoughToTurnItIntoAChip() {
        val state = MarkdownEditorState("Due 11/25/2026. Next")
        state.update(TextFieldValue(state.markdown, selection = TextRange(8)))
        val plain = state.plainDateAtSelection!!
        assertEquals(LocalDate.of(2026, 11, 25), plain.date)
        assertEquals(4, plain.start)
        assertEquals(14, plain.endExclusive)

        state.addOrUpdateDate(LocalDate.of(2026, 11, 25), Locale.US)

        assertEquals("Due [11/25/2026](date:2026-11-25). Next", state.markdown)
    }

    @Test
    fun plainDateDetectionSkipsLinksCodeAndOrdinaryText() {
        assertNull(
            MarkdownEditorState("see [x](https://a.com/2026-11-24) now")
                .also {
                    it.update(TextFieldValue(it.markdown, selection = TextRange(25)))
                }.plainDateAtSelection,
        )
        assertNull(
            MarkdownEditorState("a `11/25/2026` b")
                .also {
                    it.update(TextFieldValue(it.markdown, selection = TextRange(6)))
                }.plainDateAtSelection,
        )
        assertNull(
            MarkdownEditorState("room 12345 here")
                .also {
                    it.update(TextFieldValue(it.markdown, selection = TextRange(7)))
                }.plainDateAtSelection,
        )
        assertNull(
            MarkdownEditorState("v11/25/2026x")
                .also {
                    it.update(TextFieldValue(it.markdown, selection = TextRange(4)))
                }.plainDateAtSelection,
        )
    }

    @Test
    fun retypedDateBecomesChipAgainAfterAPauseEvenWithTheCaretMidText() {
        val state = MarkdownEditorState("[11/24/2026](date:2026-11-24) ok")
        state.update(TextFieldValue("[11/2/2026](date:2026-11-24) ok", selection = TextRange(5)))
        assertEquals("11/2/2026 ok", state.markdown)
        assertEquals(TextRange(0, 9), state.pendingDateRechip)

        // Replace the 24 with 25 and stop with the caret after the 5, in the middle of the date.
        state.update(TextFieldValue("11/25/2026 ok", selection = TextRange(5)))
        assertEquals(TextRange(0, 10), state.pendingDateRechip)
        assertEquals(1500L, state.pendingDateRechipDelayMs(Locale.US))

        state.rechipPendingDate(Locale.US)

        assertEquals("[11/25/2026](date:2026-11-25) ok", state.markdown)
        assertEquals(TextRange("[11/25/2026](date:2026-11-25)".length), state.textFieldValue.selection)
        assertNull(state.pendingDateRechip)

        state.undo()
        assertEquals("11/25/2026 ok", state.markdown)
    }

    @Test
    fun retypedDateConvertsImmediatelyOnceTheCaretLeavesIt() {
        val state = MarkdownEditorState("[11/24/2026](date:2026-11-24)\nnext")
        state.update(TextFieldValue("[11/2/2026](date:2026-11-24)\nnext", selection = TextRange(5)))
        state.update(TextFieldValue("11/25/2026\nnext", selection = TextRange(5)))
        assertEquals(1500L, state.pendingDateRechipDelayMs(Locale.US))

        // Tap on the next line.
        state.update(TextFieldValue(state.markdown, selection = TextRange(13)))
        assertEquals(0L, state.pendingDateRechipDelayMs(Locale.US))
        state.rechipPendingDate(Locale.US)

        assertEquals("[11/25/2026](date:2026-11-25)\nnext", state.markdown)
    }

    @Test
    fun spaceTypedAfterRetypedDateEndsItWithoutBecomingPartOfIt() {
        val state = MarkdownEditorState("[11/24/2026](date:2026-11-24)")
        state.update(TextFieldValue("[11/2/2026](date:2026-11-24)", selection = TextRange(5)))
        state.update(TextFieldValue("11/25/2026", selection = TextRange(10)))
        state.update(TextFieldValue("11/25/2026 ", selection = TextRange(11)))
        assertEquals(TextRange(0, 10), state.pendingDateRechip)
        assertEquals(0L, state.pendingDateRechipDelayMs(Locale.US))
        state.rechipPendingDate(Locale.US)
        assertEquals("[11/25/2026](date:2026-11-25) ", state.markdown)
    }

    @Test
    fun pendingRechipIsDroppedWhenAnEditCutsAcrossItOrTextIsNoLongerADate() {
        val state = MarkdownEditorState("[11/24/2026](date:2026-11-24)")
        state.update(TextFieldValue("[11/2/2026](date:2026-11-24)", selection = TextRange(5)))
        assertEquals(TextRange(0, 9), state.pendingDateRechip)

        // Typing before it shifts the range; deleting across its start drops it.
        state.update(TextFieldValue("> 11/2/2026", selection = TextRange(2)))
        assertEquals(TextRange(2, 11), state.pendingDateRechip)
        state.update(TextFieldValue("> 11/2/2026".removeRange(1, 5), selection = TextRange(1)))
        assertNull(state.pendingDateRechip)

        val other = MarkdownEditorState("[11/24/2026](date:2026-11-24)")
        other.update(TextFieldValue("[11/2/2026](date:2026-11-24)", selection = TextRange(5)))
        other.update(TextFieldValue("11/2x/2026", selection = TextRange(10)))
        assertNull(other.pendingDateRechipDelayMs(Locale.US))
    }
}
