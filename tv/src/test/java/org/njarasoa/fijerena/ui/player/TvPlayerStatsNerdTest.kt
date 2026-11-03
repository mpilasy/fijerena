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

/**
 * The player's Stats button is for a Nerd only, whatever the Admin flag
 * (docs/plans/20261103_nerd-role-sync-failures-plan.md, Lane G).
 */
@RunWith(RobolectricTestRunner::class)
class TvPlayerStatsNerdTest {
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
    fun aNerdHasTheStatsButton() {
        showControls(nerd = true)

        rule.onAllNodesWithContentDescription("Stats").assertCountEquals(1)
    }

    @Test
    fun anotherProfileHasNoStatsButton() {
        showControls(nerd = false)

        rule.onAllNodesWithContentDescription("Stats").assertCountEquals(0)
    }
}
