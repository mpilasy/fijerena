package org.njarasoa.fijerena.core.ui.sports

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.GameChannelHow
import org.njarasoa.fijerena.core.network.sports.LiveChannel
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.network.sports.SportsLeague
import org.njarasoa.fijerena.core.network.sports.SportsSearchResult
import org.njarasoa.fijerena.core.network.sports.SportsSource
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.showsTeamsTab
import java.util.Collections

/**
 * Search's Teams type (the sports teams plan → Screens): one search per new query, the best team
 * first with its games and their channels, then the other teams and the leagues, the followed ones
 * marked, a failure with Retry; and when the type shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TeamsSearchViewModelTest {
    private class FakeData(
        override val nerd: Boolean = false,
        var failure: Exception? = null,
    ) : TeamsSearchViewModel.Data {
        val queries: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val follows = MutableStateFlow(FollowedSports.Followed())

        var gamesFailure: Exception? = null
        val gameReads: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override suspend fun search(query: String): SportsSearchResult {
            queries += query
            failure?.let { throw it }
            return SportsSearchResult(listOf(ARSENAL_WOMEN, ARSENAL, ARSENAL_SARANDI), listOf(PREMIER_LEAGUE))
        }

        override fun followed(): Flow<FollowedSports.Followed> = follows

        override suspend fun teamGames(team: SportsTeam): List<SportsGame> {
            gameReads += team.id
            gamesFailure?.let { throw it }
            return GAMES
        }

        override suspend fun matcher(): GameMatcher =
            GameMatcher { game ->
                if (game.id == "1") listOf(GameChannel(CHANNEL, null, null, GameChannelHow.CHANNEL_NAME)) else emptyList()
            }

        override suspend fun follow(team: SportsTeam) {
            follows.value = follows.value.copy(teams = follows.value.teams + team)
        }

        override suspend fun unfollow(team: SportsTeam) {
            follows.value = follows.value.copy(teams = follows.value.teams.filter { it.id != team.id })
        }
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

    private fun <T> StateFlow<T>.await(predicate: (T) -> Boolean): T = runBlocking { withTimeout(TIMEOUT_MS) { first(predicate) } }

    @Test
    fun `a query gives the best team first, then the other teams, then the leagues`() {
        val vm = search(FakeData())
        vm.search(" arsenal ")

        val loaded = vm.await<TeamsSearchViewModel.State.Loaded>()

        assertEquals("arsenal", loaded.query)
        assertEquals(ARSENAL, loaded.best)
        assertEquals(listOf(ARSENAL_WOMEN.id, ARSENAL_SARANDI.id), loaded.teams.map { it.id })
        assertEquals(listOf("Premier League"), loaded.leagues.map { it.name })
    }

    @Test
    fun `the best team's games come with their channels, and it takes its logo from them`() {
        val data = FakeData()
        val vm = search(data)
        vm.search("arsenal")

        val games = vm.bestGames.await { it is GamesState.Loaded && it.channels.size == it.games.size } as GamesState.Loaded

        assertEquals(listOf(ARSENAL.id), data.gameReads)
        assertEquals(GAMES.map { it.id }, games.games.map { it.id })
        assertEquals(CHANNEL, games.channelsOf(GAMES[0])?.single()?.stream)
        assertEquals(emptyList<GameChannel>(), games.channelsOf(GAMES[1]))
        assertEquals(LOGO, vm.bestTeam.value?.logoUrl)
    }

    @Test
    fun `the best team's games failing shows Retry, which reads them again`() {
        val data = FakeData().apply { gamesFailure = IllegalStateException("HTTP 503") }
        val vm = search(data)
        vm.search("arsenal")
        vm.bestGames.await { it is GamesState.Failed }

        data.gamesFailure = null
        vm.retryBestGames()

        vm.bestGames.await { it is GamesState.Loaded }
        assertEquals(listOf(ARSENAL.id, ARSENAL.id), data.gameReads)
    }

    @Test
    fun `Follow on the best team follows it whole, and again stops following it`() {
        val data = FakeData()
        val vm = search(data)
        val watcher = vm.viewModelScope.launch { vm.followed.collect {} }
        vm.search("arsenal")
        vm.bestGames.await { it is GamesState.Loaded }

        vm.toggleBestFollow()
        vm.followed.await { ARSENAL.followKey in it }
        assertEquals(
            LOGO,
            data.follows.value.teams
                .single()
                .logoUrl,
        )

        vm.toggleBestFollow()
        vm.followed.await { it.isEmpty() }
        watcher.cancel()
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
    fun `a failure shows Retry, raw text only for a Nerd`() {
        val data = FakeData(failure = IllegalStateException("HTTP 500"))
        val vm = search(data)
        vm.search("arsenal")
        assertNull(vm.await<TeamsSearchViewModel.State.Failed>().raw)

        data.failure = null
        vm.retry()
        vm.await<TeamsSearchViewModel.State.Loaded>()
        assertEquals(listOf("arsenal", "arsenal"), data.queries)

        val dev = search(FakeData(nerd = true, failure = IllegalStateException("HTTP 500")))
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
        const val LOGO = "https://a.espncdn.com/i/teamlogos/soccer/500/359.png"
        val ARSENAL = SportsTeam(SportsSource.ESPN, "soccer/359", "Arsenal", sport = "soccer", league = "soccer/eng.1")
        val ARSENAL_WOMEN = SportsTeam(SportsSource.ESPN, "soccer/19973", "Arsenal", sport = "soccer", league = "soccer/eng.w.1")
        val ARSENAL_SARANDI = SportsTeam(SportsSource.ESPN, "soccer/2635", "Arsenal Sarandi", sport = "soccer", league = "soccer/arg.3")
        val CHELSEA = SportsTeam(SportsSource.ESPN, "soccer/363", "Chelsea", sport = "soccer")
        val PREMIER_LEAGUE = SportsLeague(SportsSource.ESPN, "soccer/eng.1", "Premier League", "soccer")
        val CHANNEL = LiveChannel(1, 7, "UK| PPV 3: Arsenal v Chelsea", null, "ppv")
        val GAMES =
            listOf(
                SportsGame(SportsSource.ESPN, "1", 4_000_000_000, ARSENAL.copy(logoUrl = LOGO), CHELSEA, PREMIER_LEAGUE),
                SportsGame(SportsSource.ESPN, "2", 4_000_090_000, CHELSEA, ARSENAL.copy(logoUrl = LOGO), PREMIER_LEAGUE),
            )
    }
}
