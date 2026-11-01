package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.testing.FocusWalk
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.up
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.robolectric.RobolectricTestRunner

/**
 * The TV error state under a screen's header, as on Search and Search the Guide: Retry takes focus
 * on entry, OK runs the retry, and Up and Down move between Retry and the header.
 */
@RunWith(RobolectricTestRunner::class)
class TvErrorStateFocusTest {
    @get:Rule
    val rule =
        androidx.compose.ui.test.junit4
            .createComposeRule()

    private val walk = FocusWalk(rule)

    @Test
    fun `Retry takes focus, runs the retry and reaches the header`() {
        var retries = 0
        rule.setContent {
            Column(Modifier.fillMaxSize()) {
                CinemaSecondaryButton(onClick = {}, text = "Header")
                TvErrorState(message = "Couldn't connect", onRetry = { retries++ })
            }
        }
        walk.awaitFocused("Retry")

        walk.press(Key.DirectionCenter)
        rule.runOnIdle { assertEquals(1, retries) }

        walk.run(
            up("Header"),
            down("Retry"),
        )
    }
}
