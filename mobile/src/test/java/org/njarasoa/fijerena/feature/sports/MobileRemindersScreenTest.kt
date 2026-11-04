package org.njarasoa.fijerena.feature.sports

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.sports.ReminderChannel
import org.njarasoa.fijerena.core.network.sports.SportsSource
import org.njarasoa.fijerena.core.ui.sports.ReminderItem
import org.njarasoa.fijerena.core.ui.sports.RemindersState
import org.njarasoa.fijerena.core.ui.sports.TeamRuleItem
import org.robolectric.RobolectricTestRunner

/**
 * The phone's Reminders page (the Reminders page plan → Design P): its rows' text, their actions,
 * and the empty state.
 */
@RunWith(RobolectricTestRunner::class)
class MobileRemindersScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val nowSec = 1_800_000_000L
    private val game =
        ReminderItem.Game(
            source = SportsSource.ESPN,
            gameId = "g1",
            homeName = "Arsenal",
            awayName = "Chelsea",
            competitionName = "Premier League",
            startEpochSec = nowSec + 3_600,
            team = null,
            fromTeamRule = false,
        )
    private val programme =
        ReminderItem.Programme(
            id = "3:42:${nowSec + 7_200}",
            title = "Match of the Day",
            channel = ReminderChannel(providerId = 3, streamId = 42, categoryId = "7", name = "UK| BBC One"),
            startEpochSec = nowSec + 7_200,
        )
    private val rule =
        TeamRuleItem(
            source = SportsSource.ESPN,
            teamId = "359",
            teamName = "Arsenal",
            leagueName = "Premier League",
            team = null,
            nextStartEpochSec = nowSec + 3_600,
            gameCount = 2,
        )

    @Test
    fun `a game's title has both teams, a programme's its own title`() {
        assertEquals("Arsenal v Chelsea", reminderTitle(context, game))
        assertEquals("Arsenal", reminderTitle(context, game.copy(awayName = null)))
        assertEquals("Match of the Day", reminderTitle(context, programme))
    }

    @Test
    fun `the second line is the competition, or the channel without its tags`() {
        assertEquals("Premier League", reminderDetail(game))
        assertEquals("BBC One", reminderDetail(programme))
    }

    @Test
    fun `the time line ends with when it rings`() {
        val text = reminderWhen(context, game.startEpochSec, game.startEpochSec - 600)
        assertEquals(
            true,
            text.endsWith(
                context.getString(
                    org.njarasoa.fijerena.core.ui.R.string.reminders_rings_at,
                    timeOf(game.startEpochSec - 600),
                ),
            ),
        )
    }

    @Test
    fun `the rows show both sections, and Remove and Stop act on their row`() {
        val removed = mutableListOf<ReminderItem>()
        val stopped = mutableListOf<TeamRuleItem>()
        composeTestRule.setContent {
            RemindersContent(
                state = RemindersState.Loaded(upcoming = listOf(game, programme), teams = listOf(rule)),
                leadMinutes = 10,
                nowSec = nowSec,
                onOpen = {},
                onRemove = { removed += it },
                onStop = { stopped += it },
            )
        }

        composeTestRule.onNodeWithText("Reminders ring on this device only").assertExists()
        composeTestRule.onNodeWithText("Upcoming").assertExists()
        composeTestRule.onNodeWithText("Arsenal v Chelsea").assertExists()
        composeTestRule.onNodeWithText("Match of the Day").assertExists()
        composeTestRule.onNodeWithText("Every Game Of").assertExists()
        composeTestRule.onNodeWithText("2 coming games", substring = true).assertExists()

        composeTestRule.onAllNodesWithContentDescription("Remove reminder")[1].performClick()
        composeTestRule.onNodeWithContentDescription("Stop every game").performScrollTo().performClick()

        assertEquals(listOf<ReminderItem>(programme), removed)
        assertEquals(listOf(rule), stopped)
    }

    @Test
    fun `Open acts on its row`() {
        val opened = mutableListOf<ReminderItem>()
        composeTestRule.setContent {
            RemindersContent(
                state = RemindersState.Loaded(upcoming = listOf(game), teams = emptyList()),
                leadMinutes = 10,
                nowSec = nowSec,
                onOpen = { opened += it },
                onRemove = {},
                onStop = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Open").performClick()

        assertEquals(listOf<ReminderItem>(game), opened)
    }

    @Test
    fun `no reminder shows the empty state`() {
        composeTestRule.setContent {
            RemindersContent(
                state = RemindersState.Loaded(upcoming = emptyList(), teams = emptyList()),
                leadMinutes = 10,
                nowSec = nowSec,
                onOpen = {},
                onRemove = {},
                onStop = {},
            )
        }

        composeTestRule.onNodeWithText(context.getString(org.njarasoa.fijerena.core.ui.R.string.reminders_empty)).assertExists()
    }

    private fun timeOf(epochSec: Long): String =
        org.njarasoa.fijerena.core.ui.utils.DateText
            .time(context, epochSec)
}
