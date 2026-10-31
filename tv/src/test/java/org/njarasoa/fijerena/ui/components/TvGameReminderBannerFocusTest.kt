package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.sports.GameReminderAlert
import org.njarasoa.fijerena.core.network.sports.GameReminderAlerts
import org.njarasoa.fijerena.core.network.sports.ReminderChannel
import org.njarasoa.fijerena.testing.FocusWalk
import org.njarasoa.fijerena.testing.back
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.left
import org.njarasoa.fijerena.testing.right
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.robolectric.RobolectricTestRunner

/**
 * The TV game reminder banner never takes focus by itself: Menu moves focus onto Watch, the D-pad
 * stays on its two buttons, and Back closes it and gives focus back to the control that had it
 * (the sports teams plan, Phase 7).
 */
@RunWith(RobolectricTestRunner::class)
class TvGameReminderBannerFocusTest {
    @get:Rule
    val rule =
        androidx.compose.ui.test.junit4
            .createComposeRule()

    private val walk = FocusWalk(rule)
    private val alert =
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
    private val watched = mutableListOf<GameReminderAlert>()

    @After
    fun tearDown() = GameReminderAlerts.dismissBanner(alert)

    private fun setScreen() {
        rule.setContent {
            val banner = rememberReminderBannerState()
            val second = remember { FocusRequester() }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().reminderBannerKeys(banner).reminderBannerContent(banner)) {
                    Column(Modifier.align(Alignment.Center)) {
                        CinemaSecondaryButton(onClick = {}, text = "First")
                        CinemaSecondaryButton(onClick = {}, text = "Second", modifier = Modifier.focusRequester(second))
                    }
                }
                TvGameReminderBanner(banner, onWatch = { watched += it }, modifier = Modifier.align(Alignment.TopCenter))
            }
            LaunchedEffect(Unit) { second.requestFocus() }
        }
        walk.assertFocused("Second")
        rule.runOnIdle { GameReminderAlerts.showBanner(alert) }
        rule.waitForIdle()
    }

    @Test
    fun `the banner leaves focus where it was until Menu`() {
        setScreen()
        rule.onNode(hasText("Sky Sports", substring = true)).assertExists()
        walk.assertFocused("Second")

        walk.press(Key.Menu)
        walk.assertFocused("Watch")
    }

    @Test
    fun `the D-pad stays on the banner's buttons`() {
        setScreen()
        walk.press(Key.Menu)

        walk.run(right("Dismiss"), right("Dismiss"), left("Watch"), down("Watch"))
    }

    @Test
    fun `Back closes the banner and gives focus back`() {
        setScreen()
        walk.press(Key.Menu)

        walk.run(back("Second"))
        assertNull(GameReminderAlerts.banner.value)
    }

    @Test
    fun `Watch hands over the reminder and gives focus back`() {
        setScreen()
        walk.press(Key.Menu)
        walk.press(Key.DirectionCenter)

        walk.assertFocused("Second")
        assertEquals(listOf(alert), watched)
        assertNull(GameReminderAlerts.banner.value)
    }
}
