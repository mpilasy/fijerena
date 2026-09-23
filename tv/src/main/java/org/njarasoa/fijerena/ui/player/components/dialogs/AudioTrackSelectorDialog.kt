package org.njarasoa.fijerena.ui.player.components.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.R

@Composable
fun AudioTrackSelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit,
) {
    // Keyed on tracksVersion, not just composed-once: opening this dialog before ExoPlayer
    // resolved tracks used to freeze on an empty list for the dialog's whole lifetime.
    val tracksVersion by viewModel.tracksVersion.collectAsStateWithLifecycle()
    val audioTracks = remember(tracksVersion) { viewModel.getAudioTracks() }

    TvSelectorDialog(
        title = stringResource(R.string.player_select_audio),
        emptyText = stringResource(R.string.player_no_audio),
        onDismiss = onDismiss,
        options =
            audioTracks.map { track ->
                TvSelectorOption(
                    title = track.label,
                    selected = track.isSelected,
                    subtitle = stringResource(R.string.player_audio_format_hint, track.channelCount, track.sampleRate / 1000),
                    onSelect = {
                        viewModel.selectAudioTrack(track.groupIndex, track.trackIndex)
                        onDismiss()
                    },
                )
            },
    )
}
