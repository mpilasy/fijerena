package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.viewmodels.EpgViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgViewModelFactory
import org.njarasoa.fijerena.ui.components.TvEmptyState
import org.njarasoa.fijerena.ui.components.TvErrorState

/**
 * The TV Guide. Loading, the grid and "No listings" share one chrome ([TvGuideGrid]: title, date
 * and status line, labelled header buttons), so day navigation and Refresh stay reachable when a
 * day has nothing (GD2). No guide at all and load errors are full-screen [TvErrorState]s; a list
 * with no channels is a [TvEmptyState], since nothing failed.
 *
 * [focusChannelId] (opened from the player, GD5) is the row entry focus lands on; [onSearch] opens
 * "Search the guide" on this guide's channels — the grid has no search of its own (G-9).
 * [onProgramSelected] is the details panel's Watch channel (GD6); OK on a programme opens the panel.
 */
@Composable
fun TvEpgGuideScreen(
    categoryId: String,
    categoryName: String,
    onProgramSelected: (program: EpgProgram, channel: MediaItem) -> Unit,
    onChannelSelected: (streamId: String, streamName: String, categoryId: String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    focusChannelId: String? = null,
    viewModel: EpgViewModel =
        viewModel(
            factory =
                EpgViewModelFactory(
                    context = LocalContext.current.applicationContext,
                    categoryId = categoryId,
                ),
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is EpgViewModel.UiState.NoGuide -> {
                TvErrorState(
                    message = stringResource(R.string.epg_guide_no_guide_message),
                    onRetry = { viewModel.loadEpgData() },
                    title = stringResource(R.string.epg_guide_no_guide_title),
                    onBack = onBack,
                    backLabel = stringResource(R.string.common_back),
                )
            }

            is EpgViewModel.UiState.NoChannels -> {
                TvEmptyState(
                    message = stringResource(R.string.epg_guide_no_channels),
                    icon = CinemaIcons.LiveTv,
                    actionLabel = stringResource(R.string.common_back),
                    onAction = onBack,
                    onBack = onBack,
                )
            }

            is EpgViewModel.UiState.Error -> {
                TvErrorState(
                    message = state.message,
                    onRetry = { viewModel.loadEpgData() },
                    title = stringResource(R.string.epg_error_loading),
                    onBack = onBack,
                    backLabel = stringResource(R.string.common_back),
                )
            }

            else -> {
                TvGuideGrid(
                    categoryName = categoryName,
                    state = state,
                    showDevStats = appSettings.isDevMode,
                    onProgramSelected = onProgramSelected,
                    onChannelSelected = onChannelSelected,
                    onPreviousDay = { viewModel.selectPreviousDay() },
                    onNextDay = { viewModel.selectNextDay() },
                    onJumpToNow = { viewModel.jumpToNow() },
                    onRefresh = { viewModel.forceRefresh() },
                    isRefreshing = isRefreshing,
                    onSearch = onSearch,
                    onBack = onBack,
                    onRowsVisible = viewModel::onRowsVisible,
                    isFavoriteChannel = viewModel::isFavoriteChannel,
                    onToggleFavorite = viewModel::toggleFavoriteChannel,
                    onRestoreFavorite = viewModel::restoreFavoriteChannel,
                    onRemoveFromRecent = if (viewModel.canRemoveFromRecent) viewModel::removeFromRecent else null,
                    focusChannelId = focusChannelId,
                )
            }
        }
    }
}
