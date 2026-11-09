package org.njarasoa.fijerena.ui.player.components.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.nightAudioDescription
import org.njarasoa.fijerena.core.ui.components.rememberNightAudioOn
import org.njarasoa.fijerena.core.ui.components.rememberNightAudioSetter

@Composable
fun AudioTrackSelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit,
) {
    // Keyed on tracksVersion, not just composed-once: opening this dialog before ExoPlayer
    // resolved tracks used to freeze on an empty list for the dialog's whole lifetime.
    val tracksVersion by viewModel.tracksVersion.collectAsStateWithLifecycle()
    val audioTracks = remember(tracksVersion) { viewModel.getAudioTracks() }

    val nightAudioOn = rememberNightAudioOn()
    val setNightAudio = rememberNightAudioSetter()

    TvSelectorDialog(
        // Only Night Audio to set: nothing to "select".
        title = stringResource(if (audioTracks.size > 1) R.string.player_select_audio else R.string.player_audio),
        // Night Audio always shows; the list of tracks only when there is something to choose.
        emptyText = if (audioTracks.isEmpty()) stringResource(R.string.player_no_audio) else null,
        onDismiss = onDismiss,
        switchRow =
            TvSelectorSwitch(
                title = stringResource(R.string.night_audio_title),
                description = nightAudioDescription(),
                checked = nightAudioOn,
                onCheckedChange = setNightAudio,
            ),
        options =
            audioTracks.takeIf { it.size > 1 }.orEmpty().map { track ->
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
