package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchUtilsTest {
    @Test
    fun parseQuery_splitsAndFilters() {
        val query = "  Hello   World  "
        val parsed = SearchUtils.parseQuery(query)
        assertEquals(listOf("hello", "world"), parsed.positiveWords)
        assertTrue(parsed.negativeWords.isEmpty())
    }

    @Test
    fun parseQuery_negativeWords() {
        val query = "  Hello  -World - -test "
        val parsed = SearchUtils.parseQuery(query)
        assertEquals(listOf("hello", "-"), parsed.positiveWords)
        assertEquals(listOf("world", "test"), parsed.negativeWords)
    }

    @Test
    fun matchesQuery_findsMatches() {
        val text = "This is a Hello World example"
        val parsedQuery = SearchUtils.parseQuery("hello world")
        assertTrue(SearchUtils.matchesQuery(text, parsedQuery))
    }

    @Test
    fun matchesQuery_failsOnPartialMatch() {
        val text = "This is a Hello example"
        val parsedQuery = SearchUtils.parseQuery("hello world")
        assertFalse(SearchUtils.matchesQuery(text, parsedQuery))
    }

    @Test
    fun matchesQuery_emptyQuery_returnsTrue() {
        val text = "Any text"
        val parsedQuery = SearchUtils.parseQuery("")
        assertTrue(SearchUtils.matchesQuery(text, parsedQuery))
    }

    @Test
    fun matchesQuery_negativeSearch_excludesMatch() {
        val text = "This is a Hello World example"
        val parsedQuery = SearchUtils.parseQuery("hello -world")
        assertFalse(SearchUtils.matchesQuery(text, parsedQuery))
    }

    @Test
    fun matchesQuery_negativeSearch_includesNoMatch() {
        val text = "This is a Hello example"
        val parsedQuery = SearchUtils.parseQuery("hello -world")
        assertTrue(SearchUtils.matchesQuery(text, parsedQuery))
    }

    @Test
    fun matchesQuery_negativeSearch_ignoresSingleDash() {
        val text = "This is a Hello - example"
        val parsedQuery = SearchUtils.parseQuery("hello -")
        assertTrue(SearchUtils.matchesQuery(text, parsedQuery))
    }
}
