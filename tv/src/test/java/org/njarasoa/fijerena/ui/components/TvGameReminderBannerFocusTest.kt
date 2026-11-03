package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.test.isSelected
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
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.robolectric.RobolectricTestRunner

/**
 * The TV game reminder banner takes the remote's keys, never focus (the Nerd role plan → R): it
 * shows with Watch chosen (Dismiss without a channel), Left and Right choose, OK acts, Back closes
 * it, and no key reaches the screen under it. The control under it, here nested in a list row,
 * keeps focus through it all. Under a Dialog window it waits for the dialog to close.
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

    @After
    fun tearDown() {
        GameReminderAlerts.dismissBanner(alert)
        GameReminderAlerts.dismissBanner(noChannel)
    }

    private fun setScreen(shown: GameReminderAlert = alert) {
        rule.setContent {
            val banner = rememberReminderBannerState()
            val nested = remember { FocusRequester() }
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
                        .reminderBannerKeys(banner)
                        .onPreviewKeyEvent {
                            screenKeys += it.key
                            false
                        }.onKeyEvent {
                            screenKeys += it.key
                            false
                        },
                ) {
                    LazyColumn(Modifier.align(Alignment.Center)) {
                        items(5) { row ->
                            Row {
                                CinemaSecondaryButton(onClick = {}, text = "Row $row A")
                                CinemaSecondaryButton(
                                    onClick = {},
                                    text = "Row $row B",
                                    modifier = if (row == 2) Modifier.focusRequester(nested) else Modifier,
                                )
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
            LaunchedEffect(Unit) { nested.requestFocusWithRetry() }
        }
        walk.assertFocused(NESTED)
        screenKeys.clear()
        rule.runOnIdle { GameReminderAlerts.showBanner(shown) }
        rule.waitForIdle()
    }

    private fun assertChosen(label: String) {
        rule.onNode(hasText(label) and isSelected()).assertExists()
    }

    @Test
    fun `the banner shows with Watch chosen and focus stays on the screen`() {
        setScreen()
        rule.onNode(hasText("Sky Sports", substring = true)).assertExists()
        assertChosen("Watch now")
        walk.assertFocused(NESTED)
    }

    @Test
    fun `without a channel Dismiss is chosen`() {
        setScreen(noChannel)
        assertChosen("Dismiss")
        walk.press(Key.DirectionLeft)
        assertChosen("Dismiss")
    }

    @Test
    fun `Left and Right choose Watch or Dismiss`() {
        setScreen()

        walk.press(Key.DirectionRight)
        assertChosen("Dismiss")
        walk.press(Key.DirectionRight)
        assertChosen("Dismiss")
        walk.press(Key.DirectionLeft)
        assertChosen("Watch now")
        walk.assertFocused(NESTED)
    }

    @Test
    fun `no key reaches the screen under the banner`() {
        setScreen()

        listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.Menu, Key.ChannelUp).forEach(walk::press)
        assertEquals(emptyList<Key>(), screenKeys)
        assertNotNull(GameReminderAlerts.banner.value)
        walk.assertFocused(NESTED)
    }

    @Test
    fun `OK on Watch hands over the reminder and focus stays`() {
        setScreen()
        walk.press(Key.DirectionCenter)

        assertEquals(listOf(alert), watched)
        assertNull(GameReminderAlerts.banner.value)
        assertEquals(emptyList<Key>(), screenKeys)
        walk.assertFocused(NESTED)
    }

    @Test
    fun `OK on Dismiss closes the banner without watching`() {
        setScreen()
        walk.press(Key.DirectionRight)
        walk.press(Key.DirectionCenter)

        assertEquals(emptyList<GameReminderAlert>(), watched)
        assertNull(GameReminderAlerts.banner.value)
        walk.assertFocused(NESTED)
    }

    @Test
    fun `Back closes the banner and focus stays on the nested control`() {
        setScreen()
        walk.press(Key.Back)

        assertNull(GameReminderAlerts.banner.value)
        assertEquals(emptyList<Key>(), screenKeys)
        walk.assertFocused(NESTED)

        // The keys go to the screen again.
        walk.press(Key.DirectionLeft)
        walk.assertFocused("Row 2 A")
    }

    @Test
    fun `the banner goes by itself and focus stays on the nested control`() {
        setScreen()

        rule.mainClock.advanceTimeBy(CinemaAnimation.reminderBannerDismissMs)
        rule.waitForIdle()

        assertNull(GameReminderAlerts.banner.value)
        walk.assertFocused(NESTED)
        walk.press(Key.DirectionUp)
        walk.assertFocused("Row 1 B")
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
        assertChosen("Watch now")
        walk.assertFocused(NESTED)
        screenKeys.clear()
        walk.press(Key.DirectionDown)
        assertEquals(emptyList<Key>(), screenKeys)
    }

    private companion object {
        const val NESTED = "Row 2 B"
    }
}
