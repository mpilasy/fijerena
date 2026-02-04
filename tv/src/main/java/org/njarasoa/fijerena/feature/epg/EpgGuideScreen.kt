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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.viewmodels.EpgViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgViewModelFactory
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

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

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is EpgViewModel.UiState.Loading -> LoadingScreen()
            is EpgViewModel.UiState.Success -> {
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
                    onBack = onBack
                )
            }
            is EpgViewModel.UiState.Error -> {
                ErrorScreen(message = state.message, onRetry = { viewModel.loadEpgData() }, onBack = onBack)
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(TvDimensions.progressIndicator),
                color = CinemaAccent
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = "Loading TV Guide...",
                style = MaterialTheme.typography.titleLarge,
                color = CinemaTextSecondary
            )
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Error Loading Guide",
                style = MaterialTheme.typography.displayMedium,
                color = CinemaError
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = CinemaTextSecondary
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            Row {
                CinemaSecondaryButton(
                    onClick = onBack,
                    text = "Back"
                )
                Spacer(modifier = Modifier.width(Spacing.md))
                CinemaPrimaryButton(
                    onClick = onRetry,
                    text = "Retry"
                )
            }
        }
    }
}
