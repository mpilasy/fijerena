@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.category

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.ui.components.ImmutableCategoryList
import org.njarasoa.fijerena.core.ui.components.ImmutableMediaList
import org.njarasoa.fijerena.core.ui.components.ImmutableNowPlaying
import org.njarasoa.fijerena.core.ui.components.ImmutableStringSet
import org.njarasoa.fijerena.core.ui.components.ImmutableWatchProgress
import org.njarasoa.fijerena.core.ui.components.rememberFavoriteHintVisible
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModelFactory
import org.njarasoa.fijerena.feature.category.components.ErrorScreen
import org.njarasoa.fijerena.feature.category.components.LiveTvSplitLayout
import org.njarasoa.fijerena.feature.category.components.LoadingScreen
import org.njarasoa.fijerena.feature.category.components.TwoColumnLayout
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

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
fun TvCategoryGridScreen(
    contentType: String,
    initialCategoryId: String? = null,
    initialStreamId: String? = null,
    showPreviewPane: Boolean = true,
    onStreamSelected: (streamId: String, streamName: String, categoryId: String, providerData: Map<String, String>) -> Unit,
    onSearchClick: () -> Unit = {},
    onEpgClick: (categoryId: String, categoryName: String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    viewModel: CategoryViewModel =
        viewModel(
            factory =
                CategoryViewModelFactory(
                    context = LocalContext.current.applicationContext,
                    contentType = contentType,
                    initialCategoryId = initialCategoryId,
                ),
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val nowPlayingMap by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val nowPlaying = remember(nowPlayingMap) { ImmutableNowPlaying(nowPlayingMap) }
    val supportsNativeEpg by viewModel.supportsNativeEpg.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val favoriteCategoryIds by viewModel.favoriteCategoryIds.collectAsStateWithLifecycle()
    val watchProgress by viewModel.watchProgress.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val context = LocalContext.current

    val epgIndexer = remember { EpgIndexer.getInstance(context.applicationContext) }
    val epgIndexState by epgIndexer.state.collectAsStateWithLifecycle()

    // Refresh last played item when screen resumes (e.g. back from player)
    // Uses repeatOnLifecycle to avoid recomposing the entire screen on every lifecycle transition
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshLastPlayedItem()
        }
    }

    val immutableFavoriteIds = remember(favoriteIds) { ImmutableStringSet(favoriteIds) }
    val immutableFavoriteCategoryIds = remember(favoriteCategoryIds) { ImmutableStringSet(favoriteCategoryIds) }
    val immutableWatchProgress = remember(watchProgress) { ImmutableWatchProgress(watchProgress) }

    CategoryGridContent(
        uiState = uiState,
        nowPlaying = nowPlaying,
        supportsNativeEpg = supportsNativeEpg,
        favoriteIds = immutableFavoriteIds,
        favoriteCategoryIds = immutableFavoriteCategoryIds,
        watchProgress = immutableWatchProgress,
        epgIndexState = epgIndexState,
        configuration = configuration,
        catViewModel = viewModel,
        onStreamSelected = onStreamSelected,
        onSearchClick = onSearchClick,
        onEpgClick = onEpgClick,
        onBack = onBack,
        contentType = contentType,
        initialStreamId = initialStreamId,
        showPreviewPane = showPreviewPane,
    )
}

