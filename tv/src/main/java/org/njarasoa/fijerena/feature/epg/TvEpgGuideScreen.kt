package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.sourceKey
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.history.LocalNerd
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.viewmodels.EpgViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgViewModelFactory
import org.njarasoa.fijerena.ui.components.TvEmptyState
import org.njarasoa.fijerena.ui.components.TvErrorState
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * The TV Guide. Loading, the grid and "No listings" share one chrome ([TvGuideGrid]: title, date
 * and status line, labelled header buttons), so day navigation and Refresh stay reachable when a
 * day has nothing (GD2). No guide at all and load errors are full-screen [TvErrorState]s; a list
 * with no channels is a [TvEmptyState], since nothing failed.
 *
 * [focusChannelId] (opened from the player, GD5) is the row entry focus lands on; [onSearch] opens
 * "Search the guide" on this guide's channels — the grid has no search of its own (G-9).
 * [onProgramSelected] is the details panel's Watch channel (GD6); OK on a programme opens the panel.
 * [onWatchFromStart] is its Watch from start, for a programme the channel's archive holds (catch-up).
 */
@Composable
fun TvEpgGuideScreen(
    categoryId: String,
    categoryName: String,
    onProgramSelected: (program: EpgProgram, channel: MediaItem) -> Unit,
    onWatchFromStart: (program: EpgProgram, channel: MediaItem) -> Unit,
    onChannelSelected: (channel: MediaItem) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    focusChannelId: String? = null,
    // The source whose channels the guide shows (the route's).
    providerId: Long = 0L,
    viewModel: EpgViewModel =
        viewModel(
            factory =
                EpgViewModelFactory(
                    context = LocalContext.current.applicationContext,
                    categoryId = categoryId,
                    providerId = providerId,
                ),
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            // Not a failure: Retry could only find no guide again.
            is EpgViewModel.UiState.NoGuide -> {
                GuideStatePage(categoryName) {
                    TvEmptyState(
                        message = stringResource(R.string.epg_guide_no_guide_title),
                        secondary = stringResource(R.string.epg_guide_no_guide_message),
                        icon = CinemaIcons.LiveTv,
                        actionLabel = stringResource(R.string.common_back),
                        onAction = onBack,
                        onBack = onBack,
                    )
                }
            }

            is EpgViewModel.UiState.NoChannels -> {
                GuideStatePage(categoryName) {
                    TvEmptyState(
                        message = stringResource(R.string.epg_guide_no_channels),
                        icon = CinemaIcons.LiveTv,
                        actionLabel = stringResource(R.string.common_back),
                        onAction = onBack,
                        onBack = onBack,
                    )
                }
            }

            is EpgViewModel.UiState.Error -> {
                GuideStatePage(categoryName) {
                    TvErrorState(
                        message = state.message,
                        onRetry = { viewModel.loadEpgData() },
                        title = stringResource(R.string.epg_error_loading),
                        onBack = onBack,
                        backLabel = stringResource(R.string.common_back),
                    )
                }
            }

            else -> {
                TvGuideGrid(
                    categoryName = categoryName,
                    state = state,
                    showDevStats = LocalNerd.current,
                    onProgramSelected = onProgramSelected,
                    onWatchFromStart = onWatchFromStart,
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
                    // Rows are keyed by source and id: the player's channel is on the route's source.
                    focusRowKey = focusChannelId?.let { sourceKey(providerId, it) },
                )
            }
        }
    }
}

/**
 * A guide with nothing to show (no guide, no channels, an error): the guide's header over the
 * state, as on every other page, so the page still says where it is.
 */
@Composable
private fun GuideStatePage(
    categoryName: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
    ) {
        TvScreenHeader(title = stringResource(R.string.common_tv_guide), subtitle = categoryName)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}
