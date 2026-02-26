package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.viewmodels.EpgViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgViewModelFactory
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun EpgGuideScreen(
    categoryId: String,
    categoryName: String,
    onProgramSelected: (program: EpgProgram, channel: MediaItem) -> Unit,
    onChannelSelected: (streamId: String, streamName: String, categoryId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: EpgViewModel = viewModel(
        factory = EpgViewModelFactory(
            context = LocalContext.current.applicationContext,
            categoryId = categoryId
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val uiScale by remember { mutableStateOf(appSettings.uiScale) }

    CompositionLocalProvider(LocalUiScale provides uiScale) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is EpgViewModel.UiState.Loading -> LoadingScreen()
                is EpgViewModel.UiState.Success -> {
                    val epgCategoryName = if (appSettings.isDevMode && state.epgLoadTime != null) {
                        "$categoryName | ${state.epgMatchInfo} | ${state.epgLoadTime}"
                    } else categoryName
                    EpgGridLayout(
                        categoryName = epgCategoryName,
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
                        onBack = onBack
                    )
                }
                is EpgViewModel.UiState.Error -> {
                    ErrorScreen(message = state.message, onRetry = { viewModel.loadEpgData() }, onBack = onBack)
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    val scale = LocalUiScale.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(TvDimensions.progressIndicator.scaled(scale)),
                color = CinemaAccent
            )
            Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
            Text(
                text = "Loading TV Guide...",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = MaterialTheme.typography.titleLarge.fontSize.scaled(scale)
                ),
                color = CinemaTextSecondary
            )
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    val scale = LocalUiScale.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Error Loading Guide",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = MaterialTheme.typography.displayMedium.fontSize.scaled(scale)
                ),
                color = CinemaError
            )
            Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize.scaled(scale)
                ),
                color = CinemaTextSecondary
            )
            Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
            Row {
                CinemaSecondaryButton(
                    onClick = onBack,
                    text = "Back"
                )
                Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
                CinemaPrimaryButton(
                    onClick = onRetry,
                    text = "Retry"
                )
            }
        }
    }
}
