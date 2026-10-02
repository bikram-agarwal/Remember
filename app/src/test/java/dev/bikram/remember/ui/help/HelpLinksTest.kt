package dev.bikram.remember.ui.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Help links other code to subsections by their exact titles in docs/HELP.md, and a title that
 * matches nothing fails silently: no button, no search hit, no focus. These tests make a renamed
 * heading fail the build instead.
 */
class HelpLinksTest {
    // Unit tests run from the app module. docs/HELP.md is the source the build copies into assets.
    private val helpTitles: Set<String> =
        parseHelpContent(File("../docs/HELP.md").readText())
            .flatMap { section -> section.subsections.map { it.title } }
            .toSet()

    private val sections =
        listOf(
            HelpSection("Notes", listOf(HelpSubsection("A", "a"), HelpSubsection("B", "b"))),
            HelpSection("Reminders", listOf(HelpSubsection("C", "c"))),
        )

    @Test
    fun focusTargetIndexCountsTheSearchFieldAndEachSectionLabel() {
        // 0 search, 1 "Notes" label, 2 A, 3 B, 4 "Reminders" label, 5 C.
        assertEquals(HelpFocusTarget(2, "Notes/A"), helpFocusTarget(sections, "A"))
        assertEquals(HelpFocusTarget(3, "Notes/B"), helpFocusTarget(sections, "B"))
        assertEquals(HelpFocusTarget(5, "Reminders/C"), helpFocusTarget(sections, "C"))
    }

    @Test
    fun unknownSubsectionHasNoFocusTarget() {
        assertNull(helpFocusTarget(sections, "Missing"))
    }

    @Test
    fun everyFocusTitleIsAHelpHeading() {
        val help = parseHelpContent(File("../docs/HELP.md").readText())
        helpFocusSubsectionTitles.forEach { (focusId, title) ->
            assertNotNull("$focusId -> \"$title\" is not a subsection in docs/HELP.md", helpFocusTarget(help, title))
        }
    }

    @Test
    fun everyActionButtonKeyIsAHelpHeading() {
        val missing = helpSubsectionActions.keys - helpTitles
        assertTrue("Help buttons keyed to titles not in docs/HELP.md: $missing", missing.isEmpty())
    }

    @Test
    fun everyKeywordTargetIsAHelpHeading() {
        // Same format HelpViewModel reads: "phrase -> Title | Title", "#" comments.
        val missing =
            File("src/main/assets/HELP_KEYWORDS.txt")
                .readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && "->" in it }
                .flatMap { line -> line.substringAfter("->").split("|").map { it.trim() } }
                .filter { it.isNotEmpty() && it !in helpTitles }
                .toSet()
        assertTrue("HELP_KEYWORDS.txt targets not in docs/HELP.md: $missing", missing.isEmpty())
    }
}
