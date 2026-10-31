package org.njarasoa.fijerena.core.ui.viewmodels

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.tmdb.PersonTitleSource
import org.njarasoa.fijerena.core.network.tmdb.TmdbRecommendation
import org.njarasoa.fijerena.core.network.wanted.TmdbOnlyItem
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import java.util.Collections

/**
 * Search's People type (the people search plan): one TMDB call per new query, a failure shown with
 * Retry, and the type shown only with a key and never on a child profile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PeopleSearchViewModelTest {
    private class FakeData(
        override val devMode: Boolean = false,
        var failure: Exception? = null,
    ) : PeopleSearchViewModel.Data {
        val queries: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override suspend fun search(query: String): List<TmdbRecommendation> {
            queries += query
            failure?.let { throw it }
            return RESULTS
        }

        override suspend fun sources(): List<PersonTitleSource> =
            listOf { titles, _ ->
                titles.map { t ->
                    t.displayTitle?.takeIf { it == OWNED }?.let {
                        MediaItem(
                            id = it,
                            name = it,
                            mediaType = MediaType.MOVIE,
                            categoryId = "c",
                        )
                    }
                }
            }
    }

    private val created = mutableListOf<PeopleSearchViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun search(data: FakeData) = PeopleSearchViewModel(data).also { created += it }

    private inline fun <reified T : PeopleSearchViewModel.State> PeopleSearchViewModel.await(): T =
        runBlocking { withTimeout(TIMEOUT_MS) { state.first { it is T } as T } }

    @Test
    fun `a query gives the people, then the films with the TMDB-only ones`() {
        val vm = search(FakeData())
        vm.search(" hanks ")

        val loaded = vm.await<PeopleSearchViewModel.State.Loaded>()

        assertEquals("hanks", loaded.query)
        assertEquals(listOf("Tom Hanks"), loaded.results.people.map { it.name })
        assertEquals(listOf("Big", OWNED), loaded.results.movies.map { it.name })
        assertTrue(
            loaded.results.movies[0]
                .id
                .startsWith(TmdbOnlyItem.PREFIX),
        )
    }

    @Test
    fun `the query on screen is not asked again, a short one asks nothing`() {
        val data = FakeData()
        val vm = search(data)
        vm.search("hanks")
        vm.await<PeopleSearchViewModel.State.Loaded>()
        vm.search("hanks ")
        vm.search("h")

        assertEquals(PeopleSearchViewModel.State.Idle, vm.state.value)
        assertEquals(listOf("hanks"), data.queries)
    }

    @Test
    fun `a failed call shows the failure, and Retry asks again`() {
        val data = FakeData(failure = IllegalStateException("HTTP 500"))
        val vm = search(data)
        vm.search("hanks")

        val failed = vm.await<PeopleSearchViewModel.State.Failed>()
        assertEquals("hanks", failed.query)
        // Not in Expert mode: no raw text.
        assertNull(failed.raw)

        data.failure = null
        vm.retry()
        vm.await<PeopleSearchViewModel.State.Loaded>()
        assertEquals(listOf("hanks", "hanks"), data.queries)
    }

    @Test
    fun `in Expert mode a failure shows its raw text`() {
        val vm = search(FakeData(devMode = true, failure = IllegalStateException("HTTP 500")))
        vm.search("hanks")

        assertEquals("HTTP 500", vm.await<PeopleSearchViewModel.State.Failed>().raw)
    }

    @Test
    fun `the People type shows only with a TMDB key, never for a child, and not over live channels`() {
        assertTrue(showsPeopleTab(SearchViewModel.CONTENT_TYPE_ALL, hasTmdbKey = true))
        assertTrue(showsPeopleTab("MOVIES", hasTmdbKey = true))
        assertTrue(!showsPeopleTab(SearchViewModel.CONTENT_TYPE_ALL, hasTmdbKey = false))
        assertTrue(!showsPeopleTab("LIVE_TV", hasTmdbKey = true))
        assertTrue(!showsPeopleTab(SearchViewModel.CONTENT_TYPE_ALL, hasTmdbKey = true, child = true))
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val OWNED = "Cast Away"

        val RESULTS =
            listOf(
                TmdbRecommendation(id = 31, name = "Tom Hanks", mediaType = "person", knownForDepartment = "Acting"),
                TmdbRecommendation(id = 488, name = "Steven Spielberg", mediaType = "person", knownForDepartment = "Directing"),
                TmdbRecommendation(id = 2280, title = "Big", mediaType = "movie"),
                TmdbRecommendation(id = 8358, title = OWNED, mediaType = "movie"),
            )
    }
}
