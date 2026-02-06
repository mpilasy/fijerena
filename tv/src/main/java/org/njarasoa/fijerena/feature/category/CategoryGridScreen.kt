@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.category

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModelFactory
import org.njarasoa.fijerena.feature.common.StatsOverlay
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaGlassBackground
import org.njarasoa.fijerena.ui.theme.CinemaGlassBorder
import org.njarasoa.fijerena.ui.theme.CinemaSurface
import org.njarasoa.fijerena.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
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
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val isDevMode by remember { mutableStateOf(appSettings.isDevMode) }
    val uiScale by remember { mutableStateOf(appSettings.uiScale) }

    // Provide UI scale for all child composables
    CompositionLocalProvider(LocalUiScale provides uiScale) {
        CategoryGridContent(
            uiState = uiState,
            nowPlaying = nowPlaying,
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
}

@Composable
private fun CategoryGridContent(
    uiState: CategoryViewModel.UiState,
    nowPlaying: Map<String, EpgProgram>,
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

@Composable
private fun TwoColumnLayout(
    categoryViewModel: CategoryViewModel,
    categories: List<MediaCategory>,
    selectedCategoryId: String?,
    streams: List<MediaItem>?,
    streamsLoading: Boolean,
    categoriesRefreshing: Boolean,
    lastPlayedItemId: String?,
    nowPlaying: Map<String, EpgProgram>,
    contentType: String,
    onCategorySelected: (String) -> Unit,
    onStreamSelected: (streamId: String, streamName: String, categoryId: String) -> Unit,
    onRefreshCategories: () -> Unit,
    onRefreshStreams: (String) -> Unit,
    onSearchClick: () -> Unit,
    onEpgClick: (categoryId: String, categoryName: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val providerName by remember { mutableStateOf(appSettings.providerName) }

    val scale = LocalUiScale.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.lg.scaled(scale)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CinemaSecondaryButton(
                    onClick = onSearchClick,
                    text = "Search"
                )
                // EPG button - only for Live TV
                if (contentType == "LIVE_TV" && selectedCategoryId != null) {
                    val selectedCategoryName = categories.find { it.id == selectedCategoryId }?.name
                    if (selectedCategoryName != null) {
                        CinemaSecondaryButton(
                            onClick = { onEpgClick(selectedCategoryId, selectedCategoryName) },
                            text = "TV Guide"
                        )
                    }
                }
                Column {
                    Text(
                        text = "IPTV.atr",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = MaterialTheme.typography.displaySmall.fontSize.scaled(scale)
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = contentType.replace("_", " "),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                        ),
                        color = CinemaAccent
                    )
                    Text(
                        text = "${categories.size} categories",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = MaterialTheme.typography.labelSmall.fontSize.scaled(scale)
                        ),
                        color = CinemaTextSecondary
                    )
                }
            }
            Text(
                text = providerName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)
                ),
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
            )
        }

        // Two-column content
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left column: Categories (30% width)
            CategoryList(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                categoriesRefreshing = categoriesRefreshing,
                onCategorySelected = onCategorySelected,
                onRefreshCategories = onRefreshCategories,
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
            )

            // Right column: Streams (70% width)
            StreamList(
                streams = streams,
                streamsLoading = streamsLoading,
                selectedCategoryId = selectedCategoryId,
                selectedCategoryName = categories.find { it.id == selectedCategoryId }?.name,
                lastPlayedItemId = lastPlayedItemId,
                nowPlaying = nowPlaying,
                contentType = contentType,
                categoryViewModel = categoryViewModel,
                onStreamSelected = onStreamSelected,
                onRefreshStreams = onRefreshStreams,
                modifier = Modifier
                    .weight(0.7f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun CategoryList(
    categories: List<MediaCategory>,
    selectedCategoryId: String?,
    categoriesRefreshing: Boolean,
    onCategorySelected: (String) -> Unit,
    onRefreshCategories: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Separate virtual and regular categories
    val virtualCategoryIds = setOf(
        CategoryViewModel.FAVORITES_CATEGORY_ID,
        CategoryViewModel.LAST_WATCHED_CATEGORY_ID,
        CategoryViewModel.CONTINUE_WATCHING_CATEGORY_ID
    )

    val virtualCategories = categories.filter { it.id in virtualCategoryIds }
    val regularCategories = categories.filter { it.id !in virtualCategoryIds }

    val listState = rememberTvLazyListState()
    val focusRequesters = remember(categories) {
        categories.associate { it.id to FocusRequester() }
    }

    // Auto-scroll and focus on selected category (only for regular categories)
    LaunchedEffect(regularCategories, selectedCategoryId) {
        if (regularCategories.isNotEmpty() && selectedCategoryId != null && selectedCategoryId !in virtualCategoryIds) {
            val selectedIndex = regularCategories.indexOfFirst { it.id == selectedCategoryId }
            if (selectedIndex != -1) {
                listState.animateScrollToItem(selectedIndex)
                // Request focus on the selected category
                focusRequesters[selectedCategoryId]?.requestFocus()
            }
        }
    }

    // Animate rotation when refreshing
    var targetRotation by remember { mutableStateOf(0f) }

    LaunchedEffect(categoriesRefreshing) {
        if (categoriesRefreshing) {
            while (categoriesRefreshing) {
                targetRotation += 360f
                kotlinx.coroutines.delay(CinemaAnimation.loadingDebounceMs)
            }
        }
    }

    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = CinemaAnimation.fadeInDurationMs, easing = LinearEasing),
        label = "refresh_rotation"
    )

    val scale = LocalUiScale.current

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.padding(bottom = Spacing.md.scaled(scale)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))
        ) {
            Text(
                text = "Categories",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = MaterialTheme.typography.titleLarge.fontSize.scaled(scale)
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = onRefreshCategories,
                enabled = !categoriesRefreshing,
                modifier = Modifier.size(Spacing.lg.scaled(scale))
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh categories",
                    tint = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                    modifier = Modifier
                        .size(TvDimensions.iconSmall.scaled(scale))
                        .rotate(rotation)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = CinemaGlassBackground,
                    shape = RoundedCornerShape(CornerRadius.small)
                )
                .border(
                    width = TvDimensions.borderDefault,
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            CinemaGlassBorder,
                            Color.White.copy(alpha = CinemaAlpha.ghost),
                            CinemaGlassBorder
                        )
                    ),
                    shape = RoundedCornerShape(CornerRadius.small)
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Sticky virtual categories section
                if (virtualCategories.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(Spacing.sm.scaled(scale)),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))
                    ) {
                        virtualCategories.forEach { category ->
                            CategoryItem(
                                category = category,
                                isSelected = category.id == selectedCategoryId,
                                onClick = { onCategorySelected(category.id) },
                                focusRequester = focusRequesters[category.id]
                            )
                        }
                    }

                    // Divider between virtual and regular categories
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp.scaled(scale))
                            .padding(horizontal = Spacing.md.scaled(scale))
                            .background(CinemaAccent.copy(alpha = CinemaAlpha.tint))
                    )

                    Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                }

                // Scrollable regular categories section
                TvLazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(Spacing.sm.scaled(scale)),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = regularCategories,
                        key = { it.id }
                    ) { category ->
                        CategoryItem(
                            category = category,
                            isSelected = category.id == selectedCategoryId,
                            onClick = { onCategorySelected(category.id) },
                            focusRequester = focusRequesters[category.id]
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(
    category: MediaCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val scale = LocalUiScale.current

    Card(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = Spacing.md.scaled(scale))
            .fillMaxWidth()
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.colors(
            containerColor = if (isSelected) {
                CinemaAccent.copy(alpha = CinemaAlpha.glassBorder)
            } else {
                CinemaSurface
            },
            contentColor = if (isSelected) {
                CinemaAccent
            } else {
                CinemaTextPrimary
            },
            focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.tint),
            focusedContentColor = CinemaTextPrimary
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CornerRadius.medium.scaled(scale))),
        scale = CardDefaults.scale(
            scale = TvFocusTokens.defaultScale,
            focusedScale = TvFocusTokens.focusedScaleContent,
            pressedScale = TvFocusTokens.pressedScaleSubtle
        ),
        glow = CardDefaults.glow(
            focusedGlow = androidx.tv.material3.Glow(
                elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                elevation = TvFocusTokens.focusShadowElevation
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md.scaled(scale)),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                ),
                color = CinemaTextPrimary,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
        }
    }
}

