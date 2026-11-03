package org.njarasoa.fijerena.core.ui.sports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import org.njarasoa.fijerena.core.network.sports.Broadcaster
import org.njarasoa.fijerena.core.network.sports.BroadcasterOrigin
import org.njarasoa.fijerena.core.network.sports.FollowedSports
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.GameChannelHow
import org.njarasoa.fijerena.core.network.sports.LiveChannel
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.network.sports.SportsLeague
import org.njarasoa.fijerena.core.network.sports.SportsSource
import org.njarasoa.fijerena.core.network.sports.SportsTeam

/**
 * The team and league pages (the sports teams plan → Screens): the games, then each game's
 * channels; Follow on the profile; a failure with Retry; a failed match leaves the broadcasters.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SportsPageViewModelTest {
    private class FakeData(
        override val nerd: Boolean = false,
        var failure: Exception? = null,
        var matchFailure: Exception? = null,
        var teamsFailure: Exception? = null,
    ) : SportsPageData {
        val follows = MutableStateFlow(FollowedSports.Followed())
        var reads = 0

        override suspend fun teamGames(team: SportsTeam): List<SportsGame> {
            reads++
            failure?.let { throw it }
            return GAMES
        }

        override suspend fun leagueGames(league: SportsLeague): List<SportsGame> {
            failure?.let { throw it }
            return GAMES
        }

        override suspend fun leagueTeams(league: SportsLeague): List<SportsTeam> {
            teamsFailure?.let { throw it }
            return listOf(CHELSEA, ARSENAL)
        }

        override fun followed(): Flow<FollowedSports.Followed> = follows

        override suspend fun follow(team: SportsTeam) {
            follows.value = follows.value.copy(teams = follows.value.teams + team)
        }

        override suspend fun unfollow(team: SportsTeam) {
            follows.value = follows.value.copy(teams = follows.value.teams.filter { it.id != team.id })
        }

        override suspend fun follow(league: SportsLeague) {
            follows.value = follows.value.copy(leagues = follows.value.leagues + league)
        }

        override suspend fun unfollow(league: SportsLeague) {
            follows.value = follows.value.copy(leagues = follows.value.leagues.filter { it.id != league.id })
        }

        override suspend fun matcher(): GameMatcher {
            matchFailure?.let { throw it }
            return GameMatcher { game ->
                listOf(GameChannel(CHANNEL, null, game.broadcasters.firstOrNull(), GameChannelHow.BROADCASTER_CHANNEL))
            }
        }
    }

    private val created = mutableListOf<ViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun teamPage(
        data: FakeData,
        route: SportsTeam = ROUTE_TEAM,
    ) = SportsTeamViewModel(data, route).also { created += it }

    private fun leaguePage(data: FakeData) = SportsLeagueViewModel(data, LEAGUE).also { created += it }

    private fun <T> StateFlow<T>.await(predicate: (T) -> Boolean): T = runBlocking { withTimeout(TIMEOUT_MS) { first(predicate) } }

    private fun SportsGamesViewModel.awaitMatched(): GamesState.Loaded =
        games.await { it is GamesState.Loaded && it.channels.size == it.games.size } as GamesState.Loaded

    @Test
    fun `the team page shows the games, then each game's channel`() {
        val vm = teamPage(FakeData())

        val loaded = vm.awaitMatched()

        assertEquals(GAMES.map { it.id }, loaded.games.map { it.id })
        assertEquals(CHANNEL, loaded.channelsOf(GAMES[0])?.first()?.stream)
    }

    @Test
    fun `the team from the route takes its logo and names from the games`() {
        val vm = teamPage(FakeData())
        vm.awaitMatched()

        val team = vm.team.value
        assertEquals(LOGO, team.logoUrl)
        assertTrue("Gunners" in team.altNames)
    }

    @Test
    fun `the team page shows the name the game titles use, the search's name kept as another name`() {
        val vm = teamPage(FakeData(), route = ROUTE_TEAM.copy(name = "Arsenal London"))
        vm.awaitMatched()

        val team = vm.team.value
        assertEquals("Arsenal", team.name)
        assertTrue("Arsenal London" in team.altNames)
    }

    @Test
    fun `Follow follows the team whole, and again stops following it`() {
        val data = FakeData()
        val vm = teamPage(data)
        vm.awaitMatched()

        vm.toggleFollow()
        vm.followed.await { it }
        assertEquals(
            LOGO,
            data.follows.value.teams
                .single()
                .logoUrl,
        )

        vm.toggleFollow()
        vm.followed.await { !it }
        assertTrue(
            data.follows.value.teams
                .isEmpty(),
        )
    }

    @Test
    fun `a failure shows Retry, raw text only for a Nerd, and Retry reads again`() {
        val data = FakeData(failure = IllegalStateException("HTTP 503"))
        val vm = teamPage(data)

        val failed = vm.games.await { it is GamesState.Failed } as GamesState.Failed
        assertNull(failed.raw)

        data.failure = null
        vm.retry()
        vm.awaitMatched()
        assertEquals(2, data.reads)

        val dev = teamPage(FakeData(nerd = true, failure = IllegalStateException("HTTP 503")))
        assertEquals("HTTP 503", (dev.games.await { it is GamesState.Failed } as GamesState.Failed).raw)
    }

    @Test
    fun `channels that can't be read find no channel for any game`() {
        val vm = teamPage(FakeData(matchFailure = IllegalStateException("db")))

        val loaded = vm.awaitMatched()

        assertEquals(emptyList<GameChannel>(), loaded.channelsOf(GAMES[0]))
        assertEquals(emptyList<GameChannel>(), loaded.channelsOf(GAMES[1]))
    }

    @Test
    fun `a game whose match never ends finds no channel, and the other games still find theirs`() {
        val state = MutableStateFlow<GamesState>(GamesState.Loading)
        val channel = GameChannel(CHANNEL, null, null, GameChannelHow.BROADCASTER_CHANNEL)
        val matcher = GameMatcher { game -> if (game == GAMES[0]) awaitCancellation() else listOf(channel) }

        runBlocking { readGamesAndChannels(state, { GAMES }, { matcher }, { null }, matchTimeoutMs = 200L) }

        val loaded = state.value as GamesState.Loaded
        assertEquals(emptyList<GameChannel>(), loaded.channelsOf(GAMES[0]))
        assertEquals(listOf(channel), loaded.channelsOf(GAMES[1]))
    }

    @Test
    fun `the league page lists its teams by name and follows the league`() {
        val data = FakeData()
        val vm = leaguePage(data)
        vm.awaitMatched()

        assertEquals(listOf("Arsenal", "Chelsea"), vm.teams.await { it != null }?.map { it.name })
        vm.toggleFollow()
        vm.followed.await { it }
        assertEquals(
            listOf(LEAGUE.id),
            data.follows.value.leagues
                .map { it.id },
        )
    }

    @Test
    fun `a league whose teams fail still shows its games, without the team list`() {
        val vm = leaguePage(FakeData(teamsFailure = IllegalStateException("HTTP 500")))

        assertEquals(emptyList<SportsTeam>(), vm.teams.await { it != null })
        assertEquals(GAMES.size, vm.awaitMatched().games.size)
    }

    @Test
    fun `a route names its team, a source this build does not know names none`() {
        assertEquals("soccer", sportsTeamOf("espn", "soccer/359", "Arsenal")?.sport)
        assertEquals("hockey", sportsLeagueOf("espn", "hockey/nhl", "NHL")?.sport)
        assertNull(sportsTeamOf("newsource", "1", "X"))
        assertEquals(ROUTE_TEAM.copy(sport = "soccer"), ROUTE_TEAM.copy(sport = "soccer").route().team())
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val LOGO = "https://a.espncdn.com/i/teamlogos/soccer/500/359.png"

        val ROUTE_TEAM = SportsTeam(SportsSource.ESPN, "soccer/359", "Arsenal", sport = "soccer")
        val ARSENAL = ROUTE_TEAM.copy(altNames = listOf("Gunners"), logoUrl = LOGO)
        val CHELSEA = SportsTeam(SportsSource.ESPN, "soccer/363", "Chelsea", sport = "soccer")
        val LEAGUE = SportsLeague(SportsSource.ESPN, "soccer/eng.1", "Premier League", "soccer")
        val CHANNEL = LiveChannel(1, 7, "UK: Sky Sports Main Event", "skysports.uk", "c")

        val GAMES =
            listOf(
                SportsGame(
                    SportsSource.ESPN,
                    "1",
                    1_800_000_000,
                    ARSENAL,
                    CHELSEA,
                    LEAGUE,
                    broadcasters = listOf(Broadcaster("ESPN", "US", from = BroadcasterOrigin.ESPN)),
                ),
                SportsGame(SportsSource.ESPN, "2", 1_800_600_000, CHELSEA, ARSENAL, LEAGUE),
            )
    }
}
