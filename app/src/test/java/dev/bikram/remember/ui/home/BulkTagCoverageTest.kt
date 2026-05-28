package dev.bikram.remember.ui.home

import dev.bikram.remember.data.RememberReservedTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BulkTagCoverageTest {
    @Test
    fun buildsCoverageForAllSomeAndNoneStates() {
        val coverage =
            buildBulkTagCoverage(
                availableTags = listOf("Work", "Personal", "Shopping"),
                selectedNoteTags =
                    listOf(
                        listOf("Work", "Personal"),
                        listOf("Work"),
                    ),
            ).associateBy { item -> item.tag }

        assertEquals(BulkTagCoverageState.ALL, coverage.getValue("Work").state)
        assertEquals(2, coverage.getValue("Work").matchCount)
        assertEquals(2, coverage.getValue("Work").totalCount)
        assertEquals(BulkTagCoverageState.SOME, coverage.getValue("Personal").state)
        assertEquals(1, coverage.getValue("Personal").matchCount)
        assertEquals(2, coverage.getValue("Personal").totalCount)
        assertEquals(BulkTagCoverageState.NONE, coverage.getValue("Shopping").state)
        assertEquals(0, coverage.getValue("Shopping").matchCount)
        assertEquals(2, coverage.getValue("Shopping").totalCount)
    }

    @Test
    fun excludesReservedTagsAndMatchesCaseInsensitively() {
        val coverage =
            buildBulkTagCoverage(
                availableTags =
                    listOf(
                        "WORK",
                        "Errand",
                        RememberReservedTags.STARRED,
                        RememberReservedTags.MOCK,
                    ),
                selectedNoteTags =
                    listOf(
                        listOf("work", RememberReservedTags.STARRED),
                        listOf("Work", RememberReservedTags.MOCK),
                    ),
            )
        val coverageByNormalizedTag = coverage.associateBy { item -> item.tag.lowercase() }

        assertEquals(BulkTagCoverageState.ALL, coverageByNormalizedTag.getValue("work").state)
        assertEquals(2, coverageByNormalizedTag.getValue("work").matchCount)
        assertEquals(BulkTagCoverageState.NONE, coverageByNormalizedTag.getValue("errand").state)
        assertFalse(coverageByNormalizedTag.containsKey(RememberReservedTags.STARRED.lowercase()))
        assertFalse(coverageByNormalizedTag.containsKey(RememberReservedTags.MOCK.lowercase()))
    }

    @Test
    fun resolvesBulkTagIntentActionsWithCaseInsensitiveKeys() {
        val tagKey = bulkTagIntentKey("mytag")
        val actions =
            resolveBulkTagIntentActions(
                coverage =
                    listOf(
                        BulkTagCoverage(
                            tag = "MyTag",
                            matchCount = 0,
                            totalCount = 2,
                            state = BulkTagCoverageState.NONE,
                        ),
                    ),
                extraAddedTags = listOf("mytag"),
                tagIntents = mapOf(tagKey to BulkTagIntent.ADD),
                newTagColorsByKey = mapOf(tagKey to "#ABCDEF"),
            )

        assertEquals(setOf("MyTag"), actions.addTags)
        assertEquals(emptySet<String>(), actions.removeTags)
        assertEquals(mapOf("MyTag" to "#ABCDEF"), actions.newTagColors)
    }
}
