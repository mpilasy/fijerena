package org.njarasoa.fijerena.core.network.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryMatcherRulesTest {
    @Test
    fun `dedupes within a single pasted batch, case-insensitively`() {
        val result = emptyList<CategoryMatcher>().withAddedRules(listOf("Adult", "adult", "ADULT "), MatchType.STARTS_WITH)
        assertEquals(listOf(CategoryMatcher("Adult", MatchType.STARTS_WITH)), result)
    }

    @Test
    fun `dedupes against existing rules with same matchType`() {
        val existing = listOf(CategoryMatcher("Adult", MatchType.STARTS_WITH))
        val result = existing.withAddedRules(listOf("adult", "XXX"), MatchType.STARTS_WITH)
        assertEquals(
            listOf(CategoryMatcher("Adult", MatchType.STARTS_WITH), CategoryMatcher("XXX", MatchType.STARTS_WITH)),
            result,
        )
    }

    @Test
    fun `same value with a different matchType is kept as a distinct rule`() {
        val existing = listOf(CategoryMatcher("Adult", MatchType.STARTS_WITH))
        val result = existing.withAddedRules(listOf("Adult"), MatchType.CONTAINS)
        assertEquals(
            listOf(CategoryMatcher("Adult", MatchType.STARTS_WITH), CategoryMatcher("Adult", MatchType.CONTAINS)),
            result,
        )
    }

    @Test
    fun `blank and whitespace-only entries are dropped`() {
        val result = emptyList<CategoryMatcher>().withAddedRules(listOf("", "   ", "Sport"), MatchType.EXACT)
        assertEquals(listOf(CategoryMatcher("Sport", MatchType.EXACT)), result)
    }

    @Test
    fun `values are trimmed`() {
        val result = emptyList<CategoryMatcher>().withAddedRules(listOf("  Sport  "), MatchType.EXACT)
        assertEquals(listOf(CategoryMatcher("Sport", MatchType.EXACT)), result)
    }
}
