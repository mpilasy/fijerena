package org.njarasoa.fijerena.feature.sports

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.GameChannelHow
import org.njarasoa.fijerena.core.network.sports.LiveChannel
import org.njarasoa.fijerena.core.network.sports.ReminderChannel
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.network.sports.SportsLeague
import org.njarasoa.fijerena.core.network.sports.SportsSource
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.ui.sports.LocalGameBellStore
import org.njarasoa.fijerena.core.ui.sports.ReminderItem
import org.njarasoa.fijerena.core.ui.sports.RemindersState
import org.njarasoa.fijerena.core.ui.sports.TeamRuleItem
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
 * The TV Reminders page (the Reminders page plan, RP-D10): focus on the first row (Upcoming, else
 * Every Game Of), Right along its icon actions, Up from the first row to Reminder Time and Right to
 * Clear All; after Remove or Stop, the next row, else the previous one, else Reminder Time; an empty
 * page opens on Reminder Time, with no Clear All. A game's Open shows its sheet, and Back from the
 * sheet returns to the control that opened it.
 */
@RunWith(RobolectricTestRunner::class)
class RemindersFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val walk = FocusWalk(rule)
    private var state by mutableStateOf<RemindersState>(RemindersState.Loading)
    private val played = mutableListOf<Screen.CategoryList>()
    private var cleared = 0

    /** The page over [state]; Remove, Stop and Clear All change it as the database would. */
    private fun setPage(
        loaded: RemindersState.Loaded,
        findGame: suspend (ReminderItem.Game) -> SportsGame? = { null },
    ) {
        state = loaded
        val actions =
            RemindersActions(
                setLeadMinutes = {},
                remove = { item -> state = page().copy(upcoming = page().upcoming - item) },
                stop = { rule -> state = page().copy(teams = page().teams - rule) },
                clearAll = {
                    cleared++
                    state = RemindersState.Loaded(emptyList(), emptyList())
                },
                findGame = findGame,
                findChannels = { listOf(GameChannel(CHANNEL, null, null, GameChannelHow.GUIDE)) },
                play = { played += it },
                back = {},
            )
        rule.setContent {
            FirstVideoPlayerTheme {
                CompositionLocalProvider(LocalGameBellStore provides SportsSectionFocusTest.FakeBells()) {
                    TvRemindersContent(state = state, leadMinutes = 10, leadChoices = listOf(0, 10), actions = actions)
                }
            }
        }
    }

    private fun page() = state as RemindersState.Loaded

    @Test
    fun `the first row has focus, Right walks its actions, and Left comes back`() {
        setPage(RemindersState.Loaded(listOf(GAME, PROGRAMME), listOf(RULE)))

        walk.awaitFocused("Arsenal v Chelsea")
        walk.run(
            right("Open"),
            right("Remove reminder"),
            right("Remove reminder"),
            left("Open"),
            left("Arsenal v Chelsea"),
            down("Evening News"),
            down("L / Liverpool"),
            right("Stop every game"),
            right("Stop every game"),
        )
    }

    @Test
    fun `Up from the first row reaches Reminder Time, then Clear All, and Down comes back`() {
        setPage(RemindersState.Loaded(listOf(GAME, PROGRAMME), emptyList()))

        walk.awaitFocused("Arsenal v Chelsea")
        walk.run(
            up("10 minutes before"),
            right("Clear all"),
            right("Clear all"),
            down("Arsenal v Chelsea"),
            right("Open"),
            up("10 minutes before"),
        )
    }

    @Test
    fun `with no upcoming reminder, the first team rule has focus`() {
        setPage(RemindersState.Loaded(emptyList(), listOf(RULE)))

        walk.awaitFocused("L / Liverpool")
        walk.run(up("10 minutes before"), down("L / Liverpool"))
    }

    @Test
    fun `after Remove the next row has focus, else the previous one, else Reminder Time`() {
        setPage(RemindersState.Loaded(listOf(GAME, PROGRAMME), listOf(RULE)))

        walk.awaitFocused("Arsenal v Chelsea")
        // The first row: the next one takes focus.
        walk.run(right("Open"), right("Remove reminder"), center("Evening News"))
        // The last row (Stop): the previous one.
        walk.run(down("L / Liverpool"), right("Stop every game"), center("Evening News"))
        // The only row: Reminder Time, and Clear All goes.
        walk.run(right("Open"), right("Remove reminder"), center("10 minutes before"))
        rule.onNodeWithText("No reminders", substring = true).assertExists()
        walk.run(right("10 minutes before"))
    }

    @Test
    fun `a reminder that rings goes from the page and focus goes to the next row`() {
        setPage(RemindersState.Loaded(listOf(GAME, PROGRAMME), emptyList()))

        walk.awaitFocused("Arsenal v Chelsea")
        rule.runOnIdle { state = page().copy(upcoming = listOf(PROGRAMME)) }
        walk.awaitFocused("Evening News")
    }

    @Test
    fun `an empty page opens on Reminder Time, without Clear All`() {
        setPage(RemindersState.Loaded(emptyList(), emptyList()))

        walk.awaitFocused("10 minutes before")
        rule.onNodeWithText("No reminders", substring = true).assertExists()
        walk.run(right("10 minutes before"), down("10 minutes before"))
    }

    @Test
    fun `Clear All asks first, with Cancel focused, then focus goes to Reminder Time`() {
        setPage(RemindersState.Loaded(listOf(GAME), listOf(RULE)))

        walk.awaitFocused("Arsenal v Chelsea")
        walk.run(up("10 minutes before"), right("Clear all"), center("Cancel"), right("Clear all"))
        walk.press(Key.DirectionCenter)
        walk.awaitFocused("10 minutes before")
        assertEquals(1, cleared)
    }

    @Test
    fun `Open shows the game's sheet on its channel, and Back returns to Open`() {
        setPage(RemindersState.Loaded(listOf(GAME), emptyList()), findGame = { GAME_OF_REMINDER })

        walk.awaitFocused("Arsenal v Chelsea")
        walk.run(right("Open"))
        walk.press(Key.DirectionCenter)
        walk.awaitFocused("S / Sky Sports Main Event")
        walk.run(back("Open"))
        walk.run(left("Arsenal v Chelsea"), center("S / Sky Sports Main Event"), back("Arsenal v Chelsea"))
    }

    @Test
    fun `a game gone from its schedule opens nothing and focus stays`() {
        setPage(RemindersState.Loaded(listOf(GAME), emptyList()), findGame = { null })

        walk.awaitFocused("Arsenal v Chelsea")
        walk.run(center("Arsenal v Chelsea"), right("Open"))
    }

    @Test
    fun `a programme that is on opens its channel`() {
        val onNow = PROGRAMME.copy(startEpochSec = System.currentTimeMillis() / 1000 - 60)
        setPage(RemindersState.Loaded(listOf(onNow), emptyList()))

        walk.awaitFocused("Evening News")
        walk.press(Key.DirectionCenter)
        assertEquals("7", played.single().initialStreamId)
    }

    private companion object {
        val ARSENAL = SportsTeam(SportsSource.ESPN, "soccer/359", "Arsenal", sport = "soccer")
        val CHELSEA = SportsTeam(SportsSource.ESPN, "soccer/363", "Chelsea", sport = "soccer")
        val LEAGUE = SportsLeague(SportsSource.ESPN, "soccer/eng.1", "Premier League", "soccer")
        val CHANNEL = LiveChannel(1, 7, "UK: Sky Sports Main Event", "skysports.uk", "sport")
        const val START = 4_000_000_000L

        val GAME = ReminderItem.Game(SportsSource.ESPN, "1", "Arsenal", "Chelsea", "Premier League", START, ARSENAL, fromTeamRule = false)
        val GAME_OF_REMINDER = SportsGame(SportsSource.ESPN, "1", START, ARSENAL, CHELSEA, LEAGUE)
        val PROGRAMME =
            ReminderItem.Programme(
                "1:7:${START + 3600}",
                "Evening News",
                ReminderChannel(1, 7, "news", "BBC One"),
                START + 3600,
            )
        val RULE = TeamRuleItem(SportsSource.ESPN, "soccer/364", "Liverpool", "Premier League", null, START + 7200, 2)
    }
}
