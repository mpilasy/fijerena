package org.njarasoa.fijerena.feature.sports

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.sports.FollowedSports
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.GameChannelHow
import org.njarasoa.fijerena.core.network.sports.GameReminders
import org.njarasoa.fijerena.core.network.sports.LiveChannel
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.network.sports.SportsLeague
import org.njarasoa.fijerena.core.network.sports.SportsSource
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.ui.sports.GameBellStore
import org.njarasoa.fijerena.core.ui.sports.GameMatcher
import org.njarasoa.fijerena.core.ui.sports.LocalGameBellStore
import org.njarasoa.fijerena.core.ui.sports.SportsPageData
import org.njarasoa.fijerena.core.ui.sports.SportsSectionViewModel
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
 * The TV Sports section (the sports teams plan → Sports section): focus on the first followed
 * logo, Right along the logos, Down to the games (a game of a followed team in a followed league
 * once), Right to its bell, which sets the reminder; Up from the logos to Find teams and Down back;
 * OK on a game opens its sheet, whose bell is above its first channel. Nothing followed: Find teams
 * has focus.
 */
@RunWith(RobolectricTestRunner::class)
class SportsSectionFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val walk = FocusWalk(rule)
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
    }

    private class FakeData(
        follows: FollowedSports.Followed,
    ) : SportsPageData {
        val follows = MutableStateFlow(follows)

        override val devMode = false

        override suspend fun teamGames(team: SportsTeam) = listOf(GAMES[0])

        override suspend fun leagueGames(league: SportsLeague) = GAMES

        override suspend fun leagueTeams(league: SportsLeague) = emptyList<SportsTeam>()

        override fun followed(): Flow<FollowedSports.Followed> = follows

        override suspend fun follow(team: SportsTeam) = Unit

        override suspend fun unfollow(team: SportsTeam) = Unit

        override suspend fun follow(league: SportsLeague) = Unit

        override suspend fun unfollow(league: SportsLeague) = Unit

        override suspend fun matcher(): GameMatcher = GameMatcher { listOf(GameChannel(CHANNEL, null, null, GameChannelHow.GUIDE)) }
    }

    /** The bells' reminders in memory. */
    class FakeBells : GameBellStore {
        val reminded = MutableStateFlow(GameReminders.Reminded())

        override fun reminded(): Flow<GameReminders.Reminded> = reminded

        override suspend fun set(game: SportsGame) {
            reminded.value = reminded.value.copy(games = reminded.value.games + "${game.source.id}/${game.id}")
        }

        override suspend fun clear(game: SportsGame) {
            reminded.value = reminded.value.copy(games = reminded.value.games - "${game.source.id}/${game.id}")
        }

        override suspend fun setForTeam(team: SportsTeam) {
            reminded.value = reminded.value.copy(teams = reminded.value.teams + "${team.source.id}:${team.id}")
        }

        override suspend fun clearForTeam(team: SportsTeam) {
            reminded.value = reminded.value.copy(teams = reminded.value.teams - "${team.source.id}:${team.id}")
        }
    }

    private fun setSection(
        follows: FollowedSports.Followed,
        bells: FakeBells = FakeBells(),
        onFindTeams: () -> Unit = {},
        played: MutableList<Screen.CategoryList> = mutableListOf(),
    ) {
        val vm = SportsSectionViewModel(FakeData(follows), now = { NOW_MS }).also { created += it }
        rule.setContent {
            FirstVideoPlayerTheme {
                CompositionLocalProvider(LocalGameBellStore provides bells) {
                    SportsSectionScreen(
                        onTeamSelected = {},
                        onLeagueSelected = {},
                        onFindTeams = onFindTeams,
                        onPlay = { played += it },
                        viewModel = vm,
                    )
                }
            }
        }
    }

    @Test
    fun `the logos, then the games once each, each with its bell`() {
        val bells = FakeBells()
        setSection(FollowedSports.Followed(teams = listOf(ARSENAL), leagues = listOf(LEAGUE)), bells)

        walk.awaitFocused("A / Arsenal")
        walk.run(
            right("P / Premier League"),
            left("A / Arsenal"),
            down("A / C / Arsenal v Chelsea"),
            down("L / A / Liverpool v Arsenal"),
            up("A / C / Arsenal v Chelsea"),
            right("Remind me"),
        )
        walk.press(Key.DirectionCenter)
        walk.awaitFocused("Reminder set")
        assertEquals(setOf("espn/1"), bells.reminded.value.games)
        walk.run(left("A / C / Arsenal v Chelsea"))
    }

    @Test
    fun `Up from the logos reaches Find teams, and Down comes back to the first logo`() {
        var searches = 0
        setSection(FollowedSports.Followed(teams = listOf(ARSENAL), leagues = listOf(LEAGUE)), onFindTeams = { searches++ })

        walk.awaitFocused("A / Arsenal")
        walk.run(up("Find teams"), down("A / Arsenal"), up("Find teams"))
        walk.press(Key.DirectionCenter)
        assertEquals(1, searches)
    }

    @Test
    fun `a game opens its sheet on its channel, the bell above it, and Back returns to the game`() {
        val played = mutableListOf<Screen.CategoryList>()
        setSection(FollowedSports.Followed(teams = listOf(ARSENAL)), played = played)

        walk.awaitFocused("A / Arsenal")
        walk.run(
            down("A / C / Arsenal v Chelsea"),
            center("S / Sky Sports Main Event / UK"),
            up("Remind me"),
            down("S / Sky Sports Main Event / UK"),
            back("A / C / Arsenal v Chelsea"),
        )
    }

    @Test
    fun `Right from Find teams goes nowhere, not to a game's bell`() {
        setSection(FollowedSports.Followed(teams = listOf(ARSENAL), leagues = listOf(LEAGUE)))

        walk.awaitFocused("A / Arsenal")
        walk.run(up("Find teams"), right("Find teams"), down("A / Arsenal"))
    }

    @Test
    fun `Up from the games lands on the logo last focused, the first one at first`() {
        setSection(FollowedSports.Followed(teams = listOf(ARSENAL, CHELSEA, LIVERPOOL), leagues = listOf(LEAGUE)))

        walk.awaitFocused("A / Arsenal")
        walk.run(
            down("A / C / Arsenal v Chelsea"),
            up("A / Arsenal"),
            right("C / Chelsea"),
            right("L / Liverpool"),
            down("A / C / Arsenal v Chelsea"),
            up("L / Liverpool"),
        )
    }

    @Test
    fun `Right from outside the section, the follows read late, lands on the first logo`() {
        val data = FakeData(FollowedSports.Followed(teams = listOf(ARSENAL), leagues = listOf(LEAGUE)))
        val ready = MutableStateFlow(false)
        val late =
            object : SportsPageData by data {
                override fun followed(): Flow<FollowedSports.Followed> = combine(ready.filter { it }, data.follows) { _, f -> f }
            }
        val vm = SportsSectionViewModel(late, now = { NOW_MS }).also { created += it }
        val outside = FocusRequester()
        rule.setContent {
            FirstVideoPlayerTheme {
                CompositionLocalProvider(LocalGameBellStore provides FakeBells()) {
                    Row {
                        Box(modifier = Modifier.focusRequester(outside).focusable()) { BasicText("Rail") }
                        SportsSectionScreen(onTeamSelected = {}, onLeagueSelected = {}, onFindTeams = {}, onPlay = {}, viewModel = vm)
                    }
                }
            }
        }
        rule.runOnIdle { outside.requestFocus() }
        walk.awaitFocused("Rail")
        rule.runOnIdle { ready.value = true }
        // The section's own first focus.
        walk.awaitFocused("A / Arsenal")
        walk.run(down("A / C / Arsenal v Chelsea"))
        rule.runOnIdle { outside.requestFocus() }
        walk.awaitFocused("Rail")
        walk.run(right("A / Arsenal"))
    }

    @Test
    fun `nothing followed - Find teams has focus`() {
        var searches = 0
        setSection(FollowedSports.Followed(), onFindTeams = { searches++ })

        walk.awaitFocused("Find teams")
        walk.press(Key.DirectionCenter)
        assertEquals(1, searches)
    }

    private companion object {
        val ARSENAL = SportsTeam(SportsSource.ESPN, "soccer/359", "Arsenal", sport = "soccer")
        val CHELSEA = SportsTeam(SportsSource.ESPN, "soccer/363", "Chelsea", sport = "soccer")
        val LIVERPOOL = SportsTeam(SportsSource.ESPN, "soccer/364", "Liverpool", sport = "soccer")
        val LEAGUE = SportsLeague(SportsSource.ESPN, "soccer/eng.1", "Premier League", "soccer")
        val CHANNEL = LiveChannel(1, 7, "UK: Sky Sports Main Event", "skysports.uk", "sport")

        // Now, for the section's 7 days: the games are in the next two days of it, and have not started.
        const val NOW_MS = 3_999_990_000_000L

        val GAMES =
            listOf(
                SportsGame(SportsSource.ESPN, "1", 4_000_000_000, ARSENAL, CHELSEA, LEAGUE),
                SportsGame(SportsSource.ESPN, "2", 4_000_090_000, LIVERPOOL, ARSENAL, LEAGUE),
            )
    }
}
