package org.njarasoa.fijerena.core.ui.sports

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import org.njarasoa.fijerena.core.network.sports.FollowedSports
import org.njarasoa.fijerena.core.network.sports.SportsLeague
import org.njarasoa.fijerena.core.network.sports.SportsSearchResult
import org.njarasoa.fijerena.core.network.sports.SportsSource
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.showsTeamsTab
import java.util.Collections

/**
 * Search's Teams type (the sports teams plan → Screens): one search per new query, teams then
 * leagues, the followed ones marked, a failure with Retry; and when the type shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TeamsSearchViewModelTest {
    private class FakeData(
        override val devMode: Boolean = false,
        var failure: Exception? = null,
    ) : TeamsSearchViewModel.Data {
        val queries: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val follows = MutableStateFlow(FollowedSports.Followed())

        override suspend fun search(query: String): SportsSearchResult {
            queries += query
            failure?.let { throw it }
            return SportsSearchResult(listOf(ARSENAL), listOf(PREMIER_LEAGUE))
        }

        override fun followed(): Flow<FollowedSports.Followed> = follows
    }

    private val created = mutableListOf<TeamsSearchViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun search(data: FakeData) = TeamsSearchViewModel(data).also { created += it }

    private inline fun <reified T : TeamsSearchViewModel.State> TeamsSearchViewModel.await(): T =
        runBlocking { withTimeout(TIMEOUT_MS) { state.first { it is T } as T } }

    @Test
    fun `a query gives the teams, then the leagues`() {
        val vm = search(FakeData())
        vm.search(" arsenal ")

        val loaded = vm.await<TeamsSearchViewModel.State.Loaded>()

        assertEquals("arsenal", loaded.query)
        assertEquals(listOf("Arsenal"), loaded.teams.map { it.name })
        assertEquals(listOf("Premier League"), loaded.leagues.map { it.name })
    }

    @Test
    fun `the query on screen is not asked again, a short one asks nothing`() {
        val data = FakeData()
        val vm = search(data)
        vm.search("arsenal")
        vm.await<TeamsSearchViewModel.State.Loaded>()
        vm.search("arsenal ")
        vm.search("a")

        assertEquals(TeamsSearchViewModel.State.Idle, vm.state.value)
        assertEquals(listOf("arsenal"), data.queries)
    }

    @Test
    fun `a failure shows Retry, raw text only in dev mode`() {
        val data = FakeData(failure = IllegalStateException("HTTP 500"))
        val vm = search(data)
        vm.search("arsenal")
        assertNull(vm.await<TeamsSearchViewModel.State.Failed>().raw)

        data.failure = null
        vm.retry()
        vm.await<TeamsSearchViewModel.State.Loaded>()
        assertEquals(listOf("arsenal", "arsenal"), data.queries)

        val dev = search(FakeData(devMode = true, failure = IllegalStateException("HTTP 500")))
        dev.search("arsenal")
        assertEquals("HTTP 500", dev.await<TeamsSearchViewModel.State.Failed>().raw)
    }

    @Test
    fun `a followed team or league is marked`() {
        val data = FakeData()
        val vm = search(data)
        // The marks are read while the screen shows them.
        val watcher = vm.viewModelScope.launch { vm.followed.collect {} }
        data.follows.value = FollowedSports.Followed(teams = listOf(ARSENAL), leagues = listOf(PREMIER_LEAGUE))

        val keys = runBlocking { withTimeout(TIMEOUT_MS) { vm.followed.first { it.isNotEmpty() } } }

        assertEquals(setOf(ARSENAL.followKey, PREMIER_LEAGUE.followKey), keys)
        watcher.cancel()
    }

    @Test
    fun `the Teams type shows with the switch on, never for a child, and only where live channels are searched`() {
        assertTrue(showsTeamsTab(SearchViewModel.CONTENT_TYPE_ALL, sportsOn = true))
        assertTrue(showsTeamsTab("LIVE_TV", sportsOn = true))
        assertTrue(!showsTeamsTab("MOVIES", sportsOn = true))
        assertTrue(!showsTeamsTab("TV_SHOWS", sportsOn = true))
        assertTrue(!showsTeamsTab(SearchViewModel.CONTENT_TYPE_ALL, sportsOn = false))
        assertTrue(!showsTeamsTab(SearchViewModel.CONTENT_TYPE_ALL, sportsOn = true, child = true))
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        val ARSENAL = SportsTeam(SportsSource.ESPN, "soccer/359", "Arsenal", sport = "soccer")
        val PREMIER_LEAGUE = SportsLeague(SportsSource.ESPN, "soccer/eng.1", "Premier League", "soccer")
    }
}
