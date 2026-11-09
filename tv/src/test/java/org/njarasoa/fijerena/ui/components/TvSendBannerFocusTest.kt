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
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performKeyInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.sync.PlayResolution
import org.njarasoa.fijerena.testing.FocusWalk
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.robolectric.RobolectricTestRunner

/**
 * The send banner takes the remote's keys, never focus (the send to TV plan, ST-D16): Play first,
 * Left and Right choose, OK acts on key up only, Back dismisses, and the control under it keeps
 * focus through it all. Under a Dialog window it waits for the dialog to close.
 */
@RunWith(RobolectricTestRunner::class)
class TvSendBannerFocusTest {
    @get:Rule
    val rule =
        androidx.compose.ui.test.junit4
            .createComposeRule()

    private val walk = FocusWalk(rule)
    private val banner =
        SendBanner(
            SyncPayloads.PlayRequest(
                requestId = "r1",
                profileId = "anna",
                fromDeviceName = "Pixel 8",
                providerKey = "pk",
                streamId = "42",
                contentType = ContentType.MOVIES,
                positionMs = 2_530_000L,
                title = "Malcolm X",
                sentAt = 0,
            ),
            PlayResolution.Playable(Screen.Player("42", "Malcolm X", "c1", ContentType.MOVIES, providerId = 7L), "anna", "Anna"),
            forProfile = null,
            receivedAt = 0,
        )

    private val played = mutableListOf<SendBanner>()
    private val dismissed = mutableListOf<SendBanner>()
    private val screenKeys = mutableListOf<Key>()
    private var dialogOpen by mutableStateOf(false)
    private var showing by mutableStateOf<SendBanner?>(null)

    private fun setScreen() {
        rule.setContent {
            val state = rememberSendBannerState()
            val nested = remember { FocusRequester() }
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
                        .sendBannerKeys(state)
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
                    TvSendBanner(
                        state = state,
                        banner = showing,
                        onPlay = { played += it },
                        onDismiss = { dismissed += it },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
            LaunchedEffect(Unit) { nested.requestFocusWithRetry() }
        }
        walk.assertFocused(NESTED)
        screenKeys.clear()
        rule.runOnIdle { showing = banner }
        rule.waitForIdle()
    }

    private fun assertChosen(label: String) {
        rule.onNode(hasText(label) and isSelected()).assertExists()
    }

    @Test
    fun `the banner shows Play chosen, says who sends it and focus stays`() {
        setScreen()

        rule.onNode(hasText("Pixel 8 sends Malcolm X")).assertExists()
        rule.onNode(hasText("From 42:10")).assertExists()
        assertChosen("Play")
        walk.assertFocused(NESTED)
    }

    @Test
    fun `Left and Right choose Play or Dismiss`() {
        setScreen()

        walk.press(Key.DirectionRight)
        assertChosen("Dismiss")
        walk.press(Key.DirectionRight)
        assertChosen("Dismiss")
        walk.press(Key.DirectionLeft)
        assertChosen("Play")
        walk.assertFocused(NESTED)
    }

    @Test
    fun `no key reaches the screen while the banner shows`() {
        setScreen()

        listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.Menu, Key.ChannelUp).forEach(walk::press)
        assertEquals(emptyList<Key>(), screenKeys)
        walk.assertFocused(NESTED)
    }

    @Test
    fun `OK on Play plays and focus stays`() {
        setScreen()
        walk.press(Key.DirectionCenter)

        assertEquals(listOf(banner), played)
        assertEquals(emptyList<SendBanner>(), dismissed)
        walk.assertFocused(NESTED)
    }

    @Test
    fun `OK on Dismiss dismisses and focus stays`() {
        setScreen()
        walk.press(Key.DirectionRight)
        walk.press(Key.DirectionCenter)

        assertEquals(listOf(banner), dismissed)
        assertEquals(emptyList<SendBanner>(), played)
        walk.assertFocused(NESTED)
    }

    @Test
    fun `Back dismisses and the keys go to the screen again`() {
        setScreen()
        walk.press(Key.Back)

        assertEquals(listOf(banner), dismissed)
        assertEquals(emptyList<Key>(), screenKeys)
        walk.assertFocused(NESTED)

        rule.runOnIdle { showing = null }
        rule.waitForIdle()
        walk.press(Key.DirectionLeft)
        walk.assertFocused("Row 2 A")
    }

    @Test
    fun `an OK held down before the banner shows does not act`() {
        setScreen()
        rule.runOnIdle { showing = null }
        rule.waitForIdle()
        // OK goes down on the screen; the banner shows; only OK's key up comes while it shows.
        rule.onAllNodes(isFocused(), useUnmergedTree = true).onLast().performKeyInput { keyDown(Key.DirectionCenter) }
        rule.runOnIdle { showing = banner }
        rule.waitForIdle()
        rule.onAllNodes(isFocused(), useUnmergedTree = true).onLast().performKeyInput { keyUp(Key.DirectionCenter) }

        assertEquals(emptyList<SendBanner>(), played)
        assertChosen("Play")
    }

    @Test
    fun `under a dialog the banner waits and the keys stay with the dialog`() {
        setScreen()
        rule.runOnIdle { dialogOpen = true }
        rule.waitForIdle()

        rule.onNode(hasText("Play")).assertDoesNotExist()

        rule.runOnIdle { dialogOpen = false }
        rule.waitForIdle()
        assertChosen("Play")
    }

    private companion object {
        const val NESTED = "Row 2 B"
    }
}
