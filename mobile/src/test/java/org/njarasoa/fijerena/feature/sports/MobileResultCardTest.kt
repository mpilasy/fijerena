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

/** A result on the phone's team page (the team results plan): the score in the title, the competition after it, no click. */
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
}
