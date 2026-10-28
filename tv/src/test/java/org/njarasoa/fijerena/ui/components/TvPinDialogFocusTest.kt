package org.njarasoa.fijerena.ui.components

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasContentDescription
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.profile.ProfilePins
import org.njarasoa.fijerena.core.network.sync.PinRequests
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.ui.components.PinRequest
import org.njarasoa.fijerena.core.ui.components.rememberPinPromptState
import org.njarasoa.fijerena.core.ui.sync.PinAsker
import org.njarasoa.fijerena.testing.FocusWalk
import org.njarasoa.fijerena.testing.center
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.left
import org.njarasoa.fijerena.testing.right
import org.robolectric.RobolectricTestRunner

/**
 * The TV PIN dialog's D-pad moves with Use My Phone (the PIN on the phone plan, Phase 2): Down from
 * the pad's last row reaches it, pressing it hides the pad and focuses Use the TV, and coming back
 * focuses 1 again.
 */
@RunWith(RobolectricTestRunner::class)
class TvPinDialogFocusTest {
    @get:Rule
    val rule =
        androidx.compose.ui.test.junit4
            .createComposeRule()

    private val walk = FocusWalk(rule)
    private val pins = ProfilePins(AppSettings(ApplicationProvider.getApplicationContext()))
    private var done = 0
    private var cancelled = 0
    private val requestId = "r-${System.nanoTime()}"
    private val closed = mutableListOf<String>()

    /** Answers as a linked device would, without live sync. */
    private inner class FakeAsker(
        private val linked: Boolean,
    ) : PinAsker {
        override fun canAsk(context: Context) = linked

        override fun ask(
            context: Context,
            profileId: String,
        ): String {
            PinRequests.ask(requestId)
            return requestId
        }

        override fun close(
            context: Context,
            requestId: String,
            profileId: String,
        ) {
            closed += requestId
        }
    }

    private fun setDialog(linked: Boolean = true) {
        rule.setContent {
            var open by remember { mutableStateOf(true) }
            if (open) {
                TvPinDialog(
                    state =
                        rememberPinPromptState(pins, PinRequest.Check("anna", "Enter Anna's PIN"), FakeAsker(linked)) {
                            done++
                            open = false
                        },
                    onCancel = {
                        cancelled++
                        open = false
                    },
                )
            }
        }
        walk.assertFocused("1")
    }

    @Test
    fun `Down from the pad reaches Use My Phone, and Right from it Cancel`() {
        setDialog()

        walk.run(down("4"), down("7"), down("Erase"), down("Use my phone"), right("Cancel"), left("Use my phone"))
    }

    @Test
    fun `Use My Phone hides the pad and focuses Use the TV, which brings the pad back on 1`() {
        setDialog()
        walk.run(down("4"), down("7"), down("Erase"), down("Use my phone"))

        walk.run(center("Use the TV"))
        assertEquals(
            0,
            rule
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasText("5"),
                ).fetchSemanticsNodes()
                .size,
        )

        walk.run(center("1"))
        assertEquals(listOf(requestId), closed)
    }

    @Test
    fun `unlocked on the phone closes the dialog as a right PIN does`() {
        setDialog()
        walk.run(down("4"), down("7"), down("Erase"), down("Use my phone"), center("Use the TV"))

        rule.runOnIdle { PinRequests.receiveAnswer(SyncPayloads.PinAnswer(requestId, SyncPayloads.PinAnswer.UNLOCKED)) }
        rule.waitForIdle()

        assertEquals(1, done)
        assertEquals(0, cancelled)
    }

    @Test
    fun `cancelled on the phone brings the pad back on 1`() {
        setDialog()
        walk.run(down("4"), down("7"), down("Erase"), down("Use my phone"), center("Use the TV"))

        rule.runOnIdle { PinRequests.receiveAnswer(SyncPayloads.PinAnswer(requestId, SyncPayloads.PinAnswer.CANCELLED)) }
        walk.assertFocused("1")

        assertEquals(
            1,
            rule
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasText("Cancelled on the phone."),
                ).fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    fun `not linked, there is no phone button`() {
        setDialog(linked = false)

        walk.run(down("4"), down("7"), down("Erase"), down("Cancel"))
    }

    @Test
    fun `Cancel while the phone is asked closes the request`() {
        setDialog()
        walk.run(down("4"), down("7"), down("Erase"), down("Use my phone"), center("Use the TV"), right("Cancel"))

        walk.press(Key.DirectionCenter)

        assertEquals(1, cancelled)
        assertEquals(listOf(requestId), closed)
    }

    @Test
    fun `a digit key while the phone is asked types here and brings the pad back`() {
        setDialog()
        walk.run(down("4"), down("7"), down("Erase"), down("Use my phone"), center("Use the TV"))

        walk.press(Key.One)

        walk.assertFocused("1")
        assertEquals(listOf(requestId), closed)
        val dots = rule.onAllNodes(hasContentDescription("1 / 4"), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(1, dots.size)
    }
}
