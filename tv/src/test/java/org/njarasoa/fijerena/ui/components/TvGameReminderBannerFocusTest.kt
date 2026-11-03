package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.window.Dialog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.sports.GameReminderAlert
import org.njarasoa.fijerena.core.network.sports.GameReminderAlerts
import org.njarasoa.fijerena.core.network.sports.ReminderChannel
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.testing.FocusWalk
import org.njarasoa.fijerena.testing.back
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.left
import org.njarasoa.fijerena.testing.right
import org.njarasoa.fijerena.testing.up
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.robolectric.RobolectricTestRunner

/**
 * The TV game reminder banner takes focus when it shows (Watch, or Dismiss without a channel),
 * keeps the D-pad on its two buttons, and Back closes it and gives focus back to the control that
 * had it. No key reaches the screen under it, and it goes by itself after its time even with focus.
 * Under a Dialog window it waits for the dialog to close (the Nerd role plan → R).
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
    private val noChannel = alert.copy(channel = null)
    private val watched = mutableListOf<GameReminderAlert>()

    /** The keys the screen under the banner saw. */
    private val screenKeys = mutableListOf<Key>()
    private var dialogOpen by mutableStateOf(false)

    /** Second is on the screen; its key, as a list row that recomposes under a new key. */
    private var secondShown by mutableStateOf(true)
    private var secondKey by mutableStateOf(0)

    /** The screen's buttons are on it (not while its list loads again). */
    private var buttonsShown by mutableStateOf(true)

    @After
    fun tearDown() {
        GameReminderAlerts.dismissBanner(alert)
        GameReminderAlerts.dismissBanner(noChannel)
    }

    private fun setScreen(shown: GameReminderAlert = alert) {
        rule.setContent {
            val banner = rememberReminderBannerState()
            val second = remember { FocusRequester() }
            // On Android a Dialog window takes the window focus from the screen; Robolectric leaves
            // it on the screen, so the banner gets a window that loses focus while the dialog is open.
            val window = LocalWindowInfo.current
            val screenWindow =
                remember(window) {
                    object : WindowInfo by window {
                        override val isWindowFocused get() = !dialogOpen
                    }
                }
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent {
                            screenKeys += it.key
                            false
                        }.onKeyEvent {
                            screenKeys += it.key
                            false
                        }.reminderBannerContent(banner),
                ) {
                    if (buttonsShown) {
                        Column(Modifier.align(Alignment.Center)) {
                            CinemaSecondaryButton(onClick = {}, text = "First")
                            if (secondShown) {
                                key(secondKey) {
                                    CinemaSecondaryButton(onClick = {}, text = "Second", modifier = Modifier.focusRequester(second))
                                }
                            }
                        }
                    }
                }
                CompositionLocalProvider(LocalWindowInfo provides screenWindow) {
                    TvGameReminderBanner(banner, onWatch = { watched += it }, modifier = Modifier.align(Alignment.TopCenter))
                }
                if (dialogOpen) {
                    Dialog(onDismissRequest = { dialogOpen = false }) {
                        CinemaSecondaryButton(onClick = { dialogOpen = false }, text = "Close")
                    }
                }
            }
            LaunchedEffect(Unit) { second.requestFocus() }
        }
        walk.assertFocused("Second")
        screenKeys.clear()
        rule.runOnIdle { GameReminderAlerts.showBanner(shown) }
        rule.waitForIdle()
    }

    @Test
    fun `the banner takes focus on Watch when it shows`() {
        setScreen()
        rule.onNode(hasText("Sky Sports", substring = true)).assertExists()
        walk.assertFocused("Watch now")
    }

    @Test
    fun `without a channel the banner takes focus on Dismiss`() {
        setScreen(noChannel)
        walk.assertFocused("Dismiss")
    }

    @Test
    fun `the D-pad stays on the banner's buttons`() {
        setScreen()

        walk.run(right("Dismiss"), right("Dismiss"), down("Dismiss"), left("Watch now"), left("Watch now"), up("Watch now"))
    }

    @Test
    fun `no key reaches the screen under the banner`() {
        setScreen()

        listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.Menu, Key.ChannelUp).forEach(walk::press)
        assertEquals(emptyList<Key>(), screenKeys)
        assertNotNull(GameReminderAlerts.banner.value)
    }

    @Test
    fun `Back closes the banner and gives focus back`() {
        setScreen()

        walk.run(right("Dismiss"), back("Second"))
        assertNull(GameReminderAlerts.banner.value)
        assertEquals(emptyList<Key>(), screenKeys)
    }

    @Test
    fun `Watch hands over the reminder and gives focus back`() {
        setScreen()
        walk.press(Key.DirectionCenter)

        walk.assertFocused("Second")
        assertEquals(listOf(alert), watched)
        assertNull(GameReminderAlerts.banner.value)
    }

    @Test
    fun `the banner goes by itself with focus and gives focus back`() {
        setScreen()
        walk.assertFocused("Watch now")

        rule.mainClock.advanceTimeBy(CinemaAnimation.reminderBannerDismissMs)
        rule.waitForIdle()

        assertNull(GameReminderAlerts.banner.value)
        walk.assertFocused("Second")
    }

    @Test
    fun `under a dialog the banner waits for it to close`() {
        setScreen()
        rule.runOnIdle {
            GameReminderAlerts.dismissBanner(alert)
            dialogOpen = true
        }
        walk.assertFocused("Close")

        rule.runOnIdle { GameReminderAlerts.showBanner(alert) }
        rule.onNode(hasText("Sky Sports", substring = true)).assertDoesNotExist()
        walk.assertFocused("Close")

        rule.runOnIdle { dialogOpen = false }
        rule.onNode(hasText("Sky Sports", substring = true)).assertExists()
        walk.assertFocused("Watch now")
    }

    @Test
    fun `Back gives focus to the screen when the control that had it is gone`() {
        setScreen()
        rule.runOnIdle { secondShown = false }
        rule.waitForIdle()
        walk.assertFocused("Watch now")

        walk.run(back("First"))
        assertNull(GameReminderAlerts.banner.value)
    }

    @Test
    fun `Back gives focus to the screen when the control that had it is composed again`() {
        setScreen()
        rule.runOnIdle { secondKey++ }
        rule.waitForIdle()
        walk.assertFocused("Watch now")

        // A new node under a new key is not the control that had focus: the screen's first one.
        walk.run(back("First"))
        assertNull(GameReminderAlerts.banner.value)
    }

    @Test
    fun `Back gives focus back when the screen's controls come back after the banner`() {
        setScreen()
        rule.runOnIdle { buttonsShown = false }
        rule.waitForIdle()
        walk.assertFocused("Watch now")

        rule.mainClock.autoAdvance = false
        walk.press(Key.Back)
        rule.runOnIdle { buttonsShown = true }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertNull(GameReminderAlerts.banner.value)
        walk.assertFocused("Second")
    }

    @Test
    fun `the banner goes by itself and gives focus back when the screen's controls come back`() {
        setScreen()
        rule.runOnIdle { buttonsShown = false }
        rule.waitForIdle()

        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeUntil(CinemaAnimation.reminderBannerDismissMs * 2) { GameReminderAlerts.banner.value == null }
        rule.runOnIdle { buttonsShown = true }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertNull(GameReminderAlerts.banner.value)
        walk.assertFocused("Second")
    }

    @Test
    fun `the banner goes by itself and gives focus to the screen when the control is gone`() {
        setScreen()
        rule.runOnIdle { secondShown = false }
        rule.waitForIdle()

        rule.mainClock.advanceTimeBy(CinemaAnimation.reminderBannerDismissMs)
        rule.waitForIdle()

        assertNull(GameReminderAlerts.banner.value)
        walk.assertFocused("First")
    }
}
