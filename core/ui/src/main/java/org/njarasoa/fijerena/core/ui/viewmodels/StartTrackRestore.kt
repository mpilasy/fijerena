package org.njarasoa.fijerena.core.ui.viewmodels

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import org.njarasoa.fijerena.core.player.domain.TrackAction
import org.njarasoa.fijerena.core.player.domain.startTrackActions
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService

/**
 * Starts [state]'s audio and subtitles once the player knows the tracks: a saved choice of the item
 * or its series is applied and saved again, as before; a type with no saved choice follows the
 * profile's track languages, not saved (the preferred track languages plan). The TV and phone
 * player screens call it right after `playStream`.
 *
 * Waits for the first Playing or Paused state, and stops on Error, so a failed stream does not hang
 * it. [playbackState] is a plain StateFlow, not snapshot state: it is collected, not read in
 * `snapshotFlow {}`, which would emit once and wait for ever. [loaderState] gives TMDB's original
 * language when it landed after the stream started.
 */
suspend fun applyStartTracks(
    state: StreamLoaderViewModel.StreamState.Success,
    playbackState: StateFlow<PlaybackState>,
    loaderState: StateFlow<StreamLoaderViewModel.StreamState>,
) {
    if (state.startTracks.needsTracks) {
        val ready =
            playbackState
                .filter { it is PlaybackState.Playing || it is PlaybackState.Paused || it is PlaybackState.Error }
                .first()
        val service = StreamingPlaybackService.getInstance()
        if (ready !is PlaybackState.Error && service != null) {
            val item =
                (loaderState.value as? StreamLoaderViewModel.StreamState.Success)
                    ?.takeIf { it.streamId == state.streamId }
                    ?.itemLanguages ?: state.itemLanguages
            startTrackActions(state.startTracks, state.trackLanguages, item, service.getAudioTracks(), service.getSubtitleTracks())
                .forEach { action ->
                    when (action) {
                        is TrackAction.Audio -> service.selectAudioTrack(action.index, action.persist)
                        is TrackAction.Subtitle -> service.selectSubtitleTrack(action.index, action.persist)
                    }
                }
        }
    }
}
