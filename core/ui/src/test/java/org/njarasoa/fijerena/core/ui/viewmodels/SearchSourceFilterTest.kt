package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel.CategorySearchResult
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel.SearchResult

/** Search's source filter (the search source filter plan, Phase 1). */
class SearchSourceFilterTest {
    @Test
    fun `each source is counted over categories and results, sorted by name`() {
        val counts =
            searchSourceCounts(
                categories = listOf(category(B, "beta")),
                results = listOf(result("1", A, "Alpha"), result("2", B, "beta"), result("3", A, "Alpha")),
            )

        assertEquals(listOf(SearchSourceCount(A, "Alpha", 2), SearchSourceCount(B, "beta", 2)), counts)
    }

    @Test
    fun `no sources to choose from with results from a single source`() {
        assertTrue(searchSourceCounts(emptyList(), listOf(result("1", A, "Alpha"), result("2", A, "Alpha"))).isEmpty())
        // A single-source search tags no result with its source.
        assertTrue(searchSourceCounts(emptyList(), listOf(result("1", A, null))).isEmpty())
    }

    @Test
    fun `the chosen source holds while it has results, else every source`() {
        val sources = listOf(SearchSourceCount(A, "Alpha", 1), SearchSourceCount(B, "Beta", 1))

        assertEquals(B, effectiveSourceFilter(B, sources))
        assertNull(effectiveSourceFilter(C, sources))
        assertNull(effectiveSourceFilter(B, emptyList()))
        assertNull(effectiveSourceFilter(null, sources))
    }

    @Test
    fun `results are narrowed to one source, or all kept with none chosen`() {
        val results = listOf(result("1", A, "Alpha"), result("2", B, "Beta"))
        val categories = listOf(category(A, "Alpha"), category(B, "Beta"))

        assertEquals(listOf("2"), results.fromSource(B).map { it.itemId })
        assertEquals(listOf(A), categories.fromSource(A).map { it.providerId })
        assertEquals(results, results.fromSource(null))
    }

    @Test
    fun `results narrowed to one source lose their source badge, all of them keep it`() {
        val results = listOf(result("1", A, "Alpha"), result("2", B, "Beta"))
        val categories = listOf(category(A, "Alpha"), category(B, "Beta"))

        assertEquals(listOf<String?>(null), results.fromSource(B).map { it.sourceName })
        assertEquals(listOf<String?>(null), categories.fromSource(A).map { it.sourceName })
        assertEquals(listOf("Alpha", "Beta"), results.fromSource(null).map { it.sourceName })
    }

    private fun result(
        id: String,
        providerId: Long,
        sourceName: String?,
    ) = SearchResult(id, "Title $id", "c", "Cat", ContentType.MOVIES, providerId = providerId, sourceName = sourceName)

    private fun category(
        providerId: Long,
        sourceName: String,
    ) = CategorySearchResult("c$providerId", "Cat", ContentType.MOVIES, providerId, sourceName)

    private companion object {
        const val A = 1L
        const val B = 2L
        const val C = 3L
    }
}
