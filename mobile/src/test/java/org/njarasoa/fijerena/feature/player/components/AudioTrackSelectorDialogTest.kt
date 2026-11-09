package org.njarasoa.fijerena.feature.player.components

import android.app.Application
import android.content.ComponentName
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

/** The Audio dialog always offers Night Audio, whatever the tracks; a tap changes the device's setting at once. */
@RunWith(RobolectricTestRunner::class)
class AudioTrackSelectorDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun show() {
        // No playback service in the test: the player has no tracks.
        Shadows.shadowOf(app).declareComponentUnbindable(ComponentName(app, StreamingPlaybackService::class.java))
        val viewModel = PlaybackViewModel(app)
        composeTestRule.setContent {
            AudioTrackSelectorDialog(viewModel = viewModel, onDismiss = {})
        }
    }

    @Test
    fun `Night Audio shows with no track and a tap turns it on at once`() {
        show()

        composeTestRule.onNodeWithText("Night Audio").assertIsOff().performClick()

        composeTestRule.onNodeWithText("Night Audio").assertIsOn()
        assertTrue(AppSettings(app).nightAudio)
    }
}
