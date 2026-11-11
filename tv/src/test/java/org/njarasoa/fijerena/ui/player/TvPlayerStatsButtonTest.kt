package org.njarasoa.fijerena.ui.player

import android.app.Application
import android.content.ComponentName
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.ui.player.components.overlays.TvPlayerControlsOverlay
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

/** The player's Stats button is for every profile, a Nerd or not, as on the phone. */
@RunWith(RobolectricTestRunner::class)
class TvPlayerStatsButtonTest {
    @get:Rule
    val rule =
        androidx.compose.ui.test.junit4
            .createComposeRule()

    private fun showControls(nerd: Boolean) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // No playback service in the test: the view model's bind fails quietly.
        Shadows.shadowOf(app).declareComponentUnbindable(ComponentName(app, StreamingPlaybackService::class.java))
        val viewModel = PlaybackViewModel(app)
        rule.setContent {
            FirstVideoPlayerTheme {
                TvPlayerControlsOverlay(
                    playbackState = PlaybackState.Paused(position = 0L, duration = 60_000L),
                    metadata = PlayerMetadata(title = "Film", streamUrl = "http://example.invalid/1.mp4"),
                    viewModel = viewModel,
                    livePosition = 0L,
                    liveDuration = 0L,
                    currentEpgProgram = null,
                    nextEpgProgram = null,
                    isFavorite = false,
                    onToggleFavorite = null,
                    showFullControls = true,
                    isNerd = nerd,
                    onShowAudioTrackSelector = {},
                    onShowSubtitleSelector = {},
                    onShowQualitySelector = {},
                    onShowChapterSelector = {},
                    onShowStats = {},
                )
            }
        }
    }

    @Test
    fun theAudioButtonShowsWithNoTracksForNightAudio() {
        // No service in the test: the player has no tracks. Night Audio needs the button anyway.
        showControls(nerd = false)

        rule.onAllNodesWithContentDescription("Audio").assertCountEquals(1)
    }

    @Test
    fun aNerdHasTheStatsButton() {
        showControls(nerd = true)

        rule.onAllNodesWithContentDescription("Stats").assertCountEquals(1)
    }

    @Test
    fun anotherProfileHasTheStatsButtonToo() {
        showControls(nerd = false)

        rule.onAllNodesWithContentDescription("Stats").assertCountEquals(1)
    }
}
