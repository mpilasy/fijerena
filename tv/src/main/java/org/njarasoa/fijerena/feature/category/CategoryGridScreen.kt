@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.category

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.*
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.player.model.XtreamCategory
import org.njarasoa.fijerena.core.player.model.XtreamStream
import org.njarasoa.fijerena.feature.common.StatsOverlay
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.*

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
    onStreamSelected: (streamId: Int, streamName: String, categoryId: String) -> Unit,
    onSearchClick: () -> Unit = {},
    onEpgClick: (categoryId: String, categoryName: String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    viewModel: CategoryViewModel = viewModel(
        factory = CategoryViewModelFactory(
            context = LocalContext.current.applicationContext,
            contentType = contentType
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val isDevMode by remember { mutableStateOf(appSettings.isDevMode) }
    val uiScale by remember { mutableStateOf(appSettings.uiScale) }

    // Provide UI scale for all child composables
    CompositionLocalProvider(LocalUiScale provides uiScale) {
        CategoryGridContent(
            uiState = uiState,
            configuration = configuration,
            isDevMode = isDevMode,
            viewModel = viewModel,
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
    configuration: android.content.res.Configuration,
    isDevMode: Boolean,
    viewModel: CategoryViewModel,
    onStreamSelected: (streamId: Int, streamName: String, categoryId: String) -> Unit,
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
                    horizontal = Spacing.tvSafeHorizontal(configuration.screenWidthDp),
                    vertical = Spacing.tvSafeVertical(configuration.screenHeightDp)
                )
        ) {
            when (val state = uiState) {
                is CategoryViewModel.UiState.Loading -> {
                    LoadingScreen()
                }
                is CategoryViewModel.UiState.Success -> {
                    TwoColumnLayout(
                    categories = state.categories,
                    selectedCategoryId = state.selectedCategoryId,
                    streams = state.streams,
                    streamsLoading = state.streamsLoading,
                    categoriesRefreshing = state.categoriesRefreshing,
                    lastPlayedStreamId = state.lastPlayedStreamId,
                    contentType = contentType,
                    onCategorySelected = { categoryId ->
                        viewModel.loadStreams(categoryId)
                    },
                    onStreamSelected = { streamId, streamName, categoryId ->
                        onStreamSelected(streamId, streamName, categoryId)
                    },
                    onRefreshCategories = {
                        viewModel.refreshCategories()
                    },
                    onRefreshStreams = { categoryId ->
                        viewModel.refreshStreams(categoryId)
                    },
                    onSearchClick = onSearchClick,
                    onEpgClick = onEpgClick,
                    onBack = onBack
                )
            }
                is CategoryViewModel.UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { viewModel.retry() }
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
                    viewModel.getCategoriesFetchTime()?.let { put("Cat. Time", it) }
                    state.selectedCategoryId?.let {
                        viewModel.getFetchTime(it)?.let { time -> put("Streams Time", time) }
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
    categories: List<XtreamCategory>,
    selectedCategoryId: String?,
    streams: List<XtreamStream>?,
    streamsLoading: Boolean,
    categoriesRefreshing: Boolean,
    lastPlayedStreamId: Int?,
    contentType: String,
    onCategorySelected: (String) -> Unit,
    onStreamSelected: (streamId: Int, streamName: String, categoryId: String) -> Unit,
    onRefreshCategories: () -> Unit,
    onRefreshStreams: (String) -> Unit,
    onSearchClick: () -> Unit,
    onEpgClick: (categoryId: String, categoryName: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val providerName by remember { mutableStateOf(appSettings.providerName) }
    val repository = remember {
        XtreamRepository(AccountManager(context.applicationContext), context.applicationContext)
    }

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
                    onClick = onBack,
                    text = "← Back"
                )
                CinemaSecondaryButton(
                    onClick = onSearchClick,
                    text = "Search"
                )
                // EPG button - only for Live TV
                if (contentType == "LIVE_TV" && selectedCategoryId != null) {
                    val selectedCategoryName = categories.find { it.categoryId == selectedCategoryId }?.categoryName
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
                color = CinemaTextSecondary.copy(alpha = 0.87f)
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
                selectedCategoryName = categories.find { it.categoryId == selectedCategoryId }?.categoryName,
                lastPlayedStreamId = lastPlayedStreamId,
                contentType = contentType,
                repository = repository,
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
    categories: List<XtreamCategory>,
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

    val virtualCategories = categories.filter { it.categoryId in virtualCategoryIds }
    val regularCategories = categories.filter { it.categoryId !in virtualCategoryIds }

    val listState = rememberTvLazyListState()
    val focusRequesters = remember(categories) {
        categories.associate { it.categoryId to FocusRequester() }
    }

    // Auto-scroll and focus on selected category (only for regular categories)
    LaunchedEffect(regularCategories, selectedCategoryId) {
        if (regularCategories.isNotEmpty() && selectedCategoryId != null && selectedCategoryId !in virtualCategoryIds) {
            val selectedIndex = regularCategories.indexOfFirst { it.categoryId == selectedCategoryId }
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
                kotlinx.coroutines.delay(600)
            }
        }
    }

    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
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
                    tint = CinemaTextSecondary.copy(alpha = 0.87f),
                    modifier = Modifier
                        .size(20.dp.scaled(scale))
                        .rotate(rotation)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = CinemaSurfaceVariant.copy(alpha = 0.3f),
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
                                isSelected = category.categoryId == selectedCategoryId,
                                onClick = { onCategorySelected(category.categoryId) },
                                focusRequester = focusRequesters[category.categoryId]
                            )
                        }
                    }

                    // Divider between virtual and regular categories
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp.scaled(scale))
                            .padding(horizontal = Spacing.md.scaled(scale))
                            .background(CinemaAccent.copy(alpha = 0.3f))
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
                        key = { it.categoryId }
                    ) { category ->
                        CategoryItem(
                            category = category,
                            isSelected = category.categoryId == selectedCategoryId,
                            onClick = { onCategorySelected(category.categoryId) },
                            focusRequester = focusRequesters[category.categoryId]
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(
    category: XtreamCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val scale = LocalUiScale.current

    Card(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 16.dp.scaled(scale))
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
                CinemaAccent.copy(alpha = 0.2f)
            } else {
                CinemaSurface
            },
            contentColor = if (isSelected) {
                CinemaAccent
            } else {
                CinemaTextPrimary
            },
            focusedContainerColor = CinemaAccent.copy(alpha = 0.15f),
            focusedContentColor = CinemaTextPrimary
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = 4.dp.scaled(scale), color = CinemaAccentLight)
            )
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CornerRadius.medium.scaled(scale))),
        scale = CardDefaults.scale(
            scale = 1.0f,
            focusedScale = 1.05f,
            pressedScale = 0.95f
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md.scaled(scale)),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = category.categoryName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                ),
                color = CinemaTextPrimary,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun StreamList(
    streams: List<XtreamStream>?,
    streamsLoading: Boolean,
    selectedCategoryId: String?,
    selectedCategoryName: String?,
    lastPlayedStreamId: Int?,
    contentType: String,
    repository: XtreamRepository,
    onStreamSelected: (streamId: Int, streamName: String, categoryId: String) -> Unit,
    onRefreshStreams: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Animate rotation when refreshing
    var targetRotation by remember { mutableStateOf(0f) }

    LaunchedEffect(streamsLoading) {
        if (streamsLoading) {
            while (streamsLoading) {
                targetRotation += 360f
                kotlinx.coroutines.delay(600)
            }
        }
    }

    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "refresh_rotation"
    )
    val listState = rememberTvLazyListState()
    val focusRequesters = remember(streams) {
        streams?.associate { it.streamId to FocusRequester() } ?: emptyMap()
    }

    val scale = LocalUiScale.current

    // Auto-scroll and focus on last played stream
    LaunchedEffect(streams, lastPlayedStreamId) {
        if (!streams.isNullOrEmpty() && lastPlayedStreamId != null) {
            val lastPlayedIndex = streams.indexOfFirst { it.streamId == lastPlayedStreamId }
            if (lastPlayedIndex != -1) {
                listState.animateScrollToItem(lastPlayedIndex)
                // Request focus on the last played stream
                focusRequesters[lastPlayedStreamId]?.requestFocus()
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
                            tint = CinemaTextSecondary.copy(alpha = 0.87f),
                            modifier = Modifier
                                .size(20.dp.scaled(scale))
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
                    color = CinemaSurfaceVariant.copy(alpha = 0.3f),
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
                            modifier = Modifier.size(48.dp),
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
                            key = { it.streamId }
                        ) { stream ->
                            // Get watch progress for this stream
                            val watchedStream = repository.getPlaybackPosition(stream.streamId, contentType)
                            val progress = watchedStream?.let {
                                if (it.duration > 0) {
                                    (it.playbackPosition.toFloat() / it.duration.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                            } ?: 0f

                            StreamItem(
                                stream = stream,
                                isFavorite = repository.isFavorite(stream.streamId, contentType),
                                watchProgress = progress,
                                onClick = { onStreamSelected(stream.streamId, stream.name, stream.categoryId) },
                                focusRequester = focusRequesters[stream.streamId]
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
    stream: XtreamStream,
    isFavorite: Boolean = false,
    watchProgress: Float = 0f,  // 0.0 to 1.0 (0% to 100%)
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val scale = LocalUiScale.current

    Card(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 16.dp.scaled(scale))
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
            focusedContainerColor = CinemaAccent.copy(alpha = 0.15f),
            focusedContentColor = CinemaTextPrimary
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = 4.dp.scaled(scale), color = CinemaAccentLight)
            )
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CornerRadius.medium.scaled(scale))),
        scale = CardDefaults.scale(
            scale = 1.0f,
            focusedScale = 1.05f,
            pressedScale = 0.95f
        )
    ) {
        Column {  // Wrap in Column for progress bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md.scaled(scale)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Favorite star indicator (gold color)
                        if (isFavorite) {
                            Text(
                                text = "★",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                                ),
                                color = Color(0xFFFFD700)  // Gold star
                            )
                        }

                        Text(
                            text = stream.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                            ),
                            color = CinemaTextPrimary,
                            maxLines = 1
                        )
                    }
                    stream.streamIcon?.let { icon ->
                        if (icon.isNotBlank()) {
                            Text(
                                text = "HD",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                                ),
                                color = LocalContentColor.current.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Progress bar (only show if > 0%)
            if (watchProgress > 0f) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { watchProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp.scaled(scale)),
                    color = CinemaAccent,
                    trackColor = CinemaTextPrimary.copy(alpha = 0.2f)
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
                modifier = Modifier.size(48.dp),
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