@Composable
private fun CategoryGridContent(
    uiState: CategoryViewModel.UiState,
    nowPlaying: ImmutableNowPlaying,
    supportsNativeEpg: Boolean,
    favoriteIds: ImmutableStringSet,
    favoriteCategoryIds: ImmutableStringSet,
    watchProgress: ImmutableWatchProgress,
    epgIndexState: EpgIndexState,
    configuration: android.content.res.Configuration,
    catViewModel: CategoryViewModel,
    onStreamSelected: (streamId: String, streamName: String, categoryId: String, providerData: Map<String, String>) -> Unit,
    onSearchClick: () -> Unit,
    onEpgClick: (categoryId: String, categoryName: String) -> Unit,
    onBack: () -> Unit,
    contentType: String,
    initialStreamId: String? = null,
    showPreviewPane: Boolean = true,
) {
    val scale = LocalUiScale.current
    val safeMarginModifier =
        Modifier
            .fillMaxSize()
            .padding(
                horizontal = Spacing.tvSafeMarginHorizontal,
                vertical = Spacing.tvSafeMarginVertical,
            )

    // One-time "hold to favorite" hint — favoriting has no other visible affordance on TV.
    val showFavoriteHint = rememberFavoriteHintVisible()

    // 5% padding for TV overscan safety — applied per-branch rather than around the whole
    // `when`, since LiveTvSplitLayout's promoted full-screen player must NOT inherit it (it
    // renders inside this same composable, in place, to avoid a second PlaybackViewModel/ANR —
    // see LiveTvSplitLayout's doc comment). LiveTvSplitLayout applies this same margin itself,
    // but only around its split/browsing UI, not the full-screen player.
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        when (val state = uiState) {
            is CategoryViewModel.UiState.Loading -> {
                AmbientBackdrop(modifier = Modifier.fillMaxSize())
                Box(modifier = safeMarginModifier) {
                    LoadingScreen()
                }
            }
            is CategoryViewModel.UiState.Success -> {
                val immutableCategories = remember(state.categories) { ImmutableCategoryList(state.categories) }
                val immutableStreams = remember(state.streams) { state.streams?.let { ImmutableMediaList(it) } }
                if (contentType == org.njarasoa.fijerena.core.player.domain.ContentType.LIVE_TV && showPreviewPane) {
                    val ctx = LocalContext.current
                    val devMode = remember { org.njarasoa.fijerena.core.network.AppSettings(ctx.applicationContext).isDevMode }
                    LiveTvSplitLayout(
                        categoryViewModel = catViewModel,
                        categories = immutableCategories,
                        selectedCategoryId = state.selectedCategoryId,
                        streams = immutableStreams,
                        streamsLoading = state.streamsLoading,
                        categoriesRefreshing = state.categoriesRefreshing,
                        lastPlayedItemId = state.lastPlayedItemId,
                        nowPlaying = nowPlaying,
                        contentType = contentType,
                        isDevMode = devMode,
                        favoriteIds = favoriteIds,
                        favoriteCategoryIds = favoriteCategoryIds,
                        watchProgress = watchProgress,
                        onCategorySelected = { categoryId -> catViewModel.loadStreams(categoryId) },
                        onStreamSelected = onStreamSelected,
                        onRefreshCategories = { catViewModel.refreshCategories() },
                        onRefreshStreams = { categoryId -> catViewModel.refreshStreams(categoryId) },
                        onBack = onBack,
                        initialStreamId = initialStreamId,
                    )
                } else {
                    AmbientBackdrop(modifier = Modifier.fillMaxSize())
                    Box(modifier = safeMarginModifier) {
                        TwoColumnLayout(
                            categoryViewModel = catViewModel,
                            categories = immutableCategories,
                            selectedCategoryId = state.selectedCategoryId,
                            streams = immutableStreams,
                            streamsLoading = state.streamsLoading,
                            categoriesRefreshing = state.categoriesRefreshing,
                            lastPlayedItemId = state.lastPlayedItemId,
                            nowPlaying = nowPlaying,
                            contentType = contentType,
                            favoriteIds = favoriteIds,
                            favoriteCategoryIds = favoriteCategoryIds,
                            watchProgress = watchProgress,
                            supportsNativeEpg = supportsNativeEpg,
                            epgIndexState = epgIndexState,
                            onCategorySelected = { categoryId ->
                                catViewModel.loadStreams(categoryId)
                            },
                            onStreamSelected = { streamId, streamName, categoryId, providerData ->
                                onStreamSelected(streamId, streamName, categoryId, providerData)
                            },
                            onRefreshCategories = {
                                catViewModel.refreshCategories()
                            },
                            onRefreshStreams = { categoryId ->
                                catViewModel.refreshStreams(categoryId)
                            },
                            onSearchClick = onSearchClick,
                            onEpgClick = onEpgClick,
                            onBack = onBack,
                        )
                    }
                }
            }
            is CategoryViewModel.UiState.Error -> {
                AmbientBackdrop(modifier = Modifier.fillMaxSize())
                Box(modifier = safeMarginModifier) {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { catViewModel.retry() },
                    )
                }
            }
        }

        // Skip the LiveTvSplitLayout branch — its promoted full-screen player renders in this
        // same Box (see comment above), and the hint must never draw over live video.
        val isLiveTvSplitPane = contentType == org.njarasoa.fijerena.core.player.domain.ContentType.LIVE_TV && showPreviewPane
        if (showFavoriteHint && uiState is CategoryViewModel.UiState.Success && !isLiveTvSplitPane) {
            FavoriteHintBanner(
                modifier =
                    Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .padding(bottom = Spacing.xxl.scaled(scale)),
            )
        }
    }
}

/**
 * One-time hint pointing at the hold-to-favorite gesture, which otherwise has zero on-screen
 * affordance. See [TvCategoryGridScreen] and `AppSettings.hasSeenFavoriteHint`.
 */
@Composable
private fun FavoriteHintBanner(modifier: Modifier = Modifier) {
    val scale = LocalUiScale.current
    org.njarasoa.fijerena.core.ui.components.GlassPanel(modifier = modifier) {
        androidx.tv.material3.Text(
            text = "Hold OK on a channel or category to add it to Favorites",
            style = androidx.tv.material3.MaterialTheme.typography.bodyMedium,
            color = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary,
            modifier =
                Modifier.padding(
                    horizontal = Spacing.lg.scaled(scale),
                    vertical = Spacing.sm.scaled(scale),
                ),
        )
    }
}
