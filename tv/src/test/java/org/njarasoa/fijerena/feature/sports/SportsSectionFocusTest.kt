package org.njarasoa.fijerena.feature.sports

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
 * once), Right to its bell, which sets the reminder; Up from the logos to Find Teams and Down back;
 * OK on a game opens its sheet, whose bell is above its first channel. Nothing followed: Find Teams
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
            down("A / Arsenal v Chelsea"),
            down("L / Liverpool v Arsenal"),
            up("A / Arsenal v Chelsea"),
            right("Remind Me"),
        )
        walk.press(Key.DirectionCenter)
        walk.awaitFocused("Reminder Set")
        assertEquals(setOf("espn/1"), bells.reminded.value.games)
        walk.run(left("A / Arsenal v Chelsea"))
    }

    @Test
    fun `Up from the logos reaches Find Teams, and Down comes back to the first logo`() {
        var searches = 0
        setSection(FollowedSports.Followed(teams = listOf(ARSENAL), leagues = listOf(LEAGUE)), onFindTeams = { searches++ })

        walk.awaitFocused("A / Arsenal")
        walk.run(up("Find Teams"), down("A / Arsenal"), up("Find Teams"))
        walk.press(Key.DirectionCenter)
        assertEquals(1, searches)
    }

    @Test
    fun `a game opens its sheet on its channel, the bell above it, and Back returns to the game`() {
        val played = mutableListOf<Screen.CategoryList>()
        setSection(FollowedSports.Followed(teams = listOf(ARSENAL)), played = played)

        walk.awaitFocused("A / Arsenal")
        walk.run(
            down("A / Arsenal v Chelsea"),
            center("U / UK: Sky Sports Main Event"),
            up("Remind Me"),
            down("U / UK: Sky Sports Main Event"),
            back("A / Arsenal v Chelsea"),
        )
    }

    @Test
    fun `nothing followed - Find Teams has focus`() {
        var searches = 0
        setSection(FollowedSports.Followed(), onFindTeams = { searches++ })

        walk.awaitFocused("Find Teams")
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
