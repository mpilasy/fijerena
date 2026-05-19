package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchUtilsTest {
    @Test
    fun getQueryWords_splitsAndFilters() {
        val query = "  Hello   World  "
        val words = SearchUtils.getQueryWords(query)
        assertEquals(listOf("hello", "world"), words)
    }

    @Test
    fun matchesQuery_findsMatches() {
        val text = "This is a Hello World example"
        val queryWords = SearchUtils.ParsedQuery(listOf("hello", "world"), emptyList())
        assertTrue(SearchUtils.matchesQuery(text, queryWords))
    }

    @Test
    fun matchesQuery_failsOnPartialMatch() {
        val text = "This is a Hello example"
        val queryWords = SearchUtils.ParsedQuery(listOf("hello", "world"), emptyList())
        assertFalse(SearchUtils.matchesQuery(text, queryWords))
    }

    @Test
    fun matchesQuery_emptyQuery_returnsTrue() {
        val text = "Any text"
        val queryWords = SearchUtils.ParsedQuery(emptyList(), emptyList())
        assertTrue(SearchUtils.matchesQuery(text, queryWords))
    }

    @Test
    fun matchesQuery_negativeSearch_excludesMatch() {
        val text = "This is a Hello World example"
        val queryWords = SearchUtils.ParsedQuery(listOf("hello"), listOf("world"))
        assertFalse(SearchUtils.matchesQuery(text, queryWords))
    }

    @Test
    fun matchesQuery_negativeSearch_includesNoMatch() {
        val text = "This is a Hello example"
        val queryWords = SearchUtils.ParsedQuery(listOf("hello"), listOf("world"))
        assertTrue(SearchUtils.matchesQuery(text, queryWords))
    }

    @Test
    fun matchesQuery_negativeSearch_ignoresSingleDash() {
        val text = "This is a Hello - example"
        val queryWords = SearchUtils.ParsedQuery(listOf("hello", "-"), emptyList())
        assertTrue(SearchUtils.matchesQuery(text, queryWords))
    }
}
