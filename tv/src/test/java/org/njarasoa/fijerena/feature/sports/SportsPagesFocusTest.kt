package org.njarasoa.fijerena.feature.sports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.navigation.Screen
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
import org.njarasoa.fijerena.core.ui.sports.GameMatcher
import org.njarasoa.fijerena.core.ui.sports.LocalGameBellStore
import org.njarasoa.fijerena.core.ui.sports.SportsLeagueViewModel
import org.njarasoa.fijerena.core.ui.sports.SportsPageData
import org.njarasoa.fijerena.core.ui.sports.SportsTeamViewModel
import org.njarasoa.fijerena.testing.FocusWalk
import org.njarasoa.fijerena.testing.back
import org.njarasoa.fijerena.testing.center
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.left
import org.njarasoa.fijerena.testing.right
import org.njarasoa.fijerena.testing.up
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme
import org.robolectric.RobolectricTestRunner

/**
 * The TV team and league pages and the game sheet (the sports teams plan → Screens): focus on
 * the first game, Up to Follow, OK on a game opens its sheet on its first channel, Down to the
 * broadcasters, Back closes the sheet onto the game; a league's teams follow its games. A game's
 * bell is Right of its row; once the team is followed, its bell for every game is Right of Unfollow. A row with
 * no logo reads its letter first ("C / Chelsea"), as the device walk reads it.
 */
