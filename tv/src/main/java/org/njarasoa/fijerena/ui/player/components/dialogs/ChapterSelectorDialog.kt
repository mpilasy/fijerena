package org.njarasoa.fijerena.ui.player.components.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.R

@Composable
fun ChapterSelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit,
) {
    val chapters = remember { viewModel.getChapters() }
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentPosition =
        when (val state = playbackState) {
            is PlaybackState.Playing -> state.position
            is PlaybackState.Paused -> state.position
            else -> 0L
        }
    // "Selected" here means the chapter actually playing, which moves with the playhead — it is
    // not a stored choice like a track selection.
    val currentChapterIndex = chapters.indexOfLast { it.startTimeMs <= currentPosition }.coerceAtLeast(0)

    TvSelectorDialog(
        title = stringResource(R.string.player_chapters),
        emptyText = stringResource(R.string.player_no_chapters),
        onDismiss = onDismiss,
        options =
            chapters.mapIndexed { index, chapter ->
                TvSelectorOption(
                    title = chapter.title,
                    selected = index == currentChapterIndex,
                    subtitle = formatTime(chapter.startTimeMs),
                    onSelect = {
                        viewModel.seekTo(chapter.startTimeMs)
                        onDismiss()
                    },
                )
            },
    )
}
