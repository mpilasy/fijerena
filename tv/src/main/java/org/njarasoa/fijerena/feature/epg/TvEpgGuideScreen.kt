package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import org.njarasoa.fijerena.core.ui.viewmodels.EpgViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgViewModelFactory
import org.njarasoa.fijerena.ui.components.TvErrorState
import org.njarasoa.fijerena.ui.theme.LocalUiScale

/**
 * The TV Guide. Loading, the grid and "No listings" share one chrome ([TvGuideGrid]: title, date
 * and status line, labelled header buttons), so day navigation and Refresh stay reachable when a
 * day has nothing (GD2). No guide at all and load errors are full-screen [TvErrorState]s.
 */
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
                        searchQuery = searchQuery,
                        searchResults = searchResults,
                        onSearchQueryChanged = { viewModel.searchPrograms(it) },
                        onClearSearch = { viewModel.clearSearch() },
                        onBack = onBack,
                    )
                }
            }
        }
    }
}