@RunWith(RobolectricTestRunner::class)
class SportsPagesFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val walk = FocusWalk(rule)
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()
    private val bells = SportsSectionFocusTest.FakeBells()

    // The app's theme, with the bells' reminders in memory.
    @Composable
    private fun WithBells(content: @Composable () -> Unit) {
        FirstVideoPlayerTheme { CompositionLocalProvider(LocalGameBellStore provides bells, content = content) }
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
    }

    private class FakeData : SportsPageData {
        val follows = MutableStateFlow(FollowedSports.Followed())

        override val devMode = false

        override suspend fun teamGames(team: SportsTeam) = GAMES

        override suspend fun leagueGames(league: SportsLeague) = GAMES

        override suspend fun leagueTeams(league: SportsLeague) = listOf(ARSENAL, LIVERPOOL)

        override fun followed(): Flow<FollowedSports.Followed> = follows

        override suspend fun follow(team: SportsTeam) {
            follows.value = follows.value.copy(teams = follows.value.teams + team)
        }

        override suspend fun unfollow(team: SportsTeam) {
            follows.value = FollowedSports.Followed()
        }

        override suspend fun follow(league: SportsLeague) {
            follows.value = follows.value.copy(leagues = follows.value.leagues + league)
        }

        override suspend fun unfollow(league: SportsLeague) {
            follows.value = FollowedSports.Followed()
        }

        override suspend fun matcher(): GameMatcher =
            GameMatcher { game ->
                if (game.id == "1") {
                    listOf(GameChannel(CHANNEL, null, SKY, GameChannelHow.BROADCASTER_CHANNEL)) +
                        listOf("ZA", "NG", "KE", "MG").map {
                            GameChannel(
                                null,
                                null,
                                Broadcaster("SuperSport", it, from = BroadcasterOrigin.SEASON_TABLE),
                                GameChannelHow.BROADCASTER_ONLY,
                            )
                        }
                } else {
                    listOf(GameChannel(null, null, null, GameChannelHow.NOT_ANNOUNCED))
                }
            }
    }

    @Test
    fun `team page - first game, Up to Follow, the sheet opens on its channel and Back returns to the game`() {
        val played = mutableListOf<Screen.CategoryList>()
        val vm = SportsTeamViewModel(FakeData(), ARSENAL).also { created += it }
        rule.setContent {
            WithBells {
                SportsTeamScreen(team = ARSENAL, onPlay = { played += it }, onBack = {}, viewModel = vm)
            }
        }
        walk.awaitFocused("C / Chelsea")
        walk.run(
            up("Follow"),
            down("C / Chelsea"),
            down("L / Liverpool"),
            up("C / Chelsea"),
            center("U / UK: Sky Sports Main Event"),
            down("SuperSport"),
            up("U / UK: Sky Sports Main Event"),
            back("C / Chelsea"),
        )

        // A channel in the sheet opens its preview, as a guide result.
        walk.press(Key.DirectionCenter)
        walk.press(Key.DirectionCenter)
        assertEquals(
            listOf(Screen.CategoryList(contentType = "LIVE_TV", initialCategoryId = "sport", initialStreamId = "7", providerId = 1)),
            played,
        )
    }

    @Test
    fun `the sheet names a broadcaster's countries, OK shows them all`() {
        val vm = SportsTeamViewModel(FakeData(), ARSENAL).also { created += it }
        rule.setContent {
            WithBells {
                SportsTeamScreen(team = ARSENAL, onPlay = {}, onBack = {}, viewModel = vm)
            }
        }
        walk.awaitFocused("C / Chelsea")
        walk.run(center("U / UK: Sky Sports Main Event"), down("SuperSport"))
        assertTrue(walk.focusedText(), walk.focusedText().contains("4 countries"))

        walk.press(Key.DirectionCenter)
        assertTrue(walk.focusedText(), walk.focusedText().contains("Madagascar"))
    }

    @Test
    fun `a game with nothing known shows TV not announced yet, and Follow becomes Unfollow`() {
        val vm = SportsTeamViewModel(FakeData(), ARSENAL).also { created += it }
        rule.setContent {
            WithBells {
                SportsTeamScreen(team = ARSENAL, onPlay = {}, onBack = {}, viewModel = vm)
            }
        }
        walk.awaitFocused("C / Chelsea")
        walk.run(down("L / Liverpool"), center("TV not announced yet"), back("L / Liverpool"), up("C / Chelsea"), up("Follow"))
        walk.press(Key.DirectionCenter)
        walk.awaitFocused("Unfollow")
    }

    @Test
    fun `a game's bell is Right of its row, and a followed team's bell is Right of Unfollow`() {
        val vm = SportsTeamViewModel(FakeData(), ARSENAL).also { created += it }
        rule.setContent {
            WithBells {
                SportsTeamScreen(team = ARSENAL, onPlay = {}, onBack = {}, viewModel = vm)
            }
        }
        walk.awaitFocused("C / Chelsea")
        walk.run(right("Remind Me"), down("Remind Me"), left("L / Liverpool"), up("C / Chelsea"), up("Follow"))
        walk.press(Key.DirectionCenter)
        walk.awaitFocused("Unfollow")
        walk.run(right("Remind Me of Every Game"))
        walk.press(Key.DirectionCenter)
        walk.awaitFocused("Reminders Set for Every Game")
        assertEquals(setOf("espn:soccer/359"), bells.reminded.value.teams)
        walk.run(down("C / Chelsea"))
    }

    @Test
    fun `league page - its teams come after its games`() {
        val vm = SportsLeagueViewModel(FakeData(), LEAGUE).also { created += it }
        val opened = mutableListOf<Screen.SportsTeam>()
        rule.setContent {
            WithBells {
                SportsLeagueScreen(league = LEAGUE, onTeamSelected = { opened += it }, onPlay = {}, onBack = {}, viewModel = vm)
            }
        }
        walk.awaitFocused("A / Arsenal v Chelsea")
        walk.run(up("Follow"), down("A / Arsenal v Chelsea"), down("L / Liverpool v Arsenal"), down("A / Arsenal"), down("L / Liverpool"))
        walk.press(Key.DirectionCenter)
        assertEquals(listOf(LIVERPOOL.id), opened.map { it.teamId })
    }

    private companion object {
        val ARSENAL = SportsTeam(SportsSource.ESPN, "soccer/359", "Arsenal", sport = "soccer", leagueName = "Premier League")
        val CHELSEA = SportsTeam(SportsSource.ESPN, "soccer/363", "Chelsea", sport = "soccer")
        val LIVERPOOL = SportsTeam(SportsSource.ESPN, "soccer/364", "Liverpool", sport = "soccer")
        val LEAGUE = SportsLeague(SportsSource.ESPN, "soccer/eng.1", "Premier League", "soccer")
        val SKY = Broadcaster("Sky Sports", "GB", from = BroadcasterOrigin.SEASON_TABLE)
        val CHANNEL = LiveChannel(1, 7, "UK: Sky Sports Main Event", "skysports.uk", "sport")

        // In the future, so both stay coming games whatever day the test runs.
        val GAMES =
            listOf(
                SportsGame(SportsSource.ESPN, "1", 4_000_000_000, ARSENAL, CHELSEA, LEAGUE, broadcasters = listOf(SKY)),
                SportsGame(SportsSource.ESPN, "2", 4_000_090_000, LIVERPOOL, ARSENAL, LEAGUE),
            )
    }
}
