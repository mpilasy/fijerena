package org.njarasoa.fijerena.core.ui.sports

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.sports.FollowedSports
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.GameChannelHow
import org.njarasoa.fijerena.core.network.sports.GameState
import org.njarasoa.fijerena.core.network.sports.LiveChannel
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.network.sports.SportsLeague
import org.njarasoa.fijerena.core.network.sports.SportsSource
import org.njarasoa.fijerena.core.network.sports.SportsTeam

/**
 * The games of what the profile follows (the sports teams plan → Sports section, Home row): one
 * list for every followed team and league, each game once, in the Sports section's 7 days and Home's
 * 48 hours; Home's row kept 15 minutes per profile and followed set.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FollowedGamesTest {
    private class FakeData(
        val teamGames: Map<String, List<SportsGame>> = emptyMap(),
        val leagueGames: Map<String, List<SportsGame>> = emptyMap(),
        follows: FollowedSports.Followed = FollowedSports.Followed(),
    ) : SportsPageData {
        val follows = MutableStateFlow(follows)
        val failing = mutableSetOf<String>()
        var reads = 0

        override val nerd = true

        override suspend fun teamGames(team: SportsTeam): List<SportsGame> {
            reads++
            if (team.id in failing) error("ESPN down")
            return teamGames[team.id].orEmpty()
        }

        override suspend fun leagueGames(league: SportsLeague): List<SportsGame> {
            reads++
            if (league.id in failing) error("ESPN down")
            return leagueGames[league.id].orEmpty()
        }

        override suspend fun leagueTeams(league: SportsLeague): List<SportsTeam> = emptyList()

        override fun followed(): Flow<FollowedSports.Followed> = follows

        override suspend fun follow(team: SportsTeam) = Unit

        override suspend fun unfollow(team: SportsTeam) = Unit

        override suspend fun follow(league: SportsLeague) = Unit

        override suspend fun unfollow(league: SportsLeague) = Unit

        override suspend fun matcher(): GameMatcher = GameMatcher { listOf(GameChannel(CHANNEL, null, null, GameChannelHow.GUIDE)) }
    }

    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    @Test
    fun `a game of a followed team in a followed league shows once`() {
        val game = game("1", hours = 5)
        val merged = mergeFollowedGames(listOf(listOf(game), listOf(game, game("2", hours = 10))), NOW_SEC, SECTION_WINDOW_SEC)

        assertEquals(listOf("1", "2"), merged.map { it.id })
    }

    @Test
    fun `the same game with two ids shows once, the sooner kept`() {
        val espn = game("espn-1", hours = 5)
        val other = espn.copy(id = "league-1", startEpochSec = espn.startEpochSec + 30 * 60)

        val merged = mergeFollowedGames(listOf(listOf(other), listOf(espn)), NOW_SEC, SECTION_WINDOW_SEC)

        assertEquals(listOf("espn-1"), merged.map { it.id })
    }

    @Test
    fun `the Sports section keeps the next 7 days, soonest first`() {
        val games = listOf(game("late", hours = 7 * 24 + 1), game("b", hours = 6 * 24), game("a", hours = 1))

        assertEquals(listOf("a", "b"), mergeFollowedGames(listOf(games), NOW_SEC, SECTION_WINDOW_SEC).map { it.id })
    }

    @Test
    fun `Home keeps the next 48 hours, 10 games at most`() {
        val games = (1..12).map { game("g$it", hours = it * 3) } + game("far", hours = 49)

        val merged = mergeFollowedGames(listOf(games), NOW_SEC, HOME_WINDOW_SEC, HOME_GAMES)

        assertEquals((1..10).map { "g$it" }, merged.map { it.id })
    }

    @Test
    fun `a game on now stays, a finished or cancelled one goes`() {
        val games =
            listOf(
                game("on", hours = -1),
                game("long-over", hours = -4),
                game("finished", hours = 1).copy(state = GameState.FINISHED),
                game("cancelled", hours = 2).copy(state = GameState.CANCELLED),
            )

        assertEquals(listOf("on"), mergeFollowedGames(listOf(games), NOW_SEC, HOME_WINDOW_SEC).map { it.id })
    }

    @Test
    fun `a failed read leaves its games out, and every read failing is a failure`() {
        val data = FakeData(teamGames = mapOf(ARSENAL.id to listOf(game("1", hours = 1))), leagueGames = emptyMap())
        data.failing += LEAGUE.id
        val followed = FollowedSports.Followed(teams = listOf(ARSENAL), leagues = listOf(LEAGUE))

        assertEquals(listOf(listOf("1")), runBlocking { readFollowedGames(data, followed) }.map { list -> list.map { it.id } })

        data.failing += ARSENAL.id
        val failed = runCatching { runBlocking { readFollowedGames(data, followed) } }
        assertTrue(failed.isFailure)
    }

    @Test
    fun `the Sports section reads the games of all it follows and again on a new follow`() {
        val data =
            FakeData(
                teamGames = mapOf(ARSENAL.id to listOf(game("1", hours = 3))),
                leagueGames = mapOf(LEAGUE.id to listOf(game("1", hours = 3), game("2", hours = 30))),
                follows = FollowedSports.Followed(teams = listOf(ARSENAL)),
            )
        val vm = SportsSectionViewModel(data, now = { NOW_SEC * 1000 }).also { created += it }

        assertEquals(listOf("1"), vm.awaitMatched().games.map { it.id })

        data.follows.value = FollowedSports.Followed(teams = listOf(ARSENAL), leagues = listOf(LEAGUE))
        vm.games.await { it is GamesState.Loaded && it.games.size == 2 }
        assertEquals(
            CHANNEL,
            vm
                .awaitMatched()
                .channelsOf(game("2", hours = 30))
                ?.first()
                ?.stream,
        )
    }

    @Test
    fun `the Sports section says the games failed, with the raw error for a Nerd, and nothing followed reads nothing`() {
        val data = FakeData(follows = FollowedSports.Followed(teams = listOf(ARSENAL)))
        data.failing += ARSENAL.id
        val vm = SportsSectionViewModel(data, now = { NOW_SEC * 1000 }).also { created += it }

        val failed = vm.games.await { it is GamesState.Failed } as GamesState.Failed
        assertEquals("ESPN down", failed.raw)

        val empty = FakeData()
        val idle = SportsSectionViewModel(empty, now = { NOW_SEC * 1000 }).also { created += it }
        idle.followed.await { it != null }
        assertEquals(0, empty.reads)
    }

    @Test
    fun `Home's row is kept 15 minutes, read again after them or when the follows change`() {
        var nowMs = NOW_SEC * 1000
        val data =
            FakeData(
                teamGames = mapOf(ARSENAL.id to listOf(game("1", hours = 3))),
                leagueGames = mapOf(LEAGUE.id to listOf(game("2", hours = 10))),
                follows = FollowedSports.Followed(teams = listOf(ARSENAL)),
            )
        val loader = UpcomingGamesLoader(data, enabled = { true }, profileId = { "p1" }, now = { nowMs })

        val first = runBlocking { loader.load() }
        assertEquals(listOf("1"), first.map { it.game.id })
        assertEquals(CHANNEL, first.single().bestChannel?.stream)
        assertEquals(1, data.reads)

        nowMs += UpcomingGamesLoader.REFRESH_MS - 1
        runBlocking { loader.load() }
        assertEquals(1, data.reads)

        nowMs += 1
        runBlocking { loader.load() }
        assertEquals(2, data.reads)

        data.follows.value = FollowedSports.Followed(teams = listOf(ARSENAL), leagues = listOf(LEAGUE))
        assertEquals(listOf("1", "2"), runBlocking { loader.load() }.map { it.game.id })
    }

    @Test
    fun `Home's row is absent when off, with nothing followed, and a failure is not kept`() {
        val data = FakeData(teamGames = mapOf(ARSENAL.id to listOf(game("1", hours = 3))))
        var on = false
        val loader = UpcomingGamesLoader(data, enabled = { on }, profileId = { "p1" }, now = { NOW_SEC * 1000 })

        data.follows.value = FollowedSports.Followed(teams = listOf(ARSENAL))
        assertEquals(emptyList<UpcomingGame>(), runBlocking { loader.load() })
        assertEquals(0, data.reads)

        on = true
        data.follows.value = FollowedSports.Followed()
        assertEquals(emptyList<UpcomingGame>(), runBlocking { loader.load() })

        data.follows.value = FollowedSports.Followed(teams = listOf(ARSENAL))
        data.failing += ARSENAL.id
        assertEquals(emptyList<UpcomingGame>(), runBlocking { loader.load() })
        data.failing.clear()
        assertEquals(listOf("1"), runBlocking { loader.load() }.map { it.game.id })
    }

    private fun <T> StateFlow<T>.await(predicate: (T) -> Boolean): T = runBlocking { withTimeout(TIMEOUT_MS) { first(predicate) } }

    private fun SportsGamesViewModel.awaitMatched(): GamesState.Loaded =
        games.await { it is GamesState.Loaded && it.games.isNotEmpty() && it.channels.size == it.games.size } as GamesState.Loaded

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val NOW_SEC = 4_000_000_000L
        val ARSENAL = SportsTeam(SportsSource.ESPN, "soccer/359", "Arsenal", sport = "soccer")
        val CHELSEA = SportsTeam(SportsSource.ESPN, "soccer/363", "Chelsea", sport = "soccer")
        val LEAGUE = SportsLeague(SportsSource.ESPN, "soccer/eng.1", "Premier League", "soccer")
        val CHANNEL = LiveChannel(1, 7, "UK: Sky Sports Main Event", "skysports.uk", "sport")

        // Arsenal v Chelsea, [hours] from now; each id its own game (TeamNames.sameGame needs the
        // start within 2 hours, so games far apart stay apart).
        fun game(
            id: String,
            hours: Int,
        ) = SportsGame(SportsSource.ESPN, id, NOW_SEC + hours * 3_600L, ARSENAL, CHELSEA, LEAGUE)
    }
}
