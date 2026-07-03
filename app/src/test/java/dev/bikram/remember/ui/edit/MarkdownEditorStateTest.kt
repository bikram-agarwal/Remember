package dev.bikram.remember.ui.edit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

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
    fun backspaceInsideEmptyBoldMarkersRemovesWholeWrapper() {
        val state = MarkdownEditorState()
        state.toggleBold()

        state.update(TextFieldValue("***", selection = TextRange(1)))

        assertEquals("", state.markdown)
        assertEquals(TextRange(0), state.textFieldValue.selection)
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
    fun deletingBoldContentRemovesEmptyWrapper() {
        val state = MarkdownEditorState("**BOLD**")

        state.update(TextFieldValue("****", selection = TextRange(2)))

        assertEquals("", state.markdown)
        assertEquals(TextRange(0), state.textFieldValue.selection)
    }

    @Test
    fun deletingInlineContentRemovesOnlyEmptyWrapper() {
        val state = MarkdownEditorState("Start **BOLD** end")

        state.update(TextFieldValue("Start **** end", selection = TextRange(8)))

        assertEquals("Start  end", state.markdown)
        assertEquals(TextRange(6), state.textFieldValue.selection)
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
}