@Composable
private fun StreamList(
    streams: List<MediaItem>?,
    streamsLoading: Boolean,
    selectedCategoryId: String?,
    selectedCategoryName: String?,
    lastPlayedItemId: String?,
    nowPlaying: Map<String, EpgProgram>,
    contentType: String,
    categoryViewModel: CategoryViewModel,
    onStreamSelected: (streamId: String, streamName: String, categoryId: String) -> Unit,
    onRefreshStreams: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Animate rotation when refreshing
    var targetRotation by remember { mutableStateOf(0f) }

    LaunchedEffect(streamsLoading) {
        if (streamsLoading) {
            while (streamsLoading) {
                targetRotation += 360f
                kotlinx.coroutines.delay(CinemaAnimation.loadingDebounceMs)
            }
        }
    }

    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = CinemaAnimation.fadeInDurationMs, easing = LinearEasing),
        label = "refresh_rotation"
    )
    val listState = rememberTvLazyListState()
    val focusRequesters = remember(streams) {
        streams?.associate { it.id to FocusRequester() } ?: emptyMap()
    }

    val scale = LocalUiScale.current

    // Auto-scroll and focus on last played item
    LaunchedEffect(streams, lastPlayedItemId) {
        if (!streams.isNullOrEmpty() && lastPlayedItemId != null) {
            val lastPlayedIndex = streams.indexOfFirst { it.id == lastPlayedItemId }
            if (lastPlayedIndex != -1) {
                listState.animateScrollToItem(lastPlayedIndex)
                // Request focus on the last played item
                focusRequesters[lastPlayedItemId]?.requestFocus()
            }
        }
    }

    Column(modifier = modifier) {
        Column(modifier = Modifier.padding(bottom = Spacing.md.scaled(scale))) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))
            ) {
                Text(
                    text = selectedCategoryName ?: "Select a category",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = MaterialTheme.typography.titleLarge.fontSize.scaled(scale)
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Always show refresh button when a category is selected
                selectedCategoryId?.let { categoryId ->
                    IconButton(
                        onClick = { onRefreshStreams(categoryId) },
                        enabled = !streamsLoading,
                        modifier = Modifier.size(Spacing.lg.scaled(scale))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh streams",
                            tint = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                            modifier = Modifier
                                .size(TvDimensions.iconSmall.scaled(scale))
                                .rotate(rotation)
                        )
                    }
                }
            }
            // Show stream count
            if (streams != null) {
                Text(
                    text = "${streams.size} streams",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = MaterialTheme.typography.labelSmall.fontSize.scaled(scale)
                    ),
                    color = CinemaTextSecondary
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = CinemaSurfaceVariant.copy(alpha = CinemaAlpha.tint),
                    shape = RoundedCornerShape(CornerRadius.small)
                )
        ) {
            when {
                streamsLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(TvDimensions.progressIndicator),
                            color = CinemaAccent
                        )
                    }
                }
                streams.isNullOrEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (streams == null) {
                                "Select a category to view channels"
                            } else {
                                "No channels in this category"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = CinemaTextSecondary
                        )
                    }
                }
                else -> {
                    TvLazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(Spacing.sm.scaled(scale)),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))
                    ) {
                        items(
                            items = streams,
                            key = { it.id }
                        ) { item ->
                            // Get watch progress for this item
                            val watchedItem = categoryViewModel.getPlaybackPosition(item.id, contentType)
                            val progress = watchedItem?.let {
                                if (it.duration > 0) {
                                    (it.playbackPosition.toFloat() / it.duration.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                            } ?: 0f

                            StreamItem(
                                item = item,
                                isFavorite = categoryViewModel.isFavorite(item.id, contentType),
                                watchProgress = progress,
                                nowPlayingProgram = nowPlaying[item.id],
                                onClick = { onStreamSelected(item.id, item.name, item.categoryId) },
                                focusRequester = focusRequesters[item.id]
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamItem(
    item: MediaItem,
    isFavorite: Boolean = false,
    watchProgress: Float = 0f,
    nowPlayingProgram: EpgProgram? = null,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val scale = LocalUiScale.current

    Card(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = Spacing.md.scaled(scale))
            .fillMaxWidth()
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.colors(
            containerColor = CinemaSurface,
            contentColor = CinemaTextPrimary,
            focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.tint),
            focusedContentColor = CinemaTextPrimary
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CornerRadius.medium.scaled(scale))),
        scale = CardDefaults.scale(
            scale = TvFocusTokens.defaultScale,
            focusedScale = TvFocusTokens.focusedScaleContent,
            pressedScale = TvFocusTokens.pressedScaleSubtle
        ),
        glow = CardDefaults.glow(
            focusedGlow = androidx.tv.material3.Glow(
                elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                elevation = TvFocusTokens.focusShadowElevation
            )
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.sm.scaled(scale)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))
            ) {
                // Poster thumbnail
                CinemaThumbnail(
                    url = item.thumbnailUrl,
                    fallbackLetter = item.name.firstOrNull(),
                    contentType = ThumbnailContentType.DEFAULT,
                    modifier = Modifier
                        .size(
                            width = TvDimensions.posterWidth.scaled(scale),
                            height = TvDimensions.posterHeight.scaled(scale)
                        )
                )

                // Text info
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isFavorite) {
                            Text(
                                text = "\u2605",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                                ),
                                color = CinemaAccent
                            )
                        }

                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                            ),
                            color = CinemaTextPrimary,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                    // Rating (e.g. "7.9 | PG-13")
                    item.metadata?.rating?.let { rating ->
                        Text(
                            text = "★ $rating",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                            ),
                            color = CinemaAccent.copy(alpha = CinemaAlpha.textMedium),
                            maxLines = 1
                        )
                    }
                    // "What's On Now" for Live TV
                    nowPlayingProgram?.let { program ->
                        Text(
                            text = "Now: ${program.title}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                            ),
                            color = org.njarasoa.fijerena.ui.theme.CinemaOrangeLight,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }
            }

            // Progress bar
            if (watchProgress > 0f) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { watchProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TvDimensions.borderFocused.scaled(scale)),
                    color = CinemaAccent,
                    trackColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.focusedTint)
                )
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(TvDimensions.progressIndicator),
                color = CinemaAccent
            )
            Text(
                text = "Loading...",
                style = MaterialTheme.typography.titleLarge,
                color = CinemaTextSecondary
            )
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            modifier = Modifier.padding(Spacing.xl)
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.displayMedium,
                color = CinemaError
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = CinemaTextSecondary
            )

            CinemaPrimaryButton(
                onClick = onRetry,
                text = "Retry"
            )
        }
    }
}
