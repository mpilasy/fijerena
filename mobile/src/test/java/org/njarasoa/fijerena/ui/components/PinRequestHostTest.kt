package org.njarasoa.fijerena.ui.components

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.profile.ProfilePins
import org.njarasoa.fijerena.core.network.sync.PinRequests
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.ui.components.PinSession
import org.robolectric.RobolectricTestRunner

/**
 * Another device's PIN request on the phone (the PIN on the phone plan, Phase 3): it shows over
 * the app, a right PIN answers it without unlocking anything here, Cancel answers it, and it goes
 * when the asking device closes it.
 */
@RunWith(RobolectricTestRunner::class)
class PinRequestHostTest {
    @get:Rule
    val rule = createComposeRule()

    private val requestId = "r-${System.nanoTime()}"

    private fun receive(open: Boolean = true) =
        PinRequests.receiveRequest(
            "tv",
            SyncPayloads.PinRequest(
                requestId,
                "anna",
                profileName = "Anna",
                fromDeviceName = "Living Room",
                open = open,
                sentAt = System.currentTimeMillis(),
            ),
            hlc = if (open) 1 else 2,
        )

    @Before
    fun setup() {
        ProfilePins(AppSettings(ApplicationProvider.getApplicationContext())).set("anna", "1234")
        PinSession.onActiveProfile("someone else")
        rule.setContent { PinRequestHost() }
        receive()
        rule.waitForIdle()
    }

    @Test
    fun `the request shows who asks and whose PIN`() {
        rule.onNodeWithText("Living Room asks").assertExists()
        rule.onNodeWithText("Enter Anna’s PIN").assertExists()
    }

    @Test
    fun `a right PIN answers the request and unlocks nothing here`() {
        rule.onNode(hasSetTextAction()).performTextInput("1234")
        rule.waitForIdle()

        assertTrue(PinRequests.incoming.value.isEmpty())
        assertTrue(!PinSession.isUnlocked("anna"))
        rule.onNodeWithText("Living Room asks").assertDoesNotExist()
    }

    @Test
    fun `a wrong PIN keeps the request open`() {
        rule.onNode(hasSetTextAction()).performTextInput("0000")
        rule.waitForIdle()

        assertEquals(
            requestId,
            PinRequests.incoming.value["tv"]
                ?.request
                ?.requestId,
        )
    }

    @Test
    fun `Cancel answers the request`() {
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()

        assertTrue(PinRequests.incoming.value.isEmpty())
    }

    @Test
    fun `the prompt goes when the asking device closes the request`() {
        receive(open = false)
        rule.waitForIdle()

        rule.onNodeWithText("Living Room asks").assertDoesNotExist()
    }
}
