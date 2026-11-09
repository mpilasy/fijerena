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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.sync.PlayResolution
import org.robolectric.RobolectricTestRunner

/** The send banner is a bar at the top, with the profile and the position in its second line (the send to TV plan). */
@RunWith(RobolectricTestRunner::class)
class TvSendBannerLayoutTest {
    @get:Rule
    val rule =
        androidx.compose.ui.test.junit4
            .createComposeRule()

    private val playable = PlayResolution.Playable(Screen.Player("42", "A", "c1", ContentType.MOVIES, providerId = 7L), "anna", "Anna")

    private fun banner(
        positionMs: Long? = 2_530_000L,
        contentType: String = ContentType.MOVIES,
        subtitle: String? = null,
        forProfile: String? = null,
    ) = SendBanner(
        SyncPayloads.PlayRequest(
            requestId = "r1",
            profileId = "anna",
            fromDeviceName = "Pixel 8",
            providerKey = "pk",
            streamId = "42",
            contentType = contentType,
            positionMs = positionMs,
            title = "Malcolm X",
            subtitle = subtitle,
            sentAt = 0,
        ),
        playable,
        forProfile,
        receivedAt = 0,
    )

    private fun show(banner: SendBanner?) {
        rule.setContent {
            val state = rememberSendBannerState()
            Surface(modifier = Modifier.fillMaxSize(), shape = RectangleShape) {
                Box(Modifier.fillMaxSize().sendBannerKeys(state))
                TvSendBanner(state, banner, onPlay = {}, onDismiss = {}, modifier = Modifier.align(Alignment.TopCenter))
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `the banner is a bar at the top, not the whole screen`() {
        show(banner())
        val screen = rule.onRoot().getBoundsInRoot()
        val bar = rule.onNodeWithTag(SEND_BANNER_TAG).getBoundsInRoot()

        assertEquals(screen.top, bar.top)
        assertTrue("bar ${bar.height} high on a ${screen.height} screen", bar.height < screen.height / 3)
        assertTrue("bar ${bar.width} wide on a ${screen.width} screen", bar.width < screen.width)
    }

    @Test
    fun `the second line has the position and the profile`() {
        show(banner(forProfile = "Anna"))
        rule.onNodeWithText("Pixel 8 sends Malcolm X").assertExists()
        rule.onNodeWithText("From 42:10 · for Anna").assertExists()
    }

    @Test
    fun `an episode's line comes first and live has no position`() {
        show(banner(positionMs = 9_000L, contentType = ContentType.LIVE_TV, subtitle = "S1 E2"))
        rule.onNodeWithText("S1 E2").assertExists()
        rule.onNodeWithText("From", substring = true).assertDoesNotExist()
    }

    @Test
    fun `without a banner nothing shows`() {
        show(null)
        rule.onNodeWithTag(SEND_BANNER_TAG).assertDoesNotExist()
    }
}
