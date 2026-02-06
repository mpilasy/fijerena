package org.njarasoa.fijerena.feature.epg

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.EpgUtils
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.EpgViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgViewModelFactory
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileEpgGuideScreen(
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
    var isSearchActive by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val epgDevStats = (uiState as? EpgViewModel.UiState.Success)?.let { state ->
        if (appSettings.isDevMode && state.epgLoadTime != null) {
            " | ${state.epgMatchInfo} | ${state.epgLoadTime}"
        } else ""
    } ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TV Guide - $categoryName$epgDevStats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) viewModel.clearSearch()
                        }
                    ) {
                        Icon(
                            if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            if (isSearchActive) "Close Search" else "Search"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.forceRefresh() },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(MobileDimensions.progressIndicatorSmall),
                                strokeWidth = MobileDimensions.strokeWidth
                            )
                        } else {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is EpgViewModel.UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(CinemaSpacing.md))
                            Text(
                                text = "Loading TV Guide...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is EpgViewModel.UiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Date navigation row
                        DateNavigationRow(
                            selectedDate = state.selectedDate.format(
                                DateTimeFormatter.ofPattern("EEE, MMM d")
                            ),
                            onPreviousDay = { viewModel.selectPreviousDay() },
                            onNextDay = { viewModel.selectNextDay() },
                            onJumpToNow = { viewModel.jumpToNow() }
                        )

                        if (isSearchActive) {
                            // Search mode
                            MobileEpgSearchContent(
                                searchQuery = searchQuery,
                                searchResults = searchResults,
                                onSearchQueryChanged = { viewModel.searchPrograms(it) },
                                onProgramSelected = onProgramSelected
                            )
                        } else if (state.channelRows.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No EPG data available",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            MobileEpgTimeline(
                                channelRows = state.channelRows,
                                onProgramSelected = onProgramSelected,
                                onChannelSelected = onChannelSelected,
                                onRefresh = { viewModel.forceRefresh() },
                                isRefreshing = isRefreshing
                            )
                        }
                    }
                }
                is EpgViewModel.UiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(CinemaSpacing.xl)
                        ) {
                            Text(
                                text = "Error Loading Guide",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(CinemaSpacing.md))
                            androidx.compose.material3.Button(
                                onClick = { viewModel.loadEpgData() }
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateNavigationRow(
    selectedDate: String,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onJumpToNow: () -> Unit
) {
    Row(
        modifier = Modifier.padding(
            horizontal = CinemaSpacing.sm,
            vertical = CinemaSpacing.xs
        ),
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousDay) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous Day")
        }
        Text(
            text = selectedDate,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = false,
            onClick = onJumpToNow,
            label = { Text("Now") }
        )
        IconButton(onClick = onNextDay) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next Day")
        }
    }
}

@Composable
private fun MobileEpgSearchContent(
    searchQuery: String,
    searchResults: List<EpgViewModel.EpgSearchResult>,
    onSearchQueryChanged: (String) -> Unit,
    onProgramSelected: (EpgProgram, MediaItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CinemaSpacing.sm)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            label = { Text("Search programs") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CinemaSpacing.sm)
        )

        if (searchQuery.isNotBlank() && searchResults.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No programs found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = CinemaSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)
            ) {
                items(searchResults) { result ->
                    MobileSearchResultCard(
                        result = result,
                        onClick = { onProgramSelected(result.program, result.channel) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileSearchResultCard(
    result: EpgViewModel.EpgSearchResult,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CinemaSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.program.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = result.channel.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = TimeFormat.formatTimeRange(
                        result.program.startTime,
                        result.program.endTime
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                result.program.description?.let { desc ->
                    if (desc.isNotBlank()) {
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (result.isCurrent) {
                Text(
                    text = "NOW",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}
