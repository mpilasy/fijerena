package org.njarasoa.fijerena.feature.sports

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.sports.GameState
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.network.sports.SportsLeague
import org.njarasoa.fijerena.core.network.sports.SportsSource
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The phone's sports cards (the team results plan): a result has the score in the title, the
 * competition after it, no click; a game in progress has the score and its clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class MobileResultCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val arsenal = SportsTeam(SportsSource.ESPN, "soccer/359", "Arsenal", sport = "soccer")
    private val chelsea = SportsTeam(SportsSource.ESPN, "soccer/363", "Chelsea", sport = "soccer")
    private val league = SportsLeague(SportsSource.ESPN, "soccer/eng.1", "Premier League", "soccer")

    @Test
    fun `a result shows the score, home first, and the competition`() {
        val game =
            SportsGame(
                SportsSource.ESPN,
                "past",
                1_700_000_000,
                arsenal,
                chelsea,
                league,
                GameState.FINISHED,
                homeScore = "2",
                awayScore = "1",
            )
        composeTestRule.setContent { MobileResultCard(game = game, team = arsenal) }

        composeTestRule.onNodeWithText("Arsenal 2 – 1 Chelsea").assertHasNoClickAction()
        composeTestRule.onNodeWithText("Premier League", substring = true).assertExists()
    }

    @Test
    fun `a game in progress shows the score and the clock in place of the start`() {
        val game =
            SportsGame(
                SportsSource.ESPN,
                "on",
                1_700_000_000,
                arsenal,
                chelsea,
                league,
                GameState.LIVE,
                homeScore = "1",
                awayScore = "0",
                clock = "HT",
            )
        composeTestRule.setContent { MobileGameCard(game = game, channels = emptyList(), onClick = {}, team = arsenal) }

        composeTestRule.onNodeWithText("Arsenal 1 – 0 Chelsea").assertExists()
        composeTestRule.onNodeWithText("HT · Premier League").assertExists()
    }
}
