package dev.bikram.remember.ui.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconPickerSearchTest {
    @Test
    fun lightStemHandlesCommonEnglishSuffixes() {
        assertEquals("song", lightStem("songs"))
        assertEquals("cat", lightStem("cats"))
        assertEquals("city", lightStem("cities"))
        assertEquals("run", lightStem("running"))
        assertEquals("kick", lightStem("kicked"))
    }

    @Test
    fun lightStemLeavesShortAndDoubleConsonantWordsAlone() {
        assertEquals("cat", lightStem("cat"))
        assertEquals("dress", lightStem("dress"))
        assertEquals("yes", lightStem("yes"))
    }

    @Test
    fun isWithinOneEditAcceptsSingleSubInsertOrDelete() {
        assertTrue(isWithinOneEdit("calendar", "calender"))
        assertTrue(isWithinOneEdit("phone", "phon"))
        assertTrue(isWithinOneEdit("phon", "phone"))
        assertTrue(isWithinOneEdit("equal", "equal"))
    }

    @Test
    fun isWithinOneEditRejectsTwoOrMoreEdits() {
        assertFalse(isWithinOneEdit("calendar", "celender"))
        assertFalse(isWithinOneEdit("cat", "dog"))
        assertFalse(isWithinOneEdit("phone", "ph"))
    }

    @Test
    fun scoreSearchableRequiresEveryQueryTokenToHitSomeField() {
        val fields =
            listOf(
                SearchableField("birthday cake", weight = 3.0f),
                SearchableField("celebration party", weight = 1.5f),
            )
        assertTrue(scoreSearchable(tokenizeQuery("birthday"), fields) > 0f)
        assertTrue(scoreSearchable(tokenizeQuery("birthday party"), fields) > 0f)
        // 'rocket' isn't in any field, so the AND fails even though 'birthday' would match.
        assertEquals(0f, scoreSearchable(tokenizeQuery("birthday rocket"), fields))
    }

    @Test
    fun stemmedQueryStillRanksUnderExactMatch() {
        val exactFields = listOf(SearchableField("songs", weight = 3.0f))
        val stemmedFields = listOf(SearchableField("song", weight = 3.0f))
        val exactScore = scoreSearchable(tokenizeQuery("songs"), exactFields)
        val stemmedScore = scoreSearchable(tokenizeQuery("songs"), stemmedFields)
        assertTrue("exact ($exactScore) should outrank stemmed ($stemmedScore)", exactScore > stemmedScore)
        assertTrue("stemmed query should still match", stemmedScore > 0f)
    }

    @Test
    fun fuzzyMatchEngagesOnFivePlusCharacterTypos() {
        val fields = listOf(SearchableField("calendar", weight = 3.0f))
        assertTrue(scoreSearchable(tokenizeQuery("calender"), fields) > 0f)
    }

    @Test
    fun fuzzyMatchDoesNotEngageOnShortTokens() {
        val fields = listOf(SearchableField("bat", weight = 3.0f))
        assertEquals(0f, scoreSearchable(tokenizeQuery("cat"), fields))
    }

    @Test
    fun fourCharacterQueriesDoNotFuzzyMatchNearbyUnrelatedWords() {
        val fields = listOf(SearchableField("care case came", weight = 3.0f))
        assertEquals(0f, scoreSearchable(tokenizeQuery("cake"), fields))
    }

    @Test
    fun nameFieldOutweighsCategoryField() {
        val nameMatch =
            listOf(
                SearchableField("yoga", weight = 3.0f),
                SearchableField("activities", weight = 0.8f),
            )
        val categoryMatch =
            listOf(
                SearchableField("trampoline", weight = 3.0f),
                SearchableField("yoga", weight = 0.8f),
            )
        val nameScore = scoreSearchable(tokenizeQuery("yoga"), nameMatch)
        val categoryScore = scoreSearchable(tokenizeQuery("yoga"), categoryMatch)
        assertTrue("name-weighted hit ($nameScore) should outrank category-only hit ($categoryScore)", nameScore > categoryScore)
    }

    @Test
    fun keywordMatchSurfacesItemWithoutSubstringInName() {
        // Models the 'spicy -> chili' lookup powered by CLDR keywords.
        val chili =
            listOf(
                SearchableField("hot pepper", weight = 3.0f),
                SearchableField("hot_pepper", weight = 2.0f),
                SearchableField("chili hot pepper spicy", weight = 1.5f),
                SearchableField("food and drink", weight = 0.8f),
            )
        assertTrue(scoreSearchable(tokenizeQuery("spicy"), chili) > 0f)
    }

    @Test
    fun shortTokenDoesNotFalseMatchInsideLongerWord() {
        // "lit" should NOT score a hit inside "light skin tone"; it only matches the
        // standalone keyword "lit" (e.g. the fire emoji's CLDR keywords).
        val skinTone = listOf(SearchableField("pinching hand: light skin tone", weight = 3.0f))
        val fireKeywords =
            listOf(
                SearchableField("fire", weight = 3.0f),
                SearchableField("burn flame hot lit", weight = 1.5f),
            )
        assertEquals("'lit' must not match inside 'light'", 0f, scoreSearchable(tokenizeQuery("lit"), skinTone))
        assertTrue("'lit' should match the fire keyword list", scoreSearchable(tokenizeQuery("lit"), fireKeywords) > 0f)
    }

    @Test
    fun underscoreInSlugDoesNotBlockWordMatching() {
        // Slug fields use underscores; the scorer should split on them.
        val fields = listOf(SearchableField("self_improvement", weight = 2.0f))
        assertTrue(scoreSearchable(tokenizeQuery("improvement"), fields) > 0f)
    }
}
