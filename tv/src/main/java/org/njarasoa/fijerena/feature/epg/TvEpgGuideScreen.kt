package org.njarasoa.fijerena.feature.epg

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.GuideSource
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextTertiary
import org.njarasoa.fijerena.core.ui.viewmodels.EpgViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgViewModelFactory
import org.njarasoa.fijerena.ui.components.TvErrorState
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun TvEpgGuideScreen(
    categoryId: String,
    categoryName: String,
    onProgramSelected: (program: EpgProgram, channel: MediaItem) -> Unit,
    onChannelSelected: (streamId: String, streamName: String, categoryId: String) -> Unit,
    onBack: () -> Unit,
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
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val uiScale by remember { mutableStateOf(appSettings.uiScale) }

    CompositionLocalProvider(LocalUiScale provides uiScale) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is EpgViewModel.UiState.Loading -> {
                    LoadingScreen()
                }

                is EpgViewModel.UiState.Ready -> {
                    // The grid owns the title (EpgGridLayout), so the status line sits above it
                    // until GD2 folds it into the grid's header.
                    Column(modifier = Modifier.fillMaxSize()) {
                        GuideStatusLines(
                            state = state,
                            showDevStats = appSettings.isDevMode,
                            modifier =
                                Modifier.padding(
                                    start = Spacing.tvSafeMarginHorizontal,
                                    end = Spacing.tvSafeMarginHorizontal,
                                    top = Spacing.tvSafeMarginVertical,
                                ),
                        )
                        EpgGridLayout(
                            categoryName = categoryName,
                            channelRows = state.channelRows,
                            timeSlots = state.timeSlots,
                            currentTimeSlot = state.currentTimeSlot,
                            selectedDate = state.selectedDate,
                            onProgramSelected = onProgramSelected,
                            onChannelSelected = onChannelSelected,
                            onPreviousDay = { viewModel.selectPreviousDay() },
                            onNextDay = { viewModel.selectNextDay() },
                            onJumpToNow = { viewModel.jumpToNow() },
                            onRefresh = { viewModel.forceRefresh() },
                            isRefreshing = isRefreshing,
                            searchQuery = searchQuery,
                            searchResults = searchResults,
                            onSearchQueryChanged = { viewModel.searchPrograms(it) },
                            onClearSearch = { viewModel.clearSearch() },
                            onBack = onBack,
                        )
                    }
                }

                is EpgViewModel.UiState.NoListings -> {
                    TvErrorState(
                        message = noListingsMessage(state),
                        onRetry = { viewModel.forceRefresh() },
                        title = stringResource(R.string.epg_guide_no_listings_title),
                        retryLabel = stringResource(R.string.common_refresh),
                        onBack = onBack,
                        backLabel = stringResource(R.string.common_back),
                    )
                }

                is EpgViewModel.UiState.NoGuide -> {
                    TvErrorState(
                        message = stringResource(R.string.epg_guide_no_guide_message),
                        onRetry = { viewModel.loadEpgData() },
                        title = stringResource(R.string.epg_guide_no_guide_title),
                        onBack = onBack,
                        backLabel = stringResource(R.string.common_back),
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
            }
        }
    }
}

/** "N of M channels have listings · source · updated …", and the dev stats dimmed beneath it. */
@Composable
private fun GuideStatusLines(
    state: EpgViewModel.UiState.Ready,
    showDevStats: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    val style =
        MaterialTheme.typography.bodyMedium.copy(
            fontSize =
                MaterialTheme.typography.bodyMedium.fontSize
                    .scaled(scale),
        )
    Column(modifier = modifier) {
        Text(
            text =
                stringResource(
                    R.string.epg_guide_status_format,
                    state.listedCount,
                    state.totalCount,
                    sourceLabel(state.source),
                    updatedLabel(state.updatedAtMs),
                ),
            style = style,
            color = CinemaTextSecondary,
        )
        if (showDevStats) {
            Text(
                text = state.devStats,
                style = style,
                color = CinemaTextTertiary,
            )
        }
    }
}

@Composable
private fun noListingsMessage(state: EpgViewModel.UiState.NoListings): String =
    when (state.reason) {
        EpgViewModel.NoListingsReason.INDEX_EMPTY -> {
            stringResource(R.string.epg_guide_no_listings_index_empty)
        }

        EpgViewModel.NoListingsReason.NONE -> {
            stringResource(R.string.epg_guide_no_listings_none)
        }

        EpgViewModel.NoListingsReason.STALE -> {
            stringResource(
                R.string.epg_guide_no_listings_stale_format,
                sourceLabel(state.source ?: GuideSource.XMLTV),
                updatedLabel(state.updatedAtMs),
            )
        }
    }

@Composable
private fun sourceLabel(source: GuideSource): String =
    when (source) {
        GuideSource.XMLTV -> stringResource(R.string.epg_guide_source_xmltv)
        GuideSource.NATIVE -> stringResource(R.string.epg_guide_source_native)
    }

@Composable
private fun updatedLabel(updatedAtMs: Long?): String =
    if (updatedAtMs == null) {
        stringResource(R.string.epg_guide_updated_unknown)
    } else {
        DateUtils
            .getRelativeTimeSpanString(updatedAtMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            .toString()
    }

@Composable
private fun LoadingScreen() {
    val scale = LocalUiScale.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(TvDimensions.progressIndicator.scaled(scale)),
                color = CinemaAccent,
            )
            Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
            Text(
                text = stringResource(R.string.epg_loading_guide),
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize =
                            MaterialTheme.typography.titleLarge.fontSize
                                .scaled(scale),
                    ),
                color = CinemaTextSecondary,
            )
        }
    }
}
