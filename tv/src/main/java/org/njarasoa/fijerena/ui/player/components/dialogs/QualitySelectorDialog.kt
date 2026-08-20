package org.njarasoa.fijerena.ui.player.components.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.R

@Composable
fun QualitySelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit,
) {
    val videoQualities = remember { viewModel.getVideoQualities() }
    val autoOption =
        TvSelectorOption(
            title = stringResource(R.string.player_quality_auto),
            selected = videoQualities.none { it.isSelected },
            subtitle = stringResource(R.string.player_quality_auto_subtitle),
            onSelect = {
                viewModel.enableAutoQuality()
                onDismiss()
            },
        )

    TvSelectorDialog(
        title = stringResource(R.string.player_select_quality),
        emptyText = stringResource(R.string.player_no_quality),
        onDismiss = onDismiss,
        options =
            listOf(autoOption) +
                videoQualities.map { quality ->
                    TvSelectorOption(
                        title = quality.label,
                        selected = quality.isSelected,
                        subtitle = "${quality.width}×${quality.height} • ${quality.frameRate.toInt()}fps",
                        onSelect = {
                            viewModel.selectVideoQuality(quality.groupIndex, quality.trackIndex)
                            onDismiss()
                        },
                    )
                },
    )
}
