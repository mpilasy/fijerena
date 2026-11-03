package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.tv.material3.Surface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.sports.GameReminderAlert
import org.njarasoa.fijerena.core.network.sports.GameReminderAlerts
import org.njarasoa.fijerena.core.network.sports.ReminderChannel
import org.robolectric.RobolectricTestRunner

/**
 * The TV game reminder banner is a bar at the top centre over the screen, not a layer over the
 * whole screen, also in the TV Surface that hosts it in TvNavHost (its Box passes its minimum size
 * on). Watch shows only with a channel (the sports teams plan → Reminders).
 */
@RunWith(RobolectricTestRunner::class)
class TvGameReminderBannerLayoutTest {
    @get:Rule
    val rule =
        androidx.compose.ui.test.junit4
            .createComposeRule()

    private val withChannel =
        GameReminderAlert(
            "p1",
            "espn",
            "g1",
            "Arsenal",
            "Chelsea",
            "Premier League",
            1_800_000_000,
            ReminderChannel(3, 42, "7", "Sky Sports 1"),
        )
    private val noChannel = withChannel.copy(channel = null)

    @After
    fun tearDown() {
        GameReminderAlerts.dismissBanner(withChannel)
        GameReminderAlerts.dismissBanner(noChannel)
    }

    private fun show(alert: GameReminderAlert) {
        rule.setContent {
            val banner = rememberReminderBannerState()
            Surface(modifier = Modifier.fillMaxSize(), shape = RectangleShape) {
                Box(Modifier.fillMaxSize().reminderBannerContent(banner))
                TvGameReminderBanner(banner, onWatch = {}, modifier = Modifier.align(Alignment.TopCenter))
            }
        }
        rule.runOnIdle { GameReminderAlerts.showBanner(alert) }
        rule.waitForIdle()
    }

    @Test
    fun `the banner is a bar at the top, not the whole screen`() {
        show(withChannel)
        val screen = rule.onRoot().getBoundsInRoot()
        val bar = rule.onNodeWithTag(REMINDER_BANNER_TAG).getBoundsInRoot()

        assertEquals(screen.top, bar.top)
        assertTrue("bar ${bar.height} high on a ${screen.height} screen", bar.height < screen.height / 3)
        assertTrue("bar ${bar.width} wide on a ${screen.width} screen", bar.width < screen.width)
    }

    @Test
    fun `without a channel there is only Dismiss`() {
        show(noChannel)
        rule.onNodeWithText("Dismiss").assertExists()
        rule.onNodeWithText("Watch now").assertDoesNotExist()
    }
}
