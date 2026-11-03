package org.njarasoa.fijerena.feature.search

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.sports.FollowedSports
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.GameChannelHow
import org.njarasoa.fijerena.core.network.sports.LiveChannel
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.network.sports.SportsLeague
import org.njarasoa.fijerena.core.network.sports.SportsSearchResult
import org.njarasoa.fijerena.core.network.sports.SportsSource
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.ui.sports.GameMatcher
import org.njarasoa.fijerena.core.ui.sports.GamesState
import org.njarasoa.fijerena.core.ui.sports.LocalGameBellStore
import org.njarasoa.fijerena.core.ui.sports.TeamsSearchViewModel
import org.njarasoa.fijerena.feature.sports.SportsSectionFocusTest
import org.njarasoa.fijerena.testing.FocusWalk
import org.njarasoa.fijerena.testing.back
import org.njarasoa.fijerena.testing.center
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.left
import org.njarasoa.fijerena.testing.right
import org.njarasoa.fijerena.testing.up
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme
import org.robolectric.RobolectricTestRunner

/**
 * Search's Teams type on TV (the sports teams plan → Screens): the best team first with its games,
 * then the other teams and the leagues. Down from the field lands on the best team, then goes down
 * through its games, the other teams and the leagues; Up goes back the same way. OK on a game opens
 * its sheet, Back returns to the game; OK on the best team opens its page. Follow and the team's
 * bell are Right of the best team, and Follow keeps its place when the bell comes.
 */
@RunWith(RobolectricTestRunner::class)
class TeamsSearchPaneFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val walk = FocusWalk(rule)
    private val created = mutableListOf<TeamsSearchViewModel>()
    private val opened = mutableListOf<Screen.SportsTeam>()
    private val played = mutableListOf<Screen.CategoryList>()

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
    }

    private class FakeData : TeamsSearchViewModel.Data {
        val follows = MutableStateFlow(FollowedSports.Followed())

        override val nerd = false

        override suspend fun search(query: String) = SportsSearchResult(listOf(ARSENAL_WOMEN, ARSENAL, SARANDI), listOf(LEAGUE))

        override fun followed(): Flow<FollowedSports.Followed> = follows

        override suspend fun teamGames(team: SportsTeam) = GAMES

        override suspend fun matcher(): GameMatcher =
            GameMatcher { game -> if (game.id == "1") listOf(GameChannel(PPV, null, null, GameChannelHow.CHANNEL_NAME)) else emptyList() }

        override suspend fun follow(team: SportsTeam) {
            follows.value = follows.value.copy(teams = follows.value.teams + team)
        }

        override suspend fun unfollow(team: SportsTeam) {
            follows.value = FollowedSports.Followed()
        }
    }

    // The pane with "arsenal" searched, once the best team's games have their channels.
    private fun setPane() {
        val vm = TeamsSearchViewModel(FakeData()).also { created += it }
        rule.setContent {
            FirstVideoPlayerTheme {
                CompositionLocalProvider(LocalGameBellStore provides SportsSectionFocusTest.FakeBells()) {
                    TeamsSearchPane(
                        query = "arsenal",
                        onQueryChange = {},
                        holdFocus = false,
                        fieldModifier = Modifier,
                        returnFocus = rememberNavReturnFocus(),
                        onTeamSelected = { opened += it },
                        onLeagueSelected = {},
                        onPlay = { played += it },
                        viewModel = vm,
                    )
                }
            }
        }
        rule.waitUntil(WAIT_MS) {
            (vm.bestGames.value as? GamesState.Loaded)?.let { it.channels.size == it.games.size } == true
        }
        walk.awaitFocused(FIELD)
    }

    @Test
    fun `Down from the field - the best team, its games, the other teams, the leagues, and Up comes back`() {
        setPane()
        walk.run(
            down(BEST),
            down("C / Arsenal v Chelsea"),
            down("C / Chelsea v Arsenal"),
            down("A / Arsenal / Football · Women's Super League"),
            down("A / Arsenal Sarandi"),
            down("P / Premier League"),
            up("A / Arsenal Sarandi"),
            up("A / Arsenal / Football · Women's Super League"),
            up("C / Chelsea v Arsenal"),
            up("C / Arsenal v Chelsea"),
            up(BEST),
            up(FIELD),
        )
    }

    @Test
    fun `a game opens its sheet on its channel, Back returns to it, and the best team opens its page`() {
        setPane()
        walk.run(
            down(BEST),
            down("C / Arsenal v Chelsea"),
            center("P / PPV 3: Arsenal v Chelsea / UK"),
            back("C / Arsenal v Chelsea"),
            down("C / Chelsea v Arsenal"),
            center("Not found on your channels"),
            back("C / Chelsea v Arsenal"),
            up("C / Arsenal v Chelsea"),
            center("P / PPV 3: Arsenal v Chelsea / UK"),
        )
        // The game has not started: OK asks first, and Watch now plays the channel.
        walk.press(Key.DirectionCenter)
        walk.awaitFocused("Watch now")
        walk.press(Key.DirectionCenter)
        assertEquals(listOf("7"), played.map { it.initialStreamId })

        walk.press(Key.Back)
        walk.run(up(BEST))
        walk.press(Key.DirectionCenter)
        assertEquals(listOf(ARSENAL.id), opened.map { it.teamId })
    }

    @Test
    fun `Follow and the team's bell are Right of the best team, and Follow keeps its place when the bell comes`() {
        setPane()
        walk.run(down(BEST), right("Follow"))
        val before =
            rule
                .onNodeWithContentDescription("Follow")
                .fetchSemanticsNode()
                .boundsInRoot.right
        walk.press(Key.DirectionCenter)
        walk.awaitFocused("Unfollow")
        val after =
            rule
                .onNodeWithContentDescription("Unfollow")
                .fetchSemanticsNode()
                .boundsInRoot.right
        assertEquals(before, after)
        walk.run(right("Remind me of every game"), left("Unfollow"), left(BEST))
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val FIELD = "arsenal"
        const val BEST = "A / Arsenal / Football · Premier League"
        val LEAGUE = SportsLeague(SportsSource.ESPN, "soccer/eng.1", "Premier League", "soccer")
        val ARSENAL =
            SportsTeam(SportsSource.ESPN, "soccer/359", "Arsenal", sport = "soccer", league = "soccer/eng.1", leagueName = "Premier League")
        val ARSENAL_WOMEN =
            SportsTeam(
                SportsSource.ESPN,
                "soccer/19973",
                "Arsenal",
                sport = "soccer",
                league = "soccer/eng.w.1",
                leagueName = "Women's Super League",
            )
        val SARANDI = SportsTeam(SportsSource.ESPN, "soccer/2635", "Arsenal Sarandi", sport = "soccer", league = "soccer/arg.3")
        val CHELSEA = SportsTeam(SportsSource.ESPN, "soccer/363", "Chelsea", sport = "soccer")
        val PPV = LiveChannel(1, 7, "UK| PPV 3: Arsenal v Chelsea", null, "ppv")

        // In the future, so both stay coming games whatever day the test runs.
        val GAMES =
            listOf(
                SportsGame(SportsSource.ESPN, "1", 4_000_000_000, ARSENAL, CHELSEA, LEAGUE),
                SportsGame(SportsSource.ESPN, "2", 4_000_090_000, CHELSEA, ARSENAL, LEAGUE),
            )
    }
}
