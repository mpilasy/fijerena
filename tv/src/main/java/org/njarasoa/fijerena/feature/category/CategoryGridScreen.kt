@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.category

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModelFactory
import org.njarasoa.fijerena.feature.category.components.ErrorScreen
import org.njarasoa.fijerena.feature.category.components.LoadingScreen
import org.njarasoa.fijerena.feature.category.components.TwoColumnLayout
import org.njarasoa.fijerena.feature.common.StatsOverlay
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * TV two-column layout: Categories on left, Streams on right.
 *
 * Features:
 * - Left column: Vertical list of categories
 * - Right column: Vertical list of streams for selected category
 * - D-pad navigation between columns
 * - Focus management with visual indicators
 * - 5% padding for TV overscan safety
 * - Scroll state restoration
 */
@Composable
fun CategoryGridScreen(
    contentType: String,
    initialCategoryId: String? = null,
    onStreamSelected: (streamId: String, streamName: String, categoryId: String) -> Unit,
    onSearchClick: () -> Unit = {},
    onEpgClick: (categoryId: String, categoryName: String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    viewModel: CategoryViewModel = viewModel(
        factory = CategoryViewModelFactory(
            context = LocalContext.current.applicationContext,
            contentType = contentType,
            initialCategoryId = initialCategoryId
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val supportsNativeEpg by viewModel.supportsNativeEpg.collectAsState()
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val isDevMode by remember { mutableStateOf(appSettings.isDevMode) }

    val epgIndexer = remember { EpgIndexer.getInstance(context.applicationContext) }
    val epgIndexState by epgIndexer.state.collectAsState()

    // Refresh last played item when screen resumes (e.g. back from player)
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            viewModel.refreshLastPlayedItem()
        }
    }

    CategoryGridContent(
        uiState = uiState,
        nowPlaying = nowPlaying,
        supportsNativeEpg = supportsNativeEpg,
        epgIndexState = epgIndexState,
        configuration = configuration,
        isDevMode = isDevMode,
        catViewModel = viewModel,
        onStreamSelected = onStreamSelected,
        onSearchClick = onSearchClick,
        onEpgClick = onEpgClick,
        onBack = onBack,
        contentType = contentType
    )
}

@Composable
private fun CategoryGridContent(
    uiState: CategoryViewModel.UiState,
    nowPlaying: Map<String, EpgProgram>,
    supportsNativeEpg: Boolean,
    epgIndexState: EpgIndexState,
    configuration: android.content.res.Configuration,
    isDevMode: Boolean,
    catViewModel: CategoryViewModel,
    onStreamSelected: (streamId: String, streamName: String, categoryId: String) -> Unit,
    onSearchClick: () -> Unit,
    onEpgClick: (categoryId: String, categoryName: String) -> Unit,
    onBack: () -> Unit,
    contentType: String
) {
    val scale = LocalUiScale.current

    // 5% padding for TV overscan safety
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = Spacing.tvSafeMarginHorizontal,
                    vertical = Spacing.tvSafeMarginVertical
                )
        ) {
            when (val state = uiState) {
                is CategoryViewModel.UiState.Loading -> {
                    LoadingScreen()
                }
                is CategoryViewModel.UiState.Success -> {
                    TwoColumnLayout(
                        categoryViewModel = catViewModel,
                        categories = state.categories,
                        selectedCategoryId = state.selectedCategoryId,
                        streams = state.streams,
                        streamsLoading = state.streamsLoading,
                        categoriesRefreshing = state.categoriesRefreshing,
                        lastPlayedItemId = state.lastPlayedItemId,
                        nowPlaying = nowPlaying,
                        contentType = contentType,
                        supportsNativeEpg = supportsNativeEpg,
                        epgIndexState = epgIndexState,
                        onCategorySelected = { categoryId ->
                            catViewModel.loadStreams(categoryId)
                        },
                        onStreamSelected = { streamId, streamName, categoryId ->
                            onStreamSelected(streamId, streamName, categoryId)
                        },
                        onRefreshCategories = {
                            catViewModel.refreshCategories()
                        },
                        onRefreshStreams = { categoryId ->
                            catViewModel.refreshStreams(categoryId)
                        },
                        onSearchClick = onSearchClick,
                        onEpgClick = onEpgClick,
                        onBack = onBack
                    )
                }
                is CategoryViewModel.UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { catViewModel.retry() }
                    )
                }
            }
        }

        // Stats overlay (only visible in dev mode)
        when (val state = uiState) {
            is CategoryViewModel.UiState.Success -> {
                val stats = buildMap {
                    // Payload sizes
                    state.categoriesPayloadSize?.let { put("Cat. Payload", it) }
                    state.streamsPayloadSize?.let { put("Streams Payload", it) }
                    // Fetch times
                    catViewModel.getCategoriesFetchTime()?.let { put("Cat. Time", it) }
                    state.selectedCategoryId?.let {
                        catViewModel.getFetchTime(it)?.let { time -> put("Streams Time", time) }
                    }
                    // Counts
                    put("Categories", "${state.categories.size}")
                    state.streams?.let { put("Streams", "${it.size}") }
                    // EPG index info (Live TV only)
                    if (contentType == ContentType.LIVE_TV) {
                        when (val epg = epgIndexState) {
                            is EpgIndexState.Indexed -> {
                                put("EPG Index", "${epg.programmeCount} progs, ${epg.channelCount} ch")
                            }
                            is EpgIndexState.Indexing -> {
                                put("EPG Index", "Indexing ${epg.progressPercent}%")
                            }
                            is EpgIndexState.Failed -> {
                                put("EPG Index", "Failed")
                            }
                            is EpgIndexState.NotIndexed -> {
                                put("EPG Index", "No data")
                            }
                        }
                    }
                }

                StatsOverlay(
                    visible = isDevMode,
                    stats = stats,
                    interactive = false  // Non-interactive on category screen
                )
            }
            else -> { /* No stats in loading/error states */ }
        }
    }
}
