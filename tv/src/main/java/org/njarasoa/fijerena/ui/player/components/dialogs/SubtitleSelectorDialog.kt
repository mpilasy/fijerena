package org.njarasoa.fijerena.ui.player.components.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.R

@Composable
fun SubtitleSelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit,
) {
    val subtitleTracks = remember { viewModel.getSubtitleTracks() }
    val offOption =
        TvSelectorOption(
            title = stringResource(R.string.player_subtitles_off),
            selected = subtitleTracks.none { it.isSelected },
            onSelect = {
                viewModel.disableSubtitles()
                onDismiss()
            },
        )

    TvSelectorDialog(
        title = stringResource(R.string.player_select_subtitles),
        emptyText = stringResource(R.string.player_no_subtitles),
        onDismiss = onDismiss,
        options =
            listOf(offOption) +
                subtitleTracks.map { track ->
                    TvSelectorOption(
                        title = track.label,
                        selected = track.isSelected,
                        subtitle = track.mimeType.substringAfterLast("/").uppercase(),
                        onSelect = {
                            viewModel.selectSubtitleTrack(track.groupIndex, track.trackIndex)
                            onDismiss()
                        },
                    )
                },
    )
}
