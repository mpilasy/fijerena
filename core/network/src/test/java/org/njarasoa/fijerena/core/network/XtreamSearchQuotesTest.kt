package org.njarasoa.fijerena.core.network

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.player.domain.ContentType

/** Words match as prefixes; words in double or single quotes match whole, in that order. */
class XtreamSearchQuotesTest {
    private val repository = mockk<XtreamRepository>(relaxed = true)
    private val tmdb = mockk<TmdbApiService>(relaxed = true)
    private val provider = XtreamMediaProvider(providerId = 9L, repository = repository, tmdb = tmdb)

    private suspend fun searchesFor(
        query: String,
        fts: String,
    ) {
        provider.search(query, ContentType.MOVIES, false)
        coVerify { repository.searchByFts(ContentType.MOVIES, fts, false) }
    }

    @Test
    fun wordsArePrefixes() = runTest { searchesFor("har pot", "har* pot*") }

    @Test
    fun aQuotedWordMatchesWhole() = runTest { searchesFor("\"la\" land", "\"la\" land*") }

    @Test
    fun quotedWordsMatchInOrder() = runTest { searchesFor("\"la la\" land", "\"la la\" land*") }

    @Test
    fun singleQuotesWorkToo() = runTest { searchesFor("'la la' land", "\"la la\" land*") }

    @Test
    fun anApostropheIsNotAQuote() = runTest { searchesFor("don't won't", "dont* wont*") }

    @Test
    fun anApostropheInsideSingleQuotes() = runTest { searchesFor("'ocean's' eleven", "oceans* eleven*") }

    @Test
    fun anUnclosedQuoteReadsAsWords() = runTest { searchesFor("\"la land", "la* land*") }

    @Test
    fun symbolsInsideQuotesAreDropped() = runTest { searchesFor("\"ocean's*\" eleven", "\"oceans\" eleven*") }

    @Test
    fun emptyQuotesSearchNothing() =
        runTest {
            assertNull(provider.search("\"\"", ContentType.MOVIES, false))
        }
}
